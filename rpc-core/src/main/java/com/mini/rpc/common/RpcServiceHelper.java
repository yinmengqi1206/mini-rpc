package com.mini.rpc.common;

public class RpcServiceHelper {

    public static final String SERVICE_JOIN = "@";
    public static String buildServiceKey(String serviceName, String serviceVersion) {
        return String.join(SERVICE_JOIN, serviceName, serviceVersion);
    }
}
