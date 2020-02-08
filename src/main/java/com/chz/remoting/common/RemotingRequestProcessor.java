package com.chz.remoting.common;

import com.chz.remoting.protocol.RemotingCommand;
import io.netty.channel.ChannelHandlerContext;

/**
 * 留出来的通用接口
 * **/
public interface RemotingRequestProcessor {
    // 处理请求，并返回一个RemotingCommand
    RemotingCommand processRequest(final ChannelHandlerContext ctx, final RemotingCommand command)throws Exception;
    boolean rejectProcessorRequest()throws Exception;//是否拒绝处理，返回正忙
}
