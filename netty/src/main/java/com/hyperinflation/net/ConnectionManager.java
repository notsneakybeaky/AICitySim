package com.hyperinflation.net;

import com.hyperinflation.net.protocol.ConnectionState;
import com.hyperinflation.net.protocol.Packet;
import io.netty.channel.Channel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all client connections. Thread-safe.
 */
public final class ConnectionManager {

    private final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();
    private final Map<Channel, String> channelToId = new ConcurrentHashMap<>();

    private static int clientCounter = 0;

    /** Create a new connection when a WebSocket handshake completes. */
    public ClientConnection addConnection(Channel channel) {
        String clientId = "client-" + (++clientCounter);
        ClientConnection conn = new ClientConnection(clientId, channel);
        connections.put(clientId, conn);
        channelToId.put(channel, clientId);
        System.out.println("[NET] Connection added: " + clientId
                + " from " + channel.remoteAddress());
        return conn;
    }

    /** Remove a connection when a client disconnects. */
    public void removeConnection(Channel channel) {
        String clientId = channelToId.remove(channel);
        if (clientId != null) {
            connections.remove(clientId);
            System.out.println("[NET] Connection removed: " + clientId);
        }
    }

    /** Get a connection by its Netty channel. */
    public ClientConnection getConnection(Channel channel) {
        String clientId = channelToId.get(channel);
        return clientId != null ? connections.get(clientId) : null;
    }

    /** Get a connection by client ID. */
    public ClientConnection getConnection(String clientId) {
        return connections.get(clientId);
    }

    /** Broadcast a packet to all connections in PLAY or SPECTATE state. */
    public void broadcastPacket(Packet packet) {
        for (ClientConnection conn : connections.values()) {
            if (conn.isActive()
                    && (conn.getState() == ConnectionState.PLAY
                    || conn.getState() == ConnectionState.SPECTATE)) {
                conn.sendPacket(packet);
            }
        }
    }

    /** Broadcast a packet only to clients subscribed to a specific channel. */
    public void broadcastToChannel(String channelName, Packet packet) {
        for (ClientConnection conn : connections.values()) {
            if (conn.isActive() && conn.isSubscribedTo(channelName)) {
                conn.sendPacket(packet);
            }
        }
    }

    /** Get all active connections. */
    public Collection<ClientConnection> getAllConnections() {
        return Collections.unmodifiableCollection(connections.values());
    }

    public int getConnectionCount() {
        return connections.size();
    }

    /** Disconnect idle clients (no keep-alive response in 60s). */
    public void pruneStaleConnections(long timeoutMs) {
        long now = System.currentTimeMillis();
        for (ClientConnection conn : connections.values()) {
            if (now - conn.getLastKeepAlive() > timeoutMs) {
                System.out.println("[NET] Pruning stale connection: " + conn.getClientId());
                conn.setState(ConnectionState.CLOSING);
                conn.getChannel().close();
            }
        }
    }
}