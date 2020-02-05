package com.chz.remoting;
/**
 * command 类型定义
 * **/
public enum CommandType {
    REQUEST(0),RESPONSE(1);
    private int code;
    CommandType(int code){
      this.code = code;
    }
    public int getCode(){
        return code;
    }
}
