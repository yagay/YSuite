package com.yagay.ypower.model;


public enum DiagnosticStatus {
    DETECTED("Detected", com.yagay.ypower.R.string.ypower_generated_e71233fe898b),
    PASS("Pass", com.yagay.ypower.R.string.ypower_generated_e0561a48579f),
    FAIL("Fail", com.yagay.ypower.R.string.ypower_generated_7d29ab0fc30a),
    WARN("Warning", com.yagay.ypower.R.string.ypower_generated_406dd7912457),
    UNKNOWN("Unknown", com.yagay.ypower.R.string.ypower_generated_87f340e4520f);

    public final String code;
    private final int labelRes;

    DiagnosticStatus(String code, int labelRes) {
        this.code = code;
        this.labelRes = labelRes;
    }

    public String label() {
        return com.yagay.suite.api.YLocale.text(labelRes);
    }
}
