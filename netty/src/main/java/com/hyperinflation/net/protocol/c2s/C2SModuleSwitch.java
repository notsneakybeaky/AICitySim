package com.hyperinflation.net.protocol.c2s;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.Map;

/** Client requests a module switch. Only valid in PLAY state. */
public record C2SModuleSwitch(String targetModuleId) implements Packet {

    @Override public int getPacketId() { return 0x03; }
    @Override public PacketDirection getDirection() { return PacketDirection.C2S; }

    @Override
    public Map<String, Object> serialize() {
        return Map.of("module_id", targetModuleId);
    }

    public static Packet fromMap(Map<String, Object> data) {
        return new C2SModuleSwitch((String) data.getOrDefault("module_id", ""));
    }
}