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
    // Inference and release remain mutex-serialized. Concurrent maps make
    // lightweight read-only loaded-status queries safe from UI threads.
    private val models=java.util.concurrent.ConcurrentHashMap<Int,PaddleOCR>()
    private val modelSignatures=mutableMapOf<Int,String>()
    private fun signature(app:Context,model:Int):String {
        val files=listOf(OcrModelManager.detFile(app,model),
            OcrModelManager.recFile(app,model),OcrModelManager.ymlFile(app,model))
        check(OcrModelManager.isReady(app,model)){"PaddleOCR model not downloaded"}
        val lineEnabled=PaddleLocalModelsV3.enabled(app,
            PaddleLocalModelsV3.TEXTLINE_ORIENTATION)
        val line=if(lineEnabled)PaddleLocalModelsV3.model(app,
            PaddleLocalModelsV3.TEXTLINE_ORIENTATION) else null
        return files.joinToString(":") { "${it.length()}-${it.lastModified()}" }+
            ":line="+(line?.let{"${it.length()}-${it.lastModified()}"} ?: "off")
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
    @JvmStatic fun isLoaded(model:Int):Boolean=models.containsKey(model)

    /**
     * Structure-model rectangles are not trusted to lie inside the original
     * bitmap. Clamp all optional region crops before invoking native engines.
     * The returned rectangle is also used for coordinate re-mapping.
     */
    private fun safeCropRect(bounds:Rect,image:Bitmap,minSide:Int):Rect? {
        val clipped=Rect(bounds)
        if(!clipped.intersect(0,0,image.width,image.height))return null
        return clipped.takeIf{it.width()>=minSide&&it.height()>=minSide}
    }

    @JvmStatic fun recognize(context:Context,image:Bitmap,model:Int,callback:Callback){
        val app=context.applicationContext
        scope.launch {
            try {
                require(!image.isRecycled && image.width>0 && image.height>0)
                val started=System.currentTimeMillis()
                // Build the entire document inside the inference lock, so a subsequent
                // request cannot overwrite its auxiliary data before callback delivery.
                val compat=lock.withLock {
                    val revision=signature(app,model)
                    if(models.containsKey(model) && modelSignatures[model]!=revision){
                        models.remove(model)?.release()
                        modelSignatures.remove(model)
                        DiagnosticLog.i(app,"PADDLE_V2","model_replaced="+model)
                    }
                    val ocr=models[model] ?: run {
                        check(OcrModelManager.verifyIntegrity(app,model)){"PaddleOCR model integrity failed"}
                        check(OpenCVUtils.init(app)){"OpenCV initialization failed"}
                        val plan=PaddleRuntimePolicy.forModel(app,model)
                        val cfg=PaddleOCRConfig(
                            detThresh=.20f,detBoxThresh=.45f,detUnclipRatio=1.4f,
                            recScoreThresh=0f,recBatchSize=plan.recognitionBatch)
                        DiagnosticLog.i(app,"PADDLE_RUNTIME",
                            "model="+model+" threads="+plan.threads+
                            " batch="+plan.recognitionBatch+" lowMemory="+plan.lowMemory)
                        val lineModel=if(PaddleLocalModelsV3.enabled(app,
                            PaddleLocalModelsV3.TEXTLINE_ORIENTATION)){
                            check(PaddleLocalModelsV3.verify(app,
                                PaddleLocalModelsV3.TEXTLINE_ORIENTATION)){
                                "Textline orientation ONNX integrity failed"
                            }
                            PaddleLocalModelsV3.model(app,
                                PaddleLocalModelsV3.TEXTLINE_ORIENTATION).absolutePath
                        }else null
                        PaddleOCR.create(app,cfg,EngineConfig(numThreads=plan.threads),
                            OcrModelManager.detFile(app,model).absolutePath,
                            OcrModelManager.recFile(app,model).absolutePath,
                            OcrModelManager.ymlFile(app,model).absolutePath,
                            lineModel).also{
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
                    // Native SLANet predictions are preferred. A conservative
                    // image-grid detector covers high-confidence ruled tables
                    // when SLANet is unavailable or produced no cells. It is
                    // explicitly labelled pixel_ruled_grid, not PP-TableMagic.
                    val tables=ArrayList<Pair<SLANetLocalV3.Result,String>>()
                    val regions=structure.filter{it.label=="table"}
                    val candidates=if(regions.isNotEmpty())
                        regions.map{it.bounds to true}
                    else listOf(Rect(0,0,image.width,image.height) to false)
                    for((box,hasLayout) in candidates.take(12)){
                        val rect=safeCropRect(box,image,32) ?: continue
                        val crop=if(rect.left==0&&rect.top==0&&
                            rect.width()==image.width&&rect.height()==image.height)image
                            else Bitmap.createBitmap(image,rect.left,rect.top,
                                rect.width(),rect.height())
                        try{
                            val grid=PaddleRuledGridV4.detect(crop)
                            var learned:SLANetLocalV3.Result?=null
                            if(localTable!=null && (hasLayout || grid!=null)){
                                try{learned=localTable.detect(crop)}
                                catch(error:Throwable){
                                    DiagnosticLog.i(app,"PADDLE_TABLE",
                                        "slanet_error="+error.javaClass.simpleName)
                                }
                            }
                            val choice=when{
                                learned!=null&&learned.cells.isNotEmpty() ->
                                    learned to "slanet_model"
                                grid!=null -> grid.prediction to grid.source
                                else -> null
                            } ?: continue
                            val translated=choice.first.copy(
                                cells=choice.first.cells.map{cell->
                                    val shifted=Rect(cell.bounds)
                                    shifted.offset(rect.left,rect.top)
                                    cell.copy(bounds=shifted)
                                })
                            tables.add(translated to choice.second)
                        }catch(error:Throwable){
                            DiagnosticLog.i(app,"PADDLE_TABLE",
                                "grid_error="+error.javaClass.simpleName)
                        }finally{if(crop!==image)crop.recycle()}
                    }
                    DiagnosticLog.i(app,"PADDLE_V3","table_regions="+tables.size+
                        " table_cells="+tables.sumOf{it.first.cells.size})
                    DiagnosticLog.i(app,"PADDLE_V2",
                        "ocr_lines="+chosen.results.size+" orientation="+chosen.degrees+
                        " orientationCheck="+verifyOrientation+" layout_regions="+structure.size+
                        " layoutEnabled="+PaddleStructureModelV2.enabled(app))
                    val spacing=app.getSharedPreferences(FloatSettings.PREF,
                        Context.MODE_PRIVATE).getBoolean("paddle_v2_spacing",true)
                    val page=PaddlePageV2.fromOcr(chosen.results,image.width,image.height,
                        structure,spacing)
                    val linked=tables.map{PaddleTableTextV3.link(it.first,page.lines,it.second)}
                    DiagnosticLog.i(app,"PADDLE_TABLE",
                        "sources="+linked.joinToString(","){it.source})
                    val charts=PaddleChartTextV3.extract(page)
                    lastCharts=charts
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
                                latestUnwarpedDocument=correctedPage.toDocument(
                                    "ppocr-v2-uvdoc-"+model).asRectifiedBitmapSpace()
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
                            val r=safeCropRect(region.bounds,image,16) ?: continue
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
                                val box=safeCropRect(region.bounds,image,8) ?: continue
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
                    // Counts only: never log the user's recognized text.
                    // This separates a CTC-emitted space from an overlay-added
                    // space and reveals when normalization did no work.
                    fun blanks(value:String)=value.count{ c ->
                        c==' ' || c=='\u00a0' || c=='\u3000' || c=='\u202f'
                    }
                    val beforeSpaces=page.lines.sumOf{blanks(it.rawText)}
                    val afterSpaces=page.lines.sumOf{blanks(it.text)}
                    DiagnosticLog.i(app,"PADDLE_SPACE_TRACE",
                        "stage=ocr model="+model+
                        " rawSpaces="+beforeSpaces+
                        " normalizedSpaces="+afterSpaces+
                        " delta="+(afterSpaces-beforeSpaces)+
                        " correctedLines="+fixes+
                        " lines="+page.lines.size)
                    DiagnosticLog.i(app,"PADDLE_V2",
                        "spacing_enabled="+spacing+" corrected_lines="+fixes)
                    latestPage=page
                    lastTables=linked
                    page.toDocument("ppocr-v2-"+model)
                        .withTableTexts(linked.map{it.tabSeparated})
                        .withTableHtmls(linked.map{it.html})
                        .withTableOrigins(linked.map{it.source})
                        .withSealTexts(seals.map{it.recognizedText}.filter{it.isNotBlank()})
                        .withUnwarpedText(latestUnwarpedDocument?.fullText().orEmpty())
                        .withRectifiedDocument(latestUnwarpedDocument)
                        .withFormulaTexts(formulas.map{it.latex})
                        .withChartTexts(charts.map{it.searchableText}.filter{it.isNotBlank()})
                }
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
                latestPage=null
            }
        }
    }
}
