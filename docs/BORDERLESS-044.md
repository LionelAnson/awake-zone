# 0.4.4：置顶时的屏幕黄色边框

2026-09-30，本机 Windows x64；其他平台未运行验证。

## 原因与修改

只有置顶/独立浮窗启用实时显示器采集；桌面模式停止该会话并使用壁纸取样，因此边框仅在置顶时出现与当前实现一致。0.4.3 的会话没有请求无边框许可，也没有设置 IsBorderRequired=false。未查明用户所述更早版本为何没有观察到边框，不能把该观察简单判为错误。

按[微软 IsBorderRequired 文档](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.graphicscapturesession.isborderrequired)及 [Win32CaptureSample](https://github.com/robmikh/Win32CaptureSample/blob/main/Win32CaptureSample/App.cpp) 的接口路径，实现异步 `GraphicsCaptureAccess.RequestAccessAsync(Borderless)`。只有返回 Allowed 才设置本时钟会话 `IsBorderRequired(false)`；拒绝/接口失败保留原系统提示。没有修改系统隐私注册表、自动点击授权对话框或切换到绕过授权的采集方式。

申请过程不阻塞窗口线程等待用户；结果到达后由绘制线程应用，每次隐藏恢复、桌面往返、显示器切换后创建的新会话也应用该设置。每进程只申请一次，拒绝后不会反复弹出申请。新增三个诊断字段：`borderlessAccess`（0..4 对应 WinRT 枚举，-2 未申请/-1 等待/-3 接口异常）、`borderRequired`（-1 无会话，0/1 为读回值）、`borderError`（HRESULT）。

## 实际验证

- TypeScript/Vite 和 Windows 发布构建通过，jobs=1，未升级依赖。
- 本机实际授权结果 Allowed=4；IsBorderRequired 读回 0，边框错误 0，采集帧持续增加。
- 旧安装版置顶：屏幕最外沿第 0、1 层各 146/146 个采样点呈黄色，连续三次一致；旧版返回桌面：0/146。
- 新版置顶、往返/隐藏恢复后以及关闭调试端口后的正常冷启动：同样位置均为 0/146，连续三次一致。这里使用 GDI 采样屏幕外沿，保留的仅是颜色计数，没有保存或上传真实应用截图。单独的 GDI 阴性结果不能代替物理屏幕观察，因此另外取得用户确认。
- 用户明确确认屏幕黄框“已经消失”。这是本轮物理视觉确认，不是把 API 成功当作视觉成功。
- 安装版交互回归通过：Win+Alt+X、长按一次、两组各 20 次连按、隐藏暂停/唤出、最小化恢复、锚点恢复、设置和两位小数。隐藏后恢复的会话仍读回 borderRequired=0。详细事件计数以最终索引为准。
- 最新作息、自启、桌面锚点保留，没有覆盖用户这期间调整的位置。最后正常重启前后状态哈希一致，9226 调试连接关闭。18% 浅灰遮罩和原模糊算法没有改变。

## 范围与交付

系统仍可为其他应用的采集会话显示边框。本轮未测试拒绝授权、组织策略、其他 Windows 版本、实际登录/休眠、物理拔屏及 macOS；代码包含拒绝/错误保留边框的分支，不把这些分支写成运行验证通过。没有重跑未修改的完整时间计算或全套磨砂图案质量测试。

证据：`output/capture-border-044/final-verification.json`、各 edge 计数文件、`status.json`、`after-roundtrips-status.json`、`final-running.json`。交互回归：`output/hotkey-reliability/installed-check-044/result.json`。构建快照：`output/live-capture-release/2026-09-30T14-29-17-327Z-build-tauri`，含命令、配置、源码和 EXE 哈希。安装 EXE 与原始 EXE 只有 NSIS 标识三字节差异。

安装包：`release/Personal Day_0.4.4_x64-setup.exe`；源码：`release/Personal-Day-0.4.4-source.zip`；旧安装 EXE 备份：`release/rollback-044/personal-day-0.4.3.exe`。
