package com.yagay.YFloat;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Engine-neutral OCR/text geometry shared by YFloat surfaces.
 *
 * Recognition engines produce BITMAP-space documents. Higher-level coordinators may normalize a
 * document once into absolute SCREEN space before mixing it with Accessibility/View geometry.
 */
public final class OcrDocument {
    public enum CoordinateSpace { BITMAP, SCREEN, RECTIFIED_BITMAP }

    public static final class CharUnit {
        private final String text;
        private final Rect bounds;
        private final float confidence;
        private final int line;
        private final int group;
        private final int order;

        public CharUnit(String text, Rect bounds, float confidence, int line, int group, int order) {
            this.text = text == null ? "" : text;
            this.bounds = bounds == null ? new Rect() : new Rect(bounds);
            this.confidence = confidence;
            this.line = line;
            this.group = group;
            this.order = order;
        }

        public String text() { return text; }
        public Rect bounds() { return new Rect(bounds); }
        public float confidence() { return confidence; }
        public int line() { return line; }
        public int group() { return group; }
        public int order() { return order; }

        CharUnit translated(int dx, int dy, int newLine, int newOrder) {
            Rect r = new Rect(bounds);
            r.offset(dx, dy);
            return new CharUnit(text, r, confidence, newLine, group, newOrder);
        }
    }

    public static final class Line {
        private final String text;
        private final Rect bounds;
        private final float confidence;
        private final List<CharUnit> chars;

        public Line(String text, Rect bounds, float confidence, List<CharUnit> chars) {
            this.text = text == null ? "" : text;
            this.bounds = bounds == null ? new Rect() : new Rect(bounds);
            this.confidence = confidence;
            this.chars = chars == null ? List.of() : List.copyOf(chars);
        }

        public String text() { return text; }
        public Rect bounds() { return new Rect(bounds); }
        public float confidence() { return confidence; }
        public List<CharUnit> chars() { return chars; }
    }

    private final String fullText;
    private final List<String> blocks;
    private final List<Line> lines;
    private final String engine;
    private final float confidence;
    private final double score;
    private final int imageWidth;
    private final int imageHeight;
    private final CoordinateSpace coordinateSpace;
    private final OcrStructure structure;
    private final List<String> tableTexts;
    private final List<String> tableHtmls;
    private final List<String> tableOrigins;
    private final List<String> sealTexts;
    private final List<String> formulaTexts;
    private final List<String> chartTexts;
    private final String unwarpedText;
    private final OcrDocument rectifiedDocument;

    public OcrDocument(String fullText, List<String> blocks, List<Line> lines,
                       String engine, float confidence, double score,
                       int imageWidth, int imageHeight) {
        this(fullText, blocks, lines, engine, confidence, score,
                imageWidth, imageHeight, CoordinateSpace.BITMAP);
    }

    public OcrDocument(String fullText, List<String> blocks, List<Line> lines,
                       String engine, float confidence, double score,
                       int imageWidth, int imageHeight, CoordinateSpace coordinateSpace) {
        this(fullText,blocks,lines,engine,confidence,score,
                imageWidth,imageHeight,coordinateSpace,null,null,null,null,null,null,"",null,null);
    }

    private OcrDocument(String fullText,List<String> blocks,List<Line> lines,
                        String engine,float confidence,double score,
                        int imageWidth,int imageHeight,CoordinateSpace coordinateSpace,
                        OcrStructure structure,List<String> tableTexts,List<String> tableHtmls,
                        List<String> sealTexts,List<String> formulaTexts,List<String> chartTexts,
                        String unwarpedText,List<String> tableOrigins,OcrDocument rectifiedDocument) {
        this.fullText = fullText == null ? "" : fullText.trim();
        this.blocks = blocks == null ? List.of() : List.copyOf(blocks);
        this.lines = lines == null ? List.of() : List.copyOf(lines);
        this.engine = engine == null ? "unknown" : engine;
        this.confidence = confidence;
        this.score = score;
        this.imageWidth = Math.max(1, imageWidth);
        this.imageHeight = Math.max(1, imageHeight);
        this.coordinateSpace = coordinateSpace == null ? CoordinateSpace.BITMAP : coordinateSpace;
        this.structure=structure;
        this.tableTexts=tableTexts==null?List.of():List.copyOf(tableTexts);
        this.tableHtmls=tableHtmls==null?List.of():List.copyOf(tableHtmls);
        this.tableOrigins=tableOrigins==null?List.of():List.copyOf(tableOrigins);
        this.sealTexts=sealTexts==null?List.of():List.copyOf(sealTexts);
        this.formulaTexts=formulaTexts==null?List.of():List.copyOf(formulaTexts);
        this.chartTexts=chartTexts==null?List.of():List.copyOf(chartTexts);
        this.unwarpedText=unwarpedText==null?"":unwarpedText;
        this.rectifiedDocument=rectifiedDocument;
    }

    public static OcrDocument screenSpace(String fullText, List<String> blocks, List<Line> lines,
                                          String engine, float confidence, double score,
                                          int screenWidth, int screenHeight) {
        return new OcrDocument(fullText, blocks, lines, engine, confidence, score,
                screenWidth, screenHeight, CoordinateSpace.SCREEN);
    }

    public String fullText() { return fullText; }
    public List<String> blocks() { return blocks; }
    public List<Line> lines() { return lines; }
    public String engine() { return engine; }
    public float confidence() { return confidence; }
    public double score() { return score; }
    public int imageWidth() { return imageWidth; }
    public int imageHeight() { return imageHeight; }
    public CoordinateSpace coordinateSpace() { return coordinateSpace; }
    public OcrStructure structure(){return structure;}
    public List<String> tableTexts(){return tableTexts;}
    /** Model-derived SLANet HTML retains cell spans absent in TSV. */
    public List<String> tableHtmls(){return tableHtmls;}
    public List<String> tableOrigins(){return tableOrigins;}
    public OcrDocument withTableOrigins(List<String> origins){
        return new OcrDocument(fullText,blocks,lines,engine,confidence,score,
            imageWidth,imageHeight,coordinateSpace,structure,tableTexts,tableHtmls,
            sealTexts,formulaTexts,chartTexts,unwarpedText,origins,rectifiedDocument);
    }
    public OcrDocument withTableHtmls(List<String> html){
        return new OcrDocument(fullText,blocks,lines,engine,confidence,score,
                imageWidth,imageHeight,coordinateSpace,structure,tableTexts,html,
                sealTexts,formulaTexts,chartTexts,unwarpedText,tableOrigins,rectifiedDocument);
    }
    /** Separate rectified-page text. It has no valid coordinates on the original screenshot. */
    public String unwarpedText(){return unwarpedText;}
    /** Corrected-page quads are ONLY valid on the rectified output bitmap. */
    public OcrDocument rectifiedDocument(){return rectifiedDocument;}
    public OcrDocument withRectifiedDocument(OcrDocument corrected){
        if(corrected!=null && corrected.coordinateSpace!=CoordinateSpace.RECTIFIED_BITMAP)
            throw new IllegalArgumentException("Corrected OCR has wrong coordinate system");
        return new OcrDocument(fullText,blocks,lines,engine,confidence,score,
            imageWidth,imageHeight,coordinateSpace,structure,tableTexts,tableHtmls,
            sealTexts,formulaTexts,chartTexts,unwarpedText,tableOrigins,corrected);
    }
    public OcrDocument asRectifiedBitmapSpace(){
        return new OcrDocument(fullText,blocks,lines,engine,confidence,score,
            imageWidth,imageHeight,CoordinateSpace.RECTIFIED_BITMAP,structure,
            tableTexts,tableHtmls,sealTexts,formulaTexts,chartTexts,unwarpedText,tableOrigins,null);
    }
    /** Optional seal region text. Detection polygons remain in PaddlePipelineV2. */
    public List<String> sealTexts(){return sealTexts;}
    public List<String> formulaTexts(){return formulaTexts;}
    /** Visible chart title/labels only; no fabricated numerical data series. */
    public List<String> chartTexts(){return chartTexts;}
    public OcrDocument withChartTexts(List<String> values){
        return new OcrDocument(fullText,blocks,lines,engine,confidence,score,
            imageWidth,imageHeight,coordinateSpace,structure,tableTexts,tableHtmls,
            sealTexts,formulaTexts,values,unwarpedText,tableOrigins,rectifiedDocument);
    }
    /** Genuine LaTeX is model output; no character quads are synthesized. */
    public OcrDocument withFormulaTexts(List<String> formulas){
        List<String> safe=formulas==null?List.of():List.copyOf(formulas);
        String text=fullText;
        if(text.isBlank()&&!safe.isEmpty())text=String.join("\n",safe);
        return new OcrDocument(text,blocks,lines,engine,confidence,score,
            imageWidth,imageHeight,coordinateSpace,structure,tableTexts,tableHtmls,
            sealTexts,safe,chartTexts,unwarpedText,tableOrigins,rectifiedDocument);
    }
    public OcrDocument withSealTexts(List<String> values){
        return new OcrDocument(fullText,blocks,lines,engine,confidence,score,
            imageWidth,imageHeight,coordinateSpace,structure,tableTexts,tableHtmls,values,formulaTexts,chartTexts,unwarpedText,tableOrigins,rectifiedDocument);
    }
    public OcrDocument withUnwarpedText(String value){
        return new OcrDocument(fullText,blocks,lines,engine,confidence,score,
            imageWidth,imageHeight,coordinateSpace,structure,tableTexts,tableHtmls,sealTexts,formulaTexts,chartTexts,value,tableOrigins,rectifiedDocument);
    }
    /** Table text is structured metadata, never synthetic glyph geometry. */
    public OcrDocument withTableTexts(List<String> tableText){
        return new OcrDocument(fullText,blocks,lines,engine,confidence,score,
                imageWidth,imageHeight,coordinateSpace,structure,tableText,tableHtmls,sealTexts,formulaTexts,chartTexts,unwarpedText,tableOrigins,rectifiedDocument);
    }
    public OcrDocument withStructure(OcrStructure geometry){
        return new OcrDocument(fullText,blocks,lines,engine,confidence,score,
                imageWidth,imageHeight,coordinateSpace,geometry,tableTexts,tableHtmls,sealTexts,formulaTexts,chartTexts,unwarpedText,tableOrigins,rectifiedDocument);
    }
    public boolean isBitmapSpace() { return coordinateSpace == CoordinateSpace.BITMAP; }
    public boolean isScreenSpace() { return coordinateSpace == CoordinateSpace.SCREEN; }
    public boolean isEmpty() { return fullText.isBlank() && chars().isEmpty(); }

    public List<CharUnit> chars() {
        if (lines.isEmpty()) return List.of();
        ArrayList<CharUnit> out = new ArrayList<>();
        for (Line line : lines) out.addAll(line.chars());
        return Collections.unmodifiableList(out);
    }

    /** Translate within the current coordinate space, preserving its coordinate-space identity. */
    public OcrDocument translated(int dx, int dy, int parentWidth, int parentHeight) {
        ArrayList<Line> outLines = new ArrayList<>();
        int order = 0;
        for (int li = 0; li < lines.size(); li++) {
            Line line = lines.get(li);
            Rect lineBounds = line.bounds();
            lineBounds.offset(dx, dy);
            ArrayList<CharUnit> outChars = new ArrayList<>();
            for (CharUnit c : line.chars()) {
                outChars.add(c.translated(dx, dy, li, order++));
            }
            outLines.add(new Line(line.text(), lineBounds, line.confidence(), outChars));
        }
        OcrDocument translated=new OcrDocument(fullText, blocks, outLines, engine,
                confidence, score,parentWidth,parentHeight,coordinateSpace);
        OcrDocument withStructure=structure==null?translated:translated.withStructure(
                structure.mapped((x,y)->new android.graphics.PointF(x+dx,y+dy)));
        return withStructure.withTableTexts(tableTexts).withTableHtmls(tableHtmls)
                .withTableOrigins(tableOrigins).withSealTexts(sealTexts)
                .withUnwarpedText(unwarpedText).withFormulaTexts(formulaTexts)
                .withChartTexts(chartTexts).withRectifiedDocument(rectifiedDocument);
    }
}
