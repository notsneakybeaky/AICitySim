package com.hyperinflation.net.protocol.s2c;

import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketDirection;

import java.util.LinkedHashMap;
import java.util.Map;

/** Delta update for a single city. */
public record S2CCityUpdate(
        String cityId,
        Map<String, Object> cityData
) implements Packet {

    @Override public int getPacketId() { return 0x03; }
    @Override public PacketDirection getDirection() { return PacketDirection.S2C; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("city_id", cityId);
        m.put("city",    cityData);
        return m;
    }
}