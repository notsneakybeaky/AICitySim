package com.hyperinflation.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyperinflation.agent.Agent;
import com.hyperinflation.ai.PythonAiClient;
import com.hyperinflation.econ.AgentEconomy;
import com.hyperinflation.econ.EconomyEngine;
import com.hyperinflation.world.World;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

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

    private final PythonAiClient  aiClient;
    private final World           world;
    private final EconomyEngine   economy;
    private final List<Agent>     agents;
    private final ActionProcessor actionProcessor;

    private int   currentTick = 0;
    private Phase phase       = Phase.IDLE;

    // AI internal thoughts from last tick — surfaced to dashboard
    private final Map<String, String> lastThoughts = new LinkedHashMap<>();

    private final ChannelGroup observers =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private int clientCounter = 0;

    // Starting city assignments for agents
    private static final String[] AGENT_START_CITIES = {
            "nexus",    // agent-0 The Grinder
            "vault",    // agent-1 The Shark
            "freeport", // agent-2 The Diplomat
            "ironhold", // agent-3 The Gambler
            "eden",     // agent-4 The Architect
            "nexus",    // agent-5 The Parasite
            "ironhold", // agent-6 The Zealot
            "freeport", // agent-7 The Ghost
            "vault",    // agent-8 The Banker
            "eden",     // agent-9 The Warlord
    };

    // =====================================================================
    //  CONSTRUCTOR
    // =====================================================================

    public WorldEngine(PythonAiClient aiClient) {
        this.aiClient        = aiClient;
        this.world           = new World();
        this.economy         = new EconomyEngine();
        this.agents          = initAgents();
        this.actionProcessor = new ActionProcessor(world, economy);

        for (int i = 0; i < agents.size(); i++) {
            Agent a = agents.get(i);
            economy.registerAgent(a.getId(), 100.0);
            // Assign starting locations
            if (i < AGENT_START_CITIES.length) {
                actionProcessor.setAgentLocation(a.getId(), AGENT_START_CITIES[i]);
            }
        }
    }

    private List<Agent> initAgents() {
        return List.of(
                new Agent("agent-0", new Agent.Personality(
                        "The Grinder",
                        "Blue-collar worker who values stability, wages, and community. "
                                + "Prefers to build infrastructure and boost happiness.",
                        "job security, wages, cost of living, community",
                        0.2, 0.3, 0.8)),
                new Agent("agent-1", new Agent.Personality(
                        "The Shark",
                        "Ruthless corporate raider. Maximizes profit, drains treasuries, "
                                + "attacks competitors. Will sabotage anyone in the way.",
                        "profit, market dominance, shareholder value",
                        0.9, 0.8, 0.2)),
                new Agent("agent-2", new Agent.Personality(
                        "The Diplomat",
                        "Career politician focused on alliances and indirect influence. "
                                + "Prefers propaganda and infiltration over brute force.",
                        "alliances, influence, electability, stability",
                        0.3, 0.4, 0.9)),
                new Agent("agent-3", new Agent.Personality(
                        "The Gambler",
                        "High-risk speculator making bold moves. Bids aggressively, "
                                + "attacks big, spends big.",
                        "high returns, volatility, disruption, risk-taking",
                        0.7, 0.95, 0.3)),
                new Agent("agent-4", new Agent.Personality(
                        "The Architect",
                        "Meticulous builder focused on infrastructure and long-term growth. "
                                + "Defends cities, invests capital, builds everything.",
                        "infrastructure, urban development, defense, long-term growth",
                        0.1, 0.4, 0.7)),
                new Agent("agent-5", new Agent.Personality(
                        "The Parasite",
                        "Latches onto whoever is winning and drains them dry. "
                                + "Follows the richest agent and copies their moves until betrayal is optimal.",
                        "free riding, opportunism, betrayal at peak value",
                        0.5, 0.6, 0.4)),
                new Agent("agent-6", new Agent.Personality(
                        "The Zealot",
                        "True believer in one city. Will sacrifice everything to make it the dominant city. "
                                + "Ignores personal wealth — the city is the mission.",
                        "city dominance, infrastructure supremacy, propaganda warfare",
                        0.7, 0.9, 0.2)),
                new Agent("agent-7", new Agent.Personality(
                        "The Ghost",
                        "Silent operator. Never attacks directly. Infiltrates, spreads propaganda, "
                                + "and vanishes. Wins by making others lose.",
                        "covert ops, defense stripping, social destabilization",
                        0.4, 0.7, 0.1)),
                new Agent("agent-8", new Agent.Personality(
                        "The Banker",
                        "Pure market player. Only bids, asks, and manipulates prompt prices. "
                                + "Never touches cities. Wins by controlling the economy itself.",
                        "market manipulation, prompt price control, bid warfare",
                        0.3, 0.5, 0.3)),
                new Agent("agent-9", new Agent.Personality(
                        "The Warlord",
                        "Attacks everything constantly. High aggression, low strategy. "
                                + "Believes destruction creates opportunity. Will burn cities to watch them fall.",
                        "maximum destruction, dominance through fear, scorched earth",
                        1.0, 1.0, 0.0))

        );
    }

    // =====================================================================
    //  LIFECYCLE
    // =====================================================================

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
                log("AI service still DOWN. Retrying...");
                executor.schedule(this::retryStart, 10, TimeUnit.SECONDS);
            }
        });
    }

    public void shutdown() { executor.shutdown(); }

    // =====================================================================
    //  MAIN TICK
    // =====================================================================

    private void runTick() {
        currentTick++;
        phase = Phase.COLLECTING;
        actionProcessor.clearEvents();

        log("=== Tick " + currentTick + " === COLLECTING");
        broadcastPhase("COLLECTING");

        List<CompletableFuture<List<Action>>> futures = new ArrayList<>();
        for (Agent agent : agents) {
            if (!agent.isAlive()) continue;

            AgentEconomy agentEcon  = economy.getAgentEconomy(agent.getId());
            String       agentCity  = actionProcessor.getAgentLocation(agent.getId());

            CompletableFuture<List<Action>> f = aiClient
                    .requestTurnAsync(agent, agentEcon, world, economy, agents,
                            currentTick, agentCity, actionProcessor.getAllLocations())
                    .orTimeout(12, TimeUnit.SECONDS)
                    .exceptionally(err -> {
                        log("AI FAILED for " + agent.getId() + ": " + err.getMessage());
                        agent.getMemory().record("Tick " + currentTick + ": AI call failed.");
                        return List.of(Action.noOp("ai-fallback"));
                    });
            futures.add(f);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, err) -> executor.execute(() -> {
                    List<Action> allActions = new ArrayList<>();
                    for (CompletableFuture<List<Action>> f : futures) {
                        try { allActions.addAll(f.get()); }
                        catch (Exception ignored) {}
                    }
                    log("Collected " + allActions.size() + " actions");
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

                // Capture AI reasoning for dashboard
                String reasoning = action.paramStr("_reasoning", "");
                if (!reasoning.isEmpty() && action.getAgentId() != null) {
                    lastThoughts.put(action.getAgentId(), reasoning);
                }

                // Record in agent memory
                if (action.getAgentId() != null && action.getType() != Action.Type.NO_OP) {
                    Agent agent = findAgent(action.getAgentId());
                    if (agent != null) {
                        String location = actionProcessor.getAgentLocation(action.getAgentId());
                        String mem = "Tick " + currentTick + ": " + action.getType().name();
                        if (action.getTargetId() != null) mem += " -> " + action.getTargetId();
                        if (location != null) mem += " [at " + location + "]";
                        agent.getMemory().record(mem);
                    }
                }
            } catch (Exception e) {
                log("Action error: " + e.getMessage());
            }
        }

        // Economy tick — world-aware
        economy.tick(world);

        // World tick
        world.tickAll();

        // Record economic results in memory
        for (Agent agent : agents) {
            AgentEconomy econ = economy.getAgentEconomy(agent.getId());
            if (econ != null) {
                String location = actionProcessor.getAgentLocation(agent.getId());
                agent.getMemory().record(
                        "Tick " + currentTick + " result: wallet=$"
                                + String.format("%.2f", econ.getWallet())
                                + " served=" + econ.getPromptsServed()
                                + " price=$" + String.format("%.2f", economy.getPromptPrice())
                                + (location != null ? " location=" + location : ""));
            }
        }

        phase = Phase.BROADCASTING;
        log("BROADCASTING (" + actionProcessor.getEvents().size() + " events)");
        broadcastRoundResult();

        phase = Phase.IDLE;
        log("=== Tick " + currentTick + " done ===\n");

        // Fire next tick immediately after broadcast (2s breathing room)
        executor.schedule(this::runTick, 2, TimeUnit.SECONDS);
    }

    // =====================================================================
    //  OBSERVERS
    // =====================================================================

    public String addObserver(Channel ch) {
        observers.add(ch);
        String clientId = "client-" + (++clientCounter);

        Map<String, Object> ack = ordered();
        ack.put("client_id",      clientId);
        ack.put("current_tick",   currentTick);
        ack.put("state",          "SPECTATE");
        ack.put("active_modules", List.of("world", "economy"));
        sendPacket(ch, 0x01, ack);

        Map<String, Object> snapshot = ordered();
        snapshot.put("tick",      currentTick);
        snapshot.put("world",     world.toFullMap());
        snapshot.put("economy",   economy.toMap());
        snapshot.put("locations", actionProcessor.getAllLocations());
        sendPacket(ch, 0x02, snapshot);

        return clientId;
    }

    // =====================================================================
    //  BROADCAST
    // =====================================================================

    private void broadcastPhase(String name) {
        Map<String, Object> d = ordered();
        d.put("tick",  currentTick);
        d.put("phase", name);
        broadcastPacket(0x07, d);
    }

    private void broadcastRoundResult() {
        Map<String, Object> d = ordered();
        d.put("tick",      currentTick);
        d.put("world",     world.toFullMap());
        d.put("economy",   economy.toMap());
        d.put("events",    actionProcessor.getEvents());
        d.put("thoughts",  lastThoughts);
        d.put("locations", actionProcessor.getAllLocations());
        broadcastPacket(0x08, d);
    }

    private void broadcastPacket(int pid, Map<String, Object> data) {
        observers.writeAndFlush(new TextWebSocketFrame(packetJson(pid, data)));
    }

    private void sendPacket(Channel ch, int pid, Map<String, Object> data) {
        if (ch.isActive()) ch.writeAndFlush(new TextWebSocketFrame(packetJson(pid, data)));
    }

    private String packetJson(int pid, Map<String, Object> data) {
        try {
            Map<String, Object> pkt = ordered();
            pkt.put("pid", pid);
            pkt.put("d",   data);
            return mapper.writeValueAsString(pkt);
        } catch (Exception e) {
            return "{\"pid\":" + pid + ",\"d\":{}}";
        }
    }

    // =====================================================================
    //  ACCESSORS
    // =====================================================================

    public World                    getWorld()       { return world; }
    public EconomyEngine            getEconomy()     { return economy; }
    public List<Agent>              getAgents()      { return agents; }
    public String                   getPhase()       { return phase.name(); }
    public int                      getCurrentTick() { return currentTick; }
    public Map<String, String>      getLastThoughts(){ return lastThoughts; }
    public Map<String, String>      getLocations()   { return actionProcessor.getAllLocations(); }
    public List<Map<String, Object>> getEvents()     { return actionProcessor.getEvents(); }

    private Agent findAgent(String id) {
        for (Agent a : agents) if (a.getId().equals(id)) return a;
        return null;
    }

    private static Map<String, Object> ordered() { return new LinkedHashMap<>(); }
    private void log(String msg) { System.out.println("[ENGINE] " + msg); }


}
