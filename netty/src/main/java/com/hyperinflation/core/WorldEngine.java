package com.hyperinflation.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyperinflation.agent.Agent;
import com.hyperinflation.agent.AgentPersonality;
import com.hyperinflation.ai.PythonAiClient;
import com.hyperinflation.econ.AgentEconomy;
import com.hyperinflation.econ.EconomyEngine;
import com.hyperinflation.net.ConnectionManager;
import com.hyperinflation.net.protocol.s2c.*;
import com.hyperinflation.world.World;

import java.util.*;
import java.util.concurrent.*;

public final class WorldEngine {

    public enum Phase { IDLE, COLLECTING, PROCESSING, BROADCASTING }

    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "world-engine");
                t.setDaemon(true);
                return t;
            });

    private final PythonAiClient   aiClient;
    private final World            world;
    private final EconomyEngine    economy;
    private final List<Agent>      agents;
    private final ActionProcessor  actionProcessor;
    private final ModuleRegistry   moduleRegistry;

    // ---- Networking: uses the proper ConnectionManager + protocol stack ----
    private ConnectionManager connectionManager;

    private int   currentTick = 0;
    private Phase phase       = Phase.IDLE;

    private final Map<String, String> lastThoughts = new LinkedHashMap<>();
    private volatile String lastNarration = "";

    // ---- Debate system ----
    private static final int MAX_TICKS = 25;
    private final Map<String, List<Double>> walletHistory = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> actionCounts = new LinkedHashMap<>();

    private static final String[] AGENT_START_CITIES = {
            "nexus",    // agent-0 The Grinder
            "vault",    // agent-1 The Shark
            "freeport", // agent-2 The Diplomat
            "ironhold", // agent-3 The Gambler
            "eden",     // agent-4 The Architect
    };

    public WorldEngine(PythonAiClient aiClient) {
        this.aiClient        = aiClient;
        this.world           = new World();
        this.economy         = new EconomyEngine();
        this.agents          = initAgents();
        this.actionProcessor = new ActionProcessor(world, economy);
        this.moduleRegistry  = new ModuleRegistry(world);

        for (int i = 0; i < agents.size(); i++) {
            Agent a = agents.get(i);
            economy.registerAgent(a.getId(), 100.0);
            if (i < AGENT_START_CITIES.length) {
                actionProcessor.setAgentLocation(a.getId(), AGENT_START_CITIES[i]);
            }
        }
    }

    /** Inject the ConnectionManager (created by AiGatewayServer). */
    public void setConnectionManager(ConnectionManager cm) {
        this.connectionManager = cm;
    }

    /**
     * Build agents from the AgentPersonality roster.
     * This wires the previously-dead AgentPersonality class.
     */
    private List<Agent> initAgents() {
        AgentPersonality[] roster = AgentPersonality.defaultRoster();
        List<Agent> list = new ArrayList<>();
        for (AgentPersonality ap : roster) {
            list.add(new Agent(ap));
        }
        return Collections.unmodifiableList(list);
    }

    public void start() {
        log("WorldEngine starting. Checking AI service...");
        aiClient.healthCheck().thenAccept(healthy -> {
            if (healthy) {
                log("AI service UP. First tick in 5 seconds.");
                executor.schedule(this::runTick, 5, TimeUnit.SECONDS);
            } else {
                log("AI service DOWN. Retrying in 10s...");
                executor.schedule(this::retryStart, 10, TimeUnit.SECONDS);
            }
        });
    }

    private void retryStart() {
        aiClient.healthCheck().thenAccept(healthy -> {
            if (healthy) {
                log("AI service now UP.");
                executor.schedule(this::runTick, 2, TimeUnit.SECONDS);
            } else {
                log("AI still DOWN. Retrying...");
                executor.schedule(this::retryStart, 10, TimeUnit.SECONDS);
            }
        });
    }

    public void shutdown() { executor.shutdown(); }

    private static final long TICK_DEADLINE_SECONDS = 30;
    private static final long PER_AGENT_TIMEOUT_SECONDS = 25;

    private void runTick() {
        currentTick++;
        phase = Phase.COLLECTING;
        actionProcessor.clearEvents();

        log("=== Tick " + currentTick + " === COLLECTING");
        broadcastPhase("COLLECTING");

        // 1. Collect actions from active SimulationModules
        List<Action> moduleActions = new ArrayList<>();
        for (SimulationModule mod : moduleRegistry.getActiveModules()) {
            try {
                List<Action> acts = mod.tick(world, currentTick)
                        .orTimeout(5, TimeUnit.SECONDS)
                        .join();
                if (acts != null) moduleActions.addAll(acts);
            } catch (Exception e) {
                log("Module " + mod.getModuleId() + " failed: " + e.getMessage());
            }
        }

        // 2. Fire ALL agent AI calls in parallel with individual timeouts.
        //    Each call is independent — one agent's failure cannot block another.
        List<Agent> aliveAgents = new ArrayList<>();
        for (Agent a : agents) {
            if (a.isAlive()) aliveAgents.add(a);
        }

        // Create one future per agent, each with its own timeout + fallback
        @SuppressWarnings("unchecked")
        CompletableFuture<List<Action>>[] agentFutures = new CompletableFuture[aliveAgents.size()];

        for (int i = 0; i < aliveAgents.size(); i++) {
            Agent agent = aliveAgents.get(i);
            AgentEconomy agentEcon = economy.getAgentEconomy(agent.getId());
            String agentCity = actionProcessor.getAgentLocation(agent.getId());

            agentFutures[i] = aiClient.requestTurnAsync(
                            agent, agentEcon, world, economy, agents,
                            currentTick, agentCity, actionProcessor.getAllLocations())
                    .orTimeout(PER_AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .handle((actions, err) -> {
                        if (err != null) {
                            log("AI FAILED for " + agent.getId() + ": " + err.getMessage());
                            agent.getMemory().record(currentTick, "AI_CALL", "system",
                                    false, 0.0, "AI call failed: " + err.getMessage());
                            return List.of(Action.noOp("ai-fallback"));
                        }
                        if (actions == null || actions.isEmpty()) {
                            log("AI returned null/empty for " + agent.getId() + ", using NO_OP");
                            return List.of(Action.noOp("ai-null-guard"));
                        }
                        return actions;
                    });
        }

        // 3. Wait for ALL agents with a hard tick deadline.
        //    allOf completes when every future has resolved (success or fallback).
        CompletableFuture<Void> allDone = CompletableFuture.allOf(agentFutures);

        allDone
                .orTimeout(TICK_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .whenComplete((ignored, tickErr) -> executor.execute(() -> {
                    // Gather results — every future is guaranteed resolved via .handle()
                    List<Action> allActions = new ArrayList<>(moduleActions);

                    for (int i = 0; i < agentFutures.length; i++) {
                        try {
                            List<Action> result = agentFutures[i].getNow(null);
                            if (result != null) {
                                allActions.addAll(result);
                            } else {
                                // Tick deadline hit before this agent finished
                                Agent agent = aliveAgents.get(i);
                                log("TIMEOUT: " + agent.getId() + " did not respond in time, using NO_OP");
                                agent.getMemory().record(currentTick, "TIMEOUT", "system",
                                        false, 0.0, "Tick deadline exceeded");
                                allActions.add(Action.noOp("tick-timeout"));
                            }
                        } catch (Exception e) {
                            allActions.add(Action.noOp("tick-error"));
                            log("Error collecting result for agent " + i + ": " + e.getMessage());
                        }
                    }

                    if (tickErr != null) {
                        log("TICK DEADLINE HIT (" + TICK_DEADLINE_SECONDS + "s) — proceeding with "
                                + allActions.size() + " actions collected so far");
                    } else {
                        log("All " + aliveAgents.size() + " agents responded. "
                                + allActions.size() + " total actions.");
                    }

                    processTick(allActions);
                }));
    }

    private void processTick(List<Action> actions) {
        phase = Phase.PROCESSING;
        log("PROCESSING " + actions.size() + " actions");
        broadcastPhase("PROCESSING");

        for (Action action : actions) {
            try {
                actionProcessor.process(action);

                String reasoning = action.paramStr("_reasoning", "");
                if (!reasoning.isEmpty() && action.getAgentId() != null) {
                    lastThoughts.put(action.getAgentId(), reasoning);
                }

                if (action.getAgentId() != null && action.getType() != Action.Type.NO_OP) {
                    Agent agent = findAgent(action.getAgentId());
                    if (agent != null) {
                        String location = actionProcessor.getAgentLocation(action.getAgentId());
                        String target   = action.getTargetId() != null ? action.getTargetId() : "none";
                        agent.getMemory().record(
                                currentTick,
                                action.getType().name(),
                                target,
                                true,
                                0.0,
                                location != null ? "at " + location : "no location"
                        );
                    }
                    // Track action counts for post-game debate
                    actionCounts.computeIfAbsent(action.getAgentId(), k -> new LinkedHashMap<>())
                            .merge(action.getType().name(), 1, Integer::sum);
                }
            } catch (Exception e) {
                log("Action error: " + e.getMessage());
            }
        }

        economy.tick(world);
        world.tickAll();

        // Record tick-end wallet snapshots into agent memory
        for (Agent agent : agents) {
            AgentEconomy econ = economy.getAgentEconomy(agent.getId());
            if (econ != null) {
                String location = actionProcessor.getAgentLocation(agent.getId());
                agent.getMemory().record(
                        currentTick,
                        "TICK_RESULT",
                        "self",
                        !econ.isInDebt(),
                        econ.getWallet(),
                        "wallet=$" + String.format("%.2f", econ.getWallet())
                                + " served=" + econ.getPromptsServed()
                                + " price=$" + String.format("%.2f", economy.getPromptPrice())
                                + (location != null ? " loc=" + location : "")
                );
                // Track wallet history for post-game debate
                walletHistory.computeIfAbsent(agent.getId(), k -> new ArrayList<>())
                        .add(econ.getWallet());
            }
        }

        phase = Phase.BROADCASTING;
        log("BROADCASTING (" + actionProcessor.getEvents().size() + " events)");

        // Fire narrator AI call (non-blocking — doesn't delay the tick)
        // Uses previous tick's narration for THIS broadcast, updates for next tick
        aiClient.requestNarrationAsync(
                        currentTick,
                        actionProcessor.getEvents(),
                        world.toSummaryMap(),
                        economy.toMap(),
                        new LinkedHashMap<>(lastThoughts))
                .orTimeout(8, TimeUnit.SECONDS)
                .whenComplete((narration, err) -> {
                    if (narration != null && !narration.isEmpty()) {
                        lastNarration = narration;
                        log("NARRATOR: " + narration);
                    } else if (err != null) {
                        log("Narrator failed (non-fatal): " + err.getMessage());
                    }
                });

        broadcastRoundResult();

        if (currentTick >= MAX_TICKS) {
            phase = Phase.IDLE;
            log("=== SIMULATION COMPLETE at tick " + currentTick + " ===");
            broadcastPhase("DEBATE_STARTING");
            executor.schedule(this::startDebate, 3, TimeUnit.SECONDS);
        } else {
            phase = Phase.IDLE;
            log("=== Tick " + currentTick + " done ===\n");
            executor.schedule(this::runTick, 2, TimeUnit.SECONDS);
        }
    }

    // ==================================================================
    //  BROADCASTING — uses ConnectionManager + proper S2C packets
    // ==================================================================

    /**
     * Send the initial snapshot to a new observer.
     * Called from ProtocolHandler after handshake, or from the legacy
     * ObserverWsHandler path.
     */
    public S2CWorldSnapshot buildWorldSnapshot() {
        return new S2CWorldSnapshot(
                currentTick,
                world.toFullMap(),
                economy.toMap(),
                actionProcessor.getAllLocations()
        );
    }

    private void broadcastPhase(String name) {
        S2CPhaseChange pkt = new S2CPhaseChange(
                currentTick, name, world.toSummaryMap());

        // Protocol stack (ConnectionManager)
        if (connectionManager != null) {
            connectionManager.broadcastPacket(pkt);
        }
    }

    private void broadcastRoundResult() {
        S2CRoundResult pkt = new S2CRoundResult(
                currentTick,
                world.toFullMap(),
                economy.toMap(),
                actionProcessor.getEvents(),
                new LinkedHashMap<>(lastThoughts),
                new LinkedHashMap<>(actionProcessor.getAllLocations()),
                lastNarration
        );

        if (connectionManager != null) {
            connectionManager.broadcastPacket(pkt);
        }
    }

    // ==================================================================
    //  POST-GAME DEBATE — runs after tick 50
    // ==================================================================

    private void startDebate() {
        log("=== DEBATE PHASE 1: Opening Statements ===");

        // Build shared context
        Map<String, Double> allWallets = new LinkedHashMap<>();
        for (Agent a : agents) {
            AgentEconomy econ = economy.getAgentEconomy(a.getId());
            allWallets.put(a.getId(), econ != null ? econ.getWallet() : 0.0);
        }

        Map<String, Object> cityStates = new LinkedHashMap<>();
        for (Map.Entry<String, com.hyperinflation.world.City> e : world.getCities().entrySet()) {
            Map<String, Object> cs = new LinkedHashMap<>();
            com.hyperinflation.world.City c = e.getValue();
            cs.put("name", c.getName());
            cs.put("happiness", c.getHappiness());
            cs.put("infrastructure", c.getInfrastructure());
            cs.put("defenses", c.getDigitalDefenses());
            cs.put("treasury", c.getTreasury());
            cs.put("population", c.getPopulation());
            cityStates.put(e.getKey(), cs);
        }

        Map<String, Integer> territoryCounts = new LinkedHashMap<>();
        for (String cid : world.getCities().keySet()) {
            territoryCounts.put(cid, world.getWorldMap().countTilesForCity(cid));
        }

        // Collect event highlights per agent
        Map<String, List<String>> eventHighlights = new LinkedHashMap<>();
        for (Map<String, Object> evt : actionProcessor.getEvents()) {
            String agentId = (String) evt.get("agent");
            String desc = (String) evt.get("description");
            if (agentId != null && desc != null) {
                eventHighlights.computeIfAbsent(agentId, k -> new ArrayList<>()).add(desc);
            }
        }

        // Phase 1: Opening statements (parallel)
        Map<String, String> openings = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] openingFutures = new CompletableFuture[agents.size()];

        for (int i = 0; i < agents.size(); i++) {
            Agent agent = agents.get(i);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("agent_id", agent.getId());
            body.put("personality_name", agent.getPersonality().name);
            body.put("personality_description", agent.getPersonality().description);
            body.put("priorities", agent.getPersonality().priorities);
            body.put("wallet_history", walletHistory.getOrDefault(agent.getId(), List.of()));
            AgentEconomy econ = economy.getAgentEconomy(agent.getId());
            body.put("final_wallet", econ != null ? econ.getWallet() : 0.0);
            Map<String, Integer> ac = actionCounts.getOrDefault(agent.getId(), Map.of());
            body.put("total_actions", ac.values().stream().mapToInt(Integer::intValue).sum());
            body.put("action_summary", ac);
            body.put("city_states", cityStates);
            body.put("all_agent_wallets", allWallets);
            body.put("event_highlights", eventHighlights.getOrDefault(agent.getId(), List.of()));
            body.put("territory_counts", territoryCounts);
            body.put("final_tick", currentTick);

            final int idx = i;
            openingFutures[i] = aiClient.requestDebateOpening(body)
                    .orTimeout(20, TimeUnit.SECONDS)
                    .thenAccept(statement -> {
                        synchronized (openings) {
                            openings.put(agent.getId(), statement);
                        }
                        log("OPENING [" + agent.getPersonality().name + "]: " + statement);
                    });
        }

        CompletableFuture.allOf(openingFutures)
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((v, err) -> executor.execute(() -> {
                    if (err != null) log("Some openings timed out: " + err.getMessage());
                    broadcastDebatePhase("DEBATE_OPENING", openings);
                    log("=== Opening statements broadcast ===");

                    // Phase 2: Rebuttals (after a pause)
                    executor.schedule(() -> runRebuttals(openings, allWallets, cityStates, territoryCounts),
                            4, TimeUnit.SECONDS);
                }));
    }

    private void runRebuttals(
            Map<String, String> openings,
            Map<String, Double> allWallets,
            Map<String, Object> cityStates,
            Map<String, Integer> territoryCounts) {

        log("=== DEBATE PHASE 2: Rebuttals ===");

        Map<String, String> rebuttals = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] rebuttalFutures = new CompletableFuture[agents.size()];

        for (int i = 0; i < agents.size(); i++) {
            Agent agent = agents.get(i);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("agent_id", agent.getId());
            body.put("personality_name", agent.getPersonality().name);
            body.put("personality_description", agent.getPersonality().description);
            body.put("priorities", agent.getPersonality().priorities);
            body.put("own_opening", openings.getOrDefault(agent.getId(), ""));
            Map<String, String> others = new LinkedHashMap<>(openings);
            others.remove(agent.getId());
            body.put("other_openings", others);
            AgentEconomy econ = economy.getAgentEconomy(agent.getId());
            body.put("final_wallet", econ != null ? econ.getWallet() : 0.0);
            body.put("all_agent_wallets", allWallets);

            rebuttalFutures[i] = aiClient.requestDebateRebuttal(body)
                    .orTimeout(20, TimeUnit.SECONDS)
                    .thenAccept(statement -> {
                        synchronized (rebuttals) {
                            rebuttals.put(agent.getId(), statement);
                        }
                        log("REBUTTAL [" + agent.getPersonality().name + "]: " + statement);
                    });
        }

        CompletableFuture.allOf(rebuttalFutures)
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((v, err) -> executor.execute(() -> {
                    if (err != null) log("Some rebuttals timed out: " + err.getMessage());
                    broadcastDebatePhase("DEBATE_REBUTTAL", rebuttals);
                    log("=== Rebuttals broadcast ===");

                    // Phase 3: Thesis
                    executor.schedule(() -> runThesis(openings, rebuttals, allWallets,
                            cityStates, territoryCounts), 4, TimeUnit.SECONDS);
                }));
    }

    private void runThesis(
            Map<String, String> openings,
            Map<String, String> rebuttals,
            Map<String, Double> allWallets,
            Map<String, Object> cityStates,
            Map<String, Integer> territoryCounts) {

        log("=== DEBATE PHASE 3: Thesis ===");

        // Build compressed event summary
        StringBuilder evtSummary = new StringBuilder();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (Map<String, Object> evt : actionProcessor.getEvents()) {
            String type = (String) evt.get("type");
            if (type != null) typeCounts.merge(type, 1, Integer::sum);
        }
        typeCounts.forEach((t, c) -> evtSummary.append(t).append(": ").append(c).append("x, "));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("all_openings", openings);
        body.put("all_rebuttals", rebuttals);
        body.put("wallet_histories", walletHistory);
        body.put("city_states", cityStates);
        body.put("territory_counts", territoryCounts);
        body.put("event_log_summary", evtSummary.toString());
        body.put("final_tick", currentTick);

        aiClient.requestDebateThesis(body)
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((rawResult, err) -> executor.execute(() -> {
                    Map<String, String> result;
                    if (err != null || rawResult == null) {
                        log("Thesis generation failed: " + (err != null ? err.getMessage() : "null result"));
                        result = new LinkedHashMap<>();
                        result.put("thesis", "The narrator was unable to render a verdict.");
                        result.put("winner_id", "unknown");
                        result.put("winner_name", "Unknown");
                    } else {
                        result = rawResult;
                    }

                    log("=== THESIS: " + result.get("thesis"));
                    log("=== WINNER: " + result.get("winner_name") + " ===");

                    broadcastDebatePhase("DEBATE_THESIS", result);

                    log("=== SIMULATION AND DEBATE COMPLETE ===");
                }));
    }

    /**
     * Broadcast debate data using S2CPhaseChange.
     * The worldSummary field carries the debate statements.
     */
    private void broadcastDebatePhase(String phaseName, Map<String, String> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(data);
        S2CPhaseChange pkt = new S2CPhaseChange(currentTick, phaseName, payload);
        if (connectionManager != null) {
            connectionManager.broadcastPacket(pkt);
        }
    }

    // ==================================================================
    //  GETTERS
    // ==================================================================

    public World                     getWorld()           { return world; }
    public EconomyEngine             getEconomy()         { return economy; }
    public List<Agent>               getAgents()          { return agents; }
    public String                    getPhase()           { return phase.name(); }
    public int                       getCurrentTick()     { return currentTick; }
    public Map<String, String>       getLastThoughts()    { return lastThoughts; }
    public Map<String, String>       getLocations()       { return actionProcessor.getAllLocations(); }
    public List<Map<String, Object>> getEvents()          { return actionProcessor.getEvents(); }
    public ModuleRegistry            getModuleRegistry()  { return moduleRegistry; }
    public ActionProcessor           getActionProcessor() { return actionProcessor; }
    public String                    getNarration()       { return lastNarration; }

    private Agent findAgent(String id) {
        for (Agent a : agents) if (a.getId().equals(id)) return a;
        return null;
    }

    private void log(String msg) { System.out.println("[ENGINE] " + msg); }
}