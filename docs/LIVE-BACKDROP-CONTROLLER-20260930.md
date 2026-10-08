# 2026-09-30 DesktopAcrylicController 状态—画面隔离实验

## 结论

**未修复，不替换 0.3.1。** 本轮实际建立并运行了独立 WinUI 小窗，完成 2×2 焦点／配置对照、重复切换、回退色标记、11 种后方图案及隐藏小窗后的同位置对照。

本报告更新此前交接和《2026-09-30 实时磨砂隔离实验结果》中“尚未尝试 DesktopAcrylicController”的部分。保留此前 HostBackdrop 黑色、系统 Acrylic 白色等有限结论；没有用本轮结果推断所有原生磨砂方案不可用。

本轮最重要的对应关系：

> 后方图案应用保持前台 → IsInputActive 请求与读回均为 true → 绑定成功、State=Active → 客户区稳定为 RGB(219,219,219)，不随后方颜色、条纹或相位变化。

输入配置能重复改变状态；Fallback 时绿色／洋红色能完整到达客户区。因此绑定和回退输出已经获得视觉证据，但 **Active 的实时取样／渲染仍未成功**。这次 Active 是灰色，不应沿用旧实验的“全黑”描述。

默认材质没有通过，故未进入低染色参数测试、透明 WebView2 接入或隔离 Tauri 桥接。未更新产品依赖、生成新版安装器或替换安装版。

## 1. 新现场与隔离范围

- 工作区：`F:\Desktop\codex工作环境\个人时钟`。
- 开始基线：`output/playwright/winappsdk-runs/20260930-133109-baseline/`。
- 有效矩阵 run-id：`2026-09-30T05-45-52-114228Z-matrix`，UTC 时间约 05:45:52—05:48:03，即北京时间 13:45:52—13:48:03。
- 独立工程：`diagnostics/winappsdk-backdrop/`；identifier 为 `com.personalday.probe.winappsdk`；IPC、日志、采样均在本轮 run 目录。
- 安装版 0.3.1 的原进程 PID **24048** 始终保留：`E:\应用\Personal Day\personal-day.exe`。
- 真实设置在本轮开始已是 position `[1524,442]`、anchor right `38.4` / bottom `284.0`。没有从旧报告恢复位置。
- 产品关键源码、package/Cargo 锁文件、Tauri 配置及 `.cargo/config.toml` 哈希均未变；`jobs=1`。保留 lib.rs 中 LocalState 先注册、再初始化 backdrop 的修正。
- 探针窗口在屏幕左上部，客户区 `[149,258,382,233]` 物理像素，DPI=120。截取内部 `[161,270,358,209]`，避开边框、圆角、标题栏。
- 每轮在采样前检查 ROI 内九个点的顶层窗口归属，确认未被安装版时钟或其他窗口遮住。图案窗显示时使用含 SWP_SHOWWINDOW 的操作；仅记录前台句柄不足以替代可见性检查。

## 2. 工具链、版本与参考结构

| 项目 | 本轮实际值 |
| --- | --- |
| 主机 | Windows 11 x64，10.0.26200 |
| C++ 工具链 | VS Build Tools 2026，18.4.11620.152；通过 vswhere 定位，不依赖默认 PATH |
| Windows SDK | 10.0.26100.0，已有 |
| C++/WinRT 生成器 | 2.0.250303.1，已有 |
| .NET SDK | 没有找到可用 dotnet；本轮使用 C++，未安装 .NET 或新 IDE |
| Windows App SDK NuGet | **1.7.260224002（稳定渠道 1.7.9）** |
| 对应运行时 | **7000.785.2325.0 x64**，本机已有 |
| PE 架构核对 | 0x8664，AMD64 |
| 自动分析 | 已有 Python 3.14.3 / NumPy 2.4.4 / Pillow 12.2.0 |

开始时缺少该探针的 NuGet 开发包。仅下载到独立工程的 packages 目录，没有升级产品依赖、安装 Runtime、修改驱动或安装庞大开发环境。WinUI WinMD 需要引用 WebView2 元数据，因此另下载 1.0.2903.40 包用于生成头文件；**探针没有创建 WebView2，也没有把它接入客户区**。

参考锁定为微软 WinUI Gallery：

- 提交：`dbba74ac42e1b4b5ba93ab77ac63be71eaf6cbac`。
- 文件：`WinUIGallery/Samples/SamplePages/SampleSystemBackdropsWindow.xaml.cs`。
- 使用其 Window → ICompositionSupportsSystemBackdrop → AddSystemBackdropTarget → SetSystemBackdropConfiguration 的结构，以及 Windows.System 队列、激活／主题处理和控制器生命周期。
- **版本限制明确记录**：该提交 standalone.props 原依赖 1.7.250513003（1.7.2）。本轮沿同一 1.7 稳定 API 系列，使用与本机已安装运行时精确配套的 1.7.9 开发包编译 C++ 复现。不能称为“未经修改的相同补丁 Gallery 示例运行通过”。参考文件和实际差异均保存在工程中。
- SDK／运行时的版本映射由[微软发布产物表](https://github.com/microsoft/WindowsAppSDK/wiki/WinAppSDK-Released-Artifacts)核对。选择 1.7.9 是为了固定本机现有组合，不是在推荐产品升级到这个版本。

### 实际加载的模块

Bootstrap 返回 S_OK 后才初始化 WinRT 和启动 WinUI。最终进程的模块枚举和文件版本另存 `runtime-modules.json`，不是仅列“已安装多个 Runtime”。所有下列 Runtime 模块均来自：

`C:\Program Files\WindowsApps\Microsoft.WindowsAppRuntime.1.7_7000.785.2325.0_x64__8wekyb3d8bbwe\`

| 模块 | 文件版本 |
| --- | --- |
| Microsoft.UI.Xaml.dll | 3.1.7.0 |
| Microsoft.UI.Windowing.Core.dll | 10.0.27107.1034 |
| CoreMessagingXP.dll | 10.0.27107.1034 |
| dcompi.dll、dwmcorei.dll、wuceffectsi.dll | 10.0.27107.1034 |
| Microsoft.WindowsAppRuntime.Insights.Resource.dll | 7000.785.2325.0 |
| Microsoft.Windows.ApplicationModel.Resources.dll | 1.7.0.0 |

程序旁的 Bootstrap.dll 文件版本为 1.7.0.0，来自锁定的 1.7.9 NuGet 包；系统 CoreMessaging.dll 为 10.0.26100.9444。有些 DLL 没有可读取的版本资源，原始记录为空，没有编造版本。

## 3. 初始化与接口核验

| 核验 | 证据 |
| --- | --- |
| Runtime 依赖解析 | MddBootstrapInitialize(0x00010007, 空标签, minimum 7000.785.2325.0) 返回 0；已枚举实际模块 |
| WinRT | STA 初始化成功 |
| 队列 | UI 线程 Windows.System.DispatcherQueue 和 Microsoft.UI.Dispatching.DispatcherQueue 均非空；自身创建的系统队列保留至退出 |
| 支持检测 | DesktopAcrylicController.IsSupported()=true，仅作为能力记录 |
| 实际目标类型 | `Microsoft.UI.Xaml.Window` |
| 接口 | QI `Microsoft.UI.Composition.ICompositionSupportsSystemBackdrop` 成功 |
| 绑定 | AddSystemBackdropTarget **返回 true**；重建后同样 true |
| 配置 | 非空 SystemBackdropConfiguration，请求及 getter 读回均记录 |
| 客户区覆盖 | 唯一 Grid 背景 `[A,R,G,B]=[0,0,0,0]`，Opacity=1；没有不透明内容 |
| 管理方式 | Window.SystemBackdrop 始终未设置；只用一个显式控制器，没有 Accent、DWM Acrylic 或旧 HostBackdrop visual |
| 目标输出 | target.SystemBackdrop 非空；实际 brush 类型为 `Windows.UI.Composition.CompositionEffectBrush` |
| 生命周期 | controller、target、config、grid、timer、queue 都由 App 成员强引用；线程 ID 与 HWND 所属线程相同；正常解绑、撤销事件、Close、关闭队列、BootstrapShutdown |
| 捕获亲和性 | 由探针自身成功设置 WDA_NONE，未排除自身 |

所选 WinMD 生成的头文件包含旧 `SetTarget(WindowId, Windows.UI.Composition.CompositionTarget)`、现代 AddSystemBackdropTarget、State/StateChanged、ResetProperties。本轮使用现代接口；没有为了旧接口降级。由正确目标读回的 brush 本身就在 Windows.UI 命名空间，也进一步说明不能按命名空间作“一概不兼容”的判断。

没有把编译失败算作视觉失败：初次投影缺少 WinUI 的 Resources/WebView2 元数据引用、初次编译缺少 shobjidl 声明，均已在独立工程中补齐并保留失败日志。有效矩阵使用后续成功构建。

## 4. 配置—状态—画面对应

默认材质：Theme=Light(1)、Kind=Base(1)；没有自定义四个颜色／不透明度属性。默认读回：

- FallbackColor：ARGB `[255,238,238,238]`。
- TintColor：ARGB `[255,243,243,243]`。
- TintOpacity：`0`。
- LuminosityOpacity：`0.9`。

最终探针 HWND=**1647354**；图案 HWND=**2564456**。每轮三个连续采样，状态先持续读回稳定至少约 1.2 秒；未稳定上限 6 秒。最终 34 轮均稳定，保存 **102 对、共 204 张 ROI 图像**。

| 轮次 | 实际前台 | IsInputActive 请求／读回 | 稳定 State | 绑定 | 回退色 RGB | 实际输出（GDI 与桌面合成一致） |
| --- | --- | --- | --- | --- | --- | --- |
| A：前台参照 | 探针 1647354 | true / true | Active | true | 238,238,238 | 均匀 219,219,219 |
| B：前台参照 | 探针 1647354 | false / false | Fallback | true | 238,238,238 | 均匀 238,238,238 |
| C：后方应用前台 | 图案 2564456 | false / false | Fallback | true | 238,238,238 | 均匀 238,238,238 |
| **D：核心产品条件** | **图案 2564456** | **true / true** | **Active** | **true** | 238,238,238 | **均匀 219,219,219，不响应图案** |
| repeat-0 | 图案 2564456 | false / false | Fallback | true | 238,238,238 | 238,238,238 |
| repeat-1 | 图案 2564456 | true / true | Active | true | 238,238,238 | 219,219,219 |
| repeat-2 | 图案 2564456 | false / false | Fallback | true | 238,238,238 | 238,238,238 |
| repeat-3 | 图案 2564456 | true / true | Active | true | 238,238,238 | 219,219,219 |
| marker-green | 图案 2564456 | false / false | Fallback | true | 0,255,0 | **0,255,0** |
| marker-magenta | 图案 2564456 | false / false | Fallback | true | 255,0,255 | **255,0,255** |
| default-recreated | 图案 2564456 | true / true | Active | true | 238,238,238 | 219,219,219 |

A/B 只作诊断，不能当产品方案。D 及重复切换期间没有调用 SetForegroundWindow/SetFocus/模拟点击重新激活探针；配置全部经文件 IPC。强制模式的 Activated 回调只记录事件，不改写 IsInputActive。

标记轮次没有其他纯色 marker 图层。标记之后销毁旧控制器，再创建 generation=2；四项读回恢复为默认值后才称“默认材质”。没有靠把 visual 整体透明当低染色。

### 状态事件的限制

源码已订阅 StateChanged，并在清理时撤销。**本机本轮实际收到该 API 回调数为 0**。不能把 getter 读到的转换写成“StateChanged 事件已触发”。连续读回记录了 10 次状态／代际变化；配置写入和读回之间约一个或两个心跳后才出现新状态。

- `events.jsonl`：配置、激活、原始心跳、模块和清理完整时间线。
- `state-events.json`：非心跳记录；没有伪造 StateChanged 事件。
- `observed-state-transitions.json`：明确标为 State getter 观测的转换。
- `state-image-table.csv`：102 次采样逐一记录实际前台、请求／读回、State、绑定、颜色、不透明度、主题、Kind、图案序号和双采样结果。

回调未送达的原因未确定。这不影响已录得的 getter 状态和画面对应，但属于最小复现需要保留的 API 观察。

## 5. 图案、独立采样与视觉结论

11 种图案：深色 `#101010`、灰色 `#808080`、亮色 `#f0f0f0`、纯红／绿／蓝；8 px 黑白条纹、40 px 黑白条纹、40 px 条纹相位移 20 px；80 px RGB 色块、相位移 40 px。图案均在独立 WinForms 窗口，记录序号和绘制时间。没有修改产品设置界面。

采样同时使用：

1. GDI BitBlt（原有路线）。
2. **DXGI Desktop Duplication**，按 ROI 的实际 HMONITOR 选显示器，只接受 LastPresentTime>0 且 AccumulatedFrames>0 的实际桌面提交。GPU 上裁剪客户区内部后，只有这个合成图案 ROI 会保存到磁盘。没有保存全屏或真实应用内容。

最终桌面采样格式 BGRA8(87)，显示器句柄 65537，适配器标识 Vendor 4318 / Device 9634。两套采样的 **102 对图像逐像素平均绝对差最大值为 0**。这说明本轮选定 ROI 中两种采样一致，不是整个 Windows 捕获系统的普遍保证。没有使用网页截图替代原生画面，也没有声称人工观察了物理显示器。

| 测量 | Active 小窗输出 | 隐藏小窗后的同位置对照 |
| --- | --- | --- |
| 深→亮平均绝对变化 | **0** | 16→240，变化 224 |
| 红／绿／蓝 | 都为 219,219,219 | 分别精确对应 255,0,0 / 0,255,0 / 0,0,255 |
| 8 px、40 px 条纹 | 列空间变化和边缘变化均为 **0** | 条纹清晰存在 |
| 40 px 条纹相位变化 | **0** | 平均绝对变化 **128.2123** |
| RGB 粗结构相位变化 | 固定灰色，无空间／时间对应 | 色块位置随相位改变 |
| 同一轮三个连续帧 | 一直保持相同输出 | 相同图案也稳定 |

Active 既没有保留粗结构与后方颜色，也没有随细纹或相位变化，因此分类为 **不随图案变化的纯色输出，视觉失败**。不是“模糊太强”，也不是“已经透明”；本轮没有依赖非零标准差判断成功，更没有把 Acrylic 噪声当图案。

截图示例（均为 Desktop Duplication）：

| 隐藏小窗后的条纹 | 显示小窗、失焦且 true | Fallback 绿色 | Fallback 洋红色 |
| --- | --- | --- | --- |
| ![原始条纹](../output/playwright/winappsdk-runs/2026-09-30T05-45-52-114228Z-matrix/underneath-7-0-dd.png) | ![Active 固定灰色](../output/playwright/winappsdk-runs/2026-09-30T05-45-52-114228Z-matrix/Dpattern-7-0-dd.png) | ![绿色回退输出](../output/playwright/winappsdk-runs/2026-09-30T05-45-52-114228Z-matrix/marker-green-0-dd.png) | ![洋红回退输出](../output/playwright/winappsdk-runs/2026-09-30T05-45-52-114228Z-matrix/marker-magenta-0-dd.png) |

### 采样自身的修正

第一轮 `2026-09-30T05-40-40-414851Z-matrix` 中，Desktop Duplication 初帧虽返回 S_OK，但 LastPresentTime=0、AccumulatedFrames=0，得到全黑缓冲；隐藏探针后该路线仍黑，而 GDI 对照正常。这些首帧**不是有效的材质证据**，不能用来推翻 GDI。

随后增加实际显示器匹配、丢弃无桌面提交的帧，并让独立图案窗在 ROI 之外的小区域持续更新以产生新提交；完整重跑全部矩阵。最终有效帧的提交时间及帧数均满足条件，并通过隐藏探针对照。旧轮次保留 `run-assessment.json` 标为独立采样无效；其 GDI 结果与新轮次一致。期间一条进度更新曾把初帧黑色误当结果，发现后已明确更正。

## 6. 已证实、未知及未测试项

**已证实：**

- 本机指定版本可完成初始化、现代目标绑定及控制器配置，回退输出到达目标。
- 实际焦点与 IsInputActive 已分离；后方应用保持前台时，配置能够重复改变读回状态和灰色／回退色输出。
- D 的默认材质不响应后方图案；获得前台的 A 也没有实时图案响应。因此本轮失败不能全部归因于失焦。
- 用户要求的实时、低染色磨砂未获通过证据。

**仍未确定：**

- Active 的实时取样或材质合成内部为何输出统一灰色。
- 为什么本轮没有收到 StateChanged 回调。
- ToDesk、各 GPU、Windows 内部实现、会话或其他环境因素是否参与。不能由这些现象单独认定任一项为根因。

只读现场：系统透明效果开、高对比度关、AC 供电、电量 100%、系统节能状态标志 0、SM_REMOTESESSION=0。后者不能排除 ToDesk 这类非 RDP 远程工具的存在。没有改变这些开关、停止 ToDesk、禁用适配器或修改驱动。

**本轮未执行：**

- 降低 TintOpacity、再降低 LuminosityOpacity、两者为 0。默认基线已失败，按停止条件不做这些候选；默认 TintOpacity 本身读回就是 0，但这不是低染色成功证明。
- 透明 WebView2／Tauri 接入、窗口重建或双窗口方案。
- 新产品的字色、快捷键、拖动锚点恢复、Win+D、托盘、关闭隐藏、设置、自启回归。没有产品候选，所以没有将旧自动测试或旧 0.3.1 回归复制成新版本通过记录。
- 本轮未重跑产品 npm/cargo 测试；探针构建、矩阵和像素分析是本轮实际验证范围。
- 物理屏幕人工验收、macOS、真实重启／休眠／拔屏。
- 同补丁原版 Gallery 全应用、其他 Windows App SDK 主／次版本及其他机器。

## 7. 下一项动作：仅隔离原型设计

停止本轮原生材质参数搜索。保留当前最小复现及状态—图像对应表，不宣称所有原生方案不可能。

下一候选是“**应用自身 WDA_EXCLUDEFROMCAPTURE + 桌面采集 + 局部模糊**”，本轮只设计，**没有设置此标志、没有执行该原型或接入产品**。

建议独立原型的第一道门槛：

1. 单独创建顶层测试小窗，由拥有该 HWND 的同一进程调用 WDA_EXCLUDEFROMCAPTURE 并读回；后方仍只使用自建图案。
2. 以 WDA_NONE 作基线，然后使用选定采集后端读取同一个 ROI。必须证明采到后方图案，而不是自身、黑块或旧帧；加入相位变化排除缓存。
3. 必须另有物理屏幕观察确认小窗仍显示。被排除的屏幕采集本身无法证明这一点；本轮没有这项人工证据。
4. 只有排除自身成立，才做局部模糊、色彩保持、延迟和反馈环检查；下一阶段需要相应授权再实施。

过去跨进程设置 WDA 失败不能否定自有顶层 HWND 的这条路线。副作用是时钟可能从截图、录屏、直播或 ToDesk 画面中消失；不同后端行为还需单独测量，不能承诺完全一致。若以后接入产品，这些影响和持续采集方式需要明确告知并提供控制。

## 8. 构建、命令与哈希

最终有效构建：`output/playwright/winappsdk-runs/2026-09-30T05-44-47-892Z-build/`。

| 产物 | SHA-256 |
| --- | --- |
| winappsdk-backdrop.exe | `1d8b42068ae7b7d255e23dffa3a1c250eb3449ef9d37fdabaa2a8bd972808bad` |
| capture.exe | `6fa59b8591e6f3f6e8067eb33663f1229086dcc30b5d9e2c0b168f30cda00b27` |
| SDK NuGet 包 | `1f28e5cac92c59bbfcf2767240bcfa302cc38e069f6ea9cd579cd8c1f0ed6380` |
| 元数据引用包 | `ef128016dd1e51c59178c827ed5b8aa3322c57afa8675d930f8109505542ad74` |
| 保留的安装版 EXE | `cd4c0d4a4722844fa716321a7b7567e3cc7823838d900f9cf26d235cade1a246` |

构建目录的 `manifest.json` 有对应源码逐文件 SHA-256，`source/` 是本次构建使用的源文件快照；`build-command.json` 是实际 cl 路径和完整参数，`build.log`／`capture-build.log` 是执行结果。交付索引另含运行脚本、分析脚本、文档和最终产物哈希。不能用另一个 build 目录的 exe 替代上述有效矩阵二进制。

本轮实际使用、可从项目根目录复现的入口：

```powershell
# 包哈希校验和 WinMD 投影；不安装系统环境
pwsh -NoProfile -File diagnostics/winappsdk-backdrop/prepare.ps1
# 顺序单进程编译，独立输出目录
pwsh -NoProfile -File diagnostics/winappsdk-backdrop/build.ps1
# 实際窗口、焦点/输入配置、图案与双采样矩阵
python diagnostics/winappsdk-backdrop/run.py
# 全部帧分析和状态表
python diagnostics/winappsdk-backdrop/analyze.py
```

初次下载与投影命令、逐次构建执行记录汇总另见证据根目录 `commands.md`。工具链选定、SDK SHA 校验、prepare.ps1、最终 C++ 构建、完整矩阵、分析均在本轮实际执行。

## 9. 清理与保留

- 最终探针 PID 50552 与图案 PID 25000 均正常退出，exit code=0；没有强制终止记录。
- 退出记录确认控制器移除目标并关闭、事件撤销、config/target/grid/timer 释放、系统队列异步关闭完成、BootstrapShutdown。
- 每次 Desktop Duplication 有限采样进程均释放 frame、D3D 对象及 GDI 对象后结束；没有留下采集会话。
- 收尾仅安装版 personal-day.exe 原 PID 24048 在运行，没有实验小窗／捕获进程残留。
- 从本轮基线至收尾，真实设置文件 SHA-256 一直为 `215df8a99094922f89964cbbc67d92dbe35a70d0c5cdd9850fb3958e0ca649b8`，自启仍为 `"E:\应用\Personal Day\personal-day.exe" --autostart`。
- 本轮没有向真实设置或 Run 注册表项写入，也没有用备份恢复文件。将来复现时若用户期间主动移动时钟，包装器只记录变化，不覆盖更新。

证据在有效 run 中的 `restoration-check.json`。本轮最终状态仍为：**未修复，不替换 0.3.1。**

## 10. 主参考资料

- [锁定提交的微软控制器示例](https://github.com/microsoft/WinUI-Gallery/blob/dbba74ac42e1b4b5ba93ab77ac63be71eaf6cbac/WinUIGallery/Samples/SamplePages/SampleSystemBackdropsWindow.xaml.cs)
- [微软系统背景控制器用法](https://learn.microsoft.com/en-us/windows/apps/develop/ui/system-backdrops)
- [DesktopAcrylicController API](https://learn.microsoft.com/en-us/windows/windows-app-sdk/api/winrt/microsoft.ui.composition.systembackdrops.desktopacryliccontroller?view=windows-app-sdk-1.7)
- [SystemBackdropConfiguration.IsInputActive](https://learn.microsoft.com/en-us/windows/windows-app-sdk/api/winrt/microsoft.ui.composition.systembackdrops.systembackdropconfiguration.isinputactive?view=windows-app-sdk-1.7)
- [Desktop Duplication AcquireNextFrame](https://learn.microsoft.com/en-us/windows/win32/api/dxgi1_2/nf-dxgi1_2-idxgioutputduplication-acquirenextframe)
