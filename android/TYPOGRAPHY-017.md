# 0.1.7：放大字号、略加粗笔画

将用户确认的 `output/typography-size-preview-20261001` 独立样张接入组件。沿用 Kotlin、XML RemoteViews 和原生 Canvas；不修改 Windows 工程或设置。

## 变化

- 相对 0.1.6，四种文字字号均增加 16%。时间 Roboto 字重由 150 调至 190，日期及百分比由 100 调至 130；微软雅黑 Light 保留真实字重 290，通过减少 alpha 收缩量略加粗。
- 1080×470 设计坐标：左栏起点 (32,32)，右栏 (600,32)，下两行偏移 (1,146)、(4,287)。进度条宽426、高4，刻度宽1.5。参数集中在 `ClockTypography.kt`。
- 第一行100%不透明度，下两行50%；背景透明。4×2、2×1和应用内预览使用同一绘制路径，等比例适配桌面分配的空间。
- 组件选择器静态样例同步更新。作息、计算、刷新、添加流程及应用内字色设置接口保持不变。
- 仍使用 Roboto 3.005 和 Microsoft YaHei Light 6.25；字体文件未改变。小米原字体未核实，不声称完全同款。

## 验证方法

```powershell
# 在 android 目录执行；独立 ASCII 构建目录，单 worker
.\scripts\build.ps1 -Tasks ':core:test',':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug',':app:assembleDebugAndroidTest'
# 向已连接的模拟器覆盖安装应用和独立诊断 APK 后
adb -s emulator-5554 shell am instrument -w com.personalday.android.test/com.personalday.android.TypographyInstrumentation
```

沿用现有测试，更新原生检查的测量区域和预期字重。宿主结构测试不作为像素证据。原生检查使用产品渲染器，覆盖透明度、字体加载、黑白字色、两栏边界、进度边界和十四种星期时段文字。另将实际产品渲染与已通过的样张对照，并查看 Launcher3 上的现有组件。

## 执行结果

构建 run-id：`20261001-184625-082`，Gradle 构建成功。JDK17、Kotlin2.2.21、AGP8.13.2、Gradle8.13、SDK36和Build Tools35.0.0均沿用原锁定版本，没有新增依赖。

| 项目 | 本轮结果 |
|---|---|
| 核心测试 | 21项通过 |
| Android宿主测试 | 27项通过；仅验证结构、适配和设置逻辑 |
| lintDebug / 应用与诊断APK | 成功 |
| API36原生检查 | 153项通过，0失败 |
| 字体核验 | Roboto190、Roboto130、YaHei Light290；资源哈希匹配，无回退 |
| 透明度与布局 | 首行alpha255、下两行最大alpha128；透明背景；长时间、100.00%及不同尺寸均未触及画布边界 |
| 与已通过样张的对照 | 时间、中文、日期、百分比四个区域逐像素一致；进度条首个刻度因小数坐标取整有1设计像素的位置差异 |
| Launcher3实际显示 | 已查看4×2与2×1；点击组件打开设置；时间及百分比继续刷新 |
| 更新保留 | 偏好逐键相同，原4个组件ID保留，刷新前台服务运行 |
| 小米15 Pro | 未连接，未验证 |

小米桌面的实际占格、物理屏幕效果和后台存活仍未验证。本轮没有新增长时耗电、系统重启或熄屏恢复验证。设置颜色的保存逻辑通过本轮宿主测试，原生输出核验黑白字色；未重复人工操作所有颜色选择项。

## 交付与证据

- APK：`artifacts/20261001-184625-082/PersonalDay-Android-0.1.7-debug.apk`
- SHA-256：`F5F3895144A0DAA428FDECF72EF5BBFED9BBD3B5A9BD35CE8E7DF09A36423AB7`
- 完整Android源码：`artifacts/PersonalDay-Android-0.1.7-source.zip`；哈希清单 `artifacts/SHA256SUMS-0.1.7.txt`。
- 构建日志、测试报告和源码哈希：`artifacts/20261001-184625-082/`。
- 原生报告与PNG：`artifacts/emulator-api36-017/native/`。
- 样张像素对照：`preview-comparison.json`；安装包、设置、组件ID和服务核验：`verification.json`。
- 实际桌面：`04-updated-home.png`、`06-final-home.png`；点击进入设置：`05-click-settings.png`。`03-updated-settings.png`拍摄时界面尚未切换，实际仍是桌面，不作为设置页证据。
- ADB操作记录：`commands.jsonl`。收尾移除诊断包与其专用输出目录，保留0.1.7及原有桌面实例。

源码/资源/构建配置均与此次产物清单一致；构建之后仅补充文档。没有用旧偏好文件覆盖设置，没有改动Windows应用。
