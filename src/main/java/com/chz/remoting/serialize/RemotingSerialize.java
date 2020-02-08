package com.chz.remoting.serialize;

import com.chz.remoting.protocol.RemotingCommand;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 序列化类
 * **/
public class RemotingSerialize {

    //编码指定command的头部数据
    public static byte[]encodeHeader(RemotingCommand command) throws UnsupportedEncodingException {
        // 需要得到头部数据有多大，再来分配具体的byteBuffer大小
        int headerLen = 0;
        byte[] encodeExtFields = null;
        Map<String, String> extFields = command.getExtFields();
        if(null != extFields && !extFields.isEmpty()){
            encodeExtFields = encodeExtFields(extFields);
        }
        int encodeExtFieldsLen = (encodeExtFields == null ? 0 :encodeExtFields.length);
        headerLen = headerLen
                // requestId
                + 4
                // code
                + 4
                // flag
                + 4
                // encodeExtFields的长度
                + 4
                + encodeExtFieldsLen;

        ByteBuffer headerBuffer = ByteBuffer.allocate(headerLen);

        //开始添加头部数据
        headerBuffer.putInt(command.getRequestId());
        headerBuffer.putInt(command.getCode());
        headerBuffer.putInt(command.getFlag());
        headerBuffer.putInt(encodeExtFieldsLen);

        if(null != encodeExtFields){
            headerBuffer.put(encodeExtFields);
        }
        return headerBuffer.array();

    }
    private static byte[] encodeExtFields(Map<String, String> extFields) throws UnsupportedEncodingException {
        //第一遍遍历,得到扩展头部字段的总长度
        Iterator<Map.Entry<String, String>> lenIterator = extFields.entrySet().iterator();
        int totalLen = 0;// 记录扩展字段的K,V数据内容以及对应K,V的长度
        while(lenIterator.hasNext()){
            Map.Entry<String, String> next = lenIterator.next();
            // K的大小用short 2个字节 V的大小用int ,4个字节
            String key = next.getKey();
            String value = next.getValue();
            totalLen = totalLen
                    // 2 short的字节，记录Key的长度
                    + 2
                    + key.getBytes("UTF-8").length
                    // 4 int 的字节，记录value的长度
                    + 4
                    + value.getBytes("UTF-8").length;

        }
        //第二次迭代向申请的ByteBuffer中写入数据
        ByteBuffer byteBuffer = ByteBuffer.allocate(totalLen);
        Iterator<Map.Entry<String, String>> iterator = extFields.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<String, String> next = iterator.next();
            String key = next.getKey();
            String value = next.getValue();

            byte[]k = key.getBytes("UTF-8");
            byteBuffer.putShort((short) k.length);
            byteBuffer.put(k);
            byte[]v = value.getBytes("UTF-8");
            byteBuffer.putInt(v.length);
            byteBuffer.put(v);
        }
        return byteBuffer.array();
    }

    /**
     * 将 头部的数据bytes 解析成自定义的RemotingCommand对象
     * **/
    public static RemotingCommand decode(final byte[]headerBytes) throws UnsupportedEncodingException {
        RemotingCommand command = new RemotingCommand();
        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes);

        command.setRequestId(headerBuffer.getInt());
        command.setCode(headerBuffer.getInt());
        command.setFlag(headerBuffer.getInt());
        int extFieldsLen = headerBuffer.getInt();
        if(extFieldsLen > 0){
            byte[]ext = new byte[extFieldsLen];
            headerBuffer.get(ext);
            Map<String, String> extFields = decodeExtFields(ext);
            command.setExtFields(extFields);
        }
        return command;
    }

    private static Map<String,String> decodeExtFields(final byte[] ext) throws UnsupportedEncodingException {
        Map<String,String> map = new HashMap<>();
        ByteBuffer extBuffer = ByteBuffer.wrap(ext);
        byte [] key;
        byte [] value;
        while(extBuffer.hasRemaining()){
            key = new byte[extBuffer.getShort()];
            extBuffer.get(key);
            value = new byte[extBuffer.getInt()];
            extBuffer.get(value);
            map.put(new String(key,"UTF-8"),new String(value,"UTF-8"));
        }
        return map;
    }
}
