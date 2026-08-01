# 回声英语 v1.4.0

回声英语是一款面向英语重复听读的 Android 应用，支持批量导入 MP3 与 SRT、按固定时长或字幕切分、分段重复、自动连播、倍速播放、总进度跳转、片段定位、定时关闭和锁屏后台播放。

## v1.4.0 视觉升级

- 底部播放列表、复读、设置导航改为圆角 Material Symbols 图标。
- 整体采用简洁、可爱、色彩丰富的多巴胺风格，统一紫色、珊瑚色、薄荷色、天蓝色、黄色和粉色语义配色。
- 资料库改为圆角彩色卡片，并优化批量导入、文件夹导入、空状态和字幕匹配提示。
- 复读页重新安排字幕、双进度条和播放控制区的视觉层级；保留默认单条字幕、展开滚动字幕和点击跳转。
- 设置页使用单层卡片和明确的紫色选中态，避免重复边框；按字幕分段时，每段时长选项会自动弱化并禁用。
- 保留 v1.3.1 已验证的 MediaSession 前台通知、WakeLock 和锁屏后台播放修复。

## 构建与验证

- versionName：1.4.0
- versionCode：6
- minSdk：26
- targetSdk / compileSdk：34
- JDK：17
- 自动测试：21 项全部通过
- Debug APK：v2 签名验证通过

## 本地构建

配置 JDK 17 与 Android SDK 后，在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

这是 Debug 测试版本，正式发布前需要使用独立 Release 证书签名。
