# 0.4.6 醒时区同心双环图标

## 设计

- 外环表示“醒时区”时钟，内环表示现实时钟。
- 默认作息按 08:00 起床、次日 00:00 入睡绘制：清醒段为 16 小时，占 240°；睡眠段为 8 小时，占 120°。
- 图标本体不带方形底色，保留透明背景，浅色和深色桌面均可使用。
- 外环使用暖黄色表示清醒段，灰蓝色表示睡眠段；内环使用浅色时钟线和暖色中心点。

## 文件

- `assets/icon.svg`：图标源文件。
- `src-tauri/icons/`：由 Tauri CLI 生成的 Windows、macOS、Linux、移动端尺寸。
- `docs/icon-concentric-transparent.svg`：设计预览源文件。
- `docs/icon-concentric-transparent-preview.png`：浅色、深色和透明棋盘格预览。

本轮只更新图标和版本号到 0.4.6，没有修改作息、位置、自启、快捷键或磨砂实现。
