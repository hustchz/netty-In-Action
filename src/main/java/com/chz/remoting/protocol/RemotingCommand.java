package com.chz.remoting.protocol;

import com.chz.remoting.CommandType;
import com.chz.remoting.serialize.RemotingSerialize;
import io.netty.buffer.ByteBuf;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务端和客户端共同约定的传输对象：
 * 传输的数据包括 总长度length 头部长度headerLength 头部数据headerData 和body数据
 * **/
public class RemotingCommand {
    // header 头部元素
    private static AtomicInteger requestIndex = new AtomicInteger(0);
    private int flag;//0 代表是请求，1 代表是答复
    private byte [] body;//消息体
    private int requestId = requestIndex.incrementAndGet();//每一次的请求号都是递增的
    private Map<String,String> extFields;// 自定义的扩展字段
    public RemotingCommand(int flag){
        this.flag = flag;
    }
    public RemotingCommand(){}
    public void setFlag(int flag) {
        this.flag = flag;
    }
    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }
    public void setBody(byte[]bytes){
        this.body = bytes;
    }
    public byte[] getBody(){
        return body;
    }
    public int getFlag(){
        return flag;
    }
    public int getRequestId(){
        return requestId;
    }
    public void setExtFields(Map<String, String> extFields) {
        this.extFields = extFields;
    }
    public Map<String, String> getExtFields() {
        return extFields;
    }

    //编码过程 (RemotingCommand->byteBuffer)
    public ByteBuffer encode() throws UnsupportedEncodingException {
        final int recodeTotalLengthByteSize = 4;//用int去记录总长度，4个字节
        final int recodeHeaderLengthByteSize = 4;//用int去记录总长度，4个字节
        int totalLen = 0;
        // 头部数据
        byte[] header = RemotingSerialize.encodeHeader(this);
        int headerLen = header.length;
        boolean hasBody = false;
        if(null != body && body.length>0){
            totalLen += body.length;
            hasBody = true;
        }
        totalLen += headerLen;
        ByteBuffer byteBuffer = ByteBuffer.allocate(totalLen + recodeTotalLengthByteSize + recodeHeaderLengthByteSize);

        byteBuffer.putInt(totalLen);
        byteBuffer.putInt(headerLen);
        byteBuffer.put(header);
        if(hasBody){
            byteBuffer.put(body);
        }
        byteBuffer.flip();//复位
        return byteBuffer;
    }

    public static RemotingCommand decode(final byte[]bytes) throws UnsupportedEncodingException {
        if(null == bytes || bytes.length <= 0) return null;
        return decode(ByteBuffer.wrap(bytes));
    }

    private static RemotingCommand decode(ByteBuffer byteBuffer) throws UnsupportedEncodingException {
        // 在解码器中会采用基于长度的解码器，因此处理的是去掉记录总长度字段的byteBuffer
        int totalLen = byteBuffer.limit();//头部长度+头部数据+body数据
        int headerLen = byteBuffer.getInt() & 0xFFFFFF;
        byte [] headerData = new byte[headerLen];
        byteBuffer.get(headerData);
        RemotingCommand command = RemotingSerialize.decode(headerData);
        int bodyLen = totalLen - 4 - headerLen;
        byte []body = new byte[bodyLen];
        byteBuffer.get(body);
        command.setBody(body);
        return command;
    }

    public RemotingCommand createRequestCommand(){
        return new RemotingCommand(CommandType.REQUEST.getCode());
    }
    public RemotingCommand createResponseCommand(){
        return new RemotingCommand(CommandType.RESPONSE.getCode());
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        RemotingCommand command = new RemotingCommand(0);
        Map<String,String>extField = new HashMap<>();
        extField.put("version","1.0.0");
        extField.put("test","true");
        command.setExtFields(extField);
        command.setBody("华中科技大学".getBytes("UTF-8"));
        ByteBuffer byteBuffer = command.encode();
        byteBuffer.getInt();//手动将头部总长度过滤掉
        byte []content = new byte[byteBuffer.remaining()];
        byteBuffer.get(content);
        RemotingCommand cmd = decode(content);
        System.out.println(new String(cmd.getBody(),"UTF-8"));
        Iterator<Map.Entry<String, String>> iterator = cmd.getExtFields().entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<String, String> next = iterator.next();
            System.out.println(next.getKey()+" "+next.getValue());
        }

    }
}
