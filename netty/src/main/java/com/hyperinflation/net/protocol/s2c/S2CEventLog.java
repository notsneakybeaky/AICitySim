package com.hyperinflation.net.protocol.s2c;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A batch of events that occurred during a tick. */
public record S2CEventLog(
        int tick,
        List<Map<String, Object>> events
) implements Packet {

    @Override public int getPacketId() { return 0x06; }
    @Override public PacketDirection getDirection() { return PacketDirection.S2C; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tick",   tick);
        m.put("events", events);
        return m;
    }
}