package com.mini.rpc.consumer.handler;

import com.mini.rpc.consumer.RpcConsumer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

/**
 * @author yinmengqi
 * @date 2023/8/16 16:53
 */
@Slf4j
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

    private final RpcConsumer rpcConsumer;

    public HeartbeatHandler(RpcConsumer rpcConsumer){
        this.rpcConsumer = rpcConsumer;
    }

    /**
     * 通道连接时调用-处理业务逻辑
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        InetSocketAddress intSocket = (InetSocketAddress) ctx.channel().remoteAddress();
        String clientIp = intSocket.getAddress().getHostAddress();
        int port = intSocket.getPort();
        log.info("{}:{} 通道已连接！",clientIp,port);
    }

    /**
     * 通道闲置触发-启动断线重连功能
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        InetSocketAddress intSocket = (InetSocketAddress) ctx.channel().remoteAddress();
        String clientIp = intSocket.getAddress().getHostAddress();
        int port = intSocket.getPort();
        //使用过程中断线重连
        log.error("{}:{} 断线连接中...",clientIp,port);
        rpcConsumer.connect();
        ctx.fireChannelInactive();
    }

    /**
     * 心跳方法
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state().equals(IdleState.WRITER_IDLE)) {
                /**发送心跳,保持长连接*/
                log.info("发送心跳 PING");
                ctx.channel().writeAndFlush("PING");
            }
        }
        super.userEventTriggered(ctx, evt);
    }

}
