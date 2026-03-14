package main.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Policy {

    private final String agentId;
    private final int round;
    private final String proposedText;
    private final Map<String, Double> params;
    private final double selfSatisfaction;

    public Policy(String agentId, int round, String proposedText,
                  Map<String, Double> params, double selfSatisfaction) {
        this.agentId          = agentId;
        this.round            = round;
        this.proposedText     = proposedText != null ? proposedText : "";
        this.params           = params != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(params))
                : Collections.emptyMap();
        this.selfSatisfaction = selfSatisfaction;
    }

    public String getAgentId()             { return agentId; }
    public int    getRound()               { return round; }
    public String getProposedText()        { return proposedText; }
    public Map<String, Double> getParams() { return params; }
    public double getSelfSatisfaction()    { return selfSatisfaction; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agentId",       agentId);
        m.put("proposedText",  proposedText);
        m.put("params",        params);
        m.put("satisfaction",  selfSatisfaction);
        return m;
    }
}