# 醒时区 iOS 桌面小组件

这是一个原生 WidgetKit 实现，和 Windows/Android 版使用相同的核心规则：默认北京时间（`Asia/Shanghai`），默认 08:00 起床、次日 00:00 入睡；用户在“醒时区”主应用中修改计划作息后，组件从共享设置重新计算个人时间。

## 当前交付内容

- `PersonalDay/Shared/PersonalDayCore.swift`：独立时间计算，支持跨午夜、计划休息时段、24 小时制、两位小数进度和注入当前时间。
- `PersonalDay/Shared/SharedSettings.swift`：App Group 共享作息设置。
- `PersonalDay/App/`：原生 SwiftUI 设置页，可分别修改预期起床和入睡时间。
- `PersonalDay/Widget/`：WidgetKit 小组件，支持小号、中号和大号系统组件尺寸。
- `project.yml`：XcodeGen 项目描述，便于在 macOS 上生成 Xcode 工程。
- `Tests/PersonalDayCoreTests.swift`：核心时间计算测试。

## 在 macOS 上生成并运行

本工作区运行环境是 Windows，当前没有 `xcodebuild`、Swift Apple SDK 或 iOS 模拟器，因此没有把 iOS 构建或真机显示标记为已验证。需要在 macOS 上执行：

1. 安装 Xcode 15 或更高版本，并安装 XcodeGen（`brew install xcodegen`）。
2. 在本目录运行 `xcodegen generate`，打开生成的 `PersonalDay.xcodeproj`。
3. 为 `PersonalDay` 和 `PersonalDayWidget` 两个 target 选择同一个 Apple 开发团队，并在 Signing & Capabilities 中启用 App Groups：
   `group.com.personalday.widget`
4. 确认主应用和小组件的 Bundle Identifier 与 `project.yml` 一致，选择 iOS 16 或更新的 iPhone 模拟器/真机运行。
5. 在主应用设置作息，然后把“醒时区”小组件添加到主屏幕。点击组件会打开 `personalday://settings`。

也可以在 macOS 上将 `Tests/PersonalDayCoreTests.swift` 加入一个 XCTest target，先单独验证时间计算，再运行 WidgetKit 预览和真机验收。

## 更新限制

WidgetKit 的刷新由 iOS 系统调度，代码按分钟生成时间线，但系统可能延迟刷新；不能承诺像前台 Android 服务一样每秒更新。组件不会读取屏幕内容、定位或睡眠数据。iOS 小组件的背景由系统容器管理，不能强制实现 Windows 版那种实时背景磨砂。

## 验证边界

本轮仅完成源码和 XcodeGen 配置。由于没有 macOS/Xcode，iOS 构建、签名、WidgetKit 时间线、组件添加和 iPhone 显示均为“未验证”。
