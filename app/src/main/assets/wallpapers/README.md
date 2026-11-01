# 壁纸资源

内置 12 张，每 5 分钟轮播：`author/` 下 7 张维护者自拍（CC0 1.0），`bundled/` 下 5 张 Unsplash 照片（Unsplash License）。
来源与处理方式见根目录 [`docs/WALLPAPERS.md`](../../../../../docs/WALLPAPERS.md)，Unsplash 的许可通知见 `UNSPLASH-NOTICE.txt`。

两批素材都可以免费使用：Unsplash 那批要求保留作者署名，作者照片那批是 CC0，署名也不要求。代码的 MIT 许可不覆盖这些图片。
`catalog.json` 是唯一的索引文件，最多 15 条；目录为空或索引缺失时，应用回退到五种源码绘制的渐变背景。

## 换成自己的照片

- **只是自己用、不想公开**：`scripts/prepare_wallpapers.py <照片目录>`，写入 `local/`。该目录被 Git 忽略，应用优先读取它，个人照片不会随源码公开。
- **想随包发布、且你有权公开**：仿照 `author/` 的做法，用 `scripts/build_author_wallpapers.sh` 生成，并同步更新 `catalog.json` 与 `docs/WALLPAPERS.md`。

两条路径都不改原图：生成副本最长边 1920 像素（桌面用图为 1920 × 1080），缩略图 320 × 180，均剥离 EXIF。
不要公开发布自己没有再分发权的照片，或包含私人信息的素材。
