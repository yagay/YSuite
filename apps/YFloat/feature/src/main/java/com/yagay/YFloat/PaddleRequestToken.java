package com.yagay.YFloat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cancellation signal for one OCR request. Cancellation never interrupts
 * ONNX/native inference or releases a model while an inference is running.
 * The pipeline checks it on the serialized queue and at safe stage boundaries.
 * A canceled request still receives its completion callback so that the owner
 * can recycle the bitmap it supplied.
 */
public final class PaddleRequestToken {
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final long createdNanos=System.nanoTime();
    public void cancel(){cancelled.set(true);}
    public boolean cancelled(){return cancelled.get();}
    public long queueMs(){return (System.nanoTime()-createdNanos)/1000000L;}
    public void check(){
        if(cancelled())throw new java.util.concurrent.CancellationException("OCR request superseded");
    }
}
