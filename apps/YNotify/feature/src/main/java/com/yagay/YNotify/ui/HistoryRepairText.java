package com.yagay.YNotify.ui;

import android.content.Context;

import com.yagay.YNotify.R;

import com.yagay.YNotify.data.HistoryRepairEngine;

final class HistoryRepairText {
    private HistoryRepairText() {}

    static String summary(Context context, HistoryRepairEngine.RepairReport report) {
        if (report == null) return "";
        if (!report.succeeded()) {
            return context.getString(R.string.ynotify_repair_failed, report.error);
        }
        StringBuilder out = new StringBuilder(context.getString(
                R.string.ynotify_repair_summary,
                report.scanned,
                report.eventTypeChanged,
                report.systemUiClassified,
                report.notificationKindChanged,
                report.mergedUi,
                report.crossPackageHeadsUp,
                report.mergedNotifications));
        if (report.ambiguousUi > 0) {
            out.append(' ').append(context.getString(R.string.ynotify_repair_ambiguous, report.ambiguousUi));
        }
        if (report.recheckedMerged > 0) {
            out.append(' ').append(context.getString(R.string.ynotify_repair_rechecked, report.recheckedMerged));
        }
        if (report.protectedManual > 0) {
            out.append(' ').append(context.getString(R.string.ynotify_repair_protected, report.protectedManual));
        }
        return out.toString().trim();
    }
}
