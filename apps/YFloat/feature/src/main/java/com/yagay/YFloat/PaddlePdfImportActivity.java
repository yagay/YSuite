package com.yagay.YFloat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local multi-page PDF -> Paddle OCR -> SAF Markdown/JSON export.
 * Renders one page at a time on a worker, executes OCR without any HTTP API,
 * and never holds a full PDF worth of rendered Bitmaps in RAM.
 */
public final class PaddlePdfImportActivity extends AppCompatActivity {
    private enum Format { MARKDOWN, JSON, HTML, CSV }
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "YFloat-Paddle-PDF");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private ActivityResultLauncher<String[]> openPicker;
    private ActivityResultLauncher<String> saveMarkdown;
    private ActivityResultLauncher<String> saveJson;
    private ActivityResultLauncher<String> saveHtml;
    private ActivityResultLauncher<String> saveCsv;
    private TextView status;
    private MaterialButton cancelButton;
    private PdfRenderer renderer;
    private ParcelFileDescriptor sourceFd;
    private File stagedExport;
    private BufferedWriter exportWriter;
    private int totalPages;
    private int completedPages;
    private boolean hasTables;
    private Format format = Format.MARKDOWN;
    private int model;
    private boolean running;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        openPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) startDocument(uri); });
        saveMarkdown = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/markdown"),
                uri -> saveResult(uri));
        saveJson = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> saveResult(uri));
        saveHtml = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/html"),
                uri -> saveResult(uri));
        saveCsv = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/csv"),
                uri -> saveResult(uri));

        LinearLayout root = YViewLayout.pageRoot(this,
                "Paddle OCR - Local PDF",
                "Pages are processed offline in order. No PDF is uploaded.");
        YViewSection section = YViewLayout.section(this, "Document OCR",
                "All PDF pages are recognized locally; export Markdown, JSON, HTML or table CSV.");
        status = YViewLayout.rowSubtitle(this, "Select a PDF. Install a Paddle OCR model first.");
        YViewLayout.addRow(section.body, status);

        LinearLayout actions = YViewLayout.buttonRow(this);
        MaterialButton md = YViewLayout.compactButton(this, "PDF to Markdown");
        MaterialButton json = YViewLayout.compactButton(this, "PDF to JSON");
        md.setOnClickListener(v -> choose(Format.MARKDOWN));
        json.setOnClickListener(v -> choose(Format.JSON));
        YViewLayout.addAction(actions, md);
        YViewLayout.addAction(actions, json);
        YViewLayout.addRow(section.body, actions);
        LinearLayout more=YViewLayout.buttonRow(this);
        MaterialButton html=YViewLayout.compactButton(this,"PDF to HTML");
        MaterialButton csv=YViewLayout.compactButton(this,"PDF tables to CSV");
        html.setOnClickListener(v->choose(Format.HTML));
        csv.setOnClickListener(v->choose(Format.CSV));
        YViewLayout.addAction(more,html);
        YViewLayout.addAction(more,csv);
        YViewLayout.addRow(section.body,more);
        cancelButton=YViewLayout.compactButton(this,
                java.util.Locale.getDefault().getLanguage().startsWith("zh")?
                    "取消识别":"Cancel recognition");
        cancelButton.setEnabled(false);
        cancelButton.setOnClickListener(v->{
            cancelled.set(true);
            cancelButton.setEnabled(false);
            showProgress("Cancellation requested. Finishing current inference...");
        });
        LinearLayout stop=YViewLayout.buttonRow(this);
        YViewLayout.addAction(stop,cancelButton);
        MaterialButton discard=YViewLayout.compactButton(this,"Discard saved OCR draft");
        discard.setOnClickListener(v->{
            if(running)return;
            io.execute(()->{
                discardExport();
                showProgress("Temporary OCR result discarded.");
            });
        });
        YViewLayout.addAction(stop,discard);
        YViewLayout.addRow(section.body,stop);
        YViewLayout.addSection(root, section);
        setContentView(YViewLayout.scrollPage(this, root));
    }

    private void choose(Format next) {
        if (running || stagedExport!=null) {
            Toast.makeText(this, "Save or discard the previous PDF result first",
                    Toast.LENGTH_LONG).show();
            return;
        }
        int[] preferred={OcrModelManager.SMALL,OcrModelManager.MEDIUM,
                OcrModelManager.MOBILE,OcrModelManager.TINY};
        int found=0;
        for(int candidate:preferred) {
            if(OcrModelManager.isReady(this,candidate)){found=candidate;break;}
        }
        if(found==0) {
            status.setText("Install a local Paddle OCR model in YFloat OCR settings first.");
            return;
        }
        model=found;
        format=next;
        openPicker.launch(new String[]{"application/pdf"});
    }

    private void startDocument(Uri uri) {
        if(running||uri==null)return;
        running=true;
        cancelButton.setEnabled(true);
        cancelled.set(false);
        completedPages=0;
        totalPages=0;
        hasTables=false;
        showProgress("Opening local PDF...");
        io.execute(()->{
            try{
                sourceFd=getContentResolver().openFileDescriptor(uri,"r");
                if(sourceFd==null)throw new IllegalStateException("Cannot read selected PDF");
                renderer=new PdfRenderer(sourceFd);
                int pageCount=renderer.getPageCount();
                if(pageCount==0||pageCount>500)
                    throw new IllegalArgumentException("PDF page count must be between 1 and 500");
                totalPages=pageCount;
                stagedExport=File.createTempFile("yfloat-ocr-", ".partial",getCacheDir());
                exportWriter=new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(stagedExport),StandardCharsets.UTF_8),32768);
                beginExport();
                readNext(0);
            }catch(Throwable error){fail(error);}
        });
    }

    /** Called only on the PDF worker. Only one Bitmap is alive per page. */
    private void readNext(int pageIndex) {
        if(cancelled.get()) {
            closePdf();
            discardExport();
            running=false;
            runOnUiThread(()->{
                cancelButton.setEnabled(false);
                status.setText("PDF recognition cancelled.");
            });
            return;
        }
        try{
            int count=renderer.getPageCount();
            if(pageIndex>=count){
                closePdf();
                finishExport();
                running=false;
                runOnUiThread(()->{
                    cancelButton.setEnabled(false);
                    if(cancelled.get())return;
                    status.setText("Finished "+completedPages+" pages. Save the offline result.");
                    switch(format){
                        case MARKDOWN -> saveMarkdown.launch("yfloat-ocr.md");
                        case JSON -> saveJson.launch("yfloat-ocr.json");
                        case HTML -> saveHtml.launch("yfloat-ocr.html");
                        case CSV -> {
                            if(!hasTables){
                                status.setText("No tables detected in PDF.");
                                io.execute(this::discardExport);
                            }else saveCsv.launch("yfloat-ocr-tables.csv");
                        }
                    }
                });
                return;
            }
            Bitmap image;
            try(PdfRenderer.Page page=renderer.openPage(pageIndex)) {
                int sourceWidth=page.getWidth(),sourceHeight=page.getHeight();
                float scale=Math.min(1.5f,2048f/Math.max(sourceWidth,sourceHeight));
                int width=Math.max(1,Math.round(sourceWidth*scale));
                int height=Math.max(1,Math.round(sourceHeight*scale));
                image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                new Canvas(image).drawColor(Color.WHITE);
                page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            }
            showProgress("Recognizing page "+(pageIndex+1)+" / "+count);
            PaddlePipelineV2.recognize(getApplicationContext(),image,model,
                    new PaddlePipelineV2.Callback(){
                        @Override public void onSuccess(OcrDocument document,long ms,int lines){
                            if(cancelled.get()){
                                image.recycle();
                                try{io.execute(()->readNext(pageIndex+1));}
                                catch(java.util.concurrent.RejectedExecutionException ignored){}
                                return;
                            }
                            try{
                                io.execute(()->{
                                    image.recycle();
                                    if(cancelled.get()){readNext(pageIndex+1);return;}
                                    try{
                                        appendPage(pageIndex,document);
                                        readNext(pageIndex+1);
                                    }catch(Throwable error){fail(error);}
                                });
                            }catch(java.util.concurrent.RejectedExecutionException ignored){
                                image.recycle();
                            }
                        }
                        @Override public void onFailure(String message){
                            if(cancelled.get()){
                                image.recycle();
                                try{io.execute(()->readNext(pageIndex+1));}
                                catch(java.util.concurrent.RejectedExecutionException ignored){}
                                return;
                            }
                            try{
                                io.execute(()->{
                                    image.recycle();
                                    fail(new IllegalStateException("Page "+(pageIndex+1)+": "+message));
                                });
                            }catch(java.util.concurrent.RejectedExecutionException ignored){
                                image.recycle();
                            }
                        }
                    });
        }catch(Throwable error){fail(error);}
    }

    private void beginExport()throws Exception{
        if(exportWriter==null)throw new IllegalStateException("No output stream");
        if(format==Format.JSON)
            exportWriter.write("{\"schema\":\"yfloat.paddle.document.v1\",\"pageCount\":"+
                    totalPages+",\"pages\":[");
        if(format==Format.HTML)
            exportWriter.write("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"+
                    "<title>YFloat OCR</title></head><body>");
    }

    /** Serialize one page then discard all its OCR objects before next page. */
    private void appendPage(int index,OcrDocument page)throws Exception{
        if(exportWriter==null)throw new IllegalStateException("PDF output was closed");
        switch(format){
            case MARKDOWN -> {
                if(index!=0)exportWriter.write("\n---\n\n");
                exportWriter.write("<!-- Page "+(index+1)+" -->\n\n");
                exportWriter.write(PaddleDocumentExport.markdown(page));
            }
            case JSON -> {
                if(index!=0)exportWriter.write(",");
                org.json.JSONObject wrapper=
                        new org.json.JSONObject(PaddleDocumentExport.json(page));
                org.json.JSONObject entry=wrapper.getJSONArray("pages").getJSONObject(0);
                entry.put("pageIndex",index);
                exportWriter.write(entry.toString());
            }
            case HTML -> {
                String html=PaddleDocumentExport.html(page);
                int start=html.indexOf("<body>")+6;
                int end=html.lastIndexOf("</body>");
                if(start<6||end<start)throw new IllegalStateException("HTML body not found");
                exportWriter.write("<section data-page=\""+(index+1)+"\">");
                exportWriter.write(html.substring(start,end));
                exportWriter.write("</section>");
            }
            case CSV -> {
                String tables=PaddleDocumentExport.csvTables(page);
                if(!tables.isBlank()){
                    if(hasTables)exportWriter.write("\r\n");
                    exportWriter.write(tables);
                    hasTables=true;
                }
            }
        }
        completedPages++;
        exportWriter.flush();
    }

    private void finishExport()throws Exception{
        if(exportWriter==null)throw new IllegalStateException("Missing output writer");
        if(format==Format.JSON)exportWriter.write("]}");
        if(format==Format.HTML)exportWriter.write("</body></html>");
        exportWriter.close();
        exportWriter=null;
    }

    private void saveResult(Uri dest) {
        if(dest==null){
            showProgress("Save cancelled. The offline result remains available.");
            return;
        }
        io.execute(()->{
            File ready=stagedExport;
            if(ready==null||!ready.isFile()){
                showProgress("No recognized PDF available to export.");
                return;
            }
            try(FileInputStream input=new FileInputStream(ready);
                OutputStream out=getContentResolver().openOutputStream(dest,"wt")){
                if(out==null)throw new IllegalStateException("Document provider unavailable");
                byte[] bytes=new byte[65536];int n;
                while((n=input.read(bytes))!=-1)out.write(bytes,0,n);
                out.flush();
                discardExport();
                showProgress("Saved "+completedPages+" pages.");
            }catch(Throwable error){
                showProgress("Save failed: "+safe(error));
            }
        });
    }

    private void discardExport(){
        if(exportWriter!=null){
            try{exportWriter.close();}catch(Throwable ignored){}
            exportWriter=null;
        }
        if(stagedExport!=null){
            if(stagedExport.exists())stagedExport.delete();
            stagedExport=null;
        }
    }

    private void fail(Throwable error) {
        closePdf();
        discardExport();
        running=false;
        runOnUiThread(()->cancelButton.setEnabled(false));
        showProgress("PDF OCR failed: "+safe(error));
    }

    private void closePdf() {
        if(renderer!=null) {
            try{renderer.close();}catch(Throwable ignored){}
            renderer=null;
        }
        if(sourceFd!=null) {
            try{sourceFd.close();}catch(Throwable ignored){}
            sourceFd=null;
        }
    }

    private void showProgress(String message) {
        runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&status!=null)status.setText(message);});
    }

    private static String safe(Throwable error) {
        return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
    }

    @Override public void onDestroy() {
        cancelled.set(true);
        io.execute(()->{closePdf();discardExport();});
        io.shutdown();
        super.onDestroy();
    }
}
