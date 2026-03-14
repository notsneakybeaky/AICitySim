package com.hyperinflation.agent;

import java.util.*;

/**
 * Per-agent memory of actions and outcomes.
 * This is what forces the AI to learn from the sandbox, not the internet.
 */
public final class AgentMemory {

    public record ActionOutcome(
            int tick,
            String action,
            String target,
            boolean success,
            double reward,
            String context
    ) {}

    private final int maxSize;
    private final Deque<ActionOutcome> history;

    public AgentMemory(int maxSize) {
        this.maxSize = maxSize;
        this.history = new ArrayDeque<>(maxSize);
    }

    public void record(int tick, String action, String target,
                       boolean success, double reward, String context) {
        if (history.size() >= maxSize) history.removeFirst();
        history.addLast(new ActionOutcome(tick, action, target, success, reward, context));
    }

    /** Build a tactical summary for the AI prompt. */
    public String buildTacticalSummary() {
        if (history.isEmpty()) return "You have no memory. This is your first action.";

        StringBuilder sb = new StringBuilder();
        sb.append("Your recent history (most recent first):\n");

        // Show last 10 actions
        List<ActionOutcome> recent = new ArrayList<>(history);
        Collections.reverse(recent);
        int shown = 0;
        for (ActionOutcome ao : recent) {
            if (shown >= 10) break;
            sb.append(String.format(
                    "  Tick %d: %s on %s → %s (reward: %.2f) [%s]\n",
                    ao.tick, ao.action, ao.target,
                    ao.success ? "SUCCESS" : "FAILED",
                    ao.reward, ao.context));
            shown++;
        }

        // Compute action success rates
        Map<String, int[]> actionStats = new LinkedHashMap<>();
        for (ActionOutcome ao : history) {
            actionStats.computeIfAbsent(ao.action, k -> new int[]{0, 0});
            int[] stats = actionStats.get(ao.action);
            stats[0]++; // total
            if (ao.success) stats[1]++; // successes
        }

        sb.append("\nYour action success rates:\n");
        for (Map.Entry<String, int[]> e : actionStats.entrySet()) {
            int total = e.getValue()[0];
            int wins  = e.getValue()[1];
            double rate = total > 0 ? (double) wins / total * 100 : 0;
            sb.append(String.format("  %s: %.0f%% (%d/%d)\n", e.getKey(), rate, wins, total));
        }

        // Total earnings
        double totalReward = history.stream().mapToDouble(ao -> ao.reward).sum();
        sb.append(String.format("\nTotal reward from memory: %.2f\n", totalReward));

        return sb.toString();
    }

    public int getSize() { return history.size(); }

    public List<Map<String, Object>> toList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ActionOutcome ao : history) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tick",    ao.tick);
            m.put("action",  ao.action);
            m.put("target",  ao.target);
            m.put("success", ao.success);
            m.put("reward",  ao.reward);
            m.put("context", ao.context);
            list.add(m);
        }
        return list;
    }
}