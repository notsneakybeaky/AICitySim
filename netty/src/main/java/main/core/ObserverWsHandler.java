package main.core;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * WebSocket handler for dashboard observers.
 * Observers connect, get registered with the PolicyEngine,
 * and receive live round updates.
 */
public class ObserverWsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final PolicyEngine engine;

    public ObserverWsHandler(PolicyEngine engine) {
        this.engine = engine;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            System.out.println("[WS] Observer connected: " + ctx.channel().remoteAddress());
            engine.addObserver(ctx.channel());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        // Observers are read-only. Ignore any messages they send.
        // You could add PING/PONG here if desired.
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("[WS] Observer disconnected: " + ctx.channel().remoteAddress());
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[WS] Observer error: " + cause.getMessage());
        ctx.close();
    }
}