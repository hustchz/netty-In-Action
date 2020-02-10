package com.chz.remoting;

import com.chz.remoting.common.RemotingRequestProcessor;
import com.chz.remoting.exception.RemotingConnectException;
import com.chz.remoting.exception.RemotingException;
import com.chz.remoting.protocol.RemotingCommand;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * 定义客户端的操作
 * **/
public interface RemotingClient extends RemotingService{

    RemotingCommand invoke(final String address,final RemotingCommand command,long timeout) throws RemotingException, InterruptedException, TimeoutException;
    void registerProcessor(final int requestCode, final RemotingRequestProcessor processor,
                           final ExecutorService executor);
    void invokeAsync(String address, RemotingCommand command, long timeout, InvokeCallBack callBack) throws RemotingException, InterruptedException, TimeoutException;
}
