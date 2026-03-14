package com.hyperinflation.net.protocol.s2c;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.LinkedHashMap;
import java.util.Map;

/** Economy state update each tick. */
public record S2CEconomyTick(
        int tick,
        double promptPrice,
        double totalMarketValue,
        int totalSupply,
        int totalDemand,
        Map<String, Object> agentShares
) implements Packet {

    @Override public int getPacketId() { return 0x05; }
    @Override public PacketDirection getDirection() { return PacketDirection.S2C; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tick",               tick);
        m.put("prompt_price",       promptPrice);
        m.put("total_market_value", totalMarketValue);
        m.put("total_supply",       totalSupply);
        m.put("total_demand",       totalDemand);
        m.put("agent_shares",       agentShares);
        return m;
    }
}