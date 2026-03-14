package com.hyperinflation.net.protocol.s2c;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.Map;

/** Keep-alive ping. Client must respond with C2SKeepAlive. */
public record S2CKeepAlive(long timestamp) implements Packet {

    @Override public int getPacketId() { return 0x09; }
    @Override public PacketDirection getDirection() { return PacketDirection.S2C; }

    @Override
    public Map<String, Object> serialize() {
        return Map.of("ts", timestamp);
    }
}