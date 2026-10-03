package com.yagay.YMiniGuard;

import com.yagay.suite.api.FeatureServices;
import com.yagay.suite.api.HostBinaryCommandResult;
import java.nio.charset.StandardCharsets;

final class RootManager {
    private static final FeatureServices SERVICES = FeatureServices.of("yminiguard", "YMiniGuard");

    static final class RootStatus {
        final boolean granted;
        final String detail;

        RootStatus(boolean granted, String detail) {
            this.granted = granted;
            this.detail = detail;
        }
    }

    private RootManager() {}

    static RootStatus checkAccess() {
        String output = capture("id", 4096);
        boolean granted = output.startsWith("[exit=0]") && output.contains("uid=0");
        return new RootStatus(granted, output);
    }

    static boolean run(String command, StringBuilder detail) {
        String output = capture(command, 8192);
        boolean ok = output.startsWith("[exit=0]");
        if (detail != null) detail.append(output);
        return ok;
    }

    static String capture(String command, int maxChars) {
        try {
            if (SERVICES.hostOrNull() == null) {
                return "[exception=java.lang.IllegalStateException] Managed Root host is not attached\n";
            }
            int maxBytes = Math.max(4096, Math.min(Integer.MAX_VALUE / 4, maxChars) * 4);
            HostBinaryCommandResult result = SERVICES.rootBinary(
                    "root-manager",
                    command,
                    20L,
                    maxBytes,
                    true);
            String body = new String(result.getStdout(), StandardCharsets.UTF_8);
            if (!result.getStderr().isBlank()) {
                if (!body.isBlank()) body += "\n";
                body += result.getStderr();
            }
            if (result.getErrorMessage() != null && !result.getErrorMessage().isBlank()) {
                if (!body.isBlank()) body += "\n";
                body += "[" + result.getErrorMessage() + "]";
            }
            if (body.length() > maxChars) body = body.substring(0, maxChars);
            if (result.getTimedOut()) return "[timeout]\n" + body;
            return "[exit=" + result.getCode() + "]\n" + body;
        } catch (Throwable t) {
            return "[exception=" + t.getClass().getName() + "] Managed Root: "
                    + t.getMessage() + "\n";
        }
    }

    static String quote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
