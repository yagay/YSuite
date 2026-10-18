package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

/**
 * The single local OCR runtime for YFloat image and Circle recognition.
 *
 * PaddlePipelineV2 owns model sessions, inference and the typed document.
 * This class owns selection and fallback among locally installed Paddle
 * models only. No ML Kit model, legacy text fusion or geometry synthesis.
 */
final class PaddleRecognitionRuntime {
    interface Callback {
        void onSuccess(OcrDocument document, int model, long elapsedMs);
        void onFailure(Throwable error);
    }

    private static final int[] AUTO_ORDER = {
        OcrModelManager.SMALL, OcrModelManager.MEDIUM,
        OcrModelManager.MOBILE, OcrModelManager.TINY
    };

    /** Normal OCR mode: 0 Auto, 1 Medium, 2 Small, 5 Mobile, 6 Tiny. */
    static int normalModel(int storedMode) {
        return switch(storedMode) {
            case 1 -> OcrModelManager.MEDIUM;
            case 2 -> OcrModelManager.SMALL;
            case 5 -> OcrModelManager.MOBILE;
            case 6 -> OcrModelManager.TINY;
            default -> 0;
        };
    }

    /** Circle full-screen mode: 5 Auto, 1 Tiny, 2 Small, 3 Medium, 4 Mobile. */
    static int circleModel(int storedMode) {
        return switch(storedMode) {
            case 1 -> OcrModelManager.TINY;
            case 2 -> OcrModelManager.SMALL;
            case 3 -> OcrModelManager.MEDIUM;
            case 4 -> OcrModelManager.MOBILE;
            default -> 0;
        };
    }

    static boolean hasInstalledModel(Context context) {
        for(int id:AUTO_ORDER)if(OcrModelManager.isReady(context,id))return true;
        return false;
    }

    static void recognize(Context context,Bitmap bitmap,int requestedModel,Callback cb){
        recognize(context,bitmap,requestedModel,false,cb);
    }

    /** Circle and document use independently configured Paddle stage profiles. */
    static void recognize(Context context,Bitmap bitmap,int requestedModel,
                          boolean circle,Callback cb){
        if(cb==null)return;
        if(context==null||bitmap==null||bitmap.isRecycled()||
                bitmap.getWidth()<=0||bitmap.getHeight()<=0){
            cb.onFailure(new IllegalArgumentException("Invalid image for offline PaddleOCR"));
            return;
        }
        Context app=context.getApplicationContext();
        ArrayList<Integer> ready=new ArrayList<>();
        if(requestedModel==0){
            for(int id:AUTO_ORDER)if(OcrModelManager.isReady(app,id))ready.add(id);
        }else if(OcrModelManager.isReady(app,requestedModel)){
            ready.add(requestedModel);
        }
        if(ready.isEmpty()){
            cb.onFailure(new IllegalStateException(
                    "Install or repair an offline PaddleOCR model in YFloat settings"));
            return;
        }
        recognizeCandidate(app,bitmap,ready,0,null,circle,cb);
    }

    private static void recognizeCandidate(Context app,Bitmap image,List<Integer> available,
            int next,OcrDocument previous,boolean circle,Callback callback){
        if(next>=available.size()){
            if(previous!=null&&!previous.fullText().isBlank()){
                callback.onSuccess(previous,available.get(available.size()-1),0L);
            }else callback.onFailure(new IllegalStateException(
                    "Installed PaddleOCR models did not produce usable text"));
            return;
        }
        int model=available.get(next);
        PaddlePipelineV2.recognize(app,image,model,PaddleFlowOptions.read(app,circle),
                new PaddlePipelineV2.Callback(){
            @Override public void onSuccess(OcrDocument document,long elapsed,int lineCount){
                if(document==null||document.fullText().isBlank()){
                    recognizeCandidate(app,image,available,next+1,previous,circle,callback);
                    return;
                }
                OcrDocument winner=better(previous,document);
                boolean ambiguous=document.confidence()<.78f ||
                        document.fullText().codePointCount(0,document.fullText().length())<5;
                // Only escalate the first auto model when recognition provides
                // weak evidence. Avoid wasting RAM on all four sessions.
                if(ambiguous && next==0 && available.size()>1){
                    recognizeCandidate(app,image,available,next+1,winner,circle,callback);
                    return;
                }
                callback.onSuccess(winner,model,elapsed);
            }
            @Override public void onFailure(String error){
                DiagnosticLog.i(app,"PADDLE_RUNTIME","model="+model+" failure="+error);
                if(next+1<available.size()){
                    recognizeCandidate(app,image,available,next+1,previous,circle,callback);
                }else if(previous!=null&&!previous.fullText().isBlank()){
                    callback.onSuccess(previous,model,0L);
                }else callback.onFailure(new IllegalStateException(
                        "PaddleOCR "+OcrModelManager.displayName(model)+": "+error));
            }
        });
    }

    private static OcrDocument better(OcrDocument a,OcrDocument b){
        if(a==null||a.fullText().isBlank())return b;
        if(b==null||b.fullText().isBlank())return a;
        // PaddlePageV2 creates scores from confidence, coverage and layout.
        return b.score()>a.score()?b:a;
    }
    private PaddleRecognitionRuntime(){}
}
