package com.hyperinflation.econ;

/**
 * All adjustable economic constants in one place.
 * Change these to tune the entire economy.
 */
public final class EconomyConfig {

    // ---- The Big Number ----
    /** Total prompt value in the economy. The pie everyone fights over. */
    public static double TOTAL_PROMPT_VALUE = 1000.0;

    /** Starting price per prompt. */
    public static double INITIAL_PROMPT_PRICE = 1.0;

    /** How many total prompts exist in the system per tick. */
    public static int TOTAL_PROMPT_SUPPLY = 1000;

    // ---- Market dynamics ----
    /** Price elasticity: how much supply/demand imbalance affects price. */
    public static double PRICE_ELASTICITY = 0.15;

    /** Maximum price swing per tick (prevents flash crashes). */
    public static double MAX_PRICE_CHANGE_PCT = 0.25; // 25%

    /** Random noise amplitude on price each tick. */
    public static double PRICE_NOISE = 0.03;

    /** Base demand from human prompting (simulated). */
    public static int BASE_HUMAN_DEMAND = 800;

    /** Demand variance per tick. */
    public static int DEMAND_VARIANCE = 200;

    // ---- Agent economics ----
    /** Starting wallet for each agent. */
    public static double AGENT_STARTING_WALLET = 100.0;

    /** Revenue per prompt served. */
    public static double REVENUE_PER_PROMPT = 1.0;

    /** Cost per prompt served (overhead). */
    public static double COST_PER_PROMPT = 0.3;

    // ---- Decay / maintenance ----
    /** Per-tick maintenance cost for agents (keeps them hungry). */
    public static double AGENT_MAINTENANCE_COST = 2.0;

    private EconomyConfig() {}
}