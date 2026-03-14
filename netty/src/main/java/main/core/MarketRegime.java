package main.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public final class MarketRegime {

    private double price;
    private double drift;
    private double volatility;
    private double liquidity;
    private double spreadBps;
    private double shockProb;
    private int    version;
    private long   updatedAtMs;
    private final Random rng;

    public MarketRegime(double price, double drift, double volatility,
                        double liquidity, double spreadBps, double shockProb,
                        long seed) {
        this.price       = price;
        this.drift       = drift;
        this.volatility  = volatility;
        this.liquidity   = liquidity;
        this.spreadBps   = spreadBps;
        this.shockProb   = shockProb;
        this.version     = 0;
        this.updatedAtMs = System.currentTimeMillis();
        this.rng         = new Random(seed);
    }

    public synchronized void applyPolicy(Policy policy) {
        Map<String, Double> p = policy.getParams();
        if (p.containsKey("drift"))      this.drift      = clamp(p.get("drift"),      -5.0,    5.0);
        if (p.containsKey("volatility")) this.volatility  = clamp(p.get("volatility"),  0.01,  10.0);
        if (p.containsKey("liquidity"))  this.liquidity   = clamp(p.get("liquidity"),   0.01,   5.0);
        if (p.containsKey("spread_bps")) this.spreadBps   = clamp(p.get("spread_bps"),  1.0,  500.0);
        if (p.containsKey("shock_prob")) this.shockProb   = clamp(p.get("shock_prob"),  0.0,    1.0);

        double noise = rng.nextGaussian() * volatility * 0.01;
        double shock = (rng.nextDouble() < shockProb)
                ? rng.nextGaussian() * volatility * 0.05 : 0.0;
        double step  = drift * 0.01 + noise + shock;
        this.price   = Math.max(0.01, this.price * (1.0 + step));

        this.version++;
        this.updatedAtMs = System.currentTimeMillis();
    }

    public synchronized Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("price",        Math.round(price * 100.0) / 100.0);
        m.put("drift",        drift);
        m.put("volatility",   volatility);
        m.put("liquidity",    liquidity);
        m.put("spread_bps",   spreadBps);
        m.put("shock_prob",   shockProb);
        m.put("version",      version);
        m.put("updated_at_ms", updatedAtMs);
        return m;
    }

    public synchronized String toContextString() {
        return String.format(
                "Price=%.2f, Drift=%.2f, Volatility=%.2f, Liquidity=%.2f, SpreadBps=%.1f, ShockProb=%.3f",
                price, drift, volatility, liquidity, spreadBps, shockProb);
    }

    public synchronized double getPrice()   { return price; }
    public synchronized int    getVersion() { return version; }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}