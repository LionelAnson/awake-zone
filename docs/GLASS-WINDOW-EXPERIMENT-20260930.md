# 独立实时玻璃浮窗：隔离实验结果

日期：2026-09-30。接续 `LIVE-BACKDROP-RESEARCH-20260930.md`。本轮没有更改个人时钟产品代码或安装版。

## 结论

**独立 Windows 浮窗已取得实时、无统一黑白染色的模糊证据。** 使用 Windows Graphics Capture 的会话级窗口排除列表，取得后方内容，再经 Direct2D GaussianBlur 显示。此路线不依赖 HostBackdrop 或 DesktopAcrylicController。

最终运行：18 轮，每轮 3 次采样，共 54 组。每组保存输入、GPU 渲染结果、Desktop Duplication、GDI 共四种图像，只保存自建图案及其预览区域。27 项自动检查全部通过；独立体验窗的原生拖动和关闭检查也通过。

限定：这是本机独立原型的桌面合成证据。没有人工物理屏幕验收，没有接入 Tauri／WebView，没有验证产品功能回归，也没有修复原生 Acrylic 的灰色问题。当前安装版为 **0.3.3**，未被替换。

## 实现

```text
显示器合成帧（Windows Graphics Capture）
    ↓ 只在本次会话排除玻璃 HWND，窗口 WDA 保持 NONE
GPU 裁剪窗口区域＋四周 24 px
    ↓ Direct2D GaussianBlur，标准差 6 px，96 DPI 渲染坐标
裁回 320×180 → DXGI swap chain → Win32 浮窗
```

- 独立 C++／Win32 工程：`diagnostics/glass-window/`。没有 WinUI、WebView、WorkerW、SetParent、Accent 或其他背景材质叠加。
- 从显示器创建 GraphicsCaptureItem，再对其会话查询 `IDisplayGraphicsCaptureSession`。使用 `SetWindowExclusionList`，并逐项检查 `GetWindowExclusionList` 读回自身 HWND。
- 窗口仍为 WDA_NONE。排除仅针对自己的采集会话，因此独立 Desktop Duplication 能看到该窗口。本轮没有全局设置 WDA_EXCLUDEFROMCAPTURE。
- GPU 纹理上做局部复制和模糊；连续渲染不经 JS、PNG、base64。自动测试取证时才对已核验的合成图案区域做 CPU 读回。
- “通透”是重绘实时后方图像得到的视觉效果；swap chain 本身是 opaque。没有通过降低整个窗口 opacity 来冒充模糊。
- 不添加统一 tint、luminosity 覆盖或 Acrylic 噪声。小圆角通过窗口 region 裁剪。
- 体验模式采样位置跟随拖动；右上角 X 或右键关闭，最长四分钟自动退出。限制在启动显示器内，留出采样边缘。不支持跨屏体验。

## 工具链与可复现入口

本机 Windows 11 25H2，26200.9457，x64；Windows SDK 10.0.26100.0、现有 MSVC x64，顺序编译 jobs=1。使用系统 Windows.Graphics.winmd 的新接口投影；已有 SDK 头文件不包含该接口。没有安装新的 SDK／Runtime，没有更新产品锁文件或依赖。

```powershell
# 构建；可复用与系统元数据哈希一致的已有投影
powershell -NoProfile -ExecutionPolicy Bypass -File diagnostics/glass-window/build.ps1
# 仅保存自建图案区域，有限时长对照
python diagnostics/glass-window/run.py
# 像素分析及图像索引
python diagnostics/glass-window/analyze.py
# 体验模式启动、拖动、关闭；不保存任何屏幕像素
python diagnostics/glass-window/demo-smoke.py
# 人工体验：屏幕内容仅在内存中处理，不保存截图
powershell -NoProfile -ExecutionPolicy Bypass -File diagnostics/glass-window/demo.ps1
```

最终构建：`output/glass-window/2026-09-30T07-31-27-926Z-build/`。

| 文件 | SHA-256 |
| --- | --- |
| glass-window.exe | `9f23e49e429620da2baa590416f220a78ce952b52fcd1470a27c593181837814` |
| probe.cpp | `88982c3098fece3d402b026c6757d8b5e872c47fbde409f001f52f1b43b7071d` |
| build.ps1 | `b1e2bf9a852f158be19753b57ba3f2b5f5b17c9f353bb1230bdb6d1337122edf` |
| 独立 capture.cpp | `5d9b5afdb07f16634e1d57818a649213ab2fed579028f47ce9eb51db3da29300` |

构建目录 `build-command.json` 记录编译器、全部参数、系统元数据哈希；`source/` 是构建时源码快照。

## 排除自身是否成立

最终矩阵：`output/glass-window/2026-09-30T07-32-02-804077Z-matrix/`。

| 条件 | 自己的 WGC 输入 | 独立桌面合成结果 | 判断 |
| --- | --- | --- | --- |
| 预览与源分开，无模糊 | 后方黑白条纹 | 原样条纹 | 实时取样／显示链路成立 |
| 预览与源分开，模糊开启 | 清晰条纹 | 模糊条纹 | 模糊处理成立 |
| 覆盖图案，洋红标记，未排除 | 浮窗自己的洋红色 | 洋红浮窗 | 证明基线确实采到自己 |
| 覆盖图案，洋红标记，排除自身 | 后方原始条纹 | **仍为洋红浮窗** | 只排除自己会话，其他桌面采样仍见浮窗 |
| 覆盖图案，移除标记，排除自身 | 后方原始图案 | 对应模糊图案 | 实时玻璃效果成立 |
| 取消排除→重新排除 | 自身洋红→后方条纹 | 浮窗仍显示 | 可重复，不是缓存首帧 |

每次采样都核对实际前台为独立图案程序，浮窗未重新获取前台。测试窗口层级调整使用 NOACTIVATE；没有靠抢焦点维持效果。

## 模糊与保色

- 深色 16、灰色 128、亮色 240，以及纯红／绿／蓝大区域，输出与输入的平均绝对误差均低于 1 个 8-bit 色阶。
- 8 px 黑白条纹：列亮度标准差从 127.5 降至 **4.836**。
- 40 px 黑白条纹：输出列亮度标准差仍有 **104.412**；粗结构保留，边缘呈过渡。
- 更换条纹相位、80 px RGB 色块相位，输出随之变化；没有沿用首帧。
- 与独立软件 GaussianBlur 参考的内区平均绝对差最大 **1.592** 色阶。这里软件参考用于空间模糊形状比对，不代替原生桌面取证。
- 三帧输出稳定，帧时间均向前推进。
- 最终 54 组 Desktop Duplication 与 GPU 渲染内区逐像素一致，最大 MAE=0；GDI 与 Desktop Duplication 最终也全部一致。

原始状态和表：`events.jsonl`、`manifest.json`、`measurements.csv`、`analysis.json`。截图汇总为 `contact-sheet.png`。标准差只作为辅助；上述已知图案、空间参考、相位响应和桌面采样共同支持视觉结论。

## 体验模式检查与性能边界

`output/glass-window/latest-demo-smoke.txt` 指向本轮实际体验检查：

- 启动未抢走图案窗口前台。
- 排除自身启用。
- 实际鼠标拖动后，窗口从 (180,240) 移到 (270,267)，采样区域同步更新。
- 点击右上角 X 后正常退出，exit=0。
- 没有保存屏幕图像。

最终矩阵平均处理约 **21.3 帧／秒**。采样记录中的源帧年龄为 77.3–105.3 ms，中位数约 92.4 ms。这个数是读取到的源帧时间与记录时间之差，**不是物理显示器上测得的端到端延迟**。尚未做长期 CPU/GPU、功耗、静态节能、HDR、多屏和拖动流畅性评估。

WGC 的输入是显示器纹理；GPU 后续只裁剪局部，不应宣称系统只采集了那一小块。体验模式只在内存中处理、不保存或上传图像。保留系统采集提示，没有请求或绕过无边框采集许可。安装版时钟不参与采集。

## 中间失败和差异，不隐藏

1. 最初系统提交内存接近上限，cppwinrt／编译器无法创建线程或分配堆。用户释放内存后构建成功。没有关闭用户应用、修改虚拟内存设置。
2. 早期区域检查仅看窗口外接矩形，把一个透明悬浮层当成整块遮挡，导致暂停。随后改为实际命中窗口检查，并在保存输入前逐像素验证已知合成图案；不再以透明窗口的大矩形直接判定整片区域遮挡。
3. 测试过程中一次图案窗口位置变化导致源区域失效；程序停止取证，没有保存越界画面。
4. 文件 IPC 遇到 Windows 短暂共享冲突。脚本写入增加有限重试，native 状态更新在共享冲突时等待下一 UI tick，未阻塞消息循环等待画面。
5. 前一完整矩阵 `2026-09-30T07-27-44-804308Z-matrix` 中，54 次 GDI 采样有一次得到旧蓝色画面；同一轮其余 GDI、Desktop Duplication 与 GPU 证据正常。该目录保留，`allPassed=false`，原因未确定，不能称第一次全部通过。最终构建完整重跑后 54 次两后端一致。一次重跑一致不能保证 GDI 永不再出现问题。

## 收尾与未做项目

自动实验的图案和玻璃进程均正常退出；会话／frame pool Close，D2D、D3D、swap chain 和窗口均释放。没有常驻实验采集服务。人工体验另行启动时最多持续四分钟，关闭体验窗即停止采集。

最终矩阵前后真实设置 SHA-256 同为 `2d70dbbf323e1082587c7fecb02c907e0538422c3aa17278cd035e8a6cfbb001`；自启保持 `"E:\应用\Personal Day\personal-day.exe" --autostart`；安装版 EXE SHA-256 保持 `dd1035fde52db62165692de80852b43216e67b6919b5ee3ca9ad1b0df196af7e`。没有回写或用旧备份恢复用户设置。

未测试：人工物理屏幕验收、ToDesk 实际远程端、其他录屏软件、受保护内容、其他电脑、macOS、HDR、外接屏移除、长时间运行，以及个人时钟接入后的所有回归。不能把独立样例通过等同于产品修复。

下一项动作应先收集用户对独立体验窗的视觉反馈，再评估透明 WebView 的最小接入、采集提示／许可、能耗和失效处理。无需回到 HostBackdrop 参数排列组合，也不需要预先迁移整个 UI 框架。

## 官方依据

- [会话级排除列表 API](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.idisplaygraphicscapturesession.setwindowexclusionlist?view=winrt-26100)
- [接口可用性和显示器会话要求](https://github.com/robmikh/Win32CaptureSample/issues/106#issuecomment-4456613908)
- [Direct2D GaussianBlur](https://learn.microsoft.com/en-us/windows/win32/direct2d/gaussian-blur)
- [系统采集边框与许可](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.graphicscapturesession.isborderrequired?view=winrt-26100)
