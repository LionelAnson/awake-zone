# 2026-09-30 实时磨砂隔离实验结果

**后续更新：** 本报告之后已执行独立 DesktopAcrylicController 状态与画面对照，见 [控制器实验报告](LIVE-BACKDROP-CONTROLLER-20260930.md)。下文保留当时结果；“尚未引入／未执行控制器”的状态以新报告为准。

## 结论与交付状态

**本轮没有修复置顶实时磨砂，不替换已安装的 0.3.1。** 已在本机执行隔离 A/B、纯原生探针和真实屏幕截图检查，交付可重现的失败样例、代码修正和逐轮证据。

当前可证实的范围：

1. 清除 Win32 宿主显式背景填充、单独设置 WebView2 透明，没有解决当前 HostBackdrop 黑色问题。因此不能把宿主 RGB 填充说成唯一根因。
2. 相同 SpriteVisual 的洋红色标记能显示；换成 HostBackdrop 后全黑。隐藏 WebView 后仍黑。
3. 不含 Tauri、WebView2、WorkerW、SetParent 的原生顶层窗口也重现该现象。问题能够在 **HostBackdrop 材质取样／渲染层**独立重现，不需要产品 CSS 或桌面切换参与。
4. 纯原生系统 Acrylic 在本轮失焦条件下为不透明白色；旧 Accent Acrylic 为黑色。尚未证明前者具体由失焦策略引起，也没有证明系统／驱动故障。
5. 原生 HostBackdrop 的启用返回 S_OK，系统报告高级效果已开启、效果受支持且速度快。创建成功和这些能力报告仍不能证明视觉成功。

尚未确诊到某个 Windows 内部函数或显卡行为；没有以 ToDesk、虚拟适配器或 GPU 作为已证实原因。

## 1. 本轮重新核对的现场

- 开始时间约北京时间 12:32；证据时间戳采用 UTC，目录中的 `05:04` 对应北京时间 `13:04`。
- 安装程序：`E:\应用\Personal Day\personal-day.exe`，实际版本 0.3.1，原进程 PID 24048 始终保留运行，没有停止或替换。
- 工作区源码 0.3.2；非 Git 仓库，没有执行 reset／clean。
- Windows 11 家庭版中文版，10.0.26200，x64；本轮原生窗口读到 DPI 120，Tauri 读到 scale=1.25，网页 devicePixelRatio=1.375。
- 锁文件确认：Tauri/tauri-runtime-wry 2.12.0、tao 0.37.1、wry 0.57.0、windows 0.62.2、windows-numerics 0.3.1；产品直接依赖 windows-sys 0.61.2。
- `.cargo/config.toml` 仍是 jobs=1。实验构建没有写入 `src-tauri/target/release` 或 `release` 中已有交付文件。
- 系统已有多个 Windows App Runtime（含 1.7 和 2.x），但产品没有引入 Windows App SDK 的绑定、控制器或分发依赖。

备份目录：

`output/playwright/backdrop-runs/20260930-123311-baseline/`

其中包括全部当时工程源码快照、真实 state 和备份文件、自启值、安装版 exe、已交付 release 文件及源码哈希清单。依赖缓存和编译缓存未放入源码备份。

本轮所有实验目录基准：`output/playwright/backdrop-runs/`。

## 2. 实际代码改动

### 产品代码中的局部修正

| 文件 | 修改 | 本轮验证范围 |
| --- | --- | --- |
| `src-tauri/src/backdrop.rs` | 用 `Webview::window().set_background_color(None)` 清除宿主显式 RGB 填充；用 Webview 接口单独传 `Some(Color(0,0,0,0))` | 调用链核实、A/B 执行；不解决实时黑色问题 |
| 同上 | 使用 Rust 投影中的 DWM 属性常量；检查启用 HostBackdrop 和圆角的 HRESULT | 新探针记录两者均为 0；视觉仍失败 |
| `src-tauri/src/lib.rs` | 在耗时的原生合成初始化前注册 LocalState | 修正实测出现的启动 IPC 竞态；新构建三轮启动均无设置状态错误、窗口未被错误文字撑高 |

调用链证据来自本机依赖源码：

- Tauri `WebviewWindow::set_background_color` 会分别设置 window 和 webview。
- tao 的 `WM_ERASEBKGND` 对 Some 颜色调用 RGB `CreateSolidBrush`，不使用 alpha。
- tao 的 `Window::set_background_color(None)` 将保存的显式背景色清除并触发重绘；它本身不是“整个窗口已透明”的证明。
- tauri-runtime-wry 的 **WebView** 背景分支遇到 None 会退回 `(255,255,255,255)`，所以本轮没有给 WebView 传 None。
- 产品源码中未发现另一处 `set_background_color` 调用；HTML/body/#app 透明状态也记录在各轮 manifest。CSS 没有作为唯一排查对象。

### 只供隔离诊断使用的设施

- Cargo 新增 `backdrop-diagnostics` feature，默认关闭。
- 诊断构建必须同时提供绝对路径 `PD_PROBE_ROOT` 和 `com.personalday.probe...` 独立应用标识，否则启动断言拒绝运行。
- 诊断目录代替真实设置目录；自启更新被跳过，默认不注册全局快捷键，避免与安装版冲突。没有停止安装版来腾出快捷键。
- `probe_control` 支持原生 marker/HostBackdrop 对比、单独显示／隐藏 WebView 和读取窗口状态；普通构建拒绝该命令。
- `probe_exit` 正常退出隔离实例；普通构建拒绝该命令。
- `diagnostics/native-backdrop/` 是独立 Rust Win32 程序，没有 Tauri/WebView2 依赖；自身初始化 WinRT、DispatcherQueue 和消息循环，保留 composition 对象，在结束时关闭对象并销毁窗口。

产品还未改用新材质方案；当前源码中的原生 HostBackdrop 实现仍属未通过验收状态。

## 3. 关键实验表

以下目录均位于上述实验基准目录。有效轮次包括颜色深／灰／亮／红、红蓝条纹和黑白条纹；后方图案窗口保持前台，时钟／探针不抢焦点。文件 `visual-analysis.json` 记录实际截图指标。

| Run-id | 变量／目的 | 原生观察 | 结论 |
| --- | --- | --- | --- |
| `2026-09-30T04-59-48-028Z-clear-cold` | 当前 Tauri 修正：清除宿主背景；新进程直接浮窗 | 所有背景仍黑；纯色标记正常；隐藏 WebView 仍黑 | 宿主清除不足以修复 |
| `2026-09-30T05-01-15-030Z-legacy-cold` | 与上一轮相同 exe，仅 PD_PROBE_HOST=legacy | 同样黑；纯色标记正常 | 同一构建的有效局部 A/B |
| `2026-09-30T05-01-49-577Z-clear-roundtrip` | 相同修正构建，经历桌面子窗→浮窗 | 同样黑；parent=0、置顶状态存在 | 冷启动已失败，不能把失败仅归因于 SetParent 残留 |
| `2026-09-30T04-45-03-940Z-native` | 纯 Win32 + 公开 HostBackdrop，普通边框、无 WebView | 标记洋红色正常，HostBackdrop 全黑 | 黑色能独立重现 |
| `2026-09-30T04-45-49-733Z-native--no-redirection` | 仅原生探针创建时关闭重定向位图 | 同样黑 | 此原生分支没有改善 |
| `2026-09-30T04-49-08-343Z-native--extend-frame` | 原生客户区扩展玻璃；记录效果能力 | HRESULT=0、能力均 true，仍黑 | 扩展框架不足以修复 |
| `2026-09-30T04-50-01-891Z-native--top-visual` | 独立进程改 DesktopWindowTarget isTopmost=true | 标记正常、HostBackdrop 仍黑 | 单独改变 target 层级没有改善 |
| `2026-09-30T05-04-00-675Z-native--asta---container-root` | 与微软示例对齐 ASTA 队列选项和 ContainerVisual 根节点 | 标记正常、启用 S_OK、HostBackdrop 黑；前台持续为图案窗 | 排查探针结构；不能立即归因系统故障 |
| `2026-09-30T04-54-46-649Z-native--system-acrylic---extend-frame` | 不启用 HostBackdrop，隐藏 marker visual；只用系统 Acrylic + 扩展框架 | 背景不透明白色、不随图案变化 | 未满足实时与保色要求；可能涉及材质策略，尚未确诊 |
| `2026-09-30T04-57-11-247Z-native--accent-acrylic---no-redirection` | 无 HostBackdrop 覆盖；原生 Accent Acrylic，alpha=1 | 返回成功但黑色 | 该独立原生旧 API 路径同样未通过 |

ASTA + ContainerVisual 一轮是对齐参考结构的组合对照，不用它推断其中单个选项的独立作用。其他分支也在 manifest 中记录完整 flags，避免与历史尝试混淆。

没有再次把“创建时 noRedirectionBitmap”描述成首次发现或未尝试；与交接时的区别是，本轮还在**没有 Tauri 和 WebView 的原生程序中**对比了这一变量。

### 模糊检查结果

- 对纯原生样例，只分析客户区内部，避开标题栏、边框和圆角。
- 对 Tauri 条纹检查，采用同一后方黑白条纹下的 `host-webview-hidden.png`，避免时钟文字被计入背景纹理。
- 对照图在测试最后隐藏探针后获取；它不是产品持续采集循环。
- 有效原生轮次中，暗图→亮图的客户区平均灰度变化为 **0**；条纹区域的空间标准差和相邻列变化均为 **0**。
- 最后三轮 Tauri 中，不含 WebView 文字的背景条纹空间标准差也均为 **0**。
- 对照图能看到黑白条纹，空间标准差超过 60；并非图案没有放到对应区域。
- 纯色覆盖虽然使高频变化降低，但也把颜色变化和空间变化全部消除，故判定为 **失败**，不是“模糊强度很大”。

算法和全量数值：`scripts/analyze-backdrop-runs.py`、`output/playwright/backdrop-runs/visual-summary.json`。

## 4. 探针自身发现并纠正的问题

1. 首批独立图案窗受到隐藏启动标志影响，前台句柄检查不足以证明窗口实际可见。已在显示步骤显式添加 SWP_SHOWWINDOW，保留焦点检查，并用隐藏探针后的同区域条纹对照确认实际覆盖。早期无效截图已删除，避免保留误采的真实应用内容；对应轮次保留 metadata 并标记无效。
2. 途中有一轮因前台切换中止，不纳入有效对照。
3. `DWMWA_USE_HOSTBACKDROPBRUSH` 在本地 SDK 明确是 **set-only**。早期诊断尝试 Get 得到 E_INVALIDARG，不能据此断言该属性未开启。已改为记录 Set 的 HRESULT；最终 Tauri 记录为 0。
4. 发现产品启动 IPC 竞态：网页请求 load_preferences 时 LocalState 尚未注册，显示错误并撑高窗口。已将状态注册提前；最终三轮都未出现该错误。早期临时页面 reload 不作为该修复的通过证据。
5. 构建包装器首次使用 Node 的递归 cpSync 在本机异常退出；改为逐文件读取／写入快照后构建成功。该包装器只处理项目文件，不涉及生产设置恢复。

这些无效／失败结果仍可从 run 目录和日志追溯，不把它们混入成功检查。

## 5. 没有实施的分支及原因

- **DWMWA_REDIRECTIONBITMAP_ALPHA**：本地 SDK 10.0.26100.0 的 dwmapi.h 含该定义，windows-sys 0.61.2 尚未提供该常量。本轮没有盲猜数值写入。证据没有指向“单纯重定向位图覆盖”——没有该位图的原生分支仍黑，纯色 visual 正常；若后续仍要试，应建立明确预乘 alpha 像素的独立基线，单独启用该属性。
- **加入更多 WebView 布局／双窗口重构**：在不含 WebView 的原生程序已经失败，因此没有把产品改成双窗口。WebView 的独立显示／隐藏检查已做；“留出半块原生区域”的 WebView 接入原型未做。
- **Windows App SDK DesktopAcrylicController**：尚未引入。已有运行时不等于有了 Rust 绑定或正确的 Microsoft.UI.Composition 目标；不能与当前 Windows.UI.Composition 对象混用。
- **持续屏幕采集、WDA 排除自身、GPU 局部模糊**：没有新增或执行。这些不是本轮程序的一部分。
- 没有修改系统透明开关、高对比度或省电策略，没有禁用显卡／ToDesk，没有通过激活时钟维持材质。

## 6. 本轮实际检查状态

| 检查 | 结果 |
| --- | --- |
| `npm test` | 本轮重新运行，50 项通过；只证明计算与前端辅助逻辑 |
| `npm run build` | 本轮通过 TypeScript/Vite 生产构建 |
| `cargo check --manifest-path src-tauri/Cargo.toml --release` | 本轮普通构建检查通过；使用独立 target 目录 |
| 隔离 Tauri EXE 构建 | 通过；0.3.2 + backdrop-diagnostics，独立 identifier |
| 独立原生 EXE 构建 | 通过；crate 版本 0.1.0 |
| 原生实时背景视觉检查 | **失败** |
| 修正后的启动 IPC 检查 | 三个最终隔离实例 bootError=null，布局 330×176 物理像素 |
| 原生失焦条件 | 图案应用在前台有记录；但材质本身失败，不能称失焦磨砂通过 |
| 桌面→浮窗路径 | 执行过，记录 HWND/style；这不等于完整快捷键／锚点回归通过 |
| 真实全局快捷键、反复锚点往返、Win+D、托盘点击、关闭隐藏 | 本轮未重新完整验收；安装版继续占用快捷键，隔离实例默认跳过注册 |
| macOS、真实重启／休眠／物理拔屏 | 未验证 |

旧 0.3.1 的 17 组原生检查未复用为本轮通过结果。

## 7. 状态保留与收尾

`restoration-check.json` 记录：

- 安装版仍为 0.3.1，原进程仍在运行；没有执行安装器。
- 安装 exe SHA-256 与本轮开始备份一致。
- 自启命令仍为 `"E:\应用\Personal Day\personal-day.exe" --autostart`。
- 作息、主题占位值、尺寸、自启等 preferences 与本轮开始相同。
- 真实设置文件的 position 和 anchor 在本轮期间变化，因此整文件哈希与开始时不同；**没有用备份覆盖更新后的文件**。最后三轮实验各自开始／结束的真实 state 哈希均一致，自启值均一致。
- 隔离实例使用 probe_exit 正常结束；原生样例退出消息循环并释放资源；独立图案窗通过 exit 控制结束。无需停止再恢复原安装进程。

## 8. 产物、哈希与复现

### 隔离 Tauri 样例

- 路径：`output/playwright/backdrop-runs/2026-09-30T04-56-53-214Z-build-tauri/personal-day.exe`
- 版本：0.3.2，诊断 feature 开启，**非可安装候选**。
- SHA-256：`e48fa6f9b066ed5429f05c8fa4494b59c06cd35f57de1a24de1e95de562127d1`
- 同目录包含实际命令、构建日志、完整有效配置和构建前 source 快照。

### 最小原生复现

- 路径：`output/playwright/backdrop-runs/2026-09-30T04-59-26-093Z-build-native/native-backdrop-probe.exe`
- crate 版本：0.1.0；不修改产品设置或自启。
- SHA-256：`fc359b4e7e2460a039f5a76ce66e93961cdc08173670767b5efe03e6ff234ff9`
- 同目录包含命令、日志、锁文件和源码快照。

### 安装版保持原样

SHA-256：`cd4c0d4a4722844fa716321a7b7567e3cc7823838d900f9cf26d235cade1a246`。

### 复现命令（项目根目录 PowerShell）

```powershell
# 构建目录独立，包装器自动加入 Rust PATH，保持 jobs=1。
node scripts/build-backdrop-probe.mjs tauri
node scripts/backdrop-ab.mjs legacy cold
node scripts/backdrop-ab.mjs clear cold
node scripts/backdrop-ab.mjs clear roundtrip

node scripts/build-backdrop-probe.mjs native
node scripts/native-backdrop-run.mjs
node scripts/native-backdrop-run.mjs --asta --container-root
node scripts/native-backdrop-run.mjs --no-redirection
node scripts/native-backdrop-run.mjs --top-visual
node scripts/native-backdrop-run.mjs --extend-frame
node scripts/native-backdrop-run.mjs --system-acrylic --extend-frame
node scripts/native-backdrop-run.mjs --accent-acrylic --no-redirection

python scripts/analyze-backdrop-runs.py
```

实验脚本会短暂把独立图案应用置于前台，保存的是其对应区域。不要同时运行多个探针；Tauri CDP 使用 9224。无需退出安装版。每轮生成新 run-id，exe 与 manifest 通过 SHA-256 关联；不要从散落的缓存目录手工挑选二进制代替。

打包只交付诊断源码／EXE／证据，**没有生成或交付可替换 0.3.1 的安装器**。另一台机器或解压路径变化后，应重新运行构建包装器来生成当地路径的 build manifest。

## 9. 下一项明确的区分实验

建立独立 **Microsoft.UI.Composition + DesktopAcrylicController** 小窗，保持当前同一后方图案测试与原生截图测量：

1. 选择并记录 SDK／运行时版本、正确的 backdrop target 和非空 SystemBackdropConfiguration；只在隔离程序中接入，不改产品分发。
2. 先用默认材质，比较 `IsInputActive=false/true`；两种情况下实际前台都保持为图案窗。
3. 若 true 有实时背景，再单独调整 TintOpacity／LuminosityOpacity，检查能否在低染色时保留模糊及后方颜色。
4. 若默认／true 都失败，保留支持检测和材质状态，不直接归咎 GPU；此结果可以与本轮原生 HostBackdrop 最小复现一起对照 Windows 环境。
5. 只有该层通过，才加入透明 WebView2 和产品窗口切换。

这项实验将区分“当前 Windows.UI.Composition HostBackdrop 路线的问题”与“可由新材质控制器处理的输入状态／回退策略”。本轮尚未执行，不能提前宣称它可修复。

参考资料已核对：

- [微软 Win32 Composition 示例](https://github.com/microsoft/Windows.UI.Composition-Win32-Samples/blob/master/cpp/HelloComposition/HelloComposition/CompositionHost.cpp)
- [DWM 属性与参数定义](https://learn.microsoft.com/en-us/windows/win32/api/dwmapi/ne-dwmapi-dwmwindowattribute)
- [Desktop Acrylic 材质控制器](https://learn.microsoft.com/en-us/windows/windows-app-sdk/api/winrt/microsoft.ui.composition.systembackdrops.desktopacryliccontroller)
- [SystemBackdropConfiguration.IsInputActive](https://learn.microsoft.com/en-us/windows/windows-app-sdk/api/winrt/microsoft.ui.composition.systembackdrops.systembackdropconfiguration.isinputactive)
