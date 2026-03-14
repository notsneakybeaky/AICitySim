package com.hyperinflation.net.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes Packets to JSON strings and decodes JSON strings to packet data.
 *
 * Wire format (JSON):
 * {
 *   "pid": 0x01,          // Packet ID
 *   "d":   { ... }        // Packet data
 * }
 */
public final class PacketCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Encode a Packet to a JSON string for sending over WebSocket. */
    public static String encode(Packet packet) {
        try {
            Map<String, Object> wire = new LinkedHashMap<>();
            wire.put("pid", packet.getPacketId());
            wire.put("d",   packet.serialize());
            return MAPPER.writeValueAsString(wire);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode packet " + packet.getPacketId(), e);
        }
    }

    /**
     * Decode a raw JSON string into a packet ID and data map.
     * Returns a DecodedPacket with the ID and data.
     */
    @SuppressWarnings("unchecked")
    public static DecodedPacket decode(String json) {
        try {
            Map<String, Object> wire = MAPPER.readValue(json, Map.class);
            int packetId = ((Number) wire.get("pid")).intValue();
            Map<String, Object> data = (Map<String, Object>) wire.getOrDefault("d", Map.of());
            return new DecodedPacket(packetId, data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode packet: " + json, e);
        }
    }

    /** Simple record for decoded wire data. */
    public record DecodedPacket(int packetId, Map<String, Object> data) {}

    private PacketCodec() {}
}