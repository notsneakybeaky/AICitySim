package com.hyperinflation.econ;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-agent economic state. Single authoritative class — the inner class
 * that used to live in EconomyEngine has been removed.
 */
public final class AgentEconomy {

    private final String agentId;
    private double wallet;
    private int    allocatedPrompts;
    private int    promptsServed;
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
        wallet        += amount;
        totalEarnings += amount;
    }

    /**
     * Attempt to spend. Returns false and does NOT deduct if wallet is insufficient.
     * Use forceSpend when the deduction must happen regardless (e.g. bid costs, maintenance).
     */
    public boolean spend(double amount) {
        if (wallet < amount) return false;
        wallet        -= amount;
        totalSpending += amount;
        return true;
    }

    /**
     * Deduct unconditionally — wallet may go negative (debt).
     * Used for bid costs and maintenance.
     */
    public void forceSpend(double amount) {
        wallet        -= amount;
        totalSpending += amount;
    }

    /** Per-tick maintenance. Wallet CAN go negative. Agent enters debt. */
    public void payMaintenance() {
        forceSpend(EconomyConfig.AGENT_MAINTENANCE_COST);
    }

    // ---- Getters / setters ----
    public String  getAgentId()          { return agentId; }
    public double  getWallet()           { return wallet; }
    public boolean isInDebt()            { return wallet < 0; }
    public int     getAllocatedPrompts()  { return allocatedPrompts; }
    public void    setAllocatedPrompts(int n) { this.allocatedPrompts = n; }
    public int     getPromptsServed()    { return promptsServed; }
    public void    setPromptsServed(int n)    { this.promptsServed = n; }
    public double  getTotalEarnings()    { return totalEarnings; }
    public double  getTotalSpending()    { return totalSpending; }
    public double  getNetWorth()         { return wallet; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agent_id",          agentId);
        m.put("wallet",            r2(wallet));
        m.put("allocated_prompts", allocatedPrompts);
        m.put("prompts_served",    promptsServed);
        m.put("total_earnings",    r2(totalEarnings));
        m.put("total_spending",    r2(totalSpending));
        m.put("in_debt",           isInDebt());
        return m;
    }

    private static double r2(double v) { return Math.round(v * 100.0) / 100.0; }
}
