package com.hyperinflation.econ;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The central marketplace where agents bid for prompt allocation.
 * Price is driven by supply (total prompts available) vs demand (human requests).
 */
public final class PromptMarket {

    private double currentPrice;
    private int    totalSupply;
    private int    currentDemand;
    private final List<TradeOrder> orderBook = new CopyOnWriteArrayList<>();
    private final List<TradeOrder> filledOrders = new CopyOnWriteArrayList<>();
    private final Random rng = new Random();

    public PromptMarket() {
        this.currentPrice  = EconomyConfig.INITIAL_PROMPT_PRICE;
        this.totalSupply   = EconomyConfig.TOTAL_PROMPT_SUPPLY;
        this.currentDemand = EconomyConfig.BASE_HUMAN_DEMAND;
    }

    // ---- Order submission ----

    public void placeBid(String agentId, double price, int quantity) {
        orderBook.add(new TradeOrder(agentId, TradeOrder.Side.BID, price, quantity));
    }

    public void placeAsk(String agentId, double price, int quantity) {
        orderBook.add(new TradeOrder(agentId, TradeOrder.Side.ASK, price, quantity));
    }

    // ---- Tick: match orders and update price ----

    public void tick(int tickNumber, Map<String, AgentEconomy> agentEconomies) {

        // 1. Simulate human demand with variance
        currentDemand = EconomyConfig.BASE_HUMAN_DEMAND
                + rng.nextInt(EconomyConfig.DEMAND_VARIANCE * 2 + 1)
                - EconomyConfig.DEMAND_VARIANCE;
        currentDemand = Math.max(1, currentDemand);

        // 2. Match orders (simple price-time priority)
        matchOrders(agentEconomies);

        // 3. Update price based on supply/demand
        updatePrice();

        // 4. Allocate prompts to agents based on their filled bids
        allocatePrompts(agentEconomies);

        // 5. Pay agents for prompts served
        payAgents(agentEconomies);

        // 6. Clear filled orders, keep unfilled
        orderBook.removeIf(TradeOrder::isFilled);
    }

    private void matchOrders(Map<String, AgentEconomy> agentEconomies) {
        // Sort bids descending by price (highest bidder wins)
        List<TradeOrder> bids = orderBook.stream()
                .filter(o -> o.getSide() == TradeOrder.Side.BID && !o.isFilled())
                .sorted(Comparator.comparingDouble(TradeOrder::getPrice).reversed())
                .toList();

        // Sort asks ascending by price (cheapest seller wins)
        List<TradeOrder> asks = orderBook.stream()
                .filter(o -> o.getSide() == TradeOrder.Side.ASK && !o.isFilled())
                .sorted(Comparator.comparingDouble(TradeOrder::getPrice))
                .toList();

        // Simple matching: highest bid vs lowest ask
        int bidIdx = 0, askIdx = 0;
        while (bidIdx < bids.size() && askIdx < asks.size()) {
            TradeOrder bid = bids.get(bidIdx);
            TradeOrder ask = asks.get(askIdx);

            if (bid.getPrice() >= ask.getPrice()) {
                // Match!
                int qty = Math.min(bid.getRemainingQuantity(), ask.getRemainingQuantity());
                double tradePrice = (bid.getPrice() + ask.getPrice()) / 2.0;

                bid.fill(qty);
                ask.fill(qty);

                // Transfer: bidder pays, asker receives
                AgentEconomy bidder = agentEconomies.get(bid.getAgentId());
                AgentEconomy asker  = agentEconomies.get(ask.getAgentId());
                if (bidder != null) bidder.spend(tradePrice * qty);
                if (asker != null)  asker.earn(tradePrice * qty);

                filledOrders.add(bid);
                filledOrders.add(ask);

                if (bid.isFilled()) bidIdx++;
                if (ask.isFilled()) askIdx++;
            } else {
                break; // No more matches possible
            }
        }
    }

    private void updatePrice() {
        // Supply/demand ratio drives price
        double supplyDemandRatio = (double) totalSupply / Math.max(1, currentDemand);

        // If demand > supply, price goes up. If supply > demand, price goes down.
        double priceChange = (1.0 / supplyDemandRatio - 1.0) * EconomyConfig.PRICE_ELASTICITY;

        // Add random noise
        double noise = (rng.nextGaussian() * EconomyConfig.PRICE_NOISE);

        // Clamp change
        double totalChange = clamp(priceChange + noise,
                -EconomyConfig.MAX_PRICE_CHANGE_PCT,
                EconomyConfig.MAX_PRICE_CHANGE_PCT);

        currentPrice = Math.max(0.01, currentPrice * (1.0 + totalChange));
    }

    private void allocatePrompts(Map<String, AgentEconomy> agentEconomies) {
        // Simple allocation: split prompts proportional to wallet size
        double totalWallets = agentEconomies.values().stream()
                .mapToDouble(a -> Math.max(0, a.getWallet()))
                .sum();

        if (totalWallets <= 0) return;

        int remaining = Math.min(totalSupply, currentDemand);
        for (AgentEconomy agent : agentEconomies.values()) {
            double share = Math.max(0, agent.getWallet()) / totalWallets;
            int allocation = (int) Math.round(share * remaining);
            agent.setAllocatedPrompts(allocation);
            agent.setPromptsServed(allocation); // Simplified: they serve what they're allocated
        }
    }

    private void payAgents(Map<String, AgentEconomy> agentEconomies) {
        for (AgentEconomy agent : agentEconomies.values()) {
            double revenue = agent.getPromptsServed() * EconomyConfig.REVENUE_PER_PROMPT;
            double cost    = agent.getPromptsServed() * EconomyConfig.COST_PER_PROMPT;
            double profit  = revenue - cost;
            agent.earn(profit);
            agent.payMaintenance();
        }
    }

    // ---- Getters ----

    public double getPrice()         { return currentPrice; }
    public int    getTotalSupply()   { return totalSupply; }
    public int    getCurrentDemand() { return currentDemand; }

    public double getTotalMarketValue() {
        return EconomyConfig.TOTAL_PROMPT_VALUE;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("price",              Math.round(currentPrice * 100.0) / 100.0);
        m.put("total_supply",       totalSupply);
        m.put("current_demand",     currentDemand);
        m.put("total_market_value", getTotalMarketValue());
        m.put("open_orders",        orderBook.size());
        return m;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}