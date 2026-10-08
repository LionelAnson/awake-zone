# 个人时钟 / Personal Day

> 当前 0.4.6 接入透明同心双环图标：外环为“醒时区”时钟，内环为现实时钟；默认按清醒 16 小时、睡眠 8 小时绘制。见 [图标更新记录](docs/ICON-046.md)。

> 当前 0.4.5 修正拖动到屏幕边缘后突然居中的问题，保留可操作的摆放位置；完全移出屏幕时就近恢复。见 [位置修正记录](docs/PLACEMENT-045.md)。

> 0.4.4 在取得 Windows 无边框采集许可后关闭时钟采集会话的黄色边框；Win+Alt+X 与 18% 浅灰磨砂保留。见 [黄色边框修正记录](docs/BORDERLESS-044.md)。

> 0.4.3 使用 **Win+Alt+X**：旧 Ctrl+Alt+Z 在本机被其他程序占用，已按用户确认更换。磨砂浅灰遮罩仍为 18%。

> 0.4.2 将浅灰色遮罩从 10% 调至 18%，保持实时模糊与 Ctrl+Alt+Z 快捷键。

> 0.4.1 快捷键改为 **Ctrl+Alt+Z**，独立接收按键并按顺序切换，修正最小化时保存错误位置的问题。见 [快捷键修正记录](docs/HOTKEY-041.md)。

> 0.4.0 将已验证的小方块采集磨砂接入 Windows 置顶时钟：GPU 局部模糊、新帧驱动刷新、会话内排除自身。桌面状态仍使用壁纸取样，隐藏后暂停采集。当前交付与限制见 [实时磨砂接入记录](docs/LIVE-CAPTURE-040.md)。旧原生材质失败记录仍保留，它们不代表新采集方案的结果。

一个 Tauri 2 + Vite + TypeScript 桌面小组件，使用原生 HTML/CSS。0.3.1 版在 Windows 上嵌入桌面层，使用保留壁纸原色的取样磨砂，字色按背景亮度切换，并支持登录自启、右下角位置固定和全局快捷键。按作息换算个人时间，不检测睡眠。

**时区固定为北京时间 `Asia/Shanghai`**（按后续需求调整），电脑切换时区不会改变作息边界。没有账号、联网同步、AI、打卡或统计。

## iOS 小组件

原生 WidgetKit 源码、SwiftUI 作息设置页、App Group 配置和 XcodeGen 工程描述在 [`ios/`](ios/)；由于当前工作环境是 Windows，没有 Xcode 或 Apple SDK，本地未执行 iOS 构建、签名和真机验证。详见 [`ios/README.md`](ios/README.md)。

## 使用

- 左侧为常规北京时间，右侧为个人时钟；默认不显示秒。
- 沿用约 6／4 个图标位的两档宽度（240／160 CSS 像素）。删除底部倒计时和作息两行，清醒时高度收紧为 128，休息时为 158。系统文字放大和屏幕缩放会相应扩大窗口，图标位为近似值。
- Windows 默认嵌入桌面：普通窗口可以盖住组件，Win+D 显示桌面后组件仍在。不占任务栏位置。
- **Win + Alt + X**：切换为置顶小窗，再按回到桌面层；按住不会连续切换。Windows 的 [RegisterHotKey](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-registerhotkey) 不提供 Fn 修饰键，Fn 不参与识别；键盘固件是否改变这组按键的输出需按具体键盘确认。快捷键被占用时会提示。
- 置顶后解锁拖动，浮窗移动不会改写保存的桌面位置；返回桌面后自动锁定。鼠标移到浮窗上时，顶栏出现“回到桌面并固定位置”按钮，点击即可返回。桌面状态不显示此按钮，托盘也提供“置顶 / 回到桌面”。
- 默认锁定位置，保存与所在屏幕右边、底边的距离。点小锁解锁、拖动顶栏调整，再锁定；切换尺寸和屏幕工作区变化时保持边距。组件不支持手动拉伸。
- 外观没有日间／夜间模式；按最新要求，在磨砂上叠加 18% 不透明度的浅灰色（#d8d8d8），使用 8px 小圆角。按组件下方区域的平均亮度选择字色：暗背景白字，灰色或亮背景黑字。系统颜色模式不影响组件。Windows 桌面子窗口不支持本机的原生背景模糊，因此按确认后的方案读取当前静态壁纸，做对应区域的模糊取样。桌面状态不会采集其他应用，动态壁纸动画不会被取样。置顶/独立小窗状态使用显示器采集，仅在 GPU 绘制时钟下方区域，不上传、不保存应用画面；每半秒读取少量亮度信息选择字色。取得 Windows 无边框采集许可后关闭本时钟会话的提示边框，未获准时保留系统提示；使用会话内自身排除，不设置全局截图排除属性。隐藏窗口时停止采集，恢复显示后重新创建采集会话。
- 设置在独立窗口中打开，可修改作息、大小、位置锁和登录自启。自启开关默认关闭；本机已按用户要求开启，登录后运行安装目录中的程序。取消勾选即可移除自启项。
- 在 Windows 设置中取消「嵌入桌面层」可切换为独立小窗，随后才能启用置顶。macOS 当前使用固定小窗，未实现或验证桌面层嵌入。
- Windows 托盘 / macOS 菜单栏提供「显示 / 隐藏」「设置…」「退出」。选择「退出」结束程序；关闭设置只隐藏设置窗，系统关闭组件会隐藏组件。
- 首次运行使用 08:00 起床、次日 00:00 入睡。常规 12:00 对应个人 06:00，进度 25.00%。

把组件放在桌面空白处即可；它不会自动挪动已有桌面图标。Windows Explorer 的桌面宿主结构并非稳定的跨平台接口，Explorer 重启、第三方壁纸软件及虚拟桌面切换仍需单独验收。若桌面宿主不可用，启动时会明确提示并暂以固定小窗显示。

壁纸每 5 秒检查一次变化；移动、缩放、恢复显示时更新取样坐标。读取时跳过无效的显示器记录；单屏环境另有系统壁纸接口作为备用。暂时失败时保留上次可用的取样，每 5 秒重试，连续 3 次失败才提示；恢复后自动清除壁纸提示，时钟继续更新。图片按文件变更标记缓存，单张壁纸上限 64 MB。支持系统壁纸的居中、平铺、拉伸、适应、填充和跨屏布局换算；当前机器实测为单屏「填充」。macOS 壁纸取样尚未实现。

## 环境与命令

需要 Node.js 22.12+（本次使用 24.14.1）、npm、稳定版 Rust/Cargo。

Windows 还需要 Visual Studio C++ Build Tools、Windows SDK、WebView2；macOS 需要 Xcode Command Line Tools。参见 [Tauri 官方前置依赖](https://v2.tauri.app/start/prerequisites/)。

本机初检：目录为空，Node/npm、C++ Build Tools、Windows SDK、WebView2 已存在；缺少 Rust/Cargo。本次已安装 Rust 1.98.1 的最小工具链及 rustfmt/clippy，未修改用户的永久 PATH。新的 PowerShell 会话可先运行：

```powershell
$env:Path = "$env:USERPROFILE\.cargo\bin;$env:Path"
```

0.3 新增 Rust 依赖 `tauri-plugin-autostart`，以及 Windows 壁纸接口、图片传输和自启路径处理所需的 `windows`、`base64`、`winreg`，本机均已安装。没有新增前端组件库。

0.3.1 新增 `tauri-plugin-global-shortcut`，已安装并锁定在 `Cargo.lock`。

0.4.0 的 Windows 原生模块使用 C++/WinRT、D3D11、Direct2D 和 DirectComposition，静态链接进主程序，不需要额外安装 Windows App SDK Runtime。新增直接构建依赖 `cc = 1.5.1`（原锁文件已有该版本）。所需工具链本机齐全，没有安装新的大型开发环境。采集会话自身排除接口要求较新的 Windows，运行时接口缺失会提示并回到桌面方案；其他 Windows 版本尚未验证。固定生成的 WinRT 头文件和来源说明在 `src-tauri/native`。

```sh
npm ci
npm test                 # 自动计算测试：北京、纽约、Lord Howe 独立进程
npm run build           # TypeScript 检查 + 前端生产构建
npm run dev             # 浏览器预览 http://127.0.0.1:1420
npm run desktop:dev     # Tauri 桌面开发
npm run desktop:build   # 当前操作系统的发布包
```

PowerShell 执行策略禁止 `npm.ps1` 时，将 `npm` 换成 `npm.cmd`。桌面开发命令会自动启动 Vite，请先停止单独启动的 `npm run dev`，避免 1420 端口冲突。

Rust 检查与测试：

```sh
cargo fmt --manifest-path src-tauri/Cargo.toml --check
cargo test --manifest-path src-tauri/Cargo.toml --lib --release
cargo clippy --manifest-path src-tauri/Cargo.toml --all-targets --release
```

Windows 发布版冒烟测试（先关闭正在运行的小组件）：

```sh
npm run test:windows
```

它启动真实可执行文件，通过 WebView2 调试连接和 Win32 查询检查壁纸读取与失败恢复、两位小数、快捷键、桌面父子关系、普通窗口遮挡、两档尺寸、设置、锁定/解锁拖动、关闭隐藏、重启及位置恢复。**测试会短暂发送 Ctrl+Alt+Z、两次 Win+D，并移动鼠标测试拖动**，运行时请暂勿操作鼠标键盘。仅测试期间启用本机 9223 调试端口，结束后恢复原设置并终止测试进程。结果写入 `output/playwright/windows-desktop-smoke.json`。该测试不覆盖托盘菜单实际点击、安装器、物理拔屏或真实休眠。

浏览器界面检查（先运行 `npm run dev`，需要已安装 Chrome）：

```sh
npx --yes --package @playwright/cli playwright-cli -s=personal-day open http://127.0.0.1:1420 --browser chrome
npx --yes --package @playwright/cli playwright-cli -s=personal-day run-code --filename=scripts/browser-checks.js
npx --yes --package @playwright/cli playwright-cli -s=personal-day close
```

脚本冻结页面时钟并检查设置持久化、无效设置、午夜、休息、起床及舍入边界，不修改操作系统时间。

首次 Rust 编译需要下载依赖，可能较慢。本机首次高并行编译发生内存不足，项目 `.cargo/config.toml` 设置 `jobs = 1`；可在资源充足的机器上覆盖。锁文件 `package-lock.json` 与 `src-tauri/Cargo.lock` 固定依赖。

### 打包

- Windows：运行 `npm run desktop:build`，默认输出 `src-tauri/target/release/bundle/nsis/Personal Day_0.3.3_x64-setup.exe`；独立可执行文件为 `src-tauri/target/release/personal-day.exe`。本轮为保留旧构建，使用独立 target 目录，交付文件放在 `release/`。
- macOS：在 Mac 上安装依赖后运行同一命令，输出 `.app` 与 `.dmg`。使用 `tauri.macos.conf.json`，最低 macOS 12。未在本机运行或验证。签名、公证需要自己的 Apple 开发者凭据，工程不包含这些凭据。
- Windows 配置使用当前用户 NSIS 安装，不要求管理员权限。产物未做代码签名。安装流程的实际验证状态见下节。

## 验证状态

本轮请查看 [0.3.3 快捷键与隐藏返回按钮](docs/HOTKEY-TOGGLE-033.md)。[0.3.1 壁纸修复与快捷键验证](docs/WALLPAPER-FIX.md)、[0.3 磨砂记录](docs/GLASS-AND-STARTUP.md)、[0.2 桌面组件记录](docs/DESKTOP-WIDGET.md)和[0.1 历史记录](docs/VERIFICATION.md)作为历史记录保留。**自动计算测试、浏览器检查、原生桌面检查和人工验收分别记录。**

macOS 仅保留工程结构，未验证。外接屏真实拔插、休眠唤醒、不同 DPI 显示器以及托盘实际点击需要相应设备上的人工验收。

## 时间规则

`src/time.ts` 的 `calculateDay(schedule, now, timeZone)` 可注入当前时间。产品固定使用默认时区 `Asia/Shanghai`，第三个参数供底层跨时区/DST 测试使用。

1. 按目标时区的**日历日期**构建起床时间。当前时刻早于当天起床时，使用前一日的起床边界。
2. 入睡时刻小于起床时刻时，入睡日期加一个日历日。相同时无效。
3. `p = (now - start) / (end - start)`，减法均为时间戳的真实毫秒差。
4. 一次计算返回个人时间、比例、百分比及剩余真实时长，UI 不分别计算这些数值。
5. 个人总分钟 `floor(p × 1440)`，清醒期上限 1439；百分比截断到两位小数，清醒期上限 99.99%。进度条使用同一结果的原始比例。末尾不足 0.01% 的差异是显示精度所致。
6. 到计划入睡后，个人时间固定 24:00，进度 100.00%；显示「计划休息时段」「上一清醒日已结束」及下次北京时间起床日期/时刻。到下一次起床开始新的清醒日。
7. 计算模块仍返回真实剩余时长，0.3 界面按需求隐藏倒计时与作息行；作息可在设置里查看。
8. 每秒重新读取 `new Date()`，焦点恢复、页面显示时也重算，不累计定时器次数。界面没有秒数、闪烁、持续动画或强提醒。

使用 `@js-temporal/polyfill` 处理指定时区的日历运算，不假设一天固定 24 小时。夏令时歧义采用 `compatible` 规则：不存在的墙上时刻向后移动一个跳变间隔；重复时刻取第一次。若跳变使清醒时段长度小于等于零，则明确报错。现代北京时间无夏令时，DST 测试用于保证底层日期算法正确。

## 本地保存与窗口恢复

桌面设置使用 Tauri 的 `app_config_dir()/state.json`：

- Windows 通常为 `%APPDATA%/com.personalday.widget/state.json`。
- macOS 通常为 `~/Library/Application Support/com.personalday.widget/state.json`。

包含作息、组件大小、位置锁、桌面模式、置顶、自启、窗口物理坐标和右下角边距。为兼容旧配置保留 `theme` 字段，但不再用它控制外观。保存时保留 `state.json.bak`；若主文件损坏，保留 `state.damaged.json` 并尝试备份恢复，界面显示提示。写入失败会在界面报错。浏览器预览使用独立 `localStorage`，不会修改桌面设置，也不能启用登录自启或读取系统壁纸。

位置在变化后每两秒保存，隐藏与退出时立即保存；突然断电可能丢失最后两秒的位置变化。锁定时使用屏幕名称和按 DPI 换算的右／下边距恢复位置；目标屏幕消失则回到主屏幕。解锁时仍检查窗口是否在可见区域内。单实例插件避免多个窗口同时写同一文件。

托盘与关闭事件由 Rust 处理。窗口结合屏幕 DPI 和 WebView 文字缩放调整尺寸，保留系统文字放大设置。没有鼠标穿透。壁纸读取接口只读取系统报告的当前壁纸路径，页面不具备任意文件系统访问能力。

自启通过 [Tauri autostart 插件](https://v2.tauri.app/plugin/autostart/)实现。Windows 使用当前用户的 `Run/Personal Day` 项，路径显式加引号以支持含空格的安装目录。macOS 保留 LaunchAgent 工程支持但未运行验证。壁纸来自 Windows 的 [IDesktopWallpaper 接口](https://learn.microsoft.com/en-us/windows/win32/api/shobjidl_core/nn-shobjidl_core-idesktopwallpaper)，只在本机处理。

## 源码结构

```text
src/time.ts                   独立的日期与比例计算
src/preferences.ts            前端设置校验
src/main.ts                   单快照渲染、设置交互
src/desktop.ts                Tauri / 浏览器适配
src/wallpaper.ts              壁纸取样位置与模糊背景
src/wallpaper-contrast.ts      组件下方壁纸亮度取样与字色选择
src/style.css                 无底色覆盖的磨砂卡片
tests/                        时间边界和 DST 自动测试
scripts/test.mjs              在独立 TZ 进程运行测试
src-tauri/src/lib.rs           托盘、关闭隐藏、设置文件、窗口恢复
src-tauri/src/desktop_layer.rs Windows 桌面层、原生显示/隐藏与拖动
src-tauri/src/wallpaper.rs     Windows 当前壁纸读取与变更标记
src-tauri/tauri.conf.json      共享桌面配置
src-tauri/tauri.windows.conf.json
src-tauri/tauri.macos.conf.json
assets/icon.svg               图标源文件
output/playwright/            界面检查截图
docs/DESKTOP-WIDGET.md         0.2 桌面组件实测证据及待验收项目
docs/GLASS-AND-STARTUP.md       0.3 磨砂、自启与固定位置验证
docs/WALLPAPER-FIX.md           0.3.1 壁纸错误修复、百分比与快捷键验证
docs/VERIFICATION.md           0.1 历史验证记录
```

重新生成图标：`npm run tauri -- icon assets/icon.svg`。
