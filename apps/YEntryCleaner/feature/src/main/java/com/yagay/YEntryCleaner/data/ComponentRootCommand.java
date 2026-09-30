package com.yagay.YEntryCleaner.data;

import com.yagay.YEntryCleaner.ui.DiagnosticBuffer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** One explicit user action per invocation; bounded output/time, no persistent root daemon. */
public final class ComponentRootCommand {
    private static final String SUITE_ROOT_GATEWAY = "com.yagay.suite.core.SuiteRootGateway";
    private static final String PLUGIN_ID = "yentrycleaner";

    private ComponentRootCommand() {}

    public enum RootFailureReason {
        UNAVAILABLE,
        TIMEOUT,
        DENIED
    }

    public static final class RootAccessException extends IllegalStateException {
        public final RootFailureReason reason;

        RootAccessException(RootFailureReason reason) {
            super(reason.name());
            this.reason = reason;
        }

        RootAccessException(RootFailureReason reason, Throwable cause) {
            super(reason.name(), cause);
            this.reason = reason;
        }
    }

    /** Read-only check for each explicit batch; do not cache authorization across actions. */
    public static void requireRoot() {
        final Result result;
        try {
            result = run("test \"$(id -u)\" = 0");
        } catch (Exception failure) {
            throw new RootAccessException(RootFailureReason.UNAVAILABLE, failure);
        }
        verifyRoot(result);
    }

    static void verifyRoot(Result result) {
        if (result.timedOut) {
            throw new RootAccessException(RootFailureReason.TIMEOUT);
        }
        if (result.exitCode != 0) {
            throw new RootAccessException(RootFailureReason.DENIED);
        }
    }

    public static final class Result {
        public final int exitCode;
        public final boolean timedOut;
        public final String output;
        Result(int code, boolean timeout, String text) { exitCode = code; timedOut = timeout; output = text; }
    }

    public static Result run(String script) throws Exception {
        Class<?> host = hostGateway();
        if (host != null) return runThroughHost(host, script);
        return capture(new ProcessBuilder("su", "-c", script), 25);
    }

    private static Class<?> hostGateway() {
        try {
            return Class.forName(SUITE_ROOT_GATEWAY, false, ComponentRootCommand.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Result runThroughHost(Class<?> gateway, String script) throws Exception {
        try {
            Method method = gateway.getMethod(
                    "executeBinaryFromPlugin",
                    String.class,
                    String.class,
                    String.class,
                    long.class,
                    int.class,
                    boolean.class);
            Object raw = method.invoke(
                    null,
                    PLUGIN_ID,
                    "component-root",
                    script == null ? "" : script,
                    25L,
                    16 * 1024,
                    true);
            if (raw == null) throw new IllegalStateException("YSuite root gateway returned null");
            Class<?> type = raw.getClass();
            int code = ((Number) type.getMethod("getCode").invoke(raw)).intValue();
            byte[] stdout = (byte[]) type.getMethod("getStdout").invoke(raw);
            String stderr = (String) type.getMethod("getStderr").invoke(raw);
            boolean timedOut = Boolean.TRUE.equals(type.getMethod("getTimedOut").invoke(raw));
            String errorMessage = (String) type.getMethod("getErrorMessage").invoke(raw);
            StringBuilder text = new StringBuilder(new String(stdout, StandardCharsets.UTF_8));
            if (stderr != null && !stderr.isBlank()) {
                if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') text.append('\n');
                text.append(stderr);
            }
            if (errorMessage != null && !errorMessage.isBlank()) {
                if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') text.append('\n');
                text.append('[').append(errorMessage).append(']');
            }
            return new Result(code, timedOut, text.toString());
        } catch (Throwable error) {
            // The host is present, therefore a failed host request must not open a second su entry.
            throw new IllegalStateException("YSuite root gateway failed", error);
        }
    }

    public static Result capture(ProcessBuilder builder, int seconds) throws Exception {
        DiagnosticBuffer buffer = new DiagnosticBuffer(16 * 1024);
        AtomicReference<Exception> error = new AtomicReference<>();
        Process process = builder.redirectErrorStream(true).start();
        Thread reader = new Thread(() -> {
            try (java.io.InputStream in = process.getInputStream()) {
                byte[] bytes = new byte[2048]; int size;
                while ((size = in.read(bytes)) >= 0) buffer.append(bytes, size);
            } catch (Exception failure) { error.set(failure); }
        }, "component-root-output");
        reader.setDaemon(true);
        reader.start();
        try {
            boolean finished = process.waitFor(seconds, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            reader.join(1000);
            String text = new String(buffer.snapshot(), StandardCharsets.UTF_8);
            if (buffer.truncated()) text += "\n[output truncated]";
            if (reader.isAlive() || error.get() != null) text += "\n[output incomplete]";
            return new Result(finished ? process.exitValue() : -1, !finished, text);
        } finally {
            process.destroy();
            try { process.getInputStream().close(); } catch (Exception ignored) { }
            try { process.getOutputStream().close(); } catch (Exception ignored) { }
            try { process.getErrorStream().close(); } catch (Exception ignored) { }
        }
    }
}
