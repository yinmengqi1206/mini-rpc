package com.mini.rpc.consumer;

import com.mini.rpc.codec.MiniRpcDecoder;
import com.mini.rpc.codec.MiniRpcEncoder;
import com.mini.rpc.common.MiniRpcRequest;
import com.mini.rpc.common.RpcServiceHelper;
import com.mini.rpc.common.ServiceMeta;
import com.mini.rpc.consumer.handler.HeartbeatHandler;
import com.mini.rpc.handler.RpcResponseHandler;
import com.mini.rpc.protocol.MiniRpcProtocol;
import com.mini.rpc.provider.registry.RegistryService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class RpcConsumer {

    private static final Integer RETRY_TIME = 5;//重试间隔，单位秒
    private static final int MAX_RECONNECT_TIMES = 3; // 最大重连次数
    private int reconnectTimes = 0; // 当前重连次数
    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;

    private ChannelFuture future = null;

    public String host;

    public Integer port;



    public RpcConsumer(ServiceMeta serviceMeta) {
        bootstrap = new Bootstrap();
        eventLoopGroup = new NioEventLoopGroup(4);
        bootstrap.group(eventLoopGroup).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline()
                                //心跳检测
                                .addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS))
                                .addLast(new MiniRpcEncoder())
                                .addLast(new MiniRpcDecoder())
                                .addLast(new RpcResponseHandler());
                    }
                });
        host = serviceMeta.getServiceAddr();
        port = serviceMeta.getServicePort();
        connect();
    }

    @SneakyThrows
    public void connect() {
        log.info("netty client start。。");
        //启动客户端去连接服务器端
        future = bootstrap.connect(host, port);
        future.addListener((ChannelFutureListener) arg0 -> {
            if (future.isSuccess()) {
                reconnectTimes = 0;
                log.info("connect rpc server {} on port {} success.", host, port);
            } else {
                reconnectTimes++;
                final EventLoop loop = future.channel().eventLoop();
                loop.schedule(() -> {
                    log.error("connect rpc server {} on port {} failed. 开始重试 当前次数:{}", host, port, reconnectTimes);
                    if (reconnectTimes == MAX_RECONNECT_TIMES){
                        log.info("停止重试，删除当前消费者");
                        // 关闭Channel
                        future.channel().close().addListener((ChannelFutureListener) channelFuture -> {
                            // Channel关闭完成后，关闭EventLoopGroup
                            RpcConsumerFactory.remove(this);
                            eventLoopGroup.shutdownGracefully();
                        });
                    }
                    connect();
                    //此处处理重连次数
                }, RETRY_TIME, TimeUnit.SECONDS);
                future.cause().printStackTrace();
            }
        });
        //添加心跳检测处理器
        future.channel().pipeline().addLast(new HeartbeatHandler(this));
        //阻塞等待连接完成
        future.sync();
    }

    public void sendRequest(MiniRpcProtocol<MiniRpcRequest> protocol) throws Exception {
        future.channel().writeAndFlush(protocol);
    }
}
