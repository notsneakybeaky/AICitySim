package main.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class AiGatewayServer {

    private static final int    PORT             = 8080;
    private static final String PYTHON_AI_URL    = "http://localhost:9001";
    private static final String BASE_POLICY      =
            "Implement a 15% corporate tax increase to fund public infrastructure";

    // Initial market regime
    private static final double INITIAL_PRICE      = 45.00;
    private static final double INITIAL_DRIFT      = 0.0;
    private static final double INITIAL_VOLATILITY = 1.0;
    private static final double INITIAL_LIQUIDITY  = 1.0;
    private static final double INITIAL_SPREAD_BPS = 10.0;
    private static final double INITIAL_SHOCK_PROB = 0.05;
    private static final long   RNG_SEED           = 42L;

    public static void main(String[] args) throws Exception {
        MarketRegime regime = new MarketRegime(
                INITIAL_PRICE, INITIAL_DRIFT, INITIAL_VOLATILITY,
                INITIAL_LIQUIDITY, INITIAL_SPREAD_BPS, INITIAL_SHOCK_PROB,
                RNG_SEED
        );

        PythonAiClient aiClient = new PythonAiClient(PYTHON_AI_URL);
        PolicyEngine engine     = new PolicyEngine(regime, aiClient, BASE_POLICY);

        EventLoopGroup bossGroup   = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new AiGatewayInitializer(engine))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            Channel ch = b.bind(PORT).sync().channel();

            System.out.println("=================================================");
            System.out.println("  AI Gateway running on port " + PORT);
            System.out.println("  WebSocket : ws://localhost:" + PORT + "/ws/observe");
            System.out.println("  REST API  : http://localhost:" + PORT + "/api/regime");
            System.out.println("            : http://localhost:" + PORT + "/api/decisions");
            System.out.println("            : http://localhost:" + PORT + "/api/status");
            System.out.println("  Python AI : " + PYTHON_AI_URL);
            System.out.println("  Base policy: " + BASE_POLICY);
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