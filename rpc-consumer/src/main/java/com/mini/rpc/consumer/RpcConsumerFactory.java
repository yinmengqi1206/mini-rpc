package com.mini.rpc.consumer;

import com.mini.rpc.common.MiniRpcRequest;
import com.mini.rpc.common.RpcServiceHelper;
import com.mini.rpc.common.ServiceMeta;
import com.mini.rpc.protocol.MiniRpcProtocol;
import com.mini.rpc.provider.registry.RegistryService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RpcConsumerFactory {
    private static final ConcurrentHashMap<String, RpcConsumer> RPC_CONSUMER_MAP = new ConcurrentHashMap<>();

    public static RpcConsumer getInstance(MiniRpcProtocol<MiniRpcRequest> protocol, RegistryService registryService) throws Exception {
        MiniRpcRequest request = protocol.getBody();
        Object[] params = request.getParams();
        String serviceKey = RpcServiceHelper.buildServiceKey(request.getClassName(), request.getServiceVersion());

        int invokerHashCode = params.length > 0 ? params[0].hashCode() : serviceKey.hashCode();
        ServiceMeta serviceMetadata = registryService.discovery(serviceKey, invokerHashCode);
        String key = serviceMetadata.getServiceAddr() + ":" + serviceMetadata.getServicePort();
        if(RPC_CONSUMER_MAP.containsKey(key)){
            return RPC_CONSUMER_MAP.get(key);
        }else {
            RpcConsumer rpcConsumer = new RpcConsumer(serviceMetadata);
            RPC_CONSUMER_MAP.put(key,rpcConsumer);
            return rpcConsumer;
        }
    }

    public static void remove(RpcConsumer rpcConsumer){
        log.info("连接失败，删除rpcConsumer host:{},port:{}",rpcConsumer.host,rpcConsumer.port);
        RPC_CONSUMER_MAP.remove(rpcConsumer.host + ":" + rpcConsumer.port);
    }
}
