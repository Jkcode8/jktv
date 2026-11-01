# 参与贡献

开发版与正式包不是一回事：`main` 分支是开发版，随时可能编译不过；[Releases](https://github.com/Jkcode8/jktv/releases) 里的是正式包。提改动前请先确认你要改的是哪一个。

## 报告问题

先看 [已知限制](README.md#已知限制)——「重启后 ADB 复位」「视频播放画面截不出图」这类是系统行为，不是 bug。

有用的问题报告包含这几项：

- 电视品牌、型号、Android 版本（`adb shell getprop ro.product.model` 与 `ro.build.version.release`）
- 从装机到出问题的完整步骤
- `adb logcat -s TvHome:* AndroidRuntime:E` 里相关的那段，不要贴整份日志
- 截图（画面问题）或录屏（按键无响应）

**不要**在 issue 里贴签名库、口令、配对码、完整 `local.properties`、含个人照片的截图。需要的话先打码。

## 提交改动

```bash
git clone https://github.com/Jkcode8/jktv.git
cd jktv
git checkout -b fix/your-topic
./gradlew :app:assembleDebug          # Windows: gradlew.bat
./gradlew :app:testDebugUnitTest      # 纯逻辑部分的单元测试
```

提交前请确认：

1. `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 全部通过
2. 动过 `assets/wallpapers/` 的，跑 `python -m unittest discover -s scripts -p 'test_*.py'`（需要 `Pillow>=11`）
3. 改过界面或交互的，在真机上跑一遍；没设备就在 PR 里写清楚哪些路径没实测

## 代码取向

这个项目的约束条件比一般 Android 应用多，改动时请顺着这几条：

- **最低 Android 6.0（API 23）**。不能用没有做 desugaring 的 Java 8+ API，不能用 API 23 之后才引入的 framework 方法。`View.setBackground` 可以，`View.setBackgroundTintList` 要看版本。
- **遥控器优先**。所有可交互元素必须有焦点态、必须能用方向键到达、必须能用确认键触发。触屏和鼠标是次要路径。
- **不引入重依赖**。没有 AndroidX 之外的第三方库，没有 Kotlin 协程、没有图片加载框架。封面生成与缓存是手写的，就是为了控制方法数和包体积。
- **界面外观沿用上游设计**（磨砂、圆角、玻璃 Dock）。要改视觉风格请单独开 issue 讨论，不要夹在功能 PR 里。
- **不要替换系统组件**。桌面、Home 键、HDMI、小爱、投屏的既有行为尽量保持原样。

## 关于壁纸与素材

- 可公开的照片放 `app/src/main/assets/wallpapers/author/`，需要是**你自己拍的**，或明确允许再分发的
- 私人照片放 `local/`，这个目录被 `.gitignore` 挡住，不会进仓库
- 索引上限 15 条（`FrameWallpapers.MAX_CATALOG_ENTRIES`），超了整份索引失效、应用退回渐变背景
- 提交照片前先跑 `scripts/build_author_wallpapers.sh` 或按 [`docs/WALLPAPERS.md`](docs/WALLPAPERS.md) 处理：转 16:9、剥离 EXIF（拍摄定位别跟着进仓库）

## 许可

代码以 MIT 提交，提交即表示你同意以同一许可发布。第三方素材各自适用原始许可，见 [NOTICE.md](NOTICE.md)。
