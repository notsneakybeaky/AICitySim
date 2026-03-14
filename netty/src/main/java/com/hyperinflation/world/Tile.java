package com.hyperinflation.world;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The smallest unit of the world map.
 * A grid of tiles makes up the world.
 */
public final class Tile {

    public enum Terrain {
        PLAINS, FOREST, MOUNTAIN, WATER, DESERT, URBAN, INDUSTRIAL
    }

    private final int x;
    private final int y;
    private Terrain terrain;
    private String ownerCityId;  // nullable — which city controls this tile
    private double resourceValue; // 0.0 - 1.0, how valuable this tile is

    public Tile(int x, int y, Terrain terrain) {
        this.x             = x;
        this.y             = y;
        this.terrain       = terrain;
        this.ownerCityId   = null;
        this.resourceValue = 0.0;
    }

    public int       getX()             { return x; }
    public int       getY()             { return y; }
    public Terrain   getTerrain()       { return terrain; }
    public void      setTerrain(Terrain t) { this.terrain = t; }
    public String    getOwnerCityId()   { return ownerCityId; }
    public void      setOwnerCityId(String id) { this.ownerCityId = id; }
    public double    getResourceValue() { return resourceValue; }
    public void      setResourceValue(double v) { this.resourceValue = clamp(v, 0, 1); }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x",        x);
        m.put("y",        y);
        m.put("terrain",  terrain.name());
        m.put("owner",    ownerCityId);
        m.put("resource", Math.round(resourceValue * 100.0) / 100.0);
        return m;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}