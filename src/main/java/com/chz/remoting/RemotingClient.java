package com.chz.remoting;

import com.chz.remoting.protocol.RemotingCommand;

/**
 * 定义客户端的操作
 * **/
public interface RemotingClient extends RemotingService{

    void invoke(final String address,final RemotingCommand command) throws RemotingConnectException, InterruptedException;
}
