package com.chz.remoting;


import com.chz.remoting.common.Pair;
import com.chz.remoting.common.RemotingRequestProcessor;
import com.chz.remoting.protocol.RemotingCommand;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * 将客户端服务端共同的操作封装起来
 * **/
public abstract class RemotingAbstract {

    private static Logger logger = Logger.getLogger(RemotingAbstract.class);
    /* 针对消息的code类型，去寻找有无相应的processor去做request的处理*/
    protected Map<Integer,Pair<RemotingRequestProcessor, ExecutorService>> processorTable = new HashMap<>(64);
    /*服务端和客户端都通过该方法进行消息传递*/
    public void invokeImpl(final Channel channel,final RemotingCommand command){
        //向channel中写数据
        channel.writeAndFlush(command).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                logger.info("发送成功");
            }
        });
    }

    /* 处理收到的消息*/
    public void processReceivedMessage(final ChannelHandlerContext ctx, final RemotingCommand command) throws Exception {
        // 根据command的类型去执行具体的处理逻辑
        switch (command.getType()){
            case REQUEST:{
                processRequest(ctx,command);
                break;
            }
            case RESPONSE:{
                processResponse(ctx,command);
                break;
            }
            default:
                break;
        }
    }

    protected  void processResponse(ChannelHandlerContext ctx, RemotingCommand command){
            // 暂时不实现,可以考虑另加一个交互类ResponseFuture,这个方法主要作用就是触发交互类的特定方法
            // 说明前一个的request 服务端已经收到了
        logger.infof("服务端收到了编号为%s的请求，已经做了回应",command.getRequestId());
    }

    protected void processRequest(ChannelHandlerContext ctx, RemotingCommand command) throws Exception {
        // 判断有无默认的processor需要执行的
        final int code = command.getCode();
        final int requestId = command.getRequestId();
        Pair<RemotingRequestProcessor, ExecutorService> pair = this.processorTable.get(code);
        RemotingCommand response = null;
        // 让响应和请求用一个id，但是flag不一样
        if(null != pair){
            RemotingRequestProcessor processor = pair.getKey();
            if(processor.rejectProcessorRequest()){
                //返回用户忙
                response = RemotingCommand.createResponseCommand(CommandCodeType.BUSY);
                response.setRequestId(requestId);
                ctx.writeAndFlush(response);
                return ;
            }else{
                // 由指定的processor去正常处理请求
                Runnable run = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            RemotingCommand response = processor.processRequest(ctx, command);
                            if(null != response){
                                response.setRequestId(requestId);
                                response.setFlag(CommandType.RESPONSE.getCode());
                                try{
                                    ctx.writeAndFlush(response);
                                }catch (Exception ex){
                                    logger.error("processor has processed the request(command),but fail");
                                }
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
                
                // 由指定的线程池去做
                try{
                    pair.getValue().submit(run);
                }catch (RejectedExecutionException rejectException){
                    logger.error("too many request has been submit");
                }
            }

        }else{
            response = new RemotingCommand(CommandType.RESPONSE.getCode());
            response.setRequestId(requestId);
            ctx.writeAndFlush(response);
            logger.info("没有指定的processor处理request");
        }

    }
}
