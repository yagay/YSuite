package com.yagay.YFloat

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.structure.PPDocLayoutV2
import com.paddle.ocr.structure.PPDocOrientationV3
import com.paddle.ocr.structure.SLANetLocalV3
import com.paddle.ocr.structure.UVDocLocalV3
import com.paddle.ocr.structure.PPFormulaNetLocalV3
import com.paddle.ocr.structure.PPSealDetectorLocalV3
import android.graphics.Rect
import android.graphics.PointF
import android.graphics.Matrix
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** V2 is the only active Paddle application adapter; legacy bridge is retired. */
object PaddlePipelineV2 {
    interface Callback {
        fun onSuccess(document: OcrDocument, elapsedMs: Long, lineCount: Int)
        fun onFailure(message: String)
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val lock=Mutex()
    private val models=mutableMapOf<Int,PaddleOCR>()
    private val modelSignatures=mutableMapOf<Int,String>()
    private fun signature(app:Context,model:Int):String {
        val files=listOf(OcrModelManager.detFile(app,model),
            OcrModelManager.recFile(app,model),OcrModelManager.ymlFile(app,model))
        check(OcrModelManager.isReady(app,model)){"PaddleOCR model not downloaded"}
        return files.joinToString(":") { "${it.length()}-${it.lastModified()}" }
    }
    private var orientationEngine:PPDocOrientationV3?=null
    private var orientationSignature:String?=null
    private var tableEngine:SLANetLocalV3?=null
    private var tableSignature:String?=null
    private var uvEngine:UVDocLocalV3?=null
    private var uvSignature:String?=null
    private var formulaEngine:PPFormulaNetLocalV3?=null
    private var formulaSignature:String?=null
    data class FormulaResult(val bounds:Rect,val latex:String,val tokenCount:Int)
    @Volatile private var latestFormulaResults:List<FormulaResult> = emptyList()
    @JvmStatic fun lastFormulaResults():List<FormulaResult> = latestFormulaResults
    private var sealEngine:PPSealDetectorLocalV3?=null
    private var sealSignature:String?=null
    data class SealResult(val bounds:Rect,val detections:List<PPSealDetectorLocalV3.Region>,
                          val recognizedText:String)
    @Volatile private var latestUnwarpedDocument:OcrDocument?=null
    @Volatile private var latestSealResults:List<SealResult> = emptyList()
    @JvmStatic fun lastUnwarpedDocument():OcrDocument?=latestUnwarpedDocument
    @JvmStatic fun lastSealResults():List<SealResult> = latestSealResults
    @Volatile private var lastCharts:List<PaddleChartTextV3.Chart> = emptyList()
    @JvmStatic fun latestCharts():List<PaddleChartTextV3.Chart> = lastCharts
    @Volatile private var lastTables:List<PaddleTableTextV3.Table> = emptyList()
    @JvmStatic fun latestTables():List<PaddleTableTextV3.Table> = lastTables
    private var layoutEngine:PPDocLayoutV2?=null
    private var layoutSignature:String?=null
    @Volatile private var latestPage:PaddlePageV2.Page?=null

    /** The layout ONNX runtime is owned by the same serialized inference lane. */
    private fun layout(app:Context):PPDocLayoutV2? {
        if(!PaddleStructureModelV2.enabled(app)){
            layoutEngine?.close()
            layoutEngine=null
            layoutSignature=null
            return null
        }
        val file=PaddleStructureModelV2.model(app)
        val signature="${file.length()}:${file.lastModified()}"
        if(layoutEngine!=null && layoutSignature==signature)return layoutEngine
        layoutEngine?.close()
        layoutEngine=null
        layoutSignature=null
        check(PaddleStructureModelV2.verifyIntegrity(app)) {
            "PP-DocLayout-S layout model integrity failed"
        }
        return PPDocLayoutV2(file).also {
            layoutEngine=it
            layoutSignature=signature
        }
    }
    private fun orientation(app:Context):PPDocOrientationV3? {
        if(!PaddleLocalModelsV3.enabled(app,PaddleLocalModelsV3.DOC_ORIENTATION)){
            orientationEngine?.close();orientationEngine=null;orientationSignature=null
            return null
        }
        val file=PaddleLocalModelsV3.model(app,PaddleLocalModelsV3.DOC_ORIENTATION)
        val id="${file.length()}:${file.lastModified()}"
        if(orientationEngine!=null&&orientationSignature==id)return orientationEngine
        orientationEngine?.close();orientationEngine=null;orientationSignature=null
        check(PaddleLocalModelsV3.verify(app,PaddleLocalModelsV3.DOC_ORIENTATION)){
            "Document orientation model integrity failed"
        }
        return PPDocOrientationV3(file).also{orientationEngine=it;orientationSignature=id}
    }
    private fun table(app:Context):SLANetLocalV3? {
        if(!PaddleLocalModelsV3.enabled(app,PaddleLocalModelsV3.TABLE_STRUCTURE)){
            tableEngine?.close();tableEngine=null;tableSignature=null
            return null
        }
        val file=PaddleLocalModelsV3.model(app,PaddleLocalModelsV3.TABLE_STRUCTURE)
        val dict=PaddleLocalModelsV3.vocabulary(app)
        val id="${file.length()}:${file.lastModified()}:${dict.length()}:${dict.lastModified()}"
        if(tableEngine!=null&&tableSignature==id)return tableEngine
        tableEngine?.close();tableEngine=null;tableSignature=null
        check(PaddleLocalModelsV3.verify(app,PaddleLocalModelsV3.TABLE_STRUCTURE)){
            "SLANet local model integrity failed"
        }
        return SLANetLocalV3(file,dict).also{tableEngine=it;tableSignature=id}
    }
    private fun uvdoc(app:Context):UVDocLocalV3? {
        if(!PaddleLocalModelsV3.enabled(app,PaddleLocalModelsV3.UV_DOC)){
            uvEngine?.close();uvEngine=null;uvSignature=null
            return null
        }
        val file=PaddleLocalModelsV3.model(app,PaddleLocalModelsV3.UV_DOC)
        val sig=file.length().toString()+":"+file.lastModified()
        if(uvEngine!=null&&uvSignature==sig)return uvEngine
        uvEngine?.close();uvEngine=null;uvSignature=null
        check(PaddleLocalModelsV3.verify(app,PaddleLocalModelsV3.UV_DOC)){
            "UVDoc checksum mismatch"
        }
        return UVDocLocalV3(file).also{uvEngine=it;uvSignature=sig}
    }
    private fun seal(app:Context):PPSealDetectorLocalV3? {
        if(!PaddleLocalModelsV3.enabled(app,PaddleLocalModelsV3.SEAL_DETECTION)){
            sealEngine?.close();sealEngine=null;sealSignature=null
            return null
        }
        val file=PaddleLocalModelsV3.model(app,PaddleLocalModelsV3.SEAL_DETECTION)
        val sig=file.length().toString()+":"+file.lastModified()
        if(sealEngine!=null&&sealSignature==sig)return sealEngine
        sealEngine?.close();sealEngine=null;sealSignature=null
        check(PaddleLocalModelsV3.verify(app,PaddleLocalModelsV3.SEAL_DETECTION)){
            "Seal detector checksum mismatch"
        }
        return PPSealDetectorLocalV3(file).also{sealEngine=it;sealSignature=sig}
    }
    private fun formula(app:Context):PPFormulaNetLocalV3? {
        if(!PaddleLocalModelsV3.enabled(app,PaddleLocalModelsV3.FORMULA_NET)){
            formulaEngine?.close();formulaEngine=null;formulaSignature=null
            return null
        }
        val file=PaddleLocalModelsV3.model(app,PaddleLocalModelsV3.FORMULA_NET)
        val dict=PaddleLocalModelsV3.formulaTokenizer(app)
        val sig="${file.length()}:${file.lastModified()}:${dict.length()}:${dict.lastModified()}"
        if(formulaEngine!=null&&formulaSignature==sig)return formulaEngine
        formulaEngine?.close();formulaEngine=null;formulaSignature=null
        check(PaddleLocalModelsV3.verify(app,PaddleLocalModelsV3.FORMULA_NET)){
            "FormulaNet ONNX or tokenizer integrity failed"
        }
        return PPFormulaNetLocalV3(file,dict).also{
            formulaEngine=it;formulaSignature=sig
        }
    }
    @JvmStatic fun lastPage():PaddlePageV2.Page?=latestPage

    @JvmStatic fun recognize(context:Context,image:Bitmap,model:Int,callback:Callback){
        val app=context.applicationContext
        scope.launch {
            try {
                require(!image.isRecycled && image.width>0 && image.height>0)
                val started=System.currentTimeMillis()
                val (page,linkedTables)=lock.withLock {
                    val revision=signature(app,model)
                    if(models.containsKey(model) && modelSignatures[model]!=revision){
                        models.remove(model)?.release()
                        modelSignatures.remove(model)
                        DiagnosticLog.i(app,"PADDLE_V2","model_replaced="+model)
                    }
                    val ocr=models[model] ?: run {
                        check(OcrModelManager.verifyIntegrity(app,model)){"PaddleOCR model integrity failed"}
                        check(OpenCVUtils.init(app)){"OpenCV initialization failed"}
                        val cfg=PaddleOCRConfig(
                            detThresh=.20f,detBoxThresh=.45f,detUnclipRatio=1.4f,
                            recScoreThresh=0f,recBatchSize=if(model==OcrModelManager.MEDIUM)2 else 4)
                        PaddleOCR.create(app,cfg,EngineConfig(numThreads=4),
                            OcrModelManager.detFile(app,model).absolutePath,
                            OcrModelManager.recFile(app,model).absolutePath,
                            OcrModelManager.ymlFile(app,model).absolutePath).also{
                                models[model]=it
                                modelSignatures[model]=revision
                            }
                    }
                    val verifyOrientation=app.getSharedPreferences(FloatSettings.PREF,
                        Context.MODE_PRIVATE).getBoolean("paddle_v2_auto_orientation",false)
                    val classifier=try{orientation(app)}catch(error:Throwable){
                        DiagnosticLog.i(app,"PADDLE_V3","orientation_error="+error.javaClass.simpleName)
                        null
                    }
                    val prediction=classifier?.classify(image)
                    val chosen=if(prediction!=null && prediction.confidence>=.85f
                        && prediction.degrees!=0){
                        val degrees=prediction.degrees
                        val matrix=Matrix().apply{postRotate(degrees.toFloat())}
                        val rotated=Bitmap.createBitmap(image,0,0,image.width,image.height,matrix,true)
                        try{
                            val results=ocr.recognize(rotated).results.map{
                                PaddleOrientationV2.backToImage(it,degrees,image.width,image.height)
                            }
                            PaddleOrientationV2.Choice(results,degrees,prediction.confidence.toDouble())
                        }finally{if(rotated!==image)rotated.recycle()}
                    }else{
                        PaddleOrientationV2.recognize(ocr,image,verifyOrientation&&classifier==null)
                    }
                    val structure=try {
                        layout(app)?.detect(image) ?: emptyList()
                    }catch(t:Throwable){
                        DiagnosticLog.i(app,"PP_STRUCTURE_V2",
                            "layout_failed="+t.javaClass.simpleName+
                            " detail="+(t.message ?: "unknown"))
                        emptyList()
                    }
                    val localTable=try{table(app)}catch(error:Throwable){
                        DiagnosticLog.i(app,"PADDLE_V3","table_model_error="+error.javaClass.simpleName)
                        null
                    }
                    val tables=ArrayList<SLANetLocalV3.Result>()
                    if(localTable!=null)for(region in structure.filter{it.label=="table"}){
                        val rect=region.bounds
                        if(rect.width()<16||rect.height()<16)continue
                        val crop=Bitmap.createBitmap(image,rect.left,rect.top,rect.width(),rect.height())
                        try{
                            val predicted=localTable.detect(crop)
                            // Cell boxes are crop-local; shift them to original bitmap space.
                            val translated=predicted.copy(cells=predicted.cells.map {cell->
                                val box=android.graphics.Rect(cell.bounds)
                                box.offset(rect.left,rect.top)
                                cell.copy(bounds=box)
                            })
                            tables.add(translated)
                        }catch(error:Throwable){
                            DiagnosticLog.i(app,"PADDLE_V3","table_region_error="+error.javaClass.simpleName)
                        }finally{if(crop!==image)crop.recycle()}
                    }
                    DiagnosticLog.i(app,"PADDLE_V3","table_regions="+tables.size+
                        " table_cells="+tables.sumOf{it.cells.size})
                    DiagnosticLog.i(app,"PADDLE_V2",
                        "ocr_lines="+chosen.results.size+" orientation="+chosen.degrees+
                        " orientationCheck="+verifyOrientation+" layout_regions="+structure.size+
                        " layoutEnabled="+PaddleStructureModelV2.enabled(app))
                    val spacing=app.getSharedPreferences(FloatSettings.PREF,
                        Context.MODE_PRIVATE).getBoolean("paddle_v2_spacing",true)
                    val page=PaddlePageV2.fromOcr(chosen.results,image.width,image.height,
                        structure,spacing)
                    val linked=tables.map{PaddleTableTextV3.link(it,page.lines)}
                    lastCharts=PaddleChartTextV3.extract(page)
                    // UVDoc is a separate document-OCR result. Its coordinates
                    // refer to its rectified bitmap, not the source screenshot.
                    // Never send those quads to the screen selection overlay.
                    latestUnwarpedDocument=null
                    try {
                        val engine=uvdoc(app)
                        if(engine!=null){
                            val corrected=engine.rectify(image)
                            try{
                                val textResults=ocr.recognize(corrected).results
                                val correctedPage=PaddlePageV2.fromOcr(textResults,
                                    corrected.width,corrected.height,emptyList(),spacing)
                                latestUnwarpedDocument=correctedPage.asLegacy(
                                    "ppocr-v2-uvdoc-"+model)
                            }finally{corrected.recycle()}
                        }
                    }catch(error:Throwable){
                        DiagnosticLog.i(app,"PADDLE_UVDOC",
                            "unwarp_failed="+error.javaClass.simpleName)
                    }
                    val seals=ArrayList<SealResult>()
                    try{
                        val detector=seal(app)
                        if(detector!=null)for(region in structure.filter{it.label=="seal"}){
                            val r=region.bounds
                            if(r.width()<16||r.height()<16)continue
                            val crop=Bitmap.createBitmap(image,r.left,r.top,r.width(),r.height())
                            try{
                                val polygons=detector.detect(crop).map {region->
                                    region.copy(polygon=region.polygon.map{point->
                                        PointF(point.x+r.left,point.y+r.top)
                                    })
                                }
                                val text=ocr.recognize(crop).results
                                    .joinToString(" "){it.text.trim()}.trim()
                                seals.add(SealResult(Rect(r),polygons,text))
                            }finally{crop.recycle()}
                        }
                    }catch(error:Throwable){
                        DiagnosticLog.i(app,"PADDLE_SEAL",
                            "seal_failed="+error.javaClass.simpleName)
                    }
                    latestSealResults=seals
                    // PP-FormulaNet+S is a distinct optional local recognizer.
                    // Its LaTeX belongs to the model's layout bounding region;
                    // ordinary CTC glyph selection remains untouched.
                    val formulas=ArrayList<FormulaResult>()
                    try {
                        val parser=formula(app)
                        if(parser!=null) {
                            for(region in structure.filter{it.label=="formula"}.take(24)) {
                                val box=region.bounds
                                if(box.width()<8||box.height()<8)continue
                                val crop=Bitmap.createBitmap(image,box.left,box.top,
                                    box.width(),box.height())
                                try {
                                    val output=parser.recognize(crop)
                                    formulas.add(FormulaResult(Rect(box),output.latex,
                                        output.tokenCount))
                                }catch(error:Throwable){
                                    DiagnosticLog.i(app,"PADDLE_FORMULA",
                                        "formula_region_failed="+error.javaClass.simpleName)
                                }finally{crop.recycle()}
                            }
                        }
                    }catch(error:Throwable){
                        DiagnosticLog.i(app,"PADDLE_FORMULA",
                            "formula_model_failed="+error.javaClass.simpleName)
                    }
                    latestFormulaResults=formulas
                    DiagnosticLog.i(app,"PADDLE_LOCAL",
                        "formula_regions="+formulas.size)
                    DiagnosticLog.i(app,"PADDLE_LOCAL",
                        "uvdoc_text="+(latestUnwarpedDocument?.fullText()?.length?:0)+
                        " seal_regions="+seals.size)
                    val fixes=page.lines.count {it.rawText!=it.text}
                    DiagnosticLog.i(app,"PADDLE_V2",
                        "spacing_enabled="+spacing+" corrected_lines="+fixes)
                    page to linked
                }
                latestPage=page
                lastTables=linkedTables
                val compat=page.asLegacy("ppocr-v2-"+model)
                    .withTableTexts(linkedTables.map{it.tabSeparated}.filter{it.isNotBlank()})
                    .withSealTexts(latestSealResults.map{it.recognizedText}
                        .filter{it.isNotBlank()})
                    .withUnwarpedText(latestUnwarpedDocument?.fullText().orEmpty())
                    .withFormulaTexts(latestFormulaResults.map{it.latex})
                    .withChartTexts(lastCharts.map{it.searchableText}.filter{it.isNotBlank()})
                withContext(Dispatchers.Main){
                    callback.onSuccess(compat,System.currentTimeMillis()-started,
                        compat.lines().size)
                }
            }catch(t:Throwable){
                withContext(Dispatchers.Main) {
                    callback.onFailure(t.javaClass.simpleName+": "+(t.message ?: "unknown"))
                }
            }
        }
    }
    @JvmStatic fun releaseModel(model:Int){
        scope.launch {
            lock.withLock {
                models.remove(model)?.release()
                modelSignatures.remove(model)
                // Also drop layout session when model management invalidates OCR.
                layoutEngine?.close()
                layoutEngine=null
                layoutSignature=null
                orientationEngine?.close();orientationEngine=null;orientationSignature=null
                tableEngine?.close();tableEngine=null;tableSignature=null
                uvEngine?.close();uvEngine=null;uvSignature=null
                sealEngine?.close();sealEngine=null;sealSignature=null
                formulaEngine?.close();formulaEngine=null;formulaSignature=null
                latestFormulaResults=emptyList()
                lastCharts=emptyList()
                latestUnwarpedDocument=null
                latestSealResults=emptyList()
                lastTables=emptyList()
            }
        }
    }
}
