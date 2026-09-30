# YParam · 应用参数

YParam 是一个基于现代 **libxposed API 102** 的按应用 Android 运行环境管理器。核心原则：**先读取真实/默认值，只覆盖用户明确启用的字段；恢复时删除覆盖，让目标应用重新读取真实值。**

## 当前功能（v0.1.0）

- 应用列表、搜索、系统应用筛选、已修改筛选
- 动态请求 LSPosed 作用域
- 显示/分辨率：DPI、虚拟宽高、smallestWidthDp、screenWidthDp、screenHeightDp、fontScale、xDpi/yDpi、刷新率读取值
- 语言/地区：Locale (BCP-47)、时区、浅色/深色 Configuration
- 窗口：强制方向、FLAG_SECURE（允许/禁止截图）、保持屏幕常亮
- WebView：默认 User-Agent 覆盖
- 定位：固定经纬度、海拔、精度、速度、方向；随机半径 + 更新间隔作为第一种动态模拟模式
- 每个字段留空即恢复默认；支持整个应用一键恢复
- 首次修改前保存基线快照；原始信息/Manifest/系统 Configuration 诊断
- 快速模板：平板、紧凑、英国环境
- RemotePreferences 实时同步，配置层与 Hook 层分离

## Hook 覆盖面

YParam 当前同时覆盖常见读取路径，包括：

- `Resources.getDisplayMetrics()`
- `Resources.getConfiguration()`
- `Display.getMetrics()/getRealMetrics()/getSize()/getRealSize()`
- `WindowMetrics.getBounds()`
- `Locale.getDefault()` / `LocaleList.getDefault()`
- `TimeZone.getDefault()`
- `Location` 常见访问器
- `Activity` 方向与窗口标记
- `WebSettings.getDefaultUserAgent()`

没有设置的字段不会伪造，直接调用原始实现。

## 架构

```text
Manager App
 ├─ App list / search / diagnostics
 ├─ ConfigRepository
 └─ libxposed/service RemotePreferences
           │
           ▼
LSPosed / libxposed API 102
           │
           ▼
YParamModule → EnvironmentHooks → target app
```

## 参考项目

设计和兼容性研究参考了以下开源项目/思路，但 YParam 的源码为独立实现：

- libxposed/api、libxposed/service、libxposed/example
- DPIS（按应用 DPI / 字体 / 最小宽度）
- App Settings Reborn（按应用环境设置的功能边界）
- SpoofMyDevice（Configuration / WindowMetrics / Locale / timezone 思路）
- XposedFakeLocation（按应用定位配置思路）
- DarQ（按应用夜间模式）
- DisableFlagSecure（FLAG_SECURE 场景）

## 构建

项目使用 JDK 17、Android SDK 37、AGP 9.2.x、libxposed API/Service 102。GitHub Actions 会构建 Debug APK。

```bash
gradle :app:assembleDebug
```

## 使用

1. 安装 YParam 并在支持现代 libxposed API 的框架中启用模块。
2. 打开 YParam，选择应用。
3. 点击“加入 LSPosed 作用域”并批准。
4. 设置需要覆盖的参数并保存。
5. 重新启动目标应用进程以确保新增 Hook 完整生效。之后仅修改 RemotePreferences 的参数可被运行中的 Hook 实时读取。
6. 单项清空或点击“恢复这个应用全部默认”，即可让目标应用重新使用真实值。

> YParam 的定位模拟用于应用开发、兼容性验证与测试。它不会修改 `Location.isMock()` 等反检测接口，也不包含绕过安全/完整性检测的逻辑。

## 后续架构预留

配置模型已经为参数模拟器预留：固定值、随机值、更新间隔。后续可继续加入时间轴、路线、传感器、电量、网络状态、窗口变化场景等测试模拟器，而不改变现有“默认/覆盖/模拟”的配置语义。

## License

Apache-2.0
