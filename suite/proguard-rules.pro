# YSuite deliberately loads feature entry points, runtime bridges and LSPosed modules through
# class-name strings / META-INF/xposed/java_init.list. R8 cannot reliably infer all of those
# references across independently buildable feature modules, so keep YSuite-owned code intact
# while still shrinking/optimizing the much larger third-party dependency graph.
-keep class com.yagay.** { *; }

# Keep diagnostics readable. Compact builds are intended to replace the oversized debug artifact,
# not to make crash/hook logs opaque.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature,*Annotation*

# Native OCR / tracing libraries use JNI. Their AARs provide consumer rules, but keeping native
# method owners is a cheap safety net for modules that register JNI methods dynamically.
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}
