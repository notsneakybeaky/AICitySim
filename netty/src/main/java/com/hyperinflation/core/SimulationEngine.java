package com.hyperinflation.core;

import com.hyperinflation.econ.EconomyEngine;
import com.hyperinflation.net.ConnectionManager;
import com.hyperinflation.net.protocol.s2c.S2CPhaseChange;
import com.hyperinflation.net.protocol.s2c.S2CRoundResult;
import com.hyperinflation.world.World;

import java.util.*;
import java.util.concurrent.*;

/**
 * The main simulation loop. Runs on a single thread.
 * Each tick:
 *   1. Collects actions from all active modules.
 *   2. Processes actions against the world.
 *   3. Runs the economy engine.
 *   4. Broadcasts the new state to all connected clients.
 */
public final class SimulationEngine {

    public enum Phase { IDLE, COLLECTING, PROCESSING, BROADCASTING }

    private static final long DEFAULT_TICK_MS = 25_000;

    private final World world;
    private final ModuleRegistry registry;
    private final EconomyEngine economyEngine;
    private final ConnectionManager connectionManager;
    private final ActionProcessor actionProcessor;

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sim-engine");
                t.setDaemon(true);
                return t;
            });

    private int currentTick = 0;
    private Phase phase = Phase.IDLE;
    private long tickIntervalMs = DEFAULT_TICK_MS;
    private volatile boolean running = false;

    // Event log for the current tick
    private final List<Map<String, Object>> eventLog = new CopyOnWriteArrayList<>();

    public SimulationEngine(World world, ModuleRegistry registry,
                            EconomyEngine economyEngine,
                            ConnectionManager connectionManager) {
        this.world             = world;
        this.registry          = registry;
        this.economyEngine     = economyEngine;
        this.connectionManager = connectionManager;
        this.actionProcessor   = new ActionProcessor(world, economyEngine, eventLog);
    }

    // =====================================================================
    //  LIFECYCLE
    // =====================================================================

    public void start() {
        if (running) return;
        running = true;
        System.out.println("[ENGINE] Starting simulation. Tick interval: " + tickIntervalMs + "ms");
        executor.schedule(this::runTick, 5000, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        System.out.println("[ENGINE] Simulation stopped.");
    }

    public void shutdown() {
        running = false;
        executor.shutdown();
    }

    public void setTickInterval(long ms) {
        this.tickIntervalMs = Math.max(5000, ms); // Minimum 5s
    }

    // =====================================================================
    //  THE MAIN TICK
    // =====================================================================

    private void runTick() {
        if (!running) return;

        currentTick++;
        eventLog.clear();
        System.out.println("\n[ENGINE] === Tick " + currentTick + " ===");

        // Phase 1: Collect actions from all active modules
        phase = Phase.COLLECTING;
        broadcastPhase();

        List<SimulationModule> modules = registry.getActiveModules();
        List<CompletableFuture<List<Action>>> futures = new ArrayList<>();

        for (SimulationModule module : modules) {
            CompletableFuture<List<Action>> f = module.tick(world, currentTick)
                    .exceptionally(err -> {
                        System.err.println("[ENGINE] Module " + module.getModuleId()
                                + " tick failed: " + err.getMessage());
                        return List.of();
                    });
            futures.add(f);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, err) -> executor.execute(() -> {
                    // Collect all actions
                    List<Action> allActions = new ArrayList<>();
                    for (CompletableFuture<List<Action>> f : futures) {
                        try {
                            List<Action> actions = f.get();
                            if (actions != null) allActions.addAll(actions);
                        } catch (Exception ignored) {}
                    }

                    System.out.println("[ENGINE] Collected " + allActions.size()
                            + " actions from " + modules.size() + " modules.");

                    // Phase 2: Process all actions
                    phase = Phase.PROCESSING;
                    broadcastPhase();
                    actionProcessor.processAll(allActions);

                    // Phase 3: Run economy tick
                    economyEngine.tick(world, currentTick);

                    // Phase 4: Broadcast results
                    phase = Phase.BROADCASTING;
                    broadcastTickResult();

                    // Schedule next tick
                    phase = Phase.IDLE;
                    if (running) {
                        executor.schedule(this::runTick, tickIntervalMs, TimeUnit.MILLISECONDS);
                    }

                    System.out.println("[ENGINE] === Tick " + currentTick + " complete ===");
                }));
    }

    // =====================================================================
    //  BROADCASTING
    // =====================================================================

    private void broadcastPhase() {
        S2CPhaseChange packet = new S2CPhaseChange(currentTick, phase.name(), world.toSummaryMap());
        connectionManager.broadcastPacket(packet);
    }

    private void broadcastTickResult() {
        S2CRoundResult packet = new S2CRoundResult(
                currentTick,
                world.toFullMap(),
                economyEngine.toMap(),
                eventLog
        );
        connectionManager.broadcastPacket(packet);
    }

    // =====================================================================
    //  ACCESSORS
    // =====================================================================

    public int     getCurrentTick()   { return currentTick; }
    public Phase   getPhase()         { return phase; }
    public World   getWorld()         { return world; }
    public boolean isRunning()        { return running; }
    public long    getTickInterval()  { return tickIntervalMs; }
    public ModuleRegistry getRegistry() { return registry; }
    public List<Map<String, Object>> getEventLog() { return Collections.unmodifiableList(eventLog); }
}