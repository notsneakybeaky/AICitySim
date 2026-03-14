package com.hyperinflation.net.protocol;

import com.hyperinflation.net.protocol.c2s.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Central registry mapping packet IDs to constructors.
 * This is where you register new packet types.
 */
public final class PacketRegistry {

    // C2S deserializers: packetId → function that builds a Packet from a data map
    private static final Map<Integer, Function<Map<String, Object>, Packet>> C2S_REGISTRY = new HashMap<>();

    static {
        // Register all Client-to-Server packets
        C2S_REGISTRY.put(0x01, C2SHandshake::fromMap);
        C2S_REGISTRY.put(0x02, C2SKeepAlive::fromMap);
        C2S_REGISTRY.put(0x03, C2SModuleSwitch::fromMap);
        C2S_REGISTRY.put(0x04, C2SPlayerAction::fromMap);
        C2S_REGISTRY.put(0x05, C2SSubscribe::fromMap);
    }

    /** Deserialize a C2S packet from raw JSON data. */
    public static Packet deserializeC2S(int packetId, Map<String, Object> data) {
        Function<Map<String, Object>, Packet> builder = C2S_REGISTRY.get(packetId);
        if (builder == null) {
            throw new IllegalArgumentException("Unknown C2S packet ID: 0x"
                    + Integer.toHexString(packetId));
        }
        return builder.apply(data);
    }

    /** Check if a C2S packet ID is registered. */
    public static boolean isKnownC2S(int packetId) {
        return C2S_REGISTRY.containsKey(packetId);
    }

    private PacketRegistry() {}
}