package com.chz.remoting;


import com.chz.remoting.exception.RemotingException;
import com.chz.remoting.protocol.RemotingCommand;
import io.netty.channel.Channel;

import java.util.concurrent.TimeoutException;

/**
 * 服务端的操作，针对channel
 * **/
public interface RemotingServer extends RemotingService {
    void invoke(final Channel channel, final RemotingCommand command,long timeout) throws InterruptedException, RemotingException, TimeoutException;
}
