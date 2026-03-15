package com.hyperinflation.net.protocol.s2c;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.LinkedHashMap;
import java.util.Map;

/** Full world state snapshot. Sent on connect and periodically. */
public record S2CWorldSnapshot(
        int tick,
        Map<String, Object> worldData,
        Map<String, Object> economyData,
        Map<String, String> locations
) implements Packet {

    @Override public int getPacketId() { return 0x02; }
    @Override public PacketDirection getDirection() { return PacketDirection.S2C; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tick",      tick);
        m.put("world",     worldData);
        m.put("economy",   economyData);
        m.put("locations", locations);
        return m;
    }
}