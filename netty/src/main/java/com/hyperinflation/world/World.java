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

    public City              getCity(String id) { return cities.get(id); }
    public Map<String, City> getCities()        { return Collections.unmodifiableMap(cities); }
    public Collection<City>  allCities()        { return cities.values(); }

    public void tickAll() { for (City c : cities.values()) c.tick(); }

    public double getTotalEconomicOutput() {
        return cities.values().stream().mapToDouble(City::getEconomicOutput).sum();
    }

    public double getTotalSupplyContribution() {
        return cities.values().stream().mapToDouble(City::getSupplyContribution).sum();
    }

    public double getAverageDemandMultiplier() {
        return cities.values().stream()
                .mapToDouble(City::getDemandMultiplier)
                .average()
                .orElse(1.0);
    }

    public double getDistance(String fromId, String toId) {
        if (fromId == null || toId == null || fromId.equals(toId)) return 0;
        City from = cities.get(fromId);
        City to   = cities.get(toId);
        if (from == null || to == null) return 0;
        return from.distanceTo(to);
    }

    public Map<String, Object> toFullMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> citiesMap = new LinkedHashMap<>();
        for (Map.Entry<String, City> e : cities.entrySet()) {
            citiesMap.put(e.getKey(), e.getValue().toMap());
        }
        m.put("cities",               citiesMap);
        m.put("total_gdp",            Math.round(getTotalEconomicOutput() * 100.0) / 100.0);
        m.put("total_supply_capacity", Math.round(getTotalSupplyContribution() * 100.0) / 100.0);
        m.put("avg_demand_multiplier", Math.round(getAverageDemandMultiplier() * 100.0) / 100.0);
        return m;
    }

    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> citiesMap = new LinkedHashMap<>();
        for (Map.Entry<String, City> e : cities.entrySet()) {
            City c = e.getValue();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("name",       c.getName());
            summary.put("population", c.getPopulation());
            summary.put("happiness",  Math.round(c.getHappiness() * 100.0) / 100.0);
            summary.put("treasury",   Math.round(c.getTreasury() * 100.0) / 100.0);
            citiesMap.put(e.getKey(), summary);
        }
        m.put("cities", citiesMap);
        return m;
    }
}
