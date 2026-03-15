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

    private final Map<String, String> lastThoughts = new LinkedHashMap<>();

    private final ChannelGroup observers =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private int clientCounter = 0;

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

        for (int i = 0; i < agents.size(); i++) {
            Agent a = agents.get(i);
            economy.registerAgent(a.getId(), 100.0);
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
                        0.1, 0.4, 0.7))
        );
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

        // Sequential chaining — one agent at a time to avoid Gemini 429 rate limits.
        // thenCompose builds an async queue: agent N+1 only starts after agent N finishes.
        CompletableFuture<List<Action>> sequentialChain =
                CompletableFuture.completedFuture(new ArrayList<>());

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
                                    agent.getMemory().record("Tick " + currentTick + ": AI call failed.");
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

        economy.tick(world);
        world.tickAll();

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
        executor.schedule(this::runTick, 2, TimeUnit.SECONDS);
    }

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

    public World                     getWorld()       { return world; }
    public EconomyEngine             getEconomy()     { return economy; }
    public List<Agent>               getAgents()      { return agents; }
    public String                    getPhase()       { return phase.name(); }
    public int                       getCurrentTick() { return currentTick; }
    public Map<String, String>       getLastThoughts(){ return lastThoughts; }
    public Map<String, String>       getLocations()   { return actionProcessor.getAllLocations(); }
    public List<Map<String, Object>> getEvents()      { return actionProcessor.getEvents(); }

    private Agent findAgent(String id) {
        for (Agent a : agents) if (a.getId().equals(id)) return a;
        return null;
    }

    private static Map<String, Object> ordered() { return new LinkedHashMap<>(); }
    private void log(String msg) { System.out.println("[ENGINE] " + msg); }
}