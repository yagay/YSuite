package com.yagay.ypower.root;

import com.yagay.suite.api.FeatureHost;
import com.yagay.suite.api.FeatureServices;
import com.yagay.suite.api.HostCapability;
import com.yagay.suite.api.HostCapabilityState;
import com.yagay.suite.api.HostCommandResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Root facade owned by YPower business code; the actual shell is always owned by FeatureHost. */
public final class RootShell {
    private static final FeatureServices SERVICES = FeatureServices.of("ypower", "YPower");

    private RootShell() {}

    public static boolean isRootAvailable() {
        return SERVICES.capabilityState(HostCapability.ROOT) == HostCapabilityState.GRANTED;
    }

    public static CommandResult exec(String... commands) {
        FeatureHost current = SERVICES.hostOrNull();
        if (current == null) {
            return failure("Host Root capability is not attached");
        }
        if (!current.supports(HostCapability.ROOT)) {
            return failure("ROOT capability is not declared for YPower");
        }

        try {
            String command = String.join("\n", commands == null ? new String[0] : commands);
            HostCommandResult result = current.rootExecute("root-shell", command, 20L);
            return new CommandResult(
                    result.getCode(),
                    splitLines(result.getStdout()),
                    splitLines(result.getStderr().isBlank()
                            ? (result.getErrorMessage() == null ? "" : result.getErrorMessage())
                            : result.getStderr()));
        } catch (Throwable error) {
            return failure("Host Root execution failed: " + error);
        }
    }

    private static CommandResult failure(String message) {
        return new CommandResult(
                -1,
                new ArrayList<>(),
                new ArrayList<>(Collections.singletonList(message)));
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(value.split("\\R")));
    }

    public static final class CommandResult {
        public final int code;
        public final List<String> out;
        public final List<String> err;

        public CommandResult(int code, List<String> out, List<String> err) {
            this.code = code;
            this.out = out;
            this.err = err;
        }

        public boolean ok() { return code == 0; }

        public String text() {
            StringBuilder b = new StringBuilder();
            for (String s : out) b.append(s).append('\n');
            for (String s : err) b.append(s).append('\n');
            return b.toString().trim();
        }
    }
}
