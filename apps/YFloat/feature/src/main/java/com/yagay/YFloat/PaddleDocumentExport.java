package com.yagay.YFloat;

import android.graphics.PointF;
import android.graphics.Rect;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

/**
 * Lossless local serialization of the actual Paddle OCR result.
 * Exports never invoke a network API or infer missing bounding boxes.
 * Coordinates preserve the document's BITMAP/SCREEN provenance.
 */
public final class PaddleDocumentExport {
    private PaddleDocumentExport() {}

    public static String json(OcrDocument document) {
        return jsonPages(List.of(document));
    }

    public static String jsonPages(List<OcrDocument> pages) {
        if (pages == null) throw new IllegalArgumentException("pages");
        try {
            JSONObject root = new JSONObject();
            root.put("schema", "yfloat.paddle.document.v1");
            root.put("pageCount", pages.size());
            JSONArray array = new JSONArray();
            for (int p = 0; p < pages.size(); p++) {
                OcrDocument d = pages.get(p);
                if (d == null) continue;
                JSONObject page = new JSONObject();
                page.put("pageIndex", p);
                page.put("engine", d.engine());
                page.put("imageWidth", d.imageWidth());
                page.put("imageHeight", d.imageHeight());
                page.put("coordinateSpace", d.coordinateSpace().name());
                page.put("confidence", d.confidence());
                page.put("text", d.fullText());
                page.put("unwarpedText", d.unwarpedText());
                if(d.rectifiedDocument()!=null){
                    // UVDoc is non-rigid: these coordinates must never be
                    // interpreted as positions on the original screenshot.
                    page.put("rectifiedCoordinateMapping","not_available");
                    JSONObject sub=new JSONObject(json(d.rectifiedDocument()))
                        .getJSONArray("pages").getJSONObject(0);
                    page.put("rectifiedPage",sub);
                }
                page.put("tableTexts", strings(d.tableTexts()));
                page.put("tableHtmls", strings(d.tableHtmls()));
                page.put("tableOrigins", strings(d.tableOrigins()));
                page.put("formulaTexts", strings(d.formulaTexts()));
                page.put("sealTexts", strings(d.sealTexts()));
                page.put("chartTexts", strings(d.chartTexts()));
                JSONArray blocks = new JSONArray();
                OcrStructure structured = d.structure();
                if (structured != null) {
                    for (OcrStructure.Block block : structured.blocks()) {
                        JSONObject b = new JSONObject();
                        b.put("kind", block.kind);
                        b.put("confidence", block.confidence);
                        b.put("quad", quad(block.quad));
                        JSONArray lines = new JSONArray();
                        for (OcrStructure.Line line : block.lines) {
                            JSONObject l = new JSONObject();
                            l.put("text", line.text);
                            l.put("confidence", line.confidence);
                            l.put("quad", quad(line.quad));
                            JSONArray words = new JSONArray();
                            for (OcrStructure.Word word : line.words) {
                                JSONObject w = new JSONObject();
                                w.put("text", word.text);
                                w.put("quad", quad(word.quad));
                                JSONArray elements = new JSONArray();
                                for (OcrStructure.Symbol symbol : word.symbols) {
                                    JSONObject e = new JSONObject();
                                    e.put("text", symbol.text);
                                    e.put("confidence", symbol.confidence);
                                    e.put("quad", quad(symbol.quad));
                                    elements.put(e);
                                }
                                w.put("elements", elements);
                                words.put(w);
                            }
                            l.put("words", words);
                            lines.put(l);
                        }
                        b.put("lines", lines);
                        blocks.put(b);
                    }
                }
                page.put("blocks", blocks);
                // Selection geometry may have fallback word/line units even
                // when CTC alignment lacks a per-symbol quad.
                JSONArray selection = new JSONArray();
                for (OcrDocument.CharUnit c : d.chars()) {
                    JSONObject item = new JSONObject();
                    item.put("text", c.text());
                    item.put("line", c.line());
                    item.put("group", c.group());
                    item.put("order", c.order());
                    item.put("confidence", c.confidence());
                    item.put("rect", rect(c.bounds()));
                    selection.put(item);
                }
                page.put("selectionUnits", selection);
                array.put(page);
            }
            root.put("pages", array);
            return root.toString(2);
        } catch (JSONException failure) {
            throw new IllegalStateException("Unable to serialize Paddle document", failure);
        }
    }

    public static String markdown(OcrDocument document) {
        if (document == null) throw new IllegalArgumentException("document");
        StringBuilder result = new StringBuilder();
        OcrStructure structure = document.structure();
        if (structure != null && !structure.blocks().isEmpty()) {
            for (OcrStructure.Block block : structure.blocks()) {
                StringBuilder text = new StringBuilder();
                for (OcrStructure.Line line : block.lines) {
                    String value = line.text == null ? "" : line.text.trim();
                    if (value.isEmpty()) continue;
                    if (text.length() > 0) text.append("\n");
                    text.append(value);
                }
                if (text.length() == 0) continue;
                String kind = block.kind.toLowerCase(Locale.ROOT);
                if (kind.contains("title") || kind.contains("header")) {
                    result.append(kind.contains("document_title") || kind.equals("title") ? "# " : "## ");
                } else if (kind.contains("formula")) {
                    result.append("$$\n").append(text).append("\n$$\n\n");
                    continue;
                }
                result.append(text).append("\n\n");
            }
        } else {
            result.append(document.fullText()).append("\n\n");
        }
        if (!document.tableTexts().isEmpty()) {
            result.append("## Tables\n\n");
            for (int i = 0; i < document.tableTexts().size(); i++) {
                String tsv = document.tableTexts().get(i);
                String originalHtml = i < document.tableHtmls().size()
                        ? document.tableHtmls().get(i) : "";
                // Markdown pipes cannot represent row/column spans. Embed
                // the source SLANet HTML rather than silently flatten cells.
                if (!originalHtml.isBlank() && (tsv.isBlank() ||
                        originalHtml.contains("rowspan") || originalHtml.contains("colspan")))
                    result.append(originalHtml).append("\n\n");
                else result.append(markdownTable(tsv)).append("\n");
            }
        }
        for (String formula : document.formulaTexts()) {
            if (formula != null && !formula.isBlank())
                result.append("$$\n").append(formula.trim()).append("\n$$\n\n");
        }
        if (!document.sealTexts().isEmpty()) {
            result.append("## Seals\n\n");
            for (String s : document.sealTexts()) result.append(s).append("\n\n");
        }
        if (!document.chartTexts().isEmpty()) {
            result.append("## Chart labels\n\n");
            for (String chart : document.chartTexts()) result.append(chart).append("\n\n");
        }
        if (!document.unwarpedText().isBlank()) {
            result.append("## Rectified document text\n\n")
                    .append(document.unwarpedText()).append("\n\n");
        }
        return result.toString().trim() + "\n";
    }

    public static String markdownPages(List<OcrDocument> pages) {
        if (pages == null) throw new IllegalArgumentException("pages");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            if (i != 0) out.append("\n---\n\n");
            out.append("<!-- Page ").append(i + 1).append(" -->\n\n");
            if (pages.get(i) != null) out.append(markdown(pages.get(i)));
        }
        return out.toString();
    }

    public static String htmlPages(List<OcrDocument> pages) {
        if(pages==null)throw new IllegalArgumentException("pages");
        StringBuilder out=new StringBuilder("<!DOCTYPE html><html><head>")
                .append("<meta charset=\"UTF-8\"><title>YFloat OCR</title></head><body>");
        for(int i=0;i<pages.size();i++){
            OcrDocument page=pages.get(i);
            if(page==null)continue;
            String full=html(page);
            int start=full.indexOf("<body>")+6;
            int end=full.lastIndexOf("</body>");
            if(start<6||end<start)continue;
            out.append("<section data-page=\"").append(i+1).append("\">")
                    .append(full,start,end).append("</section>");
        }
        return out.append("</body></html>").toString();
    }

    public static String csvPages(List<OcrDocument> pages) {
        if(pages==null)throw new IllegalArgumentException("pages");
        StringBuilder out=new StringBuilder();
        for(OcrDocument page:pages) {
            if(page==null||page.tableTexts().isEmpty())continue;
            if(out.length()>0)out.append("\r\n");
            out.append(csvTables(page));
        }
        return out.toString();
    }

    public static String html(OcrDocument document) {
        if (document == null) throw new IllegalArgumentException("document");
        StringBuilder out = new StringBuilder("<!DOCTYPE html><html><head>")
                .append("<meta charset=\"UTF-8\"><title>YFloat OCR</title></head><body>");
        OcrStructure s = document.structure();
        if (s != null && !s.blocks().isEmpty()) {
            for (OcrStructure.Block block : s.blocks()) {
                String kind = block.kind.toLowerCase(Locale.ROOT);
                String tag = kind.contains("title") ? "h2" : "p";
                if (kind.equals("title") || kind.equals("document_title")) tag = "h1";
                out.append('<').append(tag).append('>');
                for (int i = 0; i < block.lines.size(); i++) {
                    if (i != 0) out.append("<br>");
                    out.append(escape(block.lines.get(i).text));
                }
                out.append("</").append(tag).append('>');
            }
        } else out.append("<pre>").append(escape(document.fullText())).append("</pre>");
        if (!document.tableHtmls().isEmpty()) {
            for (String original : document.tableHtmls()) out.append(original);
        } else {
            for (String t : document.tableTexts()) {
                out.append("<table border=\"1\">");
                for (String row : t.split("\\r?\\n", -1)) {
                    out.append("<tr>");
                    for (String cell : row.split("\\t", -1))
                        out.append("<td>").append(escape(cell)).append("</td>");
                    out.append("</tr>");
                }
                out.append("</table>");
            }
        }
        for (String latex : document.formulaTexts())
            out.append("<pre class=\"latex\">").append(escape(latex)).append("</pre>");
        out.append("</body></html>");
        return out.toString();
    }

    public static String csvTables(OcrDocument document) {
        if (document == null) throw new IllegalArgumentException("document");
        StringBuilder out = new StringBuilder();
        for (String tsv : document.tableTexts()) {
            if (out.length() != 0) out.append("\n");
            for (String line : tsv.split("\\r?\\n", -1)) {
                String[] cells = line.split("\\t", -1);
                for (int i = 0; i < cells.length; i++) {
                    if (i != 0) out.append(',');
                    out.append('"').append(cells[i].replace("\"", "\"\"")).append('"');
                }
                out.append("\r\n");
            }
        }
        return out.toString();
    }

    private static String markdownTable(String text) {
        if (text == null || text.isBlank()) return "";
        String[] rows = text.split("\\r?\\n");
        StringBuilder out = new StringBuilder();
        int columns = 0;
        for (String row : rows) columns = Math.max(columns, row.split("\\t", -1).length);
        for (int i = 0; i < rows.length; i++) {
            String[] cells = rows[i].split("\\t", -1);
            out.append('|');
            for (int j = 0; j < columns; j++) out.append(' ')
                    .append(j < cells.length ? cells[j].replace("|", "\\|").replace("\n", " ") : "")
                    .append(" |");
            out.append('\n');
            if (i == 0) {
                out.append('|');
                for (int j = 0; j < columns; j++) out.append(" --- |");
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static JSONArray strings(List<String> items) {
        JSONArray a = new JSONArray();
        if (items != null) for (String value : items) a.put(value);
        return a;
    }

    private static JSONObject rect(Rect r) throws JSONException {
        return new JSONObject().put("left", r.left).put("top", r.top)
                .put("right", r.right).put("bottom", r.bottom);
    }

    private static JSONObject quad(OcrStructure.Quad q) throws JSONException {
        if (q == null) return new JSONObject().put("origin", "unavailable")
                .put("points", new JSONArray());
        JSONArray p = new JSONArray();
        for (PointF pt : q.points())
            p.put(new JSONArray().put(pt.x).put(pt.y));
        return new JSONObject().put("origin", q.origin()).put("points", p)
                .put("rect", rect(q.bounds()));
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
