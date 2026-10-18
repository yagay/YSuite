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

/**
 * Offline model registry. Network access is used ONLY by an explicit user
 * download request. Inference never contacts the network or Google App.
 *
 * Models are pinned to their verified ONNX digests. The small table vocabulary
 * file is schema-checked independently and cannot change executable weights.
 */
public final class PaddleLocalModelsV3 {
    public static final int DOC_ORIENTATION=1;
    public static final int TABLE_STRUCTURE=2;
    public static final String ORIENTATION_PREF="paddle_v3_classifier_enabled";
    public static final String TABLE_PREF="paddle_v3_table_enabled";
    private record Spec(String name,String url,long size,String sha,String yamlUrl) {}
    private static Spec spec(int id) {
        return switch(id) {
            case DOC_ORIENTATION -> new Spec(
                "PP-LCNet document orientation",
                "https://huggingface.co/ningpp/PP-LCNet_x1_0_doc_ori-ONNX/resolve/main/model.onnx",
                6781504L,"10453f80f6f50e8149161aef61b265119471e4010263a82682e5695914236f27",null);
            case TABLE_STRUCTURE -> new Spec(
                "SLANet-1M table structure",
                "https://huggingface.co/bdatdo0601/slanet-1m-onnx/resolve/main/slanet_1m.onnx",
                7671589L,"8a8aa31bf964c1c05039f02814e5f425a354a37552eaca8e6d5dc513048759f5",
                "https://huggingface.co/bdatdo0601/slanet-1m-onnx/resolve/main/inference.yml");
            default -> throw new IllegalArgumentException("unknown local Paddle model "+id);
        };
    }
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"YFloat-Paddle-model");
        t.setDaemon(true);return t;
    });
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final java.util.Set<Integer> ACTIVE=new java.util.HashSet<>();
    public interface Callback {
        void onProgress(int percent);
        void onSuccess();
        void onFailure(String reason);
    }
    public static String name(int id){return spec(id).name;}
    public static String preference(int id){
        return id==DOC_ORIENTATION?ORIENTATION_PREF:TABLE_PREF;
    }
    public static File model(Context context,int id){
        return new File(new File(context.getApplicationContext().getFilesDir(),
                "ocr_models/paddle_local_v3"),id==DOC_ORIENTATION?"doc_orientation.onnx":"table_slanet.onnx");
    }
    public static File vocabulary(Context context){
        return new File(new File(context.getApplicationContext().getFilesDir(),
                "ocr_models/paddle_local_v3"),"table_slanet.yml");
    }
    public static boolean installed(Context context,int id) {
        File file=model(context,id);
        return file.isFile()&&file.length()==spec(id).size
                &&(id!=TABLE_STRUCTURE||validVocabulary(vocabulary(context)));
    }
    public static boolean enabled(Context context,int id) {
        return context.getSharedPreferences(FloatSettings.PREF,Context.MODE_PRIVATE)
                .getBoolean(preference(id),false)&&installed(context,id);
    }
    public static void setEnabled(Context context,int id,boolean value) {
        context.getSharedPreferences(FloatSettings.PREF,Context.MODE_PRIVATE)
                .edit().putBoolean(preference(id),value).apply();
    }
    public static boolean busy(int id){synchronized(ACTIVE){return ACTIVE.contains(id);}}
    public static boolean verify(Context context,int id){
        File file=model(context,id);
        if(!installed(context,id))return false;
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            try(FileInputStream stream=new FileInputStream(file)){
                byte[] buf=new byte[65536];int n;
                while((n=stream.read(buf))!=-1)md.update(buf,0,n);
            }
            StringBuilder sha=new StringBuilder();
            for(byte b:md.digest())sha.append(String.format(Locale.ROOT,"%02x",b&255));
            return spec(id).sha.equals(sha.toString());
        }catch(Exception error){return false;}
    }
    public static void install(Context context,int id,Callback callback){
        Context app=context.getApplicationContext();
        synchronized(ACTIVE){
            if(!ACTIVE.add(id)){
                if(callback!=null)MAIN.post(()->callback.onFailure("Download already running"));
                return;
            }
        }
        WORKER.execute(()->{
            try {
                Spec s=spec(id);
                File dst=model(app,id),dir=dst.getParentFile();
                if(dir==null||(!dir.isDirectory()&&!dir.mkdirs()))
                    throw new IllegalStateException("Cannot create local model directory");
                saveUrl(s.url,dst,s.size,s.sha,callback);
                if(id==TABLE_STRUCTURE){
                    File vocab=vocabulary(app);
                    saveUrl(s.yamlUrl,vocab,32768L,null,null);
                    if(!validVocabulary(vocab))
                        throw new IllegalStateException("Table token dictionary is invalid");
                }
                if(!verify(app,id))throw new IllegalStateException("ONNX model integrity failed");
                if(callback!=null)MAIN.post(callback::onSuccess);
            } catch(Throwable error){
                delete(app,id);
                if(callback!=null)MAIN.post(()->callback.onFailure(error.getMessage()==null
                    ?error.getClass().getSimpleName():error.getMessage()));
            } finally {synchronized(ACTIVE){ACTIVE.remove(id);}}
        });
    }
    private static void saveUrl(String url,File dest,long upperBound,String sha,
                                Callback callback)throws Exception{
        File temp=new File(dest.getAbsolutePath()+".part");
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(25000);
        connection.setReadTimeout(120000);
        connection.setInstanceFollowRedirects(true);
        try {
            if(connection.getResponseCode()!=200)
                throw new IllegalStateException("Model HTTP "+connection.getResponseCode());
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            long count=0;int lastProgress=-1;
            try(var in=connection.getInputStream();var out=new FileOutputStream(temp,false)){
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1){
                    count+=n;
                    if(count>upperBound)throw new IllegalStateException("Model is larger than pinned version");
                    md.update(buf,0,n);out.write(buf,0,n);
                    if(callback!=null&&sha!=null){
                        int progress=(int)(100*count/upperBound);
                        if(progress!=lastProgress && progress%5==0){
                            lastProgress=progress;final int v=progress;
                            MAIN.post(()->callback.onProgress(v));
                        }
                    }
                }
                out.getFD().sync();
            }
            if(sha!=null){
                if(count!=upperBound)throw new IllegalStateException("Model size mismatch");
                StringBuilder hex=new StringBuilder(64);
                for(byte b:md.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
                if(!sha.equals(hex.toString()))
                    throw new IllegalStateException("Model SHA-256 mismatch");
            }else if(count<100)throw new IllegalStateException("Vocabulary too small");
            if(dest.exists()&&!dest.delete())throw new IllegalStateException("Cannot replace model file");
            if(!temp.renameTo(dest))throw new IllegalStateException("Cannot install model file");
        } finally {
            connection.disconnect();
            if(temp.exists())temp.delete();
        }
    }
    public static void delete(Context c,int id) {
        setEnabled(c,id,false);
        File f=model(c,id);
        if(f.exists())f.delete();
        if(id==TABLE_STRUCTURE){
            File v=vocabulary(c);if(v.exists())v.delete();
        }
    }
    private static boolean validVocabulary(File file) {
        if(!file.isFile()||file.length()<100||file.length()>32768)return false;
        try {
            String yaml=java.nio.file.Files.readString(file.toPath());
            int start=yaml.indexOf("character_dict:");
            if(start<0)return false;
            int entries=0;
            for(String line:yaml.substring(start).split("\\r?\\n")){
                if(line.trim().startsWith("- "))entries++;
                else if(entries>0&&!line.isBlank())break;
            }
            return entries==28;
        }catch(Exception error){return false;}
    }
    private PaddleLocalModelsV3(){}
}
