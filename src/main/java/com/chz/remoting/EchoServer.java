package com.chz.remoting;

import com.chz.remoting.exception.RemotingException;
import com.chz.remoting.protocol.RemotingCommand;
import com.chz.remoting.utils.ChannelEventListener;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.EventExecutorGroup;
import org.jboss.logging.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * netty的服务端
 * **/
public class EchoServer extends RemotingAbstract implements RemotingServer{
    private static Logger logger = Logger.getLogger(EchoServer.class);
    private String hostname;
    private int port;
    private ServerBootstrap bootstrap;
    private EventLoopGroup parentGroup;
    private EventLoopGroup childGroup;
    private EventExecutorGroup executorGroup;// 工作线程组
    private ChannelEventListener listener;
    private final int childThreadNum = 3;//连接建立后childGroup线程池的线程数
    private final int executeThreadNum = 4;//工作线程为4个

    // handlers
    private NettyServerHandler nettyServerHandler;//接受客户端请求的handler
    //private ConnectManagerHandler connectManagerHandler;//将事件封装成一个事件，做后续的listener处理
    public EchoServer(String hostname,int port){
        this.hostname = hostname;
        this.port = port;
        init();//初始化
    }
    public EchoServer(String hostname,int port,ChannelEventListener listener){
        this.listener = listener;
        this.hostname = hostname;
        this.port = port;
        init();//初始化
    }
    public void setChannelEventListener(ChannelEventListener listener){
        this.listener = listener;
    }
    private void init(){
        this.bootstrap = new ServerBootstrap();
        this.semaphore = new Semaphore(1);
        parentGroup = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,"parentThread-"+index.incrementAndGet());
            }
        });
        childGroup = new NioEventLoopGroup(childThreadNum, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r,"childThread-"+index.incrementAndGet());
            }
        });

    }

    private void prepareHandlers(){
        nettyServerHandler = new NettyServerHandler();
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
        this.bootstrap.group(parentGroup, childGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_SNDBUF, 1024)
                .childOption(ChannelOption.SO_RCVBUF, 1024)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(executorGroup,
                                new RemotingEncoder(),
                                new RemotingDecoder(),
                                nettyServerHandler
                                );
                    }
                });

        if(null != listener){
            logger.info("ChannelEventListener任务启动，处理阻塞队列中的事件");
            eventExecutor.start();
        }
        try {
            ChannelFuture future = this.bootstrap.bind(new InetSocketAddress(hostname, port)).sync();
            logger.info("服务端启动成功，等待客户端应答");
            future.channel().closeFuture().sync();//等待主动去关闭channel才退出
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void shutdown() {
        if(null != parentGroup){
            this.parentGroup.shutdownGracefully();
        }

        if(null != childGroup){
            this.childGroup.shutdownGracefully();
        }

        if(null != executorGroup){
            this.executorGroup.shutdownGracefully();
        }
    }

    @Override
    public void invoke(Channel channel, RemotingCommand command,long timeout) throws InterruptedException, RemotingException, TimeoutException {
        this.invokeSyncImpl(channel,command,timeout);
    }

    @Override
    protected ChannelEventListener getChannelEventListener() {
        return listener;
    }

    @ChannelHandler.Sharable
    class NettyServerHandler extends SimpleChannelInboundHandler<RemotingCommand>{
        @Override
        protected void messageReceived(ChannelHandlerContext ctx, RemotingCommand command) throws Exception {
            processReceivedMessage(ctx,command);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("an exception has occurred");
            if(null != listener){
                Channel channel = ctx.channel();
                eventExecutor.putChannelEvent(
                        new ChannelEvent(ChannelEventType.EXCEPTION,channel,channel.remoteAddress().toString())
                );
            }
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            logger.info("a client is trying to connect the server");
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            logger.info("a client has succeed to connect the server");
            if(null != listener){
                Channel channel = ctx.channel();
                eventExecutor.putChannelEvent(
                        new ChannelEvent(ChannelEventType.CONNECT,channel,channel.remoteAddress().toString())
                );
            }
        }
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            logger.info("channel is close");
            if(null != listener){
                Channel channel = ctx.channel();
                eventExecutor.putChannelEvent(
                        new ChannelEvent(ChannelEventType.CLOSE,channel,channel.remoteAddress().toString())
                );
            }
        }
    }

    public static void main(String[] args) {
        EchoServer server = null ;
        try{
            server = new EchoServer("127.0.0.1",2088);
            server.setChannelEventListener(new ChannelEventListener() {
                //做回调 需要先将相应的channelEvent放入阻塞队列中，才能读取做处理
                @Override
                public void processOnConnect(String address, Channel channel) {
                    logger.info("connect:"+address);
                }

                @Override
                public void processOnClose(String address, Channel channel) {
                    logger.info("close:"+address);
                }

                @Override
                public void processOnException(String address, Channel channel) {
                    logger.info("exception:"+address);
                }
            });
            server.start();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(null != server){
                server.shutdown();
            }
        }
    }
}
