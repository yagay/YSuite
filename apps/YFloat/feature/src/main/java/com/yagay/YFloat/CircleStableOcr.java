package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Paddle-only adapter for Circle's frozen frame and planned correction ROI.
 * Recognizes directly via the same PaddleRecognitionRuntime as normal OCR.
 * It does not own alternative text parsers, language-fusion passes,
 * fake character boxes or a separate native-model cache.
 */
final class CircleStableOcr {
    static void recognizeFullScreenSelected(Context context,Bitmap bitmap,
            OcrEngine.DocumentCallback callback){
        if(!valid(context,bitmap,callback))return;
        Context app=context.getApplicationContext();
        int mode=new FloatSettings(app).circleFullOcrEngine();
        recognizePaddle(app,bitmap,PaddleRecognitionRuntime.circleModel(mode),
                "full_screen_index",new PaddleRequestToken(),callback);
    }

    /** The active frozen-screen owner may cancel before queued ONNX work. */
    static void recognizeFullScreenSelected(Context context,Bitmap bitmap,
            PaddleRequestToken request,OcrEngine.DocumentCallback callback){
        if(!valid(context,bitmap,callback))return;
        Context app=context.getApplicationContext();
        int mode=new FloatSettings(app).circleFullOcrEngine();
        recognizePaddle(app,bitmap,PaddleRecognitionRuntime.circleModel(mode),
                "gesture_roi",request,callback);
    }

    static void recognizeCorrectionSelected(Context context,Bitmap bitmap,
            OcrEngine.DocumentCallback callback){
        if(!valid(context,bitmap,callback))return;
        Context app=context.getApplicationContext();
        int mode=new FloatSettings(app).circleCorrectionEngine();
        if(mode==0){
            callback.onFailure(new IllegalStateException(
                    app.getString(R.string.yfloat_circle_correction_disabled)));
            return;
        }
        recognizePaddle(app,bitmap,PaddleRecognitionRuntime.circleModel(mode),
                "gesture_correction",new PaddleRequestToken(),callback);
    }

    private static void recognizePaddle(Context app,Bitmap bitmap,int model,
            String role,PaddleRequestToken request,OcrEngine.DocumentCallback callback){
        long started=android.os.SystemClock.uptimeMillis();
        DiagnosticLog.i(app,"FL_CIRCLE_PADDLE",
                "role="+role+" requestedModel="+model+" size="+
                bitmap.getWidth()+"x"+bitmap.getHeight());
        PaddleRecognitionRuntime.recognize(app,bitmap,model,true,request,
                new PaddleRecognitionRuntime.Callback(){
                    @Override public void onSuccess(OcrDocument document,
                            int usedModel,long elapsedMs){
                        if(document==null||document.fullText().isBlank()||
                                document.chars().isEmpty()){
                            callback.onFailure(new IllegalStateException(
                                    app.getString(R.string.yfloat_ppocr_empty)));
                            return;
                        }
                        DiagnosticLog.i(app,"FL_CIRCLE_PADDLE",
                                "role="+role+" model="+usedModel+
                                " lines="+document.lines().size()+
                                " units="+document.chars().size()+
                                " elapsedMs="+(android.os.SystemClock.uptimeMillis()-started));
                        callback.onSuccess(document);
                    }
                    @Override public void onFailure(Throwable error){
                        callback.onFailure(error==null?
                                new IllegalStateException("PaddleOCR failed"):error);
                    }
                });
    }

    static String fullModeLabel(Context context){
        return modeLabel(new FloatSettings(context.getApplicationContext())
                .circleFullOcrEngine());
    }

    static String correctionModeLabel(Context context){
        int mode=new FloatSettings(context.getApplicationContext()).circleCorrectionEngine();
        return mode==0?context.getString(R.string.yfloat_ocr_off):modeLabel(mode);
    }

    private static String modeLabel(int mode){
        return switch(mode){
            case 1 -> "paddle_tiny";
            case 2 -> "paddle_small";
            case 3 -> "paddle_medium";
            case 4 -> "paddle_mobile";
            default -> "paddle_auto";
        };
    }

    private static boolean valid(Context context,Bitmap bitmap,
            OcrEngine.DocumentCallback callback){
        if(callback==null)return false;
        if(context==null||bitmap==null||bitmap.isRecycled()||
                bitmap.getWidth()<1||bitmap.getHeight()<1){
            callback.onFailure(new IllegalArgumentException("Invalid Circle OCR frame"));
            return false;
        }
        return true;
    }
    private CircleStableOcr(){}
}
