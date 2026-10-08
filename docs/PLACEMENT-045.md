# 0.4.5 拖动后跳到中央修正（2026-10-01）

## 原因与改动

旧 `ensure_visible` 每两秒要求整个外框位于同一显示器工作区内；不满足就放到主屏中央。外框包含不可见边框，屏幕边缘、任务栏附近及跨屏摆放均容易触发。调整尺寸、显示窗口也调用该逻辑。

新 `placement::recover` 保留顶部可操作区域仍在屏幕上的位置。不可操作时，以最小位移移回最近工作区边缘，不再自动居中。锁定桌面时仍按原来的右下角锚点恢复；拖动中、最小化时仍跳过修正。未改快捷键、作息、浅灰遮罩、采集算法或自启配置。

## 本轮验证

- `npm test`：50 项通过。
- `cargo test --manifest-path src-tauri/Cargo.toml --lib -j 1`：7 项通过，包含新增 5 项位置测试。首次运行因 Windows 提交内存不足（1455）失败；可用内存恢复后重跑通过，未关闭用户程序或更改页面文件。
- 旧隔离 0.4.1 的居中函数与本轮修改前 0.4.4 相同。旧窗口右侧越界 12px，从 (1584,200) 被移到 (786,398)；底部越界同样居中。
- 新隔离 0.4.5：右侧 (1584,200)、底部 (400,809) 等待 4.5 秒保持原位；完全移出屏幕 (2320,200) 后回到右边缘 (1572,200)。
- 真实鼠标按下、移动、释放后，从 (500,300) 拖到 (580,340)，4.5 秒后位置相同。首次严格鼠标位移断言得到 (571,337)，但释放后的坐标没有变化；脚本改为验证确有移动且释放后稳定，重新执行通过。保留失败记录。
- 隔离原生回归通过：快捷键往返、浮窗移动后恢复原桌面锚点、隐藏唤出、Win+D、设置、关闭隐藏、四图标布局、20 次快速及 20 次保持修饰键切换、最小化恢复。
- 多屏、负坐标、混合缩放、显示器移除由纯计算测试覆盖，未实际拔插外接显示器；macOS 未运行验证。

## 黄框反馈

测试期间用户再次报告屏幕黄框。旧隔离探针会带采集边框，但退出后用户仍报告存在，因此不能把原因仅归于探针。屏幕边缘 GDI 计数为零与用户观察不一致，不能据此认定视觉通过。重启安装版 0.4.4 后读取到 borderlessAccess=4、borderRequired=0、borderError=0，用户随后确认“已消失”。复现原因未确定，没有更改系统采集许可或关闭其他程序。本次保留 0.4.4 的无边框许可处理。

## 命令与证据

```powershell
npm test
& "$env:USERPROFILE/.cargo/bin/cargo.exe" test --manifest-path src-tauri/Cargo.toml --lib -j 1
node scripts/build-hotkey-probe.mjs
node scripts/placement-native-check.mjs
node scripts/hotkey-toggle-check.mjs
node scripts/build-live-release.mjs
node scripts/installed-live-smoke.mjs
```

所有构建 jobs=1，隔离标识 com.personalday.probe.hotkey；独立状态目录、Ctrl+Shift+Alt+Z 实验快捷键。实际产品保持 Win+Alt+X。

- `output/placement-045/baseline-1790822663456/result.json`：旧版复现。
- `output/placement-045/fixed-1790823005888/result.json`：新版位置及鼠标拖动。
- `output/hotkey-toggle/2026-10-01T02-50-54-879Z-native/result.json`：隔离回归。
- `output/placement-045/restarted-044-glass.json`：黄框反馈后的重启状态。
- 构建清单记录源码快照、EXE 哈希、执行命令及工具链配置。

## 安装

已构建并安装 0.4.5；安装前即时保存并核对当前状态，自启值和状态文件在安装过程中未变。安装证据在 `output/placement-045/install/`，旧 EXE 保留于 `release/rollback-045/`。

安装版 `output/hotkey-reliability/installed-check-045/result.json` 11 项检查通过。最终运行时 capture error=0、borderlessAccess=4、borderRequired=0、borderError=0。结束后关闭调试端口并正常重启；`final-running.json` 记录状态文件未变、自启未变及唯一安装版进程。用户期间移动的最新位置 (935,203) 已保留，没有用测试前的位置覆盖。

安装版 SHA256：`24c48b7c76bffafb9fe1e13e75fbbb012d145cbf2b2ed1afbfa0cfcc19ccde38`。
原始构建 EXE SHA256：`c203bf247605468af7696ebad5f01678668c1a0d7091ec9889366c4f720cd3d7`（NSIS 打包会写入 bundle 类型，故两者不同）。
