# 醒时区安卓版

独立的 Kotlin Android 工程，包名 `com.personalday.android`，应用名称为“醒时区”。不读取或修改 Windows 版的设置。

## 功能

- 4×2、2×1 标准 AppWidget（横向格数 × 纵向格数），点击进入设置；桌面决定实际像素尺寸和可放置位置。
- 北京时间、个人时钟、两位小数百分比、25/50/75% 刻度。所有时间来自同一计算快照。
- 默认作息为 08:00—24:00（次日 00:00）；用户可在应用中分别设置实际预期起床和入睡时间。起床时开始新清醒日，计划入睡后停在 24:00、100.00%。结束、下次起床和暂停说明显示在应用状态区及无障碍描述中，桌面保持三行。
- 双列布局，默认全透明：左侧为时间、星期及上下午、月日；右侧为个人时间、进度条、两位小数百分比。常规时间固定使用 24 小时制（HH:mm），不受系统 12/24 小时制设置影响，日期和作息继续使用北京时间。左右主时钟字号相同；2×1 按比例缩小。
- 应用内新增独立“设计”页，提供透明／纯色背景、背景不透明度、字色、三行字号和不透明度、进度条粗细。左右同一行联动，所有组件共享设置；预览和桌面共用原生Canvas。可切换4×2／2×1预览，深浅观察底色不属于组件。
- 字色可选自动、黑、白或自定义 RGB。默认第一行不透明度 **100%**，下面两行 **50%**，可在设计页分别调整。采用已通过的放大版排版（字号增加16%、笔画略加粗、收紧外围留白），使用 Roboto 数字和微软雅黑 Light，字体随 APK 打包；仍不称为已确认的小米原字体。设置页保留华文中宋。外观操作在应用内“设计”页完成，编辑时只更新草稿预览，点击“保存并应用”更新全部组件。字号60—110%，100%为0.1.7大小；时间变化不改变字号。
- 所有组件共用本地设置；不含账号、网络服务、云同步、睡眠检测或统计。

## 手机上安装与使用

1. 将交付的 `PersonalDay-Android-0.1.11-debug.apk` 复制到手机，点开安装。可直接覆盖本项目此前版本，无需卸载、清除数据。
2. 打开“醒时区”，设置计划起床和计划入睡时间，打开“持续刷新”，点击“保存并应用”，允许无声通知。
3. 点击页面顶部的“添加 4×2 组件”或“添加 2×1 组件”。若出现确认框，确认添加；部分桌面可能直接添加。组件可使用默认作息直接添加，之后再改设置；也可长按桌面，在安卓小组件列表查找“醒时区”。小米小部件中心与安卓原生组件列表是不同入口，名称随桌面版本变化。
4. 拒绝通知时保存作息和外观，保持手动模式。应用状态区及组件无障碍说明显示“刷新已暂停”；点击组件后可立即刷新一次。
5. 小米后台策略可能暂停服务。如遇停更，在系统应用设置检查自启动和省电限制；本应用不代改系统策略。强行停止后需要重新打开应用。

### 点添加没有出现组件

页面在添加按钮下显示“系统识别到 N 种组件 / 已绑定桌面 N 个”，并显示添加请求是否被拒绝、是否仍在等待桌面确认。`requestPinAppWidget` 返回 true 只表示请求已提交，应用不会据此声称已添加。

**小米没有弹窗且桌面没有新增时**：0.1.2 补充了 `com.android.launcher.permission.INSTALL_SHORTCUT` 兼容声明。点击“检查‘桌面快捷方式’权限”，在个人时钟系统应用信息的“其他权限”中检查“桌面快捷方式 / 创建桌面快捷方式”；允许后返回应用，再点添加。也可在系统“隐私保护 → 其他权限”中查找个人时钟。入口取决于系统版本，未找到时反馈实际可见的选项。该按钮只打开设置页，不代替用户授权；普通 Android 权限显示已授予，也不能证明小米的独立开关已经允许。

如果仍无法添加，点击“添加帮助 / 诊断信息”→“复制诊断信息”，将文字反馈给开发者，并说明上述开关状态。信息仅在本地生成，包含系统版本、桌面名称/版本、当前应用的组件注册/绑定情况、兼容权限声明与最近结果，不读取设备序列号或其他应用列表，不自动上传。无需卸载应用、清除小米桌面数据或调整无关权限、省电设置。

测试 APK 使用本机固定 Android debug 签名。该密钥位于用户目录的 `.android/debug.keystore`，不包含在源码中；后续更新须保留同一签名。其他机器使用不同密钥构建的 APK 不能直接覆盖此 APK。

## 开发环境

| 项目 | 锁定版本 |
|---|---|
| JDK | 17；本机 Corretto 17.0.20.1+12 |
| Gradle | 8.13，Wrapper 校验 SHA-256 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.21 |
| compileSdk / targetSdk | 36 / 36 |
| minSdk | 31（Android 12） |
| Build Tools | 35.0.0 |
| JUnit | 4.13.2 |
| Robolectric（仅宿主测试） | 4.16.1，模拟 API 35 |

安装 JDK 17、Android SDK command-line tools，然后安装 `platforms;android-36`、`build-tools;35.0.0` 和 `platform-tools`。无需 Android Studio、NDK 或模拟器。首次构建需要访问 Google Maven、Maven Central 和 Gradle 分发站。

本机工具单独放在 `%LOCALAPPDATA%\PersonalDayAndroidToolchain`；没有修改系统 PATH。在 `android` 目录执行：

```powershell
. .\scripts\enter-env.ps1
.\scripts\build.ps1
adb devices -l
# 仅在设备已经连接并授权后安装：
adb install -r .\artifacts\<run-id>\PersonalDay-Android-0.1.10-debug.apk
```

本机中文路径触发了 JDK 17 / Gradle 测试进程的类路径加载失败，即使指定 UTF-8 仍失败。`build.ps1` 为每次构建创建一个英文路径副本，使用同一 Wrapper 执行以下任务，并将 APK、测试报告和源码哈希复制回 `artifacts/<run-id>/`。它不删除或移动原始源码。

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug --max-workers=1
.\gradlew.bat :app:assembleDebug --max-workers=1
```

其他机器设置 `JAVA_HOME`、`ANDROID_HOME`，在 ASCII 路径可直接执行上述命令。Linux/macOS 使用 `./gradlew`；没有在这些系统上运行验证。本机的 `enter-env.ps1` 只设置当前 PowerShell 进程环境。

Gradle 限制一个 worker、JVM 最大堆 1536 MB、测试进程最大堆 512 MB、Kotlin 编译在同一进程中完成。`android.overridePathCheck=true` 可绕过 AGP 路径检查，但不能解决上述 JDK 问题；本机应使用构建脚本。

## 结构与计算契约

- `core`：纯 Kotlin/java.time，`calculateDay(schedule, now, zone)` 可注入 Instant 和时区；产品固定北京时间。按日期构造边界，时间戳计算实际长度；缺失时刻向前平移，重复时刻选第一次，与桌面 Temporal 的 compatible 规则一致。
- `app`：原生 Activity 设置、SharedPreferences、RemoteViews、两个组件 Provider、一个前台刷新服务。
- 百分比和进度条使用同一万分比整数；百分比和个人分钟向下取整，结束前不会提前显示完成。
- `RefreshLoop` 只持有一个待执行回调，动态监听亮灭屏、解锁及校时。亮屏且解锁每秒检查，显示内容变化才提交 RemoteViews；关闭刷新或无组件时停止。
- 首次启用从可见设置页启动服务；Android 14+ 使用 `specialUse` 类型。启动被系统拒绝会记录原因并显示暂停状态。重启后只在已启用且仍有组件时尝试恢复。
- 不通过周期闹钟唤醒，不申请屏幕采集、无障碍、悬浮窗、全盘读取、网络或唤醒锁权限。`updatePeriodMillis=0`，不依赖系统 30 分钟轮询。
- 普通组件没有可靠的桌面可见回调：屏幕亮起并解锁后，即使打开其他应用也可能继续刷新。系统强行终止进程时，已经提交的 RemoteViews 可能暂留，无法保证即时标记暂停。

## 验证边界

0.1.10：刷新对齐系统整秒；解锁状态延迟时最多重试2秒，锁屏时不绘制；服务存活期间监听系统分钟广播以重新启动刷新循环。打开应用前保存服务状态和最近成功提交组件的时间（最多每分钟持久记录一次），可从“添加帮助 / 诊断信息”复制。提交成功不能证明桌面已经显示；这些改进不能保证被 HyperOS 终止的服务自行恢复。

0.1.9 仅将桌面和设计预览的常规时间固定为 `HH:mm`。本轮运行24项核心测试、35项应用宿主测试，全部通过，包含系统设为12小时制时预览仍显示16:49的检查；构建成功，lint 0错误、23警告。本轮未重新运行模拟器或小米真机视觉验收。构建记录：`artifacts/20261001-230447-813/`。

0.1.8 的设计页和本轮验证见 [DESIGN-018.md](DESIGN-018.md)；基础排版历史见 [TYPOGRAPHY-017.md](TYPOGRAPHY-017.md)。小米同款字体、当前 HyperOS 真机显示和后台恢复仍未验证。桌面组件在应用进程内绘图后通过 RemoteViews 发送，保留整体无障碍描述和点击设置。旧版 [LAYOUT-015.md](LAYOUT-015.md)、[THEME-014.md](THEME-014.md) 不作为新版验证证据。

0.1.3 的原生模拟器记录见 `EMULATOR-013.md`；权限兼容背景见 `WIDGET-012.md`，更早记录见 `WIDGET-011.md` 和 `VERIFICATION.md`。用户在 0.1.1 的诊断确认两种 Provider 已被系统识别，但没有绑定 ID，点击添加也没有弹窗；小米权限兼容仍需手机验证。JVM 测试验证计算、持久化抽象和刷新调度；Android 编译和 lint 检查 API/资源/清单。它们不能证明真实桌面已正常显示、通知已出现或 HyperOS 不会回收进程。

额外的 Robolectric 测试检查 SharedPreferences 适配器、RemoteViews 加载与重用、静音通知通道和设置页初始化。它在宿主机模拟 Android API，不等于手机进程重启后的持久化、实际画面、权限交互或前台服务生命周期验收。当前没有连接的手机时，不将真机项目标为通过。

## 参考

- [AGP 8.13 兼容表](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Android 组件更新](https://developer.android.com/develop/ui/views/appwidgets/advanced)
- [前台服务类型 specialUse](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [后台启动限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [壁纸颜色提示](https://developer.android.com/reference/android/app/WallpaperColors)
- [添加接口及配置页规则](https://developer.android.com/reference/android/appwidget/AppWidgetManager#requestPinAppWidget(android.content.ComponentName,%20android.os.Bundle,%20android.app.PendingIntent))
- [小米小部件与安卓原生组件的入口区别](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1591)
- [小米官方：其他权限中的桌面快捷方式设置（REDMI 示例，入口可能有差异）](https://www.mi.com/tw/support/faq/details/KA-498878/)
- [Android INSTALL_SHORTCUT 权限定义](https://developer.android.com/reference/android/Manifest.permission#INSTALL_SHORTCUT)

本版本用于个人侧载测试。若将来上架，应单独评估商店对 specialUse 前台服务的审核要求。
