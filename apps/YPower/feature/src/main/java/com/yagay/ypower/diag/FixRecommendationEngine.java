package com.yagay.ypower.diag;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.FixRecommendation;

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
        StringBuilder b = new StringBuilder(com.yagay.suite.api.YLocale.text(
                com.yagay.ypower.R.string.ypower_attr_base,
                role, f.ruleId, f.representativeState, f.correlationScore));
        if (f.closestDeltaMs != Long.MAX_VALUE) {
            b.append(com.yagay.suite.api.YLocale.text(
                    com.yagay.ypower.R.string.ypower_attr_delta, f.closestDeltaMs));
        }
        b.append(com.yagay.suite.api.YLocale.text(
                com.yagay.ypower.R.string.ypower_attr_counts,
                f.totalCount, f.hitCount, f.checkedCount, f.notHitCount, f.unknownCount));
        if (f.sameThreadAsExit) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_same_exit_thread));
        if (f.sharedExitFrames > 0) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_shared_exit, f.sharedExitFrames));
        if (f.sameThreadAsFatal) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_same_fatal_thread));
        if (f.sharedFatalFrames > 0) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_shared_fatal, f.sharedFatalFrames));
        if (notBlank(f.input)) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_input, f.input));
        if (notBlank(f.result)) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_result, f.result));
        if (notBlank(f.exception)) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_exception, f.exception));
        if (f.representativeState.name().equals("CHECKED")) {
            b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_checked_note));
        }
        return b.toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

}
