package com.yagay.ypower.root;

import com.topjohnwu.superuser.Shell;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RootShell {
    private static final String SUITE_ROOT_GATEWAY = "com.yagay.suite.core.SuiteRootGateway";
    private static final String PLUGIN_ID = "ypower";

    private RootShell() {}

    public static boolean isRootAvailable() {
        Class<?> host = hostGateway();
        if (host != null) {
            CommandResult result = execThroughHost(host, new String[]{"id -u"});
            return result.ok() && result.out.stream().anyMatch(line -> "0".equals(line.trim()));
        }

        try {
            Shell shell = Shell.getShell();
            boolean granted = shell.isRoot();
            // libsu caches its process-wide main shell. Do not keep a denied/non-root shell,
            // otherwise granting YPower standalone in KernelSU later can remain invisible here.
            if (!granted) {
                try { shell.close(); } catch (Throwable ignored) {}
            }
            return granted;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static CommandResult exec(String... commands) {
        Class<?> host = hostGateway();
        if (host != null) return execThroughHost(host, commands);

        try {
            Shell.Result r = Shell.cmd(commands).exec();
            return new CommandResult(r.getCode(), new ArrayList<>(r.getOut()), new ArrayList<>(r.getErr()));
        } catch (Throwable t) {
            List<String> err = new ArrayList<>();
            err.add(t.toString());
            return new CommandResult(-1, new ArrayList<>(), err);
        }
    }

    private static Class<?> hostGateway() {
        try {
            return Class.forName(SUITE_ROOT_GATEWAY, false, RootShell.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CommandResult execThroughHost(Class<?> gateway, String[] commands) {
        try {
            Method method = gateway.getMethod(
                    "executeFromPlugin",
                    String.class,
                    String.class,
                    String.class,
                    long.class);
            String command = String.join("\n", commands == null ? new String[0] : commands);
            Object raw = method.invoke(null, PLUGIN_ID, "root-shell", command, 20L);
            if (raw == null) throw new IllegalStateException("YSuite root gateway returned null");
            Class<?> type = raw.getClass();
            int code = ((Number) type.getMethod("getCode").invoke(raw)).intValue();
            String stdout = (String) type.getMethod("getStdout").invoke(raw);
            String stderr = (String) type.getMethod("getStderr").invoke(raw);
            List<String> out = splitLines(stdout);
            List<String> err = splitLines(stderr);
            return new CommandResult(code, out, err);
        } catch (Throwable t) {
            // Host class exists: never bypass YSuite by silently opening a second root shell.
            return new CommandResult(
                    -1,
                    new ArrayList<>(),
                    new ArrayList<>(Collections.singletonList("YSuite root gateway error: " + t)));
        }
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
