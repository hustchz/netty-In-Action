package com.chz.remoting;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class SemaphoreReleaseOnlyOnce {
    private Semaphore semaphore;
    private volatile AtomicBoolean released = new AtomicBoolean(false);

    public SemaphoreReleaseOnlyOnce(Semaphore semaphore){
        this.semaphore = semaphore;
    }
    public Semaphore getSemaphore(){
        return this.semaphore;
    }
    public void release(){
        if(null != semaphore){
            if(released.compareAndSet(false,true)){
                semaphore.release();
            }
        }
    }
}
