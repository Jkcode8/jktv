#!/usr/bin/env bash
# 由作者自有照片生成随包壁纸（1920×1080 + 320×180 缩略图）。
#
# 处理链：16:9 中心裁切 → 轻微压暗（-0.09 EV）→ 降饱和（0.92）→ 按平均亮度自适应暗角
# → 剥离 EXIF（含拍摄地点与设备信息）→ 写回 wallpapers/catalog.json。
#
# 用法：scripts/build_author_wallpapers.sh <照片目录>
# 依赖：ffmpeg、python3
#
# 这些照片是可公开发布的素材（见 app/src/main/assets/wallpapers/README.md）。
# 个人照片请走 scripts/prepare_wallpapers.py，它写入被 Git 忽略的 local/。
set -euo pipefail

SRC="${1:?用法: $0 <照片目录>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/wallpapers/author"
FFMPEG="${FFMPEG:-ffmpeg}"

# 源文件名（不含扩展名）→ 显示名。顺序即壁纸列表顺序。
# 新增照片时在这里追加一行，然后重跑本脚本。
PHOTOS=(
  "04_雅丹_IMG_3195|雅丹层岩"
  "06_雅丹_IMG_3354|戈壁孤峰"
  "07_雅丹_IMG_3525|夕照台地"
  "08_雅丹_IMG_3549|岩壁天光"
  "微信图片_20261031163923_4_20|草原与雪岭"
  "微信图片_20261031164915_18_20|梯田村落"
  "微信图片_20261101095323_3_2|山水之间"
)

BASE="scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080"
BRIGHTNESS=-0.09
SATURATION=0.92
VIG_BRIGHT="vignette=angle=PI/3.0"   # YAVG > 140
VIG_MID="vignette=angle=PI/3.4"      # 100 < YAVG <= 140
VIG_DARK="vignette=angle=PI/4"       # YAVG <= 100

mkdir -p "$OUT"
mapfile_file="$OUT/.names.tsv"
: > "$mapfile_file"

index=0
for item in "${PHOTOS[@]}"; do
  stem="${item%%|*}"
  label="${item##*|}"
  index=$((index + 1))
  name=$(printf "author-%02d" "$index")
  src=$(ls "$SRC/$stem".* 2>/dev/null | head -1 || true)
  if [ -z "$src" ]; then
    echo "跳过（找不到源文件）：$stem" >&2
    continue
  fi

  yavg=$("$FFMPEG" -hide_banner -i "$src" \
    -vf "scale=160:90,signalstats,metadata=print:key=lavfi.signalstats.YAVG" \
    -f null - 2>&1 | grep -oE "YAVG=[0-9.]+" | head -1 | cut -d= -f2)
  yi=${yavg%.*}

  if   [ "$yi" -gt 140 ]; then VIG="$VIG_BRIGHT"
  elif [ "$yi" -gt 100 ]; then VIG="$VIG_MID"
  else                         VIG="$VIG_DARK"
  fi

  # -map_metadata -1 剥掉 EXIF：定位、机型、拍摄时间都不该进安装包
  "$FFMPEG" -hide_banner -loglevel error -y -i "$src" \
    -vf "$BASE,eq=brightness=${BRIGHTNESS}:saturation=${SATURATION},$VIG" \
    -map_metadata -1 -q:v 3 "$OUT/$name.jpg"
  "$FFMPEG" -hide_banner -loglevel error -y -i "$OUT/$name.jpg" \
    -vf "scale=320:180" -map_metadata -1 -q:v 4 "$OUT/${name}_thumb.jpg"

  printf '%s\t%s\n' "$name" "$label" >> "$mapfile_file"
  echo "$name  $label  YAVG=$yi  $VIG"
done

# 合并清单：作者照片在前，原有的 Unsplash 条目原样保留在后。
python3 - "$OUT" "$ROOT" <<'PY'
import io, json, os, sys
out, root = sys.argv[1], sys.argv[2]
names = {}
with io.open(os.path.join(out, ".names.tsv"), encoding="utf-8") as handle:
    for line in handle:
        if "\t" in line:
            key, label = line.rstrip("\n").split("\t", 1)
            names[key] = label
author = [{
    "id": name,
    "name": names.get(name, name),
    "file": f"author/{name}.jpg",
    "thumb": f"author/{name}_thumb.jpg",
} for name in sorted(x[:-4] for x in os.listdir(out)
                     if x.endswith(".jpg") and not x.endswith("_thumb.jpg"))]
catalog_path = os.path.join(root, "app/src/main/assets/wallpapers/catalog.json")
with io.open(catalog_path, encoding="utf-8") as handle:
    existing = json.load(handle)
unsplash = [e for e in existing if e["id"].startswith("unsplash-")]
merged = author + unsplash
with io.open(catalog_path, "w", encoding="utf-8") as handle:
    handle.write(json.dumps(merged, ensure_ascii=False, indent=2) + "\n")
print(f"catalog: {len(author)} author + {len(unsplash)} unsplash = {len(merged)}")
PY

rm -f "$mapfile_file"
du -sh "$OUT"
