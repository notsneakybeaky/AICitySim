package com.hyperinflation.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

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
                    String clientId = engine.addObserver(ctx.channel());
                    System.out.println("[WS] Registered: " + clientId);
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