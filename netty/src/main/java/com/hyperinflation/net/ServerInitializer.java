package com.hyperinflation.net;

import com.hyperinflation.core.SimulationEngine;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {

    private final SimulationEngine engine;
    private final ConnectionManager connectionManager;

    public ServerInitializer(SimulationEngine engine, ConnectionManager connectionManager) {
        this.engine            = engine;
        this.connectionManager = connectionManager;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast("http-codec",      new HttpServerCodec());
        p.addLast("http-aggregator", new HttpObjectAggregator(65536));
        p.addLast("idle",            new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));
        p.addLast("http-api",        new HttpApiHandler(engine));
        p.addLast("ws-protocol",     new WebSocketServerProtocolHandler(
                "/ws", null, true, 65536));
        p.addLast("protocol-handler", new ProtocolHandler(connectionManager, engine));
    }
}