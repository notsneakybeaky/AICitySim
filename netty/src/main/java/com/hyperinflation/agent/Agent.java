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
    }

    // ---- Inner: Memory ----
    public static final class Memory {
        private final List<String> entries = new ArrayList<>();
        private static final int MAX = 50;

        public void record(String entry) {
            entries.add(entry);
            if (entries.size() > MAX) entries.remove(0);
        }

        public String buildTacticalSummary() {
            if (entries.isEmpty()) return "No prior actions or observations.";
            int start = Math.max(0, entries.size() - 15);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < entries.size(); i++) {
                sb.append("- ").append(entries.get(i)).append("\n");
            }
            return sb.toString();
        }
    }

    private final String      id;
    private final Personality  personality;
    private final Memory       memory;
    private boolean            alive;

    public Agent(String id, Personality personality) {
        this.id          = id;
        this.personality = personality;
        this.memory      = new Memory();
        this.alive       = true;
    }

    public String      getId()          { return id; }
    public Personality  getPersonality() { return personality; }
    public Memory       getMemory()      { return memory; }
    public boolean      isAlive()        { return alive; }
    public void         kill()           { alive = false; }
}