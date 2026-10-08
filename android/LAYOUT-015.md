# 0.1.5 透明双列布局测试版

## 范围与未完成项

用户提供小米“时钟·数字时钟”参考图，并补充时钟版本为 17.71.0。当前不能连接手机，用户已同意“先完成布局，字体留待核对”。本版暂用系统 sans-serif、字重 100；它不是已确认的小米同款字体。Android 模拟器中中文回退字体明显较粗。设置页和诊断信息均显示这项限制。

已实现：

- 4×2 和 2×1 使用透明双列图形，按桌面分配的尺寸等比例缩放；左右主时间字号相同。
- 左列：常规时间、中文星期及上下午、MM/dd 日期。常规时间跟随系统 12/24 小时制；仍使用北京时间，和个人时钟共用同一 Instant 快照。
- 右列：个人时间、进度条、25/50/75% 刻度、两位小数百分比。个人时间始终为 0:00—24:00，不跟随 12 小时制折返。
- 平时没有作息、倒计时、设置控件或额外标题。左右含义保留在无障碍描述中；休息和暂停状态仍有小字提示。
- 应用内保留文字自动/黑/白/自定义 RGB、背景颜色、不透明度与预览。星期、日期和百分比使用选定字色的 65% 不透明度。
- 全透明是新默认外观。旧设置缺少 `appearance_version=2` 时按 0% 背景显示，不改变作息、刷新开关、字色及背景 RGB。下一次保存会记录新版本；此后用户选择的不透明度继续保留。读取旧设置不会回写整份偏好。

## 自动测试与构建

环境继续使用 JDK 17、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、compile/target SDK 36、Build Tools 35.0.0，依赖未升级。固定本地 debug 签名，密钥未纳入源码。

实际命令，在 `android/` 下：

```powershell
.\scripts\build.ps1
```

脚本在 ASCII 路径副本运行：

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=1 --console=plain
```

成功 run-id：`20261001-151451-034`，46 项测试，0 失败、0 错误；lint 0 错误、10 警告。警告为旧图标/依赖提示、未使用字符串、为 RemoteViews 兼容而保留的间隔容器、9sp 状态文字。

覆盖原有时间、跨日、夏令时、持久化及刷新测试，并增加星期/日期、12/24 小时制、午夜跨年与时间回拨、新版透明外观迁移及随后手动调整背景的测试。Android 适配测试检查新日期行和透明默认值。

初次 run `20261001-151337-238` 因 `Space` 不被 RemoteViews 允许加载而失败。源码已换为支持的 `FrameLayout` 间隔容器；随后上述完整构建及 46 项测试通过。失败记录保留，不计作成功。

APK：`artifacts/20261001-151451-034/PersonalDay-Android-0.1.5-debug.apk`

SHA-256：`A216B2A7B299CF0BC042C0E1B75EB48312ABD21C610E366FEFC360792BDAA1AB`

签名证书 SHA-256：`ea4beb8b6254ae73582f5213154a16e4655fed69b9d704f32a0534b8309ed67b`，与本项目此前 APK 一致。

## 原生模拟器记录

本轮使用已有独立 AVD `personalday_api36`，AOSP Android 16/API 36、x86_64、Launcher3、720×1560/320dpi、软件 GPU、1536 MB RAM、2 核。它不能代表小米桌面。

证据位于 `artifacts/emulator-api36-015/`，包含 ADB 命令、UI XML、实际屏幕截图和仅测试应用的偏好文件。

- 覆盖安装 0.1.5 成功，两个已绑定组件继续存在。
- `01-settings`：应用内新布局预览和临时字体说明。
- `03-home-widgets`：旧版 65% 背景升级为实际全透明；旧黑字设置保留，在暗壁纸上对比不足。
- `05-home-white`：应用内改为白字并保存，两种尺寸都显示三行日期和个人时间/进度；未观察到内容裁切。
- `06-home-12hour`：仅在测试模拟器切换系统时间制，常规时间从 15:18 变为 3:18，个人时钟继续按 24 小时清醒日显示。
- `07-reopened-home` / `07-reopened-prefs`：强行停止测试应用后重新打开，白字、透明度 0、作息和刷新设置保留并重新计算。
- `08-custom-ink` / `08-custom-prefs`：通过应用内输入 `#A5D9FF`，两种组件均变为浅蓝字，背景保持完全透明；随后通过应用设置恢复白字，见 `09-final-white`。

测试后恢复了模拟器原来的 24 小时时间制，停止测试应用并正常关闭 AVD；没有连接或修改实体手机。截图只记录测试模拟器。主题操作中一次向错误方向滚动未找到颜色控件，改为向上滚动后完成，没有据此修改产品代码。

本轮没有完成小米 15 Pro 真机、同款字体、10 分钟桌面停留、30 分钟锁屏恢复、重启、耗电或所有无障碍读屏项目。休息时段和极限比例有自动测试，不将其写成此次原生人工验收通过。2×1 的刻度数字因缩小而较小，实机可读性待确认。

## 字体核对结果

使用 agent-reach 的 Exa 搜索和 GitHub CLI，静态读取公开资源，没有安装或执行第三方时钟 APK，也没有把下载的字体打包进 Personal Day。

1. 从公开镜像取得 [Xiaomi Clock 17.71.0](https://memeosupdates.com/apps/com.android.deskclock/1305007100)。`aapt dump badging` 确认包名 `com.android.deskclock`、versionCode `1305007100`、versionName `17.71.0`；该文件的 Manifest 没有 APPWIDGET 注册。它不是从用户手机提取的安装包。
2. 读取 [小米 15 Pro 公开系统转储](https://github.com/gm-stuffs/xiaomi_haotian_dump/tree/a4f7c4b7b46fc138d3b75d9a8c84e03f8420d075)，该转储是 OS2.0.12.0，不能当作用户当前系统。不同系统时钟模板分别调用 `miui-thin`、`mitype-clock.ttf`、`mipro-normal`/`mipro-light`，不足以确认截图中的“数字时钟”。
3. [小米官方 MAML 文档](https://zhuti.designer.xiaomi.com/docs/grammar/)支持通过 `fontFamily` 使用系统字体；这个机制也不能单独证明截图采用哪套字体。

来源、ROM 提交、下载 APK 哈希、Manifest 读取结果保存在 `artifacts/xiaomi-font-research-015/`。后续应只读核对用户手机正在使用的小组件资源、字体映射和具体字重，再同时替换左右字体。无需因本版布局调整而修改 Windows 版。

## 源码交付

`artifacts/PersonalDay-Android-0.1.5-source.zip` 包含完整 Android 源码、Gradle Wrapper、锁文件和文档，排除构建目录、证据附件、机器路径及签名密钥。源码及 APK 的 SHA-256 见 `artifacts/SHA256SUMS-0.1.5.txt`。实际参与构建的文件哈希见成功 run 目录中的 `source-sha256.json`；构建后只补充交付文档，代码、资源和构建配置已逐项核对一致。
