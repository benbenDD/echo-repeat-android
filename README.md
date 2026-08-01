# 回声英语 v1.3.0

## 本次修复：锁屏后台播放

代码排查发现，v1.2.0虽然已经使用MediaSessionService并声明了大部分前台服务权限，但仍存在两个关键缺口：ExoPlayer没有启用熄屏播放WakeMode，首次加载音频仍通过普通startService启动。

v1.3.0完成以下修改：

- ExoPlayer启用`C.WAKE_MODE_LOCAL`，播放和缓冲时由Media3按需持有CPU WakeLock。
- 首次加载音频使用`ContextCompat.startForegroundService`启动媒体服务。
- Manifest确认声明`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`WAKE_LOCK`和`POST_NOTIFICATIONS`。
- PlaybackService声明`android:foregroundServiceType="mediaPlayback"`。
- 增加MediaSessionService和平台MediaBrowserService入口。
- 最近任务被划走时，只要播放器正在播放或仍有已加载媒体，就不主动停止服务。
- MediaItem增加稳定Media ID以及标题、应用名称等元数据，供系统媒体面板和锁屏控制读取。
- 增加低频生命周期、播放状态和错误日志，日志标签为`EchoPlayback`。
- 播放错误会同步到应用界面，通过Snackbar提示具体错误代码。
- 保留导入时的持久URI读取授权。

## 验证结果

- 版本：1.3.0，versionCode 4
- 包名：com.echoenglish.app
- 最低Android版本：Android 8.0（API 26）
- 自动测试：21项，全部通过
- Kotlin编译及Debug APK构建通过
- 合并Manifest中的前台媒体服务、WakeLock及通知声明已核验
- APK Signature Scheme v2签名

## 需要真机确认

当前开发环境不能代替真实手机锁屏，因此尚未声称完成15分钟或30分钟真机锁屏测试。安装后建议依次测试：

1. 播放至少30分钟的本地MP3，锁屏15分钟。
2. 再锁屏连续播放30分钟。
3. 分别测试重复1次、3次、0.75x和1.5x。
4. 锁屏期间检查通知栏或锁屏媒体控制是否持续存在。
5. 检查定时关闭、耳机拔出和蓝牙断开。

如果仍然中断，可连接ADB执行：

```text
adb logcat -s EchoPlayback
adb shell dumpsys activity services com.echoenglish.app
adb shell dumpsys media_session
```

这是Debug测试版本，正式发布前需要使用独立Release证书签名。