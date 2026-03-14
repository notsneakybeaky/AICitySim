package com.hyperinflation.net.protocol.c2s;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.LinkedHashMap;
import java.util.Map;

/** Player submits an action (god mode, manual city management, etc). */
public record C2SPlayerAction(
        String actionType,
        String targetId,
        Map<String, Object> params
) implements Packet {

    @Override public int getPacketId() { return 0x04; }
    @Override public PacketDirection getDirection() { return PacketDirection.C2S; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action_type", actionType);
        m.put("target_id",   targetId);
        m.put("params",      params);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Packet fromMap(Map<String, Object> data) {
        return new C2SPlayerAction(
                (String) data.getOrDefault("action_type", "NO_OP"),
                (String) data.getOrDefault("target_id", ""),
                (Map<String, Object>) data.getOrDefault("params", Map.of())
        );
    }
}