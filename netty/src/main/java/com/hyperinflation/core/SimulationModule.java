package com.hyperinflation.core;

import com.hyperinflation.world.World;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Any pluggable logic that acts on the world implements this.
 * Modules are hot-swappable at runtime via ModuleRegistry.
 */
public interface SimulationModule {

    /** Unique ID for this module instance. */
    String getModuleId();

    /** Human-readable name for the UI. */
    String getDisplayName();

    /** Called once when the module is first loaded. */
    default void onLoad(World world) {}

    /** Called once when the module is unloaded/swapped out. */
    default void onUnload(World world) {}

    /**
     * Called every simulation tick. The module inspects the world
     * and returns a list of Actions it wants to perform.
     */
    CompletableFuture<List<Action>> tick(World world, int currentTick);

    /** Whether this module is currently active. */
    default boolean isActive() { return true; }
}