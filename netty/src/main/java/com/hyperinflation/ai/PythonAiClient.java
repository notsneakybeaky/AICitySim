package com.hyperinflation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyperinflation.agent.Agent;
import com.hyperinflation.core.Action;
import com.hyperinflation.econ.AgentEconomy;
import com.hyperinflation.econ.EconomyEngine;
import com.hyperinflation.world.World;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Async HTTP client that calls the Python AI service.
 * Completely rewritten for the new world simulation architecture.
 *
 * Endpoints:
 *   POST /ai/turn      → Agent decides actions based on world state
 *   POST /ai/alliance   → Agent decides whether to accept an alliance
 *   GET  /health        → Health check
 */
public final class PythonAiClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public PythonAiClient(String baseUrl) {
        this.baseUrl    = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper     = new ObjectMapper();
    }

    // =====================================================================
    //  MAIN ENDPOINT: Agent Turn
    // =====================================================================

    /**
     * Ask the AI service for an agent's actions this tick.
     *
     * @param agent       The agent making the decision
     * @param agentEcon   The agent's economic state
     * @param world       The current world state
     * @param economy     The full economy engine (for market data)
     * @param allAgents   All agents (for the "other agents" view)
     * @param tick        Current tick number
     * @return A future list of Actions to be processed by the ActionProcessor
     */
    public CompletableFuture<List<Action>> requestTurnAsync(
            Agent agent,
            AgentEconomy agentEcon,
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

        // Agent economic state
        body.put("wallet",            agentEcon.getWallet());
        body.put("allocated_prompts", agentEcon.getAllocatedPrompts());
        body.put("prompts_served",    agentEcon.getPromptsServed());
        body.put("in_debt",           agentEcon.isInDebt());

        // Agent memory
        body.put("memory_summary", agent.getMemory().buildTacticalSummary());

        // World state
        body.put("world_state", world.toFullMap());

        // Economy state
        body.put("economy_state", economy.toMap());

        // Other agents (public info only)
        List<Map<String, Object>> otherAgents = new ArrayList<>();
        for (Agent other : allAgents) {
            if (other.getId().equals(agent.getId())) continue;
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id",     other.getId());
            info.put("name",   other.getPersonality().name);
            info.put("alive",  other.isAlive());
            AgentEconomy otherEcon = economy.getAgentEconomy(other.getId());
            if (otherEcon != null) {
                info.put("wallet",           otherEcon.getWallet());
                info.put("allocated_prompts", otherEcon.getAllocatedPrompts());
                info.put("in_debt",          otherEcon.isInDebt());
            }
            otherAgents.add(info);
        }
        body.put("other_agents", otherAgents);

        // Tick
        body.put("current_tick", tick);

        // Fire the request
        return postJson(baseUrl + "/ai/turn", body)
                .thenApply(json -> parseActions(json, agent.getId()));
    }

    /**
     * Parse the AI response into a list of Action objects.
     */
    private List<Action> parseActions(JsonNode json, String agentId) {
        List<Action> actions = new ArrayList<>();
        String moduleId = "ai-agent-module"; // Source module for all AI actions

        JsonNode actionsNode = json.path("actions");
        if (!actionsNode.isArray() || actionsNode.isEmpty()) {
            return List.of(Action.noOp(moduleId));
        }

        for (JsonNode actionNode : actionsNode) {
            try {
                String typeStr = actionNode.path("type").asText("NO_OP").toUpperCase();
                Action.Type type;
                try {
                    type = Action.Type.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("[AI-CLIENT] Unknown action type: " + typeStr
                            + " from " + agentId + ", skipping.");
                    continue;
                }

                String targetId = actionNode.path("target_id").asText(null);
                if ("null".equals(targetId)) targetId = null;

                Map<String, Object> params = new LinkedHashMap<>();
                JsonNode paramsNode = actionNode.path("params");
                if (paramsNode.isObject()) {
                    paramsNode.fieldNames().forEachRemaining(field -> {
                        JsonNode val = paramsNode.path(field);
                        if (val.isNumber()) {
                            params.put(field, val.asDouble());
                        } else if (val.isTextual()) {
                            params.put(field, val.asText());
                        } else if (val.isBoolean()) {
                            params.put(field, val.asBoolean());
                        }
                    });
                }

                // Include the AI's reasoning in the params for logging
                String reasoning = actionNode.path("reasoning").asText("");
                if (!reasoning.isEmpty()) {
                    params.put("_reasoning", reasoning);
                }

                actions.add(Action.of(type, moduleId, agentId, targetId, params));

            } catch (Exception e) {
                System.err.println("[AI-CLIENT] Failed to parse action for "
                        + agentId + ": " + e.getMessage());
            }
        }

        if (actions.isEmpty()) {
            actions.add(Action.noOp(moduleId));
        }

        return actions;
    }

    // =====================================================================
    //  SECONDARY ENDPOINT: Alliance Decision
    // =====================================================================

    /**
     * Ask the AI service whether an agent should accept an alliance.
     *
     * @return A future resolving to true (accept) or false (reject)
     */
    public CompletableFuture<Boolean> requestAllianceDecision(
            Agent agent,
            AgentEconomy agentEcon,
            Agent proposer,
            AgentEconomy proposerEcon,
            String terms) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id",              agent.getId());
        body.put("personality_name",      agent.getPersonality().name);
        body.put("priorities",            agent.getPersonality().priorities);
        body.put("memory_summary",        agent.getMemory().buildTacticalSummary());
        body.put("wallet",                agentEcon.getWallet());
        body.put("proposer_id",           proposer.getId());
        body.put("proposer_name",         proposer.getPersonality().name);
        body.put("proposer_description",  proposer.getPersonality().description);
        body.put("proposer_wallet",       proposerEcon.getWallet());
        body.put("alliance_terms",        terms);

        return postJson(baseUrl + "/ai/alliance", body)
                .thenApply(json -> json.path("accept").asBoolean(false));
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
    //  INTERNAL HTTP
    // =====================================================================

    private CompletableFuture<JsonNode> postJson(String url, Map<String, Object> body) {
        try {
            String jsonBody = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120)) // LLM calls can be slow
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            throw new RuntimeException("Python AI service error "
                                    + resp.statusCode() + ": " + resp.body());
                        }
                        try {
                            return mapper.readTree(resp.body());
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse response JSON", e);
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}