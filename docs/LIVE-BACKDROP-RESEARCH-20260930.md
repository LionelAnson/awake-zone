# 实时磨砂研究补充：先做独立玻璃浮窗

日期：2026-09-30。接续 `LIVE-BACKDROP-CONTROLLER-20260930.md`。这是资料核对和有限接口检查，尚无新材质视觉通过结果。

## 现场与既有结论

- 本轮只读核验：安装版已经是 0.3.3，路径 `E:\应用\Personal Day\personal-day.exe`，SHA-256 `DD1035FDE52DB62165692DE80852B43216E67B6919B5EE3CA9AD1B0DF196AF7E`。不能沿用旧报告的“现场仍为 0.3.1”。本轮没有替换安装版、写真实设置或自启。
- 系统版本：25H2，26200.9457。本机开发 SDK 10.0.26100.0。
- 前轮正确绑定的 DesktopAcrylicController 可在后方图案保持前台时切换 Fallback/Active；Active 仍输出不随图案变化的 RGB 219。保留原结论，不认定焦点、GPU、ToDesk 或 Windows 为根因。

## 新发现：采集会话自己的排除列表

微软文档公开 `Windows.Graphics.Capture.IDisplayGraphicsCaptureSession`，包含 `SetWindowExclusionList` 与 `GetWindowExclusionList`。参考样例维护者在 2026-05-15 说明：对显示器创建的 GraphicsCaptureSession 查询该接口，排除列表仅用于对应采集会话；更新后的 26100+ 系统逐步可用，未启用的系统 QI 会失败。

这为以下设计提供依据：**保持浮窗 WDA_NONE → 仅从自己的 WGC 会话排除浮窗 → 取得后方图像 → GPU 局部模糊 → 显示在浮窗内**。

它比全局 WDA_EXCLUDEFROMCAPTURE 更值得优先验证，因为有机会让其他截图／录屏仍看到玻璃浮窗。其他采集后端和 ToDesk 的实际表现尚未验证，不能直接保证。

资料：

- [接口及 ContractVersion](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.idisplaygraphicscapturesession?view=winrt-26100)
- [设置排除列表](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.idisplaygraphicscapturesession.setwindowexclusionlist?view=winrt-26100)
- [维护者说明及可用性限制](https://github.com/robmikh/Win32CaptureSample/issues/106#issuecomment-4456613908)

## 本轮实际检查

独立源码：`diagnostics/wgc-capability/probe.cpp`、`run.ps1`。

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File diagnostics/wgc-capability/run.ps1
```

本地 SDK 原有头文件没有新接口，但系统 `C:\Windows\System32\WinMetadata\Windows.Graphics.winmd` 包含接口和方法。本轮使用已有 cppwinrt 从系统元数据生成隔离投影，用现有 MSVC x64 顺序编译（jobs=1）；没有下载或安装 SDK、Runtime，也没有升级产品依赖。

有效 run：`output/wgc-research/2026-09-30T07-03-53-967Z-capability/`。包含源码快照、构建参数、源码与 EXE 哈希、系统元数据哈希及原始 JSONL。

| 检查 | 结果 |
| --- | --- |
| GraphicsCaptureSession.IsSupported | true |
| UniversalApiContract v19 | true |
| CreateForMonitor | S_OK |
| 未启动会话 QI IDisplayGraphicsCaptureSession | S_OK |
| GetWindowExclusionList | 成功；未设置列表返回 null |
| StartCapture / 取帧 / 写排除列表 / 写 WDA | 均未执行 |
| 资源清理 | session、pool 已 Close；程序 exit 0 |

初次编译的命名空间歧义已修正；中间运行错误枚举 null 排除列表而访问冲突，已加非空判断后重跑。不能把这次探针代码错误当作系统 API 或磨砂失败。失败目录保留。

**这里证明的是接口可查询且 getter 可调用；没有证明 setter、排除效果、实时取景或模糊成功。** 未启动采集，不保存任何屏幕像素。未执行产品回归，因此不新增产品“已验证”结论。

## 其他源码核对

微软 PowerToys 的 AlwaysActiveDesktopAcrylicBackdrop 使用始终 true 的 IsInputActive：

- [锁定源码 386a16ff9461aaa9ab39c2054e0320423495d9b2](https://github.com/microsoft/PowerToys/blob/386a16ff9461aaa9ab39c2054e0320423495d9b2/src/common/Common.UI.Controls/Backdrops/AlwaysActiveDesktopAcrylicBackdrop.cs)

这支持“失焦时配置仍可保持活跃”的实现方式；本项已被旧探针覆盖，不能据此宣称本机视觉问题修复，也没有理由只重复设置 true。

可复用的采集结构：

- [微软 Win32 合成示例，ee50e2ea137dcef7b82ba504eff7435e5ebf5294](https://github.com/microsoft/Windows.UI.Composition-Win32-Samples/blob/ee50e2ea137dcef7b82ba504eff7435e5ebf5294/cpp/ScreenCaptureforHWND/ScreenCaptureforHWND/SimpleCapture.cpp)
- [Win32CaptureSample，49fefe79fd9b11025f0b5eb91783a98888516070](https://github.com/robmikh/Win32CaptureSample/blob/49fefe79fd9b11025f0b5eb91783a98888516070/Win32CaptureSample/SimpleCapture.cpp)

后者使用 CreateFreeThreaded、D3D 纹理及 swap chain，包含尺寸变更和资源释放。这里只核对源码，没有构建运行这两个完整样例。

## 独立玻璃浮窗的建议设计（未实施）

用小型 C++ Win32 程序，仅保留一个约 320×180 的可拖动置顶浮窗和关闭入口。先无时钟、无 WebView、无桌面嵌入。不迁移产品 UI。

1. **先分开放置源与预览**：在独立自建图案窗显示色块和移动条纹，WGC 取指定显示器的合成帧，GPU 裁出图案 ROI，在旁边预览高斯模糊。先证明实时取景、色彩和模糊；此时不需要排除自己。
2. **再将预览移到图案上方**：使用会话排除列表仅排除预览窗。对照未排除、排除、隐藏后的同坐标帧，证明读取的是后方内容而非自己、黑块或缓存。后方图案持续保持前台。
3. **最后做玻璃外观**：圆角裁剪、模糊强度控制，先不加统一黑白色层。GPU 上保留纹理；不把每帧变为 PNG/base64 经 JS 传输。初始目标 30 fps，再测延迟、功耗和拖动；这不是性能承诺。

模糊采用 Direct2D GaussianBlur，先采比小窗更大的边缘区域，模糊后裁回，避免边缘黑晕。文档给出的核半径为 3×标准差（DIP），需要按 DPI 换算像素。不能将整个浮窗的透明度设为零来冒充玻璃。

- [Direct2D 高斯模糊与边缘规则](https://learn.microsoft.com/en-us/windows/win32/direct2d/gaussian-blur)

WGC 对象来源是显示器，GPU 输入仍可能为整屏纹理；“只对局部处理”不能宣称系统只采了局部。未来实验仅持久保存自建图案区域，不保存真实应用内容。采集提示边框及其许可也需要验证；不能为消除边框绕过系统许可。

- [CreateForMonitor](https://learn.microsoft.com/en-us/windows/win32/api/windows.graphics.capture.interop/nf-windows-graphics-capture-interop-igraphicscaptureiteminterop-createformonitor)
- [IsBorderRequired 与用户许可](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.graphicscapturesession.isborderrequired?view=winrt-26100)

新接口若排除失败，备用方案才是自有顶层窗 WDA_EXCLUDEFROMCAPTURE + WGC／Desktop Duplication。该标志会影响其他兼容采集工具，截图、录屏及远程画面可能看不到时钟；只能在告知副作用后单独验证。桌面嵌入时产品使用 WS_CHILD，不能直接沿用要求自有顶层 HWND 的 WDA；浮窗通过后仍需单独解决返回桌面的生命周期。

- [WDA 顶层窗口、所有权和版本要求](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowdisplayaffinity)

## 本轮结论

增加了一条有官方接口依据、且本机可查询的新候选：**WGC 会话级排除自身 + 局部 GPU 模糊**。研究优先级高于继续排列组合 HostBackdrop 参数。独立玻璃浮窗仍未实现和视觉验收；当前安装版 0.3.3 未被替换。
