package com.yagay.YNotify.ui;

import android.content.Context;

import com.yagay.YNotify.data.HistoryRepairEngine;

final class HistoryRepairText {
    private HistoryRepairText() {}

    static String summary(Context context, HistoryRepairEngine.RepairReport report) {
        if (report == null) return "";
        boolean zh = context.getResources().getConfiguration().getLocales().get(0).getLanguage().equalsIgnoreCase("zh");
        if (!report.succeeded()) {
            return zh ? "修复失败：" + report.error : "Repair failed: " + report.error;
        }
        StringBuilder out = new StringBuilder();
        if (zh) {
            out.append("扫描 ").append(report.scanned).append(" 条。")
                    .append("类型纠正 ").append(report.eventTypeChanged).append(" 条。")
                    .append("SystemUI 分类 ").append(report.systemUiClassified).append(" 条。")
                    .append("通知子类型纠正 ").append(report.notificationKindChanged).append(" 条。")
                    .append("横幅关联 ").append(report.mergedUi).append(" 条。")
                    .append("跨应用横幅 ").append(report.crossPackageHeadsUp).append(" 条。")
                    .append("重复通知合并 ").append(report.mergedNotifications).append(" 条。");
            if (report.ambiguousUi > 0) out.append("证据不足 ").append(report.ambiguousUi).append(" 条。");
            if (report.recheckedMerged > 0) out.append("重新检查旧合并 ").append(report.recheckedMerged).append(" 条。");
            if (report.protectedManual > 0) out.append("保留手动分类 ").append(report.protectedManual).append(" 条。");
        } else {
            out.append("Scanned ").append(report.scanned).append(" records. ")
                    .append("Corrected event type for ").append(report.eventTypeChanged).append(" records. ")
                    .append("Classified ").append(report.systemUiClassified).append(" SystemUI records. ")
                    .append("Corrected notification subtype for ").append(report.notificationKindChanged).append(" records. ")
                    .append("Linked ").append(report.mergedUi).append(" heads-up records. ")
                    .append("Linked ").append(report.crossPackageHeadsUp).append(" cross-app heads-up records. ")
                    .append("Merged ").append(report.mergedNotifications).append(" duplicate notifications. ");
            if (report.ambiguousUi > 0) out.append(report.ambiguousUi).append(" records had insufficient evidence. ");
            if (report.recheckedMerged > 0) out.append("Rechecked ").append(report.recheckedMerged).append(" previous merges. ");
            if (report.protectedManual > 0) out.append("Preserved ").append(report.protectedManual).append(" manual classifications. ");
        }
        return out.toString().trim();
    }
}
