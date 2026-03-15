package com.hyperinflation.world;

import java.util.*;

/**
 * WORLD MAP — 2D grid of Tiles with territory mechanics.
 *
 * TERRITORY RULES:
 *   Claim radius = 2 + floor(infrastructure / 25)
 *     infra  0  → radius 2 (tiny footprint)
 *     infra 50  → radius 4
 *     infra 100 → radius 6 (large territory)
 *
 *   Contested tiles: if two cities overlap, the city with the
 *   higher strength score wins:  strength = (infra + defenses + happiness) / 3
 *
 *   Lost tiles revert to unclaimed.
 *
 * COMPACT SNAPSHOT PROTOCOL:
 *   terrain: "PPFFMMWW..." — one char per tile, row-major (y * width + x)
 *   owners:  "nnn..iiff..." — first char of city id, '.' for unclaimed
 *   Sent once on connect. Per-tick: only tile_changes[] deltas.
 */
public final class WorldMap {

    /** Represents a single tile that changed this tick. */
    public record TileChange(int x, int y, String terrain, String owner) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("x", x);
            m.put("y", y);
            if (terrain != null) m.put("terrain", terrain);
            if (owner != null)   m.put("owner", owner);
            return m;
        }
    }

    private final int width;
    private final int height;
    private final Tile[][] grid;
    private final List<TileChange> pendingChanges = new ArrayList<>();

    public WorldMap(int width, int height) {
        this.width  = width;
        this.height = height;
        this.grid   = new Tile[width][height];

        // Generate terrain (deterministic based on position)
        generateTerrain();
    }

    // =====================================================================
    //  TERRAIN GENERATION
    // =====================================================================

    private void generateTerrain() {
        Random rng = new Random(42); // deterministic seed

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = new Tile(x, y, chooseTerrain(x, y, rng));
            }
        }
    }

    private Tile.Terrain chooseTerrain(int x, int y, Random rng) {
        // Water: river running roughly through the middle + coastal edges
        if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
            return Tile.Terrain.WATER;
        }
        // A river from top-center to bottom-right
        double riverX = 13.0 + (y - 5) * 0.4 + Math.sin(y * 0.6) * 2;
        if (Math.abs(x - riverX) < 1.2) {
            return Tile.Terrain.WATER;
        }
        // Lake in the lower middle
        double lakeDist = Math.sqrt((x - 14) * (x - 14) + (y - 16) * (y - 16));
        if (lakeDist < 2.5) {
            return Tile.Terrain.WATER;
        }

        // Mountains: top-right cluster + scattered peaks
        if (x > 18 && y < 8 && rng.nextDouble() < 0.5) return Tile.Terrain.MOUNTAIN;
        if (x > 20 && y < 10) return Tile.Terrain.MOUNTAIN;

        // Desert: bottom-left area
        if (x < 8 && y > 15 && rng.nextDouble() < 0.6) return Tile.Terrain.DESERT;
        if (x < 5 && y > 13) return Tile.Terrain.DESERT;

        // Forest belts
        if (y > 4 && y < 12 && x > 2 && x < 10 && rng.nextDouble() < 0.5) {
            return Tile.Terrain.FOREST;
        }
        if (y > 10 && y < 18 && x > 15 && x < 22 && rng.nextDouble() < 0.4) {
            return Tile.Terrain.FOREST;
        }

        // Scattered forests elsewhere
        if (rng.nextDouble() < 0.08) return Tile.Terrain.FOREST;

        return Tile.Terrain.PLAINS;
    }

    // =====================================================================
    //  TILE ACCESS
    // =====================================================================

    public Tile getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return null;
        return grid[x][y];
    }

    public void setTile(int x, int y, Tile tile) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            grid[x][y] = tile;
        }
    }

    public int getWidth()  { return width; }
    public int getHeight() { return height; }

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

    /** Count tiles owned by a specific city. */
    public int countTilesForCity(String cityId) {
        int count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (cityId.equals(grid[x][y].getOwnerCityId())) count++;
            }
        }
        return count;
    }

    // =====================================================================
    //  TERRITORY CLAIMING (used during initialization)
    // =====================================================================

    /** Assign a radius of tiles around a city's location to that city. */
    public void claimRadius(String cityId, int cx, int cy, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                Tile t = getTile(x, y);
                if (t != null && t.getTerrain() != Tile.Terrain.WATER) {
                    double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                    if (dist <= radius) {
                        if (t.getOwnerCityId() == null) {
                            t.setOwnerCityId(cityId);
                        }
                    }
                }
            }
        }
    }

    // =====================================================================
    //  TERRITORY RECALCULATION (called every tick)
    // =====================================================================

    /**
     * Recalculate territory based on current city stats.
     * Cities with higher infrastructure claim larger radii.
     * Contested tiles go to the stronger city.
     *
     * @param cities the current city map
     */
    @SuppressWarnings("unchecked")
    public void recalculateTerritories(Map<String, City> cities) {
        pendingChanges.clear();

        // 1. Build a claim map: for each tile, which city wants it and at what strength?
        //    claimMap[x][y] = {cityId -> strength}
        Map<String, Double>[][] claims = new Map[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                claims[x][y] = new HashMap<>();
            }
        }

        for (Map.Entry<String, City> entry : cities.entrySet()) {
            String cityId = entry.getKey();
            City city     = entry.getValue();
            int cx        = city.getTileX();
            int cy        = city.getTileY();
            int radius    = 2 + (int) (city.getInfrastructure() / 25.0);
            double strength = (city.getInfrastructure()
                    + city.getDigitalDefenses()
                    + city.getHappiness()) / 3.0;

            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int y = cy - radius; y <= cy + radius; y++) {
                    if (x < 0 || x >= width || y < 0 || y >= height) continue;
                    if (grid[x][y].getTerrain() == Tile.Terrain.WATER) continue;
                    double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                    if (dist <= radius) {
                        // Strength falls off with distance
                        double effectiveStrength = strength * (1.0 - dist / (radius + 1.0));
                        claims[x][y].put(cityId, effectiveStrength);
                    }
                }
            }
        }

        // 2. Resolve each tile: winner is highest strength, or unclaimed if no claims
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Tile tile = grid[x][y];
                if (tile.getTerrain() == Tile.Terrain.WATER) continue;

                Map<String, Double> tileClaims = claims[x][y];
                String oldOwner = tile.getOwnerCityId();
                String newOwner = null;

                if (!tileClaims.isEmpty()) {
                    // Find strongest claimant
                    double maxStr = -1;
                    for (Map.Entry<String, Double> c : tileClaims.entrySet()) {
                        if (c.getValue() > maxStr) {
                            maxStr = c.getValue();
                            newOwner = c.getKey();
                        }
                    }
                }

                // Record change if ownership shifted
                if (!Objects.equals(oldOwner, newOwner)) {
                    tile.setOwnerCityId(newOwner);
                    pendingChanges.add(new TileChange(x, y, null, newOwner));
                }

                // Terrain upgrades: city center tiles become URBAN,
                // nearby owned tiles become INDUSTRIAL if they were PLAINS
                City ownerCity = newOwner != null ? cities.get(newOwner) : null;
                if (ownerCity != null) {
                    int cx = ownerCity.getTileX();
                    int cy = ownerCity.getTileY();
                    double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));

                    if (dist < 1.5 && tile.getTerrain() != Tile.Terrain.URBAN) {
                        tile.setTerrain(Tile.Terrain.URBAN);
                        pendingChanges.add(new TileChange(x, y,
                                Tile.Terrain.URBAN.name(), newOwner));
                    } else if (dist < 3.0
                            && tile.getTerrain() == Tile.Terrain.PLAINS
                            && ownerCity.getInfrastructure() > 50) {
                        tile.setTerrain(Tile.Terrain.INDUSTRIAL);
                        pendingChanges.add(new TileChange(x, y,
                                Tile.Terrain.INDUSTRIAL.name(), newOwner));
                    }
                }
            }
        }
    }

    // =====================================================================
    //  TILE CHANGE TRACKING
    // =====================================================================

    /** Get changes from the last recalculation and clear the list. */
    public List<TileChange> getAndClearChanges() {
        List<TileChange> copy = new ArrayList<>(pendingChanges);
        pendingChanges.clear();
        return copy;
    }

    // =====================================================================
    //  COMPACT SNAPSHOT PROTOCOL
    // =====================================================================

    /**
     * Full grid snapshot — sent once on connect.
     * ~2KB total instead of ~25KB for individual tile JSON objects.
     *
     * Format:
     *   width: 25, height: 22
     *   terrain: "PPFFMMWW..." (550 chars, row-major: index = y * width + x)
     *   owners:  "nnn..iiff..." (550 chars, first char of city id or '.')
     */
    public Map<String, Object> toCompactSnapshot() {
        int total = width * height;
        char[] terrain = new char[total];
        char[] owners  = new char[total];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                Tile t = grid[x][y];
                terrain[idx] = t.terrainChar();
                owners[idx]  = t.ownerChar();
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("width",   width);
        m.put("height",  height);
        m.put("terrain", new String(terrain));
        m.put("owners",  new String(owners));
        return m;
    }

    /**
     * Tile changes as a list of maps — sent per tick (typically 0-20 items).
     */
    public List<Map<String, Object>> toChangeList(List<TileChange> changes) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TileChange c : changes) {
            list.add(c.toMap());
        }
        return list;
    }

    /** Old lightweight summary — still used for narrator context. */
    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("width",  width);
        m.put("height", height);
        return m;
    }
}