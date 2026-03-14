package com.hyperinflation.net.protocol;

import java.util.Map;

/**
 * Base interface for all packets. Inspired by Minecraft's packet system.
 * Each packet has a unique ID within its direction and can serialize to a Map.
 */
public interface Packet {

    /** Unique packet ID (within its direction). */
    int getPacketId();

    /** S2C or C2S. */
    PacketDirection getDirection();

    /** Serialize to a Map for JSON encoding. */
    Map<String, Object> serialize();
}