package com.hyperinflation.net.protocol.s2c;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Acknowledges a client's handshake. Tells them their connection state. */
public record S2CHandshakeAck(
        String clientId,
        String assignedState,
        int currentTick,
        List<String> activeModules
) implements Packet {

    @Override public int getPacketId() { return 0x01; }
    @Override public PacketDirection getDirection() { return PacketDirection.S2C; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("client_id",      clientId);
        m.put("state",          assignedState);
        m.put("current_tick",   currentTick);
        m.put("active_modules", activeModules);
        return m;
    }
}