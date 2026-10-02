package com.yagay.ypower.diag;

import com.yagay.ypower.hook.DetectionRuleIds;
import com.yagay.ypower.model.DetectionHitState;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DetectionRuleCatalog {
    private static final Map<String, DetectionRuleDefinition> RULES = new LinkedHashMap<>();

    static {
        add(DetectionRuleIds.ROOT_FILE_SU, "root", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_10fa893138d4),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f389fdfdd96f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_873791cf48cb),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4ae3c07ad13e),
                "RootBeer / RootRoot");

        add(DetectionRuleIds.ROOT_FILE_MAGISK, "root", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e95f62abb8e5),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7165ef94c13b),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7774b353a1a0),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_90f7cf4acf99),
                "RootBeer / RootRoot");

        add(DetectionRuleIds.ROOT_FILE_KERNELSU, "root", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8b0a3fe8f335),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f0ffb2c7d79a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8b6227d1c406),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f880058b0f43),
                "RootRoot / Ruru");

        add(DetectionRuleIds.ROOT_FILE_APATCH, "root", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_97108469d74f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2618006e23c5),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_92cfe19b14ed),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f41dea35a0b8),
                "RootRoot / Ruru");

        add(DetectionRuleIds.ROOT_DATA_ADB, "mount", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5da489ec4ab2),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a9a4cd74b5d0),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b1dea124e44a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_693903731694),
                "Root detection / systemless environment model");

        add(DetectionRuleIds.HOOK_PROC_MAPS, "hook", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_dac130b4d12a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5f438a026897),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d6da7177c658),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_327ec625e9b3),
                "LSPosed / ByteHook / ShadowHook diagnostics");

        add(DetectionRuleIds.DEBUG_PROC_STATUS, "debugger", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b49b50294380),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_156c9daa7111),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b0ae103ba454),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1b2cbf3e3d04),
                "Android debugger diagnostics");

        add(DetectionRuleIds.MOUNT_PROC_MOUNT, "mount", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_00cbf25ee247),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_961688b4330b),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2d86785957c2),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ab11bb5374ae),
                "RootBeer / proc-mount diagnostics");

        addPackage(DetectionRuleIds.PACKAGE_MAGISK, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_db93e4bf9aa2), "Magisk", "RootBeer / Ruru");
        addPackage(DetectionRuleIds.PACKAGE_KERNELSU, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ccf8c03b6184), "KernelSU", "Ruru / RootRoot");
        addPackage(DetectionRuleIds.PACKAGE_APATCH, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f3629bb1cb77), "APatch", "Ruru / RootRoot");
        addPackage(DetectionRuleIds.PACKAGE_LSPOSED, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_890a872c393c), "LSPosed", "LSPosed / Ruru");
        addPackage(DetectionRuleIds.PACKAGE_XPOSED, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3efdeeb1e5bc), "Xposed", "Xposed / Ruru");
        addPackage(DetectionRuleIds.PACKAGE_FRIDA, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d34a043bbb43), "Frida", "Frida / RASP detection projects");
        addPackage(DetectionRuleIds.PACKAGE_SHIZUKU, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_187f5cf77cb2), "Shizuku", "Shizuku / package-query diagnostics");

        add(DetectionRuleIds.PACKAGE_ENUMERATION, "package", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_15ccaf3b70f7),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8bf8168330e8),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4d707f31e88d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3f2c27bf2cef),
                "Android PackageManager / RootBeer");

        addProperty(DetectionRuleIds.PROP_VERIFIED_BOOT, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_aa286bc0558f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_631cff8f9a98), "Android Verified Boot");
        addProperty(DetectionRuleIds.PROP_VBMETA_STATE, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_90558c47eb4e),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_434c06705336), "Android Verified Boot");
        addProperty(DetectionRuleIds.PROP_FLASH_LOCKED, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_375873a3ca6d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_63b980c5d093), "Android Verified Boot");
        addProperty(DetectionRuleIds.PROP_DEBUGGABLE, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4a0b0948b127),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7fe9b657282e), "RootBeer / Android build properties");
        addProperty(DetectionRuleIds.PROP_SECURE, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e543da889146),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c1622541535b), "RootBeer / Android build properties");
        addProperty(DetectionRuleIds.PROP_BUILD_TAGS, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_05d1ca2a8f85),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a2fbc5c793f0), "RootBeer");
        addProperty(DetectionRuleIds.PROP_BUILD_TYPE, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e4cec1db5326),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_dd76573bf497), "Android build properties");
        add(DetectionRuleIds.PROP_GENERIC, "environment", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f8549f2bb258),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6631f4e7ffe8),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_adf0ff58bc63),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2bbf21117d07),
                "Android SystemProperties");

        addCommand(DetectionRuleIds.CMD_SU, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_fefa6d8a6731),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9f6d454986e9));
        addCommand(DetectionRuleIds.CMD_GETPROP, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_750175a02ed4),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f828ebdd3ccf));
        addCommand(DetectionRuleIds.CMD_MOUNT, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f77cdd87293c),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cee5ddc07a2d));
        addCommand(DetectionRuleIds.CMD_SELINUX, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_80da3f80fba0),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5b7257caed33));

        add(DetectionRuleIds.DEBUG_IS_CONNECTED, "debugger", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1c94387caab1),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e04d213fd77b),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_461d7040c6ce),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d3873a971543),
                "Android Debug API");
        add(DetectionRuleIds.DEBUG_WAITING, "debugger", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_73f931777e05),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7d3033f30ac8),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_461d7040c6ce),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_81e1269dd44a),
                "Android Debug API");

        add(DetectionRuleIds.NATIVE_PTRACE, "debugger", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_58a008721df5),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_159a787d1755),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e9c33f4eb391),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_370b201a50e3),
                "Android/Linux ptrace diagnostics");

        add(DetectionRuleIds.PERMISSION_QUERY, "permission", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_abde6ee50bf3),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_30970f87eaa3),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d2fe285f71a2),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_34e7e749fe51),
                "App Manager / Android permissions");

        addExit(DetectionRuleIds.EXIT_SYSTEM, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a4295ddc746b));
        addExit(DetectionRuleIds.EXIT_HALT, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3280e4060022));
        addExit(DetectionRuleIds.EXIT_KILL_PROCESS, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a9099a8a876a));
        addExit(DetectionRuleIds.EXIT_NATIVE_ABORT, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_358113821976));
        addExit(DetectionRuleIds.EXIT_NATIVE_EXIT, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_22a5ca296cda));
        addExit(DetectionRuleIds.EXIT_NATIVE_KILL, com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c961abc6a716));

        add(DetectionRuleIds.KERNEL_UNAME_QUERY, "kernel", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a33d2fad45aa),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_bf09a5ed0243),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c7997d24866c),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_606c93932856),
                "DuckDetector Kernel Check / Linux uname");
        add(DetectionRuleIds.KERNEL_PROC_VERSION, "kernel", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_247328688c21),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a34d375c85be),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_25b733caf56f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_74efaecfac3d),
                "DuckDetector Kernel Check");
        add(DetectionRuleIds.KERNEL_CMDLINE_QUERY, "kernel", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_359f81863d39),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_79ea1731a247),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4ef89dce84b1),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4d1c27fbc9f8),
                "DuckDetector Kernel Check");
        add(DetectionRuleIds.KERNEL_OSRELEASE_QUERY, "kernel", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3f9ea5181777),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_00e681241287),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0e6a7ec7205f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7fabe083d533),
                "DuckDetector Kernel Check");
        add(DetectionRuleIds.KERNEL_SYS_VERSION_QUERY, "kernel", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b185e6bfa32d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4cf9c8e8c961),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0e6a7ec7205f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3a762d836bc6),
                "DuckDetector Kernel Check");
        add(DetectionRuleIds.KERNEL_KPTR_QUERY, "kernel", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_106274c90bae),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_dadeb1cf92dc),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cabcf32a8fe7),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_84fcaedebf44),
                "DuckDetector Kernel Check");

        add(DetectionRuleIds.SELINUX_ENFORCE_READ, "selinux", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5f0c3f79ac54),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_971ca411538f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e9de3867f0a5),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_43ba584161f4),
                "DuckDetector SELinux");
        add(DetectionRuleIds.SELINUX_CONTEXT_READ, "selinux", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a36071df830a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_55363502b025),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c6b736c9393b),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_41faa83170d2),
                "DuckDetector SELinux");
        add(DetectionRuleIds.SELINUX_POLICY_READ, "selinux", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5ded98c7b33a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e42a0594a921),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e03414c8ff6d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1757eae6bca5),
                "DuckDetector SELinux");
        add(DetectionRuleIds.SELINUX_XATTR_QUERY, "selinux", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_35522d4bf01b),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_680e3885d574),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0a54cb4b4286),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1e2fee6ccc91),
                "DuckDetector SELinux / Linux xattr");

        add(DetectionRuleIds.MEMORY_SMAPS_QUERY, "memory", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5420bd2be920),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0164bf55f8c0),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5e98aabe93c8),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6fe5d2231521),
                "DuckDetector Memory / Zygisk");
        add(DetectionRuleIds.MEMORY_FD_QUERY, "memory", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f10c5425a271),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cdfc08810933),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1f57e19b3565),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2ad5e7e449a3),
                "DuckDetector Memory / Zygisk");
        add(DetectionRuleIds.MEMORY_TASK_QUERY, "memory", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f4f21d48a812),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_929987107870),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_433570af6297),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9360347bd33c),
                "DuckDetector Zygisk");
        add(DetectionRuleIds.MEMORY_LINKER_ENUM_QUERY, "memory", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_60e2fe17d5fa),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e26d6d240871),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_31eab8743eff),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d438c5e79d3a),
                "DuckDetector Memory linker detector");
        add(DetectionRuleIds.MEMORY_SIGNAL_QUERY, "memory", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8036a7a2f3da),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f69b2c2bedeb),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_47589448510f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ce2114f49bea),
                "DuckDetector Memory signal detector");
        add(DetectionRuleIds.MEMORY_VDSO_QUERY, "memory", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8f55d828eefb),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_90c6df3a893b),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_fa7cf9df6e73),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6f942171b47d),
                "DuckDetector Memory vDSO detector");
        add(DetectionRuleIds.MEMORY_MPROTECT_QUERY, "memory", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f611b7fa434d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_af79d456fbdd),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_bfa98da90171),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cdd1b0001a46),
                "Linux mprotect / DuckDetector memory model");

        add(DetectionRuleIds.KEYSTORE_INSTANCE_QUERY, "attestation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_03a45d0ee7f9),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_61a55d837176),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cde2cd6c2327),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2f2db3655805),
                "AndroidKeyStore / KeyAttestation");
        add(DetectionRuleIds.KEY_ATTESTATION_CHALLENGE, "attestation", "Key Attestation Challenge",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_71843ebc935f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a35a2dc3e518),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a5ab2a7bcca7),
                "Android Key Attestation / KeyMint");
        add(DetectionRuleIds.KEY_STRONGBOX_REQUEST, "attestation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2eb2693c3600),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_da3b806f354b),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1fa3ede64945),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_bcd20c687b4f),
                "Android StrongBox / KeyMint");
        add(DetectionRuleIds.KEY_CERT_CHAIN_QUERY, "attestation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2e4eeb73d1f1),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e129dafc1347),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cf073b5084f5),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_186bc99824ce),
                "KeyAttestation / AndroidKeyStore");
        add(DetectionRuleIds.KEY_SECURITY_LEVEL_QUERY, "attestation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_80a82cead088),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ab5a104c6c52),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9a5069c21530),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e48426a43129),
                "Android KeyInfo / KeyMint");

        add(DetectionRuleIds.PLAY_INTEGRITY_REQUEST, "integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b2ec439a5a1f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_bcbf71dd328d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_50699ed1e07a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_414a18b78de0),
                "Google Play Integrity / SPIC");
        add(DetectionRuleIds.PLAY_INTEGRITY_STANDARD_PREPARE, "integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_27ffcd9e79d3),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d50556667e13),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_627980402578),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d0e383420e8c),
                "Google Play Integrity Standard API");
        add(DetectionRuleIds.PLAY_INTEGRITY_STANDARD_REQUEST, "integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_892f2ba3fe66),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1089fb602593),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6cfc3451d170),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_94cafdacd10a),
                "Google Play Integrity Standard API");
        add(DetectionRuleIds.PLAY_INTEGRITY_TOKEN_QUERY, "integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_587908893161),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d13958d68a2d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b41f198ded2e),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f3ad0b05fc99),
                "Google Play Integrity");

        add(DetectionRuleIds.SELINUX_ACCESS_PROBE, "selinux", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b984d74a8fe4),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8fff7fbbcd9e),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b1661c89831a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8d629aa44d21),
                "LSPosed DirtySepolicy");
        add(DetectionRuleIds.SELINUX_STATUS_SEQNO, "selinux", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_805d86b31792),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c8b1c1132d5d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_24b64514dcaa),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_bdb813eafebb),
                "DirtySepolicy / SELinux status");
        add(DetectionRuleIds.SELINUX_POLICYLOAD_QUERY, "selinux", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1854da471f1f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_830a694271df),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1ff9eefcd17a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ef1f60d7dadf),
                "DirtySepolicy / SELinux policy");
        add(DetectionRuleIds.APP_ZYGOTE_PROBE, "selinux", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_821df77d6285),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_232cae2157bd),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6b53676d13aa),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_aea0af6b795b),
                "DirtySepolicy / Android App Zygote");

        add(DetectionRuleIds.PROCESS_FORK_QUERY, "zygisk", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0f7a3fd8341c),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_081dcd664602),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2cdaba881c6f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f93ceeee1a58),
                "DetectZygisk / Linux process");
        add(DetectionRuleIds.PROCESS_WAITPID_QUERY, "zygisk", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8791315a9ba3),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9f16dea905e4),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_bb304b1716ce),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_98322c873f86),
                "DetectZygisk / ptrace");
        add(DetectionRuleIds.PTRACE_ATTACH_QUERY, "zygisk", "PTRACE_ATTACH",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5ec0271b64ee),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_96d211cab572),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_968c717709cd),
                "DetectZygisk");
        add(DetectionRuleIds.PTRACE_EVENTMSG_QUERY, "zygisk", "PTRACE_GETEVENTMSG",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ff54a32de075),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_71a43aff7c1d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4f8c73c14905),
                "DetectZygisk");
        add(DetectionRuleIds.PTRACE_SYSCALL_QUERY, "debugger", "PTRACE_SYSCALL",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a14fb722c1cd),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7c267fd79ffd),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2acb5d7f2044),
                "Linux ptrace");
        add(DetectionRuleIds.PTRACE_DETACH_QUERY, "zygisk", "PTRACE_DETACH",
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ab1372a68573),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_089120edee1b),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_72d63510e43d),
                "DetectZygisk / Linux ptrace");

        add(DetectionRuleIds.APP_SIGNATURE_QUERY, "self_integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6be72b3efac1),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_db386211072d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2cd0ea248922),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c194508b20c0),
                "Android SigningInfo / GarudaDefender");
        add(DetectionRuleIds.SELF_APK_READ, "self_integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_76d336e588db),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d5f93b5dc531),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d2d333f8fef3),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5f173fee908a),
                "APK integrity / GarudaDefender");
        add(DetectionRuleIds.SELF_DEX_READ, "self_integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b02e7e1991c5),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f8e28db9fe9e),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b7b523d00a08),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f2c6721f017a),
                "DEX integrity / GarudaDefender");
        add(DetectionRuleIds.SELF_SO_READ, "self_integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d897be6adb58),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_075c0972e348),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8f4bab065010),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c99df70ed7a8),
                "Native integrity / GarudaDefender");
        add(DetectionRuleIds.CERTIFICATE_DIGEST_QUERY, "self_integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_94e4c9ad5949),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_2a2c7b0d968f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_69feb7f2fe0d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_87462b2d5450),
                "Java MessageDigest / app integrity");

        add(DetectionRuleIds.ATTESTATION_FLOW, "attestation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_87ec0e0319a2),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_48d633ff0d12),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b6d4a7e7d56d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_91a79ea7d5c5),
                "Android Key Attestation / KeyAttestation");
        add(DetectionRuleIds.PLAY_INTEGRITY_FLOW, "integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b92e4c8f89ca),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_0abeeac1e350),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_187172256c72),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ddfc05f5e85b),
                "Google Play Integrity / SPIC");
        add(DetectionRuleIds.DIRTY_SEPOLICY_FLOW, "selinux", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3562562d988f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_10ee1223a11d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8df003406074),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ebabde456087),
                "LSPosed DirtySepolicy");
        add(DetectionRuleIds.ZYGISK_PTRACE_FLOW, "zygisk", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_34b8db6b9148),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_691e0dab4d8d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_41acb9e25248),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c067a1bdcda0),
                "DetectZygisk");
        add(DetectionRuleIds.SELF_INTEGRITY_FLOW, "self_integrity", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_65318f5d830b),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4a2c036bf8af),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_df8a5b0af495),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1ecd56b9df7a),
                "GarudaDefender / Android app integrity");

        add(DetectionRuleIds.JAVA_LOAD_LIBRARY, "instrumentation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a9b331bfb8a3),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c600b532c71a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_53c49a8aef4b),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_e21d80013744),
                "Android Runtime / JNI loading");
        add(DetectionRuleIds.LINKER_DLOPEN, "instrumentation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_50f35b57016e),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_8f9c133744db),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a22f83b35e50),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_82624695da53),
                "ByteHook dlopen callback");
        add(DetectionRuleIds.LINKER_DLSYM, "instrumentation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_47592f644420),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9a4581353742),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d2c52ebf5744),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_21453567ddc3),
                "Android linker / ByteHook");

        add(DetectionRuleIds.JAVA_UNCAUGHT_EXCEPTION, "error", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_bb8ec6557e9a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a651fa3241a3),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1876dd5b141a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_ec8a16a54057),
                "Android Thread / RuntimeInit");
        add(DetectionRuleIds.COROUTINE_UNHANDLED_EXCEPTION, "error", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5ae66087dbad),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_fe7244a95c8e),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c22eac5cdc93),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f90f69a770d6),
                "kotlinx.coroutines CoroutineExceptionHandler");
        add(DetectionRuleIds.RXJAVA2_GLOBAL_ERROR, "error", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a98d06380a33),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_19243d5e9e77),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b4a086bdb8c4),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_002d69b7e843),
                "RxJava2 RxJavaPlugins");
        add(DetectionRuleIds.RXJAVA3_GLOBAL_ERROR, "error", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_baf9cf69c035),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_02d424ac1705),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_b4a086bdb8c4),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_002d69b7e843),
                "RxJava3 RxJavaPlugins");

        add(DetectionRuleIds.JAVA_DEFAULT_EXCEPTION_HANDLER_SET, "instrumentation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5547226a6cdf),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_71fd1b26ede9),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_c7f2c08c0d0a),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_29f27b254c25),
                "Java Thread.UncaughtExceptionHandler");
        add(DetectionRuleIds.JAVA_THREAD_EXCEPTION_HANDLER_SET, "instrumentation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a97f93ec4e6c),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1057568c7de5),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_6fde12bef659),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_29f27b254c25),
                "Java Thread.UncaughtExceptionHandler");
        add(DetectionRuleIds.RXJAVA2_ERROR_HANDLER_SET, "instrumentation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_70834f4bbf4d),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f068c4260b70),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3cad5c456423),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_47ffb81b3768),
                "RxJava2 RxJavaPlugins");
        add(DetectionRuleIds.RXJAVA3_ERROR_HANDLER_SET, "instrumentation", com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_46672adfa8e3),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cbd3f69e6845),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3cad5c456423),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_47ffb81b3768),
                "RxJava3 RxJavaPlugins");
    }

    private DetectionRuleCatalog() {}

    private static void addPackage(String id, String title, String target, String ref) {
        add(id, "package", title,
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_cf32aadc33b1) + target + com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f7858d2bb342),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_a2fbfb346d9f),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_f0ecbab8294e),
                ref);
    }

    private static void addProperty(String id, String title, String why, String ref) {
        add(id, "environment", title, why,
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_391d78ee80f9),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_fc208cdfb2da),
                ref);
    }

    private static void addCommand(String id, String title, String why) {
        add(id, "command", title, why,
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_3e4289f36fe5),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_d77099de43be),
                "Android Runtime / ProcessBuilder diagnostics");
    }

    private static void addExit(String id, String title) {
        add(id, "exit", title,
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_5d1eeccdfde5),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_7b100013a3d4),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_1566bfdbd835),
                "xCrash / YPower runtime timeline");
    }

    private static void add(
            String id,
            String category,
            String title,
            String whyDetected,
            String projectExplanation,
            String remediation,
            String reference
    ) {
        RULES.put(id, new DetectionRuleDefinition(
                id, category, title, whyDetected, projectExplanation, remediation, reference
        ));
    }

    public static DetectionRuleDefinition get(String id) {
        return RULES.get(id);
    }

    public static DetectionRuleDefinition getOrDefault(String id, String category, String title) {
        DetectionRuleDefinition rule = RULES.get(id);
        if (rule != null) return rule;
        return new DetectionRuleDefinition(
                id == null ? DetectionRuleIds.UNKNOWN : id,
                category == null ? "unknown" : category,
                title == null ? com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_9c01f9c5b4ff) : title,
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_fdd4adf29552),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_4505bbe58f07),
                com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_generated_788ecbcd7c70),
                "YPower runtime diagnostics"
        );
    }

    public static DetectionHitState evaluate(String ruleId, boolean observedPositive, String result, String exception) {
        String value = result == null ? "" : result.trim().toLowerCase(Locale.ROOT);

        if (isCheckedOnly(ruleId)) {
            return DetectionHitState.CHECKED;
        }

        if (isExit(ruleId) || isErrorEvent(ruleId)) {
            return DetectionHitState.HIT;
        }

        if (isBooleanRule(ruleId)) {
            if ("true".equals(value)) return DetectionHitState.HIT;
            if ("false".equals(value)) return DetectionHitState.NOT_HIT;
            return observedPositive ? DetectionHitState.HIT : DetectionHitState.UNKNOWN;
        }

        if (isPropertyRule(ruleId)) {
            return evaluateProperty(ruleId, value);
        }

        if (isPackageRule(ruleId)) {
            if (exception != null && exception.contains("NameNotFoundException")) {
                return DetectionHitState.NOT_HIT;
            }
            if (observedPositive) return DetectionHitState.HIT;
            return DetectionHitState.NOT_HIT;
        }

        if (isRootExistenceRule(ruleId)) {
            return observedPositive ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
        }

        if (DetectionRuleIds.PERMISSION_QUERY.equals(ruleId)) {
            if ("0".equals(value)) return DetectionHitState.NOT_HIT;
            if ("-1".equals(value)) return DetectionHitState.HIT;
            return DetectionHitState.UNKNOWN;
        }

        return observedPositive ? DetectionHitState.HIT : DetectionHitState.UNKNOWN;
    }

    private static boolean isCheckedOnly(String id) {
        return DetectionRuleIds.HOOK_PROC_MAPS.equals(id)
                || DetectionRuleIds.DEBUG_PROC_STATUS.equals(id)
                || DetectionRuleIds.MOUNT_PROC_MOUNT.equals(id)
                || DetectionRuleIds.ROOT_DATA_ADB.equals(id)
                || DetectionRuleIds.PACKAGE_ENUMERATION.equals(id)
                || DetectionRuleIds.CMD_SU.equals(id)
                || DetectionRuleIds.CMD_GETPROP.equals(id)
                || DetectionRuleIds.CMD_MOUNT.equals(id)
                || DetectionRuleIds.CMD_SELINUX.equals(id)
                || DetectionRuleIds.NATIVE_PTRACE.equals(id)
                || DetectionRuleIds.JAVA_LOAD_LIBRARY.equals(id)
                || DetectionRuleIds.LINKER_DLOPEN.equals(id)
                || DetectionRuleIds.LINKER_DLSYM.equals(id)
                || DetectionRuleIds.JAVA_DEFAULT_EXCEPTION_HANDLER_SET.equals(id)
                || DetectionRuleIds.JAVA_THREAD_EXCEPTION_HANDLER_SET.equals(id)
                || DetectionRuleIds.RXJAVA2_ERROR_HANDLER_SET.equals(id)
                || DetectionRuleIds.RXJAVA3_ERROR_HANDLER_SET.equals(id)
                || DetectionRuleIds.KERNEL_UNAME_QUERY.equals(id)
                || DetectionRuleIds.KERNEL_PROC_VERSION.equals(id)
                || DetectionRuleIds.KERNEL_CMDLINE_QUERY.equals(id)
                || DetectionRuleIds.KERNEL_OSRELEASE_QUERY.equals(id)
                || DetectionRuleIds.KERNEL_SYS_VERSION_QUERY.equals(id)
                || DetectionRuleIds.KERNEL_KPTR_QUERY.equals(id)
                || DetectionRuleIds.SELINUX_ENFORCE_READ.equals(id)
                || DetectionRuleIds.SELINUX_CONTEXT_READ.equals(id)
                || DetectionRuleIds.SELINUX_POLICY_READ.equals(id)
                || DetectionRuleIds.SELINUX_XATTR_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_SMAPS_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_FD_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_TASK_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_LINKER_ENUM_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_SIGNAL_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_VDSO_QUERY.equals(id)
                || DetectionRuleIds.MEMORY_MPROTECT_QUERY.equals(id)
                || DetectionRuleIds.KEYSTORE_INSTANCE_QUERY.equals(id)
                || DetectionRuleIds.KEY_ATTESTATION_CHALLENGE.equals(id)
                || DetectionRuleIds.KEY_STRONGBOX_REQUEST.equals(id)
                || DetectionRuleIds.KEY_CERT_CHAIN_QUERY.equals(id)
                || DetectionRuleIds.KEY_SECURITY_LEVEL_QUERY.equals(id)
                || DetectionRuleIds.PLAY_INTEGRITY_REQUEST.equals(id)
                || DetectionRuleIds.PLAY_INTEGRITY_STANDARD_PREPARE.equals(id)
                || DetectionRuleIds.PLAY_INTEGRITY_STANDARD_REQUEST.equals(id)
                || DetectionRuleIds.PLAY_INTEGRITY_TOKEN_QUERY.equals(id)
                || DetectionRuleIds.SELINUX_ACCESS_PROBE.equals(id)
                || DetectionRuleIds.SELINUX_STATUS_SEQNO.equals(id)
                || DetectionRuleIds.SELINUX_POLICYLOAD_QUERY.equals(id)
                || DetectionRuleIds.APP_ZYGOTE_PROBE.equals(id)
                || DetectionRuleIds.PROCESS_FORK_QUERY.equals(id)
                || DetectionRuleIds.PROCESS_WAITPID_QUERY.equals(id)
                || DetectionRuleIds.PTRACE_ATTACH_QUERY.equals(id)
                || DetectionRuleIds.PTRACE_EVENTMSG_QUERY.equals(id)
                || DetectionRuleIds.PTRACE_SYSCALL_QUERY.equals(id)
                || DetectionRuleIds.PTRACE_DETACH_QUERY.equals(id)
                || DetectionRuleIds.APP_SIGNATURE_QUERY.equals(id)
                || DetectionRuleIds.SELF_APK_READ.equals(id)
                || DetectionRuleIds.SELF_DEX_READ.equals(id)
                || DetectionRuleIds.SELF_SO_READ.equals(id)
                || DetectionRuleIds.CERTIFICATE_DIGEST_QUERY.equals(id)
                || DetectionRuleIds.ATTESTATION_FLOW.equals(id)
                || DetectionRuleIds.PLAY_INTEGRITY_FLOW.equals(id)
                || DetectionRuleIds.DIRTY_SEPOLICY_FLOW.equals(id)
                || DetectionRuleIds.ZYGISK_PTRACE_FLOW.equals(id)
                || DetectionRuleIds.SELF_INTEGRITY_FLOW.equals(id);
    }

    private static boolean isBooleanRule(String id) {
        return DetectionRuleIds.DEBUG_IS_CONNECTED.equals(id)
                || DetectionRuleIds.DEBUG_WAITING.equals(id);
    }

    private static boolean isRootExistenceRule(String id) {
        return DetectionRuleIds.ROOT_FILE_SU.equals(id)
                || DetectionRuleIds.ROOT_FILE_MAGISK.equals(id)
                || DetectionRuleIds.ROOT_FILE_KERNELSU.equals(id)
                || DetectionRuleIds.ROOT_FILE_APATCH.equals(id);
    }

    private static boolean isPackageRule(String id) {
        return id != null && id.startsWith("PACKAGE_")
                && !DetectionRuleIds.PACKAGE_ENUMERATION.equals(id);
    }

    private static boolean isPropertyRule(String id) {
        return id != null && id.startsWith("PROP_")
                && !DetectionRuleIds.PROP_GENERIC.equals(id);
    }

    private static boolean isExit(String id) {
        return id != null && id.startsWith("EXIT_");
    }

    private static boolean isErrorEvent(String id) {
        return DetectionRuleIds.JAVA_UNCAUGHT_EXCEPTION.equals(id)
                || DetectionRuleIds.COROUTINE_UNHANDLED_EXCEPTION.equals(id)
                || DetectionRuleIds.RXJAVA2_GLOBAL_ERROR.equals(id)
                || DetectionRuleIds.RXJAVA3_GLOBAL_ERROR.equals(id);
    }

    private static DetectionHitState evaluateProperty(String ruleId, String value) {
        if (value.isBlank()) return DetectionHitState.UNKNOWN;
        switch (ruleId) {
            case DetectionRuleIds.PROP_VERIFIED_BOOT:
                return "green".equals(value) ? DetectionHitState.NOT_HIT : DetectionHitState.HIT;
            case DetectionRuleIds.PROP_VBMETA_STATE:
                return ("unlocked".equals(value) || "orange".equals(value))
                        ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            case DetectionRuleIds.PROP_FLASH_LOCKED:
                return ("0".equals(value) || "false".equals(value))
                        ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            case DetectionRuleIds.PROP_DEBUGGABLE:
                return ("1".equals(value) || "true".equals(value))
                        ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            case DetectionRuleIds.PROP_SECURE:
                return ("0".equals(value) || "false".equals(value))
                        ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            case DetectionRuleIds.PROP_BUILD_TAGS:
                return value.contains("test-keys") ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            case DetectionRuleIds.PROP_BUILD_TYPE:
                return ("eng".equals(value) || "userdebug".equals(value))
                        ? DetectionHitState.HIT : DetectionHitState.NOT_HIT;
            default:
                return DetectionHitState.UNKNOWN;
        }
    }
}
