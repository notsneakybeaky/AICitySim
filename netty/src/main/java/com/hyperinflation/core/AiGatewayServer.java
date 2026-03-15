package com.hyperinflation.core;

import com.hyperinflation.ai.PythonAiClient;
import com.hyperinflation.econ.EconomyEngine;
import com.hyperinflation.net.ConnectionManager;
import com.hyperinflation.net.ServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class AiGatewayServer {

    private static final int    PORT          = 8080;
    private static final String PYTHON_AI_URL = "http://localhost:9001";

    public static void main(String[] args) throws Exception {

        PythonAiClient    aiClient          = new PythonAiClient(PYTHON_AI_URL);
        WorldEngine       engine            = new WorldEngine(aiClient);
        ConnectionManager connectionManager = new ConnectionManager();

        // Wire the ConnectionManager into WorldEngine for broadcasting
        engine.setConnectionManager(connectionManager);

        EventLoopGroup bossGroup   = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerInitializer(engine, connectionManager))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            Channel ch = b.bind(PORT).sync().channel();

            System.out.println("=================================================");
            System.out.println("  HYPERINFLATION ENGINE on port " + PORT);
            System.out.println("  WebSocket : ws://localhost:" + PORT + "/ws");
            System.out.println("  REST API  : http://localhost:" + PORT + "/api/status");
            System.out.println("            : http://localhost:" + PORT + "/api/world");
            System.out.println("            : http://localhost:" + PORT + "/api/economy");
            System.out.println("  Python AI : " + PYTHON_AI_URL);
            System.out.println("  Prompt Value: $" + EconomyEngine.TOTAL_PROMPT_VALUE);
            System.out.println("=================================================");

            engine.start();
            ch.closeFuture().sync();

        } finally {
            engine.shutdown();
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}