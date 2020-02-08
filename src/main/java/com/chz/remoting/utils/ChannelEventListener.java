package com.chz.remoting.utils;

import io.netty.channel.Channel;

/**
 * 处理 channelEvent事件的接口
 * **/
public interface ChannelEventListener {

    void processOnConnect(final String address,final Channel channel);

    void processOnClose(final String address,final Channel channel);

    void processOnException(final String address,final Channel channel);
}
