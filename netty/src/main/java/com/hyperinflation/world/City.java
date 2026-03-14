package com.hyperinflation.world;

import java.util.*;

public final class City {

    private final String id;
    private final String name;
    private final String regionId;
    private final int    tileX, tileY;

    private int    population;
    private double happiness;
    private double employmentRate;
    private double infrastructure;
    private double digitalDefenses;
    private double socialCohesion;
    private double treasury;
    private double economicOutput;
    private double taxRate;

    public City(String id, String name, String regionId, int tileX, int tileY,
                int population, double happiness, double infrastructure, double taxRate) {
        this.id              = id;
        this.name            = name;
        this.regionId        = regionId;
        this.tileX           = tileX;
        this.tileY           = tileY;
        this.population      = population;
        this.happiness       = happiness;
        this.employmentRate  = 65.0;
        this.infrastructure  = infrastructure;
        this.digitalDefenses = 50.0;
        this.socialCohesion  = 70.0;
        this.treasury        = 100.0;
        this.economicOutput  = 0.0;
        this.taxRate         = taxRate;
    }

    // ---- Mutations ----

    public void adjustHappiness(double d)       { happiness       = clamp(happiness + d, 0, 100); }
    public void adjustInfrastructure(double d)   { infrastructure  = clamp(infrastructure + d, 0, 100); }
    public void adjustDefenses(double d)         { digitalDefenses = clamp(digitalDefenses + d, 0, 100); }
    public void adjustSocialCohesion(double d)   { socialCohesion  = clamp(socialCohesion + d, 0, 100); }
    public void adjustTreasury(double d)         { treasury += d; }
    public void adjustPopulation(int d)          { population = Math.max(0, population + d); }

    /** Natural dynamics each tick. */
    public void tick() {
        // Infrastructure decays
        infrastructure = clamp(infrastructure - 0.15, 0, 100);

        // Cohesion drifts toward 50
        socialCohesion = clamp(socialCohesion + (50 - socialCohesion) * 0.01, 0, 100);

        // Population: grows if happy, shrinks if miserable
        if (happiness > 60) {
            population += (int) (population * 0.001);
        } else if (happiness < 30) {
            population -= (int) (population * 0.002);
            population = Math.max(100, population);
        }

        // Economic output
        double popF   = Math.log10(Math.max(1, population)) / 7.0;
        double infraF = infrastructure / 100.0;
        double happyF = happiness / 100.0;
        double empF   = employmentRate / 100.0;
        economicOutput = popF * infraF * happyF * empF * 50.0;

        // Employment shifts toward target
        double targetEmp = 40 + (infrastructure * 0.3) + (happiness * 0.2);
        employmentRate = clamp(employmentRate + (targetEmp - employmentRate) * 0.05, 0, 100);

        // Tax revenue
        treasury += economicOutput * taxRate;

        // Happiness equilibrium
        double happyTarget = (employmentRate * 0.4) + (infrastructure * 0.3) + (socialCohesion * 0.3);
        happiness = clamp(happiness + (happyTarget - happiness) * 0.02, 0, 100);
    }

    /** Attack: returns effective damage dealt. */
    public double receiveAttack(double power) {
        double effective = power * (1.0 - digitalDefenses / 200.0);
        effective = Math.max(0, effective);
        adjustInfrastructure(-effective * 0.5);
        adjustHappiness(-effective * 0.3);
        adjustDefenses(-effective * 0.2);
        adjustSocialCohesion(-effective * 0.2);
        return effective;
    }

    // ---- Getters ----

    public String getId()                { return id; }
    public String getName()              { return name; }
    public String getRegionId()          { return regionId; }
    public int    getPopulation()        { return population; }
    public double getHappiness()         { return happiness; }
    public double getEmploymentRate()    { return employmentRate; }
    public double getInfrastructure()    { return infrastructure; }
    public double getDigitalDefenses()   { return digitalDefenses; }
    public double getSocialCohesion()    { return socialCohesion; }
    public double getTreasury()          { return treasury; }
    public double getEconomicOutput()    { return economicOutput; }
    public double getTaxRate()           { return taxRate; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name",             name);
        m.put("region_id",        regionId);
        m.put("tile_x",           tileX);
        m.put("tile_y",           tileY);
        m.put("population",       population);
        m.put("happiness",        r2(happiness));
        m.put("employment_rate",  r2(employmentRate));
        m.put("infrastructure",   r2(infrastructure));
        m.put("digital_defenses", r2(digitalDefenses));
        m.put("social_cohesion",  r2(socialCohesion));
        m.put("treasury",         r2(treasury));
        m.put("economic_output",  r2(economicOutput));
        m.put("tax_rate",         taxRate);
        return m;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}