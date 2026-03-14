package com.hyperinflation.world;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A city in the world. Cities are the primary economic and population units.
 */
public final class City {

    private final String id;
    private final String name;
    private final String regionId;
    private final int tileX;
    private final int tileY;

    // --- Population ---
    private long   population;
    private double happiness;        // 0 - 100
    private double employmentRate;   // 0 - 100

    // --- Infrastructure ---
    private double infrastructure;   // 0 - 100
    private double digitalDefenses;  // 0 - 100
    private double socialCohesion;   // 0 - 100

    // --- Economy ---
    private double treasury;
    private double economicOutput;   // GDP per tick
    private double taxRate;          // 0.0 - 1.0

    public City(String id, String name, String regionId, int tileX, int tileY,
                long population, double treasury) {
        this.id              = id;
        this.name            = name;
        this.regionId        = regionId;
        this.tileX           = tileX;
        this.tileY           = tileY;
        this.population      = population;
        this.treasury        = treasury;
        this.happiness       = 70.0;
        this.employmentRate  = 80.0;
        this.infrastructure  = 60.0;
        this.digitalDefenses = 50.0;
        this.socialCohesion  = 75.0;
        this.economicOutput  = population * 0.001;
        this.taxRate         = 0.15;
    }

    // ---- Simulation: called each tick by the World ----

    public void simulateTick() {
        // GDP grows with infrastructure and employment
        economicOutput = population * 0.001
                * (infrastructure / 100.0)
                * (employmentRate / 100.0);

        // Treasury grows from tax revenue
        treasury += economicOutput * taxRate;

        // Infrastructure decays slowly
        infrastructure = clamp(infrastructure - 0.1, 0, 100);

        // Happiness influenced by employment and infrastructure
        double targetHappiness = (employmentRate * 0.5 + infrastructure * 0.3
                + socialCohesion * 0.2);
        happiness += (targetHappiness - happiness) * 0.1; // Smooth towards target
        happiness = clamp(happiness, 0, 100);

        // Population grows/shrinks based on happiness
        if (happiness > 60) {
            population += (long)(population * 0.001 * (happiness - 60) / 40.0);
        } else if (happiness < 40) {
            population -= (long)(population * 0.002 * (40 - happiness) / 40.0);
        }
        population = Math.max(100, population);
    }

    // ---- Modification methods (called by ActionProcessor) ----

    public void modifyInfrastructure(double amount) {
        infrastructure = clamp(infrastructure + amount, 0, 100);
    }

    public void modifyHappiness(double amount) {
        happiness = clamp(happiness + amount, 0, 100);
    }

    public void modifyDefenses(double amount) {
        digitalDefenses = clamp(digitalDefenses + amount, 0, 100);
    }

    public void modifySocialCohesion(double amount) {
        socialCohesion = clamp(socialCohesion + amount, 0, 100);
    }

    public double drainTreasury(double amount) {
        double drained = Math.min(amount, treasury);
        treasury -= drained;
        return drained;
    }

    public void injectCapital(double amount) {
        treasury += amount;
    }

    // ---- Serialization ----

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                id);
        m.put("name",              name);
        m.put("region_id",         regionId);
        m.put("tile_x",            tileX);
        m.put("tile_y",            tileY);
        m.put("population",        population);
        m.put("happiness",         round2(happiness));
        m.put("employment_rate",   round2(employmentRate));
        m.put("infrastructure",    round2(infrastructure));
        m.put("digital_defenses",  round2(digitalDefenses));
        m.put("social_cohesion",   round2(socialCohesion));
        m.put("treasury",          round2(treasury));
        m.put("economic_output",   round2(economicOutput));
        m.put("tax_rate",          taxRate);
        return m;
    }

    // ---- Getters ----

    public String getId()                { return id; }
    public String getName()              { return name; }
    public String getRegionId()          { return regionId; }
    public int    getTileX()             { return tileX; }
    public int    getTileY()             { return tileY; }
    public long   getPopulation()        { return population; }
    public double getHappiness()         { return happiness; }
    public double getEmploymentRate()    { return employmentRate; }
    public double getInfrastructure()    { return infrastructure; }
    public double getDigitalDefenses()   { return digitalDefenses; }
    public double getSocialCohesion()    { return socialCohesion; }
    public double getTreasury()          { return treasury; }
    public double getEconomicOutput()    { return economicOutput; }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}