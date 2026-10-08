# Personal Day：Windows 磨砂效果工作交接

更新时间：2026-09-30（北京时间）。本文供后续 AI 在没有聊天记录的情况下接手。

**最新续接：** 独立 DesktopAcrylicController 实验已执行，见 [控制器状态—画面实验报告](LIVE-BACKDROP-CONTROLLER-20260930.md)。本机回退标记正常、Active 为不响应图案的灰色；仍未修复，不替换 0.3.1。该报告优先于本文及上一轮报告中已更新的“尚未尝试”和现场描述。

**续接更新：** 本文是之前的交接现场。后续已执行隔离 A/B 与纯原生实验；最新证据、源码修正及状态核验见 [本轮实验报告](LIVE-BACKDROP-EXPERIMENTS-20260930.md)。实时磨砂仍未通过，不要把本文的进程、位置或“未尝试”清单直接当作最新状态。

## 0. 先读结论

- **用户要的置顶实时磨砂尚未实现成功。** 最新实验显示黑色背景；原生 API 返回成功、编译成功均不代表视觉效果通过。
- **实际安装并运行的是 0.3.1**：`E:\应用\Personal Day\personal-day.exe`。该版使用静态壁纸取样模糊，置顶后仍显示壁纸，不能满足最新需求。
- **工作区源码版本为 0.3.2，属于未交付实验代码**。不能直接把当前源码或 `target/release` 程序当作已验证版本安装给用户。
- `release/` 中已交付的 0.3.1 与 `src-tauri/target/release/` 中的 0.3.2 必须区分。
- 最近一次构建额外使用临时配置启用了 `noRedirectionBitmap: true`、`shadow: false`，仍然失败。**该配置没有写入项目的 `tauri.conf.json`，所以最新 exe 与直接按仓库默认配置构建的行为可能不同。**
- 本次只整理交接文档，没有再次修改程序、运行视觉测试或替换安装版。

## 1. 用户最新需求，以本节为准

产品是“个人时钟 / Personal Day”，采用 Tauri 2 + Vite + TypeScript，原生 HTML/CSS，无组件库。原始完整时间规则见 `README.md`。

### 磨砂与外观

1. 小圆角矩形，约 4 或 6 个桌面图标位大小；清醒期不显示底部倒计时和作息两行。
2. **取消日间／夜间模式**，不再按系统主题添加黑色或白色覆盖层。尽量保留后方内容的颜色，只做磨砂模糊。
3. 背景暗则白字，灰色或明亮则黑字。
4. 桌面模式：嵌入桌面层，普通窗口可盖住它，Win+D 后仍显示。
5. 置顶模式：透过时钟，应看到并模糊**实际位于时钟后方的应用内容**。例如放在 ChatGPT 上方，应跟随 ChatGPT 的颜色和内容变化。
6. 置顶后用户继续操作 ChatGPT 时，磨砂仍应有效，不能靠持续抢焦点维持。
7. 桌面模式之前经用户确认可使用静态壁纸取样磨砂；这不代表置顶模式也可以继续显示壁纸。

### 操作与其他约束

- 快捷键切换置顶；再按回到**桌面右下角的保存位置**。浮窗拖到别处不应覆盖桌面锚点。
- 用户原述为 Ctrl+Fn+Shift+Win+Z；已实现可注册组合为 **Ctrl+Shift+Win+Z**。Windows RegisterHotKey 没有 Fn 修饰键，硬件 Fn 行为未验证。
- 登录自启已按用户要求开启；不要在排查时永久改为测试程序路径。
- 时间固定北京时间 `Asia/Shanghai`；百分比两位小数，结束前不得提前显示 100.00% 或个人 24:00。
- 保留 macOS 工程；当前磨砂实验仅在 Windows 上执行，不能标记 macOS 已验证。

## 2. 环境、路径及运行状态

| 项目 | 值／状态 |
| --- | --- |
| 工作区 | `F:\Desktop\codex工作环境\个人时钟` |
| Shell | Windows PowerShell |
| 当前 Windows | Windows 11，10.0.26200，x64 |
| 屏幕 | 最近排查为单屏 1920×1080，工作区 1920×1020 |
| 缩放 | 监视器缩放曾读到 1.25；WebView `devicePixelRatio` 为 1.375，不能混用 |
| 工具链 | Node 24.14.1、npm 11.11、Rust 1.98.1；C++ Build Tools、SDK、WebView2 已具备 |
| 实际依赖 | Tauri 2.12.0、tao 0.37.1、wry 0.57.0、windows 0.62.2、windows-sys 0.61.2 |
| Rust 注册表源码 | `C:\Users\admin\.cargo\registry\src\index.crates.io-1949cf8c6b5b557f` |
| 本地设置 | `%APPDATA%\com.personalday.widget\state.json` |
| 自启注册项 | `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`，值名 `Personal Day` |
| 正确自启命令 | `"E:\应用\Personal Day\personal-day.exe" --autostart` |
| 编译并发 | `.cargo/config.toml` 设置 jobs=1；此前高并行编译内存不足 |

只读环境诊断曾确认：DWM 合成开启、系统透明效果开启、当前桌面和输入桌面均为 `Default`。机器具有 Intel Iris Xe、NVIDIA RTX 3050 Laptop 和 ToDesk Virtual Display Adapter。**虚拟显示适配器影响磨砂目前只是猜测，没有建立因果关系。**

文档编写时再次读到安装程序版本为 0.3.1，实验程序版本为 0.3.2；安装版进程正在运行。实时设置当时为：`desktopMode=false`、`alwaysOnTop=true`、`positionLocked=false`、`startAtLogin=true`，位置约 `(1342,654)`。这些会随用户操作变化，不是期望的最终交付配置。

### 右下角位置的历史基准

0.3.1 首次交付时：外框 `(1546,760)`、`348×186` 物理像素，右边距 26、工作区底边距 74 物理像素。保存的逻辑锚点为 `right=20.8`、`bottom=59.2`，监视器名 `\\.\DISPLAY1`。

旧版会在浮窗解锁拖动时覆盖锚点；排查期间也发生过用户拖动。因此当前 state 中较大的 right/bottom 不一定代表用户想要的桌面位置。后续恢复要结合工作区和窗口实际尺寸，不能只硬编码旧坐标，也不能未经核对把最新浮窗位置当作桌面锚点。

## 3. 已经完成的壁纸磨砂工作（0.3.1）

对应文档：`docs/WALLPAPER-FIX.md`。更早的 `docs/GLASS-AND-STARTUP.md` 包含已被取消的系统主题设计，仅作历史记录。

- 最初尝试在桌面子窗口上使用原生 Blur/Acrylic，未得到可用效果；用户同意使用静态壁纸取样。
- 通过 Windows 壁纸接口获得图片和布局，按组件位置换算取样区域；前端 CSS `filter: blur(14px)` 模糊壁纸图层。
- 8px 圆角，保留壁纸原色，不做日间／夜间覆盖。
- 根据壁纸局部 32×20 取样的平均线性亮度选择文字黑白，阈值 0.18。
- 修复 `未指定的错误 (0x80004005)`：接口返回两个显示器记录，一个有效，另一个矩形读取失败。原代码被无效记录中断；修复为跳过无效记录，匹配有效显示器。单屏场景另有系统壁纸备用接口。
- 壁纸每 5 秒检查，缓存图片，暂时失败保留上次结果，连续三次失败才提示，恢复后清除提示。
- 百分比改为两位小数；注册全局快捷键；桌面返回前先取消置顶，再附着为子窗口。

**局限：壁纸取样只能模糊静态壁纸。即使窗体置顶，它也不会自动变成应用背景取样。这个局限正是用户当前报告的问题。**

## 4. 当前 0.3.2 源码改动

以下是“代码已写入”，不是“功能已通过验收”。

### 4.1 文件索引

| 文件 | 作用及改动 |
| --- | --- |
| `src-tauri/src/backdrop.rs` | 新增 Windows 原生 HostBackdrop 实验与背景亮度读取 |
| `src-tauri/src/lib.rs` | 启动／切换模式时启停原生背景；注册亮度命令；调整锚点保存和快捷键往返 |
| `src-tauri/src/desktop_layer.rs` | 桌面子窗口仍用 SetWindowRgn；浮窗跳过该裁剪，交给 DWM 圆角 |
| `src/main.ts` | desktop/floating 模式标记；浮窗亮度轮询和自适应文字 |
| `src/style.css` | floating 模式强制隐藏壁纸层 |
| `src/wallpaper.ts` | 非桌面模式暂停读取壁纸；模式切换触发刷新；忽略已失效的异步结果 |
| `src/desktop.ts` | 窗口尺寸缓存键加入桌面模式，模式切换可重新更新窗口 |
| `tests/wallpaper-recovery.test.ts` | 新增壁纸暂停／恢复与模式变化后旧请求结果处理测试 |
| `scripts/windows-desktop-smoke.mjs` | 新增浮窗移动后回归原桌面边距的检查；新版检查尚未完整重跑 |
| `package.json`、锁文件、Cargo 配置、Tauri 配置 | 版本改为 0.3.2，新增 Windows Composition 相关依赖特性 |

### 4.2 原生背景当前实现

`backdrop.rs` 的核心流程：

1. `run_on_main_thread` 将原生操作交给主线程，用同步 channel 返回结果。
2. 浮窗取消装饰和阴影，调用 `set_background_color(Some(Color(0,0,0,0)))`。
3. 动态读取 `SetWindowCompositionAttribute`，设置 `WCA_ACCENT_POLICY=19`，启用时 AccentState=5（HOSTBACKDROP），禁用时为 0。
4. 设置 `DWMWA_USE_HOSTBACKDROPBRUSH=17`；清除窗口 region；设置小圆角属性 33，值 3。
5. 创建／保留当前线程 DispatcherQueue、Compositor、DesktopWindowTarget、SpriteVisual。
6. `CreateDesktopWindowTarget(hwnd, false)`；SpriteVisual 填满区域，brush 来自 `CreateHostBackdropBrush()`；设置为 target root。
7. 用 thread_local 保存这些对象，切换桌面模式时隐藏 visual，浮窗时显示。

**当前结果：没有创建报错，但实际背景全黑。**

当前代码还没有检查两处 `DwmSetWindowAttribute` 的 HRESULT。后续应补充结构化诊断；不能因为创建成功就判定效果可用。当前实现还依赖未公开保证的 Accent 枚举取值，不能把它当作完全由公开 API 组成的稳定实现。

### 4.3 自适应文字

- 前端约每 700ms 调用 `backdrop_brightness`，只在原生浮窗运行。
- 原生通过 `GetDC(NULL)` / `GetPixel` 读取合成后窗口边缘 24 个点，以线性 RGB 计算亮度；不持久保存后方应用截图。
- 使用 0.16／0.20 的切换阈值迟滞，避免亮度在边界附近导致字色频繁切换。
- 由于当前画面全黑，亮度始终为 0，文字保持白色。这只是黑屏的结果，不能认为自适应已通过。

### 4.4 桌面锚点修正

- `remember_position` 只在 `desktop_mode && !position_locked` 时更新角落锚点；浮窗位置可以保存，但不更新桌面锚点。
- `ensure_visible` 只在 `position_locked && desktop_mode` 时强制按锚点定位。
- 快捷键进入浮窗时解锁；回归桌面时锁定，以保存锚点恢复位置。
- 已把对应检查加到 Windows 冒烟脚本，尚不能声明新版往返位置功能已通过完整实测。

## 5. 实验矩阵：做过什么、看到什么

本节综合之前会话中的实际工具结果；不是每个早期尝试都有独立日志。不要将一张可能被覆盖的截图当作完整历史证据。

| 方法 | 实际观察 | 判断 |
| --- | --- | --- |
| 桌面子窗口原生 Blur/Acrylic | 早期未得到可用磨砂 | 促成用户选择壁纸取样；不代表所有 Windows 子窗口方案都不可能 |
| 静态壁纸取样 + CSS blur | 0.3.1 可用 | 满足桌面模式，不满足置顶实时应用背景 |
| AccentState=3（BLURBEHIND） | API 返回成功，窗口背景黑色 | 本机未通过 |
| AccentState=4（ACRYLICBLURBEHIND） | API 返回成功，窗口背景黑色 | 本机未通过 |
| AccentState=2（TRANSPARENTGRADIENT），透明颜色 | 在较早、尚未挂 HostBackdrop visual 的实现中，确实透出后方红蓝测试窗口，但边缘清晰，无模糊 | 证明某个实验配置下真实透明可行；不等于磨砂成功，也不保证当前代码同样有效 |
| DWM 属性 38，值 3（Desktop Acrylic） | API 返回成功，观察到不透明灰色；部分组合出现原生标题栏残影 | 未达到要求 |
| 强制 active appearance + SetForegroundWindow | 未解决上述灰色结果 | 不能以抢焦点作为最终方案 |
| 清除 region、修改边框 style、DwmExtendFrameIntoClientArea、空区域 DwmEnableBlurBehindWindow | 多个组合未解决黑／灰背景 | 组合条件不同，见诊断脚本；不应再无记录地排列组合 |
| WinRT CreateHostBackdropBrush + DesktopWindowTarget | 创建成功，实际全黑 | 当前源码主方案，失败 |
| 窗口创建后加 WS_EX_NOREDIRECTIONBITMAP | 未解决黑色 | 这是运行时实验，不等同于创建时设置 |
| **创建时 noRedirectionBitmap=true、shadow=false** | 编译成功；独立后方窗口由暗变亮时，取样亮度仍为 0，断言失败 | 最新实验，同样失败；已实际尝试，勿标记为“尚未尝试” |
| 从外部进程设置 WDA_EXCLUDEFROMCAPTURE | 返回 false | 不能证明应用自身设置也失败；未实现这种截图磨砂方案 |

### 最新失败的关键输出

```text
CSS app: rgba(0, 0, 0, 0)
CSS root: rgba(0, 0, 0, 0)
FLOAT: parent=0, child=false, topmost=true, width=330, height=176
background #202020 brightness 0 ink white
background #e0e0e0 brightness 0 ink white
AssertionError: window must show the actual application behind it
```

亮色测试窗口仍读到 0，说明当前合成结果没有正确显示其颜色。暗背景测试通过阈值本身没有证明力，因为黑屏也会通过。

### 已排除／尚未排除

已观察到：

- DOM、根容器背景为透明，WebView 页面截图背景有透明像素。
- 关闭／隐藏时钟后，对相同屏幕区域截图，确实能看到测试窗口条纹；至少相关实验不是把测试窗口放错了位置。
- 原生屏幕截图显示黑／灰，与单独 WebView 页面截图不同。仅看页面截图不足以验收跨窗口模糊。

尚未确定：

- Tauri 父窗、WebView2 子窗和 WinRT visual 的合成／覆盖关系。
- 从桌面子窗口切换为顶层窗口，是否留下影响背景的合成状态。
- 字体／显示缩放、显卡／虚拟适配器、系统策略对问题的贡献。
- 无 Tauri 的最小原生例子能否在这台机器上稳定显示实时磨砂。

## 6. 测试脚本与证据

### 脚本

| 文件 | 用途／注意事项 |
| --- | --- |
| `scripts/floating-backdrop-probe.mjs` | 启动 release 实验程序，用 CDP 9223 连接 WebView，切换桌面／浮窗，启动独立后方窗口并截图；要求没有其他 personal-day 进程 |
| `scripts/backdrop-test-window.ps1` | 独立 WinForms 后方测试窗，标题含 `background test (auto close)`，约 60 秒自动关闭；从文件读取条纹／颜色 |
| `scripts/probe-backdrop.ps1` | 对指定 HWND 试验透明、Blur、Acrylic、DWM、active、no-redirection 等；诊断代码，不是生产实现 |
| `scripts/capture-widget.ps1` | 获取原生窗口矩形，用 GetDC + BitBlt（SRCCOPY\|CAPTUREBLT）截取真实屏幕区域 |
| `scripts/window-probe.ps1` | 查询父子关系、置顶、位置，模拟拖动／快捷键／Win+D／关闭等 |
| `scripts/windows-desktop-smoke.mjs` | 较全面的原生窗口检查；会发送快捷键、Win+D、移动鼠标 |

`floating-backdrop-probe.mjs` 的 `finally` 会终止测试程序和测试窗口，恢复原 state 文件及 Run 注册项，但**不会自动重新启动安装版**。异常、强制中断和部分初始化失败仍需检查是否恢复完整。启动实验程序可能短暂更新自启路径，因此不要省略恢复检查。

测试控制变量：

- `LIVE_ONLY=1`：只验证当前原生效果，再测试深色／亮色背景和字色。
- `NO_REDIRECTION=1`：在窗口创建后修改 exstyle；不同于构建时配置。
- `TRANSPARENT_HOST=1`：附加运行时透明 Accent 实验。
- 脚本变量按是否存在判断；用 `Remove-Item Env:变量名` 清除，不能用字符串 `0` 作为关闭方式。

### 日志与截图

| 路径 | 解释 |
| --- | --- |
| `build-live-backdrop.log` | 0.3.2 HostBackdrop 编译及 NSIS 打包成功；视觉效果未通过 |
| `build-no-redirection.log` | 最新创建时 no-redirection 实验，构建成功；使用 `--no-bundle` |
| `check-live-backdrop.log` | Windows Rust 编译检查通过 |
| `test-live-backdrop.log` | 50 项自动测试通过 |
| `output/playwright/windows-desktop-smoke.json` | **内容 version=0.3.1**，17 组通过；不能作为当前 0.3.2 通过证据 |
| `output/playwright/glass-native-adaptive-0.3.1.png` | 旧版壁纸原色磨砂的实际截图 |
| `output/playwright/floating-probe-*.png` | 各种实验截图；同名会被后续运行覆盖，历史组合未必一一对应 |
| `output/playwright/live-backdrop-white.png` | 暗背景分支截图，黑屏也能生成；不是实时磨砂通过证据 |

当前没有 `live-backdrop-black.png` 成功产物；最新亮背景断言先失败。最新失败控制台输出见本文件第 5 节，未另存为完整测试报告。

### 测试范围必须区分

- 0.3.1：48 项自动测试、17 组 Windows 原生检查曾通过。
- 0.3.2：50 项自动测试已通过（分组为 19+4+7+5+7+7+1）；新增两项覆盖壁纸模式切换。它们不验证 Windows 合成效果。
- 0.3.2：TypeScript/Vite、Rust 检查、构建成功；**实时背景视觉检查失败**。
- macOS、真实重启登录、物理拔屏、真实休眠、Explorer 重启等未因此获得验证。

## 7. 已联网核实的实现方向

下面是已阅读资料的结论，不等于本机当前实现已经成功。

1. **Mica 主要采用壁纸信息，不满足本需求。**
   - https://learn.microsoft.com/en-us/windows/apps/design/style/mica
2. **Background／Desktop Acrylic 可以显示后方应用和壁纸。** 默认还受失焦、透明效果设置、省电等条件影响，可能退化为纯色。
   - https://learn.microsoft.com/en-us/windows/apps/design/style/acrylic
3. Windows 11 Build 22621 起，`DWMWA_SYSTEMBACKDROP_TYPE` 的 `DWMSBT_TRANSIENTWINDOW` 对应 Desktop Acrylic；`DWMSBT_MAINWINDOW` 对应 Mica。
   - https://learn.microsoft.com/en-us/windows/win32/api/dwmapi/ne-dwmapi-dwm_systembackdrop_type
4. Windows 11 Build 22000 起，`DWMWA_USE_HOSTBACKDROPBRUSH` 支持 Win32 使用 HostBackdropBrush。
   - https://learn.microsoft.com/en-us/windows/win32/api/dwmapi/ne-dwmapi-dwmwindowattribute
5. `CreateHostBackdropBrush` 的取样来自窗口绘制前的后方内容；它本身不允许应用读取 brush 像素，因此文字亮度检测需要独立设计。
   - https://learn.microsoft.com/en-us/uwp/api/windows.ui.composition.compositor.createhostbackdropbrush
6. `DesktopAcrylicController` 提供 TintOpacity、LuminosityOpacity 等控制；`SystemBackdropConfiguration.IsInputActive` 控制材质是否将窗口视为具有输入焦点。它们是后续候选，不是当前已接入功能。
   - https://learn.microsoft.com/en-us/windows/windows-app-sdk/api/winrt/microsoft.ui.composition.systembackdrops.desktopacryliccontroller
   - https://learn.microsoft.com/en-us/windows/windows-app-sdk/api/winrt/microsoft.ui.composition.systembackdrops.systembackdropconfiguration.isinputactive
   - https://learn.microsoft.com/en-us/windows/apps/develop/ui/system-backdrops
7. Tauri/window-vibrancy 源码对较新的 Windows 采用系统背景类型实现 Acrylic，旧版本走 Accent API；不能假设所有 Windows 构建都支持同一种 Blur 方法。
   - https://github.com/tauri-apps/window-vibrancy
8. 微软 Win32 Composition 示例的旧讨论也曾出现 HostBackdrop 黑色；讨论年份和 Windows 版本很重要，不能照搬“Win32 不支持”的历史结论。
   - https://github.com/microsoft/Windows.UI.Composition-Win32-Samples/issues/84
   - https://github.com/microsoft/Windows.UI.Composition-Win32-Samples

## 8. 下一步排查建议（未实施／未验证）

目标是建立能区分原因的最小实验，避免重复组合 API 但没有记录。

1. **做无 Tauri／WebView2 的最小原生磨砂窗口**，让它覆盖独立颜色／条纹窗口。记录系统版本、API HRESULT、style/exstyle、前后台状态和原生截图。用它区分系统条件问题与 Tauri 集成问题。
2. 再加入透明 WebView2，检查父窗、子窗和 composition root 的关系。比较“冷启动就是顶层浮窗”与“桌面子窗切为浮窗”。现有探针会主动经历一次桌面→浮窗，不能代替纯顶层窗口基线。
3. **检查原生背景填充，而不只检查 DOM 透明。** 已阅读本地源码：Tauri `WebviewWindow::set_background_color` 同时设置 window 和 webview；tao 的 `WM_ERASEBKGND` 在背景颜色为 Some 时用 RGB `CreateSolidBrush` 填充，没有利用第四个 alpha 分量。当前调用 `Some(0,0,0,0)` 是否造成黑色覆盖需要对照实验，尚不能认定为根因。
4. 另一条未试线索：微软文档列出 Windows 11 Build 26100 起的 `DWMWA_REDIRECTIONBITMAP_ALPHA`，用于启用重定向位图 alpha。当前未试此属性。它与 `noRedirectionBitmap` 不是同一件事，需先确认目标 HWND 和实际渲染路径。
5. 如选用 Windows App SDK 的 `DesktopAcrylicController`，先明确新增依赖、运行时分发、与现有 WinRT compositor 的关系；当前 Cargo 里的 Windows API 投影并不等于已集成 Windows App SDK。
6. 验证在用户继续操作后方应用时仍保持磨砂；不要靠实际抢焦点实现。检查材质染色是否符合“保留后方原色”的要求。
7. 如考虑屏幕采集再模糊，先评估自我递归、延迟、CPU/GPU 消耗、截图／录屏中时钟是否消失及 ToDesk 影响。**当前没有实现采集回退方案，也没有验证它满足要求。**
8. 实时磨砂成立后，再联调桌面嵌入、圆角、文字自适应和右下角往返，最后更新文档、打包和安装。

## 9. 构建与复现命令

以下在项目根目录 PowerShell 执行。不要自动覆盖安装目录。

```powershell
$env:Path = "$env:USERPROFILE\.cargo\bin;$env:Path"
npm test
npm run build
cargo check --manifest-path src-tauri/Cargo.toml --release
npm run desktop:build -- --no-bundle
```

重现最后一次“创建时 no-redirection”构建（只写临时配置）：

```powershell
$probeConfig = Get-Content src-tauri\tauri.conf.json -Raw | ConvertFrom-Json
$probeConfig.app.windows[0] | Add-Member -NotePropertyName noRedirectionBitmap -NotePropertyValue $true -Force
$probeConfig.app.windows[0] | Add-Member -NotePropertyName shadow -NotePropertyValue $false -Force
$probeConfigPath = Join-Path $env:TEMP 'personal-day-no-redirection.json'
$probeConfig | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 $probeConfigPath
npm run desktop:build -- --no-bundle --config $probeConfigPath
```

先通过托盘退出安装版，再运行探针；不要直接杀掉所有同名进程而不核对路径。

```powershell
Get-Process personal-day -ErrorAction SilentlyContinue | Select-Object Id,Path
Remove-Item Env:NO_REDIRECTION,Env:TRANSPARENT_HOST -ErrorAction SilentlyContinue
$env:LIVE_ONLY = '1'
node scripts\floating-backdrop-probe.mjs
Remove-Item Env:LIVE_ONLY -ErrorAction SilentlyContinue
```

预期当前亮背景断言失败。脚本会短暂弹出独立测试窗口，不要在测试期间拖动时钟或操作测试窗口。恢复后检查 state 和 Run 值是否仍正确，再启动安装版：

```powershell
Start-Process -FilePath 'E:\应用\Personal Day\personal-day.exe' -ArgumentList '--autostart' -WindowStyle Hidden
```

## 10. 接手时必须避免的错误

- **不要再把设置页改成红蓝条纹。** 之前为了验证后方内容临时修改过实验设置页 DOM，用户明确询问为什么设置变成条纹；已经撤销。后续只使用独立命名测试窗口。
- 不要把 `0.3.1` 的 17 组检查或壁纸截图用作 `0.3.2` 实时磨砂的验收结果。
- 不要把 `target/release` 最新 exe 与该目录里的 NSIS 安装器假定为同一配置：最后一次构建用了临时 no-redirection 配置且未重新打包。
- 不要用 WebView 页面截图代替真实屏幕合成截图；不要只测试纯暗色背景。
- 不要把虚拟适配器、GPU、失焦等猜测写成确定原因。
- 不要把历史“跟随系统深浅色”重新带回产品；最终外观应跟随后方内容。
- 不要为了这次磨砂修改时钟计算、两位小数或北京时间规则。
- 不要擅自重置用户作息、自启或位置；测试前备份，结束后恢复。当前设置仍可能在用户操作中变化。

## 11. 完成标准

只有同时满足以下要求，才可以向用户交付“置顶实时磨砂已修复”：

1. 用实际后方窗口切换深、灰、亮和彩色／条纹内容，真实屏幕截图能看到对应背景变化，并且边缘确实被模糊。
2. 用户操作后方 ChatGPT 或其他应用时仍有效，时钟不抢焦点、不频繁闪烁。
3. 字色按背景切换，文字保持清晰，没有日间／夜间底色覆盖。
4. 快捷键：桌面→置顶→拖到别处→再次快捷键，正确回到保存的桌面右下角；重复多次通过。
5. 桌面模式仍满足普通窗口遮挡、Win+D 可见；设置窗口恢复正常样式；关闭隐藏与托盘退出行为不退化。
6. 测试报告写明实际版本、构建配置、测试平台，区分自动测试、原生窗口检查和人工验收；未执行的跨平台／设备测试继续标注未验证。
7. 测试成功后再安装新包，检查安装路径、自启命令和用户设置，并从安装目录启动最终程序。

**接手摘要：先定位 HostBackdrop 在本机 Tauri/Win32/WebView2 组合下显示黑色的原因。当前资料已确认原生实时背景磨砂方向可行，但本项目的实现尚未验证成功。**
