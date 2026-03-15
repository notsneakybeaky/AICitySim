package com.hyperinflation.world;

import java.util.*;

public final class World {

    private final Map<String, City>   cities  = new LinkedHashMap<>();
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final WorldMap            worldMap;

    // Tile changes from the last tick — included in round result broadcasts
    private List<WorldMap.TileChange> lastTileChanges = List.of();

    public World() {
        // Create regions with render colors
        Region alpha = new Region("alpha", "Alpha Sector", 1.2, "#22D3EE"); // cyan
        Region beta  = new Region("beta",  "Beta Sector",  1.0, "#10B981"); // emerald
        Region gamma = new Region("gamma", "Gamma Sector", 0.8, "#F59E0B"); // amber
        regions.put("alpha", alpha);
        regions.put("beta",  beta);
        regions.put("gamma", gamma);

        // Create cities
        cities.put("nexus",    new City("nexus",    "Nexus Prime", "alpha", 12,  8, 500_000, 70, 75, 0.12));
        cities.put("ironhold", new City("ironhold", "Ironhold",    "alpha",  6, 14, 320_000, 55, 80, 0.15));
        cities.put("freeport", new City("freeport", "Freeport",    "beta",  20,  5, 250_000, 65, 60, 0.08));
        cities.put("eden",     new City("eden",     "New Eden",    "beta",  18, 18, 180_000, 80, 50, 0.10));
        cities.put("vault",    new City("vault",    "The Vault",   "gamma",  3,  3, 150_000, 60, 90, 0.20));

        // Register cities in their regions
        for (Map.Entry<String, City> e : cities.entrySet()) {
            Region r = regions.get(e.getValue().getRegionId());
            if (r != null) r.addCity(e.getKey());
        }

        // Build the spatial world map (terrain is generated inside WorldMap)
        worldMap = new WorldMap(25, 22);

        // Set city center tiles to URBAN and stamp initial territory
        for (Map.Entry<String, City> e : cities.entrySet()) {
            City c = e.getValue();
            Tile cityTile = worldMap.getTile(c.getTileX(), c.getTileY());
            if (cityTile != null) {
                cityTile.setTerrain(Tile.Terrain.URBAN);
                cityTile.setResourceValue(0.8);
            }
            // Initial claim radius based on starting infrastructure
            int startRadius = 2 + (int) (c.getInfrastructure() / 25.0);
            worldMap.claimRadius(e.getKey(), c.getTileX(), c.getTileY(), startRadius);
        }

        // Clear any changes from initialization — the first connect snapshot
        // will send the full grid anyway
        worldMap.getAndClearChanges();
    }

    // =====================================================================
    //  ACCESSORS
    // =====================================================================

    public City                  getCity(String id)    { return cities.get(id); }
    public Map<String, City>     getCities()           { return Collections.unmodifiableMap(cities); }
    public Collection<City>      allCities()           { return cities.values(); }
    public Map<String, Region>   getRegions()          { return Collections.unmodifiableMap(regions); }
    public WorldMap              getWorldMap()          { return worldMap; }

    // =====================================================================
    //  TICK — city stats update, then territory recalculation
    // =====================================================================

    public void tickAll() {
        // 1. Update all city stats (happiness drift, infra decay, GDP, etc.)
        for (City c : cities.values()) c.tick();

        // 2. Recalculate territory based on updated city stats
        worldMap.recalculateTerritories(cities);
        lastTileChanges = worldMap.getAndClearChanges();
    }

    /** Tile changes from the most recent tick. */
    public List<WorldMap.TileChange> getLastTileChanges() {
        return lastTileChanges;
    }

    // =====================================================================
    //  AGGREGATES
    // =====================================================================

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

    // =====================================================================
    //  SERIALIZATION
    // =====================================================================

    /**
     * Full world state — sent on connect AND each round result.
     * Includes compact grid snapshot on connect (full terrain + owners).
     * Per-tick broadcasts only include tile_changes[].
     */
    public Map<String, Object> toFullMap() {
        Map<String, Object> m = new LinkedHashMap<>();

        // Cities
        Map<String, Object> citiesMap = new LinkedHashMap<>();
        for (Map.Entry<String, City> e : cities.entrySet()) {
            citiesMap.put(e.getKey(), e.getValue().toMap());
        }
        m.put("cities", citiesMap);

        // Aggregates
        m.put("total_gdp",            r2(getTotalEconomicOutput()));
        m.put("total_supply_capacity", r2(getTotalSupplyContribution()));
        m.put("avg_demand_multiplier", r2(getAverageDemandMultiplier()));

        // Regions
        Map<String, Object> regionMap = new LinkedHashMap<>();
        for (Map.Entry<String, Region> e : regions.entrySet()) {
            regionMap.put(e.getKey(), e.getValue().toMap());
        }
        m.put("regions", regionMap);

        // Full grid snapshot (compact encoding — ~2KB)
        m.put("grid", worldMap.toCompactSnapshot());

        // Tile changes from this tick (for per-tick deltas)
        if (!lastTileChanges.isEmpty()) {
            m.put("tile_changes", worldMap.toChangeList(lastTileChanges));
        }

        // Territory counts per city
        Map<String, Integer> territoryCounts = new LinkedHashMap<>();
        for (String cityId : cities.keySet()) {
            territoryCounts.put(cityId, worldMap.countTilesForCity(cityId));
        }
        m.put("territory", territoryCounts);

        return m;
    }

    /** Lightweight summary for narrator and phase broadcasts. */
    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> citiesMap = new LinkedHashMap<>();
        for (Map.Entry<String, City> e : cities.entrySet()) {
            City c = e.getValue();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("name",       c.getName());
            summary.put("population", c.getPopulation());
            summary.put("happiness",  r2(c.getHappiness()));
            summary.put("treasury",   r2(c.getTreasury()));
            summary.put("territory",  worldMap.countTilesForCity(e.getKey()));
            citiesMap.put(e.getKey(), summary);
        }
        m.put("cities", citiesMap);
        return m;
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}