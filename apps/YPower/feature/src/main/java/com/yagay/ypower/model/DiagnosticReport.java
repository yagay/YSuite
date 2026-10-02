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
        line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_35d63f9d6d8c), packageName);
        line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3225693c8052), levelLabel(level));
        if (sessionStartMs > 0) {
            String end = sessionEndMs > 0 ? formatTime(sessionEndMs) : com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_df143870c04b);
            line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8f20edcbd6df), formatTime(sessionStartMs) + " to " + end);
        }
        line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9114928331c8), String.valueOf(observedEventCount));
        b.append('\n');

        if (findings.isEmpty()) {
            b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cb544251b098)).append('\n');
            return b.toString();
        }

        if (!exitSummary.isBlank()) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_fd60953f03cd), exitSummary);
        if (fatalExceptionTimestamp > 0) {
            String value = fatalExceptionClass;
            if (!fatalExceptionMessage.isBlank()) value += ". " + fatalExceptionMessage;
            line(b, "Java Fatal", value);
        }
        if (!attribution.isBlank()) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cc8c6af5b345), attribution);
        if (!exitSummary.isBlank() || !attribution.isBlank()) b.append('\n');

        if (!perfettoTracePath.isBlank() || !simpleperfDataPath.isBlank() || !syscallTracePath.isBlank()) {
            b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3acf7aeb4f70)).append('\n');
            if (!perfettoTracePath.isBlank()) line(b, "Perfetto", perfettoTraceBytes + " bytes");
            if (!simpleperfDataPath.isBlank()) line(b, "simpleperf", simpleperfDataBytes + " bytes");
            if (!syscallTracePath.isBlank()) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_081ba34fa472), syscallTraceBytes + " bytes");
            b.append('\n');
        }

        int index = 1;
        for (DiagnosticFinding finding : findings) {
            b.append(index++).append(". ");
            if (finding.attributionRank == 1) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6f3bc8a9fc39));
            else if (finding.attributionRank == 2) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5da39a419ad3));
            b.append(finding.title).append('\n');
            line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_81c4e4e7a4c8), displayDetectionState(finding));
            if (finding.correlationScore > 0) {
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0810979d5fe5), finding.correlationScore + " " + com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0ecf13cfed47));
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
            line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d925f905dbc7), f.status.label());
            if (f.attributionRank == 1) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f1151a2b5b87), com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_59dd7f2a15a6));
            else if (f.attributionRank == 2) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f1151a2b5b87), com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c709c646caa6));
            line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4e1be532447f), f.category);
            if (f.ruleId != null && !f.ruleId.isBlank()) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_926188a1031f), f.ruleId);
            line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a0a8ca755df0), f.summary);
            if (f.totalCount > 0) {
                String states = "HIT " + f.hitCount
                        + ", CHECKED " + f.checkedCount
                        + ", NOT_HIT " + f.notHitCount
                        + ", UNKNOWN " + f.unknownCount
                        + ", " + com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6a2109cfaaca) + " " + f.totalCount;
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8b1dae00dd60), states);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3057e103ffb7), displayDetectionState(f));
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_dbe62bdb4e56), String.valueOf(f.representativeState));
            }
            if (f.closestDeltaMs != Long.MAX_VALUE) {
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a862d30463ac), f.closestDeltaMs + " ms");
            }
            if (f.tid >= 0) {
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9c16645c55a6), f.thread + ", pid=" + f.pid + ", tid=" + f.tid);
            }
            if (f.sameThreadAsExit) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f5f82a540533), com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c05c6221a1e7));
            if (f.sharedExitFrames > 0) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_25bcccd9d858), String.valueOf(f.sharedExitFrames));
            if (f.sameThreadAsFatal) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_bcc9bbb54868), com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c05c6221a1e7));
            if (f.sharedFatalFrames > 0) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_60b5c4f8f3c2), String.valueOf(f.sharedFatalFrames));
            if (notBlank(f.input)) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_61ac3df8f882), f.input);
            if (notBlank(f.result)) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_933009f496a0), f.result);
            if (notBlank(f.exception)) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_dbfaff4d3c05), f.exception);
            if (notBlank(f.throwableId)) line(b, "Throwable ID", f.throwableId);
            if (notBlank(f.cause)) line(b, "Cause", f.cause);
            if (f.suppressedCount > 0) line(b, "Suppressed", String.valueOf(f.suppressedCount));
            if (f.correlationScore > 0) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9b106581db4e), f.correlationScore + " " + com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0ecf13cfed47));
            if (f.detail != null && !f.detail.equals(f.summary)) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_fd2b4530c5c8), f.detail);
            for (String e : f.evidence) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8627da61e118), e);
            for (FixRecommendation recommendation : f.recommendations) {
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_46aa5a1b77cc), recommendation.title);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8a7c34c165cd), recommendation.whyDetected);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7aed0d10ef23), recommendation.projectExplanation);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cfc2ebe19f3f), recommendation.whyAttributed);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_bdc2cc5cce19), recommendation.repair);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a317091e2f09), recommendation.source);
            }
            b.append('\n');
        }

        if (!staticEvidence.isEmpty()) {
            b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0118ea5f4a77)).append('\n');
            int staticIndex = 1;
            for (DiagnosticFinding f : staticEvidence) {
                b.append(staticIndex++).append(". ").append(f.title).append('\n');
                if (notBlank(f.ruleId)) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_926188a1031f), f.ruleId);
                if (notBlank(f.summary)) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_46aa5a1b77cc), f.summary);
                for (String e : f.evidence) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2791434111b3), e);
                b.append('\n');
            }
        }

        if (!exceptionPropagation.isEmpty()) {
            b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_556c83bc3aa7)).append('\n');
            appendNumbered(b, exceptionPropagation);
            b.append('\n');
        }

        if (!linkerMappings.isEmpty()) {
            b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_abb7a244e59e)).append('\n');
            appendNumbered(b, linkerMappings);
            b.append('\n');
        }
        if (!simpleperfSummary.isBlank()) {
            b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2c2eb0fa3b6f)).append('\n')
                    .append(simpleperfSummary).append("\n\n");
        }
        if (!perfettoTracePath.isBlank()) line(b, "Perfetto Trace", perfettoTracePath);
        if (!simpleperfDataPath.isBlank()) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3af848644bd8), simpleperfDataPath);
        if (!simpleperfReportPath.isBlank()) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ba05931f6be5), simpleperfReportPath);
        if (!syscallTracePath.isBlank()) line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_081ba34fa472), syscallTracePath);

        return b.toString();
    }

    public String recommendationText() {
        StringBuilder b = new StringBuilder();
        if (!attribution.isBlank()) {
            line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2e61fddc21ee), attribution);
            b.append('\n');
        }
        int count = 0;
        for (DiagnosticFinding finding : findings) {
            for (FixRecommendation recommendation : finding.recommendations) {
                count++;
                b.append(count).append(". ").append(recommendation.title).append('\n');
                String level = finding.attributionRank == 1
                        ? com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_464d2bd27a32)
                        : com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2487c9061fec);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e161cf3369d3), level + ". " + finding.title);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0810979d5fe5), finding.correlationScore + " " + com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0ecf13cfed47));
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3057e103ffb7), displayDetectionState(finding));
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8a7c34c165cd), recommendation.whyDetected);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7aed0d10ef23), recommendation.projectExplanation);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6456781ef53b), recommendation.whyAttributed);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7c805bd2a8b6), recommendation.repair);
                line(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a317091e2f09), recommendation.source);
                b.append('\n');
            }
        }
        if (count == 0) {
            return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2a68a9d90e93);
        }
        return b.toString();
    }

    public String rawText() {
        if (raw.isEmpty()) {
            return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a559c1fe92d6);
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
        if (level == DiagnosticLevel.QUICK) return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b9b0f082ed01);
        if (level == DiagnosticLevel.DEEP) return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cfb34c6c227b);
        return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e82299110866);
    }

    private static String displayDetectionState(DiagnosticFinding finding) {
        if (finding == null) return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_87f340e4520f);
        switch (finding.representativeState) {
            case NOT_HIT:
                return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7f759987a658);
            case HIT:
                return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_faaa5ec8d944);
            case CHECKED:
                return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6549ed732c87);
            case UNKNOWN:
            default:
                if (finding.result != null) {
                    String value = finding.result.trim();
                    if ("false".equalsIgnoreCase(value)) return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7f759987a658);
                    if (!value.isBlank()) return value;
                }
                return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_87f340e4520f);
        }
    }

    private static String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date(timestamp));
    }
}
