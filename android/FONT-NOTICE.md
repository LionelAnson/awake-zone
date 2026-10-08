# 字体来源与本轮用途

本次按用户要求使用本机 `C:\Windows\Fonts\STZHONGS.TTF`，完整复制为 `app/src/main/res/font/stzhongs.ttf`，没有改写或裁剪字形。

- 字族 / PostScript 名称：STZhongsong（华文中宋）
- 款式：Regular；版本：1.02
- 原文件版权字段：Copyright (c) 1991-1998, Changzhou SinoType Technology Co., Ltd. All rights reserved.
- SHA-256：`E822A3F2859A6CC982F04C6AD1EABC18371CE26D6874B710188A30C35DCED0F1`
- 大小：12,135,284 字节；OS/2 fsType=8。此元数据不代表已取得应用公开分发授权。

此交付为用户本机字体制作的个人测试 APK。字体保留原版权，不纳入项目代码的许可；公开分发或上架前另行核对字体授权。

0.1.4 的设置页、预览和进度刻度均使用这套字体。0.1.5 的应用设置页继续使用华文中宋，桌面图形及其预览暂用系统细体；小米“数字时钟”同款字体尚未核对，见 `LAYOUT-015.md`。本轮没有打包网络下载的小米字体。

实际模拟器曾发现 Launcher3 忽略 RemoteViews TextView 的字体资源，因此组件在应用进程内绘图，再通过 RemoteViews 图片提交；整体保留可读的无障碍说明和点击设置。此绘制只生成组件自身内容，不采集屏幕或壁纸。

实现依据：[Android 字体资源文档](https://developer.android.com/develop/ui/views/text-and-emoji/fonts-in-xml)。

## 0.1.6 已通过的排版试配

设置页继续用华文中宋。时钟图形使用以下明确字体资源，不依赖手机系统字体回退：

| 文件 | 来源与版本 | 本轮字重 |
|---|---|---|
| `res/font/clock_roboto.ttf` | API 36 AOSP 模拟器 `/system/fonts/Roboto-Regular.ttf`，Roboto 3.005; 2022，完整可变字体 | 时间 wght=150；日期/百分比 wght=100；wdth=100、ital=0 |
| `res/font/clock_yahei_light.ttf` | 本机 `C:\Windows\Fonts\msyhl.ttc`，索引0，Microsoft YaHei Light 6.25；用 fontTools 提取完整字体面 | 静态真实字重290，不宣称Thin |

Roboto 字体元数据声明 Apache License 2.0。微软雅黑保留原字体版权字段、字形和嵌入标志；字体面提取时未裁剪字形、未重命名字族。绘制阶段的纵向变换及 alpha 收缩不改写字体文件。

```text
clock_roboto.ttf       9CA9DEBB09459BF4E3E7F826F5CD0F35F253902B85684921FCE2BA3F28DD0F50
clock_yahei_light.ttf  FF99C091C5CA8DC8DC44C8B1D93CDDEBF4325BBCB2535AC995C920CB5E32D773
msyhl.ttc 原始来源    7E9BDF90BB5D3FE1B5975FC8AE31944B8FA674122261F92C28D4EC0B9C482FA1
```

原生渲染会对所需字符的实际 Font 缓冲区哈希、TTC 索引和字重做核验。宿主 LEGACY 模拟测试不验证实际字体或像素，结果单列；API 36 原生检查见 `TYPOGRAPHY-016.md`。

此版本沿用个人侧载测试范围，没有将微软雅黑授权为项目开源字体。样张已获用户确认，但 Roboto 的 1/9 和雅黑“周、四、午”仍与小米参考有字形差异；未取得小米时钟原字体资源。

## 0.1.7 放大版

字体文件及哈希保持不变。采用用户确认的放大样张：时间 Roboto wght=190，日期及百分比 wght=130；微软雅黑 Light 仍为290，通过减少绘制阶段的收缩量略加粗。原生字体核验与实际渲染对照见 `TYPOGRAPHY-017.md`。
