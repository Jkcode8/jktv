# JK TV

**把硬盘里的片子与照片，放到开机第一屏；一次点击，直接开播。**

一个可以在老款 Android TV 上自建桌面、直读外接硬盘的轻量 Launcher。没有推荐位、没有开机广告、没有账号，只有你自己的入口、真实缩略图和天气。

![首页](screenshots/01-home.jpg)

---

## 这个版本从哪来

本项目是 [**guizang-tv**](https://github.com/op7418/guizang-tv) 的二次开发版本。

- 上游作者：[@op7418（归藏）](https://github.com/op7418)
- 上游仓库：<https://github.com/op7418/guizang-tv>
- 本分支起点：上游 `main` @ `312c011`

上游项目本身也站在别人的肩膀上——它衍生自 [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)（MIT），并参考了 [nthnclrk/CleanFrame](https://github.com/nthnclrk/CleanFrame) 的设置交互。完整的来源链条、第三方素材与许可说明，请见上游仓库的 [`NOTICE.md`](https://github.com/op7418/guizang-tv/blob/main/NOTICE.md)。

**首页的整体设计语言、列表与 Dock 布局、选中样式与磨砂材质、设置分组与首页预览、AI 助手浮层的视觉，都来自上游。** 本版本没有重新设计界面，做的是另一件事：让它在 2016 年的机器上装得上、跑得动，并且真的能当文件浏览器用。

---

## 上游 vs 本版本

| 能力 | 上游 guizang-tv | 本版本 |
| --- | --- | --- |
| 最低系统 | `minSdk 26`（Android 8） | `minSdk 23`（Android 6），含 Java 8 API desugaring |
| 首页入口 | 应用快捷方式 | 增加「文件夹」类型入口，可绑定存储卷路径，按下确认直接落到该目录 |
| 目录浏览 | — | 新增 `FolderActivity`：4 列 16:9 网格、封面生成与落盘缓存 |
| 图片查看 | — | 新增 `ImageViewerActivity`：全屏、左右翻页、序号显示 |
| 音频 | 交给视频播放器，画面全黑 | 新增 `AudioPlayerActivity`：文件名、进度条、时长、上下首 |
| 视频播放 | 通用 `ACTION_VIEW` | 小米私有 `PLAY_URL` + `playInfo`，固定调用系统播放器 |
| 天气 | 实时天气 | 增加今日高低温与未来三天预报；TLS 兼容性重试 |
| 壁纸 | 内置 5 张 + 轮播 | 支持导入本地照片：转 16:9、按 EXIF 摆正、剥离定位信息 |
| 亮色壁纸可读性 | 白字直接压在图上 | 左侧渐变随亮度加深，文字加投影 |
| 开机自启 | — | `start_3rd_app` + 极轻量启动判断 Activity |
| 应用名 | 归藏电视 | JK TV（包名 `com.jktv.launcher`，可与上游版本共存安装） |

---

## 功能

### 硬盘直达，跳过三层菜单

首页的「电影动画」「家庭照片」不是快捷方式，而是绑定到具体存储卷的路径。按下确认键，直接落到那个目录的缩略图墙，中间不经过任何系统菜单。

![目录网格](screenshots/02-folder.jpg)

### 每一格都有真实封面

- 文件夹优先取内部第一张照片作封面；没有照片时，取第一个视频的抽帧（按时长 1/5 处取，避开片头黑场）
- 当前层找不到就向下找两层，深层目录也能出图
- 照片按 EXIF 方向自动摆正，竖拍不会躺倒
- 封面结果落盘缓存，第二次进入直接出图
- 视频同样出缩略图并标注文件大小

![视频列表](screenshots/05-videos.jpg)

### 照片：从缩略图墙到全屏

![照片网格](screenshots/03-photos.jpg)

全屏查看时，左上角显示文件名与 `2 / 5`，左右翻页，在当前目录内循环。

![全屏查看](screenshots/04-viewer.jpg)

### 声音也要有界面

音频如果交给视频播放器，结果是**只有声音、画面全黑**——那本来就是个视频播放器。所以单独做了一个音频页面：文件名、播放状态、进度条、`0:06 / 0:55`、曲目序号，全部可见。

| 按键 | 行为 |
| --- | --- |
| 确认 | 播放 / 暂停 |
| ← → | 快退 / 快进 10 秒 |
| ↑ ↓ | 上一首 / 下一首（循环） |
| 播完 | 自动接下一首 |

### 天气与时间

右上角一行显示时间、天气图标、温度、天气状况、城市名。数据来自 [Open-Meteo](https://open-meteo.com/)，城市可自动识别或手动指定，另含今日最高／最低温与未来三天预报。

![时间与天气](screenshots/08-weather.jpg)

### 背景与设置

七组设置全部在同一页面完成，右侧是「首页预览」，改动实时反映，不用反复退回首页确认。

![背景设置](screenshots/07-background.jpg)

![设置中心](screenshots/06-settings.jpg)

---

## 为了跑在 Android 6 上，踩过的坑

这台测试机是 2016 年的小米电视（MiTV4，Android 6.0.1 / API 23），而上游最低要求 API 26。真正的工作量不在界面，在兼容层。

**1 · 系统根证书库太旧，天气一直拿不到数据**

现象是天气开关打开了、缓存文件却始终是空的。`api.open-meteo.com` 用 Let's Encrypt，`ipwho.is` 用 Google Trust Services——两张根证书都不在 2015 年的系统证书库里，TLS 握手在请求发出之前就失败了，抛的是 `SSLException`，不是网络错误。

解法：首次请求失败时捕获 `SSLException`，用受限的宽松校验重试一次；同时把超时从 8 秒放宽到 12 秒（实测响应约 3.2 秒）。

**2 · Java 8 默认方法在 API 23 上不存在**

改字节码把 dex 版本从 038 降到 035 只能解决「装不上」。装上之后 `Map.putIfAbsent` 立刻 `NoSuchMethodError`。最终放弃二进制改造，改为开启 `coreLibraryDesugaring` 重新编译，共处理 4 处调用。

**3 · 视频播放不能走通用入口**

直接拼 `ACTION_VIEW + file://` 在这台电视上会报 `error_code (1001,1)`。改用小米的私有接口 `tv.intent.action.external.PLAY_URL`，以 JSON 传 `playInfo`，播放器正常拉起，倍速与字幕都可用；同时固定指向 `com.mitv.videoplayer`，不再每次弹「用哪个应用打开」。

**4 · 视频播放中的画面截不出来**

硬件解码输出到独立图层，系统截图和录屏只能拿到黑帧。所以本文里所有视频相关截图，展示的都是文件列表与封面，而不是播放中的画面——这是硬解的正常表现，不影响实际观看。

---

## 构建

环境：JDK 17、Gradle 8.9、Android SDK 34（build-tools 34）。

```bash
./gradlew :app:assembleDebug
```

产物在 `app/build/outputs/apk/debug/`。

> `settings.gradle` 里保留了一份阿里云 Maven 镜像，是为了在国内网络下能拉到依赖。如果不需要，删掉那几行 `maven { url 'https://maven.aliyun.com/...' }` 即可。

---

## 安装到电视

```bash
adb connect <电视IP>:5555
adb install -r app-debug.apk

# 开机自启：把包名写入系统设置项
adb shell settings put system start_3rd_app com.jktv.launcher
```

装好后按遥控器首页键回到 JK TV；想退回小米原桌面，在设置里关掉「桌面与启动」，或直接卸载——应用没有替换任何系统组件。

---

## 已知限制

| 限制 | 原因 |
| --- | --- |
| 电视重启后 ADB 会复位 | MIUI TV 的既有行为，需要在开发者选项里重新打开网络调试。只影响远程维护 |
| AI 助手入口未接通 | 界面与入口来自上游，后端需要在电脑侧运行本地服务并完成一次授权。当前版本点击该入口不产生动作 |
| 视频播放画面无法截图 | 硬解输出在独立图层 |
| 只适配单卷路径 | 两个直达入口绑定具体存储卷 ID，换硬盘后需要在设置里重新指定目录 |
| 仅在一台机型上验证 | MiTV4-ANSM0 / Android 6.0.1。其他机型（尤其非小米）的视频拉起逻辑需要另做适配 |

---

## 关于包名与签名

本版本的 `applicationId` 是 `com.jktv.launcher`，与上游的 `io.github.op7418.guizangtv` 完全独立，两者可以同时装在一台电视上互不影响。

如果你要发布自己的 APK，建议再改一次包名（换成你自己的域名反写），并在 `app/build.gradle` 里配置签名：

```bash
export JKTV_KEYSTORE=/path/to/release.jks
export JKTV_STORE_PASSWORD=******
export JKTV_KEY_ALIAS=jktv
export JKTV_KEY_PASSWORD=******
```

不配置这组环境变量时，构建产出未签名 APK，只能自己调试用。密钥与密码不要提交到 Git。

---

## 截图说明

文中的照片、视频均为演示素材：照片取自项目内置的 Unsplash 壁纸（Unsplash License），视频由这些图片生成的短暂推拉镜头，不含第三方影视内容与任何真人肖像。

---

## 致谢

- [@op7418](https://github.com/op7418) — [guizang-tv](https://github.com/op7418/guizang-tv)，本项目的基础
- [Gathin23](https://github.com/Gathin23) — [cleanyourtv](https://github.com/Gathin23/cleanyourtv)，上游的上游
- [Nathan Clark](https://github.com/nthnclrk) — [CleanFrame](https://github.com/nthnclrk/CleanFrame)，设置交互参考
- [Open-Meteo](https://open-meteo.com/) — 免费天气与地理编码，数据 CC BY 4.0
- [Unsplash](https://unsplash.com/) — 内置壁纸，Unsplash License

## License

代码沿用上游的 MIT License，见 [`LICENSE`](LICENSE)。第三方素材（壁纸、图标、天气数据）各自适用其原始许可，不因本仓库的 MIT 声明而改变。
