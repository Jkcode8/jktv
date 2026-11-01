# 变更记录

本项目从上游 [guizang-tv](https://github.com/op7418/guizang-tv) 的 `312c011` 分支出来。0.3.0 及更早的版本由上游发布，此文件只记录本仓库的改动。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [未发布]

### 新增

- 「设置 → 首页内容 → 添加入口 → 文件夹」：可以从 U 盘、SD 卡或内部存储挑一个目录作为首页入口。此前文件夹入口只能在源码里写死，换硬盘后无从修复
- 首页默认入口改为运行时探测：外接盘优先，其次内部存储，最后是确实装着的系统应用

### 修复

- 首页默认入口把开发者本机的两个 U 盘卷 ID 与第三方应用包名写死在源码里。别的机器上装出来的入口全部指向不存在的路径，点了没有反应
- 壁纸索引上限提为 `FrameWallpapers.MAX_CATALOG_ENTRIES`，超限时报错信息带上实际条数

### 文档

- 新增 `README.en.md`（英文）、`CONTRIBUTING.md`、`CHANGELOG.md`、Issue／PR 模板
- README 首屏加入 CI／版本／minSdk／许可徽章与「三十秒判断要不要往下看」
- 新增「接下来想做什么」，写清语音入口、多卷、非小米适配的方向
- 「已知限制」按实际行为重写，不再描述尚未提供的功能

---

## [v0.4.1] — 2026-11-01

### 新增

- 内置 7 张维护者自拍风景壁纸（雅丹、草原、梯田、山水），与原有 5 张 Unsplash 合计 12 张；统一处理为 1920 × 1080、亮度 −0.09 EV、按原图亮度自适应暗角，输出剥离 EXIF
- `scripts/build_author_wallpapers.sh`：换一批照片时重跑即可重写索引与缩略图
- README 增加「电视怎么开 ADB 调试」，覆盖小米／红米、原生 Android TV、Android 11+ 配对制三种入口，附故障排查表
- README 增加「不想装开发环境」的三条路，含可直接复制的智能体提示词
- README 增加壁纸素材与许可分项说明
- `.github/workflows/release.yml`：打 tag 即构建、校验签名并上传 Release 资产
- `CONTRIBUTING.md`、Issue／PR 模板、本变更记录

### 修复

- 小米桌面入口的开关写死 `Build.MODEL.equals("MiTV-MTSU0")`，其他小米机型上「小米桌面」「HDMI」「输入源」三个入口被整体禁用。改为按品牌 + `com.mitv.tvhome` 组件探测
- 新装后天气默认关闭且入口藏在实验项里，改为默认开启并随首页显示

### 变更

- 自拍壁纸许可由 CC BY 4.0 改为 **CC0 1.0**（作者为完整权利人，直接释出到公有领域，无需署名）
- 壁纸索引上限由硬编码数字提为 `FrameWallpapers.MAX_CATALOG_ENTRIES`，超限时报错信息带上实际条数

---

## [v0.4.0] — 2026-11-01

### 新增

- 文件夹型首页入口与 `FolderActivity`：绑定存储卷路径，4 列 16:9 网格，确认键直接落到目录
- `ImageViewerActivity`：全屏查看、左右翻页、`2 / 5` 序号
- `AudioPlayerActivity`：文件名、进度条、时长、上下首
- 封面生成与落盘缓存：文件夹取首图，无图则取首个视频 1/5 处抽帧，当前层找不到向下找两层
- 天气增加今日高低温与未来三天预报，TLS 握手失败时降级重试
- 左侧渐变随壁纸上平均亮度加深，改善亮色壁纸上的文字可读性
- 开机自启：`start_3rd_app` 系统设置项 + 极轻量启动判断 Activity
- 公开仓库：MIT 许可、NOTICE、CI（编译 + 单元测试 + lint）、8 张无肖像截图

### 变更

- `minSdk` 26 → 23，加入 Java 8 API desugaring
- 应用名改为 JK TV，`applicationId` 改为 `com.jktv.launcher`，与上游版本可共存
- 视频播放由通用 `ACTION_VIEW` 改为小米私有 `PLAY_URL` + `playInfo`

---

## 0.3.0 及更早

属于上游 [guizang-tv](https://github.com/op7418/guizang-tv)，变更见上游仓库的提交历史。本仓库从上游 `312c011` 起算。

[v0.4.1]: https://github.com/Jkcode8/jktv/releases/tag/v0.4.1
[v0.4.0]: https://github.com/Jkcode8/jktv/releases/tag/v0.4.0
