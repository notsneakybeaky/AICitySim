package com.hyperinflation;

import com.hyperinflation.agent.Agent;
import com.hyperinflation.agent.AgentPersonality;
import com.hyperinflation.core.ModuleRegistry;
import com.hyperinflation.core.SimulationEngine;
import com.hyperinflation.econ.EconomyConfig;
import com.hyperinflation.econ.EconomyEngine;
import com.hyperinflation.net.ConnectionManager;
import com.hyperinflation.net.ServerInitializer;
import com.hyperinflation.world.City;
import com.hyperinflation.world.Region;
import com.hyperinflation.world.World;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {

        // ===== 1. Build the World =====
        World world = new World(64, 64); // 64x64 tile map

        // Regions
        Region northlands = new Region("region-north", "The Northlands", 1.2);
        Region coastals   = new Region("region-coast", "Coastal Federation", 1.0);
        Region heartlands = new Region("region-heart", "The Heartlands", 0.8);
        world.addRegion(northlands);
        world.addRegion(coastals);
        world.addRegion(heartlands);

        // Cities
        City neoKyoto   = new City("city-0", "Neo-Kyoto",    "region-north", 10, 10, 15_000_000, 500.0);
        City theSprawl   = new City("city-1", "The Sprawl",    "region-coast", 40, 15, 30_000_000, 200.0);
        City aethelburg  = new City("city-2", "Aethelburg",    "region-heart", 25, 35, 8_000_000,  350.0);
        City ironhaven   = new City("city-3", "Ironhaven",     "region-north", 15, 50, 12_000_000, 400.0);
        City portMeridian = new City("city-4", "Port Meridian", "region-coast", 50, 45, 20_000_000, 300.0);

        world.addCity(neoKyoto);
        world.addCity(theSprawl);
        world.addCity(aethelburg);
        world.addCity(ironhaven);
        world.addCity(portMeridian);

        northlands.addCity("city-0");
        northlands.addCity("city-3");
        coastals.addCity("city-1");
        coastals.addCity("city-4");
        heartlands.addCity("city-2");

        // ===== 2. Build the Economy =====
        EconomyEngine economyEngine = new EconomyEngine();

        // You can adjust total prompt value here or via API later
        EconomyConfig.TOTAL_PROMPT_VALUE = 1000.0;

        // ===== 3. Build the Agents =====
        AgentPersonality[] roster = AgentPersonality.defaultRoster();
        Agent[] agents = new Agent[roster.length];
        for (int i = 0; i < roster.length; i++) {
            agents[i] = new Agent(roster[i]);
            economyEngine.registerAgent(roster[i].id);
        }

        // ===== 4. Build the Engine =====
        ConnectionManager connectionManager = new ConnectionManager();
        ModuleRegistry registry = new ModuleRegistry(world);

        SimulationEngine engine = new SimulationEngine(
                world, registry, economyEngine, connectionManager);

        // TODO: Register your simulation modules here
        // registry.register(new AiPlagueModule(aiClient, agents));
        // registry.register(new RandomChaosModule());
        // registry.register(new PlayerControlModule());
        // registry.activate("ai-plague");

        // ===== 5. Start Netty =====
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

            System.out.println("==========================================================");
            System.out.println("  HYPERINFLATION WORLD ENGINE");
            System.out.println("==========================================================");
            System.out.println("  WebSocket : ws://localhost:" + PORT + "/ws");
            System.out.println("  REST API  : http://localhost:" + PORT + "/api/status");
            System.out.println("            : http://localhost:" + PORT + "/api/world");
            System.out.println("            : http://localhost:" + PORT + "/api/modules");
            System.out.println("            : http://localhost:" + PORT + "/api/modules/switch");
            System.out.println("            : http://localhost:" + PORT + "/api/economy");
            System.out.println("            : http://localhost:" + PORT + "/api/events");
            System.out.println("==========================================================");
            System.out.println("  World     : " + world.getAllCities().size() + " cities");
            System.out.println("  Map       : " + world.getMap().getWidth() + "x" + world.getMap().getHeight());
            System.out.println("  Agents    : " + agents.length);
            System.out.println("  Prompt $  : " + EconomyConfig.TOTAL_PROMPT_VALUE);
            System.out.println("==========================================================");

            engine.start();
            ch.closeFuture().sync();

        } finally {
            engine.shutdown();
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}