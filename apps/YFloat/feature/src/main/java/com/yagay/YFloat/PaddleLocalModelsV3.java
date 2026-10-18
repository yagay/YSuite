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
    public static final int UV_DOC=3;
    public static final int SEAL_DETECTION=4;
    public static final int FORMULA_NET=5;
    public static final int TEXTLINE_ORIENTATION=6;
    public static final String ORIENTATION_PREF="paddle_v3_classifier_enabled";
    public static final String TABLE_PREF="paddle_v3_table_enabled";
    public static final String UV_DOC_PREF="paddle_v3_uvdoc_enabled";
    public static final String SEAL_PREF="paddle_v3_seal_enabled";
    public static final String FORMULA_PREF="paddle_v3_formula_enabled";
    public static final String TEXTLINE_PREF="paddle_v3_textline_orientation_enabled";
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
            case UV_DOC -> new Spec("UVDoc (document unwarping)",
                "https://huggingface.co/marsena/paddleocr-onnx-models/resolve/main/UVDoc_infer.onnx",
                31684150L,"1092557894d49644e7858b293df6cb9c873d53e51319b91a8614ca9c71686dc0",null);
            case SEAL_DETECTION -> new Spec("PP-OCRv4 mobile seal detector",
                "https://huggingface.co/x3zvawq/paddleocr-js-onnx/resolve/main/ppocr_v4_mobile_seal_det/PP-OCRv4_mobile_seal_det_infer.onnx",
                -1L,"1d559581c8e8ef19a7f6d3b82f267ff51da31d863dd44ebb2bd02dd5f1a99d12",null);
            case TEXTLINE_ORIENTATION -> new Spec(
                "PP-LCNet textline orientation (0 / 180)",
                "https://huggingface.co/PaddlePaddle/PP-LCNet_x1_0_textline_ori_onnx/resolve/main/inference.onnx",
                -1L,"38aa97cd4be591e0ad304e659f07ba30d946f27a63315433f6659c69c8778345",null);
            case FORMULA_NET -> new Spec("PP-FormulaNet_plus-S (LaTeX)",
                "https://huggingface.co/x3zvawq/paddleocr-js-onnx/resolve/main/PP_FormulaNet_plus_S/PP-FormulaNet_plus-S_infer.onnx",
                -400000000L,"713d472d8299abbf936e0c7ac74de9c63cc62f9bcf2a01be5fa376bc1d30fb76",
                "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf/ppocr/utils/dict/unimernet_tokenizer/tokenizer.json");
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
    /** All runtime models are downloaded on demand into private app files. */
    public static long downloadBytes(int id){return Math.max(0L,spec(id).size);}
    public static boolean isDownloadable(int id){
        return id==DOC_ORIENTATION||id==TABLE_STRUCTURE||
               id==UV_DOC||id==SEAL_DETECTION||id==FORMULA_NET||
               id==TEXTLINE_ORIENTATION;
    }
    public static String displaySize(int id){
        long size=downloadBytes(id);
        return size>0?String.format(Locale.ROOT,"%.1f MB",size/1048576d):
                id==FORMULA_NET?"约 334 MB":id==SEAL_DETECTION?"约 4.8 MB":
                id==TEXTLINE_ORIENTATION?"约 6.8 MB":"未知大小";
    }
    public static String preference(int id){
        return switch(id) {
            case DOC_ORIENTATION -> ORIENTATION_PREF;
            case TABLE_STRUCTURE -> TABLE_PREF;
            case UV_DOC -> UV_DOC_PREF;
            case SEAL_DETECTION -> SEAL_PREF;
            case FORMULA_NET -> FORMULA_PREF;
            case TEXTLINE_ORIENTATION -> TEXTLINE_PREF;
            default -> throw new IllegalArgumentException("unknown preference "+id);
        };
    }
    public static File model(Context context,int id){
        return new File(new File(context.getApplicationContext().getFilesDir(),
                "ocr_models/paddle_local_v3"),switch(id) {
            case DOC_ORIENTATION -> "doc_orientation.onnx";
            case TABLE_STRUCTURE -> "table_slanet.onnx";
            case UV_DOC -> "uvdoc.onnx";
            case SEAL_DETECTION -> "seal_detector.onnx";
            case FORMULA_NET -> "formula_plus_s.onnx";
            case TEXTLINE_ORIENTATION -> "textline_orientation.onnx";
            default -> throw new IllegalArgumentException("unknown local model "+id);
        });
    }
    public static File formulaTokenizer(Context context){
        return new File(new File(context.getApplicationContext().getFilesDir(),
                "ocr_models/paddle_local_v3"),"formula_tokenizer.json");
    }
    public static File vocabulary(Context context){
        return new File(new File(context.getApplicationContext().getFilesDir(),
                "ocr_models/paddle_local_v3"),"table_slanet.yml");
    }
    public static boolean installed(Context context,int id) {
        File file=model(context,id);
        Spec s=spec(id);
        // Seal export has a pinned SHA-256 but its exact byte length is not
        // exposed by our upstream source. Never mark it executable on size alone.
        return file.isFile()&&(s.size>0?file.length()==s.size:file.length()>1000000L)
                &&(id!=TABLE_STRUCTURE||validVocabulary(vocabulary(context)))
                &&(id!=FORMULA_NET||validFormulaTokenizer(formulaTokenizer(context)));
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
                // Each model is optional and independently installed. Never
                // prefetch other models or update them as a side effect.
                long required=s.size>0?s.size:s.size< -1?-s.size:20000000L;
                if(dir.getUsableSpace()<required+64L*1024L*1024L)
                    throw new IllegalStateException("Insufficient storage for "
                        +s.name+" (leave 64 MB free)");
                saveUrl(s.url,dst,s.size,s.sha,callback);
                if(id==TABLE_STRUCTURE){
                    File vocab=vocabulary(app);
                    saveUrl(s.yamlUrl,vocab,32768L,null,null);
                    if(!validVocabulary(vocab))
                        throw new IllegalStateException("Table token dictionary is invalid");
                }
                if(id==FORMULA_NET){
                    File vocab=formulaTokenizer(app);
                    saveUrl(s.yamlUrl,vocab,5000000L,null,null);
                    if(!validFormulaTokenizer(vocab))
                        throw new IllegalStateException("Formula tokenizer validation failed");
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
                    long limit=upperBound>0?upperBound:upperBound< -1?-upperBound:20000000L;
                    if(count>limit)throw new IllegalStateException("Model exceeds size limit");
                    md.update(buf,0,n);out.write(buf,0,n);
                    if(callback!=null&&sha!=null){
                        int progress=(int)(100*count/limit);
                        if(progress!=lastProgress && progress%5==0){
                            lastProgress=progress;final int v=progress;
                            MAIN.post(()->callback.onProgress(v));
                        }
                    }
                }
                out.getFD().sync();
            }
            if(sha!=null){
                if(upperBound>0&&count!=upperBound)
                    throw new IllegalStateException("Model size mismatch");
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
        if(id==FORMULA_NET){
            File v=formulaTokenizer(c);if(v.exists())v.delete();
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
    private static boolean validFormulaTokenizer(File file){
        if(!file.isFile()||file.length()<500000L||file.length()>5000000L)return false;
        try(FileInputStream input=new FileInputStream(file)){
            // SHA-1 Git blob, pinned to PaddleOCR revision, NOT a plain file hash.
            MessageDigest sha=MessageDigest.getInstance("SHA-1");
            sha.update(("blob "+file.length()).getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
            sha.update((byte)0);
            byte[] data=new byte[65536];int n;
            while((n=input.read(data))!=-1)sha.update(data,0,n);
            StringBuilder out=new StringBuilder();
            for(byte v:sha.digest())out.append(String.format(Locale.ROOT,"%02x",v&255));
            return "55a77607295cd64d9f57365ad5b72d8c837af978".equals(out.toString());
        }catch(Exception error){return false;}
    }
    private PaddleLocalModelsV3(){}
}
