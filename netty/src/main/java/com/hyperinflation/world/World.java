package com.hyperinflation.world;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The world container. Holds all cities, regions, the map, and agents.
 * This is the single source of truth for the simulation state.
 */
public final class World {

    private final WorldMap map;
    private final Map<String, City> cities = new ConcurrentHashMap<>();
    private final Map<String, Region> regions = new ConcurrentHashMap<>();
    private int tickCount = 0;

    public World(int mapWidth, int mapHeight) {
        this.map = new WorldMap(mapWidth, mapHeight);
    }

    // ---- City management ----

    public void addCity(City city) {
        cities.put(city.getId(), city);
        // Claim surrounding tiles
        map.claimRadius(city.getId(), city.getTileX(), city.getTileY(), 3);
        // Set city tile to URBAN
        Tile centerTile = map.getTile(city.getTileX(), city.getTileY());
        if (centerTile != null) centerTile.setTerrain(Tile.Terrain.URBAN);
    }

    public City getCity(String id) {
        return cities.get(id);
    }

    public Collection<City> getAllCities() {
        return Collections.unmodifiableCollection(cities.values());
    }

    public City getRandomCity() {
        List<City> list = new ArrayList<>(cities.values());
        if (list.isEmpty()) return null;
        return list.get(new Random().nextInt(list.size()));
    }

    // ---- Region management ----

    public void addRegion(Region region) {
        regions.put(region.getId(), region);
    }

    public Region getRegion(String id) {
        return regions.get(id);
    }

    // ---- Simulation ----

    public void simulateTick() {
        tickCount++;
        for (City city : cities.values()) {
            city.simulateTick();
        }
    }

    // ---- Serialization ----

    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tick",        tickCount);
        m.put("city_count",  cities.size());
        m.put("map",         map.toSummaryMap());

        long totalPop = cities.values().stream().mapToLong(City::getPopulation).sum();
        double totalGdp = cities.values().stream().mapToDouble(City::getEconomicOutput).sum();
        m.put("total_population",     totalPop);
        m.put("total_economic_output", Math.round(totalGdp * 100.0) / 100.0);
        return m;
    }

    public Map<String, Object> toFullMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tick",    tickCount);
        m.put("map",     map.toSummaryMap());

        // Cities
        Map<String, Object> cityMap = new LinkedHashMap<>();
        for (City c : cities.values()) {
            cityMap.put(c.getId(), c.toMap());
        }
        m.put("cities", cityMap);

        // Regions
        Map<String, Object> regionMap = new LinkedHashMap<>();
        for (Region r : regions.values()) {
            regionMap.put(r.getId(), r.toMap());
        }
        m.put("regions", regionMap);

        return m;
    }

    public WorldMap getMap()  { return map; }
    public int getTickCount() { return tickCount; }
}