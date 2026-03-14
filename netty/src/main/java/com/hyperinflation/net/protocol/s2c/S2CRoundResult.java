package com.hyperinflation.net.protocol.s2c;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Full result of a simulation tick. */
public record S2CRoundResult(
        int tick,
        Map<String, Object> worldState,
        Map<String, Object> economyState,
        List<Map<String, Object>> events
) implements Packet {

    @Override public int getPacketId() { return 0x08; }
    @Override public PacketDirection getDirection() { return PacketDirection.S2C; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tick",    tick);
        m.put("world",   worldState);
        m.put("economy", economyState);
        m.put("events",  events);
        return m;
    }
}