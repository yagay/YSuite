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
    private PdfRenderer renderer;
    private ParcelFileDescriptor sourceFd;
    private final ArrayList<OcrDocument> results = new ArrayList<>();
    private Format format = Format.MARKDOWN;
    private String output;
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
                "Export structured text as Markdown or JSON, with source-page geometry.");
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
        YViewLayout.addSection(root, section);
        setContentView(YViewLayout.scrollPage(this, root));
    }

    private void choose(Format next) {
        if (running) {
            Toast.makeText(this, "PDF recognition is already running", Toast.LENGTH_SHORT).show();
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
        cancelled.set(false);
        results.clear();
        output=null;
        showProgress("Opening local PDF...");
        io.execute(()->{
            try{
                sourceFd=getContentResolver().openFileDescriptor(uri,"r");
                if(sourceFd==null)throw new IllegalStateException("Cannot read selected PDF");
                renderer=new PdfRenderer(sourceFd);
                int pageCount=renderer.getPageCount();
                if(pageCount==0||pageCount>500)
                    throw new IllegalArgumentException("PDF page count must be between 1 and 500");
                readNext(0);
            }catch(Throwable error){fail(error);}
        });
    }

    /** Called only on the PDF worker. Only one Bitmap is alive per page. */
    private void readNext(int pageIndex) {
        if(cancelled.get()) {closePdf();return;}
        try{
            int count=renderer.getPageCount();
            if(pageIndex>=count){
                closePdf();
                output=switch(format){
                    case MARKDOWN -> PaddleDocumentExport.markdownPages(results);
                    case JSON -> PaddleDocumentExport.jsonPages(results);
                    case HTML -> PaddleDocumentExport.htmlPages(results);
                    case CSV -> PaddleDocumentExport.csvPages(results);
                };
                running=false;
                runOnUiThread(()->{
                    if(cancelled.get())return;
                    status.setText("Finished "+results.size()+" pages. Choose where to save.");
                    switch(format){
                        case MARKDOWN -> saveMarkdown.launch("yfloat-ocr.md");
                        case JSON -> saveJson.launch("yfloat-ocr.json");
                        case HTML -> saveHtml.launch("yfloat-ocr.html");
                        case CSV -> {
                            if(output.isBlank())status.setText("No tables detected in PDF.");
                            else saveCsv.launch("yfloat-ocr-tables.csv");
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
                            io.execute(()->{
                                image.recycle();
                                if(cancelled.get()){closePdf();return;}
                                results.add(document);
                                readNext(pageIndex+1);
                            });
                        }
                        @Override public void onFailure(String message){
                            io.execute(()->{
                                image.recycle();
                                fail(new IllegalStateException("Page "+(pageIndex+1)+": "+message));
                            });
                        }
                    });
        }catch(Throwable error){fail(error);}
    }

    private void saveResult(Uri dest) {
        if(dest==null||output==null)return;
        String text=output;
        io.execute(()->{
            try(OutputStream stream=getContentResolver().openOutputStream(dest,"wt")){
                if(stream==null)throw new IllegalStateException("Cannot open selected output");
                stream.write(text.getBytes(StandardCharsets.UTF_8));
                stream.flush();
                showProgress("Saved "+results.size()+" pages successfully.");
                output=null;
            }catch(Throwable error){showProgress("Save failed: "+safe(error));}
        });
    }

    private void fail(Throwable error) {
        closePdf();
        results.clear();
        output=null;
        running=false;
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
        io.execute(this::closePdf);
        io.shutdown();
        super.onDestroy();
    }
}
