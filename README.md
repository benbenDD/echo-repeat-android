# 回声英语 v1.5.0

回声英语是一款面向英语重复听读的 Android 应用，支持批量导入 MP3 与 SRT、按固定时长或字幕切分、分段重复、自动连播、倍速播放、总进度跳转、片段定位、分段静音间隔、定时关闭和锁屏后台播放。

## v1.5.0 更新

- 为 MediaSession 设置明确的应用入口，点击通知栏或锁屏媒体播放器可直接进入复读页。
- 系统通知栏、锁屏播放器和耳机上一条/下一条命令统一映射为上一段/下一段。
- 上一段采用 3 秒规则：当前段超过 3 秒时重播当前段，否则进入上一段；第一段不会越界。
- 使用唯一 PlayerMessage 精确片段边界、generation token 和唯一间隔任务，过期边界与延迟回调会被忽略。
- 重复、切段、拖动、倍速修改或设置变化时会取消旧调度，降低相邻片段交叉和重复范围不一致。
- 新增无间隔、0.5、1、2、3、5 秒分段间隔；设置可持久保存并立即应用。
- 间隔支持暂停、继续、上一段、下一段、拖动进度和睡眠定时器取消。
- 重复 1 次且无间隔时继续使用自然连续时间线，避免相邻分段产生不必要的停顿。
- 保留 v1.3.1 的 MediaSession 前台通知、WakeLock 和锁屏后台播放修复，以及 v1.4.0 的多巴胺界面。

## 构建与验证

- versionName：1.5.0
- versionCode：7
- minSdk：26
- targetSdk / compileSdk：34
- JDK：17
- 自动测试：37 项全部通过
- Debug APK：v2 签名验证通过

## 本地构建

配置 JDK 17 与 Android SDK 后，在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

这是 Debug 测试版本，正式发布前需要使用独立 Release 证书签名。
