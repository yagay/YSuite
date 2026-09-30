package com.yagay.ypower.diag;

import com.yagay.ypower.hook.DetectionRuleIds;
import com.yagay.ypower.hook.SupplementalRuleIds;
import com.yagay.ypower.model.DetectionHitState;
import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticLevel;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.DiagnosticStatus;
import com.yagay.ypower.model.FixRecommendation;

import java.util.ArrayList;
import java.util.List;

public final class SupplementalReportProcessor {
    private SupplementalReportProcessor() {}

    public static void apply(DiagnosticReport report) {
        if (report.level == DiagnosticLevel.DEEP) {
            StaticApiReferenceScanner.scan(report);
        }

        retitleSupplementalFindings(report);
        addFlow(report,
                SupplementalRuleIds.VIRTUALIZATION_FLOW,
                2,
                5000,
                new String[]{
                        DetectionRuleIds.VIRTUAL_PROPERTY_QUERY,
                        DetectionRuleIds.VIRTUAL_GL_RENDERER_QUERY,
                        DetectionRuleIds.VIRTUAL_TELEPHONY_QUERY,
                        DetectionRuleIds.VIRTUAL_SENSOR_QUERY
                });
        addFlow(report,
                SupplementalRuleIds.NETWORK_ENVIRONMENT_FLOW,
                2,
                5000,
                new String[]{
                        DetectionRuleIds.VPN_TRANSPORT_QUERY,
                        DetectionRuleIds.VPN_INTERFACE_QUERY,
                        DetectionRuleIds.PROXY_PROPERTY_QUERY,
                        DetectionRuleIds.PROXY_SELECTOR_QUERY
                });
        addFlow(report,
                SupplementalRuleIds.SERVICE_BINDER_FLOW,
                2,
                5000,
                new String[]{
                        DetectionRuleIds.SERVICE_MANAGER_QUERY,
                        DetectionRuleIds.SERVICE_LIST_QUERY,
                        SupplementalRuleIds.BINDER_DESCRIPTOR_QUERY,
                        SupplementalRuleIds.BINDER_TRANSACT_QUERY
                });
        addFlow(report,
                SupplementalRuleIds.UNIX_SOCKET_FLOW,
                2,
                5000,
                new String[]{
                        DetectionRuleIds.UNIX_SOCKET_QUERY,
                        DetectionRuleIds.MAGISK_UNIX_SOCKET_QUERY,
                        SupplementalRuleIds.NATIVE_UNIX_SOCKET_CONNECT
                });
    }

    public static void enrichRecommendations(DiagnosticReport report) {
        for (DiagnosticFinding finding : report.findings) {
            if (finding.attributionRank != 1 && finding.attributionRank != 2) continue;
            DetectionRuleDefinition def = SupplementalRuleCatalog.get(finding.ruleId);
            if (def == null) continue;

            finding.recommendations.clear();
            String role = finding.attributionRank == 1 ? "主要归因" : "次要归因";
            String why = "本项被标记为" + role
                    + "，规则 ID=" + finding.ruleId
                    + "，代表状态=" + finding.representativeState
                    + "，关联分数=" + finding.correlationScore + "/100。"
                    + (finding.closestDeltaMs == Long.MAX_VALUE
                    ? ""
                    : " 与退出最近相隔 " + finding.closestDeltaMs + " ms。")
                    + (finding.sameThreadAsExit ? " 代表事件与退出发生在同一线程。" : "")
                    + (finding.sharedExitFrames > 0
                    ? " 与退出栈共享 " + finding.sharedExitFrames + " 个业务帧。"
                    : "");

            finding.recommendations.add(new FixRecommendation(
                    role + "：" + def.title,
                    def.whyDetected,
                    def.projectExplanation,
                    why,
                    def.remediation,
                    def.reference
            ));
        }
    }

    private static void retitleSupplementalFindings(DiagnosticReport report) {
        for (DiagnosticFinding finding : report.findings) {
            DetectionRuleDefinition def = SupplementalRuleCatalog.get(finding.ruleId);
            if (def == null) continue;
            finding.category = def.category;
            finding.title = def.title;
            finding.summary = def.whyDetected + countSuffix(finding);
            finding.detail = finding.summary;
        }
    }

    private static String countSuffix(DiagnosticFinding finding) {
        if (finding.totalCount <= 0) return "";
        return "；本次运行 " + finding.totalCount + " 次"
                + "（HIT " + finding.hitCount
                + " / CHECKED " + finding.checkedCount
                + " / NOT_HIT " + finding.notHitCount
                + " / UNKNOWN " + finding.unknownCount + "）";
    }

    private static void addFlow(
            DiagnosticReport report,
            String flowRuleId,
            int minDistinctRules,
            long maxWindowMs,
            String[] components
    ) {
        if (hasFinding(report, flowRuleId)) return;

        List<DiagnosticFinding> matched = new ArrayList<>();
        for (DiagnosticFinding finding : report.findings) {
            for (String component : components) {
                if (component.equals(finding.ruleId)) {
                    matched.add(finding);
                    break;
                }
            }
        }
        if (matched.size() < minDistinctRules) return;

        long earliest = Long.MAX_VALUE;
        long latest = 0;
        int distinct = 0;
        List<String> seen = new ArrayList<>();
        DiagnosticFinding latestFinding = null;

        for (DiagnosticFinding finding : matched) {
            if (!seen.contains(finding.ruleId)) {
                seen.add(finding.ruleId);
                distinct++;
            }
            if (finding.closestEventTimestamp > 0) {
                earliest = Math.min(earliest, finding.closestEventTimestamp);
                if (finding.closestEventTimestamp >= latest) {
                    latest = finding.closestEventTimestamp;
                    latestFinding = finding;
                }
            }
        }

        if (distinct < minDistinctRules) return;
        if (earliest == Long.MAX_VALUE || latest <= 0 || latest - earliest > maxWindowMs) return;

        DetectionRuleDefinition def = SupplementalRuleCatalog.get(flowRuleId);
        if (def == null) return;

        DiagnosticFinding flow = new DiagnosticFinding(
                "runtime." + flowRuleId,
                def.category,
                def.title,
                DiagnosticStatus.DETECTED,
                def.whyDetected
        );
        flow.ruleId = flowRuleId;
        flow.representativeState = DetectionHitState.CHECKED;
        flow.totalCount = distinct;
        flow.checkedCount = distinct;
        flow.closestEventTimestamp = latest;
        flow.closestDeltaMs = report.lastExitTimestamp > 0 && latest <= report.lastExitTimestamp
                ? report.lastExitTimestamp - latest
                : Long.MAX_VALUE;
        flow.source = "YPowerSupplementalFlow";
        flow.input = "window=" + (latest - earliest) + "ms distinctRules=" + distinct;
        flow.result = "CHECKED";

        for (DiagnosticFinding item : matched) {
            flow.evidence(item.ruleId + " · " + item.title
                    + " · state=" + item.representativeState);
        }
        if (latestFinding != null) {
            flow.pid = latestFinding.pid;
        }
        report.findings.add(flow);
    }

    private static boolean hasFinding(DiagnosticReport report, String ruleId) {
        for (DiagnosticFinding finding : report.findings) {
            if (ruleId.equals(finding.ruleId)) return true;
        }
        return false;
    }
}
