# 回声英语 v1.3.1

## 修复内容

v1.3.0调用`ContextCompat.startForegroundService()`后，创建的MediaSession没有注册给MediaSessionService，导致默认媒体通知未建立，系统在约30秒后抛出`ForegroundServiceDidNotStartInTimeException`并销毁应用。

v1.3.1完成以下修复：

- 创建MediaSession后显式调用`addSession(session)`。
- MediaSessionService能够监听播放器状态并自动建立MediaStyle前台通知。
- 增加`onUpdateNotification()`诊断日志，记录系统是否要求前台提升。
- 保留`ContextCompat.startForegroundService()`、`C.WAKE_MODE_LOCAL`、媒体播放服务类型和WakeLock权限。
- 保留播放列表、字幕、分段复读、总进度、定时关闭和后台播放等功能。

## 验证结果

- 版本：1.3.1，versionCode 5
- 包名：com.echoenglish.app
- 自动测试：21项全部通过
- APK v2签名有效
- 已覆盖安装到Xiaomi M2102K1C（Android API 34），应用数据保留
- 前台持续播放超过2分钟，跨过旧版约30秒崩溃阈值
- 服务状态：`isForeground=true`
- 前台媒体通知：ID 1001，类型`mediaPlayback`
- 真机锁屏并进入Dozing状态后连续观察100秒
- 锁屏期间PID保持29656，WakeLock和媒体通知持续存在
- 锁屏测试期间崩溃、前台服务超时和服务销毁计数均为0

## 仍建议继续观察

本轮已完成约100秒真实锁屏测试，但尚未完成15分钟和30分钟长时间测试。建议用户继续播放观察，若再发生中断可执行：

```text
adb logcat -d -s EchoPlayback
```

这是Debug测试版本，正式发布前需要使用独立Release证书签名。