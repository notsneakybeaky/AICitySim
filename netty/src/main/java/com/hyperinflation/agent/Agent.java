package com.hyperinflation.agent;

import java.util.*;

public final class Agent {

    // ---- Inner: Personality ----
    public static final class Personality {
        public final String name;
        public final String description;
        public final String priorities;
        public final double aggression;
        public final double riskTolerance;
        public final double cooperation;

        public Personality(String name, String description, String priorities,
                           double aggression, double riskTolerance, double cooperation) {
            this.name          = name;
            this.description   = description;
            this.priorities    = priorities;
            this.aggression    = aggression;
            this.riskTolerance = riskTolerance;
            this.cooperation   = cooperation;
        }

        /** Construct from the standalone AgentPersonality class. */
        public static Personality fromAgentPersonality(AgentPersonality ap) {
            return new Personality(ap.name, ap.description, ap.priorities,
                    ap.aggression, ap.riskTolerance, ap.cooperation);
        }
    }

    private final String         id;
    private final Personality    personality;
    private final AgentMemory    memory;
    private boolean              alive;

    public Agent(String id, Personality personality) {
        this.id          = id;
        this.personality = personality;
        this.memory      = new AgentMemory(50);
        this.alive       = true;
    }

    /** Convenience: construct from AgentPersonality roster entry. */
    public Agent(AgentPersonality ap) {
        this(ap.id, Personality.fromAgentPersonality(ap));
    }

    public String        getId()          { return id; }
    public Personality   getPersonality() { return personality; }
    public AgentMemory   getMemory()      { return memory; }
    public boolean       isAlive()        { return alive; }
    public void          kill()           { alive = false; }
}