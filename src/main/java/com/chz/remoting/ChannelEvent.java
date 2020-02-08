package com.chz.remoting;

import io.netty.channel.Channel;

public class ChannelEvent {
   private ChannelEventType eventType ;
   private Channel channel;
   private String address;

    public ChannelEventType getEventType() {
        return eventType;
    }
    public Channel getChannel() {
        return channel;
    }
    public String getAddress() {
        return address;
    }

    public ChannelEvent(ChannelEventType eventType, Channel channel, String address) {
        this.eventType = eventType;
        this.channel = channel;
        this.address = address;
    }

    @Override
    public String toString() {
        return "[eventType= "+eventType+" , address = "+address+" ]";
    }
}
