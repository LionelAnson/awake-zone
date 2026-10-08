# 磨砂方块刷新改进

## 改动

采集池 FrameArrived 通过合并后的窗口消息唤醒渲染；回调不操作 GPU。GPU 与 D2D 仍归 UI 线程使用。每次取尽可用帧，仅处理最新帧。交换链开启等待对象支持并设置最大排队帧数为 1，仍使用 Present(1,0)，未声称消除所有呈现等待。100 ms 定时器仅作为控制命令、超时和刷新兜底。状态文件降至最多约每秒十次，减少每帧文件写入。

退出时先关闭回调入口、取消订阅，再关闭采集会话和帧池、释放绘制对象。没有改变模糊强度、窗口排除方式或安装版产品。

## 本机 Windows 实测

- 构建：`output/glass-window/2026-09-30T08-01-50-804Z-build`，jobs=1。
- EXE SHA-256：`2182f232c3561aa4e9fbedbb873cded5db6ed6240609dc3133b7b6f4b12039e6`。源码哈希见同目录 manifest.json。
- 矩阵：`output/glass-window/2026-09-30T08-02-07-481805Z-matrix`。
- 18 轮、54 次样本，27 项分析检查全部通过：颜色、粗细条纹、相位变化、自身排除、实际前台、Desktop Duplication/GDI 与 GPU 输出一致性等。
- 同一矩阵流程的历史基线约 21.33 FPS，新版约 48.01 FPS。不同时间运行，未进行交替重复实验或能耗测试，不能排除系统负载差异。
- 样本处源时间戳差中位数从 92.36 ms 降至 4.44 ms；这不是物理屏幕端到端延迟。
- 新增 Present 后的有符号时间戳差记录出现约 -4 至 1 ms；部分体验状态也为负。原始数据保留，原因尚未校准，不能解释为负延迟或据此声称实际延迟只有几毫秒。矩阵快照的 presentedAgeMs 对应上一呈现帧，frameAgeMs 对应当前样本，两者不能当作同帧阶段耗时相减。
- 矩阵 droppedFrames=0，说明本轮并未观测到明显积压；不能把全部改善归功于丢帧策略。拖动测试观测到丢弃 1 帧。

## 交互与失败记录

首次 demo-smoke 的启动保持后方前台、拖动跟随均通过，但关闭按钮点击超时。脚本使用状态文件中的位置点击，而新状态节流导致拖动后坐标可能尚未更新。改为等拖动结束并读取实际窗口矩形再点击，重跑通过：启动失焦、拖动采样位置跟随、自身排除、关闭正常退出；未保存实际应用画面。首次失败目录保留。

最终交互结果见 `output/glass-window/latest-demo-smoke.txt` 指向目录。此次修改的是独立原型，不是产品回归；未验证 macOS、长期能耗、多显示器、受保护内容、物理端到端延迟。

矩阵前后安装版 EXE、真实设置和自启一致，productUnchanged=true。实验图案和测试探针已退出。另行打开体验方块供用户观察，四分钟自动结束；右键可提前关闭。

## 交付与复现

独立体验包：`release/Glass-Square-Low-Latency/Start-Square.cmd`。旧体验包保留，安装版未替换。

```powershell
powershell -NoProfile -File diagnostics/glass-window/build.ps1
python diagnostics/glass-window/run.py
python diagnostics/glass-window/analyze.py
python diagnostics/glass-window/demo-smoke.py
```

前两项实验会打开独立测试窗；只保存已验证为自建图案的区域。当前结论是刷新吞吐提高且画面自动回归通过，主观延迟改善等待用户观察。
