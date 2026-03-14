package com.hyperinflation.net.protocol.c2s;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client hello. Identifies the client and requests a connection state. */
public record C2SHandshake(
        String clientName,
        String requestedState,  // "PLAY" or "SPECTATE"
        int protocolVersion
) implements Packet {

    public static final int CURRENT_PROTOCOL_VERSION = 1;

    @Override public int getPacketId() { return 0x01; }
    @Override public PacketDirection getDirection() { return PacketDirection.C2S; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("client_name",      clientName);
        m.put("requested_state",  requestedState);
        m.put("protocol_version", protocolVersion);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Packet fromMap(Map<String, Object> data) {
        return new C2SHandshake(
                (String) data.getOrDefault("client_name", "unknown"),
                (String) data.getOrDefault("requested_state", "SPECTATE"),
                ((Number) data.getOrDefault("protocol_version", 1)).intValue()
        );
    }
}