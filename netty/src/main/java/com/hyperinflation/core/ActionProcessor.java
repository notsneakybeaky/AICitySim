package com.hyperinflation.core;

import com.hyperinflation.econ.EconomyEngine;
import com.hyperinflation.world.City;
import com.hyperinflation.world.World;

import java.util.*;

public final class ActionProcessor {

    private final World world;
    private final EconomyEngine economy;
    private final List<Map<String, Object>> events = new ArrayList<>();

    public ActionProcessor(World world, EconomyEngine economy) {
        this.world   = world;
        this.economy = economy;
    }

    public List<Map<String, Object>> getEvents() { return events; }
    public void clearEvents()                     { events.clear(); }

    public void process(Action action) {
        if (action.getType() == Action.Type.NO_OP) return;

        String agentId                   = action.getAgentId();
        String targetId                  = action.getTargetId();
        EconomyEngine.AgentEconomy econ  = agentId != null ? economy.getAgentEconomy(agentId) : null;
        City city                        = targetId != null ? world.getCity(targetId) : null;

        switch (action.getType()) {

            case PLACE_BID: {
                double price = action.param("price", 1.0);
                int qty      = (int) action.param("quantity", 10);
                economy.placeBid(agentId, price, qty);
                event("BID", agentId, targetId,
                        agentId + " bid " + qty + " prompts @ $" + f(price));
                break;
            }

            case BUILD_INFRASTRUCTURE: {
                if (city == null) break;
                double amt  = action.param("amount", 5.0);
                double cost = amt * 2.0;
                if (econ != null) econ.spend(cost);
                city.adjustInfrastructure(amt);
                event("BUILD", agentId, targetId,
                        agentId + " built +" + f(amt) + " infra in " + city.getName()
                                + " (-$" + f(cost) + ")");
                break;
            }

            case DAMAGE_INFRASTRUCTURE: {
                if (city == null) break;
                double amt = action.param("amount", 5.0);
                city.adjustInfrastructure(-amt);
                event("SABOTAGE", agentId, targetId,
                        agentId + " sabotaged " + city.getName() + " infra -" + f(amt));
                break;
            }

            case BOOST_HAPPINESS: {
                if (city == null) break;
                double amt  = action.param("amount", 5.0);
                double cost = amt * 1.5;
                if (econ != null) econ.spend(cost);
                city.adjustHappiness(amt);
                event("BOOST", agentId, targetId,
                        agentId + " boosted happiness in " + city.getName()
                                + " +" + f(amt) + " (-$" + f(cost) + ")");
                break;
            }

            case DAMAGE_HAPPINESS: {
                if (city == null) break;
                double amt = action.param("amount", 5.0);
                city.adjustHappiness(-amt);
                event("UNREST", agentId, targetId,
                        agentId + " caused unrest in " + city.getName() + " -" + f(amt));
                break;
            }

            case ATTACK_CITY: {
                if (city == null) break;
                double power = action.param("power", 10.0);
                double cost  = power * 3.0;
                if (econ != null) econ.spend(cost);
                double effective = city.receiveAttack(power);
                event("ATTACK", agentId, targetId,
                        agentId + " attacked " + city.getName()
                                + " (pow=" + f(power) + " eff=" + f(effective) + " -$" + f(cost) + ")");
                break;
            }

            case DEFEND_CITY: {
                if (city == null) break;
                double amt  = action.param("amount", 5.0);
                double cost = amt * 1.5;
                if (econ != null) econ.spend(cost);
                city.adjustDefenses(amt);
                event("DEFEND", agentId, targetId,
                        agentId + " fortified " + city.getName() + " +" + f(amt));
                break;
            }

            case INFILTRATE: {
                if (city == null) break;
                double amt = action.param("amount", 3.0);
                city.adjustDefenses(-amt);
                event("INFILTRATE", agentId, targetId,
                        agentId + " infiltrated " + city.getName() + " defenses -" + f(amt));
                break;
            }

            case SPREAD_PROPAGANDA: {
                if (city == null) break;
                double amt = action.param("amount", 3.0);
                city.adjustSocialCohesion(-amt);
                event("PROPAGANDA", agentId, targetId,
                        agentId + " spread propaganda in " + city.getName()
                                + " cohesion -" + f(amt));
                break;
            }

            case DRAIN_TREASURY: {
                if (city == null) break;
                double amt    = action.param("amount", 10.0);
                double actual = Math.min(amt, Math.max(0, city.getTreasury()));
                city.adjustTreasury(-actual);
                if (econ != null) econ.earn(actual);
                event("DRAIN", agentId, targetId,
                        agentId + " drained $" + f(actual) + " from " + city.getName());
                break;
            }

            case INJECT_CAPITAL: {
                if (city == null) break;
                double amt = action.param("amount", 10.0);
                if (econ != null) econ.spend(amt);
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
        e.put("description", desc);
        e.put("ts",          System.currentTimeMillis());
        events.add(e);
    }

    private static String f(double v) { return String.format("%.1f", v); }
}