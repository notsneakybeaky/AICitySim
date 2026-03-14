package com.hyperinflation.net.protocol.s2c;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.LinkedHashMap;
import java.util.Map;

/** Delta update for a single agent. */
public record S2CAgentUpdate(
        String agentId,
        Map<String, Object> agentData
) implements Packet {

    @Override public int getPacketId() { return 0x04; }
    @Override public PacketDirection getDirection() { return PacketDirection.S2C; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agent_id", agentId);
        m.put("agent",    agentData);
        return m;
    }
}