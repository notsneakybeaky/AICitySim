package com.hyperinflation.net.protocol.c2s;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.Map;

/** Keep-alive response. */
public record C2SKeepAlive(long timestamp) implements Packet {

    @Override public int getPacketId() { return 0x02; }
    @Override public PacketDirection getDirection() { return PacketDirection.C2S; }

    @Override
    public Map<String, Object> serialize() {
        return Map.of("ts", timestamp);
    }

    public static Packet fromMap(Map<String, Object> data) {
        return new C2SKeepAlive(((Number) data.getOrDefault("ts", 0L)).longValue());
    }
}