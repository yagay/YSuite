# YSuite 单一 UI 与公共服务规范

## 唯一的视觉样式源

* **尺寸**：`libs/yui/yui_tokens.json`
* **静态亮/暗配色**：`libs/yui/yui_palette.json`
* **生成器**：`python3 tools/generate_yui_tokens.py`
* **审计**：`python3 tools/generate_yui_tokens.py --check` 和 `python3 tools/verify_yui_single_source.py`

生成器输出的 Kotlin `YDimens`、`YUiPalette` 和 Android View `dimen/color` 资源均视为自动产物，不手工修改。Android 12+ 的动态取色可以覆盖静态配色。

**YFiles / YDownload 没有第二套按钮、输入框、设置行和静态主题。** 它们的 `next/core/designsystem` 只保留与旧调用约定兼容的轻量转发层，实际调用 `libs/yui`：

| 组件/职责 | 公共实现 |
|---|---|
| 主题颜色、字体、圆角 | `YTheme` |
| 主/次按钮和文字按钮 | `YPrimaryButton`、`YSecondaryButton`、`YPrimaryActionButton`、`YSecondaryActionButton`、`YTextActionButton` |
| 设置行、开关 | `YListItem`、`YSwitchItem` |
| 卡片、分组 | `YCard`、`YSectionTitle` |
| 搜索、输入、筛选 | `YTextField`、`YFilterBar` |
| 确认弹窗、状态徽标 | `YConfirmDialog`、`YStatusPill` |
| Java/View 组件 | `YView`、`YViewLayout`；同一份生成的 Android 资源 |

YFiles 和 YDownload 仍可以设计不同的**产品页面结构和专用业务控件**（如目录树、文件预览、下载速度图、进度图等），但要从 YUI 读取基础颜色和尺寸，不应重建一个通用按钮/卡片/主题系统。

## 共享业务基础设施

两个模块保留文件操作、下载调度等不同业务逻辑，公共部分复用：

* Root、Shizuku、LSPosed Hook 能力：`next/core/platform/api` 和 `next/core/platform/android`
* 运行时可重新绑定的 Hook 通道：`BindableHookGateway`
* 日志结构与转发：`next/core/logging/api`，Android 输出：`next/core/logging/android`
* 模块生命周期、导航、权限接口：相应 `next/core/runtime`、`next/core/ui`、`next/core/permissions/api` 模块。

不要将具有不同格式/生命周期的下载任务数据库与文件浏览器工作区配置强行合并；统一的是**接口和底层服务**，不是不同业务的数据模型。

### 常用调节值

`button_padding_horizontal`：按钮文字左右留白；`button_padding_vertical`：上下留白；`button_visual_height`：可见按钮高度；`button_radius`：圆角；`option_row_height`：设置行高度；`card_padding`、`section_gap`：卡片与列表密度；`touch_target`：触控区域，须至少 48dp。

以后修改只更改 JSON 并重新运行生成器、CI；不要批量在各个 feature 的 Compose 源码中替换硬编码值。
