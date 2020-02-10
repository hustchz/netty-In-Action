package com.chz.remoting;


import com.chz.remoting.common.Pair;
import com.chz.remoting.common.RemotingRequestProcessor;
import com.chz.remoting.exception.RemotingException;
import com.chz.remoting.exception.RemotingTimeoutException;
import com.chz.remoting.protocol.RemotingCommand;
import com.chz.remoting.utils.ChannelEventListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 将客户端服务端共同的操作封装起来
 * **/
public abstract class RemotingAbstract {

    private static Logger logger = Logger.getLogger(RemotingAbstract.class);
    /* 针对消息的code类型，去寻找有无相应的processor去做request的处理*/
    protected Map<Integer,Pair<RemotingRequestProcessor, ExecutorService>> processorTable = new HashMap<>(64);
    protected final ChannelEventExecutor eventExecutor = new ChannelEventExecutor();
    protected Map<Integer,ResponseFuture> onGoingTable = new HashMap<>(256);
    protected Semaphore semaphore;

    /*服务端和客户端都通过该方法进行消息传递 同步式*/
    public RemotingCommand invokeSyncImpl(final Channel channel,final RemotingCommand command,final long timeout) throws InterruptedException, TimeoutException, RemotingException {
        final int requestId = command.getRequestId();
        try{
            //向channel中写数据
            ResponseFuture future = new ResponseFuture(
                    requestId,channel,timeout,null,null
            );
            onGoingTable.put(requestId,future);//正在进行处理的请求
            channel.writeAndFlush(command).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    logger.info("处理完成，改变future的状态");
                    if(channelFuture.isSuccess()){
                        future.setSuccess(true);
                        return ;
                    }
                    // 发送消息失败
                    logger.warn("fail to send request[Sync]");
                    onGoingTable.remove(requestId);// 说明此次的请求失败，应该移除掉
                    future.setSuccess(false);
                    future.setCause(channelFuture.cause());
                    future.setResponseCommand(null);
                }
            });

            // 同步等待结果
            RemotingCommand responseCommand = future.waitRemotingCommand(timeout);
            if(null == responseCommand){
                // 1. 超时 2. 失败了
                if(future.isSuccess()){
                    throw new TimeoutException("invokeSync TIMEOUT");
                }else{
                    throw new RemotingException("invokeSync exception"+future.getCause());
                }
            }
            return responseCommand ;
        } finally {
            // 同步式：只有等待结果返回后才能确定上一个请求流程已经结束
            onGoingTable.remove(requestId);
        }
    }

    /*服务端和客户端都通过该方法进行消息传递 异步式*/
    public void invokeASyncImpl(final Channel channel,final RemotingCommand command,
                                           final long timeout,InvokeCallBack callBack) throws InterruptedException, TimeoutException, RemotingException {
        final int requestId = command.getRequestId();
        long startTimeout = System.currentTimeMillis();
        try{
            if(semaphore.tryAcquire(timeout,TimeUnit.MILLISECONDS)){
                //向channel中写数据
                final SemaphoreReleaseOnlyOnce once = new SemaphoreReleaseOnlyOnce(semaphore);
                ResponseFuture future = new ResponseFuture(
                        requestId,channel,timeout,callBack,once
                );
                if(System.currentTimeMillis() - startTimeout > timeout){
                    // 设定timeout太小，直接超时
                    once.release();
                    throw new RemotingTimeoutException("invokeASyncImpl call timeout");
                }
                onGoingTable.put(requestId,future);//正在进行处理的请求
                channel.writeAndFlush(command).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        logger.info("处理完成，改变future的状态[Async]");
                        if(channelFuture.isSuccess()){
                            future.setSuccess(true);
                            return ;
                        }
                        // 发送消息失败
                        logger.warn("fail to send request[Async]");
                        onGoingTable.remove(requestId);// 说明此次的请求失败，应该移除掉
                        future.setSuccess(false);
                        future.setCause(channelFuture.cause());
                        future.setResponseCommand(null);
                        //失败后做回调
                        executeCallBack(future);
                    }
                });
            }else{
                String info =
                        String.format("invokeAsyncImpl tryAcquire semaphore timeout, %dms, waiting thread nums: %d semaphoreAsyncValue: %d",
                                timeout,
                                this.semaphore.getQueueLength(),
                                this.semaphore.availablePermits()
                        );
                logger.warn(info);
                throw new RemotingTimeoutException(info);
            }

        } finally {
            // 异步式： 不能确定是否成功，只有在收到服务端的回应时才知道完成了
           if(null != semaphore){
               semaphore.release();
           }
        }
    }

    private void executeCallBack(final ResponseFuture responseFuture){
        if(null != responseFuture){
            try{
                responseFuture.executeInvokeCallback();
            }catch (Exception ex){
                ex.printStackTrace();
            }finally {
                responseFuture.release();
            }
        }
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

    /* 说明服务端已经发送成功*/
    protected  void processResponse(ChannelHandlerContext ctx, RemotingCommand command){
            // 暂时不实现,可以考虑另加一个交互类ResponseFuture,这个方法主要作用就是触发交互类的特定方法
            // 说明前一个的request 服务端已经收到了
        logger.infof("客户端收到了编号为%s的回应",command.getRequestId());
        final int requestId = command.getRequestId();
        ResponseFuture future = this.onGoingTable.get(requestId);
        if(null != future){
            try{
                future.putRemotingCommand(command);
                executeCallBack(future);
            }finally {
                future.release();
                this.onGoingTable.remove(requestId);
            }
        }else{
            logger.warn("received request , but can't match");
        }
    }

    // 处理请求
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
            logger.info("没有指定的processor处理request"+command);
            response = new RemotingCommand(CommandType.RESPONSE.getCode());
            response.setRequestId(requestId);
            ctx.writeAndFlush(response);
        }
    }

    class ChannelEventExecutor implements Runnable{

        private final int maxProcessorChannelEventNum = 10000;//最多处理多少ChannelEvent事件
        private LinkedBlockingQueue<ChannelEvent>queue = new LinkedBlockingQueue<>();

        public void putChannelEvent(ChannelEvent event) throws InterruptedException {
            if(queue.size() > maxProcessorChannelEventNum){
                logger.warnf(
                        "channelEvent is enough , can't add this event{%s} to the queue",event
                        );
            }else{
                logger.info("有channelEvent事件放入阻塞队列中");
                queue.put(event);
            }
        }

        @Override
        public void run() {
            final ChannelEventListener listener = getChannelEventListener();
            for(;;){
                try {
                    ChannelEvent event = this.queue.poll(3000, TimeUnit.MILLISECONDS);
                    if(null != listener && null != event){
                        logger.info(event);
                        switch (event.getEventType()){
                            case CONNECT:{
                                listener.processOnConnect(event.getAddress(),event.getChannel());
                                break;
                            }
                            case CLOSE:{
                                listener.processOnClose(event.getAddress(),event.getChannel());
                                break;
                            }
                            case EXCEPTION:{
                                listener.processOnException(event.getAddress(),event.getChannel());
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }

        public void start(){
            new Thread(this,"ChannelEventThread").start();
        }
    }

    protected abstract ChannelEventListener getChannelEventListener();

}
