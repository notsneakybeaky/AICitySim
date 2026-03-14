package com.hyperinflation.core;

import com.hyperinflation.econ.EconomyEngine;
import com.hyperinflation.world.City;
import com.hyperinflation.world.World;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes a list of Actions against the World and EconomyEngine.
 * This is where actions have real effects.
 */
public final class ActionProcessor {

    private final World world;
    private final EconomyEngine economy;
    private final List<Map<String, Object>> eventLog;

    public ActionProcessor(World world, EconomyEngine economy,
                           List<Map<String, Object>> eventLog) {
        this.world    = world;
        this.economy  = economy;
        this.eventLog = eventLog;
    }

    public void processAll(List<Action> actions) {
        for (Action action : actions) {
            try {
                process(action);
            } catch (Exception e) {
                System.err.println("[PROCESSOR] Failed to process action "
                        + action.getType() + ": " + e.getMessage());
            }
        }
    }

    private void process(Action action) {
        switch (action.getType()) {

            // ---- Economic actions ----
            case PLACE_BID -> {
                double price = action.getParamDouble("price", 0);
                int quantity = (int) action.getParamDouble("quantity", 1);
                economy.getPromptMarket().placeBid(
                        action.getSourceAgentId(), price, quantity);
                logEvent(action, "Placed bid: " + quantity + " @ " + price);
            }
            case PLACE_ASK -> {
                double price = action.getParamDouble("price", 0);
                int quantity = (int) action.getParamDouble("quantity", 1);
                economy.getPromptMarket().placeAsk(
                        action.getSourceAgentId(), price, quantity);
                logEvent(action, "Placed ask: " + quantity + " @ " + price);
            }
            case DRAIN_TREASURY -> {
                City city = world.getCity(action.getTargetId());
                if (city != null) {
                    double amount = action.getParamDouble("amount", 0);
                    double drained = city.drainTreasury(amount);
                    logEvent(action, "Drained " + drained + " from " + city.getName());
                }
            }
            case INJECT_CAPITAL -> {
                City city = world.getCity(action.getTargetId());
                if (city != null) {
                    double amount = action.getParamDouble("amount", 0);
                    city.injectCapital(amount);
                    logEvent(action, "Injected " + amount + " into " + city.getName());
                }
            }

            // ---- City actions ----
            case BUILD_INFRASTRUCTURE -> {
                City city = world.getCity(action.getTargetId());
                if (city != null) {
                    double amount = action.getParamDouble("amount", 5);
                    city.modifyInfrastructure(amount);
                    logEvent(action, city.getName() + " infrastructure +" + amount);
                }
            }
            case DAMAGE_INFRASTRUCTURE -> {
                City city = world.getCity(action.getTargetId());
                if (city != null) {
                    double amount = action.getParamDouble("amount", 5);
                    city.modifyInfrastructure(-amount);
                    logEvent(action, city.getName() + " infrastructure -" + amount);
                }
            }
            case BOOST_HAPPINESS -> {
                City city = world.getCity(action.getTargetId());
                if (city != null) {
                    double amount = action.getParamDouble("amount", 5);
                    city.modifyHappiness(amount);
                    logEvent(action, city.getName() + " happiness +" + amount);
                }
            }
            case DAMAGE_HAPPINESS -> {
                City city = world.getCity(action.getTargetId());
                if (city != null) {
                    double amount = action.getParamDouble("amount", 5);
                    city.modifyHappiness(-amount);
                    logEvent(action, city.getName() + " happiness -" + amount);
                }
            }

            // ---- Agent actions ----
            case AGENT_EARN -> {
                double amount = action.getParamDouble("amount", 0);
                logEvent(action, action.getSourceAgentId() + " earned " + amount);
            }
            case AGENT_SPEND -> {
                double amount = action.getParamDouble("amount", 0);
                logEvent(action, action.getSourceAgentId() + " spent " + amount);
            }

            // ---- Conflict ----
            case ATTACK_CITY -> {
                City city = world.getCity(action.getTargetId());
                if (city != null) {
                    double power = action.getParamDouble("power", 10);
                    city.modifyInfrastructure(-power * 0.5);
                    city.modifyHappiness(-power * 0.3);
                    logEvent(action, action.getSourceAgentId()
                            + " attacked " + city.getName() + " (power=" + power + ")");
                }
            }
            case INFILTRATE -> {
                City city = world.getCity(action.getTargetId());
                if (city != null) {
                    double amount = action.getParamDouble("amount", 3);
                    city.modifyDefenses(-amount);
                    logEvent(action, action.getSourceAgentId()
                            + " infiltrated " + city.getName());
                }
            }
            case SPREAD_PROPAGANDA -> {
                City city = world.getCity(action.getTargetId());
                if (city != null) {
                    double amount = action.getParamDouble("amount", 5);
                    city.modifySocialCohesion(-amount);
                    city.modifyHappiness(-amount * 0.5);
                    logEvent(action, "Propaganda spread in " + city.getName());
                }
            }

            // ---- Meta ----
            case CHANGE_MODULE -> {
                String targetModule = action.getParamString("module_id", "");
                logEvent(action, "Module switch requested: " + targetModule);
                // Handled by the engine, not here
            }

            case NO_OP -> {} // Do nothing

            default -> System.out.println("[PROCESSOR] Unhandled action type: " + action.getType());
        }
    }

    private void logEvent(Action action, String description) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tick",        0); // Will be set by the engine
        entry.put("type",        action.getType().name());
        entry.put("source",      action.getSourceModuleId());
        entry.put("agent",       action.getSourceAgentId());
        entry.put("target",      action.getTargetId());
        entry.put("description", description);
        entry.put("ts",          System.currentTimeMillis());
        eventLog.add(entry);
        System.out.println("[EVENT] " + description);
    }
}