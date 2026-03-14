package com.hyperinflation.net.protocol.s2c;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.LinkedHashMap;
import java.util.Map;

/** Notifies clients of a simulation phase change. */
public record S2CPhaseChange(
        int tick,
        String phase,
        Map<String, Object> worldSummary
) implements Packet {

    @Override public int getPacketId() { return 0x07; }
    @Override public PacketDirection getDirection() { return PacketDirection.S2C; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tick",          tick);
        m.put("phase",         phase);
        m.put("world_summary", worldSummary);
        return m;
    }
}