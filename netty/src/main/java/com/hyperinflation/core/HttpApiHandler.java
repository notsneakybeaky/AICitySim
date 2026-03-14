package com.hyperinflation.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.util.LinkedHashMap;
import java.util.Map;

public class HttpApiHandler extends ChannelInboundHandlerAdapter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final WorldEngine  engine;

    public HttpApiHandler(WorldEngine engine) {
        this.engine = engine;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }

        FullHttpRequest req = (FullHttpRequest) msg;

        if (isWebSocketUpgrade(req)) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            if (req.method() == HttpMethod.OPTIONS) {
                sendJson(ctx, HttpResponseStatus.OK, "{}");
                return;
            }

            String uri = req.uri().split("\\?")[0];

            switch (uri) {
                case "/api/world":
                    sendJson(ctx, HttpResponseStatus.OK,
                            mapper.writeValueAsString(engine.getWorld().toFullMap()));
                    break;

                case "/api/economy":
                    sendJson(ctx, HttpResponseStatus.OK,
                            mapper.writeValueAsString(engine.getEconomy().toMap()));
                    break;

                case "/api/status": {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("phase",        engine.getPhase());
                    s.put("current_tick", engine.getCurrentTick());
                    s.put("agent_count",  engine.getAgents().size());
                    s.put("city_count",   engine.getWorld().getCities().size());
                    s.put("prompt_price", engine.getEconomy().getPromptPrice());
                    s.put("ts",           System.currentTimeMillis());
                    sendJson(ctx, HttpResponseStatus.OK, mapper.writeValueAsString(s));
                    break;
                }

                case "/health":
                    sendJson(ctx, HttpResponseStatus.OK, "{\"ok\":true}");
                    break;

                default:
                    sendJson(ctx, HttpResponseStatus.NOT_FOUND,
                            "{\"error\":\"Not found\",\"uri\":\"" + uri + "\"}");
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private boolean isWebSocketUpgrade(FullHttpRequest req) {
        return req.headers().containsValue(
                HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)
                || req.headers().containsValue(
                HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true);
    }

    private void sendJson(ChannelHandlerContext ctx, HttpResponseStatus status, String json) {
        ByteBuf content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE,                 "application/json");
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH,               content.readableBytes());
        resp.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN,  "*");
        resp.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS");
        resp.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "*");
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }
}