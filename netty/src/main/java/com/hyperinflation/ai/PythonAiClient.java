package com.hyperinflation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PythonAiClient {

    private final String       baseUrl;
    private final HttpClient   httpClient;
    private final ObjectMapper mapper;

    public PythonAiClient(String baseUrl) {
        this.baseUrl    = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper     = new ObjectMapper();
    }

    // =====================================================================
    //  MAIN: Agent Turn
    // =====================================================================

    public CompletableFuture<List<Action>> requestTurnAsync(
            Agent agent,
            EconomyEngine.AgentEconomy agentEcon,
            World world,
            EconomyEngine economy,
            List<Agent> allAgents,
            int tick) {

        Map<String, Object> body = new LinkedHashMap<>();

        // Agent identity
        body.put("agent_id",                agent.getId());
        body.put("personality_name",        agent.getPersonality().name);
        body.put("personality_description", agent.getPersonality().description);
        body.put("priorities",              agent.getPersonality().priorities);
        body.put("aggression",              agent.getPersonality().aggression);
        body.put("risk_tolerance",          agent.getPersonality().riskTolerance);
        body.put("cooperation",             agent.getPersonality().cooperation);

        // Agent economy
        body.put("wallet",            agentEcon.getWallet());
        body.put("allocated_prompts", agentEcon.getAllocatedPrompts());
        body.put("prompts_served",    agentEcon.getPromptsServed());
        body.put("in_debt",           agentEcon.isInDebt());

        // Memory
        body.put("memory_summary", agent.getMemory().buildTacticalSummary());

        // World + economy
        body.put("world_state",   world.toFullMap());
        body.put("economy_state", economy.toMap());

        // Other agents (public info)
        List<Map<String, Object>> others = new ArrayList<>();
        for (Agent other : allAgents) {
            if (other.getId().equals(agent.getId())) continue;
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id",    other.getId());
            info.put("name",  other.getPersonality().name);
            info.put("alive", other.isAlive());
            EconomyEngine.AgentEconomy otherEcon = economy.getAgentEconomy(other.getId());
            if (otherEcon != null) {
                info.put("wallet",            otherEcon.getWallet());
                info.put("allocated_prompts", otherEcon.getAllocatedPrompts());
                info.put("in_debt",           otherEcon.isInDebt());
            }
            others.add(info);
        }
        body.put("other_agents", others);
        body.put("current_tick", tick);

        return postJson(baseUrl + "/ai/turn", body)
                .thenApply(json -> parseActions(json, agent.getId()));
    }

    private List<Action> parseActions(JsonNode json, String agentId) {
        List<Action> actions = new ArrayList<>();

        JsonNode actionsNode = json.path("actions");
        if (!actionsNode.isArray() || actionsNode.isEmpty()) {
            return List.of(Action.noOp("ai-agent"));
        }

        for (JsonNode node : actionsNode) {
            try {
                String typeStr = node.path("type").asText("NO_OP").toUpperCase();
                Action.Type type;
                try {
                    type = Action.Type.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("[AI-CLIENT] Unknown action: " + typeStr);
                    continue;
                }

                String targetId = node.path("target_id").asText(null);
                if ("null".equals(targetId)) targetId = null;

                Map<String, Object> params = new LinkedHashMap<>();
                JsonNode pNode = node.path("params");
                if (pNode.isObject()) {
                    pNode.fieldNames().forEachRemaining(field -> {
                        JsonNode val = pNode.path(field);
                        if (val.isNumber())       params.put(field, val.asDouble());
                        else if (val.isTextual())  params.put(field, val.asText());
                        else if (val.isBoolean())  params.put(field, val.asBoolean());
                    });
                }

                String reasoning = node.path("reasoning").asText("");
                if (!reasoning.isEmpty()) params.put("_reasoning", reasoning);

                actions.add(Action.of(type, "ai-agent", agentId, targetId, params));

            } catch (Exception e) {
                System.err.println("[AI-CLIENT] Parse error for " + agentId + ": " + e.getMessage());
            }
        }

        return actions.isEmpty() ? List.of(Action.noOp("ai-agent")) : actions;
    }

    // =====================================================================
    //  HEALTH CHECK
    // =====================================================================

    public CompletableFuture<Boolean> healthCheck() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> resp.statusCode() == 200)
                .exceptionally(err -> false);
    }

    // =====================================================================
    //  INTERNAL
    // =====================================================================

    private CompletableFuture<JsonNode> postJson(String url, Map<String, Object> body) {
        try {
            String jsonBody = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            throw new RuntimeException("AI error " + resp.statusCode()
                                    + ": " + resp.body());
                        }
                        try { return mapper.readTree(resp.body()); }
                        catch (Exception e) { throw new RuntimeException("JSON parse failed", e); }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}