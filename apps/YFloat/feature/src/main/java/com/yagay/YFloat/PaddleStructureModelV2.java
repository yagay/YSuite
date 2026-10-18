package com.yagay.YFloat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent verified model owner for PP-DocLayout-S offline layout stage. */
public final class PaddleStructureModelV2 {
    public static final String NAME="PP-DocLayout-S (on-device layout)";
    public static final String URL="https://huggingface.co/stefanj0/PP-DocLayout-S-ONNX/resolve/main/pp_doclayout_s.onnx?download=true";
    public static final long BYTES=4917852L;
    public static final String SHA256="33688dbee1c23e34b81777e97cb428eb40f24b242c02b5f623484959e830aec8";
    public static final String PREF_ENABLED="paddle_structure_v2_enabled";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile boolean downloading;
    public interface Callback {
        void onProgress(int percent);
        void onSuccess();
        void onFailure(String reason);
    }
    public static File model(Context context) {
        return new File(context.getApplicationContext().getFilesDir(),
                "ocr_models/pp_structure_v3/pp_doclayout_s.onnx");
    }
    public static boolean isReady(Context context) {
        File f=model(context);
        return f.isFile()&&f.length()==BYTES;
    }
    /** Size alone is not sufficient before loading an executable ONNX graph. */
    public static boolean verifyIntegrity(Context context) {
        File source=model(context);
        if(!isReady(context))return false;
        try(FileInputStream in=new FileInputStream(source)){
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[65536];
            int n;
            while((n=in.read(buffer))!=-1)sha.update(buffer,0,n);
            StringBuilder out=new StringBuilder(64);
            for(byte b:sha.digest())out.append(String.format(Locale.ROOT,"%02x",b&0xff));
            return SHA256.equals(out.toString());
        }catch(Throwable ignored){return false;}
    }
    public static boolean enabled(Context context) {
        return context.getSharedPreferences(FloatSettings.PREF,Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED,false) && isReady(context);
    }
    public static void setEnabled(Context context,boolean enabled) {
        context.getSharedPreferences(FloatSettings.PREF,Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED,enabled).apply();
    }
    public static boolean isDownloading(){return downloading;}
    public static void download(Context context,Callback callback) {
        Context app=context.getApplicationContext();
        synchronized(PaddleStructureModelV2.class){
            if(downloading){if(callback!=null)MAIN.post(()->callback.onFailure("already downloading"));return;}
            downloading=true;
        }
        IO.execute(()->{
            File output=model(app);
            File part=new File(output.getAbsolutePath()+".part");
            try{
                File parent=output.getParentFile();
                if(parent==null||(!parent.exists()&&!parent.mkdirs()))
                    throw new IllegalStateException("Cannot create model directory");
                HttpURLConnection conn=(HttpURLConnection)new URL(URL).openConnection();
                conn.setConnectTimeout(20000);conn.setReadTimeout(120000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent","YFloat-PaddleV2");
                try {
                    if(conn.getResponseCode()!=200)
                        throw new IllegalStateException("HTTP "+conn.getResponseCode());
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");
                    long bytes=0;
                    try(var stream=conn.getInputStream();
                        var fos=new FileOutputStream(part,false)){
                        byte[] buffer=new byte[65536];
                        int n;
                        while((n=stream.read(buffer))!=-1){
                            bytes+=n;
                            if(bytes>BYTES)throw new IllegalStateException("Model too large");
                            digest.update(buffer,0,n);fos.write(buffer,0,n);
                            int percent=(int)(bytes*100/BYTES);
                            if(callback!=null&&percent%10==0)
                                MAIN.post(()->callback.onProgress(percent));
                        }
                        fos.getFD().sync();
                    }
                    StringBuilder hash=new StringBuilder(64);
                    for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&0xff));
                    if(bytes!=BYTES||!SHA256.equals(hash.toString()))
                        throw new IllegalStateException("Model checksum mismatch");
                } finally { conn.disconnect(); }
                if(output.exists()&&!output.delete())throw new IllegalStateException("Cannot replace model");
                if(!part.renameTo(output))throw new IllegalStateException("Cannot install model");
                if(callback!=null)MAIN.post(callback::onSuccess);
            }catch(Throwable e){
                if(callback!=null)MAIN.post(()->callback.onFailure(
                        e.getClass().getSimpleName()+": "+e.getMessage()));
                DiagnosticLog.i(app,"PP_STRUCTURE_V2","model_download_failed="+e.getClass().getSimpleName());
            }finally{
                if(part.exists())part.delete();
                downloading=false;
            }
        });
    }
    public static void delete(Context context){
        setEnabled(context,false);
        File f=model(context);
        if(f.exists())f.delete();
    }
    private PaddleStructureModelV2(){}
}
