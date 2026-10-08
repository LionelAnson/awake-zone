# 0.1.6：已通过样张的透明双栏排版

用户于 2026-10-01 确认独立样张，并指定第一行时间不透明度 100%，下方两行 50%。本版将这套排版接入 Android 组件；不修改 Windows 工程或设置。

## 实现

- `ClockTypography.kt` 集中管理字体、字号、真实字重、纵向比例、字距、alpha 收缩、行位置、列位置和不透明度。
- `ClockFaceRenderer.kt` 使用同一个三行模块绘制两栏。两栏内部左对齐、顶部一致，沿用样张的 1080×470 坐标，整个图形等比例适配 4×2、2×1 及调整后的桌面尺寸。
- 字体随 APK 打包：Roboto 3.005（顶部字重150，日期与百分比100）、微软雅黑 Light 6.25（真实字重290）。中文和日期沿用样张的细化/拉长参数，未更换为系统回退字体。来源及哈希见 `FONT-NOTICE.md`。
- 字形经 4 倍超采样、对称 alpha 收缩、一次降采样，按可见边界对齐；透明像素直接跳过收缩计算。字形缓存与整幅位图缓存各限制约4 MiB。
- 原生字体通过 `Font.Builder(resources, resId)` 加载，核验指定字符实际 Font 的字体缓冲哈希、字重和TTC面；异常不静默改用系统字体。
- 第一行100%，中文、日期、百分比50%；进度条填充整体50%，轨道与刻度按样张保留相对明暗。未结束时按向下取整绘制条长，99.99%不会因四舍五入画满。
- `WidgetRenderer.preview` 与桌面提交共用同一个 Canvas 位图路径；RemoteViews 只携带图片、整体无障碍描述和打开设置的点击动作。Binder位图限制480000像素。组件选择器使用固定样例图，不把样例当作实时数据。
- 设置页保留自动/黑/白/自定义RGB字色，设置在点击保存时才写盘。组件始终全透明，旧背景字段在本地保留但不再绘制；没有新增卡片、边框、装饰或第四行。
- 结束、下次起床与刷新暂停说明移至应用状态区及组件无障碍描述。原有计算、作息、刷新服务、组件添加流程保持原有接口。

## 字体差异仍然存在

Roboto 的“1”顶部为斜引笔，“9”的尾部为弧线；微软雅黑的“周、四、午”骨架也与小米截图不同。这是用户已确认的候选方案，不称为取得或完整复现小米时钟原字体。没有连接小米15 Pro，因此不标记HyperOS真机通过。

## 构建及自动检查

工具链沿用JDK17、Kotlin2.2.21、AGP8.13.2、Gradle8.13、SDK36及Build Tools35.0.0，无新增组件库或开发环境。每次构建使用独立ASCII目录与一个worker：

```powershell
.\scripts\build.ps1 -Tasks ':core:test',':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug',':app:assembleDebugAndroidTest'
```

最终产物、测试数量及哈希见本文件后面的执行结果。

宿主测试包含21项计算/日期/设置/刷新核心测试，以及27项Android结构与适配测试。Robolectric LEGACY不渲染可靠的实际字形；结构测试使用明确的 `StructureOnlyCanvasShadow`，其无像素输出不能当作视觉证据。

原生检查使用自带 Instrumentation，不增加AndroidX测试依赖：

```powershell
adb -s emulator-5554 install -r <app-debug.apk>
adb -s emulator-5554 install -r <app-debug-androidTest.apk>
adb -s emulator-5554 shell am instrument -w com.personalday.android.test/com.personalday.android.TypographyInstrumentation
```

原生报告由产品的 `ClockFaceRenderer` 实际生成，存放在目标应用私有 `files/typography-016/` 并导出到 `artifacts/emulator-api36-016/`。检查包括透明区域、255/128的alpha、黑白字色、两栏可见/边界、0/99.99/100%进度、14种星期时段变化、真实字体哈希与字重。原生PNG及检查均不等于小米物理屏幕人工验收。

## 本轮修正的真实问题

1. `Typeface.Builder(assets, "res/font/...")` 无法按资源目录加载字体。第一次原生运行0项检查、初始化失败。改为公开的资源Font API后，三个实际字体/字重组合核验通过；没有把初始化失败写成视觉失败或通过。
2. 初版收缩循环遍历大量透明像素，在调试模拟器首次中文绘制较慢。增加透明像素和最小alpha归零的短路，保持非零轮廓的采样顺序及取整不变；优化前后PNG需逐像素对照。
3. 宿主LEGACY的 `drawBitmap` 忽略Canvas缩放，导致右栏坐标在模拟位图中越界。这是测试替身的限制，修正在 `src/test` 内；原生生产绘制保持独立验证。

## 未验证项

- 小米15 Pro / HyperOS的实际占格、壁纸对比、字体尺寸与后台存活。
- Android31–35实际设备运行、系统重启、长时熄屏恢复与耗电。
- Xiaomi时钟17.71.0原字体资源及完全同款程度。

这些项目不能用本轮宿主测试、API36模拟器结果或旧版本报告代替。

## 最终执行结果

构建 run-id：`20261001-181328-417`。本次源码/资源/构建配置与该构建清单逐项匹配；构建之后只补充交付文档。

| 检查 | 本轮结果 |
|---|---|
| 核心自动测试 | 21项通过 |
| Android宿主结构/适配测试 | 27项通过；不作为像素证据 |
| `lintDebug`、应用APK、诊断APK构建 | 成功 |
| API36原生Instrumentation | 153项检查、0失败 |
| 实际字体 | Roboto150、Roboto100、YaHei Light290，资源哈希匹配，未观察到回退 |
| 不透明度 | 设计尺度首行最大alpha255，下方两行最大alpha128；背景alpha0 |
| 优化前后图像 | 9张PNG逐像素相同 |
| API36 Launcher3 | 4×2和2×1实际显示、三行透明布局已查看；保留原有3个实例 |
| 设置与重开 | 点击组件打开设置；自定义#A5D9FF保存并作用于全部组件；恢复原白字；强行停止后重开正确恢复 |
| 现场设置 | 测试结束与测试前的偏好逐键一致，未用旧备份覆盖设置 |
| 小米15 Pro | 未连接，未验证 |

本机调试模拟器中，原生整组检查从约110秒降至约14.7秒，9张图不变；优化后首次标准图绘制约2176ms，已缓存样本约0.45–4.95ms，含新中文行的另一例约1148ms。这是单次诊断记录，不能外推手机帧率或承诺冷启动耗时。模拟器应用冷开约4.6–4.9秒，后续还可针对冷启动改进；本轮未把它宣称为无延迟。

### 交付

应用APK：`artifacts/20261001-181328-417/PersonalDay-Android-0.1.6-debug.apk`

```text
SHA-256 3F761270DE5B1014C641D88797EE56D1F85A6C1FAAFC1E12CF226222BE9A319B
```

完整Android源码：`artifacts/PersonalDay-Android-0.1.6-source.zip`，包含Wrapper、锁文件、字体资源、原生诊断及文档，排除构建缓存、设备数据、签名密钥。汇总哈希：`artifacts/SHA256SUMS-0.1.6.txt`。

### 证据索引

- `artifacts/20261001-181328-417/build.log`、各模块 `build/test-results/` 和 `source-sha256.json`。
- `artifacts/emulator-api36-016/native/report.json`：最终原生检查、字体记录及每帧耗时；同目录为真实产品渲染PNG。
- `optimization-comparison.json`：优化前后9张图的像素比较。
- `02-home-updated.png`：新版三个实例的桌面显示。
- `03-settings-opened.png`、`04-custom-preview.png`、`05-custom-home.png`：点击、同路径预览及实际自定义字色。
- `07-final-home.png`、`07-reopened-prefs.xml`、`final-services.txt`：恢复原字色并重开，刷新服务运行。
- `source-verification.json`：当前代码与最终构建产物一致、偏好未变。
- `commands.jsonl`：本轮模拟器操作记录。

已卸载仅用于诊断的 `com.personalday.android.test`，导出后清理它生成的专用图像目录。Personal Day 0.1.6、原有组件及无声刷新服务保留，模拟器继续打开。
