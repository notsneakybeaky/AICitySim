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

        // 2. Sequential AI agent chain (avoids Gemini 429 rate limits)
        CompletableFuture<List<Action>> sequentialChain =
                CompletableFuture.completedFuture(new ArrayList<>(moduleActions));

        for (Agent agent : agents) {
            if (!agent.isAlive()) continue;

            AgentEconomy agentEcon = economy.getAgentEconomy(agent.getId());
            String       agentCity = actionProcessor.getAgentLocation(agent.getId());

            sequentialChain = sequentialChain.thenCompose(accumulated ->
                    aiClient.requestTurnAsync(agent, agentEcon, world, economy, agents,
                                    currentTick, agentCity, actionProcessor.getAllLocations())
                            .orTimeout(12, TimeUnit.SECONDS)
                            .handle((actions, err) -> {
                                if (err != null) {
                                    log("AI FAILED for " + agent.getId() + ": " + err.getMessage());
                                    agent.getMemory().record(currentTick, "AI_CALL", "system",
                                            false, 0.0, "AI call failed: " + err.getMessage());
                                    accumulated.add(Action.noOp("ai-fallback"));
                                } else if (actions != null) {
                                    accumulated.addAll(actions);
                                }
                                return accumulated;
                            })
            );
        }

        sequentialChain.whenComplete((allActions, err) ->
                executor.execute(() -> {
                    if (allActions == null) {
                        log("FATAL: action chain returned null (err=" + err + ")");
                        phase = Phase.IDLE;
                        executor.schedule(this::runTick, 5, TimeUnit.SECONDS);
                        return;
                    }
                    log("Collected " + allActions.size() + " actions sequentially");
                    processTick(allActions);
                })
        );
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
                        // Rich memory via AgentMemory
                        agent.getMemory().record(
                                currentTick,
                                action.getType().name(),
                                target,
                                true,   // success (not blocked)
                                0.0,
                                location != null ? "at " + location : "no location"
                        );
                    }
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
            }
        }

        phase = Phase.BROADCASTING;
        log("BROADCASTING (" + actionProcessor.getEvents().size() + " events)");
        broadcastRoundResult();

        phase = Phase.IDLE;
        log("=== Tick " + currentTick + " done ===\n");
        executor.schedule(this::runTick, 2, TimeUnit.SECONDS);
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
                new LinkedHashMap<>(actionProcessor.getAllLocations())
        );

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

    private Agent findAgent(String id) {
        for (Agent a : agents) if (a.getId().equals(id)) return a;
        return null;
    }

    private void log(String msg) { System.out.println("[ENGINE] " + msg); }
}