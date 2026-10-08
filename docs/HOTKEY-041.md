# 0.4.1 快捷键修正

日期：2026-09-30；本机 Windows x64。其他平台未运行验证。

## 变更

- 经用户确认，快捷键从 Ctrl+Shift+Win+Z 改为 **Ctrl+Alt+Z**。旧组合不再注册，不需要 Fn。设置页和托盘说明同步更新。
- Windows `RegisterHotKey` 改在独立线程接收 `WM_HOTKEY`，使用 `MOD_NOREPEAT`。该线程只把操作交给 UI 队列；磨砂重建和桌面切换按收到的顺序执行，不在监听线程中等待。
- 每两秒的位置检查也交给同一 UI 队列，避免后台线程持有设置锁时等待 UI、UI 同时等待设置锁的风险。
- 隐藏或最小化的窗口优先恢复为置顶小窗。最小化时不记录窗口位置、不做屏幕边界修正；恢复显示后再检查可见范围。
- 新增 `shortcut_status`：注册状态、接收数、完成数、错误数，以及最近 64 次该快捷键的等待时间、执行时间和前台 HWND。仅保存在进程内存，不监听/记录其他按键或应用内容。退出时结束监听线程并注销快捷键。
- 实时磨砂绘制、浅灰层、作息算法、自启配置与桌面锚点格式未改动。没有升级依赖或安装额外环境。

## 发现与证据边界

旧版本隔离测试的 50 次输入全部引起状态切换，未稳定复现用户所述“多次按也无反应”。不能断言已证明原报告只有一个原因。

源码确认旧热键回调同步等待磨砂生命周期操作；旧后台位置记录在某些路径上持有设置锁并调用需要 UI 线程的窗口查询。本轮移除了这些交叉等待路径，但不把源码风险写成已经复现的死锁。

新回归实际发现：最小化窗口的系统临时坐标 `(-32000, -32000)` 被保存到本地状态。已修改为跳过最小化坐标；测试会保持最小化超过两秒，再检查唤出和保存位置。

早期几轮测试分别在前台保持、物理鼠标拖动及往返断言处中止，结果保留。后来改为短按和通过 Win32 移动测试窗口，减少与用户操作的冲突；最终移动回归不能冒充最终版人工拖动验收。前台变化单独记录，不把无关前台变化判成时钟主动抢焦点。

## 执行入口

```powershell
npm test
cargo test --manifest-path src-tauri/Cargo.toml --lib -j 1
node scripts/build-hotkey-probe.mjs
node scripts/hotkey-toggle-check.mjs
node scripts/build-live-release.mjs
# 安装后临时启用本地调试连接，再运行；完成后关闭调试连接重启。
node scripts/installed-live-smoke.mjs
```

隔离构建沿用独立 identifier、设置目录、源码快照、哈希及构建缓存，jobs=1。隔离测试用 Ctrl+Shift+Alt+Z；安装版另测真实 Ctrl+Alt+Z。

原生交互检查覆盖重复往返、移动后恢复锚点、隐藏唤出、返回按钮、Win+D、设置、关闭隐藏、两档大小，以及两组各 20 次的短按/保持修饰键连按。通过接收计数与完成计数核对输入，不只检查最终奇偶状态。

这轮不重新声称实时磨砂画质、人手键盘、真实休眠/锁屏、实际登录、物理拔屏、远程控制环境或托盘鼠标点击已经验收。采集帧计数恢复和 API 成功不等于重新完成原生磨砂视觉验收。

## 最终验证与交付

- `npm test`：50 项通过；最后一轮原生 `cargo test --lib -j 1`：2 项通过。
- 最终隔离原生回归：18 项记录完成，51 次接收 / 51 次完成 / 0 次错误；包括两组各 20 次连按、保持最小化超过两秒后恢复。设置文件没有被最小化临时坐标覆盖。真实安装的设置/自启/EXE 在隔离回归前后一致，探针正常退出，没有强制清理。
- 正式安装版：真实 Ctrl+Alt+Z 共 46 次接收 / 46 次完成 / 0 次错误。按住 1500 ms 只切换一次；关闭隐藏、隐藏浮窗唤出、最小化恢复、设置页新提示、原锚点恢复均通过。两组连按各 20 次全部完成。这里是自动注入 Windows 输入，不是人工键盘验收。
- 新快捷键注册前检查未占用。最终正常启动后，新组合占用、旧 Ctrl+Shift+Win+Z 可注册；设置页和托盘显示新组合。没有更换作息、位置、锚点或真实自启值。
- 更新及移除调试连接后的重启，设置 SHA-256 均为 `C9A3FFCB4AE17DCF22296F661EF540D33175BB25BEF3BC43C5A70E36FC25C71B`。位置 (1450,831)，锚点右 97.6 / 下 2.4；没有恢复此前的旧位置。真实自启仍为 `"E:\应用\Personal Day\personal-day.exe" --autostart`。
- 仅保留安装版 0.4.1 正常运行，测试调试端口 9226 已关闭。

证据目录：

- 隔离构建：`output/hotkey-toggle/2026-09-30T12-06-20-542Z-build-tauri`，EXE SHA-256 `c6b79b4365a7fb37adbcfe37469fac1d470898e8c1fd7096f83661ef0bb47a1b`。
- 最终隔离回归：`output/hotkey-toggle/2026-09-30T12-09-22-672Z-native/result.json`。
- 正式构建：`output/live-capture-release/2026-09-30T12-09-44-386Z-build-tauri`，原始 EXE SHA-256 `d97e089049e5ccd52abe818153764d272073adef5df035dba8b82cd01b768ca8`。
- 安装 EXE SHA-256 `eada8b8ec40694a7d117072a2d786e1901b8edf0d718dfe8f61fefcb33581b7c`；与原始 EXE 仅 NSIS 标识 `UNK → NSS` 的 3 字节不同。
- 正式版回归：`output/hotkey-reliability/installed-check-041/result.json`；更新前后记录及最终运行状态在同级 `install-041`、`final-running.json`。
- 安装包：`release/Personal Day_0.4.1_x64-setup.exe`；源码：`release/Personal-Day-0.4.1-source.zip`；旧 EXE 保留于 `release/rollback-041/personal-day-0.4.0.exe`，未回写旧设置。

