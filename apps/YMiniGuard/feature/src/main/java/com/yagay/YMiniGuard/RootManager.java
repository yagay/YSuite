package com.yagay.YMiniGuard;

import com.yagay.suite.api.FeatureHost;
import com.yagay.suite.api.FeatureHostRegistry;
import com.yagay.suite.api.HostBinaryCommandResult;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class RootManager {
    private static final String PLUGIN_ID = "yminiguard";

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
        FeatureHost host = FeatureHostRegistry.find(PLUGIN_ID);
        if (host != null) return captureThroughHost(host, command, maxChars);

        java.lang.Process process = null;
        StringBuilder out = new StringBuilder();

        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (out.length() < maxChars) {
                        int remain = maxChars - out.length();
                        String append = line.length() > remain
                                ? line.substring(0, remain)
                                : line;
                        out.append(append).append('\n');
                    }
                }
            }

            int exit = process.waitFor();
            String body = out.toString();
            if (body.length() > maxChars) body = body.substring(0, maxChars);
            return "[exit=" + exit + "]\n" + body;
        } catch (Throwable t) {
            return "[exception=" + t.getClass().getName() + "] "
                    + t.getMessage() + "\n" + out;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String captureThroughHost(FeatureHost host, String command, int maxChars) {
        try {
            int maxBytes = Math.max(4096, Math.min(Integer.MAX_VALUE / 4, maxChars) * 4);
            HostBinaryCommandResult result = host.rootExecuteBinary(
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
            // Host exists: never bypass YSuite with a second local su process.
            return "[exception=" + t.getClass().getName() + "] YSuite Root host: "
                    + t.getMessage() + "\n";
        }
    }

    static String quote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
