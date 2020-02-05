/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chz.remoting;

import com.chz.remoting.protocol.RemotingCommand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.MessageToByteEncoder;
import org.jboss.logging.Logger;

import java.nio.ByteBuffer;

@ChannelHandler.Sharable
public class RemotingEncoder extends MessageToByteEncoder<RemotingCommand> {
    private Logger logger = Logger.getLogger(RemotingEncoder.class);
    @Override
    public void encode(ChannelHandlerContext ctx, RemotingCommand remotingCommand, ByteBuf out)
        throws Exception {
        ByteBuffer byteBuffer = remotingCommand.encode();
        try{
            out.writeBytes(byteBuffer);
            logger.info("request-"+remotingCommand.getRequestId()+"发送成功");
        }catch (Exception e) {
            final Channel channel = ctx.channel();
            logger.error("encode exception, " + channel.remoteAddress());
            if (remotingCommand != null) {
                logger.error(remotingCommand.toString());
            }
            channel.close().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    logger.infof(
                            "closeChannel: close the connection to remote address[{}] result: {}",channel.remoteAddress(),channelFuture.isSuccess()
                    );
                }
            });
        }
    }
}
