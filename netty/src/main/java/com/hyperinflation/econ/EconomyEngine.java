package com.hyperinflation.econ;

import com.hyperinflation.world.City;
import com.hyperinflation.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the economy each simulation tick.
 * Manages the prompt market, agent economies, and price history.
 */
public final class EconomyEngine {

    private final PromptMarket promptMarket;
    private final Map<String, AgentEconomy> agentEconomies = new ConcurrentHashMap<>();
    private final PriceHistory priceHistory;

    public EconomyEngine() {
        this.promptMarket = new PromptMarket();
        this.priceHistory = new PriceHistory(500); // Keep last 500 ticks
    }

    /** Register an agent in the economy. Call once per agent at startup. */
    public void registerAgent(String agentId) {
        agentEconomies.put(agentId, new AgentEconomy(
                agentId, EconomyConfig.AGENT_STARTING_WALLET));
    }

    /** Main economy tick. Called by SimulationEngine each tick. */
    public void tick(World world, int tickNumber) {
        // 1. Run the prompt market
        promptMarket.tick(tickNumber, agentEconomies);

        // 2. Record price history
        priceHistory.record(tickNumber, promptMarket.getPrice(),
                promptMarket.getCurrentDemand());

        // 3. City economies contribute to the total prompt value
        // Richer cities increase demand
        double cityWealth = 0;
        for (City city : world.getAllCities()) {
            cityWealth += city.getTreasury();
        }

        // Adjust total prompt value based on city wealth (dynamic economy)
        // Base value + city contribution
        double dynamicValue = EconomyConfig.TOTAL_PROMPT_VALUE
                + (cityWealth * 0.01);
        // This doesn't mutate the config, just affects this tick's calculations

        System.out.println("[ECON] Tick " + tickNumber
                + " | Price: " + String.format("%.2f", promptMarket.getPrice())
                + " | Demand: " + promptMarket.getCurrentDemand()
                + " | Agents: " + agentEconomies.size());
    }

    // ---- Accessors ----

    public PromptMarket getPromptMarket() { return promptMarket; }

    public AgentEconomy getAgentEconomy(String agentId) {
        return agentEconomies.get(agentId);
    }

    public Map<String, AgentEconomy> getAllAgentEconomies() {
        return Collections.unmodifiableMap(agentEconomies);
    }

    public PriceHistory getPriceHistory() { return priceHistory; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("market", promptMarket.toMap());
        m.put("price_history", priceHistory.toList());

        Map<String, Object> agents = new LinkedHashMap<>();
        for (AgentEconomy ae : agentEconomies.values()) {
            agents.put(ae.getAgentId(), ae.toMap());
        }
        m.put("agents", agents);
        return m;
    }
}