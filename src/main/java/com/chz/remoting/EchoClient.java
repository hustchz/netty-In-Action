package com.chz.remoting;

import com.chz.remoting.common.Pair;
import com.chz.remoting.common.RemotingRequestProcessor;
import com.chz.remoting.exception.RemotingConnectException;
import com.chz.remoting.protocol.RemotingCommand;
import com.chz.remoting.utils.RemotingUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.EventExecutorGroup;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * netty的客户端
 * **/
public class EchoClient extends RemotingAbstract implements RemotingClient {
    private static Logger logger = Logger.getLogger(EchoClient.class);
    private String hostname;
    private int port;
    private Bootstrap bootstrap;
    private EventLoopGroup workerGroup;
    private EventExecutorGroup executorGroup;// 工作线程组

    private final int workerThreadNum = 1;//连接建立后childGroup线程池的线程数
    private final int executeThreadNum = 4;//工作线程为4个

    private static Map<String,ChannelWrapper> channelTables = new ConcurrentHashMap<>(256);

    // handlers
    private NettyClientHandler nettyClientHandler;//接受客户端请求的handler
    public EchoClient(String hostname, int port){
        this.hostname = hostname;
        this.port = port;
        init();
    }
    private void init(){
        this.bootstrap = new Bootstrap();

        workerGroup = new NioEventLoopGroup(workerThreadNum, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,"parentThread-"+index.incrementAndGet());
            }
        });

    }

    private void prepareHandlers(){
        nettyClientHandler = new NettyClientHandler();
    }
    @Override
    public void start() {
        prepareHandlers();

        executorGroup = new NioEventLoopGroup(executeThreadNum, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,"executeThread-"+index.incrementAndGet());
            }
        });

        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .option(ChannelOption.SO_SNDBUF, 1024)
                .option(ChannelOption.SO_RCVBUF, 1024)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(executorGroup,
                                new RemotingEncoder(),
                                new RemotingDecoder(),
                                nettyClientHandler
                                );
                    }
                });

    }

    @Override
    public void shutdown() {
        if(null != workerGroup){
            workerGroup.shutdownGracefully();
        }

        if(null != executorGroup){
            executorGroup.shutdownGracefully();
        }
    }

    private Channel createChannel(final String address){
        ChannelWrapper wrapper = channelTables.get(address);
        if(null != wrapper && wrapper.isActive()){
            // 当前的channel 是可用的，获取即可
            return channelTables.get(address).getChannel();
        }
        // 有两种可能，有可能是还没有添加至channelTables中 或者channel不可用了(还在建立中，已经建立好了)
        synchronized (channelTables){
            boolean firstConnect = false;//表示第一次连接
            //双检锁,避免重复添加
            wrapper = channelTables.get(address);
            if(null != wrapper){
                if(wrapper.isActive()){
                    return wrapper.getChannel();
                }else if(wrapper.isDone()){
                    // 已经建立好了但是都不可用，说明需要重新连接,原来的彻底失效了
                    channelTables.remove(address);
                    firstConnect = true;
                }else{
                    // 还在建立中，不需要重新连接
                    firstConnect = false;
                }
            }else{
                //还没有添加过
                firstConnect = true ;
            }

            if(firstConnect){
                try {
                    ChannelFuture future = this.bootstrap.connect(RemotingUtils.String2Address(address)).sync();
                    wrapper = new ChannelWrapper(future);
                    channelTables.put(address,wrapper);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if(null != wrapper){
                if(wrapper.isActive()){
                    return wrapper.getChannel();
                }
            }
            return null;
        }
    }

    @Override
    public void invoke(String address, RemotingCommand command) throws RemotingConnectException, InterruptedException {
        final Channel channel = this.createChannel(address);
        // 由于是异步，channel 也可能不可用
        if(null != channel && channel.isActive()){
            //可以操作
            this.invokeImpl(channel,command);
            channel.closeFuture().sync();
        }else{
            this.closeChannel(address,channel);
            throw new RemotingConnectException(address);
        }
    }

    @Override
    public void registerProcessor(int requestCode, RemotingRequestProcessor processor,
                                  ExecutorService executor) {
        Pair<RemotingRequestProcessor,ExecutorService> pair = new Pair<>(processor,executor);
        this.processorTable.put(requestCode,pair);
    }

    public void closeChannel(String address,final Channel channel){
        if(null == channel)return;
        if(null == address || address.equals("")){
            address = channel.localAddress().toString();
        }
        synchronized (channelTables){
            // 避免重复关闭
            ChannelWrapper wrapper = channelTables.get(address);
            if(null != wrapper && wrapper.getChannel()==channel){
                channelTables.remove(address);
                channel.close().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        logger.info("channel is closed");
                    }
                });
            }
        }
    }

    @ChannelHandler.Sharable
    class NettyClientHandler extends SimpleChannelInboundHandler<RemotingCommand>{

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, RemotingCommand command) throws Exception {
            logger.info(command.getFlag());
            processReceivedMessage(ctx,command);
        }
    }

    public static void main(String[] args) {
        EchoClient client = null ;
        try{
            client = new EchoClient("127.0.0.1",2088);
            client.start();
            RemotingCommand command = new RemotingCommand(CommandType.REQUEST.getCode());
            command.setBody("你好a".getBytes("UTF-8"));
            client.invoke("127.0.0.1:2088",command);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(null != client){
                client.shutdown();
            }
        }
    }

    // channel的包装类，客户端从包装类从取出channelFuture与服务端进行交互
    class ChannelWrapper{
        private ChannelFuture channelFuture;
        public ChannelWrapper(ChannelFuture channelFuture){
            this.channelFuture = channelFuture;
        }
        public Channel getChannel(){
            if(channelFuture == null){
                return null;
            }
            return channelFuture.channel();
        }
        public ChannelFuture getChannelFuture(){
            return channelFuture;
        }
        public boolean isActive(){
            Channel channel = getChannel();
            return (channel!=null&&channel.isActive());
        }
        public boolean isDone(){
           return (null != channelFuture && channelFuture.isDone());
        }
    }

}
