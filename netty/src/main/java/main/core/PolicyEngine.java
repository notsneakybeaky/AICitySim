package main.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.*;
import java.util.concurrent.*;

/**
 * Single-threaded state machine that orchestrates AI-driven policy rounds.
 *
 * Agents are virtual personas. The engine calls the Python AI service
 * (via PythonAiClient) with each agent's personality, collects proposals
 * and ratings, picks a winner, and applies the winning policy to MarketRegime.
 *
 * ALL state mutations run on the single engine thread.
 * Python AI calls are async and never block Netty threads.
 */
public final class PolicyEngine {

    // ---- Phases ----
    public enum Phase {
        IDLE, PROPOSING, EVALUATING, MEDIATING, APPLYING, COMPLETE
    }

    // ---- Config ----
    private static final double LAMBDA            = 0.5;
    private static final double SCORE_THRESHOLD   = -0.5;
    private static final long   ROUND_INTERVAL_MS = 30_000;

    // ---- Dependencies ----
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "policy-engine");
                t.setDaemon(true);
                return t;
            });

    private final PythonAiClient aiClient;
    private final List<Agent> agents;
    private final String basePolicyText;

    // ---- Round state ----
    private int   currentRound = 0;
    private Phase phase        = Phase.IDLE;
    private final List<Policy>                     proposals   = new ArrayList<>();
    private final Map<String, Map<String, Double>> evaluations = new LinkedHashMap<>();

    // ---- Market state ----
    private final MarketRegime regime;

    // ---- Decision log ----
    private final List<Map<String, Object>> decisionLog = new CopyOnWriteArrayList<>();

    // ---- WebSocket observers (dashboard) ----
    private final ChannelGroup observers =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    // =====================================================================
    //  CONSTRUCTOR
    // =====================================================================

    public PolicyEngine(MarketRegime regime, PythonAiClient aiClient, String basePolicyText) {
        this.regime         = regime;
        this.aiClient       = aiClient;
        this.basePolicyText = basePolicyText;
        this.agents         = initializeAgents();
    }

    private List<Agent> initializeAgents() {
        return List.of(
                new Agent("agent-0", "blue-collar worker",
                        "job security, wages, cost of living"),
                new Agent("agent-1", "CEO of a large corporation",
                        "profit margins, regulatory burden, shareholder value"),
                new Agent("agent-2", "career politician",
                        "electability, optics, constituency approval"),
                new Agent("agent-3", "parent of school-age children",
                        "education funding, safety, future opportunity"),
                new Agent("agent-4", "small business owner",
                        "tax burden, local economy, access to credit")
        );
    }

    // =====================================================================
    //  PUBLIC API
    // =====================================================================

    public void start() {
        log("PolicyEngine starting. Checking Python AI service...");
        aiClient.healthCheck().thenAccept(healthy -> {
            if (healthy) {
                log("Python AI service is UP. First round in 5 seconds...");
                executor.schedule(this::startRound, 5, TimeUnit.SECONDS);
            } else {
                log("WARNING: Python AI service is DOWN at startup. Will retry in 10s...");
                executor.schedule(this::retryStart, 10, TimeUnit.SECONDS);
            }
        });
    }

    private void retryStart() {
        aiClient.healthCheck().thenAccept(healthy -> {
            if (healthy) {
                log("Python AI service is now UP. Starting rounds...");
                executor.schedule(this::startRound, 2, TimeUnit.SECONDS);
            } else {
                log("Python AI service still DOWN. Retrying in 10s...");
                executor.schedule(this::retryStart, 10, TimeUnit.SECONDS);
            }
        });
    }

    public void addObserver(Channel ch) {
        observers.add(ch);
        Map<String, Object> msg = ordered();
        msg.put("type",   "MARKET_STATE");
        msg.put("round",  currentRound);
        msg.put("phase",  phase.name());
        msg.put("regime", regime.toMap());
        sendToChannel(ch, msg);
    }

    public void shutdown() { executor.shutdown(); }

    // =====================================================================
    //  ROUND LIFECYCLE
    // =====================================================================

    private void startRound() {
        currentRound++;
        proposals.clear();
        evaluations.clear();
        phase = Phase.PROPOSING;

        log("=== Round " + currentRound + " === Phase: PROPOSING");
        broadcastPhase("PROPOSING");

        String context = regime.toContextString();

        // Fire 5 parallel proposal requests to Python AI service
        List<CompletableFuture<Policy>> futures = new ArrayList<>();
        for (Agent agent : agents) {
            CompletableFuture<Policy> f = aiClient
                    .proposeAsync(agent, basePolicyText, context, currentRound)
                    .exceptionally(err -> {
                        log("Proposal FAILED for " + agent.getId() + ": " + err.getMessage());
                        return null;
                    });
            futures.add(f);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, err) -> executor.execute(() -> {
                    for (CompletableFuture<Policy> f : futures) {
                        try {
                            Policy p = f.get();
                            if (p != null) {
                                proposals.add(p);
                                log("  Proposal from " + p.getAgentId()
                                        + " | satisfaction=" + p.getSelfSatisfaction());
                            }
                        } catch (Exception ignored) {}
                    }

                    log("Received " + proposals.size() + "/" + agents.size() + " proposals");
                    broadcastProposals();

                    if (proposals.size() < 2) {
                        log("Too few proposals. Skipping round.");
                        completeRound(null);
                    } else {
                        startEvaluationPhase();
                    }
                }));
    }

    // ---- Evaluation phase ----

    private void startEvaluationPhase() {
        phase = Phase.EVALUATING;
        log("Phase: EVALUATING");
        broadcastPhase("EVALUATING");

        // Fire 5 parallel rating requests to Python AI service
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Agent agent : agents) {
            CompletableFuture<Void> f = aiClient
                    .rateAsync(agent, proposals)
                    .thenAccept(grades -> {
                        synchronized (evaluations) {
                            evaluations.put(agent.getId(), grades);
                        }
                        log("  Rating from " + agent.getId() + ": " + grades);
                    })
                    .exceptionally(err -> {
                        log("Rating FAILED for " + agent.getId() + ": " + err.getMessage());
                        return null;
                    });
            futures.add(f);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, err) -> executor.execute(() -> {
                    log("Received " + evaluations.size() + "/" + agents.size() + " evaluations");
                    mediate();
                }));
    }

    // ---- Mediation ----

    private void mediate() {
        phase = Phase.MEDIATING;
        log("Phase: MEDIATING");

        // Aggregate: for each proposal, compute weighted_score = mean - λ * std_dev
        Map<String, double[]> scoreData = new LinkedHashMap<>();

        for (Policy proposal : proposals) {
            String proposerId = proposal.getAgentId();
            List<Double> scores = new ArrayList<>();

            for (Map.Entry<String, Map<String, Double>> eval : evaluations.entrySet()) {
                // Skip self-rating
                if (eval.getKey().equals(proposerId)) continue;
                Double score = eval.getValue().get(proposerId);
                if (score != null) scores.add(score);
            }

            double mean = scores.stream().mapToDouble(d -> d).average().orElse(0.0);
            double variance = scores.stream()
                    .mapToDouble(d -> (d - mean) * (d - mean))
                    .average().orElse(0.0);
            double stdDev = Math.sqrt(variance);
            double weightedScore = mean - LAMBDA * stdDev;

            scoreData.put(proposerId, new double[]{mean, stdDev, weightedScore});
            log(String.format("  %s: mean=%.3f std=%.3f weighted=%.3f",
                    proposerId, mean, stdDev, weightedScore));
        }

        // Pick the highest weighted score
        String winnerId    = null;
        double bestScore   = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, double[]> e : scoreData.entrySet()) {
            double ws = e.getValue()[2];
            if (ws > bestScore) {
                bestScore = ws;
                winnerId  = e.getKey();
            }
        }

        log("Winner: " + winnerId + " (weighted=" + String.format("%.3f", bestScore)
                + ", threshold=" + SCORE_THRESHOLD + ")");

        if (winnerId != null && bestScore >= SCORE_THRESHOLD) {
            String finalWinnerId = winnerId;
            Policy winner = proposals.stream()
                    .filter(p -> p.getAgentId().equals(finalWinnerId))
                    .findFirst().orElse(null);
            completeRound(winner);
        } else {
            log("No policy met threshold → HOLD");
            completeRound(null);
        }
    }

    // ---- Round completion ----

    private void completeRound(Policy winner) {
        phase = Phase.APPLYING;

        Map<String, Object> result = ordered();
        result.put("type",  "ROUND_RESULT");
        result.put("round", currentRound);

        if (winner != null) {
            regime.applyPolicy(winner);
            result.put("status",         "APPLIED");
            result.put("winner_agent",   winner.getAgentId());
            result.put("winning_policy", winner.toMap());

            Map<String, Object> entry = ordered();
            entry.put("round",        currentRound);
            entry.put("winner",       winner.getAgentId());
            entry.put("policy",       winner.toMap());
            entry.put("regime_after", regime.toMap());
            entry.put("ts",           System.currentTimeMillis());
            decisionLog.add(entry);

            log("Policy APPLIED → price=" + String.format("%.2f", regime.getPrice())
                    + " version=" + regime.getVersion());
        } else {
            result.put("status", "HOLD");
            result.put("reason", "No policy met threshold or too few proposals");
        }

        result.put("regime", regime.toMap());
        broadcastObservers(result);

        phase = Phase.COMPLETE;
        log("=== Round " + currentRound + " complete ===\n");

        // Schedule next round
        phase = Phase.IDLE;
        executor.schedule(this::startRound, ROUND_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // =====================================================================
    //  BROADCAST / MESSAGING
    // =====================================================================

    private void broadcastPhase(String phaseName) {
        broadcastObservers(Map.of(
                "type",  "PHASE_CHANGE",
                "round", currentRound,
                "phase", phaseName,
                "regime", regime.toMap()
        ));
    }

    private void broadcastProposals() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Policy p : proposals) list.add(p.toMap());
        broadcastObservers(Map.of(
                "type",      "PROPOSALS",
                "round",     currentRound,
                "proposals", list
        ));
    }

    private void broadcastObservers(Map<String, Object> msg) {
        String json = toJson(msg);
        observers.writeAndFlush(new TextWebSocketFrame(json));
    }

    private void sendToChannel(Channel ch, Map<String, Object> msg) {
        if (ch.isActive()) {
            ch.writeAndFlush(new TextWebSocketFrame(toJson(msg)));
        }
    }

    // =====================================================================
    //  ACCESSORS (for HTTP API)
    // =====================================================================

    public MarketRegime getRegime()                   { return regime; }
    public List<Map<String, Object>> getDecisionLog() { return Collections.unmodifiableList(decisionLog); }
    public String getPhase()                          { return phase.name(); }
    public int    getCurrentRound()                   { return currentRound; }
    public int    getAgentCount()                     { return agents.size(); }

    // =====================================================================
    //  UTILITIES
    // =====================================================================

    private String toJson(Map<String, Object> map) {
        try { return mapper.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }

    private static Map<String, Object> ordered() { return new LinkedHashMap<>(); }

    private void log(String msg) {
        System.out.println("[ENGINE] " + msg);
    }
}