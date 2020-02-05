package com.chz.remoting;


import com.chz.remoting.protocol.RemotingCommand;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
 * 将客户端服务端共同的操作封装起来
 * **/
public abstract class RemotingAbstract {

    /*服务端和客户端都通过该方法进行消息传递*/
    public void invokeImpl(final Channel channel,final RemotingCommand command){
        //向channel中写数据
        channel.writeAndFlush(command).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                System.out.println("发送成功");
            }
        });
    }
}
