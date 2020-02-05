package com.chz.remoting;


import com.chz.remoting.protocol.RemotingCommand;
import io.netty.channel.Channel;

/**
 * 服务端的操作，针对channel
 * **/
public interface RemotingServer extends RemotingService {
    void invoke(final Channel channel, final RemotingCommand command);
}
