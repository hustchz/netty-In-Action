package com.chz.remoting;

import com.chz.remoting.protocol.RemotingCommand;
import io.netty.channel.Channel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步返回的处理类
 * **/
public class ResponseFuture {
    private int requestId;//RemotingCommand的id号
    private Channel channel;
    private long timeout;// 超时时间ms
    private long beginTimeStamp = System.currentTimeMillis();//开始时间戳
    private InvokeCallBack callBack;//回调接口
    private final AtomicBoolean callBackOnlyOnce = new AtomicBoolean(false);//回调最多执行一次
    private CountDownLatch latch = new CountDownLatch(1);

    private SemaphoreReleaseOnlyOnce once;

    // 异步一样需要包装RemotingCommand对象作为response
    private volatile RemotingCommand responseCommand;//让responseCommand对其他线程立刻感知
    private volatile Throwable cause;
    private volatile boolean isSuccess;

    public ResponseFuture(int requestId, Channel channel, long timeout,
                          InvokeCallBack callBack, SemaphoreReleaseOnlyOnce once) {
        this.requestId = requestId;
        this.channel = channel;
        this.timeout = timeout;
        this.callBack = callBack;
        this.once = once;
    }

    public void executeInvokeCallback() {
        if (callBack != null) {
            if (this.callBackOnlyOnce.compareAndSet(false, true)) {
                callBack.operateComplete(this);
            }
        }
    }
    public void release(){
        if(null != once){
            once.release();
        }
    }
    public Boolean isTimeout(){
        return (System.currentTimeMillis() - beginTimeStamp) > timeout;
    }

    public void putRemotingCommand(final RemotingCommand responseCommand){
        this.responseCommand = responseCommand;
        latch.countDown();
    }
    public RemotingCommand waitRemotingCommand(final long timeout) throws InterruptedException {
        latch.await(timeout, TimeUnit.MILLISECONDS);
        return this.responseCommand;
    }

    public int getRequestId() {
        return requestId;
    }
    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }
    public RemotingCommand getResponseCommand() {
        return responseCommand;
    }
    public void setResponseCommand(RemotingCommand responseCommand) {
        this.responseCommand = responseCommand;
    }
    public Throwable getCause() {
        return cause;
    }
    public void setCause(Throwable cause) {
        this.cause = cause;
    }
    public Channel getChannel() {
        return channel;
    }
    public void setChannel(Channel channel) {
        this.channel = channel;
    }
    public InvokeCallBack getCallBack() {
        return callBack;
    }
    public void setCallBack(InvokeCallBack callBack) {
        this.callBack = callBack;
    }
    public boolean isSuccess() {
        return isSuccess;
    }
    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    @Override
    public String toString() {
        return "ResponseFuture [responseCommand=" + responseCommand
                + ", cause=" + cause
                + ", requestId=" + requestId
                + ", Channel=" + channel
                + ", timeoutMillis=" + timeout
                + ", invokeCallback=" + callBack
                + ", beginTimestamp=" + beginTimeStamp;
    }
}
