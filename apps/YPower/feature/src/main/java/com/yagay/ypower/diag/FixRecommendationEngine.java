package com.yagay.ypower.diag;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.FixRecommendation;

import java.util.Locale;

public final class FixRecommendationEngine {
    private FixRecommendationEngine() {}

    public static void apply(DiagnosticReport report) {
        SupplementalReportProcessor.apply(report);
        CorrelationEngine.analyze(report);

        for (DiagnosticFinding finding : report.findings) {
            finding.recommendations.clear();
        }

        if (report.lastExitTimestamp <= 0) return;

        for (DiagnosticFinding finding : report.findings) {
            if (finding.attributionRank != 1 && finding.attributionRank != 2) continue;

            DetectionRuleDefinition rule = DetectionRuleCatalog.getOrDefault(
                    finding.ruleId,
                    finding.category,
                    finding.title
            );

            String role = finding.attributionRank == 1
                    ? com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_464d2bd27a32)
                    : com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2487c9061fec);
            finding.recommendations.add(new FixRecommendation(
                    role + ": " + rule.title,
                    rule.whyDetected,
                    rule.projectExplanation,
                    buildAttributionExplanation(finding, role),
                    rule.remediation,
                    rule.reference
            ));
        }

        SupplementalReportProcessor.enrichRecommendations(report);
    }

    private static String buildAttributionExplanation(DiagnosticFinding f, String role) {
        StringBuilder b = new StringBuilder();

        if (isChinese()) {
            b.append("本项被标记为").append(role)
                    .append("。规则 ID 为 ").append(f.ruleId)
                    .append("。代表状态为 ").append(f.representativeState)
                    .append("。关联分数为 ").append(f.correlationScore).append("，满分 100。");

            if (f.closestDeltaMs != Long.MAX_VALUE) {
                b.append(" 离真实退出最近的一次同规则事件相隔 ")
                        .append(f.closestDeltaMs).append(" ms。");
            }

            b.append(" 本次共记录 ").append(f.totalCount)
                    .append(" 次。HIT ").append(f.hitCount)
                    .append("，CHECKED ").append(f.checkedCount)
                    .append("，NOT_HIT ").append(f.notHitCount)
                    .append("，UNKNOWN ").append(f.unknownCount).append("。");

            if (f.sameThreadAsExit) b.append(" 代表事件与退出发生在同一线程。");
            if (f.sharedExitFrames > 0) {
                b.append(" 检测栈与退出栈有 ").append(f.sharedExitFrames).append(" 个共同业务调用帧。");
            }
            if (f.sameThreadAsFatal) b.append(" 该检测与 Java Fatal 发生在同一线程。");
            if (f.sharedFatalFrames > 0) {
                b.append(" 检测栈与 Java Fatal 栈有 ").append(f.sharedFatalFrames)
                        .append(" 个共同业务调用帧，形成检测、异常和退出之间的中间证据。");
            }
            if (notBlank(f.input)) b.append(" 代表输入：").append(f.input).append("。");
            if (notBlank(f.result)) b.append(" 代表返回结果：").append(f.result).append("。");
            if (notBlank(f.exception)) b.append(" 代表调用异常：").append(f.exception).append("。");
            if (f.representativeState.name().equals("CHECKED")) {
                b.append(" CHECKED 只证明应用执行了检查，并不证明检查结果命中了风险状态。");
            }
        } else {
            b.append("This item is marked as ").append(role)
                    .append(". Rule ID is ").append(f.ruleId)
                    .append(". Representative state is ").append(f.representativeState)
                    .append(". Correlation score is ").append(f.correlationScore).append(" out of 100.");

            if (f.closestDeltaMs != Long.MAX_VALUE) {
                b.append(" The closest event for this rule occurred ")
                        .append(f.closestDeltaMs).append(" ms before the recorded exit.");
            }

            b.append(" This run recorded ").append(f.totalCount)
                    .append(" events. HIT ").append(f.hitCount)
                    .append(", CHECKED ").append(f.checkedCount)
                    .append(", NOT_HIT ").append(f.notHitCount)
                    .append(", UNKNOWN ").append(f.unknownCount).append(".");

            if (f.sameThreadAsExit) b.append(" The representative event and the exit occurred on the same thread.");
            if (f.sharedExitFrames > 0) {
                b.append(" The detection stack and exit stack share ").append(f.sharedExitFrames).append(" application frames.");
            }
            if (f.sameThreadAsFatal) b.append(" This detection and the Java Fatal event occurred on the same thread.");
            if (f.sharedFatalFrames > 0) {
                b.append(" The detection stack and Java Fatal stack share ").append(f.sharedFatalFrames)
                        .append(" application frames, providing intermediate evidence between the check, exception, and exit.");
            }
            if (notBlank(f.input)) b.append(" Representative input: ").append(f.input).append(".");
            if (notBlank(f.result)) b.append(" Representative result: ").append(f.result).append(".");
            if (notBlank(f.exception)) b.append(" Representative exception: ").append(f.exception).append(".");
            if (f.representativeState.name().equals("CHECKED")) {
                b.append(" CHECKED only proves that the app performed the check. It does not prove that the result matched a risk condition.");
            }
        }

        return b.toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isChinese() {
        return "zh".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }
}
