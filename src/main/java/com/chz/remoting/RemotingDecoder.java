package com.chz.remoting;

import com.chz.remoting.protocol.RemotingCommand;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.jboss.logging.Logger;

import java.nio.ByteBuffer;

public class RemotingDecoder extends LengthFieldBasedFrameDecoder {

    private final static int maxFrameLength = 16777216;
    private final static int lengthFieldOffset = 0;
    private final static int lengthFieldLength = 4;
    private final static int lengthAdjustment = 0;
    private final static int initialBytesToStrip = 4;

    private Logger logger = Logger.getLogger(RemotingDecoder.class);
    public RemotingDecoder() {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = null;
        try {
            frame = (ByteBuf) super.decode(ctx, in);
            if (null == frame) {
                return null;
            }

            ByteBuffer byteBuffer = frame.nioBuffer();
            return RemotingCommand.decode(byteBuffer);
        } catch (Exception e) {
            final Channel channel  = ctx.channel();
            logger.error("decode exception, " + channel.remoteAddress());
            channel.close().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    logger.infof(
                            "closeChannel: close the connection to remote address[{}] result: {}",channel.remoteAddress(),channelFuture.isSuccess()
                    );
                }
            });
        } finally {
            if (null != frame) {
                frame.release();
            }
        }
        return null;
    }
}
