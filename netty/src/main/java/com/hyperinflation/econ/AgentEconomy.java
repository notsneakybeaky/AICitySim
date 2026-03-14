package com.hyperinflation.econ;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-agent economic state. Tracks wallet, allocation, and performance.
 */
public final class AgentEconomy {

    private final String agentId;
    private double wallet;
    private int allocatedPrompts;  // How many prompts this agent controls
    private int promptsServed;     // How many prompts served this tick
    private double totalEarnings;
    private double totalSpending;

    public AgentEconomy(String agentId, double startingWallet) {
        this.agentId          = agentId;
        this.wallet           = startingWallet;
        this.allocatedPrompts = 0;
        this.promptsServed    = 0;
        this.totalEarnings    = 0;
        this.totalSpending    = 0;
    }

    public void earn(double amount) {
        wallet += amount;
        totalEarnings += amount;
    }

    public boolean spend(double amount) {
        if (wallet < amount) return false;
        wallet -= amount;
        totalSpending += amount;
        return true;
    }

    public void payMaintenance() {
        double cost = EconomyConfig.AGENT_MAINTENANCE_COST;
        wallet -= cost;
        totalSpending += cost;
        // Wallet CAN go negative. Agent goes into debt.
    }

    // Getters & setters
    public String getAgentId()           { return agentId; }
    public double getWallet()            { return wallet; }
    public int    getAllocatedPrompts()   { return allocatedPrompts; }
    public void   setAllocatedPrompts(int n) { this.allocatedPrompts = n; }
    public int    getPromptsServed()     { return promptsServed; }
    public void   setPromptsServed(int n) { this.promptsServed = n; }
    public double getTotalEarnings()     { return totalEarnings; }
    public double getTotalSpending()     { return totalSpending; }
    public double getNetWorth()          { return wallet; }
    public boolean isInDebt()            { return wallet < 0; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agent_id",           agentId);
        m.put("wallet",             Math.round(wallet * 100.0) / 100.0);
        m.put("allocated_prompts",  allocatedPrompts);
        m.put("prompts_served",     promptsServed);
        m.put("total_earnings",     Math.round(totalEarnings * 100.0) / 100.0);
        m.put("total_spending",     Math.round(totalSpending * 100.0) / 100.0);
        m.put("in_debt",            isInDebt());
        return m;
    }
}