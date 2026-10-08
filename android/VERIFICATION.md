# 安卓 0.1.0 验证记录

日期：2026-09-30（北京时间）。最终构建 run-id：`20260930-220037-585`。

## 交付结论

已在 Windows 主机完成安卓 APK 构建、自动测试、lint 和 APK 签名验证。
**没有连接并授权的 Android 设备，未安装到小米 15 Pro，不能据此宣称手机功能或画面已验收。**

最终测试 APK：

`artifacts/20260930-220037-585/PersonalDay-Android-0.1.0-debug.apk`

- 包名：`com.personalday.android`
- 版本：`0.1.0` / versionCode 1
- 大小：2,552,363 字节
- SHA-256：`2915228C20AD663DF283FE912EF240D8205EDC3410B51920C794DFD1915A9E9A`
- APK v2 签名验证通过；固定本地 Android Debug 证书 SHA-256：`ea4beb8b6254ae73582f5213154a16e4655fed69b9d704f32a0534b8309ed67b`

## 实际执行与结果

在 `android/` 中执行：

```powershell
.\scripts\build.ps1
```

脚本将源码复制至 ASCII 路径，实际执行：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=1 --console=plain
```

最终命令退出码 0，`BUILD SUCCESSFUL in 46s`，58 个 Gradle task 执行。
依赖锁文件由前一轮 `build.ps1 -WriteLocks` 生成，最终轮使用已保存的锁文件。

| 检查 | 结果 | 能证明的范围 |
|---|---|---|
| 纯 Kotlin 核心测试 | 16 项通过，0 失败 | 时间边界、设置序列化、单一刷新循环、锁屏暂停和直接重算、显示差异去重 |
| Android 模块测试 | 7 项通过，0 失败 | 3 项样式规则；4 项 Robolectric API 35 宿主模拟测试 |
| Robolectric 具体检查 | 通过 | SharedPreferences 适配器读写；两种 RemoteViews 加载和 reapply；静音通知通道；设置页初始化 |
| Android lint | 0 错误、3 警告 | 静态 API、资源、清单检查；不是运行验收 |
| APK 构建 | 通过 | Kotlin、资源、Dex 和打包成功 |
| apksigner verify | 通过 | APK v2 签名和归档完整性 |
| aapt 权限检查 | 通过 | 仅 POST_NOTIFICATIONS、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE、RECEIVE_BOOT_COMPLETED |
| adb devices -l | 无设备 | 本轮没有手机运行证据 |

3 条 lint 警告为：Robolectric 已有更新版本、minSdk 31 下图标目录的 v26 后缀可省略、自适应图标未提供 monochrome 资源。保留计划中的固定依赖版本；这些警告不作为手机兼容性通过证据。

所有最终 XML 测试结果、构建日志、lint 报告、源码逐文件哈希、APK 签名及权限输出都保存在该 run-id 目录。`artifacts/toolchain.json` 记录工具下载哈希。

## 计算覆盖

- 默认 08:00—次日 00:00 的 08/12/14/16/20/次日00/次日08 时刻，以及起床前仍为休息时段。
- 10:00—次日 02:00 在午夜显示 21:00、87.50%，不归零；同日作息、跨年和闰年跨月。
- 入睡前 1 毫秒显示 23:59、99.99%；0.01% 边界按真实时长向下截断。
- 无效时间、起止相同、系统时间前后调整、重开或休眠后的直接计算。
- 纽约 23/25 小时日、7/9 小时清醒时段、跳时与重复时刻、夏令时导致无效时段。
- Lord Howe 半小时夏令时；默认产品时区固定北京时间。

## 工具链与遇到的问题

- 新准备：Corretto JDK 17.0.20.1+12、SDK command-line tools 19.0、平台 36 revision 2、Build Tools 35.0.0、Platform Tools 37.0.1。
- 构建：Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21；JUnit 4.13.2；Robolectric 4.16.1 仅用于测试，不打入产品。
- JDK 从 Corretto 官方源下载，官方 MD5 相符，`java.exe` 的 Amazon 签名状态为 Valid；本地 SHA-256 留档。
- Gradle 官方 SHA-256 校验相符。Android command-line tools 与 Google 仓库元数据中的 SHA-1 相符，另记录本地 SHA-256。
- 初始 PATH / 常见目录未发现 JDK、SDK、ADB 或 Gradle。工具放在独立的 `%LOCALAPPDATA%\PersonalDayAndroidToolchain`，未修改用户或系统 PATH，未安装 IDE、模拟器、NDK。
- 中文工程路径中，测试 class 文件已经生成，但 Gradle 测试进程报 ClassNotFoundException；增加 UTF-8 参数仍失败。相同源码在 ASCII 副本中通过。构建辅助脚本保留这一实际绕行方法，没有修改或移动原工程。
- SDK 安装器在 Windows 加密目录的 CopyFileEx 复制阶段长时间停留；中断后，将已解包的平台文件按字节写入预定 SDK 目录。随后 `sdkmanager --list_installed` 正确识别平台 revision 2，后续 Android 构建成功。没有修改系统加密或安全设置。

## 小米 15 Pro 待验清单

下面全部尚未执行。安装 APK 后记录手机 Android/HyperOS/桌面版本及实际结果：

1. 从桌面列表或应用按钮添加 4×2、2×2，检查标签、字色、浅灰透明度、刻度和休息文字是否完整。
2. 桌面停留 10 分钟，与北京时间和独立计算结果核对；检查 0.01% 的变化没有提前或持续卡住。
3. 熄屏 30 分钟再解锁；服务存活时目标为 2 秒内恢复正确数据。检查锁屏期间没有周期刷新。
4. 分别测试切换应用、清理最近任务、系统回收、强行停止和重启，记录服务状态，不能将其中一种结果推广到其他场景。
5. 拒绝通知后为手动模式；关闭刷新显示暂停；删除最后一个组件后服务和通知退出；增加多个组件仍只有一个服务。
6. 保存非默认作息/透明度/字色，结束进程后重新打开，确认真实落盘与恢复。
7. 在相同亮度和使用条件下对比启用/关闭刷新的耗电；短时电量百分比不作为低耗电结论。

应用没有后台永驻保证。系统强杀进程时，桌面可能保留最后一帧，进程无法立即将该帧改为暂停提示。普通组件没有可靠的可见回调，亮屏解锁后打开其他应用仍可能刷新。

## 桌面工程保护

已核对开始时记录的 Windows 源码、锁文件、安装 EXE 和自启注册值：收尾比较均未变化。本轮未向 Windows 时钟发送控制命令，也未写入其设置或恢复旧备份。

开始时记录到安装版 PID 28524；中途一次查询没有检测到安装版进程，最终查询又检测到 1 个安装版进程。中途设置哈希与开始时相同，最终查询时 2 个设置文件哈希发生变化，具体原因未确定。本轮没有调用 Windows 时钟的停止/启动或设置命令，没有擅自重启或用旧备份覆盖现场。最终现场记录见 `artifacts/desktop-after.json`；此记录与安卓验证分开。
