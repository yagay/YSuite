package com.yagay.ypower.model;

import java.util.Locale;

public enum DiagnosticStatus {
    DETECTED("Detected", "检测到"),
    PASS("Pass", "通过"),
    FAIL("Fail", "异常"),
    WARN("Warning", "警告"),
    UNKNOWN("Unknown", "未知");

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
