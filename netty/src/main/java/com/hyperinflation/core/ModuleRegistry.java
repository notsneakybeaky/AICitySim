package com.hyperinflation.core;

import com.hyperinflation.world.World;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for hot-swapping simulation modules at runtime.
 * Thread-safe. Can be called from the HTTP API handler or WebSocket.
 */
public final class ModuleRegistry {

    private final List<SimulationModule> activeModules = new CopyOnWriteArrayList<>();
    private final Map<String, SimulationModule> catalog = new ConcurrentHashMap<>();
    private final World world;

    public ModuleRegistry(World world) {
        this.world = world;
    }

    /** Register a module in the catalog (makes it available but not active). */
    public void register(SimulationModule module) {
        catalog.put(module.getModuleId(), module);
        System.out.println("[REGISTRY] Registered module: " + module.getModuleId());
    }

    /** Activate a module by its ID. It starts receiving ticks. */
    public boolean activate(String moduleId) {
        SimulationModule module = catalog.get(moduleId);
        if (module == null) {
            System.out.println("[REGISTRY] Module not found: " + moduleId);
            return false;
        }
        if (activeModules.contains(module)) {
            System.out.println("[REGISTRY] Module already active: " + moduleId);
            return false;
        }
        module.onLoad(world);
        activeModules.add(module);
        System.out.println("[REGISTRY] Activated module: " + moduleId);
        return true;
    }

    /** Deactivate a module. It stops receiving ticks. */
    public boolean deactivate(String moduleId) {
        SimulationModule module = catalog.get(moduleId);
        if (module == null || !activeModules.contains(module)) return false;
        module.onUnload(world);
        activeModules.remove(module);
        System.out.println("[REGISTRY] Deactivated module: " + moduleId);
        return true;
    }

    /** Swap: deactivate all, then activate only the specified module. */
    public boolean switchTo(String moduleId) {
        // Deactivate all current modules
        for (SimulationModule m : activeModules) {
            m.onUnload(world);
            System.out.println("[REGISTRY] Unloaded module: " + m.getModuleId());
        }
        activeModules.clear();

        // Activate the new one
        return activate(moduleId);
    }

    /** Get all currently active modules. */
    public List<SimulationModule> getActiveModules() {
        return Collections.unmodifiableList(activeModules);
    }

    /** Get all registered module IDs. */
    public List<String> getRegisteredIds() {
        return List.copyOf(catalog.keySet());
    }

    /** Get all active module IDs. */
    public List<String> getActiveIds() {
        return activeModules.stream().map(SimulationModule::getModuleId).toList();
    }
}