# 0.1.10 刷新恢复改进

用户报告：桌面时间偶尔长时间停留，进入应用立即恢复。没有连接手机，未确认根因。

改动：整秒对齐调度；绘制中停止后不再添加旧回调；解锁状态延迟时250ms重试，最多8次，亮屏额外250ms复查；服务存活期间由系统TIME_TICK重建循环。熄屏仍取消刷新，不增加唤醒锁或精确闹钟。

诊断记录：成功提交RemoteViews的时间，每分钟最多持久更新一次；设置页恢复前保留服务状态及提交时间。它不是桌面实际显示成功的证据，也不是心跳。休息时内容不变可能长期没有提交。

验证：{'core': {'tests': 27, 'failures': 0, 'errors': 0, 'skipped': 0}, 'app': {'tests': 36, 'failures': 0, 'errors': 0, 'skipped': 0}}。lint：{'Error': 0, 'Warning': 23}。构建成功。
新增测试覆盖解锁状态延迟与有界重试、熄屏取消、绘制耗时后的整秒对齐、延迟后读取当前时间、绘制期间停止、打开应用前记录保留。
本轮未运行原生模拟器或小米真机验证。服务被终止、进程冻结或桌面未应用RemoteViews是否发生仍未确定；不承诺本版已经解决手机上的停更。

构建目录：artifacts/20261002-000731-102
命令：`./scripts/build.ps1 -Tasks ':core:test',':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug'`
APK SHA-256：`6c4503be0bf6e0cda93355eb90ceb6ce5e8a9ac1c7f184a7c8065ad6a500ba79`

下次停更后打开应用，复制“添加帮助 / 诊断信息”，结合 Refresh before opening 和实际停更时长继续定位。外观、作息和24小时制不变。
