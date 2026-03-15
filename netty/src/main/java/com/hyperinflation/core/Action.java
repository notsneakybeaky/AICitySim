package com.hyperinflation.core;

import java.util.*;

public final class Action {

    public enum Type {
        PLACE_BID, PLACE_ASK, CANCEL_ORDER,
        ADJUST_PRICE, DRAIN_TREASURY, INJECT_CAPITAL,
        BUILD_INFRASTRUCTURE, DAMAGE_INFRASTRUCTURE,
        BOOST_HAPPINESS, DAMAGE_HAPPINESS,
        GROW_POPULATION, SHRINK_POPULATION,
        AGENT_EARN, AGENT_SPEND,
        FORM_ALLIANCE, BREAK_ALLIANCE,
        ATTACK_CITY, DEFEND_CITY,
        INFILTRATE, SPREAD_PROPAGANDA,
        MOVE_TO,
        NO_OP
    }

    private final Type type;
    private final String sourceModule;
    private final String agentId;
    private final String targetId;
    private final Map<String, Object> params;

    private Action(Type type, String sourceModule, String agentId,
                   String targetId, Map<String, Object> params) {
        this.type         = type;
        this.sourceModule = sourceModule;
        this.agentId      = agentId;
        this.targetId     = targetId;
        this.params       = params != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(params))
                : Collections.emptyMap();
    }

    public static Action of(Type type, String sourceModule, String agentId,
                            String targetId, Map<String, Object> params) {
        return new Action(type, sourceModule, agentId, targetId, params);
    }

    public static Action noOp(String sourceModule) {
        return new Action(Type.NO_OP, sourceModule, null, null, null);
    }

    public Type                 getType()         { return type; }
    public String               getSourceModule() { return sourceModule; }
    public String               getAgentId()      { return agentId; }
    public String               getTargetId()     { return targetId; }
    public Map<String, Object>  getParams()       { return params; }

    public double param(String key, double fallback) {
        Object v = params.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return fallback;
    }

    public String paramStr(String key, String fallback) {
        Object v = params.get(key);
        if (v instanceof String) return (String) v;
        return fallback;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type",   type.name());
        m.put("agent",  agentId);
        m.put("target", targetId);
        m.put("params", params);
        return m;
    }
}