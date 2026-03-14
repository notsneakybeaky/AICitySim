package com.hyperinflation.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fully-featured agent with personality, memory, and economic state.
 */
public final class Agent {

    private final AgentPersonality personality;
    private final AgentMemory memory;

    // Runtime state
    private boolean alive;

    public Agent(AgentPersonality personality) {
        this.personality = personality;
        this.memory      = new AgentMemory(50); // Remember last 50 actions
        this.alive       = true;
    }

    public String           getId()          { return personality.id; }
    public AgentPersonality getPersonality() { return personality; }
    public AgentMemory      getMemory()      { return memory; }
    public boolean          isAlive()        { return alive; }
    public void             kill()           { this.alive = false; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",             personality.id);
        m.put("name",           personality.name);
        m.put("description",    personality.description);
        m.put("priorities",     personality.priorities);
        m.put("aggression",     personality.aggression);
        m.put("risk_tolerance", personality.riskTolerance);
        m.put("cooperation",    personality.cooperation);
        m.put("alive",          alive);
        m.put("memory_size",    memory.getSize());
        return m;
    }
}