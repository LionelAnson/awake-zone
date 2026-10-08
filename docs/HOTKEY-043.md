# 0.4.3 快捷键占用处理

2026-09-30；仅本机 Windows x64 已运行验证。

## 现场结论

发现一份安装版时钟进程。停止该进程后，三次尝试注册 Ctrl+Alt+Z 仍返回 Windows 错误 1409，因此不是该时钟进程自身的注册。没有确定占用程序的身份，也没有终止其他应用。此前 0.4.1 验证时该组合可用，不代表之后永远空闲。

用户提出四键组合 Win+Alt+Z+X；说明标准 RegisterHotKey 只接受修饰键加一个普通键后，用户明确选择 **Win+Alt+X**。修改前实际检查该组合可注册。本次没有加入键盘钩子或四键监听。

## 修改

- 正式组合改为 `MOD_WIN | MOD_ALT | MOD_NOREPEAT` 加 `X`，沿用独立监听线程与 UI 顺序执行。
- 设置页、托盘与操作脚本同步更新；清除重复的注册错误描述，1409 与其他错误分开说明。
- 隔离测试备用组合 Ctrl+Shift+Alt+Z 保留，正式应用不注册它。
- 浅灰遮罩仍为 18%；作息、自启及用户最新桌面锚点保留。没有复原之前的旧位置。

## 本轮验证

- TypeScript/Vite、Windows 发布构建通过，jobs=1；原生单元测试 2 项通过。
- 在实际安装目录运行 0.4.3：46 次 Win+Alt+X 自动输入全部接收、全部完成，0 次错误。覆盖长按 1500 ms 只切一次、两组各 20 次快速连按、隐藏后唤出、最小化恢复、回到保存的桌面锚点、设置页新提示及两位小数。
- 读取运行中遮罩样式为 `rgba(216, 216, 216, 0.18)`；重启前后状态文件哈希一致。测试调试端口 9226 已关闭，仅保留正式安装版正常运行。
- 本轮没有将自动输入当作人手键盘验收，也没有重新验证真实登录/休眠、物理拔屏、远程端、托盘鼠标点击、磨砂画质或 macOS。未复用旧版本结果作为新版本通过证据。

证据：`output/hotkey-043/final-verification.json`、`final-running.json`、`install/before.json`、`install/after.json`；真实按键记录：`output/hotkey-reliability/installed-check-043/result.json`。构建目录见验证索引，包含源码快照、命令、配置及 EXE 哈希。安装 EXE 与原始 EXE 仅 NSIS 的 UNK→NSS 三字节标记不同。

安装包：`release/Personal Day_0.4.3_x64-setup.exe`；源码：`release/Personal-Day-0.4.3-source.zip`；旧 EXE：`release/rollback-043/personal-day-0.4.2.exe`。
