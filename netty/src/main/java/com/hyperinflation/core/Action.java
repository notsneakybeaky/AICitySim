package com.hyperinflation.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A generic, serializable action to be performed on the world.
 * All modules produce Actions; the SimulationEngine processes them.
 */
public final class Action {

    public enum Type {
        // Economic
        PLACE_BID, PLACE_ASK, CANCEL_ORDER,
        ADJUST_PRICE, DRAIN_TREASURY, INJECT_CAPITAL,

        // City
        BUILD_INFRASTRUCTURE, DAMAGE_INFRASTRUCTURE,
        BOOST_HAPPINESS, DAMAGE_HAPPINESS,
        GROW_POPULATION, SHRINK_POPULATION,

        // Agent
        AGENT_EARN, AGENT_SPEND,
        FORM_ALLIANCE, BREAK_ALLIANCE,

        // Combat / Conflict
        ATTACK_CITY, DEFEND_CITY,
        INFILTRATE, SPREAD_PROPAGANDA,

        // Meta
        CHANGE_MODULE, TRIGGER_EVENT,
        NO_OP
    }

    private final Type type;
    private final String sourceModuleId;
    private final String sourceAgentId;   // nullable
    private final String targetId;         // city id, agent id, etc.
    private final Map<String, Object> params;
    private final long timestamp;

    public Action(Type type, String sourceModuleId, String sourceAgentId,
                  String targetId, Map<String, Object> params) {
        this.type           = type;
        this.sourceModuleId = sourceModuleId;
        this.sourceAgentId  = sourceAgentId;
        this.targetId       = targetId;
        this.params         = params != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(params))
                : Collections.emptyMap();
        this.timestamp      = System.currentTimeMillis();
    }

    // Convenience builder
    public static Action of(Type type, String moduleId, String agentId, String targetId) {
        return new Action(type, moduleId, agentId, targetId, null);
    }

    public static Action of(Type type, String moduleId, String agentId,
                            String targetId, Map<String, Object> params) {
        return new Action(type, moduleId, agentId, targetId, params);
    }

    public static Action noOp(String moduleId) {
        return new Action(Type.NO_OP, moduleId, null, null, null);
    }

    // Getters
    public Type                    getType()           { return type; }
    public String                  getSourceModuleId() { return sourceModuleId; }
    public String                  getSourceAgentId()  { return sourceAgentId; }
    public String                  getTargetId()       { return targetId; }
    public Map<String, Object>     getParams()         { return params; }
    public long                    getTimestamp()       { return timestamp; }

    public double getParamDouble(String key, double fallback) {
        Object v = params.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return fallback;
    }

    public String getParamString(String key, String fallback) {
        Object v = params.get(key);
        return v != null ? v.toString() : fallback;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type",       type.name());
        m.put("source",     sourceModuleId);
        m.put("agent",      sourceAgentId);
        m.put("target",     targetId);
        m.put("params",     params);
        m.put("ts",         timestamp);
        return m;
    }
}