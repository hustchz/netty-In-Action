package com.chz.remoting;

import com.chz.remoting.common.RemotingRequestProcessor;
import com.chz.remoting.exception.RemotingConnectException;
import com.chz.remoting.protocol.RemotingCommand;

import java.util.concurrent.ExecutorService;

/**
 * 定义客户端的操作
 * **/
public interface RemotingClient extends RemotingService{

    void invoke(final String address,final RemotingCommand command) throws RemotingConnectException, InterruptedException;
    void registerProcessor(final int requestCode, final RemotingRequestProcessor processor,
                           final ExecutorService executor);
}
