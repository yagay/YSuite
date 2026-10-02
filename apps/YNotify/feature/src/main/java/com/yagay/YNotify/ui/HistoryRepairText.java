package com.yagay.YNotify.ui;

import android.content.Context;

import com.yagay.YNotify.data.HistoryRepairEngine;

final class HistoryRepairText {
    private HistoryRepairText() {}

    static String summary(Context context, HistoryRepairEngine.RepairReport report) {
        if (report == null) return "";
        boolean zh = context.getResources().getConfiguration().getLocales().get(0).getLanguage().equalsIgnoreCase("zh");
        if (!report.succeeded()) {
            return zh ? com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_76e6723dc54e) + report.error : "Repair failed: " + report.error;
        }
        StringBuilder out = new StringBuilder();
        if (zh) {
            out.append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_ee918ab7c27d)).append(report.scanned).append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d410d88f8154))
                    .append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_0d8672f9af65)).append(report.eventTypeChanged).append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d410d88f8154))
                    .append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d61fd687e483)).append(report.systemUiClassified).append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d410d88f8154))
                    .append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_a4f4ad23b54d)).append(report.notificationKindChanged).append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d410d88f8154))
                    .append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_56a84253e54d)).append(report.mergedUi).append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d410d88f8154))
                    .append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_38f877f8ffe6)).append(report.crossPackageHeadsUp).append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d410d88f8154))
                    .append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_b99e7464c42d)).append(report.mergedNotifications).append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d410d88f8154));
            if (report.ambiguousUi > 0) out.append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_0ad7a125e250)).append(report.ambiguousUi).append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d410d88f8154));
            if (report.recheckedMerged > 0) out.append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_3ebef972d88b)).append(report.recheckedMerged).append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d410d88f8154));
            if (report.protectedManual > 0) out.append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_c447ebe95bf0)).append(report.protectedManual).append(com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_d410d88f8154));
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
