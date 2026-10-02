package com.yagay.ypower.diag;

import com.yagay.ypower.hook.SupplementalRuleIds;
import com.yagay.ypower.model.DetectionHitState;
import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.DiagnosticStatus;
import com.yagay.ypower.root.RootShell;
import com.yagay.ypower.util.ShellEscaper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class StaticApiReferenceScanner {
    private static final int CHUNK = 64 * 1024;
    private static final int OVERLAP = 256;
    private static final int MAX_EVIDENCE_PER_RULE = 10;

    private static final Map<String, String[]> API_PATTERNS = new LinkedHashMap<>();

    static {
        API_PATTERNS.put(SupplementalRuleIds.STATIC_DEX_BUILD_REF, new String[]{
                "Landroid/os/Build;", "FINGERPRINT", "HARDWARE", "SUPPORTED_ABIS"
        });
        API_PATTERNS.put(SupplementalRuleIds.STATIC_DEX_DEBUG_REF, new String[]{
                "Landroid/os/Debug;", "isDebuggerConnected"
        });
        API_PATTERNS.put(SupplementalRuleIds.STATIC_DEX_INTEGRITY_REF, new String[]{
                "com/google/android/play/core/integrity", "setAttestationChallenge",
                "AndroidKeyStore", "KeyGenParameterSpec"
        });
        API_PATTERNS.put(SupplementalRuleIds.STATIC_DEX_VIRTUALIZATION_REF, new String[]{
                "Landroid/opengl/GLES20;", "Landroid/opengl/GLES30;", "eglQueryString"
        });
        API_PATTERNS.put(SupplementalRuleIds.STATIC_DEX_BINDER_REF, new String[]{
                "Landroid/os/ServiceManager;", "Landroid/os/IBinder;", "BinderProxy"
        });
        API_PATTERNS.put(SupplementalRuleIds.STATIC_DEX_SOCKET_REF, new String[]{
                "Landroid/net/LocalSocket;", "LocalSocketAddress"
        });
    }

    private StaticApiReferenceScanner() {}

    public static void scan(DiagnosticReport report) {
        if (!RootShell.isRootAvailable()) return;
        try {
            List<String> apkPaths = resolveApkPaths(report.packageName);
            if (apkPaths.isEmpty()) return;

            Map<String, Set<String>> hits = new LinkedHashMap<>();
            for (String id : API_PATTERNS.keySet()) hits.put(id, new LinkedHashSet<>());
            for (String apk : apkPaths) scanApk(apk, hits);

            for (Map.Entry<String, Set<String>> entry : hits.entrySet()) {
                if (entry.getValue().isEmpty() || hasStaticEvidence(report, entry.getKey())) continue;
                DetectionRuleDefinition def = SupplementalRuleCatalog.get(entry.getKey());
                if (def == null) continue;

                DiagnosticFinding finding = new DiagnosticFinding(
                        "static." + entry.getKey(),
                        "static_evidence",
                        def.title,
                        DiagnosticStatus.DETECTED,
                        def.whyDetected
                );
                finding.ruleId = entry.getKey();
                finding.representativeState = DetectionHitState.CHECKED;
                finding.totalCount = entry.getValue().size();
                finding.checkedCount = finding.totalCount;
                finding.source = "DEX framework API scanner";
                finding.result = "STATIC_ONLY";
                finding.summary = def.whyDetected
                        + com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_dynamic_8cf121f771d2);
                for (String evidence : entry.getValue()) finding.evidence(evidence);
                report.staticEvidence.add(finding);
                report.raw.add("[static-api auxiliary] " + entry.getKey() + " "
                        + String.join(", ", entry.getValue()));
            }
        } catch (Throwable ignored) {
        }
    }

    private static List<String> resolveApkPaths(String packageName) {
        List<String> result = new ArrayList<>();
        String out = RootShell.exec(
                "pm path " + ShellEscaper.q(packageName) + " 2>/dev/null || true"
        ).text();
        for (String line : out.split("\\R")) {
            String value = line.trim();
            if (value.startsWith("package:")) value = value.substring("package:".length());
            if (value.endsWith(".apk") && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private static boolean hasStaticEvidence(DiagnosticReport report, String ruleId) {
        for (DiagnosticFinding finding : report.staticEvidence) {
            if (ruleId.equals(finding.ruleId)) return true;
        }
        return false;
    }

    private static void scanApk(String apkPath, Map<String, Set<String>> hits) {
        File file = new File(apkPath);
        if (!file.isFile()) return;
        try (ZipFile zip = new ZipFile(file)) {
            zip.stream()
                    .filter(e -> !e.isDirectory())
                    .filter(e -> e.getName() != null
                            && e.getName().startsWith("classes")
                            && e.getName().endsWith(".dex"))
                    .forEach(entry -> scanDex(zip, file.getName(), entry, hits));
        } catch (Throwable ignored) {
        }
    }

    private static void scanDex(
            ZipFile zip,
            String apkName,
            ZipEntry entry,
            Map<String, Set<String>> hits
    ) {
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] buffer = new byte[CHUNK];
            byte[] tail = new byte[0];
            int read;
            while ((read = in.read(buffer)) > 0) {
                ByteArrayOutputStream merged = new ByteArrayOutputStream(tail.length + read);
                merged.write(tail);
                merged.write(buffer, 0, read);
                byte[] data = merged.toByteArray();
                String lower = new String(data, StandardCharsets.ISO_8859_1).toLowerCase();

                for (Map.Entry<String, String[]> rule : API_PATTERNS.entrySet()) {
                    Set<String> found = hits.get(rule.getKey());
                    if (found == null || found.size() >= MAX_EVIDENCE_PER_RULE) continue;
                    for (String pattern : rule.getValue()) {
                        if (found.size() >= MAX_EVIDENCE_PER_RULE) break;
                        if (lower.contains(pattern.toLowerCase())) {
                            found.add(apkName + "!" + entry.getName() + " → " + pattern);
                        }
                    }
                }

                int keep = Math.min(OVERLAP, data.length);
                tail = new byte[keep];
                System.arraycopy(data, data.length - keep, tail, 0, keep);
            }
        } catch (Throwable ignored) {
        }
    }
}
