package com.hyperinflation.world;

import java.util.*;

/**
 * CITY STATS SHEET
 * ================
 *
 * HAPPINESS (0-100)
 *   - Drives global market DEMAND. Happy cities buy more prompts.
 *   - Demand multiplier: 0.5 + (happiness/200) = range 0.5x to 1.0x
 *   - BUT: if social cohesion < 40, happiness effect is INVERTED.
 *     A manipulated population doesn't spend even if nominally happy.
 *   - Drifts toward: (employment*0.4) + (infrastructure*0.3) + (cohesion*0.3)
 *
 * INFRASTRUCTURE (0-100)
 *   - Directly contributes to prompt SUPPLY capacity.
 *   - Supply contribution: (infra/100) * log(population) * 500
 *   - Creates job demand: more infra = more workers needed.
 *   - If infra high but employment low = labor shortage = happiness penalty.
 *   - Decays -0.15/tick. Must invest to maintain.
 *
 * DIGITAL DEFENSES (0-100)
 *   - Reduces attack effectiveness as hard percentage: effective = power*(1-defenses/100)
 *   - 100 defenses = immune to attacks. 0 defenses = full damage.
 *   - Also reduces infiltrate (50% as effective) and propaganda (30% as effective).
 *   - Decays -0.05/tick. Must invest to maintain.
 *
 * SOCIAL COHESION (0-100)
 *   - HIGH (>60): amplifies happiness demand bonus by up to +20%.
 *   - LOW (<40): INVERTS happiness. Happy but divided city suppresses demand.
 *   - Drifts toward current happiness value (happiness pulls cohesion).
 *   - Propaganda pushes cohesion down against the drift.
 *
 * EMPLOYMENT (0-100)
 *   - Target: 30 + (infra*0.5) + (happiness*0.2)
 *   - High infra + low employment = labor gap = happiness penalty.
 *   - Feeds economic output and tax revenue.
 *
 * ECONOMIC OUTPUT (computed each tick)
 *   - GDP = log(pop) * infra * happiness * employment (all normalized)
 *   - Feeds city treasury via taxRate.
 *   - Feeds global demand pool in EconomyEngine.
 */
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

    private double supplyContribution;
    private double demandMultiplier;

    public City(String id, String name, String regionId, int tileX, int tileY,
                int population, double happiness, double infrastructure, double taxRate) {
        this.id               = id;
        this.name             = name;
        this.regionId         = regionId;
        this.tileX            = tileX;
        this.tileY            = tileY;
        this.population       = population;
        this.happiness        = happiness;
        this.employmentRate   = 65.0;
        this.infrastructure   = infrastructure;
        this.digitalDefenses  = 50.0;
        this.socialCohesion   = 70.0;
        this.treasury         = 100.0;
        this.economicOutput   = 0.0;
        this.taxRate          = taxRate;
        this.supplyContribution = 0.0;
        this.demandMultiplier   = 1.0;
    }

    // ---- Mutations ----
    public void adjustHappiness(double d)      { happiness       = clamp(happiness + d, 0, 100); }
    public void adjustInfrastructure(double d)  { infrastructure  = clamp(infrastructure + d, 0, 100); }
    public void adjustDefenses(double d)        { digitalDefenses = clamp(digitalDefenses + d, 0, 100); }
    public void adjustSocialCohesion(double d)  { socialCohesion  = clamp(socialCohesion + d, 0, 100); }
    public void adjustTreasury(double d)        { treasury += d; }
    public void adjustPopulation(int d)         { population = Math.max(0, population + d); }

    // =====================================================================
    //  TICK
    // =====================================================================

    public void tick() {
        // 1. Infrastructure decays
        infrastructure = clamp(infrastructure - 0.15, 0, 100);

        // 2. Defense decays
        digitalDefenses = clamp(digitalDefenses - 0.05, 0, 100);

        // 3. Social cohesion drifts toward happiness
        socialCohesion = clamp(socialCohesion + (happiness - socialCohesion) * 0.03, 0, 100);

        // 4. Employment — infrastructure demands workers
        double targetEmp = 30.0 + (infrastructure * 0.5) + (happiness * 0.2);
        employmentRate   = clamp(employmentRate + (targetEmp - employmentRate) * 0.05, 0, 100);

        // 5. Labor shortage penalty
        double laborGap = Math.max(0, (infrastructure * 0.5) - employmentRate);
        if (laborGap > 20) {
            happiness = clamp(happiness - (laborGap - 20) * 0.02, 0, 100);
        }

        // 6. Population dynamics
        if (happiness > 60 && employmentRate > 55) {
            population += (int) (population * 0.001);
        } else if (happiness < 30 || employmentRate < 25) {
            population -= (int) (population * 0.002);
            population  = Math.max(100, population);
        }

        // 7. Economic output
        double popF    = Math.log10(Math.max(1, population)) / 7.0;
        double infraF  = infrastructure / 100.0;
        double happyF  = happiness / 100.0;
        double empF    = employmentRate / 100.0;
        economicOutput = popF * infraF * happyF * empF * 50.0;

        // 8. Tax revenue
        treasury += economicOutput * taxRate;

        // 9. Happiness equilibrium
        double happyTarget = (employmentRate * 0.4) + (infrastructure * 0.3) + (socialCohesion * 0.3);
        happiness = clamp(happiness + (happyTarget - happiness) * 0.02, 0, 100);

        // 10. Compute market contributions
        computeMarketContributions();
    }

    private void computeMarketContributions() {
        // Supply: infrastructure * population scale
        double popScale = Math.log10(Math.max(1, population)) / 7.0;
        supplyContribution = (infrastructure / 100.0) * popScale * 500.0;

        // Demand: happiness modified by social cohesion
        double rawDemand = 0.5 + (happiness / 200.0); // 0.5 to 1.0

        if (socialCohesion < 40) {
            // INVERSION: low cohesion flips happiness effect
            double inversionStrength = (40 - socialCohesion) / 40.0;
            rawDemand = rawDemand * (1.0 - inversionStrength)
                      + (1.0 - rawDemand) * inversionStrength;
        } else if (socialCohesion > 60) {
            // AMPLIFICATION: high cohesion boosts demand
            double amplification = (socialCohesion - 60) / 200.0;
            rawDemand = Math.min(1.2, rawDemand + amplification);
        }

        demandMultiplier = rawDemand;
    }

    // =====================================================================
    //  COMBAT
    // =====================================================================

    /** Attack: defenses reduce as hard percentage. Returns effective damage. */
    public double receiveAttack(double power) {
        double effective = power * (1.0 - digitalDefenses / 100.0);
        effective = Math.max(0, effective);
        adjustInfrastructure(-effective * 0.50);
        adjustHappiness(-effective * 0.25);
        adjustDefenses(-effective * 0.15);
        adjustSocialCohesion(-effective * 0.10);
        return effective;
    }

    /** Infiltrate: defenses half as effective vs covert ops. */
    public double receiveInfiltrate(double amount) {
        double effective = amount * (1.0 - digitalDefenses / 200.0);
        adjustDefenses(-effective);
        return effective;
    }

    /** Propaganda: defenses partially resist. */
    public double receivePropaganda(double amount) {
        double effective = amount * (1.0 - digitalDefenses / 333.0);
        adjustSocialCohesion(-effective);
        return effective;
    }

    // =====================================================================
    //  DISTANCE
    // =====================================================================

    public double distanceTo(City other) {
        double dx = this.tileX - other.tileX;
        double dy = this.tileY - other.tileY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // =====================================================================
    //  GETTERS
    // =====================================================================

    public String getId()                 { return id; }
    public String getName()               { return name; }
    public String getRegionId()           { return regionId; }
    public int    getTileX()              { return tileX; }
    public int    getTileY()              { return tileY; }
    public int    getPopulation()         { return population; }
    public double getHappiness()          { return happiness; }
    public double getEmploymentRate()     { return employmentRate; }
    public double getInfrastructure()     { return infrastructure; }
    public double getDigitalDefenses()    { return digitalDefenses; }
    public double getSocialCohesion()     { return socialCohesion; }
    public double getTreasury()           { return treasury; }
    public double getEconomicOutput()     { return economicOutput; }
    public double getTaxRate()            { return taxRate; }
    public double getSupplyContribution() { return supplyContribution; }
    public double getDemandMultiplier()   { return demandMultiplier; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name",               name);
        m.put("region_id",          regionId);
        m.put("tile_x",             tileX);
        m.put("tile_y",             tileY);
        m.put("population",         population);
        m.put("happiness",          r2(happiness));
        m.put("employment_rate",    r2(employmentRate));
        m.put("infrastructure",     r2(infrastructure));
        m.put("digital_defenses",   r2(digitalDefenses));
        m.put("social_cohesion",    r2(socialCohesion));
        m.put("treasury",           r2(treasury));
        m.put("economic_output",    r2(economicOutput));
        m.put("tax_rate",           taxRate);
        m.put("supply_contribution", r2(supplyContribution));
        m.put("demand_multiplier",   r2(demandMultiplier));
        return m;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
