package com.yagay.ypower.model;

import java.util.Locale;

public enum DiagnosticStatus {
    DETECTED("Detected", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e71233fe898b)),
    PASS("Pass", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e0561a48579f)),
    FAIL("Fail", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7d29ab0fc30a)),
    WARN("Warning", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_406dd7912457)),
    UNKNOWN("Unknown", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_87f340e4520f));

    public final String code;
    private final String zh;

    DiagnosticStatus(String code, String zh) {
        this.code = code;
        this.zh = zh;
    }

    public String label() {
        return "zh".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? zh : code;
    }
}
