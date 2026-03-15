package com.hyperinflation.core;

import com.hyperinflation.econ.AgentEconomy;
import com.hyperinflation.econ.EconomyEngine;
import com.hyperinflation.world.City;
import com.hyperinflation.world.World;

import java.util.*;

/**
 * ACTION PROCESSOR
 * ================
 *
 * LOCATION COSTS:
 *   Agents pay a distance tax on city-targeted actions.
 *   Cost multiplier = 1.0 + (distance / 20.0)
 *   Adjacent cities (~5 tiles): ~1.25x cost
 *   Far cities (~20 tiles):     ~2.0x cost
 *   Same city:                   1.0x cost (no penalty)
 *
 *   This makes it expensive to attack from afar and rewards
 *   agents who position themselves near their target cities.
 */
public final class ActionProcessor {

    private final World         world;
    private final EconomyEngine economy;
    private final List<Map<String, Object>> events = new ArrayList<>();

    // Agent current locations — maps agentId to cityId
    private final Map<String, String> agentLocations = new LinkedHashMap<>();

    public ActionProcessor(World world, EconomyEngine economy) {
        this.world   = world;
        this.economy = economy;
    }

    public List<Map<String, Object>> getEvents() { return events; }
    public void clearEvents()                     { events.clear(); }

    /** Set an agent's current city location. */
    public void setAgentLocation(String agentId, String cityId) {
        agentLocations.put(agentId, cityId);
    }

    /** Get an agent's current city location. */
    public String getAgentLocation(String agentId) {
        return agentLocations.get(agentId);
    }

    public Map<String, String> getAllLocations() {
        return Collections.unmodifiableMap(agentLocations);
    }

    /**
     * Distance cost multiplier for acting on a target city.
     * If agent has no location set, no penalty.
     */
    private double distanceCostMultiplier(String agentId, String targetCityId) {
        String agentCity = agentLocations.get(agentId);
        if (agentCity == null || agentCity.equals(targetCityId)) return 1.0;
        double dist = world.getDistance(agentCity, targetCityId);
        return 1.0 + (dist / 20.0);
    }

    public void process(Action action) {
        if (action.getType() == Action.Type.NO_OP) return;

        String       agentId  = action.getAgentId();
        String       targetId = action.getTargetId();
        AgentEconomy econ     = agentId != null ? economy.getAgentEconomy(agentId) : null;
        City         city     = targetId != null ? world.getCity(targetId) : null;

        switch (action.getType()) {

            case PLACE_BID: {
                double price = action.param("price", 1.0);
                int    qty   = (int) action.param("quantity", 10);
                economy.placeBid(agentId, price, qty);
                event("BID", agentId, targetId,
                        agentId + " bid " + qty + " prompts @ $" + f(price));
                break;
            }

            case PLACE_ASK: {
                double price = action.param("price", 1.0);
                int    qty   = (int) action.param("quantity", 10);
                economy.placeAsk(agentId, price, qty);
                event("ASK", agentId, targetId,
                        agentId + " ask " + qty + " prompts @ $" + f(price));
                break;
            }

            case MOVE_TO: {
                // Agent moves to a new city
                // Cost: $1 per tile of distance
                if (targetId == null || world.getCity(targetId) == null) {
                    event("BLOCKED", agentId, targetId, agentId + " tried to move to unknown city");
                    break;
                }
                String fromCity = agentLocations.get(agentId);
                double dist     = fromCity != null ? world.getDistance(fromCity, targetId) : 0;
                double moveCost = Math.max(1.0, dist * 1.0);

                if (econ != null && !econ.spend(moveCost)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford move to " + targetId
                                    + " (cost $" + f(moveCost) + ")");
                    break;
                }
                agentLocations.put(agentId, targetId);
                event("MOVE", agentId, targetId,
                        agentId + " moved to " + world.getCity(targetId).getName()
                                + " (dist=" + f(dist) + " cost=$" + f(moveCost) + ")");
                break;
            }

            case BUILD_INFRASTRUCTURE: {
                if (city == null) break;
                double amt  = action.param("amount", 5.0);
                double cost = amt * 2.0 * distanceCostMultiplier(agentId, targetId);
                if (econ != null && !econ.spend(cost)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford BUILD in " + city.getName()
                                    + " (need $" + f(cost) + ")");
                    break;
                }
                city.adjustInfrastructure(amt);
                event("BUILD", agentId, targetId,
                        agentId + " built +" + f(amt) + " infra in " + city.getName()
                                + " (-$" + f(cost) + ")");
                break;
            }

            case DAMAGE_INFRASTRUCTURE: {
                if (city == null) break;
                double amt  = action.param("amount", 5.0);
                double cost = amt * 1.5 * distanceCostMultiplier(agentId, targetId);
                if (econ != null && !econ.spend(cost)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford SABOTAGE in " + city.getName()
                                    + " (need $" + f(cost) + ")");
                    break;
                }
                city.adjustInfrastructure(-amt);
                event("SABOTAGE", agentId, targetId,
                        agentId + " sabotaged " + city.getName() + " infra -" + f(amt)
                                + " (-$" + f(cost) + ")");
                break;
            }

            case BOOST_HAPPINESS: {
                if (city == null) break;
                double amt  = action.param("amount", 5.0);
                double cost = amt * 1.5 * distanceCostMultiplier(agentId, targetId);
                if (econ != null && !econ.spend(cost)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford BOOST in " + city.getName());
                    break;
                }
                city.adjustHappiness(amt);
                event("BOOST", agentId, targetId,
                        agentId + " boosted happiness in " + city.getName()
                                + " +" + f(amt) + " (-$" + f(cost) + ")");
                break;
            }

            case DAMAGE_HAPPINESS: {
                if (city == null) break;
                double amt  = action.param("amount", 5.0);
                double cost = amt * 1.0 * distanceCostMultiplier(agentId, targetId);
                if (econ != null && !econ.spend(cost)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford UNREST in " + city.getName()
                                    + " (need $" + f(cost) + ")");
                    break;
                }
                city.adjustHappiness(-amt);
                event("UNREST", agentId, targetId,
                        agentId + " caused unrest in " + city.getName() + " -" + f(amt)
                                + " (-$" + f(cost) + ")");
                break;
            }

            case ATTACK_CITY: {
                if (city == null) break;
                double power = action.param("power", 10.0);
                double cost  = power * 3.0 * distanceCostMultiplier(agentId, targetId);
                if (econ != null && !econ.spend(cost)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford ATTACK on " + city.getName());
                    break;
                }
                double effective = city.receiveAttack(power);
                event("ATTACK", agentId, targetId,
                        agentId + " attacked " + city.getName()
                                + " (pow=" + f(power) + " eff=" + f(effective)
                                + " def=" + f(city.getDigitalDefenses())
                                + " -$" + f(cost) + ")");
                break;
            }

            case DEFEND_CITY: {
                if (city == null) break;
                double amt  = action.param("amount", 5.0);
                double cost = amt * 1.5 * distanceCostMultiplier(agentId, targetId);
                if (econ != null && !econ.spend(cost)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford DEFEND in " + city.getName());
                    break;
                }
                city.adjustDefenses(amt);
                event("DEFEND", agentId, targetId,
                        agentId + " fortified " + city.getName() + " +" + f(amt));
                break;
            }

            case INFILTRATE: {
                if (city == null) break;
                double amt  = action.param("amount", 3.0);
                double cost = amt * 2.0 * distanceCostMultiplier(agentId, targetId);
                if (econ != null && !econ.spend(cost)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford INFILTRATE in " + city.getName()
                                    + " (need $" + f(cost) + ")");
                    break;
                }
                double effective = city.receiveInfiltrate(amt);
                event("INFILTRATE", agentId, targetId,
                        agentId + " infiltrated " + city.getName()
                                + " (req=" + f(amt) + " eff=" + f(effective)
                                + " -$" + f(cost) + ")");
                break;
            }

            case SPREAD_PROPAGANDA: {
                if (city == null) break;
                double amt  = action.param("amount", 3.0);
                double cost = amt * 1.5 * distanceCostMultiplier(agentId, targetId);
                if (econ != null && !econ.spend(cost)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford PROPAGANDA in " + city.getName()
                                    + " (need $" + f(cost) + ")");
                    break;
                }
                double effective = city.receivePropaganda(amt);
                event("PROPAGANDA", agentId, targetId,
                        agentId + " spread propaganda in " + city.getName()
                                + " (req=" + f(amt) + " eff=" + f(effective)
                                + " -$" + f(cost) + ")");
                break;
            }

            case DRAIN_TREASURY: {
                if (city == null) break;
                double amt      = action.param("amount", 10.0);
                double mult     = distanceCostMultiplier(agentId, targetId);
                double overhead = amt * 0.2 * mult;
                if (econ != null && !econ.spend(overhead)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford DRAIN on " + city.getName()
                                    + " (overhead $" + f(overhead) + ")");
                    break;
                }
                double actual = Math.min(amt, Math.max(0, city.getTreasury()));
                city.adjustTreasury(-actual);
                if (econ != null) econ.earn(actual);
                event("DRAIN", agentId, targetId,
                        agentId + " drained $" + f(actual) + " from " + city.getName()
                                + " (overhead -$" + f(overhead) + ")");
                break;
            }

            case INJECT_CAPITAL: {
                if (city == null) break;
                double amt = action.param("amount", 10.0);
                if (econ != null && !econ.spend(amt)) {
                    event("BLOCKED", agentId, targetId,
                            agentId + " cannot afford INJECT into " + city.getName());
                    break;
                }
                city.adjustTreasury(amt);
                event("INJECT", agentId, targetId,
                        agentId + " injected $" + f(amt) + " into " + city.getName());
                break;
            }

            case FORM_ALLIANCE: {
                String terms = action.paramStr("terms", "mutual cooperation");
                event("ALLIANCE", agentId, targetId,
                        agentId + " proposed alliance with " + targetId
                                + ": \"" + terms + "\"");
                break;
            }

            case BREAK_ALLIANCE: {
                event("ALLIANCE_BREAK", agentId, targetId,
                        agentId + " broke alliance with " + targetId);
                break;
            }

            default:
                break;
        }
    }

    private void event(String type, String agent, String target, String desc) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type",        type);
        e.put("agent",       agent);
        e.put("target",      target);
        e.put("source_city", agent != null ? agentLocations.get(agent) : null);
        e.put("description", desc);
        e.put("ts",          System.currentTimeMillis());
        events.add(e);
    }

    private static String f(double v) { return String.format("%.1f", v); }
}