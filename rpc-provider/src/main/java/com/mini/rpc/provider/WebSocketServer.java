package com.mini.rpc.provider;

/**
 * @author yinmengqi
 * @date 2023/8/17 11:46
 */
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

@Slf4j
public class WebSocketServer {

    public static void main(String[] args) throws Exception {
        int port = 8080;

        // 创建 ChannelGroup 用于管理连接的客户端
        ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

        // 创建 ServerBootstrap
        ServerBootstrap bootstrap = new ServerBootstrap();

        // 设置 EventLoopGroup
        bootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup());

        // 设置 Channel 类型为 NioServerSocketChannel
        bootstrap.channel(NioServerSocketChannel.class);

        // 设置 ChannelInitializer
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();

                // 添加 HTTP 相关的编解码器和处理器
                pipeline.addLast(new HttpRequestDecoder());
                pipeline.addLast(new HttpObjectAggregator(65536));
                pipeline.addLast(new HttpResponseEncoder());
                pipeline.addLast(new ChunkedWriteHandler());

                // 添加 WebSocket 相关的编解码器和处理器
                pipeline.addLast(new WebSocketServerProtocolHandler("/websocket"));
                pipeline.addLast(new WebSocketServerHandler(channelGroup));
            }
        });

        // 绑定端口并启动服务器
        ChannelFuture future = bootstrap.bind(port).sync();
        System.out.println("WebSocket Server started on port " + port);

        // 等待服务器关闭
        future.channel().closeFuture().sync();
    }

    public static class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

        private final ChannelGroup channelGroup;

        private WebSocketServerHandshaker handshaker;

        public WebSocketServerHandler(ChannelGroup channelGroup) {
            this.channelGroup = channelGroup;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            log.info("收到消息:{}",msg);
            if (msg instanceof FullHttpRequest) {
                // 处理 HTTP 请求
                handleHttpRequest(ctx, (FullHttpRequest) msg);
            } else if (msg instanceof WebSocketFrame) {
                // 处理 WebSocket 帧
                handleWebSocketFrame(ctx, (WebSocketFrame) msg);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("加入连接");
            // 当有新的客户端连接时，将其添加到 ChannelGroup 中
            channelGroup.add(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("断开连接");
            // 当有客户端断开连接时，将其从 ChannelGroup 中移除
            channelGroup.remove(ctx.channel());
        }

        private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            // 如果请求不是 WebSocket 握手请求，则返回错误页面
            if (!request.decoderResult().isSuccess()
                    || (!"websocket".equals(request.headers().get("Upgrade")))) {
                sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
                return;
            }

            // 创建 WebSocket 握手处理器
            handshaker = new WebSocketServerHandshakerFactory(
                    "ws://" + request.headers().get(HOST) + "/websocket", null, false).newHandshaker(request);

            // 如果握手处理器为空，则返回错误页面
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                return;
            }

            // 握手响应
            handshaker.handshake(ctx.channel(), request);

            // 将 WebSocket 连接添加到 ChannelGroup 中
            channelGroup.add(ctx.channel());
        }

        private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
            // 如果是关闭 WebSocket 连接的帧，则关闭连接
            if (frame instanceof CloseWebSocketFrame) {
                handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
                return;
            }

            // 如果是文本帧，则将其发送给所有连接的客户端
            if (frame instanceof TextWebSocketFrame) {
                String message = ((TextWebSocketFrame) frame).text();
                channelGroup.writeAndFlush(new TextWebSocketFrame(message));
            }
        }

        private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
            // 返回错误页面
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
            ByteBuf buffer = Unpooled.copiedBuffer("Failure: " + HttpResponseStatus.BAD_REQUEST.toString() + "\r\n",
                    CharsetUtil.UTF_8);
            response.content().writeBytes(buffer);
            buffer.release();
            ctx.channel().writeAndFlush(response);
        }
    }
}

