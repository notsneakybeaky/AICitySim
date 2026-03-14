package com.hyperinflation.net.protocol.c2s;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.List;
import java.util.Map;

/**
 * Client subscribes to specific data channels.
 * This lets Flutter request only the data it needs for its current view.
 */
public record C2SSubscribe(List<String> channels) implements Packet {

    @Override public int getPacketId() { return 0x05; }
    @Override public PacketDirection getDirection() { return PacketDirection.C2S; }

    @Override
    public Map<String, Object> serialize() {
        return Map.of("channels", channels);
    }

    @SuppressWarnings("unchecked")
    public static Packet fromMap(Map<String, Object> data) {
        List<String> channels = (List<String>) data.getOrDefault("channels", List.of("all"));
        return new C2SSubscribe(channels);
    }
}