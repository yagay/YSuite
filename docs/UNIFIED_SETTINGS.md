# YSuite 统一设置说明

YSuite 的宿主设置分为 **统一外观** 与 **通用功能**，两者共用入口但各自独立。

## 统一外观

YUI 负责主题、字体、按钮、列表、卡片、输入框、开关、对话框、导航与布局参数；全局设置可被模块覆盖。默认维持标准 Material 3 布局。只有使用 YUI / YView 共享组件的页面才会响应对应参数。独立 APK 维持 Android 私有配置，不会跨包偷偷同步。

## 通用功能

| 领域 | 单一真实实现 | 统一设置 |
| --- | --- | --- |
| 模块开关 | FeatureStateStore / FeatureRuntimeManager | 管理所有已集成模块的启用状态，复用既有 Root/LSPosed 门控 |
| 日志 | SuiteLog / SuiteCommonSettings | 全局与模块日志等级、文件容量、是否保留上一份 |
| 诊断 | SuiteLog.export / SuiteDiagnostics | 整体及单模块诊断导出 |
| 共享权限 | SuitePermissionState | 查看真实授权并打开 Android 权限设置 |
| 首页交互 | YAppearanceStore 中已有首页字段 | 滑动置顶、共享状态条 |

日志默认维持原行为：记录全部级别、单文件 1 MiB、保留一份轮转旧文件；所有日志级别均保留错误。独立覆盖优先于全局；重置仅影响日志参数，不会改模块启用状态、权限、私有数据。

诊断包包含有效通用配置、模块状态和采集覆盖信息；日志可能有隐私内容，请慎重分享。

## 新增统一功能的原则

首先证明至少两个模块确实使用同一份核心实现，再由共同宿主实现参数，测试默认行为和模块覆盖，最后把真实开关接入这里。不得只因为名称类似就把不同业务功能合并；各模块特有的 NFC、下载、文件、悬浮交互等仍保留在本模块。

## UI 生效范围与刷新策略（2026-10-10）

- **唯一视觉配置**：全局和模块覆盖均通过 `YAppearanceStore`；旧 `AppSettings` 不再覆盖 YUI 的主题或动态取色。按钮、卡片、输入框、对话框各自使用对应的圆角，不共享错误的按钮半径。
- **Compose**：`YTheme` 提供统一的 Material 3 配色、字体比例和按控件类型区分的形状；共享按钮、行高、卡片、输入框与对话框组件读取外观参数。
- **传统 View**：`YView` 依据同一 YUI 调色板和模块设置计算颜色、按钮对比度、字号、圆角、列表行高与间距；调色板按 Context/配置缓存。返回已打开的传统 View 页面时，如果外观参数改变，生命周期适配器会重建该页面，使既有 XML 控件重新加载外观。
- **XML 列表**：YNotify 的应用和事件列表保留 XML 结构，但绑定时根据 YUI 配置应用文字、卡片与行距；通用 View 页面通过 YViewLayout 获得共享参数。
- **特殊窗口**：截图选区、NFC 日志控制台、诊断终端、拖动排序和文件/下载器的专用交互尺寸可以保持业务要求；其普通文字、按钮和外围页面外观应使用 YUI。
- **验证**：`tools/verify_ui_framework.py`、`verify_yui_controls.py` 与 `verify_yui_content_compatibility.py` 在架构 CI 中执行，保护统一配置入口与功能交互边界。

以上共享配置只在同一个 Android 应用包的私有配置中继承；独立安装的 APK 不会自动跨应用同步 YSuite 的设置。

## General settings, language and home (2026-10-10)

The root YSuite settings screen now has a **General settings** destination, distinct from
YUI appearance and the shared functionality / Root / LSPosed area.

- **App language**: Follow system, English or Simplified Chinese. Android 13+ delegates to
  Android's per-app LocaleManager so that system App Languages and in-app selection stay in sync.
  On Android 12 / 12L the YSuite Application and all shared YComposeActivity feature screens
  inherit the host's localized Context; AppCompat screens also receive the requested locale.
  The user-facing choice is global to the integrated host, not a per-feature override.
  Independent APKs have independent package locales.
- **Home behavior**: hide disabled feature entries without disabling or removing their runtime
  configuration; show/hide search and diagnostics toolbar shortcuts. Module Management and shared
  diagnostics remain reachable regardless of shortcut visibility.
- **About and app management**: the installed version and a link to Android's App Info page.
- All four options reuse SuiteCommonSettings' existing validated preference store. General settings
  cannot be overridden by modules, and the **Reset shared log settings** action deliberately
  leaves language and home preferences unchanged.

Existing appearance controls and export/import continue to affect appearance only.

## Adjustable UI parameter audit (2026-10-10)

This work audited the YUI numeric setting definitions, native View and Compose control
implementations, product settings facades and visual limits in YFloat, YDownload and
the other bundled modules. The previous implementation had multiple unrelated layers
restricting values: UI slider ranges, validation before persistence, render-time fixed
minimums, template-specific padding, and old generated dimensions.

### Consolidated, controllable appearance

- 18 existing YUI dimensions now use much broader ranges from one `YSettingKey`
  validator. Values can be entered in exact 1dp / 1% increments and round-trip through JSON.
- Four additional parameter groups are exposed: page vertical spacing, visual icon size,
  list icon size and toolbar height. These are connected to their actual View/Compose consumers.
- Shared integer sliders (`YIntSliderField`) accept exact numeric values, and old Java View
  sliders (`YViewLayout.sliderSetting`) expose a numeric entry beside the slider.
- Phone and tablet list page margins use the same preference; no more unexplained
  tablet-only fixed default. Legacy toolbar, list icons, list row dimensions, icon touch
  slots and bottom sheet padding are no longer independently hard-coded.
- Generated YUI JSON/Kotlin/XML defaults are synchronized at button radius 12dp, horizontal
  padding 16dp and vertical padding 4dp, without changing existing user overrides.
- YFloat's icon size, border, opacity, slide interval, gesture timing/distance and text-menu
  item count use consistent input and runtime ranges.

### Deliberately retained constraints

- An Android button's text/content may impose an intrinsic minimum even if the requested
  visual height is smaller. Very small interactive targets are possible but may reduce
  accessibility and usability. The default remains standard, not compact.
- Operational ranges such as download concurrency, worker thread count, retry policy and
  polling frequencies are still bounded to avoid resource exhaustion. Numeric entry does
  not bypass those **functional safety** constraints.
- Feature-specific OCR, Root/LSPosed and NFC runtime parameters are not recast as UI
  appearance settings. A setting without a real consumer is not exposed as a fake control.

Automated coverage now checks shared validation, generated default alignment, real
appearance consumers and exact entry controls, as well as the previous integration checks.
