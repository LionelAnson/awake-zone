# 0.1.8：应用内设计页

用户确认采用透明／纯色背景、三行左右联动的设计设置。继续使用原生 Kotlin、Canvas 和 XML RemoteViews。

## 设计规则

- 独立设计页：顶部预览可切换4×2、2×1及深浅观察底色，下方设置，底部恢复默认与保存操作。
- 背景可选全透明或纯色；支持色板、六位RGB和0—100%不透明度。背景贴合时钟设计画布，使用小圆角。
- 字色保留自动、黑、白、自定义。实心背景的自动字色参考背景明度，透明或半透明背景参考整体壁纸提示，可手动覆盖。
- 三行分别调整字号与不透明度，左右同一行联动。第二行的不透明度同时作用于进度条。字号60—110%，进度条粗细50—200%；100%为0.1.7已确认的字号和线条。
- 字号固定，不因时间文本长度变化而缩放。最大字号需通过最长数字、100.00%和十四种星期时段的原生边界检查。
- 设计页编辑草稿；保存后更新全部组件。恢复默认仅重置外观。作息与刷新使用保存时的最新值，避免页面往返覆盖设计。
- 预览与桌面共用产品渲染器。预览合并高频变化，在后台生成位图，忽略过时结果；设计参数全部参与整图缓存键，透明度变化复用字形缓存。

## 升级与默认值

初始外观保持0.1.7：透明背景、首行100%、其余50%、字号和线条100%。没有`design_version=1`的旧设置不会激活历史背景透明度。原作息、刷新开关和字色保留。

## 验证与构建

使用现有锁定工具链与一个worker，无新增第三方UI依赖：

```powershell
# android 目录
.\scripts\build.ps1 -Tasks ':core:test',':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug',':app:assembleDebugAndroidTest'
adb -s emulator-5554 shell am instrument -w com.personalday.android.test/com.personalday.android.TypographyInstrumentation
```

原生检查输出位于应用私有`files/typography-018/`，导出后清理。宿主测试只验证模型、设置与界面交互，原生图像单独核验。

## 本轮执行结果

最终构建 run-id：`20261001-223402-164`，版本0.1.8 / versionCode9。APK与模拟器安装文件SHA-256一致。

| 检查 | 结果 |
|---|---|
| 核心自动测试 | 24项通过 |
| Android宿主测试 | 35项通过；不作为实际字形证据 |
| lint与构建 | lint无错误，保留23项警告；应用和原生诊断APK构建成功 |
| API36原生图像检查 | 234项、0失败；覆盖背景、三行alpha、60/110字号、最长内容、14种中文、进度线宽与10种缓存变更 |
| 默认排版 | 9张PNG与0.1.7逐像素一致 |
| 草稿与返回 | 编辑不写盘；实际系统返回键弹出确认，放弃后设置不变 |
| 保存与主页往返 | 设计保存后，回主页保存作息仍保留设计；4×2和2×1实际显示已查看 |
| 恢复默认 | 重置先只改变草稿；保存后透明背景、100/50/50以及100%字号生效 |
| 字色与颜色输入 | 通过界面操作纯色色板、RGB输入、自定义字色及白字选择；保存可重开 |
| 升级复查 | 新建组件13、14从0.1.7覆盖升级0.1.8后保留；再运行原生诊断也保留 |
| 收尾设置 | 通过界面恢复测试前的有效外观、作息和刷新开关；未用旧偏好文件覆盖 |
| 小米15 Pro | 未连接，未验证 |

### 限制和现场异常

开始时模拟器绑定了4个个人时钟组件。带窗口的模拟器运行期间退出；无窗口重启后，在本轮UI操作之前即发现原4个绑定不存在。现有证据不能确定原因，也不能归因于用户、系统或本次更新。未恢复旧桌面数据库或位置；重新添加了4×2和2×1，针对这两个新实例完成版本往返、原生诊断及显示验证。因此不把“最初4个实例保留”列为通过。

草稿重建由自动测试覆盖；本轮未进行真机旋转、HyperOS、其他Android版本、长期后台或耗电验证。预览使用固定时刻并合并120ms内的调整；它是排版预览，观察底色不保存为组件背景。

### 检查中修正的问题

- 最细进度条的填充与轨道在半像素边缘重叠，覆盖面积等效2.1569像素。改为不重叠的填充与轨道，原生检查为2.0像素；默认图像保持一致。
- 修正了Kotlin接收者命名和View成员命名冲突。测试对白色沿用的#FAFAFA色值、AlertDialog异步点击消息做了准确断言。
- API33+使用平台`OnBackInvokedCallback`，API31/32保留旧返回入口。仅对这个低版本入口抑制不识别平台迁移方式的lint提示，未全局关闭检查。原生平台回调是[Android官方支持的返回处理方式](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)。API36系统返回键实际确认通过。
- UI采样脚本曾在SharedPreferences异步写盘前读到旧文件；等待界面完成后核对全部字段通过。部分早期“桌面”文件实际上仍是应用或没有组件的主页，不作为组件显示证据。

## 交付与证据

- APK：`artifacts/20261001-223402-164/PersonalDay-Android-0.1.8-debug.apk`
- SHA-256：`CAD09012D0102A47F8DD7614A4836B4BEC1BECC2BE0149C563108EF32922E86A`
- 完整Android源码：`artifacts/PersonalDay-Android-0.1.8-source.zip`
- 哈希清单：`artifacts/SHA256SUMS-0.1.8.txt`
- 本轮源码/资源/构建配置与最终构建清单一致；构建后仅补充文档。

`artifacts/emulator-api36-018/` 内：

- `native/report.json`及24张PNG；`native-with-widgets-report.json`为有绑定实例时的复查。
- `default-comparison.json`、`verification.json`、`upgrade-widget-check.json`记录像素、安装包、设置及组件ID核对。
- `02-design-default.png`、`06-edited-rows-draft.png`、`07-compact-preview.png`：设计页与尺寸切换。
- `04-unsaved-system-back.png`、`22-reset-exit-prompt.png`：退出提示。
- `20-styled-desktop-0.png`：两个实际桌面实例的自定义外观；`27-final-home.png`：恢复原外观后的实际桌面。
- `commands.jsonl`、`commands-ui.jsonl`、`ui_driver.py`：安装、测试和界面操作记录。
- `native-initial/`保留首次细进度条失败数据，`instrumentation-initial.txt`标注1项失败。

无新增依赖，工具版本及签名方案沿用README。收尾清理诊断包与专用输出目录，并关闭本轮启动的无窗口模拟器。没有修改Windows应用或设置。
