package com.yagay.YEntryCleaner.data;

import com.yagay.suite.api.FeatureServices;
import com.yagay.suite.api.HostBinaryCommandResult;
import java.nio.charset.StandardCharsets;

/** One explicit user action per invocation; bounded output/time, no feature-owned Root process. */
public final class ComponentRootCommand {
    private static final FeatureServices SERVICES = FeatureServices.of("yentrycleaner", "YEntryCleaner");

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

    /** Read-only check for each explicit batch; authorization is owned by the shared Root host. */
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

    public static Result run(String script) {
        return runThroughHost(script);
    }

    private static Result runThroughHost(String script) {
        try {
            if (SERVICES.hostOrNull() == null) {
                throw new IllegalStateException("Managed Root host is not attached");
            }
            HostBinaryCommandResult raw = SERVICES.rootBinary(
                    "component-root",
                    script == null ? "" : script,
                    25L,
                    16 * 1024,
                    true);
            StringBuilder text = new StringBuilder(new String(raw.getStdout(), StandardCharsets.UTF_8));
            if (!raw.getStderr().isBlank()) {
                if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') text.append('\n');
                text.append(raw.getStderr());
            }
            if (raw.getErrorMessage() != null && !raw.getErrorMessage().isBlank()) {
                if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') text.append('\n');
                text.append('[').append(raw.getErrorMessage()).append(']');
            }
            return new Result(raw.getCode(), raw.getTimedOut(), text.toString());
        } catch (Throwable error) {
            throw new IllegalStateException("Managed Root host failed", error);
        }
    }
}
