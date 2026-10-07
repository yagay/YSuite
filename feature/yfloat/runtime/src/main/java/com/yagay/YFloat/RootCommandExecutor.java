package com.yagay.YFloat;

import com.yagay.suite.api.FeatureServices;
import com.yagay.suite.api.HostBinaryCommandResult;

import java.nio.charset.StandardCharsets;

/** One bounded, host-owned implementation for every YFloat Root command. */
final class RootCommandExecutor {
    private static final FeatureServices SERVICES = FeatureServices.of("yfloat", "YFloat");

    static final class Result {
        final int exitCode;
        final byte[] stdout;
        final String stderr;
        final Throwable error;
        final boolean timedOut;

        Result(int exitCode, byte[] stdout, String stderr, Throwable error, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? new byte[0] : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.error = error;
            this.timedOut = timedOut;
        }

        boolean success() { return !timedOut && error == null && exitCode == 0; }

        String text() {
            return new String(stdout, StandardCharsets.UTF_8).trim();
        }

        String failureMessage(String timeoutMessage) {
            if (timedOut) return timeoutMessage == null ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_a569c77d0225) : timeoutMessage;
            if (error != null) {
                String message = error.getMessage();
                return message == null || message.isBlank()
                        ? error.getClass().getSimpleName() : message;
            }
            if (!stderr.isBlank()) return stderr.trim();
            return "root exit=" + exitCode;
        }
    }

    static Result runText(String command, long timeoutSeconds, int maxOutputBytes) {
        return run(command, timeoutSeconds, Math.max(1024, maxOutputBytes), true);
    }

    static Result runBinary(String command, long timeoutSeconds, int maxOutputBytes) {
        return run(command, timeoutSeconds, Math.max(1024, maxOutputBytes), false);
    }

    private static Result run(String command,
                              long timeoutSeconds,
                              int maxStdoutBytes,
                              boolean mergeError) {
        try {
            if (SERVICES.hostOrNull() == null) {
                return new Result(
                        -1,
                        new byte[0],
                        "Managed Root host is not attached",
                        new IllegalStateException("Managed Root host is not attached"),
                        false);
            }
            HostBinaryCommandResult raw = SERVICES.rootBinary(
                    "root-command",
                    command == null ? "" : command,
                    timeoutSeconds,
                    maxStdoutBytes,
                    mergeError);
            String errorMessage = raw.getErrorMessage();
            Throwable error = errorMessage == null || errorMessage.isBlank()
                    ? null
                    : new IllegalStateException(errorMessage);
            return new Result(
                    raw.getCode(),
                    raw.getStdout(),
                    raw.getStderr(),
                    error,
                    raw.getTimedOut());
        } catch (Throwable t) {
            return new Result(-1, new byte[0], "", t, false);
        }
    }

    private RootCommandExecutor() {}
}
