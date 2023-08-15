package com.mini.rpc.provider.registry;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.mini.rpc.common.RpcServiceHelper;
import com.mini.rpc.common.ServiceMeta;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.Properties;

/**
 * @author yinmengqi
 * @date 2023/8/15 14:40
 */
public class NacosRegistryService implements RegistryService {

    private final NamingService naming;

    @SneakyThrows
    public NacosRegistryService(String registryAddr) {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, registryAddr);
        properties.put(PropertyKeyConst.USERNAME, "nacos");
        properties.put(PropertyKeyConst.PASSWORD, "z0b61ZonO4oklLwz");
        properties.put(PropertyKeyConst.NAMESPACE,"test");
        naming = NamingFactory.createNamingService(properties);
    }

    @Override
    public void register(ServiceMeta serviceMeta) throws Exception {
        naming.registerInstance(RpcServiceHelper.buildServiceKey(serviceMeta.getServiceName(), serviceMeta.getServiceVersion()), serviceMeta.getServiceAddr(), serviceMeta.getServicePort());
    }

    @Override
    public void unRegister(ServiceMeta serviceMeta) throws Exception {
        naming.deregisterInstance(RpcServiceHelper.buildServiceKey(serviceMeta.getServiceName(), serviceMeta.getServiceVersion()), serviceMeta.getServiceAddr(), serviceMeta.getServicePort());
    }

    @Override
    public ServiceMeta discovery(String serviceName, int invokerHashCode) throws Exception {
        Instance instance = naming.selectOneHealthyInstance(serviceName);
        String[] split = serviceName.split("#");
        return new ServiceMeta()
                .setServiceAddr(instance.getIp())
                .setServicePort(instance.getPort())
                .setServiceName(split[0])
                .setServiceVersion(split[1]);
    }

    @SneakyThrows
    @Override
    public void destroy() throws IOException {
        naming.shutDown();
    }
}
