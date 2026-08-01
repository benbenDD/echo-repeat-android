# 回声英语 v1.7.4

回声英语是一款面向英语重复听读的 Android 应用，支持持久化播放列表、批量导入 MP3/SRT、按固定时长或字幕分段、仅播放字幕片段、分段重复、重复间隔、倍速播放、总进度与分段跳转、睡眠定时、媒体通知和锁屏后台播放。

## v1.7.4 更新

- 固定时长与字幕分段统一规范化，排序、裁剪并消除区间重叠。
- 重复播放时从分段起点开始，不再把保存的段内进度当作第一遍起点。
- 同段重复复用已准备的媒体项，避免每遍重新创建 MP3 解码入口。
- 当前段最后一遍和下一段第一遍使用两个预缓冲的独立裁剪媒体项；设置的静音间隔只用于同段重复。
- 为 VBR MP3 启用 `Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING` 和 `SeekParameters.EXACT`，提高较后位置和分段边界的准确度。
- 重复间隔期间保留 mediaPlayback 前台服务，并短时持有 `PARTIAL_WAKE_LOCK`，间隔结束后立即释放。
- 系统媒体会话使用整条音频的绝对进度，支持系统上一段、下一段和点击通知返回应用。

## 构建与验证

- versionName：1.7.4
- versionCode：13
- minSdk：26
- targetSdk / compileSdk：34
- JDK：17
- 自动测试：63 项全部通过
- Debug APK：APK Signature Scheme v2 验证通过
- 真机：Xiaomi M2102K1C 覆盖安装成功

## 本地构建

配置 JDK 17 与 Android SDK 后，在项目根目录执行：

```powershell
.\gradlew.bat --no-daemon --max-workers=1 testDebugUnitTest assembleDebug
```

如 D8 在依赖合并阶段内存不足，可临时设置：

```powershell
$env:GRADLE_OPTS='-Xmx4096m -Dfile.encoding=UTF-8'
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

这是 Debug 测试版本，正式上架前应使用独立 Release 证书签名。