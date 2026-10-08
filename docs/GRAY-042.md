# 0.4.2 浅灰磨砂遮罩

2026-09-30，Windows x64。

- 将组件背景上的 `#d8d8d8` 遮罩不透明度从 10% 增至 18%。只改 `src/style.css` 的背景遮罩和版本信息；文字、按钮、实时采集/模糊、快捷键逻辑未改。80 个前后端逻辑/原生文件与 0.4.1 发布快照逐字节一致。
- TypeScript、Vite 和 Windows 发布构建通过，jobs=1。已安装并恢复正常运行；安装后的实际样式读回为 `rgba(216, 216, 216, 0.18)`，遮罩不拦截点击，文字内容位于遮罩上层。快捷键注册成功，采集帧数推进且错误为 0。这些检查不等于重新完成原生画质人工验收。
- 保留更新当时的作息、自启、浮窗位置和桌面锚点，没有回写旧设置。关闭调试连接后重启，状态文件哈希一致。
- 本轮没有重跑未改动的计算测试、完整快捷键回归或跨平台验证。macOS 未运行验证。
- 证据：`output/gray-042/check.json`、`install/before.json`、`install/after.json`、`final-running.json`、`unchanged-code.json`；完整构建记录见 `output/live-capture-release/2026-09-30T12-45-04-535Z-build-tauri`。
- 安装包：`release/Personal Day_0.4.2_x64-setup.exe`；源码：`release/Personal-Day-0.4.2-source.zip`；旧 EXE 备份于 `release/rollback-042`。
