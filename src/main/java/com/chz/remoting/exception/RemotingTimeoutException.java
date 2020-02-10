package com.chz.remoting.exception;

public class RemotingTimeoutException extends RemotingException {
    public RemotingTimeoutException(String message) {
        super(message);
    }

    public RemotingTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
