# YSuite 共享模块整合审计

主要功能模块：YDiag、YEntryCleaner、YFloat、YMiniGuard、YNFC、YNotify、
YParam、YPower、YTaskManager、YFiles、YDownload。

## 唯一公共基础设施

| 职责 | 公共代码 | 保留业务 |
| --- | --- | --- |
| Feature 宿主、Root、能力与生命周期 | libs/yapi + libs/ycore | 操作与业务数据 |
| Root 结果/错误/文本行转换 | FeatureRootCommands | 模块既有接口签名 |
| 诊断缓存 | FeatureLogBuffer | 每模块显示格式 |
| 诊断 ZIP 和 Downloads 发布 | FeatureDiagnosticArchive | 各模块证据采集 |
| 本地常规设置 | FeatureSettings | 原文件名、键和默认值 |
| UI/主题/组件 | libs/yui | 页面信息架构 |
| 新模块 Root | DefaultPlatformServices(featureId) → FeatureServices | 无宿主时兼容 |
| Hook 传输 | next/core/platform/api / XposedHostBridge | 各 Hook 的远端协议 |

所有 apps/*/feature/build.gradle.kts 的 YUI/API 改为 project(":ui") /
project(":api")，不再通过远程 JitPack 引入可能不一致的公共源码。

## 必须保留独立的业务边界

- YNotify：加密 Room/SQLCipher、FTS、通知历史协议。
- YEntryCleaner：Intent 规则、组件清理与排序状态。
- YFloat：OCR、悬浮窗、Google Lens 专有 Hook。
- YNFC：NFC/RF/NCI 指令与厂商系统服务。
- YPower 和 YMiniGuard：设备厂商专属增强及 Hook。
- YFiles：文件系统、网络与云协议、传输队列。
- YDownload：下载调度、重试、断点续传及任务存储。

统一权限、Root、日志、配置、Host API、UI，而不要强行合并不同的持久化模型、
Hook 作用域或后台服务生命周期。旧配置键值不应在无迁移计划时更名。

## 验证

tools/verify_shared_infrastructure.py 校验单一依赖和公共组件使用。
CI 先执行 :api:testDebugUnitTest 与两款新版模块的测试，再构建 APK。
