package com.hyperinflation.agent;

/**
 * Predefined agent personality configurations.
 */
public final class AgentPersonality {

    public final String id;
    public final String name;
    public final String description;
    public final String priorities;
    public final double aggression;    // 0.0 (passive) - 1.0 (aggressive)
    public final double riskTolerance; // 0.0 (conservative) - 1.0 (reckless)
    public final double cooperation;   // 0.0 (lone wolf) - 1.0 (team player)

    public AgentPersonality(String id, String name, String description,
                            String priorities, double aggression,
                            double riskTolerance, double cooperation) {
        this.id            = id;
        this.name          = name;
        this.description   = description;
        this.priorities    = priorities;
        this.aggression    = aggression;
        this.riskTolerance = riskTolerance;
        this.cooperation   = cooperation;
    }

    /** Default roster. */
    public static AgentPersonality[] defaultRoster() {
        return new AgentPersonality[]{
                new AgentPersonality("agent-0", "The Grinder",
                        "A relentless worker who values consistency over flash.",
                        "steady income, low risk, market share",
                        0.3, 0.2, 0.5),
                new AgentPersonality("agent-1", "The Shark",
                        "A ruthless competitor who will crush others to dominate.",
                        "maximum profit, market dominance, crushing competition",
                        0.9, 0.8, 0.1),
                new AgentPersonality("agent-2", "The Diplomat",
                        "A smooth operator who builds alliances and trades favors.",
                        "alliances, influence, long-term positioning",
                        0.2, 0.4, 0.9),
                new AgentPersonality("agent-3", "The Gambler",
                        "A chaos agent who loves high-risk, high-reward plays.",
                        "big payoffs, disruption, volatility",
                        0.6, 1.0, 0.3),
                new AgentPersonality("agent-4", "The Architect",
                        "A builder who invests in cities and infrastructure for long-term returns.",
                        "city growth, infrastructure, sustainable economy",
                        0.1, 0.3, 0.7),
        };
    }
}