package com.hyperinflation.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyperinflation.net.ClientConnection;
import com.hyperinflation.net.ConnectionManager;
import com.hyperinflation.net.protocol.PacketCodec;
import com.hyperinflation.net.protocol.s2c.S2CHandshakeAck;
import com.hyperinflation.net.protocol.s2c.S2CWorldSnapshot;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import java.util.List;

/**
 * Legacy observer WebSocket handler.
 * Now delegates to ConnectionManager for proper client tracking.
 */
public class ObserverWsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final WorldEngine  engine;

    public ObserverWsHandler(WorldEngine engine) {
        this.engine = engine;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            System.out.println("[WS] WebSocket connected: " + ctx.channel().remoteAddress());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        try {
            JsonNode pkt = mapper.readTree(frame.text());
            int pid      = pkt.path("pid").asInt(0);

            switch (pid) {
                case 0x01: // Handshake
                    String name = pkt.path("d").path("client_name").asText("unknown");
                    System.out.println("[WS] Handshake from: " + name);

                    // Send ack + snapshot using the proper packet types
                    S2CHandshakeAck ack = new S2CHandshakeAck(
                            "legacy-" + ctx.channel().id().asShortText(),
                            "SPECTATE",
                            engine.getCurrentTick(),
                            List.of("world", "economy")
                    );
                    ctx.writeAndFlush(new TextWebSocketFrame(PacketCodec.encode(ack)));

                    S2CWorldSnapshot snapshot = engine.buildWorldSnapshot();
                    ctx.writeAndFlush(new TextWebSocketFrame(PacketCodec.encode(snapshot)));

                    System.out.println("[WS] Registered legacy observer: " + name);
                    break;

                case 0x02: // Keep-alive
                    ctx.writeAndFlush(new TextWebSocketFrame(
                            "{\"pid\":9,\"d\":{\"ts\":" + System.currentTimeMillis() + "}}"));
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            System.err.println("[WS] Parse error: " + e.getMessage());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("[WS] Disconnected: " + ctx.channel().remoteAddress());
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[WS] Error: " + cause.getMessage());
        ctx.close();
    }
}