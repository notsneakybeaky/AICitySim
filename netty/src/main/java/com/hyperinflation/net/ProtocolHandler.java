package com.hyperinflation.net;

import com.hyperinflation.core.WorldEngine;
import com.hyperinflation.net.protocol.*;
import com.hyperinflation.net.protocol.c2s.*;
import com.hyperinflation.net.protocol.s2c.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import java.util.List;
import java.util.Map;

/**
 * Handles WebSocket frames, decodes them into Packets, and routes them.
 * This is the Minecraft-style packet router.
 */
public class ProtocolHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final ConnectionManager connectionManager;
    private final WorldEngine engine;
    public ProtocolHandler(ConnectionManager connectionManager, WorldEngine engine) { this.connectionManager = connectionManager; this.engine = engine; }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // WebSocket upgrade complete. Register the connection.
            ClientConnection conn = connectionManager.addConnection(ctx.channel());
            System.out.println("[PROTOCOL] WebSocket handshake complete for " + conn.getClientId());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        ClientConnection conn = connectionManager.getConnection(ctx.channel());
        if (conn == null) {
            System.err.println("[PROTOCOL] Received frame from unknown connection");
            return;
        }

        try {
            PacketCodec.DecodedPacket decoded = PacketCodec.decode(frame.text());
            int packetId = decoded.packetId();

            if (!PacketRegistry.isKnownC2S(packetId)) {
                System.err.println("[PROTOCOL] Unknown packet ID: 0x"
                        + Integer.toHexString(packetId));
                return;
            }

            Packet packet = PacketRegistry.deserializeC2S(packetId, decoded.data());
            routePacket(conn, packet);

        } catch (Exception e) {
            System.err.println("[PROTOCOL] Failed to process frame: " + e.getMessage());
        }
    }

    private void routePacket(ClientConnection conn, Packet packet) {
        switch (packet) {
            case C2SHandshake handshake -> handleHandshake(conn, handshake);
            case C2SKeepAlive keepAlive -> handleKeepAlive(conn, keepAlive);
            case C2SModuleSwitch moduleSwitch -> handleModuleSwitch(conn, moduleSwitch);
            case C2SPlayerAction playerAction -> handlePlayerAction(conn, playerAction);
            case C2SSubscribe subscribe -> handleSubscribe(conn, subscribe);
            default -> System.out.println("[PROTOCOL] Unhandled packet type: "
                    + packet.getClass().getSimpleName());
        }
    }

    // ---- Handlers ----

    private void handleHandshake(ClientConnection conn, C2SHandshake handshake) {
        System.out.println("[PROTOCOL] Handshake from " + handshake.clientName()
                + " (protocol v" + handshake.protocolVersion() + ")");

        conn.setClientName(handshake.clientName());

        // Assign state based on request
        ConnectionState newState = "PLAY".equalsIgnoreCase(handshake.requestedState())
                ? ConnectionState.PLAY
                : ConnectionState.SPECTATE;
        conn.setState(newState);

        // Send acknowledgment
        S2CHandshakeAck ack = new S2CHandshakeAck(
                conn.getClientId(),
                newState.name(),
                engine.getCurrentTick(),
                List.of("world", "economy")  // hardcode since WorldEngine has no registry
        );
        conn.sendPacket(ack);

        // Send full world snapshot
        S2CWorldSnapshot snapshot = new S2CWorldSnapshot(
                engine.getCurrentTick(),
                engine.getWorld().toFullMap(),
                engine.getEconomy().toMap()  // was Map.of()
        );
        conn.sendPacket(snapshot);
    }

    private void handleKeepAlive(ClientConnection conn, C2SKeepAlive keepAlive) {
        conn.touchKeepAlive();
    }

    private void handleModuleSwitch(ClientConnection conn, C2SModuleSwitch moduleSwitch) {
        System.out.println("[PROTOCOL] Module switch ignored - not supported.");
    }

    private void handlePlayerAction(ClientConnection conn, C2SPlayerAction action) {
        if (conn.getState() != ConnectionState.PLAY) return;
        System.out.println("[PROTOCOL] Player action from " + conn.getClientId()
                + ": " + action.actionType() + " → " + action.targetId());
        // TODO: queue the action for the next tick via a PlayerControlModule
    }

    private void handleSubscribe(ClientConnection conn, C2SSubscribe subscribe) {
        conn.setSubscriptions(subscribe.channels());
        System.out.println("[PROTOCOL] " + conn.getClientId()
                + " subscribed to: " + subscribe.channels());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        connectionManager.removeConnection(ctx.channel());
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[PROTOCOL] Error: " + cause.getMessage());
        ctx.close();
    }
}