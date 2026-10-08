# 安卓 0.1.3：Android 16 原生模拟器验证

测试时间：2026-09-30 至 2026-10-01，北京时间。最终 APK 构建 run-id：`20260930-234725-584`。

## 结论

已在 Android 16 / API 36 的原生 AOSP 模拟器实际安装、点击、添加并运行小组件。桌面将两种入口识别为 **4 格宽 × 2 格高**和 **2 格宽 × 1 格高**，两种组件均显示并更新。这是运行中的 Launcher3 / AppWidgetHost 证据，独立于 Robolectric 测试。

**未验证小米 HyperOS 添加成功。** 用户的 0.1.1 诊断确认 Provider 注册正常、pin 支持为 true，但无绑定且不弹窗。0.1.3 保留桌面快捷方式权限兼容声明和设置入口；该手机的静默拒绝原因仍未证实。

## 本轮发现和修复

### 不透明背景

在 0.1.2 的模拟器画面中，组件已添加，但背景是近白色实心块，白字难以看清。原因是 `ImageView.setColorFilter(int)` 默认使用 SRC_ATOP，颜色中的 alpha 并不会降低原始不透明 drawable 的整体 alpha。此前的样式数值测试未覆盖这个实际混合效果。

0.1.3 将灰色与图片透明度分别设置：颜色为不透明 `#D8D8D8`，默认 `setImageAlpha(46)`（18% 经 8 位量化）。预览 XML 显式使用 `src_in`。新增 RemoteViews 测试验证 0%、18%、100% 的图片 alpha 和文字不透明度；模拟器截图确认背景能透出壁纸。

此处仍是半透明背景，**没有实时背景磨砂**。

### 占格和紧凑版

0.1.2 在该 Launcher3 上实际变成 4×3 / 3×3，最小尺寸约束过大。已降低尺寸约束，并按用户最新要求把紧凑版改为 **2×1**：两种时间并排，保留标签、两位小数百分比和 25/50/75% 刻度。紧凑版休息文字缩短，完整说明保留在无障碍描述中。

0.1.3 的添加确认页实际显示 4×2 / 2×1；当前 AVD 上桌面内容边界分别为 524×428 px 和 250×202 px。像素尺寸、边距和外部圆角由具体桌面决定。此前已放置的组件不会保证自动缩成新尺寸，需要调整或重新添加。

## 实际运行检查

| 场景 | 实际观察 |
|---|---|
| APK 覆盖安装 | 从 0.1.2 更新为 0.1.3 成功；后续新建测试前清空了仅用于实验的 Launcher3 桌面数据 |
| 4×2、2×1 添加 | 实际点击应用按钮，出现 Launcher3 确认页，点击 ADD TO HOME SCREEN 后显示在桌面 |
| 成功回调 | 应用显示“桌面已确认添加”，系统绑定 2 个组件；不再只是直接调用接收器的模拟测试 |
| 背景、标签、刻度 | 截图中能看见壁纸；两套布局的时间、标签、百分比及三处刻度可见 |
| 持续刷新 | 两个组件同步从 99.26% 更新到 99.36%，两个采样点相隔约 56 秒；时间也变化 |
| 通知拒绝 | 在系统权限框点击拒绝，应用保持手动刷新并显示说明 |
| 通知允许 | 再次开启并允许后，系统记录仅一个前台刷新服务，服务类型 specialUse |
| 无声通知 | 系统 channel importance=2、sound=null、vibration=false，关闭刷新后活动通知消失 |
| 短时熄屏 | 约 12 秒，系统记录 Asleep；期间 AppWidget dump 未变化，解锁后两个组件更新到 99.39% |
| 午夜休息 | 控制模拟器系统时间到北京时间 00:00：常规 00:00、个人 24:00、100.00%，并显示下次起床 |
| 新清醒日 | 调到北京时间 08:00：个人 00:00、0.00% |
| 中午 | 调到北京时间 12:00：个人 06:00、25.00% |
| 字色 | 通过设置页选择黑色并保存，截图确认变化；再选择白色并保存 |
| 强行停止与重新打开 | force-stop 后服务为空；重新打开应用后，已保存的白字、18% 和刷新开关仍在，前台服务恢复 |
| 关闭刷新 | 通过设置页关闭并保存；两种组件显示暂停，系统服务和活动通知消失 |
| 实际安装文件 | 模拟器 base.apk 的 SHA-256 与交付 APK 一致 |

上述时间跳转仅修改隔离 AVD 的系统时钟，没有改变电脑或实体手机时间。测试后恢复 AVD 自动校时，关闭模拟器。crash buffer 查询没有返回条目；不将此扩大为长期稳定性保证。

实拍截图（模拟器测试时间 12:00）：

![4×2 和 2×1，测试时间 12:00](artifacts/emulator-api36-013/run-013/18-noon.png)

## 自动测试与记录

- 最终构建重新运行 **35 项 JUnit 测试**：核心 16、Android 集成 5、样式 3、添加流程 11；0 失败、0 错误。
- lint：0 错误、3 条原有警告；构建 42 秒，58 个任务执行，退出码 0。
- 另对原生运行保存的 XML 和 dumpsys 输出核对了 24 个条件，全部满足。这是对上述现场证据的检查，不是新增 24 个独立端到端测试场景。
- 自动测试仍使用 Robolectric API 35；本轮运行的模拟器为 API 36，两者分别记录。
- 截图、界面 XML、主机 UTC 命令时间线、设备截图时间范围、服务/通知/绑定输出、安装 APK 哈希位于 `artifacts/emulator-api36-013/`。其中 `run-012` 保留不透明背景的失败现场，`run-013` 为最终版本；`evidence-sha256.json` 为逐文件哈希。
- `scripts/emulator-tools.ps1` 保存采样和点击方法，只允许 `emulator-*` 且检查 `ro.kernel.qemu=1`，拒绝实体设备。

## 尚未验证

小米 15 Pro 的实际放置、HyperOS 权限和后台策略；熄屏 30 分钟与 2 秒恢复指标；10 分钟桌面连续观察；重启恢复；最近任务清理、系统回收、删除最后一个组件的真机行为；耗电；其他屏幕/桌面网格/放大字体和横屏。自动字色只参考整体壁纸提示，不能保证每个放置位置都有足够对比度，可手动覆盖。

## 工具与命令

构建仍使用 JDK 17、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、SDK 36、Build Tools 35.0.0，锁文件未升级，worker=1。

新增独立模拟器目录：`F:\PersonalDayAndroidEmulator`。没有安装 Android Studio 或 NDK，没有修改 Windows 功能或重启电脑。通过原生能力查询及 `emulator -accel-check` 确认 WHPX 可用。

- Android Emulator **37.1.11.0 / build 15917651**，Windows x64。
- AOSP `system-images;android-36;default;x86_64` **revision 2**，扩展级别 17。
- AVD `personalday_api36`：720×1560、320 dpi、1536 MB RAM、2 核、software GPU、WHPX、无窗口、无快照。
- SDK manager 的整包下载曾停留，后用官方 URL 分段下载并核对 Google 仓库的 SHA-1；解压后 `sdkmanager --list_installed` 正确识别版本。
- 模拟器 ZIP SHA-256：`5FF441F3B12ACE9B13E9CF96FB0007D233967718652A8110705E995AC47BFEB7`
- 系统镜像 ZIP SHA-256：`E1B9D9FB665001EF27B16E57D8762A2D54AEC6BFF617E17506EDB8676667B9DA`

实际构建：

```powershell
.\scripts\build.ps1
# ASCII 副本内执行：
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=1 --console=plain
```

主要运行命令（完整参数与点击记录在 evidence 的 launch.json / commands.jsonl）：

```powershell
emulator -avd personalday_api36 -port 5554 -no-window -no-audio -no-snapshot -no-boot-anim -no-metrics -gpu software -memory 1536 -cores 2 -timezone Asia/Shanghai
adb -s emulator-5554 install -r PersonalDay-Android-0.1.3-debug.apk
adb -s emulator-5554 shell am start -W -n com.personalday.android/.SettingsActivity
adb -s emulator-5554 shell uiautomator dump /sdcard/personalday-ui.xml
adb -s emulator-5554 shell screencap -p /sdcard/personalday-screen.png
adb -s emulator-5554 emu kill
```

## 交付

- APK：`artifacts/20260930-234725-584/PersonalDay-Android-0.1.3-debug.apk`
- 大小：2,569,899 字节；包名 `com.personalday.android`；versionCode 4；minSdk 31、targetSdk 36。
- APK SHA-256：`B47516F9F657BFC503F74FD81E89A95D448A7FFC335C0A7A27DE92BEA29BB12D`
- 签名 v2 验证通过，证书 SHA-256：`ea4beb8b6254ae73582f5213154a16e4655fed69b9d704f32a0534b8309ed67b`，可覆盖本项目旧版。
- 源码 ZIP：`artifacts/PersonalDay-Android-0.1.3-source.zip`；校验清单：`artifacts/SHA256SUMS-0.1.3.txt`。密钥、SDK、模拟器磁盘和构建缓存不在源码包内。

小米安装后：点“检查‘桌面快捷方式’权限”，到本应用“其他权限”中检查并允许，再返回点击添加。若仍失败，反馈该开关状态和新诊断，不能凭本次 AOSP 成功就认定小米问题已解决。

## 依据

- [ImageView 的 SRC_ATOP 和 setImageAlpha](https://developer.android.com/reference/android/widget/ImageView#setColorFilter(int))
- [Android 小组件尺寸约束](https://developer.android.com/develop/ui/views/appwidgets/layouts)
- [Android Emulator 命令与运行](https://developer.android.com/studio/run/emulator-commandline)
- [Windows 模拟器硬件加速](https://developer.android.com/studio/run/emulator-acceleration)
