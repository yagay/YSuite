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
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_dynamic_07d633b9c57c)
            ),
            diagnosticMedia(
                    "com.ss.android.ugc.aweme.lite",
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_74d7198ecae6),
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_dynamic_07d633b9c57c)
            ),
            diagnosticMedia(
                    "com.phoenix.read",
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1e9abd12c156),
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_dynamic_4f8d6e845461)
            ),
            diagnosticMedia(
                    "com.phoenix.read.oversea.gp",
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_25e5d70742d4),
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_dynamic_4f8d6e845461)
            )
    );

    public static RecommendedAppPreset find(String packageName) {
        for (RecommendedAppPreset preset : BUILTIN) {
            if (preset.packageName.equals(packageName)) return preset;
        }
        return null;
    }
}
