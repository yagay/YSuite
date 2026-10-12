package com.yagay.YFloat.hook;

import java.lang.reflect.Method;

/**
 * APK-verified structural mapping for Google App 17.64.15.ve (14 DEX files).
 *
 * dvkh.z(dvjy,boolean) -> void, dvjy.a:dwtg, and dwtg.k():String /
 * dwtg.b():RectF were checked against the uploaded APK's actual class data.
 *
 * A profile is only a fast-path. Unknown future APK versions fall back to the
 * signature resolver, which never assumes these obfuscated names are stable.
 */
final class GoogleLens1764Profile {
    static final String NAME = "17.64.15.ve";
    private static final String CONTROLLER = "dvkh";
    private static final String METADATA = "dvjy";
    private static final String USER_SELECTION = "dwtg";

    static GoogleLensDynamicResolver.SelectionBinding resolveSelection(ClassLoader loader) {
        if (loader == null) return GoogleLensDynamicResolver.SelectionBinding.none("classLoader=null");
        try {
            Class<?> controller = Class.forName(CONTROLLER, false, loader);
            for (Method method : GoogleReflection.declaredMethods(controller)) {
                Class<?>[] params = method.getParameterTypes();
                if (!"z".equals(method.getName()) || params.length != 2
                        || !METADATA.equals(params[0].getName())
                        || params[1] != boolean.class
                        || method.getReturnType() != void.class) continue;
                GoogleLensDynamicResolver.Candidate candidate =
                        GoogleLensDynamicResolver.scoreSelectionMethod(method);
                if (candidate == null
                        || candidate.score < GoogleLensDynamicResolver.MIN_SELECTION_CONFIDENCE
                        || !USER_SELECTION.equals(
                                candidate.userSelectionField.getType().getName())) continue;
                return new GoogleLensDynamicResolver.SelectionBinding(method,
                        candidate.userSelectionField, candidate.textMethod,
                        candidate.boundsMethod, candidate.pointMethod,
                        candidate.score, "verified 17.64 Lens selection signature");
            }
            return GoogleLensDynamicResolver.SelectionBinding.none(
                    "17.64 classes found but selection signature mismatch");
        } catch (Throwable t) {
            return GoogleLensDynamicResolver.SelectionBinding.none(
                    "17.64 unavailable=" + t.getClass().getSimpleName());
        }
    }

    private GoogleLens1764Profile() {}
}
