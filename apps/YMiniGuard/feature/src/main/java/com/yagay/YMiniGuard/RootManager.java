package com.yagay.YMiniGuard;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

final class RootManager {
    private static final String SUITE_ROOT_GATEWAY = "com.yagay.suite.core.SuiteRootGateway";
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
        Class<?> host = hostGateway();
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

    private static Class<?> hostGateway() {
        try {
            return Class.forName(SUITE_ROOT_GATEWAY, false, RootManager.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String captureThroughHost(Class<?> gateway, String command, int maxChars) {
        try {
            Method method = gateway.getMethod(
                    "executeFromPlugin",
                    String.class,
                    String.class,
                    String.class,
                    long.class);
            Object raw = method.invoke(null, PLUGIN_ID, "root-manager", command, 20L);
            if (raw == null) throw new IllegalStateException("YSuite root gateway returned null");
            Class<?> type = raw.getClass();
            int code = ((Number) type.getMethod("getCode").invoke(raw)).intValue();
            String stdout = (String) type.getMethod("getStdout").invoke(raw);
            String stderr = (String) type.getMethod("getStderr").invoke(raw);
            boolean timedOut = Boolean.TRUE.equals(type.getMethod("getTimedOut").invoke(raw));
            String body = (stdout == null ? "" : stdout);
            if (stderr != null && !stderr.isBlank()) {
                if (!body.isBlank()) body += "\n";
                body += stderr;
            }
            if (body.length() > maxChars) body = body.substring(0, maxChars);
            if (timedOut) return "[timeout]\n" + body;
            return "[exit=" + code + "]\n" + body;
        } catch (Throwable t) {
            // Host exists: never bypass YSuite with a second local su process.
            return "[exception=" + t.getClass().getName() + "] YSuite root gateway: "
                    + t.getMessage() + "\n";
        }
    }

    static String quote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
