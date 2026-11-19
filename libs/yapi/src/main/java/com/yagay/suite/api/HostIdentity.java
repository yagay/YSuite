package com.yagay.suite.api;

/**
 * Immutable runtime identity of a feature APK. Cross-process messages must be routed to the
 * installed APK that contains the hook, never to an eventually synchronized preference value.
 *
 * <p>Feature build scripts inject HOST_PACKAGE for both the standalone and YSuite variants.
 * The fallback exists for older standalone builds that did not define a host package.</p>
 */
public final class HostIdentity {
    private HostIdentity() {}

    public static String compiledPackage(String compiledHost, String standalonePackage) {
        String host = compiledHost == null ? "" : compiledHost.trim();
        if (host.isEmpty()) host = standalonePackage == null ? "" : standalonePackage.trim();
        if (!validPackage(host)) {
            throw new IllegalArgumentException("Invalid compiled feature host package: " + host);
        }
        return host;
    }

    private static boolean validPackage(String value) {
        if (value == null || value.isEmpty() || value.startsWith(".") || value.endsWith(".")) {
            return false;
        }
        String[] segments = value.split("\\.", -1);
        if (segments.length < 2) return false;
        for (String segment : segments) {
            if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) {
                return false;
            }
            for (int i = 1; i < segment.length(); i++) {
                if (!Character.isJavaIdentifierPart(segment.charAt(i))) return false;
            }
        }
        return true;
    }
}
