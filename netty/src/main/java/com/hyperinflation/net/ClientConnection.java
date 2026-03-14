package com.hyperinflation.net;

import com.hyperinflation.net.protocol.ConnectionState;
import com.hyperinflation.net.protocol.Packet;
import com.hyperinflation.net.protocol.PacketCodec;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Represents a single connected client. Holds per-client state.
 */
public final class ClientConnection {

    private final String clientId;
    private final Channel channel;
    private ConnectionState state;
    private String clientName;
    private long lastKeepAlive;
    private final Set<String> subscribedChannels = new CopyOnWriteArraySet<>();

    public ClientConnection(String clientId, Channel channel) {
        this.clientId      = clientId;
        this.channel       = channel;
        this.state         = ConnectionState.HANDSHAKE;
        this.clientName    = "unknown";
        this.lastKeepAlive = System.currentTimeMillis();
        this.subscribedChannels.add("all"); // Default subscription
    }

    /** Send a packet to this client. */
    public void sendPacket(Packet packet) {
        if (channel.isActive()) {
            String json = PacketCodec.encode(packet);
            channel.writeAndFlush(new TextWebSocketFrame(json));
        }
    }

    /** Check if this client is subscribed to a specific channel. */
    public boolean isSubscribedTo(String channel) {
        return subscribedChannels.contains("all") || subscribedChannels.contains(channel);
    }

    // Getters and setters
    public String          getClientId()            { return clientId; }
    public Channel         getChannel()             { return channel; }
    public ConnectionState getState()               { return state; }
    public void            setState(ConnectionState s) { this.state = s; }
    public String          getClientName()          { return clientName; }
    public void            setClientName(String n)  { this.clientName = n; }
    public long            getLastKeepAlive()       { return lastKeepAlive; }
    public void            touchKeepAlive()         { this.lastKeepAlive = System.currentTimeMillis(); }
    public Set<String>     getSubscribedChannels()  { return subscribedChannels; }

    public void setSubscriptions(List<String> channels) {
        subscribedChannels.clear();
        subscribedChannels.addAll(channels);
    }

    public boolean isActive() {
        return channel.isActive() && state != ConnectionState.CLOSING;
    }
}