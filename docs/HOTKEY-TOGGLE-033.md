# 0.3.3 快捷键与隐藏返回按钮

日期：2026-09-30。已生成独立安装包，**没有覆盖 E:\应用\Personal Day 中保留运行的 0.3.1**。

## 最终行为

- 正常 Windows 构建使用 **Ctrl+Shift+Win+Z**，按一次成为置顶浮窗，并解锁拖动；再按回到保存的桌面位置并锁定。Fn 不是 Windows 可注册的修饰键。
- 浮窗拖动只保存浮窗位置，不更新桌面右边／底边的锚点。桌面解锁后移动才更新锚点。
- 浮窗顶栏新增“回到桌面并固定位置”按钮：平时隐藏，鼠标移入组件后显示，点击即返回；桌面状态完全隐藏。键盘聚焦时也可见。
- 返回按钮执行独立、单向的 return_to_desktop 命令，重复调用也不会变为置顶。
- 托盘新增“置顶 / 回到桌面”入口，与快捷键共用操作。
- 隐藏的组件可通过快捷键唤出。原生显示采用不激活窗口的方式，键盘切换时保留后方应用的输入焦点。
- 普通构建采用现有壁纸取样外观。失败的原生实时磨砂仅在显式 experimental-backdrop feature 下启用；backdrop-diagnostics 仍包含它。**实时磨砂没有修复**，0.3.3 不能宣称透过浮窗看见后方应用。

## 实现范围

主要变更在 `src-tauri/src/lib.rs`、`desktop_layer.rs`、`backdrop.rs`、`src/main.ts`、`src/style.css` 和 `index.html`。保留先注册 LocalState 的修正。

保留当前已保存的桌面锚点，没有按旧备份改用户的位置。如果旧版曾把浮窗位置写成锚点，新版无法凭空恢复更早的桌面位置；可返回桌面后解锁调整一次。后续浮窗移动不再覆盖它。

没有新增开发依赖或升级锁定的第三方库。npm 和 Cargo 锁文件仅更新项目自身版本至 0.3.3。Windows C++/WebView2、Node、Rust 等本机依赖已具备，jobs=1。

## 本轮实测

有效原生检查：

`output/hotkey-toggle/2026-09-30T06-46-18-723Z-native/result.json`

| 检查 | 实际结果 |
| --- | --- |
| 计算与前端辅助自动测试 | 50 项通过 |
| Rust 自动测试 | 2 项通过；另保存最终源码重新运行的日志 |
| TypeScript/Vite 生产构建 | 通过 |
| Windows x64 普通版本与 NSIS 构建 | 通过 |
| 原生窗口自动检查 | 14 组通过 |
| 长按快捷键 1.5 秒 | 只切换一次 |
| 后方图案窗保持前台时切换 | 后方 HWND 保持前台 |
| 浮窗实际鼠标拖动 | 从 (1542,804) 移至 (1242,624)，桌面锚点仍为 right=24/bottom=24 |
| 再按返回 | 恢复 (1542,804)，WS_CHILD=true、topmost=false、positionLocked=true |
| 再次往返 | 两轮恢复相同桌面位置 |
| 隐藏后快捷键唤出 | 恢复可见置顶浮窗 |
| 隐藏按钮悬停、键盘聚焦、点击 | 正常；返回后按钮消失，位置恢复并锁定 |
| 返回命令重复调用 | 保持桌面，不反向置顶 |
| Win+D | 桌面组件仍可见并可命中 |
| 设置窗口 | 打开、正确作息值、关闭隐藏正常 |
| 原生关闭窗口 | 隐藏；切换命令可重新显示 |
| 壁纸与百分比 | 普通浮窗保持壁纸，百分比为两位小数 |
| 4 图标位尺寸 | 无横向溢出；返回按钮可点击并固定到桌面 |

### 验证边界

安装版一直占用 Ctrl+Shift+Win+Z。为保留安装版运行，隔离探针使用 **Ctrl+Shift+Alt+Z** 注册并实际发送按键，执行相同的原生切换处理器；它使用独立 identifier 和设置目录，禁止真实自启写入。普通构建仍注册 Ctrl+Shift+Win+Z。本轮没有把替代组合测试写成“已在安装版替换后实测正式组合”。

设置按钮和返回按钮通过 Playwright 连接真实 WebView2 操作，再以 Win32 核对窗口父级、置顶、可见性及坐标；不是只看浏览器模拟图。两张按钮截图仅展示界面，不用来证明实时磨砂。

托盘新菜单的鼠标实际点击、安装器实际安装、自启登录／重启、物理拔屏和 macOS 未在本轮验证。没有复用 0.3.1 的旧测试作为这些行为的通过证据。

中间有两次检查未完成：一次直接 hover 尚不接收鼠标的隐藏按钮，后改为先移入组件；一次 CDP 初始化时选中了设置页面，后改为核对文档角色再连接主界面。失败记录保留；表中的通过结果来自最后完整运行。

## 隔离、收尾与复现

最终原生轮次前后真实 state.json、自启值和安装 EXE 的哈希均相同，productionUnchanged=true。各次检查都正常结束探针；安装版原 PID 24048 仍运行。本轮期间用户更新过真实位置，未用备份覆盖这些更新。

```powershell
npm test
npm run build
$env:Path = "$env:USERPROFILE\.cargo\bin;$env:Path"
cargo test --manifest-path src-tauri/Cargo.toml --lib --release -j 1

# 独立 identifier / 设置目录，替代测试组合，不修改安装版
node scripts/build-hotkey-probe.mjs
node scripts/hotkey-toggle-check.mjs

# 正常产品构建；默认不启用实验磨砂
npm run desktop:build
```

构建时的完整配置、源码快照和哈希记录在 output/hotkey-toggle 中。安装包采用普通构建，未启用 window-diagnostics 或 experimental-backdrop。

## 交付

- 安装包：`release/Personal Day_0.3.3_x64-setup.exe`。
- 独立 EXE：`release/personal-day-0.3.3.exe`。
- 完整源码：`release/Personal-Day-0.3.3-source.zip`。
- 安装包 SHA-256：`fdd5cf147d228fb36fe10ce8b1b147f24da3146f12334b39d722260da6a44b57`。
- 普通 EXE SHA-256：`70aa846baf3d8ae4ddd32a9bb9d3de0ae6d24e3fcc2ee55e1a09d4b0ca4e10ae`。

当前正在运行的仍是 0.3.1；需要安装上述新版后，现有时钟才会出现返回按钮。
