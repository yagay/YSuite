package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Official PP-StructureV3 complete pipeline client.
 *
 * Unlike the on-device PP-DocLayout-S stage, this sends a page to an
 * explicitly configured official PaddleOCR /layout-parsing service. The
 * service owns doc preprocessing, OCR, table/formula/chart/seal models, reading
 * order, Markdown and DOCX. Nothing is silently uploaded from other modes.
 */
final class PaddleStructureFullV3 {
    static final String K_ENDPOINT="paddle_structure_full_v3_endpoint";
    static final String K_MARKDOWN_IMAGES="paddle_structure_full_v3_images";
    static final String K_DOCX="paddle_structure_full_v3_docx";
    private static final int MAX_RESPONSE_BYTES=64*1024*1024;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"YFloat-PPStructure-FullV3");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    interface Callback {
        void onSuccess(OcrDocument document);
        void onFailure(Throwable error);
    }

    static String configuredEndpoint(Context context) {
        String endpoint=context.getSharedPreferences(FloatSettings.PREF,Context.MODE_PRIVATE)
                .getString(K_ENDPOINT,"");
        return endpoint==null?"":endpoint.trim();
    }

    static String validateEndpoint(String value) {
        if(value==null||value.isBlank())return "Service address is required";
        try{
            URL parsed=new URL(value.trim());
            if(!"https".equalsIgnoreCase(parsed.getProtocol()))
                return "HTTPS is required to protect screenshots and document contents";
            if(parsed.getUserInfo()!=null||parsed.getRef()!=null||parsed.getQuery()!=null)
                return "Do not include user credentials, query or fragment";
            if(parsed.getHost()==null||parsed.getHost().isBlank())return "Missing server host";
            String path=parsed.getPath();
            if(!(path.isEmpty()||"/".equals(path)
                    ||"/layout-parsing".equals(path)))
                return "Use the service origin or the /layout-parsing endpoint";
            return "";
        }catch(Exception e){return "Invalid HTTPS service address";}
    }

    static String url(String configured) {
        String trimmed=configured.trim().replaceAll("/+$","");
        return trimmed.endsWith("/layout-parsing")?trimmed:trimmed+"/layout-parsing";
    }

    static void recognize(Context context, Bitmap bitmap, Callback callback) {
        if(callback==null)return;
        Context app=context.getApplicationContext();
        String endpoint=configuredEndpoint(app);
        String validation=validateEndpoint(endpoint);
        if(!validation.isEmpty()){
            MAIN.post(()->callback.onFailure(new IllegalStateException(validation)));
            return;
        }
        if(bitmap==null||bitmap.isRecycled()||bitmap.getWidth()==0||bitmap.getHeight()==0){
            MAIN.post(()->callback.onFailure(new IllegalArgumentException("Invalid OCR image")));
            return;
        }
        boolean includeImages=app.getSharedPreferences(FloatSettings.PREF,Context.MODE_PRIVATE)
                .getBoolean(K_MARKDOWN_IMAGES,false);
        boolean includeDocx=app.getSharedPreferences(FloatSettings.PREF,Context.MODE_PRIVATE)
                .getBoolean(K_DOCX,false);
        // Caller keeps ownership of its original image. Take one immutable snapshot.
        Bitmap snapshot;
        try{
            snapshot=bitmap.copy(Bitmap.Config.ARGB_8888,false);
            if(snapshot==null)throw new IllegalStateException("Unable to copy OCR image");
        }catch(Throwable failure){
            MAIN.post(()->callback.onFailure(failure));
            return;
        }
        WORKER.execute(()->{
            try{
                JSONObject request=new JSONObject();
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                if(!snapshot.compress(Bitmap.CompressFormat.PNG,100,bytes))
                    throw new IllegalStateException("Failed to encode input image");
                request.put("file",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP));
                request.put("fileType",1);
                request.put("useDocOrientationClassify",true);
                request.put("useDocUnwarping",true);
                request.put("useTextlineOrientation",true);
                request.put("useSealRecognition",true);
                request.put("useTableRecognition",true);
                request.put("useFormulaRecognition",true);
                request.put("useChartRecognition",true);
                request.put("useRegionDetection",true);
                request.put("formatBlockContent",true);
                request.put("returnMarkdownImages",includeImages);
                request.put("visualize",false);
                if(includeDocx)request.put("outputFormats",new JSONArray().put("docx"));

                byte[] payload=request.toString().getBytes(StandardCharsets.UTF_8);
                HttpURLConnection conn=(HttpURLConnection)new URL(url(endpoint)).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20_000);
                conn.setReadTimeout(300_000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                conn.setRequestProperty("Accept","application/json");
                conn.setFixedLengthStreamingMode(payload.length);
                try{
                    try(OutputStream out=conn.getOutputStream()){out.write(payload);}
                    int status=conn.getResponseCode();
                    if(status!=200)throw new IllegalStateException("PP-StructureV3 HTTP "+status);
                    String response;
                    try(InputStream in=conn.getInputStream()){
                        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                        byte[] chunk=new byte[64*1024];
                        int n;
                        while((n=in.read(chunk))!=-1){
                            if(buffer.size()+n>MAX_RESPONSE_BYTES)
                                throw new IllegalStateException("Structured result too large");
                            buffer.write(chunk,0,n);
                        }
                        response=buffer.toString(StandardCharsets.UTF_8);
                    }
                    JSONObject body=new JSONObject(response);
                    if(body.optInt("errorCode",-1)!=0)
                        throw new IllegalStateException("Paddle service: "+
                                body.optString("errorMsg","unknown error"));
                    OcrDocument document=toDocument(body,snapshot.getWidth(),snapshot.getHeight());
                    if(document.fullText().isBlank())
                        throw new IllegalStateException("PP-StructureV3 returned no readable content");
                    File saved=saveRaw(app,response);
                    DiagnosticLog.i(app,"PP_STRUCTURE_FULL_V3",
                            "success blocks="+document.blocks().size()
                            +" lines="+document.lines().size()
                            +" layout=true table=true formula=true chart=true seal=true"
                            +" rawSaved="+(saved!=null));
                    MAIN.post(()->callback.onSuccess(document));
                }finally{conn.disconnect();}
            }catch(Throwable error){
                DiagnosticLog.i(app,"PP_STRUCTURE_FULL_V3",
                        "failed="+error.getClass().getSimpleName());
                MAIN.post(()->callback.onFailure(error));
            }finally{snapshot.recycle();}
        });
    }

    /** Parse the official result without flattening its full JSON on disk. */
    static OcrDocument toDocument(JSONObject response,int width,int height) throws Exception {
        JSONObject top=response.getJSONObject("result");
        JSONArray pages=top.getJSONArray("layoutParsingResults");
        ArrayList<String> blocks=new ArrayList<>();
        ArrayList<OcrDocument.Line> lines=new ArrayList<>();
        StringBuilder full=new StringBuilder();
        for(int i=0;i<pages.length();i++){
            JSONObject page=pages.getJSONObject(i);
            String markdown=page.optJSONObject("markdown")==null?""
                    :page.getJSONObject("markdown").optString("text","");
            JSONObject data=page.optJSONObject("prunedResult");
            if(data!=null){
                JSONArray pieces=data.optJSONArray("parsing_res_list");
                if(pieces!=null){
                    for(int j=0;j<pieces.length();j++){
                        JSONObject block=pieces.optJSONObject(j);
                        if(block==null)continue;
                        String content=block.optString("block_content","").trim();
                        if(content.isEmpty())continue;
                        blocks.add(content);
                        Rect bounds=blockRect(block.optJSONArray("block_bbox"),width,height);
                        // Line objects here represent layout BLOCK geometry.
                        // No Word or Character geometry is fabricated.
                        if(bounds!=null)lines.add(new OcrDocument.Line(content,bounds,0f,List.of()));
                    }
                }
            }
            if(markdown.isBlank()&&!blocks.isEmpty())markdown=String.join("\n\n",blocks);
            if(!markdown.isBlank()){
                if(full.length()>0)full.append("\n\n");
                full.append(markdown);
            }
        }
        return new OcrDocument(full.toString(),blocks,lines,"pp-structure-v3-full",
                0f,0d,width,height);
    }

    private static Rect blockRect(JSONArray a,int width,int height){
        if(a==null||a.length()<4)return null;
        int l=Math.max(0,Math.min(width,a.optInt(0)));
        int t=Math.max(0,Math.min(height,a.optInt(1)));
        int r=Math.max(l,Math.min(width,a.optInt(2)));
        int b=Math.max(t,Math.min(height,a.optInt(3)));
        Rect rect=new Rect(l,t,r,b);
        return rect.isEmpty()?null:rect;
    }

    private static File saveRaw(Context app,String json) {
        File dir=new File(app.getFilesDir(),"ocr_results/ppstructure_full_v3");
        try{
            if(!dir.exists()&&!dir.mkdirs())return null;
            File file=new File(dir,"latest_response.json");
            File tmp=new File(dir,"latest_response.json.part");
            try(FileOutputStream out=new FileOutputStream(tmp,false)){
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if(file.exists()&&!file.delete())return null;
            return tmp.renameTo(file)?file:null;
        }catch(Throwable ignored){return null;}
    }
    private PaddleStructureFullV3(){}
}
