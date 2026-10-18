package com.yagay.YFloat;

import android.app.ActivityManager;
import android.content.Context;

/** Conservative local ONNX memory limits; no unverified NNAPI/QNN claim. */
public final class PaddleRuntimePolicy {
    public static final class Plan {
        public final int threads;
        public final int recognitionBatch;
        public final boolean lowMemory;
        private Plan(int threads, int recognitionBatch, boolean lowMemory) {
            this.threads=threads;this.recognitionBatch=recognitionBatch;
            this.lowMemory=lowMemory;
        }
    }

    public static Plan forModel(Context context,int model){
        ActivityManager manager=(ActivityManager)
                context.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        boolean low=false;
        if(manager!=null){
            low=manager.isLowRamDevice();
            try{
                manager.getMemoryInfo(memory);
                low|=memory.lowMemory||memory.availMem<768L*1024L*1024L;
            }catch(Throwable ignored){}
        }
        int cores=Math.max(1,Runtime.getRuntime().availableProcessors());
        int threads=Math.min(cores,low?2:4);
        int batch=low?1:(model==OcrModelManager.MEDIUM?2:4);
        // Medium uses substantially more weights and per-crop activations.
        if(model==OcrModelManager.MEDIUM)threads=Math.min(threads,3);
        return new Plan(Math.max(1,threads),Math.max(1,batch),low);
    }
    private PaddleRuntimePolicy(){}
}
