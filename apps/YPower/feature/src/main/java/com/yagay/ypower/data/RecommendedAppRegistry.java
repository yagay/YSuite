package com.yagay.ypower.data;

import com.yagay.ypower.model.RecommendedAppPreset;

import java.util.List;

public final class RecommendedAppRegistry {
    private RecommendedAppRegistry() {}

    private static RecommendedAppPreset diagnosticMedia(String pkg, String name, String reason) {
        return new RecommendedAppPreset(
                pkg, name, reason,
                true, true, true, true,
                false, false,
                true, true, true, true, true, true, true, true, true
        );
    }

    public static final List<RecommendedAppPreset> BUILTIN = List.of(
            diagnosticMedia(
                    "com.ss.android.ugc.aweme",
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9a3e24de1595),
                    "适合做环境/退出诊断；默认只记录敏感检测行为，不修改检测结果。"
            ),
            diagnosticMedia(
                    "com.ss.android.ugc.aweme.lite",
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_74d7198ecae6),
                    "适合做环境/退出诊断；默认只记录敏感检测行为，不修改检测结果。"
            ),
            diagnosticMedia(
                    "com.phoenix.read",
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1e9abd12c156),
                    "适合分析后台、环境检测和主动退出链；默认只启用诊断型 Hook。"
            ),
            diagnosticMedia(
                    "com.phoenix.read.oversea.gp",
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_25e5d70742d4),
                    "适合分析后台、环境检测和主动退出链；默认只启用诊断型 Hook。"
            )
    );

    public static RecommendedAppPreset find(String packageName) {
        for (RecommendedAppPreset preset : BUILTIN) {
            if (preset.packageName.equals(packageName)) return preset;
        }
        return null;
    }
}
