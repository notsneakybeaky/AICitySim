package com.hyperinflation.world;

import java.util.*;

/**
 * Spatial map of the world. A 2D grid of Tiles.
 * Skeleton — you'll flesh this out with generation, pathfinding, etc.
 */
public final class WorldMap {

    private final int width;
    private final int height;
    private final Tile[][] grid;

    public WorldMap(int width, int height) {
        this.width  = width;
        this.height = height;
        this.grid   = new Tile[width][height];

        // Initialize with default terrain
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = new Tile(x, y, Tile.Terrain.PLAINS);
            }
        }
    }

    public Tile getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return null;
        return grid[x][y];
    }

    public void setTile(int x, int y, Tile tile) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            grid[x][y] = tile;
        }
    }

    /** Get all tiles owned by a specific city. */
    public List<Tile> getTilesForCity(String cityId) {
        List<Tile> result = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (cityId.equals(grid[x][y].getOwnerCityId())) {
                    result.add(grid[x][y]);
                }
            }
        }
        return result;
    }

    /** Assign a radius of tiles around a city's location to that city. */
    public void claimRadius(String cityId, int cx, int cy, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                Tile t = getTile(x, y);
                if (t != null && t.getOwnerCityId() == null) {
                    double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                    if (dist <= radius) {
                        t.setOwnerCityId(cityId);
                    }
                }
            }
        }
    }

    public int getWidth()  { return width; }
    public int getHeight() { return height; }

    /** Serialize the full map. WARNING: can be large. Use for initial snapshot only. */
    public List<Map<String, Object>> toMapList() {
        List<Map<String, Object>> tiles = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles.add(grid[x][y].toMap());
            }
        }
        return tiles;
    }

    /** Lightweight serialization — just dimensions and city ownership. */
    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("width",  width);
        m.put("height", height);
        // Don't send all tiles in summary — too much data
        return m;
    }
}