package com.yagay.ypower.diag;

import com.yagay.ypower.hook.SupplementalRuleIds;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SupplementalRuleCatalog {
    private static final Map<String, DetectionRuleDefinition> RULES = new LinkedHashMap<>();

    static {
        add(SupplementalRuleIds.NATIVE_UNIX_SOCKET_CONNECT, "socket",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6b297dc968b5),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_449c4279f337),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a0ecc5494039),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0025b7c7258c),
                "Linux AF_UNIX / RootBeerFresh style diagnostics");
        add(SupplementalRuleIds.NATIVE_ROOT_UNIX_SOCKET_CONNECT, "root",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c0bbc1802dc2),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7e659d550ea8),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_63c037bd61c8),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a7be7dbb2c38),
                "RootBeerFresh / Unix socket diagnostics");

        add(SupplementalRuleIds.BINDER_DESCRIPTOR_QUERY, "service",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9ee39887b413),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b6d24895cc93),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8788a5597df4),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c19d0da5a094),
                "Android Binder / ServiceManager diagnostics");
        add(SupplementalRuleIds.BINDER_TRANSACT_QUERY, "service",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7b9e049f5d70),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cf7ea4d318a4),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1ba78aaee9cd),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_742d14cf72b2),
                "Android Binder IPC");

        addStatic(SupplementalRuleIds.STATIC_DEX_BUILD_REF, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b267e82e14fa),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_03579d0d8a64),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_30814ea5e7ec));
        addStatic(SupplementalRuleIds.STATIC_DEX_DEBUG_REF, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8e53f976b02e),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7231161abd0a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_06134d3bbad4));
        addStatic(SupplementalRuleIds.STATIC_DEX_ROOT_REF, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_964c52b1d717),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e87f2d892dad),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b01871fe2d14));
        addStatic(SupplementalRuleIds.STATIC_DEX_INTEGRITY_REF, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_fbad819bd57d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ea112ebbed48),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cba90bea3fec));
        addStatic(SupplementalRuleIds.STATIC_DEX_VIRTUALIZATION_REF, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9914087d4772),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_29fb4da4c442),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_59c7a7b5f039));
        addStatic(SupplementalRuleIds.STATIC_DEX_BINDER_REF, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3ed821b6035a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_31806c2100e3),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_633f2cfc751e));
        addStatic(SupplementalRuleIds.STATIC_DEX_SOCKET_REF, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d0103d5c765f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_12f93452cc40),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_20c5d94a9fc9));

        add(SupplementalRuleIds.VIRTUALIZATION_FLOW, "virtualization",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_623e81e02f3d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0106ff971dc0),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1a3ae02e7817),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d247611cf6ee),
                "DuckDetector / emulator diagnostics");
        add(SupplementalRuleIds.NETWORK_ENVIRONMENT_FLOW, "network",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7b36cf2abc20),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4ab571611383),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_38c2bb465168),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_717a6c92b973),
                "Android NetworkCapabilities / Proxy diagnostics");
        add(SupplementalRuleIds.SERVICE_BINDER_FLOW, "service",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5e489f1556f2),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c38d0bb4a95f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ad9772029d13),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_516295f3079c),
                "Android Binder / ServiceManager");
        add(SupplementalRuleIds.UNIX_SOCKET_FLOW, "socket",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8273e085429a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cd88f7302597),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a8833ce318da),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7346c23e569f),
                "Android LocalSocket / Linux AF_UNIX");
    }

    private SupplementalRuleCatalog() {}

    public static DetectionRuleDefinition get(String id) {
        return id == null ? null : RULES.get(id);
    }

    private static void addStatic(String id, String title, String why, String project) {
        add(id, "static_evidence", title, why, project,
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7abffa6acccb),
                "YPower DEX reference scanner");
    }

    private static void add(
            String id,
            String category,
            String title,
            String whyDetected,
            String projectExplanation,
            String remediation,
            String reference
    ) {
        RULES.put(id, new DetectionRuleDefinition(
                id, category, title, whyDetected, projectExplanation, remediation, reference
        ));
    }
}
