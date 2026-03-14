package com.hyperinflation.world;

import java.util.*;

public final class World {

    private final Map<String, City> cities = new LinkedHashMap<>();

    public World() {
        cities.put("nexus",    new City("nexus",    "Nexus Prime", "alpha", 12,  8, 500_000, 70, 75, 0.12));
        cities.put("ironhold", new City("ironhold", "Ironhold",    "alpha",  6, 14, 320_000, 55, 80, 0.15));
        cities.put("freeport", new City("freeport", "Freeport",    "beta",  20,  5, 250_000, 65, 60, 0.08));
        cities.put("eden",     new City("eden",     "New Eden",    "beta",  18, 18, 180_000, 80, 50, 0.10));
        cities.put("vault",    new City("vault",    "The Vault",   "gamma",  3,  3, 150_000, 60, 90, 0.20));
    }

    public City              getCity(String id)  { return cities.get(id); }
    public Map<String, City> getCities()         { return Collections.unmodifiableMap(cities); }
    public Collection<City>  allCities()         { return cities.values(); }

    public void tickAll() {
        for (City c : cities.values()) c.tick();
    }

    /** Full serialization — all city data. */
    public Map<String, Object> toFullMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> citiesMap = new LinkedHashMap<>();
        for (Map.Entry<String, City> e : cities.entrySet()) {
            citiesMap.put(e.getKey(), e.getValue().toMap());
        }
        m.put("cities", citiesMap);
        return m;
    }

    /**
     * Lightweight summary — just city names, populations, and happiness.
     * Used for phase-change broadcasts where full data would be too large.
     */
    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> citiesMap = new LinkedHashMap<>();
        for (Map.Entry<String, City> e : cities.entrySet()) {
            City c = e.getValue();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("name",        c.getName());
            summary.put("population",  c.getPopulation());
            summary.put("happiness",   Math.round(c.getHappiness() * 100.0) / 100.0);
            summary.put("treasury",    Math.round(c.getTreasury() * 100.0) / 100.0);
            citiesMap.put(e.getKey(), summary);
        }
        m.put("cities", citiesMap);
        return m;
    }
}
