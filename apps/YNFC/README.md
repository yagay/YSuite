# YNFC

面向 OxygenOS / ColorOS 的 NFC UID 模拟与诊断工具。应用通过 LSPosed 在系统 NFC 进程中发现并验证 RF 配置写入路径，以真实的原生返回结果判断模拟是否生效。

## 当前版本

- 应用版本：`1.0.56`（versionCode `57`）
- Hook 构建：`46`
- Provider 协议：`7`

## 主要能力

- **唯一模拟路径：main 的 RF_CONFIG_WRITE Hook。** 不提供 VENDOR / INTERCEPT / NATIVE 模拟模式，卡片模拟时始终发送 RF 命令。
- 通过已发现的 Java RF 刷新触发器，必要时使用 main 原有的 `enableNfcShareMode` 回退。刷新调用不代表 UID 已成功写入，只有匹配的原生 RF 结果才可判定成功。
- 旧安装已激活其他后端时，先执行 STOP，再启动新的 RF 模拟；保留旧会话的停止恢复入口。NFC 进程的 Hook 更新需要显式重新加载 NFC 服务，不要求整机重启。

- 读取并保存 4/7/10 字节 NFC UID。
- APPLY、STOP 和恢复流程均绑定命令 generation、控制器 epoch 与 NFC 进程 PID。
- 通过原生 RF 写入结果 `0` 确认成功，不把 Binder 调用或刷新触发本身当作成功。
- 自动发现、学习并缓存与当前系统指纹和 NFC APK 版本匹配的 Hook profile。
- 支持导出 Provider 状态、运行日志和 Hook 诊断信息。
- 界面黑暗模式跟随系统；保留原有系统栏颜色策略，并处理状态栏与按键导航栏安全区域。

## 运行状态

- `APPLYING`：命令已发布，等待与本次请求严格对应的 RF 写入结果。
- `ACTIVE`：已收到同 generation、PID 和 epoch 的模拟 RF 成功结果。
- `STOPPING`：正在等待恢复原始 RF 配置。
- `INACTIVE`：已收到严格对应的原始 RF 成功结果。
- `WAIT_FOR_REPLAY`：刷新流程结束但精确 RF 回放稍晚到达；最多额外等待 1.2 秒，避免生命周期时序造成误报。
- `FAILED`：超时、原生返回失败或身份信息不匹配。

## 项目结构

- `MainActivity`：Activity 生命周期、权限入口和顶层界面装配。
- `NfcAppScreen`：Compose 页面与交互展示。
- `SimulationCoordinator`：应用侧 APPLY / STOP 流程编排。
- `DiagnosticExporter`：诊断导出。
- `NfcInjectionModule`：NFC 进程 Hook 安装与关键生命周期协调。
- `HookDiscoveryEngine`：RF 写入候选发现与评分。
- `RfPayloadEngine`：RF payload 识别与安全改写。
- `HookConfigStore` / `HookStateWriter`：Provider 配置读取与状态发布。
- `SimulationResultPolicy`：严格的结果归属和成功判定。

更完整的设计说明见 [Hook 架构](docs/HOOK_ARCHITECTURE.md) 和 [运行时优化](docs/RUNTIME_OPTIMIZATION.md)。

## 构建与安装

1. 使用 JDK 17 执行 `./gradlew assembleDebug testDebugUnitTest`。
2. 安装 APK，在 LSPosed 中启用模块，作用域仅选择“系统 NFC / `com.android.nfc`”。
3. 重启 NFC 进程或手机，使新 Hook 构建被加载。
4. 首次验证时执行一次 APPLY、STOP、再次 APPLY，并导出完整诊断。

版本升级后不能只覆盖安装就判断 Hook 是否更新；诊断中的 Hook build 应为 `46`，Provider protocol 应为 `7`。

## 自动检查

GitHub Actions 会构建 APK、运行 JVM 单元测试，并校验：

- versionCode / versionName 与预期一致；
- Xposed 入口和模块元数据存在；
- 模块作用域保持为 `com.android.nfc`；
- APK 已签名并通过 zipalign 检查。

## 安全边界

本项目直接作用于系统 NFC 进程。结构重构必须保持命令协议、generation/PID/epoch 归属与 RF 原生结果判定不变。没有设备日志证明收益时，不拆分 Hook 安装、生命周期恢复和早期 RF 回放之间的时序关键路径。

## 新版系统的自定义 UID 写入（需设备验证）

新系统已移除 NxpNativeNfcManager.changeRfParamsByConfig(byte[])；新增 NativeNfcManager.setNciConfig(int,byte[],int,boolean)。RF 模式直接获取 NfcService 的 DeviceHost，并仅发送自己构造的 LA_NFCID1 (0x33) 参数；从不调用系统 emulateNfcATag()，也没有新的模拟后端菜单。该方法返回 void：状态 UNVERIFIED 表示请求已下发而非实际射频验证成功。停止后采用 NFC 服务恢复，不要求整机重启。

## Wallet / payment coexistence and HAL observation (test branch)

- Native NXP HAL writer replacement is **not installed**. We only analyze existing
  `NxpNciX`/`NxpNciR` logcat. This path does not change routing, AIDs,
  RF discovery, payment APDUs, or the secure element.
- The NCI diagnostic distinguishes a post-APPLY `LA_NFCID1=33 00` request
  from a 4/7/10-byte NFCID1 request, and records later discovery, routing,
  and RF activation counts **without persisting raw packets or UID bytes**.
  Counts cannot identify wallet transactions or prove the UID seen over RF.
- YNFC's existing active UID emulation is different: its controller-wide
  `setNciConfig(0x33)` and discovery reassertion **can interfere with**
  HCE or secure-element wallet tap transactions, even though payment AIDs
  and APDUs are not modified. Simultaneous wallet compatibility is not proven.
- When using Google Wallet or any off-host/eSE payment, **STOP YNFC first**.
  Wait for the existing stock NFC recovery; if not confirmed, verify the wallet
  in a safe non-transaction test before relying on it. No automatic HAL patch,
  NFC service restart, wallet AID hook, or transaction interception is introduced
  by the read-only diagnostic change.

## 2026-11-07 follow-up: discovery gating and accurate NCI acknowledgement

A diagnostic captured initial 4-byte LA_NFCID1 configuration accepted by the
NFCC, but during OEM Extended Field Detect Mode the prior reassertion fired
also on an observed Listen-Disabled route. A subsequent 850ms throttle could
then skip a useful later Listen-Enabled phase. The reassertion is now
fail-closed unless the verified NFC discovery parameter contract reports host
routing enabled and reader mode disabled. Every skip can be logged while
diagnostic logging is enabled. This does not prove OEM-specific Listen masking,
so over-the-air verification is still required.

Only the matching LA_NFCID1 CORE_SET_CONFIG response is now reported as
`nci_config_response`; unrelated SET_CONFIG responses appear separately.
Do not treat unrelated 0x00 successes or a rejected empty-param command as
an NFCID1 result. No new HAL native hook, wallet AID changes, or automatic
service restart are introduced.

## Discovery one-shot rollback (2026-11-07)

The primary RF mode is restored to the **first hardware-accepted single-shot
implementation** from commit `6c062c0f`: per explicit APPLY, pause discovery
once, send exactly one LA_NFCID1 (`0x33`) config, then post exactly one
`NfcService.applyRouting(true)` resume to the NFC main looper.

Removed the later `NativeNfcManager.enableDiscovery()` hook and all automatic
UID reassertion during natural OEM discovery changes. In particular, YNFC
does **not** repeatedly send `0x33` when OPlus ExtendedFieldDetectMode
cycles Listen ON/OFF. There is no periodic task and no NFC-process reboot
to maintain the UID. The firmware can still naturally rotate identities;
the app must report that as unverified, not claim on-air stability.

The separately user-controlled polling guard now skips duplicate/unknown
automatic startup/controller-ready routing refreshes; after attempting an
actual routing refresh it never retries simply because the OEM Java mask
is unverified. Explicit user preference changes retain a single request.
`STOP` continues using the pre-existing stock-controller recovery if a
native UID write has occurred and no reversible stock snapshot exists.
That may reload NFC **once on explicit STOP**, not repeatedly during emulation.

OPlus firmware's own discovery cycling on RF-field detection is not
suppressed. Intercepting it would risk HCE, wallet payments and eSE routes.

## 2026-11-07: direct UID write without RF Discovery pause (experimental)

This branch intentionally tests **one** `NativeNfcManager#setNciConfig(0x33)`
call per user APPLY, with **no explicit `disableDiscovery()`,
`applyRouting(true)`, RF restart, or UID retry** in the direct write path.
The OEM itself may still cycle RF discovery for normal NFC behavior; this
experiment does not suppress those system operations.

The original unpaused attempt on SN220T previously returned
`NCI_STATUS_INVALID_PARAM (0x09)`; no guarantee is made that the direct
unpaused request will work. Capture `LA_NFCID1_RSP_STATUS` and verify actual
UID using a separate NFC-A reader. The API returns void and must stay
`UNVERIFIED` until evidence is available.

If the exact modern native writer is installed but not ready, YNFC now fails
closed instead of starting an older RF refresh/share-mode fallback (which
could restart Discovery). On older firmware without the modern writer,
the legacy RF_CONFIG_WRITE path is left unchanged.

STOP remains a separate explicit safety action: after a possible UID
controller mutation, stock restoration may still reload the NFC service once.
Changing the manual STOP recovery is outside this experiment.

## No-discovery-pause experiment outcome (2026-11-07)

The experimental direct-only `setNciConfig(0x33)` APPLY without
`disableDiscovery()` or `applyRouting(true)` built successfully, but the
device owner confirmed **card emulation did not work**. This is a device-level
negative test; no new NCI response log was provided, so do not claim the
experiment specifically returned `0x09`. An earlier unpaused attempt had
returned `0x09 INVALID_PARAM`.

The experiment is now reverted **only for the NFC-A write path**, restoring
commit `f444bdd6`'s known working one-shot RF pause/write/resume:
- On each explicit APPLY: one `disableDiscovery()` call, one
  `setNciConfig(0x33)` call, and one posted `applyRouting(true)`.
- No recurring `enableDiscovery()` reassertion and no automatic retries of
  routing when the OEM cannot verify the Java mask.
- Normal OEM field-detection discovery cycles are not blocked.
- An NFC OFF/ON or reboot clears controller RF config; restoring that only on
  controller READY (at most once per epoch) is a **separate future change**,
  not introduced here.
- STOP preserves the stock NFC controller recovery path.

This rollback changes NFC commands, not UID or card data. Test only with
the owner's own test card; avoid wallet payment while emulation is active.

## Fast switching and one-tap stock restoration UI

The saved-card list now offers **Switch UID** when another UID is selected.
Selecting another card issues a new serialized APPLY directly; it does not
interpose STOP or restart the NFC process, and retains the existing *one*
Discovery pause / NCI 0x33 write / routing resume for each manual switch.

A prominent **Stop and restore stock NFC** control appears directly under
the runtime status while emulation is selected. It uses the existing serialized
STOP and verified stock-recovery fallback (which may reload com.android.nfc
once if there is no reversible native stock snapshot). UI taps are guarded
while an operation is in flight so users cannot accidentally queue repeated
switches/stops. No new native hooks or controller recovery assumptions have
been introduced.

HAL startup configuration replacement alone is NOT the active fast-switch
backend: it would require the next HAL initialization to apply another UID.
In particular, a zero-length 0x33 soft restore must not be treated as a
successful stock restoration without a matching NCI response and actual
reader verification.

## Modern NFCID1 one-shot restoration after OFF/ON and boot

The current SN220T `setNciConfig(0x33)` call returns `void`, so a
manually submitted command remains terminal `UNVERIFIED`, not `SUCCESS`.
The older lifecycle recovery was only eligible for legacy `SUCCESS`
commands. The new bridge accepts just a selected, terminal
`UNVERIFIED` RF `APPLY` whose prior RF source was `own-nci-0x33`,
where a real NFC controller invalidation occurred and the RF epoch or PID
has become stale.

The bridge waits for Android NFC to become enabled after initialization
and, on the serialized command executor, performs **one** existing
`disableDiscovery -> setNciConfig(0x33) -> applyRouting(true)` write
per controller invalidation. A newer manual STOP or card switch cancels
a queued recovery. Native throws, missing writer and rejected writes are
never auto-retried in that epoch. Normal Discovery/EFDM callbacks have
no effect, and no NFC process reboot or HAL injection is introduced.

The diagnostic snapshot includes `own_nci_lifecycle_stage`, `reason`,
`generation`, `epoch`, `pid`, and `at`. A lifecycle request only proves
Java dispatched an NCI command; it does not certify NFCC acceptance or
the on-air UID. Testing on real SN220T after NFC OFF/ON and device reboot
is required; this change has not been hardware-validated yet.
