package main.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Async HTTP client that calls the Python AI service.
 * All calls return CompletableFuture — never blocks Netty threads.
 */
public final class PythonAiClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public PythonAiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Ask the Python service to generate a policy proposal for one agent.
     */
    public CompletableFuture<Policy> proposeAsync(
            Agent agent, String basePolicy, String contextData, int round) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id",     agent.getId());
        body.put("personality",  agent.getPersonality());
        body.put("priorities",   agent.getPriorities());
        body.put("base_policy",  basePolicy);
        body.put("context_data", contextData);

        return postJson(baseUrl + "/ai/propose", body)
                .thenApply(json -> {
                    try {
                        String proposedText    = json.path("proposed_text").asText("");
                        double satisfaction     = json.path("satisfaction").asDouble(0.5);
                        Map<String, Double> params = new LinkedHashMap<>();
                        JsonNode paramsNode    = json.path("params");
                        paramsNode.fieldNames().forEachRemaining(field ->
                                params.put(field, paramsNode.path(field).asDouble(0)));
                        return new Policy(agent.getId(), round, proposedText, params, satisfaction);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse propose response", e);
                    }
                });
    }

    /**
     * Ask the Python service to rate proposals for one agent.
     * Returns a map of {proposer_agent_id → score}.
     */
    public CompletableFuture<Map<String, Double>> rateAsync(
            Agent agent, List<Policy> proposals) {

        List<Map<String, String>> proposalList = new ArrayList<>();
        for (Policy p : proposals) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("agent_id",      p.getAgentId());
            entry.put("proposed_text", p.getProposedText());
            proposalList.add(entry);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id",      agent.getId());
        body.put("personality",   agent.getPersonality());
        body.put("priorities",    agent.getPriorities());
        body.put("proposals",     proposalList);
        body.put("own_agent_id",  agent.getId());

        return postJson(baseUrl + "/ai/rate", body)
                .thenApply(json -> {
                    try {
                        Map<String, Double> grades = new LinkedHashMap<>();
                        JsonNode gradesNode = json.path("grades");
                        gradesNode.fieldNames().forEachRemaining(field ->
                                grades.put(field, gradesNode.path(field).asDouble(0)));
                        return grades;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse rate response", e);
                    }
                });
    }

    /**
     * Check if the Python service is alive.
     */
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

    // ---- Internal ----

    private CompletableFuture<JsonNode> postJson(String url, Map<String, Object> body) {
        try {
            String jsonBody = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
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