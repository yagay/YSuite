package com.yagay.ypower.model;

public final class RecommendedAppPreset {
    public final String packageName;
    public final String displayName;
    public final String reason;

    public final boolean dozeWhitelist;
    public final boolean backgroundOps;
    public final boolean standbyActive;
    public final boolean backgroundData;

    public final boolean simulateSystemApp;
    public final boolean simulatePermissions;

    public final boolean tracePackageScan;
    public final boolean traceFiles;
    public final boolean traceCommands;
    public final boolean traceProperties;
    public final boolean tracePermissions;
    public final boolean traceDebugger;
    public final boolean traceExceptions;
    public final boolean traceSecurityApis;
    public final boolean traceStacks;

    public RecommendedAppPreset(
            String packageName,
            String displayName,
            String reason,
            boolean dozeWhitelist,
            boolean backgroundOps,
            boolean standbyActive,
            boolean backgroundData,
            boolean simulateSystemApp,
            boolean simulatePermissions,
            boolean tracePackageScan,
            boolean traceFiles,
            boolean traceCommands,
            boolean traceProperties,
            boolean tracePermissions,
            boolean traceDebugger,
            boolean traceExceptions,
            boolean traceSecurityApis,
            boolean traceStacks
    ) {
        this.packageName = packageName;
        this.displayName = displayName;
        this.reason = reason;
        this.dozeWhitelist = dozeWhitelist;
        this.backgroundOps = backgroundOps;
        this.standbyActive = standbyActive;
        this.backgroundData = backgroundData;
        this.simulateSystemApp = simulateSystemApp;
        this.simulatePermissions = simulatePermissions;
        this.tracePackageScan = tracePackageScan;
        this.traceFiles = traceFiles;
        this.traceCommands = traceCommands;
        this.traceProperties = traceProperties;
        this.tracePermissions = tracePermissions;
        this.traceDebugger = traceDebugger;
        this.traceExceptions = traceExceptions;
        this.traceSecurityApis = traceSecurityApis;
        this.traceStacks = traceStacks;
    }

    public String hookSummary() {
        StringBuilder b = new StringBuilder();
        if (tracePackageScan) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_494fc333ccb2));
        if (traceFiles) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3103e8e64678));
        if (traceCommands) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_26394c4aba74));
        if (traceProperties) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_95f96608c2c4));
        if (tracePermissions) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6fafdf537988));
        if (traceDebugger) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e32e081fee63));
        if (traceExceptions) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c4703a1b1c6c));
        if (traceSecurityApis) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ee11287b0419));
        if (simulateSystemApp) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_457a46fb5c7d));
        if (simulatePermissions) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d604c31a0920));
        if (traceStacks) append(b, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d46c20543e5a));
        return b.length() == 0 ? com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1894ca1044e6) : b.toString();
    }

    private static void append(StringBuilder b, String text) {
        if (b.length() > 0) b.append("、");
        b.append(text);
    }
}
