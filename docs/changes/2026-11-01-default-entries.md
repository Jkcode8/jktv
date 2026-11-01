# 开源呈现与默认入口修复

日期：2026-11-01
范围：`HomeItems`、`HomeSettings`、`FrameWallpapers`、新增 `StorageVolumes`；仓库文档与 CI

## 起因

准备把仓库推给更多人看时，重读 README 发现两处描述与代码不符：

1. 「已知限制」写着「换硬盘后需要在设置里重新指定目录」，但设置里没有任何添加或修改文件夹入口的入口。
2. 「首页入口」写着「绑定存储卷路径」，实际把开发者本机两个 U 盘的卷 ID 与第三方包名写死在 `HomeItems.DEFAULTS` 里。

第 2 条对别人影响更直接：装上后首页前两项是 `dir:/storage/9E2C13BF2C1390FF` 这类不存在的路径，点了没有反应；`com.xiaodianshi.tv.yst`（当贝影视）没装的机器上同样是个死链。

## 改了什么

### 默认入口改为运行时探测

`HomeItems.DEFAULTS` 删除，换成 `defaults()`：

1. `/storage` 下的可移动卷（U 盘、SD 卡），路径取自运行时扫描，按字典序排序保证顺序稳定
2. 内部存储根 `/storage/emulated/0`
3. `com.mitv.tvhome`、`com.mitv.gallery`、`com.xiaomi.mitv.smartshare`——先 `getPackageInfo` 探测，没装就跳过

新增 `StorageVolumes`，把 `/storage` 的子目录过滤（排除 `emulated`、`self`、隐藏项）抽成纯函数 `rootsFrom(List<String>)`，可在 JVM 单元测试里直接验证。

### 补上「添加文件夹」

`设置 → 首页内容 → 添加入口 → 文件夹`：列出各存储卷根，以及内部存储下的 `Movies`／`Pictures`／`DCIM`／`Download`（存在才列）。选中后追加到首页末尾，走既有的 `save()` 路径，带撤销。

已加过的目录不再重复列出；超过 9 个入口时提示上限。

### 命名

- 两个以上外接盘时带序号（`移动存储 1`、`移动存储 2`），否则并排三个同名入口分不出来
- 内部存储的子目录用目录名（`Movies`），根目录才叫「内部存储」

### 顺带

- `FrameWallpapers` 的索引上限 `15` 提为 `MAX_CATALOG_ENTRIES`，异常信息带上实际条数与上限

## 验证

- `:app:assembleRelease`、`:app:testDebugUnitTest`、`:app:lintDebug` 均通过
- 新增 `StorageVolumesTest`（5 个用例）：排序、内部存储排除、隐藏/空名过滤、空列表兜底、标签区分
- 真机（MiTV4-ANSM0 / Android 6.0.1）实测：装新包后首页入口变为两个「移动存储」、「内部存储」、「小米原桌面」、「相册」、「小米投屏」；`添加文件夹` 页正确列出四个内部存储目录，添加后入口出现在第 7 位

## 已知取舍

- 老用户若保存过入口顺序（`home-items` 的 `order`），不受影响；未保存过的会看到入口名从「电影动画／家庭照片」变为「移动存储 1／2」，路径未变
- 换硬盘导致入口失效时，仍需先「从首页隐藏」再重新添加，未做按卷名自动重绑
- 目录选择只到「卷根 + 内部存储的常见媒体目录」一层，未做多级下钻
