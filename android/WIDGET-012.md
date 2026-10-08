# 安卓 0.1.2：小米桌面添加权限兼容

日期：2026-09-30（北京时间）。最终 run-id：`20260930-232136-915`。

## 本轮证据与结论

用户提供的 0.1.1 诊断为 Android 16 / API 36、Xiaomi 2410DPN6CC、桌面 `com.miui.home`，pin 支持为 true。系统识别 WideWidgetProvider 和 CompactWidgetProvider，两者 features=5，绑定 ID 为空。用户进一步确认：只出现应用自己的“已请求添加”文字，实际没有桌面确认框。

这证明两种 Provider 已在该手机上被系统识别，尚未观察到组件绑定；不能再把问题描述为尚未确认的“Provider 是否注册”。这些数据不能说明具体是权限、桌面策略还是其他兼容问题，也不证明布局渲染成功。

查到小米/红米开发者的同类复现：桌面可能静默受“桌面快捷方式”权限限制，不弹添加对话框。小米官方也说明系统“其他权限”中有该设置。0.1.1 没有声明 `com.android.launcher.permission.INSTALL_SHORTCUT`，本轮补充该兼容声明与人工检查入口。

**这是有依据的兼容修正，尚未在本用户的 HyperOS / Android 16 上证实根因或添加成功。** 本轮 ADB 无连接设备，手机结果仍需用户更新后确认。

## 修改范围

1. 新增 `com.android.launcher.permission.INSTALL_SHORTCUT` 清单声明。继续使用标准 `requestPinAppWidget`；没有发送旧的快捷方式安装广播，也没有创建替代时钟组件的应用图标。
2. 小米设备显示“检查‘桌面快捷方式’权限”按钮，通过标准 `ACTION_APPLICATION_DETAILS_SETTINGS` 打开本应用系统信息，提示到“其他权限”检查。只跳转页面，不代替用户切换权限。
3. 请求结果不再保证一定有确认框；对小米增加无新增时的权限检查提示。API 返回 true 仍不算放置成功。
4. 诊断补充桌面版本、兼容权限声明和 Android 普通权限结果。小米独立开关明确记录为 unknown，需手动查看；不把 `checkSelfPermission == GRANTED` 当成小米开关已经允许。
5. 包名、固定签名、作息计算、两种布局、刷新逻辑和依赖锁文件保持原有内容。版本为 0.1.2 / versionCode 3。Windows 工程未参与本轮修改。

未增加隐藏 API、硬编码 AppOps 编号、厂商商店审核标记、悬浮窗权限或后台弹窗权限。

## 实际执行与验证

在 `android/` 执行 `./scripts/build.ps1`，在新的 ASCII 路径副本中运行：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=1 --console=plain
```

构建副本：`C:\Users\admin\AppData\Local\PersonalDayAndroidToolchain\runs\20260930-232136-915`。

| 项目 | 本轮结果 |
|---|---|
| 核心测试 | 16 通过，0 失败 |
| Android 模块测试 | 18 通过，0 失败：集成 4、样式 3、添加流程 11 |
| lint | 0 错误、3 警告 |
| assembleDebug | 通过，58 个任务执行，57 秒，退出码 0 |
| APK v2 签名验证 | 通过，与 0.1.0 / 0.1.1 证书相同 |
| aapt 清单检查 | 包名/版本/minSdk/targetSdk 正确，新权限已进入 APK |
| adb devices -l | 无连接设备 |
| 小米桌面放置与实际显示 | 未验证 |

新增 3 项测试检查编译后的权限声明、Android 授权不等于小米独立授权，以及小米请求文案和已有作息/外观设置保留。原有测试全部重新运行。Robolectric 模拟 API 35，不模拟 MIUI/HyperOS 的权限管理或桌面；回调端到端测试的限制沿用 `WIDGET-011.md` 中的说明。

第一轮 `20260930-232035-695` 因新增测试对 nullable 的 requestedPermissions 未做空值处理而编译失败；修正测试后重新执行完整任务。失败日志保留，不计入成功结果。

lint 警告仍为 Robolectric 有新版、图标 v26 目录冗余、缺少 monochrome 图标。工具链沿用 Corretto JDK 17.0.20.1+12、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、SDK 36 revision 2、Build Tools 35.0.0；未安装新依赖或模拟器。

## APK 与审计材料

APK：`artifacts/20260930-232136-915/PersonalDay-Android-0.1.2-debug.apk`

- 包名 `com.personalday.android`，版本 0.1.2 / 3，minSdk 31、targetSdk 36
- 大小：2,568,307 字节
- SHA-256：`9A4BC0C78DE92A0BE18E4F895654B76F6117D522B0EE01D918542395A3C8D3F1`
- 签名证书 SHA-256：`ea4beb8b6254ae73582f5213154a16e4655fed69b9d704f32a0534b8309ed67b`

该 run-id 目录包含构建日志、源码 SHA-256 清单、测试 XML/HTML、lint、签名、权限和 ADB 输出。本文在最终构建后补充。`artifacts/PersonalDay-Android-0.1.2-source.zip` 为完整源码包（含 Wrapper，排除缓存、产物和密钥），`artifacts/SHA256SUMS-0.1.2.txt` 记录 APK 与源码包哈希。

## 用户验证步骤

1. 直接覆盖安装 0.1.2，无需卸载或清除桌面数据。
2. 打开个人时钟，点“检查‘桌面快捷方式’权限”。在本应用信息中的“其他权限”检查“桌面快捷方式 / 创建桌面快捷方式”，允许后返回应用。某些系统可从“隐私保护 → 其他权限”进入；名称以手机实际界面为准。
3. 再点一次“添加 4×2 组件”，检查桌面是否新增，也查看应用内的已绑定数量。
4. 若该项本已允许或仍无新增，反馈开关状态及新诊断（包含桌面版本）。若找不到选项，反馈实际可见文字；不要据此开启无关权限。

仅收到回调或看到非空绑定 ID，也仍需检查桌面实际画面；刷新、熄屏恢复等项目继续按 README 的真机清单验收。

## 来源与适用范围

- [小米官方：REDMI 上“其他权限”及桌面快捷方式设置](https://www.mi.com/tw/support/faq/details/KA-498878/)：证明存在厂商独立设置，不能证明小米 15 Pro 所有版本入口完全相同。
- [开发者对 requestPinAppWidget 在 Redmi 不弹窗的直接复现](https://stackoverflow.com/questions/57781688/app-widget-dialog-from-activity-not-showing-in-redmi-phones)：提供权限门控的排查依据，时间早于当前 HyperOS，不作为本机根因结论。
- [AnkiDroid 的小米快捷方式权限记录](https://github.com/ankidroid/Anki-Android/issues/18601)：为厂商独立权限及静默拒绝的补充线索；快捷方式与 AppWidget 并非同一个 API。
- [Android INSTALL_SHORTCUT 定义](https://developer.android.com/reference/android/Manifest.permission#INSTALL_SHORTCUT)：普通权限定义和旧广播限制，不意味着现代标准 AppWidget API 一律要求该权限。
- [标准组件 pin 流程](https://developer.android.com/develop/ui/views/appwidgets/discoverability)、[小米原生组件入口说明](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1591)。
