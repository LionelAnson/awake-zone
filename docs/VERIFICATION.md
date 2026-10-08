# 验证记录

日期：2026-09-29。环境：Windows 11 家庭中文版 10.0.26200，x64。

## 已实际执行

| 检查 | 结果 | 范围 |
|---|---|---|
| `npm test` | 27 项通过 | 19 项基本/边界测试、7 项纽约测试、1 项 Lord Howe 半小时 DST 测试 |
| `npm run build` | 通过 | TypeScript 类型检查、Vite 生产构建 |
| `npm audit` | 0 个已报告漏洞 | 本次安装的锁定 npm 依赖 |
| Playwright + Chrome 浏览器检查 | 通过 | 无效作息提示、保存/刷新恢复、深色主题、跨午夜不归零、入睡冻结、次日起床归零、99.9% 边界、320×224 无溢出 |
| `cargo fmt --check` | 通过 | Rust 源码格式 |
| `cargo test --lib --release` | 1 项通过 | Rust 侧设置校验，含无效时刻和相同时刻 |

计算测试在启动 Node 进程前设置 TZ，避免 Windows 运行中切换 TZ 的差异；产品默认始终使用 `Asia/Shanghai`。纽约进程中也单独验证了默认北京时间不受系统时区影响。

浏览器检查使用页面时钟模拟，不修改系统时间。相关脚本：`scripts/browser-checks.js`。截图：`output/playwright/light.png`、`dark.png`、`rest.png`。首次开发预览存在 favicon 404，补充图标链接后新会话控制台错误为 0。

## 原生构建和运行

首次默认并行 Rust 编译因内存分配失败而中断；改用项目级 `jobs = 1` 重试。首次发布版成功编译并实际运行，初轮原生检查通过设置保存、置顶、关闭隐藏、单实例、位置保存和恢复。

原生截图检查发现本机 WebView 文字缩放叠加屏幕 DPI，导致初版窗口裁切；已增加按实际缩放比例调整尺寸，并加入原生无溢出断言。探测脚本自身也已声明 DPI awareness，避免把 Win32 虚拟坐标与应用物理坐标混用。

NSIS 的 GitHub release 下载曾超时；通过 GitHub 官方 API 下载并按 [Tauri 官方源码](https://github.com/tauri-apps/tauri/blob/dev/crates/tauri-bundler/src/bundle/windows/nsis/mod.rs) 核对 SHA-1 后放入本机工具缓存：

- `nsis-3.11.zip`：`EF7FF767E5CBD9EDD22ADD3A32C9B8F4500BB10D`
- `nsis_tauri_utils.dll`：`75197FEE3C6A814FE035788D1C34EAD39349B860`

### 最终结果

**Windows x64 构建、启动与下列自动检查已验证。** 最终 `npm run desktop:build` 成功，生成 1.37 MiB 的 NSIS 安装包；安装器本身尚未安装/卸载验收。

`npm run test:windows` 对最终发布版通过 9 项检查（原始记录：`output/playwright/windows-smoke.json`）：

1. 本机 DPI / 文字缩放下卡片无滚动条、无内容裁切。
2. 发布版启动并在 WebView2 中渲染。
3. 无效作息被拒绝；有效作息和主题写入真实本地文件。
4. 置顶开关实际改变 Win32 `WS_EX_TOPMOST`，开/关均验证。
5. 原生位置改变后保存物理坐标。
6. 发送系统 `WM_CLOSE` 后窗口隐藏，进程仍存活。
7. 再次启动程序显示已有窗口，重复进程退出。
8. 将窗口移至 `(30000, 30000)` 后自动回到可见屏幕。
9. 重启后恢复作息、主题和修复后的位置。

检查使用真实发布版，未模拟 Tauri IPC。测试结束已恢复默认作息、浅色和关闭置顶，测试进程已结束。最终原生截图为 `output/playwright/windows-native.png`；`native-inspect.png` 是修复前的诊断截图，不代表最终效果。

产物 SHA-256：

```text
personal-day.exe
132D5F7A0675BC59ED1398D3A97CEA6A65508ACDBCE871500FEE1B60E9AD845D

Personal Day_0.1.0_x64-setup.exe
0AEA8562E7476E316176E524A709C89E369B662B42FA0C4AA848884603E7F52E
```

## 仍需人工验收

以下项目不能由单元测试或浏览器检查代替：

- Windows 托盘图标的实际点击：显示/隐藏、设置、退出；任务栏折叠托盘区域中的可达性。
- 鼠标拖动顶栏的实际手感；多屏不同缩放比例下的位置和尺寸。
- 在外接显示器上放置窗口后，**真实拔除显示器**。模拟屏幕外坐标恢复不能替代物理拔插。
- **真实休眠和唤醒**，以及手动调整操作系统时间；页面时钟跳变测试仅覆盖计算与重绘。
- NSIS 安装、卸载和不同 Windows 用户权限下的数据目录行为。
- macOS 的构建、运行、菜单栏、关闭隐藏、位置恢复、置顶和签名/公证。**macOS 未验证。**

建议的人工时间验收：在测试环境控制系统时间，依次观察北京时间 07:59 → 08:00、23:59 → 次日 00:00，以及作息 10:00—02:00 时的午夜。勿在日常工作机器上随意修改系统时间。
