package com.yagay.ypower.diag;

import com.yagay.ypower.hook.SupplementalRuleIds;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SupplementalRuleCatalog {
    private static final Map<String, DetectionRuleDefinition> RULES = new LinkedHashMap<>();

    static {
        add(SupplementalRuleIds.NATIVE_UNIX_SOCKET_CONNECT, "socket",
                "Native Unix Socket 连接",
                "目标 App 的 native 代码调用 connect(AF_UNIX)，说明它直接探测或访问了本地 Unix Domain Socket。",
                "Root/环境检测项目有时会通过本地 socket/daemon 可达性识别管理器或高权限组件。",
                "先看具体 sun_path、连接返回值和调用者 SO；普通应用自己的 Unix socket 不应被当成安全命中。",
                "Linux AF_UNIX / RootBeerFresh style diagnostics");
        add(SupplementalRuleIds.NATIVE_ROOT_UNIX_SOCKET_CONNECT, "root",
                "Native Root/管理器 Unix Socket 探测",
                "目标 App native 代码尝试连接名称中包含 Magisk/Zygisk/KernelSU/APatch 等特征的 Unix Socket。",
                "Unix socket 可达性可以作为环境信号，但单个 socket 名称或一次连接仍不足以独立证明整个设备状态。",
                "结合连接成功、调用栈、重复性以及后续异常/退出判断；不要用单一 socket 信号直接下结论。",
                "RootBeerFresh / Unix socket diagnostics");

        add(SupplementalRuleIds.BINDER_DESCRIPTOR_QUERY, "service",
                "Binder Interface Descriptor 查询",
                "目标 App 查询 Binder interface descriptor，可能用于确认系统服务真实类型、代理层或服务替换情况。",
                "Service/Binder 完整性检测通常会把 ServiceManager 名称、Binder descriptor 与 transact 行为组合分析。",
                "记录 descriptor 与调用栈；descriptor 查询本身保持 CHECKED。",
                "Android Binder / ServiceManager diagnostics");
        add(SupplementalRuleIds.BINDER_TRANSACT_QUERY, "service",
                "Binder transact 安全调用",
                "目标 App 在安全/完整性调用栈中直接执行 Binder transact。",
                "底层安全组件、Keystore、Gatekeeper、Integrity 等最终都会通过 Binder IPC 与系统服务交互。",
                "只记录 transaction code/flags/descriptor 线索，不记录 Parcel 内容，也不修改 Binder 返回结果。",
                "Android Binder IPC");

        addStatic(SupplementalRuleIds.STATIC_DEX_BUILD_REF, "DEX 静态 Build/设备字段引用",
                "目标 APK 的 DEX 字符串表包含 Build/设备身份相关引用。",
                "静态引用只能证明代码可能使用这些字段，不能证明本次运行真的执行了该路径。");
        addStatic(SupplementalRuleIds.STATIC_DEX_DEBUG_REF, "DEX 静态调试检测引用",
                "目标 APK 的 DEX 中存在 Debug/TracerPid/proc status 等调试检测相关引用。",
                "用于弥补 Build 字段/getstatic 等普通 method Hook 无法直接观察的盲区。");
        addStatic(SupplementalRuleIds.STATIC_DEX_ROOT_REF, "DEX 静态 Root 环境引用",
                "目标 APK 的 DEX 中存在 Magisk/KernelSU/APatch/su/data-adb 等 Root 环境引用。",
                "静态存在不等于本次运行命中 Root，只作为定位潜在检测代码的辅助证据。");
        addStatic(SupplementalRuleIds.STATIC_DEX_INTEGRITY_REF, "DEX 静态完整性/Attestation 引用",
                "目标 APK 的 DEX 中存在 Play Integrity、AndroidKeyStore、Attestation 等相关引用。",
                "静态证据可帮助解释运行时 token/attestation 调用来自哪些代码模块。");
        addStatic(SupplementalRuleIds.STATIC_DEX_VIRTUALIZATION_REF, "DEX 静态虚拟化/模拟器引用",
                "目标 APK 的 DEX 中存在 qemu/goldfish/ranchu/EGL/GL/虚拟化相关引用。",
                "这类字段和常量有些通过 getstatic 读取，普通 LSPosed method Hook 无法直接看到，因此静态扫描用于补盲。");
        addStatic(SupplementalRuleIds.STATIC_DEX_BINDER_REF, "DEX 静态 Binder/Service 引用",
                "目标 APK 的 DEX 中存在 ServiceManager/IBinder/BinderProxy 等引用。",
                "只有结合运行时 Binder/ServiceManager 事件才能判断实际执行路径。");
        addStatic(SupplementalRuleIds.STATIC_DEX_SOCKET_REF, "DEX 静态 Unix Socket 引用",
                "目标 APK 的 DEX 中存在 LocalSocket/AF_UNIX/dev/socket 等本地 socket 引用。",
                "只有运行时 connect/LocalSocket 事件才能说明本次实际探测行为。");

        add(SupplementalRuleIds.VIRTUALIZATION_FLOW, "virtualization",
                "虚拟化/模拟器检测组合链",
                "同一诊断会话内出现了两种或以上虚拟化环境查询，例如 property + GL/EGL + Telephony/Sensor。",
                "多来源组合比单一 Build/property 查询更接近真实虚拟化检测逻辑。",
                "组合 Flow 默认仍为 CHECKED；只有明确返回异常环境特征并与退出相关时才提高归因。",
                "DuckDetector / emulator diagnostics");
        add(SupplementalRuleIds.NETWORK_ENVIRONMENT_FLOW, "network",
                "VPN/Proxy 网络环境检测组合链",
                "同一会话内出现 VPN transport/interface 与 proxy 查询等多种网络环境检查。",
                "网络环境检查可能用于合规、风控或连接诊断，本身不意味着退出原因。",
                "查看具体 HIT/NOT_HIT、调用栈与退出时序，不把普通 VPN/Proxy 使用自动判为异常。",
                "Android NetworkCapabilities / Proxy diagnostics");
        add(SupplementalRuleIds.SERVICE_BINDER_FLOW, "service",
                "ServiceManager/Binder 检测组合链",
                "目标 App 在短时间内组合使用 ServiceManager 查询、descriptor 或 Binder transact。",
                "这类组合可用于确认系统服务身份、完整性或底层安全组件交互。",
                "记录服务名、descriptor、transaction code 和调用栈；不修改 Binder 行为。",
                "Android Binder / ServiceManager");
        add(SupplementalRuleIds.UNIX_SOCKET_FLOW, "socket",
                "Unix Socket 探测组合链",
                "同一会话中同时观察到 Java LocalSocket 与 native AF_UNIX connect 等本地 socket 行为。",
                "Java/native 两层同时出现能提高‘目标 App 正在主动探测本地 daemon/socket’的可信度。",
                "结合具体名称、连接返回值和退出链；组合事件本身仍保持 CHECKED。",
                "Android LocalSocket / Linux AF_UNIX");
    }

    private SupplementalRuleCatalog() {}

    public static DetectionRuleDefinition get(String id) {
        return id == null ? null : RULES.get(id);
    }

    private static void addStatic(String id, String title, String why, String project) {
        add(id, "static_evidence", title, why, project,
                "只作为静态辅助证据，不参与主要/次要退出归因；需要与运行时事件对照。",
                "YPower DEX reference scanner");
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
}
