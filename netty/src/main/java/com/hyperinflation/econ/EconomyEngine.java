package com.hyperinflation.econ;

import com.hyperinflation.world.World;

import java.util.*;

/**
 * ECONOMY ENGINE
 * ==============
 *
 * SUPPLY is now driven by city infrastructure:
 *   totalSupply = base + sum(city.supplyContribution)
 *   High infrastructure cities produce more prompt supply capacity.
 *
 * DEMAND is now driven by city happiness + social cohesion:
 *   totalDemand = base * avgDemandMultiplier * gdpBonus
 *   Happy, cohesive cities buy more prompts.
 *   Low cohesion INVERTS happiness → demand suppression even in "happy" cities.
 *
 * PRICE floats based on supply/demand ratio as before.
 * TOTAL_PROMPT_VALUE grows with world GDP — the pie gets bigger as cities prosper.
 */
public final class EconomyEngine {

    public static double TOTAL_PROMPT_VALUE = 1000.0;

    private double promptPrice   = EconomyConfig.INITIAL_PROMPT_PRICE;
    private int    totalSupply   = EconomyConfig.TOTAL_PROMPT_SUPPLY;
    private int    currentDemand = 0;
    private int    openOrders    = 0;
    private final Random rng     = new Random();

    private final Map<String, AgentEconomy> agentEconomies = new LinkedHashMap<>();
    private final Map<String, double[]>     bids           = new LinkedHashMap<>();

    public void registerAgent(String id, double startingWallet) {
        agentEconomies.put(id, new AgentEconomy(id, startingWallet));
    }

    public AgentEconomy getAgentEconomy(String id) {
        return agentEconomies.get(id);
    }

    public void placeBid(String agentId, double price, int quantity) {
        bids.put(agentId, new double[]{price, quantity});
    }

    /**
     * Run one tick of the prompt economy.
     * World is passed in so supply and demand can be derived from city stats.
     */
    public void tick(World world) {

        // 1. Supply = base + infrastructure contribution from all cities
        double infraSupply   = world.getTotalSupplyContribution();
        int    baseSupply    = EconomyConfig.TOTAL_PROMPT_SUPPLY;
        int    worldSupply   = baseSupply + (int) infraSupply;

        // 2. Demand = base scaled by world happiness/cohesion and GDP
        double demandMult    = world.getAverageDemandMultiplier(); // 0.5 to 1.2
        double gdpBonus      = 1.0 + (world.getTotalEconomicOutput() / 500.0); // GDP expands market
        int    baseDemand    = (int) (TOTAL_PROMPT_VALUE / Math.max(0.01, promptPrice));
        int    noise         = rng.nextInt(Math.max(1, baseDemand / 5)) - baseDemand / 10;
        currentDemand        = Math.max(10, (int) (baseDemand * demandMult * gdpBonus) + noise);

        // 3. Total supply from agent bids (agents provide capacity on top of world supply)
        int    totalBidQty   = 0;
        double totalBidVal   = 0;
        for (double[] bid : bids.values()) {
            totalBidQty += (int) bid[1];
            totalBidVal += bid[0] * bid[1];
        }
        totalSupply = Math.max(1, worldSupply + totalBidQty);

        // 4. Allocate prompts to agents
        if (bids.isEmpty()) {
            int perAgent = currentDemand / Math.max(1, agentEconomies.size());
            for (AgentEconomy econ : agentEconomies.values()) {
                econ.setAllocatedPrompts(perAgent);
                econ.setPromptsServed(perAgent);
                econ.earn(perAgent * promptPrice * 0.8);
            }
        } else {
            for (Map.Entry<String, double[]> bid : bids.entrySet()) {
                AgentEconomy econ = agentEconomies.get(bid.getKey());
                if (econ == null) continue;

                double bidPrice  = bid.getValue()[0];
                int    bidQty    = (int) bid.getValue()[1];
                double share     = totalBidVal > 0 ? (bidPrice * bidQty) / totalBidVal : 0;
                int    allocated = (int) (currentDemand * share);
                int    served    = Math.min(allocated, bidQty);

                econ.setAllocatedPrompts(allocated);
                econ.setPromptsServed(served);
                econ.earn(served * promptPrice);
                econ.forceSpend(bidPrice * bidQty * 0.1);
            }

            for (Map.Entry<String, AgentEconomy> e : agentEconomies.entrySet()) {
                if (!bids.containsKey(e.getKey())) {
                    e.getValue().setAllocatedPrompts(0);
                    e.getValue().setPromptsServed(0);
                }
            }
        }

        // 5. Update price
        double ratio      = (double) currentDemand / Math.max(1, totalSupply);
        double priceShift = (ratio - 1.0) * 0.05 + rng.nextGaussian() * 0.01;
        promptPrice       = Math.max(0.01, promptPrice * (1.0 + priceShift));

        openOrders = bids.size();
        bids.clear();
    }

    /** Legacy tick with no world data — used as fallback. */
    public void tick() {
        int baseDemand = (int) (TOTAL_PROMPT_VALUE / Math.max(0.01, promptPrice));
        int noise      = rng.nextInt(Math.max(1, baseDemand / 5)) - baseDemand / 10;
        currentDemand  = Math.max(10, baseDemand + noise);
        totalSupply    = Math.max(1, EconomyConfig.TOTAL_PROMPT_SUPPLY);

        double ratio      = (double) currentDemand / Math.max(1, totalSupply);
        double priceShift = (ratio - 1.0) * 0.05 + rng.nextGaussian() * 0.01;
        promptPrice       = Math.max(0.01, promptPrice * (1.0 + priceShift));

        openOrders = bids.size();
        bids.clear();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();

        Map<String, Object> market = new LinkedHashMap<>();
        market.put("price",              Math.round(promptPrice * 100.0) / 100.0);
        market.put("total_supply",       totalSupply);
        market.put("current_demand",     currentDemand);
        market.put("total_market_value", TOTAL_PROMPT_VALUE);
        market.put("open_orders",        openOrders);
        m.put("market", market);

        Map<String, Object> agents = new LinkedHashMap<>();
        for (Map.Entry<String, AgentEconomy> e : agentEconomies.entrySet()) {
            agents.put(e.getKey(), e.getValue().toMap());
        }
        m.put("agents", agents);

        return m;
    }

    public double getPromptPrice()   { return promptPrice; }
    public int    getTotalSupply()   { return totalSupply; }
    public int    getCurrentDemand() { return currentDemand; }
}
