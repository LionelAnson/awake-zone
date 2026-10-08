# 安卓 0.1.1：桌面组件添加流程修正

日期：2026-09-30（北京时间）。最终 run-id：`20260930-223146-005`。

## 结论与边界

用户报告：应用内点添加没有新增，桌面组件列表中也找不到。已修正添加流程中的反馈和首次更新缺口，提供可覆盖安装的 0.1.1 APK。

**尚未确认小米 15 Pro 上的具体原因，也没有真机添加成功的证据。** 本轮 `adb devices -l` 为空；构建、模拟测试通过不能代替 HyperOS 桌面验证。若仍失败，应先取得新版提供的本地诊断文本，再决定下一项修正。

## 已查明与修改

- 旧流程没有确认 `requestPinAppWidget` 的返回值，也没有添加成功回调，无法区分拒绝、等待确认和实际绑定。新版检查返回值、处理异常，通过显式 PendingIntent 接收成功回调；返回 true 仅显示请求已提交。
- 两种组件都已有有效默认作息。元数据增加 `configuration_optional`，保留 `reconfigurable`；首次放置可使用默认或已保存设置，之后再调整。
- Provider 的首次更新和配置完成路径直接使用系统传入的组件 ID，避免组件枚举暂未更新时漏掉首帧。成功回调也主动刷新已绑定组件。
- 添加按钮移到设置页顶部，显示系统识别到的组件种类数、已绑定数量和最近请求状态。新增“添加帮助 / 诊断信息”，支持复制系统版本、桌面包名、当前应用的 Provider 注册与绑定状态。
- 诊断只在本地生成，不收集序列号或全量应用列表，不上传。包名和固定签名不变，版本升为 0.1.1 / versionCode 2。

这些代码问题并不足以解释“列表没有组件”的全部情况。小米官方区分小部件中心和安卓原生组件入口；当前用户使用的具体入口、桌面版本、布局限制及系统是否识别 Provider，仍需手机上的诊断结果确认。没有修改桌面数据、系统权限或省电设置。

## 实际执行

在 `android/` 中执行：

```powershell
.\scripts\build.ps1
```

脚本在新的 ASCII 路径副本中执行同一 Wrapper，限制一个 worker：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=1 --console=plain
```

最终退出码 0，`BUILD SUCCESSFUL in 54s`，58 个任务执行。实际副本位于：

`C:\Users\admin\AppData\Local\PersonalDayAndroidToolchain\runs\20260930-223146-005`

另执行 `apksigner verify --verbose --print-certs`、`aapt dump badging`、`aapt dump permissions` 和 `adb devices -l`。

沿用 JDK Corretto 17.0.20.1+12、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、SDK 36 revision 2、Build Tools 35.0.0。JUnit 4.13.2、Robolectric 4.16.1（模拟 API 35）及依赖锁文件未升级。

## 自动检查

| 检查 | 本轮结果 | 范围 |
|---|---|---|
| 核心模块测试 | 16 通过，0 失败 | 时间边界、时区/DST、设置、刷新循环 |
| 原有 Android 模块测试 | 7 通过，0 失败 | 样式与 Robolectric 集成检查 |
| 新增添加流程测试 | 8 通过，0 失败 | 下述请求与更新逻辑 |
| Android lint | 0 错误、3 警告 | 静态检查 |
| APK 构建及 v2 签名 | 通过 | 可生成有效签名归档 |
| 小米 15 Pro 桌面 | 未验证 | 没有连接并授权的手机 |

新增测试覆盖：不支持添加、接口返回 false、返回 true 尚未确认、成功回调处理后发布首帧、确认但尚未枚举到绑定、平台抛异常、枚举滞后时按明确 ID 更新、编译后两种 Provider 的可选配置标志。

测试限制：Robolectric 中 `PendingIntent.send()` 未分发到清单声明的接收器，前两次测试运行因此失败，记录保留在 `20260930-222803-509` 与 `20260930-223020-807`。最终测试先核验 PendingIntent 的显式目标、action、immutable 属性，以及编译清单中的接收器 enabled/exported 设置，再直接调用接收器入口验证处理逻辑。**没有把这种模拟测试算作真实桌面到回调的端到端交付验证。**

lint 的 3 条警告是：Robolectric 有新版、minSdk 31 下图标目录的 v26 后缀冗余、缺少 monochrome 图标。未因本次修正更新固定依赖。

## APK 与证据

APK：`artifacts/20260930-223146-005/PersonalDay-Android-0.1.1-debug.apk`

- 包名：`com.personalday.android`
- 版本：0.1.1 / 2，minSdk 31、targetSdk 36
- 大小：2,563,963 字节
- SHA-256：`2252C45D8309C2E0EEB9E3A76C262859B8E23A51D6954471BDB7F086F091E178`
- 签名证书 SHA-256：`ea4beb8b6254ae73582f5213154a16e4655fed69b9d704f32a0534b8309ed67b`，与交付的 0.1.0 相同
- 权限仍为 POST_NOTIFICATIONS、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE、RECEIVE_BOOT_COMPLETED

本 run-id 目录保留构建日志、测试 XML/HTML、lint、签名/权限输出、run.json 和构建输入逐文件 SHA-256。本文在构建后补充，不属于编译输入。完整源码 ZIP 和其 SHA-256 清单位于 `artifacts/`；源码包排除构建缓存、产物与签名密钥。

## 手机上的下一步

1. 用 0.1.1 APK 直接覆盖旧版，无需卸载或清除数据。
2. 打开个人时钟，查看顶部识别状态，再点“添加 4×2 组件”并确认桌面弹窗。
3. 若仍无组件，点“添加帮助 / 诊断信息”→“复制诊断信息”，反馈文字。先核对系统是否识别两种 Provider、是否支持 pin、是否有已绑定 ID。
4. 添加成功后再继续 README / 0.1.0 验证记录中的刷新、熄屏恢复、通知、透明度和后台行为真机检查；这些仍未验收。

本轮只编辑安卓工程，不向 Windows 个人时钟发送控制命令，不恢复或覆盖用户设置。

## 依据

- [Android requestPinAppWidget：返回值、成功回调、配置页行为](https://developer.android.com/reference/android/appwidget/AppWidgetManager#requestPinAppWidget(android.content.ComponentName,%20android.os.Bundle,%20android.app.PendingIntent))
- [Android 可选配置与配置生命周期](https://developer.android.com/develop/ui/views/appwidgets/configuration)
- [小米：小部件中心与安卓原生组件入口区别](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1591)
