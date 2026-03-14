package com.hyperinflation.world;

import java.util.*;

/**
 * A named region containing multiple cities.
 * Regions define trade routes and political boundaries.
 */
public final class Region {

    private final String id;
    private final String name;
    private final List<String> cityIds = new ArrayList<>();
    private double tradeBonus;  // Multiplier for inter-city trade within this region

    public Region(String id, String name, double tradeBonus) {
        this.id         = id;
        this.name       = name;
        this.tradeBonus = tradeBonus;
    }

    public void addCity(String cityId) {
        if (!cityIds.contains(cityId)) cityIds.add(cityId);
    }

    public String       getId()        { return id; }
    public String       getName()      { return name; }
    public List<String> getCityIds()   { return Collections.unmodifiableList(cityIds); }
    public double       getTradeBonus() { return tradeBonus; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          id);
        m.put("name",        name);
        m.put("cities",      cityIds);
        m.put("trade_bonus", tradeBonus);
        return m;
    }
}