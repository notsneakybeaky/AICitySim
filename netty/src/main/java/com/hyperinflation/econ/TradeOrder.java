package com.hyperinflation.econ;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A buy or sell order on the prompt marketplace.
 */
public final class TradeOrder {

    public enum Side { BID, ASK }

    private final String agentId;
    private final Side side;
    private final double price;
    private final int quantity;
    private final long timestamp;
    private int filledQuantity;

    public TradeOrder(String agentId, Side side, double price, int quantity) {
        this.agentId        = agentId;
        this.side           = side;
        this.price          = price;
        this.quantity        = quantity;
        this.timestamp       = System.currentTimeMillis();
        this.filledQuantity  = 0;
    }

    public boolean isFilled() { return filledQuantity >= quantity; }
    public int     getRemainingQuantity() { return quantity - filledQuantity; }
    public void    fill(int amount) { filledQuantity += amount; }

    public String  getAgentId()        { return agentId; }
    public Side    getSide()           { return side; }
    public double  getPrice()          { return price; }
    public int     getQuantity()       { return quantity; }
    public long    getTimestamp()       { return timestamp; }
    public int     getFilledQuantity() { return filledQuantity; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agent_id", agentId);
        m.put("side",     side.name());
        m.put("price",    price);
        m.put("quantity", quantity);
        m.put("filled",   filledQuantity);
        m.put("ts",       timestamp);
        return m;
    }
}