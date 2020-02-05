package com.chz.remoting.utils;

import java.net.InetSocketAddress;

public class RemotingUtils {

    public static InetSocketAddress String2Address(final String address){
        int split = address.lastIndexOf(":");
        String host = address.substring(0, split);
        String port = address.substring(split + 1);
        InetSocketAddress isa = new InetSocketAddress(host, Integer.parseInt(port));
        return isa;
    }
}
