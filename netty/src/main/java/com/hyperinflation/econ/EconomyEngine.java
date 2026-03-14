package com.hyperinflation.econ;

import java.util.*;

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

    /** Run one tick of the prompt economy. */
    public void tick() {
        // 1. Simulate demand (humans prompting)
        int baseDemand = (int) (TOTAL_PROMPT_VALUE / Math.max(0.01, promptPrice));
        int noise      = rng.nextInt(Math.max(1, baseDemand / 5)) - baseDemand / 10;
        currentDemand  = Math.max(10, baseDemand + noise);

        // 2. Total supply from bids
        int    totalBidQty = 0;
        double totalBidVal = 0;
        for (double[] bid : bids.values()) {
            totalBidQty += (int) bid[1];
            totalBidVal += bid[0] * bid[1];
        }
        totalSupply = Math.max(1, totalBidQty);

        // 3. Allocate prompts
        if (bids.isEmpty()) {
            // Default: split evenly
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
                // 10% bid cost — force spend even if wallet is low (bid is a commitment)
                econ.forceSpend(bidPrice * bidQty * 0.1);
            }

            // Agents who didn't bid get nothing this tick
            for (Map.Entry<String, AgentEconomy> e : agentEconomies.entrySet()) {
                if (!bids.containsKey(e.getKey())) {
                    e.getValue().setAllocatedPrompts(0);
                    e.getValue().setPromptsServed(0);
                }
            }
        }

        // 4. Update price
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
