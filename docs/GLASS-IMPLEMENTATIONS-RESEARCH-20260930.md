# 实时磨砂浮窗：现成实现与延迟线索

日期：2026-09-30。范围：网络检索、固定提交源码核对及当前原型代码对照。本轮没有安装或运行下列第三方程序，没有修改产品或实验程序。第三方性能与当前系统兼容性均未实测。

## 结论

确有接近同款的可拖动磨砂浮窗，但它们同样使用采集后模糊，不能据此认为会更低延迟。最直接可借鉴的是 Magpie 的最新帧处理策略。另一条不同路线是 DWM 共享合成画面，但找到的示例依赖私有接口，并明确存在失焦回退问题，尚不满足“后方应用保持前台”的要求。

## 已核对的实现

### 1. blur-on-screen：几乎同款的小浮窗

- 仓库：[officialdad/blur-on-screen](https://github.com/officialdad/blur-on-screen)。核对提交 `eb2d5b6f96aed2ac6d02f82f4d8a0de4685dcaeb`。
- [核心源码](https://github.com/officialdad/blur-on-screen/blob/eb2d5b6f96aed2ac6d02f82f4d8a0de4685dcaeb/blur_overlay.py)：PySide6 无边框置顶窗口，支持拖动和调整大小；定时器间隔 40 ms，目标约 25 FPS，并非本机实测帧率。
- 使用 `screen.grabWindow` 取得窗口下方区域，再缩小、平滑放大形成模糊，绘制成窗口背景。
- Windows 使用 `WDA_EXCLUDEFROMCAPTURE` 排除自身。失败时采用短暂隐藏后采集的退路，会影响连续更新。
- 它的排除方式也影响其他采集程序；当前原型使用会话内排除自身，与这个全局窗口属性不同。
- 判断：可证明这种产品形态已有实现，不能证明其刷新延迟优于当前 GPU 原型。

### 2. BlurMe：GDI 采集与 CPU 模糊

- 仓库：[HAKORADev/BlurMe](https://github.com/HAKORADev/BlurMe)。提交 `98560a31d77d0cd51f6462551c3b96f0220a31d9`。
- [C++ 源码](https://github.com/HAKORADev/BlurMe/blob/98560a31d77d0cd51f6462551c3b96f0220a31d9/src/blurme_windows.cpp)：后台线程 `BitBlt` → `GetDIBits` → 多轮横向/纵向 box blur 近似高斯模糊 → 通知窗口绘制；也使用 WDA 排除自身。
- 判断：当前原型已经在 GPU 上裁剪与模糊。改用这条 CPU 路径没有已知的延迟收益，不优先移植。

### 3. Magpie：最有用的是取帧和呈现调度

- 仓库：[Blinue/Magpie](https://github.com/Blinue/Magpie)。提交 `b06ec901eef847cbd839bdccab403b50770c8f3c`。
- [GraphicsCaptureFrameSource.cpp](https://github.com/Blinue/Magpie/blob/b06ec901eef847cbd839bdccab403b50770c8f3c/src/Magpie.Core/GraphicsCaptureFrameSource.cpp#L82)：每次更新连续调用 `TryGetNextFrame`，丢弃旧帧，只处理最新可用帧。源码明确说明这可降低低帧率时的延迟。
- GPU 纹理通过 `CopySubresourceRegion` 进入后续处理；`FrameArrived` 用于唤醒处理线程。
- [AdaptivePresenter.cpp](https://github.com/Blinue/Magpie/blob/b06ec901eef847cbd839bdccab403b50770c8f3c/src/Magpie.Core/AdaptivePresenter.cpp)：使用交换链等待对象及最大排队帧数控制呈现节奏。
- 该实现使用 4 个采集缓冲，并持续取到最新帧。因此不能简单把“缓冲数减到 1”等同于低延迟。
- 它主要采集源窗口，不是本任务的显示器磨砂浮窗；借鉴的是调度策略，不能直接替换自身排除逻辑。仓库标注 GPL-3.0，本轮未复制代码。

### 4. Win32-Acrylic-Effect：DWM 共享画面再交给 DirectComposition 模糊

- 仓库：[selastingeorge/Win32-Acrylic-Effect](https://github.com/selastingeorge/Win32-Acrylic-Effect)。提交 `c3a8af7d370742386e14c5ed87f9a04c98900386`。
- [AcrylicCompositor.cpp](https://github.com/selastingeorge/Win32-Acrylic-Effect/blob/c3a8af7d370742386e14c5ed87f9a04c98900386/Acrylic%20Window/AcrylicCompositor.cpp)：通过 DWM 私有共享缩略图/多窗口 visual 接口取得合成内容，附加 DirectComposition 高斯模糊效果；没有我们当前的 WGC 定时取帧循环。
- 通过 ordinal 147、163、164 加载接口，并按系统构建版本切换调用。这里的共享 visual 并不等于此前失败的 `CreateHostBackdropBrush`。
- 关键限制：`WM_ACTIVATE` 调用 `SyncFallbackVisual`；失焦使用 fallbackColor，激活使用 tintColor。README 明确记录实时 Acrylic 限制和失焦回退，所列测试系统是 Windows 10 20H2，不能推断当前 Windows 可用。
- 判断：这是值得有限隔离验证的新背景来源，但还不是成功方案。若实验，先核对当前版本接口、失焦行为、动态图案与自身排除，再考虑接入；不能只删掉回退分支便宣称修复。

### 5. window-vibrancy：原生系统材质接口封装

- 仓库：[tauri-apps/window-vibrancy](https://github.com/tauri-apps/window-vibrancy)。提交 `74216857a010454ae4dd19618787f06b656747b3`。
- [windows.rs](https://github.com/tauri-apps/window-vibrancy/blob/74216857a010454ae4dd19618787f06b656747b3/src/windows.rs)：按 Windows 版本使用 DWM 系统背景类型、Accent Blur/Acrylic 等接口。
- 判断：与此前原生材质实验路线重叠。换一层封装不能保证解决当前机器的视觉失败，也不能把所有原生方案都判为不可用。

### 6. DWMBlurGlass / OpenGlass：系统合成器扩展

- [DWMBlurGlass](https://github.com/Maplespe/DWMBlurGlass)，提交 `e3a160c849c35415a4b959462815eebc6870270b`。核对 README、扩展初始化、Helper 注入代码和 BlurBackdrop/DCompBackdrop。
- [OpenGlass](https://github.com/ALTaleX531/OpenGlass)，提交 `d725eb41a8bc8f0f406840e1f31e3d03132237fd`。核对 README、GlassService 和 Legacy GlassRenderer/D3DGlassRealizer。
- 这些项目通过 DWM 扩展、注入或内部绘制 hook 修改系统合成过程。BlurBackdrop 中直接构造 backdrop brush 和 GaussianBlurEffect，必须结合其 DWM 执行环境理解，不能照搬到普通窗口就假定有效。
- 判断：可作为合成层实现的参考；部署与系统版本耦合较大，对单个时钟浮窗不优先。本轮没有注入、安装或重启 DWM。未测量其延迟。

## 与当前原型的具体差异

当前 `diagnostics/glass-window/probe.cpp` 常规更新路径：

1. `CreateFreeThreaded` 建立 2 缓冲采集池。
2. `SetTimer(..., 33, ...)` 定时更新。
3. 每次通常只取一帧 `TryGetNextFrame`，没有排空旧帧并保留最新帧。
4. GPU 局部裁剪、高斯模糊后 `Present(1, 0)`。

此前实验报告记录约 21.3 FPS、采样源帧年龄中位数约 92 ms。这不是物理屏幕端到端延迟测量，也尚未拆分各阶段耗时。可以确认存在值得优化的取帧/调度设计，不能断言延迟全部来自某一处。

微软文档确认 [CreateFreeThreaded 的 FrameArrived 在内部工作线程触发](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.direct3d11captureframepool.createfreethreaded?view=winrt-26100)；可据此让新帧唤醒渲染处理，而非依赖 UI 定时器。实现时需保留明确的 GPU 上下文线程归属和有界队列。

[SetMaximumFrameLatency](https://learn.microsoft.com/en-us/windows/win32/api/dxgi1_3/nf-dxgi1_3-idxgiswapchain2-setmaximumframelatency) 控制交换链允许排队的帧数，要求创建时使用相应等待对象标志；不能直接对现有任意交换链加一个调用就认为生效。

## 建议的下一步与判据

优先对已确认模糊效果的小方块做低延迟隔离版本：最新帧优先、事件驱动更新、限制呈现排队，保持 GPU 路径。分别测量源帧年龄分布、实际呈现帧率、已知移动图案的响应以及后台前台状态；不能只提高定时器频率或 FPS 数字便称延迟解决。

第二候选是 DWM 共享 visual 小窗。先证明后方图案窗保持前台时仍随图案实时模糊，再比较延迟与保色。初始化失败、失焦回退或画面不更新都应停止该轮，不能把私有接口示例当作当前系统兼容承诺。

本轮只完成源码研究，没有新的第三方运行验证或产品回归结果。
