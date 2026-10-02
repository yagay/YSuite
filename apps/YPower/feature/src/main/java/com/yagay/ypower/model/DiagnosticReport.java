package com.yagay.ypower.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DiagnosticReport {
    public String packageName;
    public DiagnosticLevel level;
    public long createdAt = System.currentTimeMillis();
    public long sessionStartMs;
    public long sessionEndMs;
    public String sessionId = "";
    public long lastExitTimestamp;
    public int exitPid = -1;
    public int exitTid = -1;
    public String exitThread = "";
    public String exitSource = "";
    public String exitStack = "";
    public String exitRuleId = "";

    public long fatalExceptionTimestamp;
    public int fatalExceptionPid = -1;
    public int fatalExceptionTid = -1;
    public String fatalExceptionThread = "";
    public String fatalExceptionClass = "";
    public String fatalExceptionMessage = "";
    public String fatalThrowableId = "";
    public String fatalExceptionStack = "";
    public final List<String> exceptionPropagation = new ArrayList<>();

    public int observedEventCount;
    public String exitSummary = "";
    public String attribution = "";

    public String perfettoTracePath = "";
    public long perfettoTraceBytes;
    public String simpleperfDataPath = "";
    public long simpleperfDataBytes;
    public String simpleperfReportPath = "";
    public String simpleperfSummary = "";
    public String syscallTracePath = "";
    public long syscallTraceBytes;
    public String syscallSummary = "";
    public final List<String> linkerMappings = new ArrayList<>();

    public final List<DiagnosticFinding> findings = new ArrayList<>();
    public final List<DiagnosticFinding> staticEvidence = new ArrayList<>();
    public final List<String> raw = new ArrayList<>();

    public DiagnosticReport(String packageName, DiagnosticLevel level) {
        this.packageName = packageName;
        this.level = level;
    }

    public String simpleText() {
        StringBuilder b = new StringBuilder();
        line(b, tr("App", "应用"), packageName);
        line(b, tr("Level", "级别"), levelLabel(level));
        if (sessionStartMs > 0) {
            String end = sessionEndMs > 0 ? formatTime(sessionEndMs) : tr("Running", "进行中");
            line(b, tr("Runtime session", "运行会话"), formatTime(sessionStartMs) + " to " + end);
        }
        line(b, tr("Observed events", "实际观察事件"), String.valueOf(observedEventCount));
        b.append('\n');

        if (findings.isEmpty()) {
            b.append(tr(
                    "No recognizable detection or abnormal event was observed during this run.",
                    "本次运行未观察到可识别的检测或异常事件。"
            )).append('\n');
            return b.toString();
        }

        if (!exitSummary.isBlank()) line(b, tr("Exit", "退出"), exitSummary);
        if (fatalExceptionTimestamp > 0) {
            String value = fatalExceptionClass;
            if (!fatalExceptionMessage.isBlank()) value += ". " + fatalExceptionMessage;
            line(b, "Java Fatal", value);
        }
        if (!attribution.isBlank()) line(b, tr("Attribution", "归因"), attribution);
        if (!exitSummary.isBlank() || !attribution.isBlank()) b.append('\n');

        if (!perfettoTracePath.isBlank() || !simpleperfDataPath.isBlank() || !syscallTracePath.isBlank()) {
            b.append(tr("System collection", "系统级采集")).append('\n');
            if (!perfettoTracePath.isBlank()) line(b, "Perfetto", perfettoTraceBytes + " bytes");
            if (!simpleperfDataPath.isBlank()) line(b, "simpleperf", simpleperfDataBytes + " bytes");
            if (!syscallTracePath.isBlank()) line(b, tr("Raw syscall experimental", "Raw syscall 实验"), syscallTraceBytes + " bytes");
            b.append('\n');
        }

        int index = 1;
        for (DiagnosticFinding finding : findings) {
            b.append(index++).append(". ");
            if (finding.attributionRank == 1) b.append(tr("Primary attribution. ", "主要归因。"));
            else if (finding.attributionRank == 2) b.append(tr("Secondary attribution. ", "次要归因。"));
            b.append(finding.title).append('\n');
            line(b, tr("Detection state", "检测状态"), displayDetectionState(finding));
            if (finding.correlationScore > 0) {
                line(b, tr("Correlation score", "关联分数"), finding.correlationScore + " " + tr("of 100", "满分 100"));
            }
            b.append('\n');
        }
        return b.toString();
    }

    public String detailedText() {
        StringBuilder b = new StringBuilder(simpleText()).append('\n');
        List<DiagnosticFinding> ordered = new ArrayList<>(findings);
        ordered.sort(Comparator.comparingInt((DiagnosticFinding f) -> f.correlationScore).reversed());
        int index = 1;
        for (DiagnosticFinding f : ordered) {
            b.append(index++).append(". ").append(f.title).append('\n');
            line(b, tr("Status", "状态"), f.status.label());
            if (f.attributionRank == 1) line(b, tr("Attribution level", "归因级别"), tr("Primary", "主要"));
            else if (f.attributionRank == 2) line(b, tr("Attribution level", "归因级别"), tr("Secondary", "次要"));
            line(b, tr("Category", "类别"), f.category);
            if (f.ruleId != null && !f.ruleId.isBlank()) line(b, tr("Rule", "规则"), f.ruleId);
            line(b, tr("Summary", "摘要"), f.summary);
            if (f.totalCount > 0) {
                String states = "HIT " + f.hitCount
                        + ", CHECKED " + f.checkedCount
                        + ", NOT_HIT " + f.notHitCount
                        + ", UNKNOWN " + f.unknownCount
                        + ", " + tr("total", "总计") + " " + f.totalCount;
                line(b, tr("Run states", "本次状态"), states);
                line(b, tr("Detection state", "应用检测状态"), displayDetectionState(f));
                line(b, tr("Representative state", "代表状态"), String.valueOf(f.representativeState));
            }
            if (f.closestDeltaMs != Long.MAX_VALUE) {
                line(b, tr("Time before exit", "距退出"), f.closestDeltaMs + " ms");
            }
            if (f.tid >= 0) {
                line(b, tr("Thread", "线程"), f.thread + ", pid=" + f.pid + ", tid=" + f.tid);
            }
            if (f.sameThreadAsExit) line(b, tr("Exit relation", "与退出"), tr("Same thread", "同线程"));
            if (f.sharedExitFrames > 0) line(b, tr("Shared exit stack frames", "与退出共同调用栈帧"), String.valueOf(f.sharedExitFrames));
            if (f.sameThreadAsFatal) line(b, tr("Java Fatal relation", "与 Java Fatal"), tr("Same thread", "同线程"));
            if (f.sharedFatalFrames > 0) line(b, tr("Shared Java Fatal business frames", "与 Java Fatal 共同业务栈帧"), String.valueOf(f.sharedFatalFrames));
            if (notBlank(f.input)) line(b, tr("Input", "输入"), f.input);
            if (notBlank(f.result)) line(b, tr("Result", "结果"), f.result);
            if (notBlank(f.exception)) line(b, tr("Exception", "异常"), f.exception);
            if (notBlank(f.throwableId)) line(b, "Throwable ID", f.throwableId);
            if (notBlank(f.cause)) line(b, "Cause", f.cause);
            if (f.suppressedCount > 0) line(b, "Suppressed", String.valueOf(f.suppressedCount));
            if (f.correlationScore > 0) line(b, tr("Exit correlation", "退出关联"), f.correlationScore + " " + tr("of 100", "满分 100"));
            if (f.detail != null && !f.detail.equals(f.summary)) line(b, tr("Detail", "详情"), f.detail);
            for (String e : f.evidence) line(b, tr("Evidence", "证据"), e);
            for (FixRecommendation recommendation : f.recommendations) {
                line(b, tr("Explanation", "说明"), recommendation.title);
                line(b, tr("Why it is checked", "为什么检测"), recommendation.whyDetected);
                line(b, tr("Open-source reference explanation", "开源项目说明"), recommendation.projectExplanation);
                line(b, tr("Why it is attributed", "为什么归因"), recommendation.whyAttributed);
                line(b, tr("Repair or investigation", "修复或排查"), recommendation.repair);
                line(b, tr("Reference", "参考"), recommendation.source);
            }
            b.append('\n');
        }

        if (!staticEvidence.isEmpty()) {
            b.append(tr(
                    "Static supplemental evidence. Deep diagnostics only. It does not mean the code ran during this session and it is not used for attribution.",
                    "静态辅助证据。仅用于深度诊断，不代表本次运行已经执行，也不参与归因。"
            )).append('\n');
            int staticIndex = 1;
            for (DiagnosticFinding f : staticEvidence) {
                b.append(staticIndex++).append(". ").append(f.title).append('\n');
                if (notBlank(f.ruleId)) line(b, tr("Rule", "规则"), f.ruleId);
                if (notBlank(f.summary)) line(b, tr("Explanation", "说明"), f.summary);
                for (String e : f.evidence) line(b, tr("Reference", "引用"), e);
                b.append('\n');
            }
        }

        if (!exceptionPropagation.isEmpty()) {
            b.append(tr("Exception propagation", "异常传播链")).append('\n');
            appendNumbered(b, exceptionPropagation);
            b.append('\n');
        }

        if (!linkerMappings.isEmpty()) {
            b.append(tr("JNI and Linker mapping", "JNI 和 Linker 映射")).append('\n');
            appendNumbered(b, linkerMappings);
            b.append('\n');
        }
        if (!simpleperfSummary.isBlank()) {
            b.append(tr("simpleperf native call graph summary", "simpleperf Native 调用图摘要")).append('\n')
                    .append(simpleperfSummary).append("\n\n");
        }
        if (!perfettoTracePath.isBlank()) line(b, "Perfetto Trace", perfettoTracePath);
        if (!simpleperfDataPath.isBlank()) line(b, tr("simpleperf data", "simpleperf 数据"), simpleperfDataPath);
        if (!simpleperfReportPath.isBlank()) line(b, tr("simpleperf report", "simpleperf 报告"), simpleperfReportPath);
        if (!syscallTracePath.isBlank()) line(b, tr("Raw syscall experimental", "Raw syscall 实验"), syscallTracePath);

        return b.toString();
    }

    public String recommendationText() {
        StringBuilder b = new StringBuilder();
        if (!attribution.isBlank()) {
            line(b, tr("Attribution result", "归因结果"), attribution);
            b.append('\n');
        }
        int count = 0;
        for (DiagnosticFinding finding : findings) {
            for (FixRecommendation recommendation : finding.recommendations) {
                count++;
                b.append(count).append(". ").append(recommendation.title).append('\n');
                String level = finding.attributionRank == 1
                        ? tr("Primary attribution", "主要归因")
                        : tr("Secondary attribution", "次要归因");
                line(b, tr("Related finding", "对应"), level + ". " + finding.title);
                line(b, tr("Correlation score", "关联分数"), finding.correlationScore + " " + tr("of 100", "满分 100"));
                line(b, tr("Detection state", "应用检测状态"), displayDetectionState(finding));
                line(b, tr("Why it is checked", "为什么检测"), recommendation.whyDetected);
                line(b, tr("Open-source reference explanation", "开源项目说明"), recommendation.projectExplanation);
                line(b, tr("Why this run is attributed", "为什么这次归因"), recommendation.whyAttributed);
                line(b, tr("Repair or investigation", "应该怎样修复或排查"), recommendation.repair);
                line(b, tr("Reference", "参考"), recommendation.source);
                b.append('\n');
            }
        }
        if (count == 0) {
            return tr(
                    "The attribution evidence is insufficient, so no repair recommendation is generated.\n",
                    "当前归因证据不足，因此不生成修复建议。\n"
            );
        }
        return b.toString();
    }

    public String rawText() {
        if (raw.isEmpty()) {
            return tr(
                    "No displayable raw event was collected during this run.\n",
                    "本次运行没有采集到可显示的原始事件。\n"
            );
        }
        StringBuilder b = new StringBuilder();
        for (String line : raw) b.append(line).append('\n');
        return b.toString();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("packageName", packageName);
            o.put("level", level.name());
            o.put("createdAt", createdAt);
            o.put("sessionStartMs", sessionStartMs);
            o.put("sessionEndMs", sessionEndMs);
            o.put("sessionId", sessionId);
            o.put("observedEventCount", observedEventCount);
            o.put("lastExitTimestamp", lastExitTimestamp);
            o.put("exitPid", exitPid);
            o.put("exitTid", exitTid);
            o.put("exitThread", exitThread);
            o.put("exitSource", exitSource);
            o.put("exitStack", exitStack);
            o.put("exitRuleId", exitRuleId);
            o.put("fatalExceptionTimestamp", fatalExceptionTimestamp);
            o.put("fatalExceptionPid", fatalExceptionPid);
            o.put("fatalExceptionTid", fatalExceptionTid);
            o.put("fatalExceptionThread", fatalExceptionThread);
            o.put("fatalExceptionClass", fatalExceptionClass);
            o.put("fatalExceptionMessage", fatalExceptionMessage);
            o.put("fatalThrowableId", fatalThrowableId);
            o.put("fatalExceptionStack", fatalExceptionStack);
            JSONArray ep = new JSONArray();
            for (String item : exceptionPropagation) ep.put(item);
            o.put("exceptionPropagation", ep);
            o.put("exitSummary", exitSummary);
            o.put("attribution", attribution);
            o.put("perfettoTracePath", perfettoTracePath);
            o.put("perfettoTraceBytes", perfettoTraceBytes);
            o.put("simpleperfDataPath", simpleperfDataPath);
            o.put("simpleperfDataBytes", simpleperfDataBytes);
            o.put("simpleperfReportPath", simpleperfReportPath);
            o.put("simpleperfSummary", simpleperfSummary);
            o.put("syscallTracePath", syscallTracePath);
            o.put("syscallTraceBytes", syscallTraceBytes);
            o.put("syscallSummary", syscallSummary);
            JSONArray lm = new JSONArray();
            for (String mapping : linkerMappings) lm.put(mapping);
            o.put("linkerMappings", lm);
            JSONArray fs = new JSONArray();
            for (DiagnosticFinding f : findings) fs.put(f.toJson());
            o.put("findings", fs);
            JSONArray se = new JSONArray();
            for (DiagnosticFinding f : staticEvidence) se.put(f.toJson());
            o.put("staticEvidence", se);
            JSONArray rs = new JSONArray();
            for (String r : raw) rs.put(r);
            o.put("raw", rs);
        } catch (JSONException ignored) {
        }
        return o;
    }

    private static void line(StringBuilder b, String label, String value) {
        b.append(label).append(": ").append(value == null ? "" : value).append('\n');
    }

    private static void appendNumbered(StringBuilder b, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            b.append(i + 1).append(". ").append(values.get(i)).append('\n');
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String tr(String english, String chinese) {
        return "zh".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? chinese : english;
    }

    private static String levelLabel(DiagnosticLevel level) {
        if (level == DiagnosticLevel.QUICK) return tr("Fast", "快速");
        if (level == DiagnosticLevel.DEEP) return tr("Deep", "深度");
        return tr("Standard", "标准");
    }

    private static String displayDetectionState(DiagnosticFinding finding) {
        if (finding == null) return tr("Unknown", "未知");
        switch (finding.representativeState) {
            case NOT_HIT:
                return tr("Not hit", "未命中");
            case HIT:
                return tr("Hit", "命中");
            case CHECKED:
                return tr("Checked", "已检查");
            case UNKNOWN:
            default:
                if (finding.result != null) {
                    String value = finding.result.trim();
                    if ("false".equalsIgnoreCase(value)) return tr("Not hit", "未命中");
                    if (!value.isBlank()) return value;
                }
                return tr("Unknown", "未知");
        }
    }

    private static String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date(timestamp));
    }
}
