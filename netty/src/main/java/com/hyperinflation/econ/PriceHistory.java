package com.hyperinflation.econ;

import java.util.*;

/**
 * Tracks price over time. Fixed-size rolling window.
 */
public final class PriceHistory {

    private final int maxSize;
    private final Deque<double[]> history; // [tick, price, volume]

    public PriceHistory(int maxSize) {
        this.maxSize = maxSize;
        this.history = new ArrayDeque<>(maxSize);
    }

    public void record(int tick, double price, int volume) {
        if (history.size() >= maxSize) history.removeFirst();
        history.addLast(new double[]{tick, price, volume});
    }

    public double getLatestPrice() {
        if (history.isEmpty()) return EconomyConfig.INITIAL_PROMPT_PRICE;
        return history.getLast()[1];
    }

    public double getPriceChange() {
        if (history.size() < 2) return 0.0;
        double[] prev = ((ArrayDeque<double[]>) history).stream()
                .skip(history.size() - 2).findFirst().orElse(null);
        double[] curr = history.getLast();
        if (prev == null) return 0.0;
        return (curr[1] - prev[1]) / prev[1];
    }

    public List<Map<String, Object>> toList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (double[] entry : history) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tick",   (int) entry[0]);
            m.put("price",  Math.round(entry[1] * 100.0) / 100.0);
            m.put("volume", (int) entry[2]);
            list.add(m);
        }
        return list;
    }
}