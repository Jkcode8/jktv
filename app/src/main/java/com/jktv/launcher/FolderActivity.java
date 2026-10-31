package com.jktv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Remote-first browser for the disks plugged into the set. Folders arrive as a
 * grid of covers - the first photo inside, or a still from its first video -
 * so a 30-folder disk can be scanned at a glance instead of read line by line.
 * Folders holding only photos open the same way; anything else stays a list,
 * which is both faster and easier to read for long episode names.
 */
public final class FolderActivity extends Activity {
    static final String EXTRA_DIR = "dir";
    static final String EXTRA_TITLE = "title";
    private static final int REQ_STORAGE = 41;
    private static final int MAX_ROWS = 600;
    private static final int COLUMNS = 4;
    private static final Map<String, String> MIME = new HashMap<>();

    static {
        String[][] table = {
                {"mp4", "video/mp4"}, {"mkv", "video/x-matroska"}, {"avi", "video/x-msvideo"},
                {"ts", "video/mp2t"}, {"m2ts", "video/mp2t"}, {"mov", "video/quicktime"},
                {"wmv", "video/x-ms-wmv"}, {"flv", "video/x-flv"}, {"rmvb", "video/vnd.rn-realvideo"},
                {"rm", "video/vnd.rn-realvideo"}, {"mpg", "video/mpeg"}, {"mpeg", "video/mpeg"},
                {"m4v", "video/x-m4v"}, {"3gp", "video/3gpp"}, {"webm", "video/webm"},
                {"vob", "video/mpeg"}, {"iso", "video/mp2t"},
                {"jpg", "image/jpeg"}, {"jpeg", "image/jpeg"}, {"png", "image/png"},
                {"gif", "image/gif"}, {"bmp", "image/bmp"}, {"webp", "image/webp"},
                {"heic", "image/heic"}, {"heif", "image/heif"},
                {"mp3", "audio/mpeg"}, {"flac", "audio/flac"}, {"wav", "audio/x-wav"},
                {"aac", "audio/aac"}, {"m4a", "audio/mp4"}, {"ogg", "audio/ogg"},
                {"ape", "audio/x-ape"}, {"wma", "audio/x-ms-wma"}
        };
        for (String[] row : table) MIME.put(row[0], row[1]);
    }

    private String root;
    private String rootTitle;
    private String current;
    private LinearLayout list;
    private ScrollView scroll;
    private TextView title;
    private TextView subtitle;
    private int generation;
    private int tileWidth;
    private int tileHeight;
    private final List<Tile> tiles = new ArrayList<>();

    /** One grid cell, kept so covers can be fetched only once it scrolls into view. */
    private static final class Tile {
        final File entry;
        final boolean folder;
        final View view;
        final ImageView cover;
        final ImageView glyph;
        final TextView meta;
        boolean asked;

        Tile(File entry, boolean folder, View view, ImageView cover, ImageView glyph, TextView meta) {
            this.entry = entry;
            this.folder = folder;
            this.view = view;
            this.cover = cover;
            this.glyph = glyph;
            this.meta = meta;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        String dir = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_DIR);
        if (TextUtils.isEmpty(dir)) { finish(); return; }
        rootTitle = getIntent().getStringExtra(EXTRA_TITLE);
        // Volume names on NTFS sticks change when the disk is re-seated, so a
        // stored /storage/<uuid> path can go stale; re-find the volume by the
        // folder the entry is named after.
        root = resolve(dir, rootTitle);
        metrics();
        chrome();
        if (granted()) load(root);
        else requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
    }

    private String resolve(String path, String label) {
        if (new File(path).exists()) return path;
        File[] volumes = new File("/storage").listFiles();
        if (volumes == null) return path;
        for (File volume : volumes) {
            String name = volume.getName();
            if ("emulated".equals(name) || "self".equals(name)) continue;
            File[] kids = volume.listFiles();
            if (kids == null) continue;
            for (File kid : kids) {
                if (!kid.isDirectory()) continue;
                String marker = kid.getName();
                if (label != null && marker.length() >= 2
                        && (label.contains(marker) || marker.contains(label)))
                    return volume.getAbsolutePath();
            }
        }
        return path;
    }

    private void metrics() {
        int available = getResources().getDisplayMetrics().widthPixels - dp(128);
        int gap = dp(16);
        tileWidth = (available - gap * (COLUMNS - 1)) / COLUMNS;
        tileHeight = Math.round(tileWidth * 9f / 16f);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != REQ_STORAGE) return;
        if (granted()) load(current == null ? root : current);
        else error("需要存储权限才能读取硬盘", "在「设置 → 应用 → JK TV → 权限」里打开存储权限后重试。");
    }

    private boolean granted() {
        return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void chrome() {
        FrameLayout stage = new FrameLayout(this);
        View back = new View(this);
        back.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A0E12, 0xFF131C23}));
        stage.addView(back, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(64), dp(44), dp(64), dp(28));
        stage.addView(column, new FrameLayout.LayoutParams(-1, -1));

        title = new TextView(this);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(40);
        title.setIncludeFontPadding(false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(title);

        subtitle = new TextView(this);
        subtitle.setTextColor(0x8AFFFFFF);
        subtitle.setTextSize(15);
        subtitle.setIncludeFontPadding(false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.START);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(10);
        column.addView(subtitle, sp);

        View rule = new View(this);
        rule.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(1));
        rp.topMargin = dp(22);
        column.addView(rule, rp);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        // The scroller must never take focus itself, otherwise DPAD navigation can
        // park the highlight on it and the grid looks dead.
        scroll.setFocusable(false);
        scroll.setFocusableInTouchMode(false);
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> pump());
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setFocusable(false);
        list.setFocusableInTouchMode(false);
        list.setPadding(0, dp(10), 0, dp(46));
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = dp(6);
        column.addView(scroll, lp);

        TextView hint = new TextView(this);
        hint.setText("OK 打开 · 返回 上一层");
        hint.setTextColor(0x66FFFFFF);
        hint.setTextSize(14);
        hint.setIncludeFontPadding(false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(14);
        column.addView(hint, hp);

        setContentView(stage);
    }

    private void load(String path) { load(path, null); }

    private void load(String path, String focusName) {
        current = path;
        generation++;
        tiles.clear();
        File dir = new File(path);
        if (!dir.exists()) { error("文件夹不存在", path); return; }
        File[] children = dir.listFiles();
        if (children == null) { error("无法读取这个文件夹", path + "\n可能是存储权限或磁盘已拔出。"); return; }

        List<File> dirs = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File f : children) {
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (name.equalsIgnoreCase("$RECYCLE.BIN")) continue;
            if (name.equalsIgnoreCase("System Volume Information")) continue;
            if (f.isDirectory()) dirs.add(f); else files.add(f);
        }
        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(dirs, (a, b) -> collator.compare(a.getName(), b.getName()));
        Collections.sort(files, (a, b) -> collator.compare(a.getName(), b.getName()));

        String heading = dir.getName().isEmpty() ? path : dir.getName();
        if (path.equals(root) && !TextUtils.isEmpty(rootTitle)) heading = rootTitle;
        title.setText(heading);
        subtitle.setText(path);
        list.removeAllViews();

        int total = dirs.size() + files.size();
        if (total == 0) {
            TextView empty = new TextView(this);
            empty.setText("这个文件夹是空的");
            empty.setTextColor(0x66FFFFFF);
            empty.setTextSize(22);
            empty.setPadding(dp(18), dp(30), 0, 0);
            list.addView(empty);
            return;
        }
        if (!dirs.isEmpty()) {
            addGrid(dirs, true, Math.min(dirs.size(), MAX_ROWS));
            if (!files.isEmpty()) addRows(files, Math.max(0, MAX_ROWS - dirs.size()));
        } else if (hasMedia(files)) {
            // Phone backups mix clips and stills in one folder; a cover grid reads
            // far better there than a wall of file names.
            addGrid(files, false, Math.min(files.size(), MAX_ROWS));
        } else {
            addRows(files, MAX_ROWS);
        }

        View remembered = rowFor(focusName);
        final View target = remembered != null ? remembered : list;
        // Focus has to wait for layout; requesting it inline can silently fail on
        // long lists and leave the screen without any highlight.
        list.post(() -> {
            pump();
            View focusable = firstFocusable(target);
            if (focusable != null && !focusable.isFocused()) focusable.requestFocus();
        });
    }

    private static View firstFocusable(View container) {
        if (container.isFocusable()) return container;
        if (container instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) container;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = firstFocusable(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean withinList(View view) {
        View walk = view;
        while (walk != null) {
            if (walk == list) return true;
            walk = walk.getParent() instanceof View ? (View) walk.getParent() : null;
        }
        return false;
    }

    private static boolean hasMedia(List<File> files) {
        if (files.isEmpty()) return false;
        for (File f : files) {
            String type = mime(f);
            if (type.startsWith("image") || type.startsWith("video")) return true;
        }
        return false;
    }

    private void addGrid(List<File> entries, boolean folders, int limit) {
        int gap = dp(16);
        LinearLayout row = null;
        for (int i = 0; i < entries.size() && i < limit; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setFocusable(false);
                row.setFocusableInTouchMode(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.topMargin = gap;
                list.addView(row, rp);
            }
            Tile tile = tile(entries.get(i), folders);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(tileWidth, -2);
            if (i % COLUMNS != COLUMNS - 1) tp.rightMargin = gap;
            (row != null ? row : list).addView(tile.view, tp);
            tiles.add(tile);
        }
    }

    private Tile tile(final File entry, final boolean folder) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setFocusable(true);
        cell.setFocusableInTouchMode(true);
        cell.setClickable(true);
        cell.setPadding(dp(6), dp(6), dp(6), dp(6));
        cell.setBackground(plate(false));
        cell.setTag(entry.getName());

        FrameLayout box = new FrameLayout(this);
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(8));
        frame.setColor(0x1FFFFFFF);
        box.setBackground(frame);
        box.setClipToOutline(true);

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setVisibility(View.INVISIBLE);
        box.addView(cover, new FrameLayout.LayoutParams(-1, -1));

        ImageView glyph = new ImageView(this);
        Drawable mark = getDrawable(folder ? R.drawable.mark_folder : iconFor(entry)).mutate();
        mark.setTint(0x8CFFFFFF);
        glyph.setImageDrawable(mark);
        box.addView(glyph, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));

        cell.addView(box, new LinearLayout.LayoutParams(tileWidth - dp(12), tileHeight));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(9);
        lp.leftMargin = dp(2);
        lp.rightMargin = dp(2);
        cell.addView(line, lp);

        final TextView name = new TextView(this);
        name.setText(entry.getName());
        name.setTextSize(18);
        name.setTextColor(0xF2FFFFFF);
        name.setIncludeFontPadding(false);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        final TextView meta = new TextView(this);
        meta.setText(folder ? "" : size(entry.length()));
        meta.setTextSize(14);
        meta.setTextColor(0x80FFFFFF);
        meta.setIncludeFontPadding(false);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
        mp.leftMargin = dp(10);
        line.addView(meta, mp);

        TextView sub = new TextView(this);
        sub.setText(folder ? "文件夹" : kind(entry));
        sub.setTextSize(13);
        sub.setTextColor(0x55FFFFFF);
        sub.setIncludeFontPadding(false);
        sub.setSingleLine(true);
        LinearLayout.LayoutParams subp = new LinearLayout.LayoutParams(-1, -2);
        subp.topMargin = dp(4);
        subp.leftMargin = dp(2);
        cell.addView(sub, subp);

        cell.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(plate(focused));
            name.setTextColor(focused ? 0xFFFFFFFF : 0xF2FFFFFF);
            if (focused) { centre(v); pump(); }
        });
        cell.setOnClickListener(v -> {
            if (folder) load(entry.getAbsolutePath());
            else open(entry);
        });
        return new Tile(entry, folder, cell, cover, glyph, meta);
    }

    /**
     * Fetches covers only for cells near the viewport. A photo album can hold
     * hundreds of entries and decoding every one up front would stall the set.
     */
    private void pump() {
        if (scroll == null || tiles.isEmpty()) return;
        int top = scroll.getScrollY() - scroll.getHeight();
        int bottom = scroll.getScrollY() + scroll.getHeight() * 2;
        final int gen = generation;
        for (Tile tile : tiles) {
            if (tile.asked) continue;
            int y = offsetInList(tile.view);
            if (y > bottom || y + tile.view.getHeight() < top) continue;
            tile.asked = true;
            Thumbs.inspect(this, tile.entry, tile.folder, tileWidth - dp(12), tileHeight,
                    (bmp, count) -> {
                        if (gen != generation) return;
                        if (tile.folder && count >= 0) tile.meta.setText(count + " 项");
                        if (bmp == null || !tile.cover.isAttachedToWindow()) return;
                        tile.cover.setImageBitmap(bmp);
                        tile.cover.setVisibility(View.VISIBLE);
                        tile.glyph.setVisibility(View.GONE);
                    });
        }
    }

    private int offsetInList(View view) {
        int y = 0;
        View walk = view;
        while (walk != null && walk != list) {
            y += walk.getTop();
            if (!(walk.getParent() instanceof View)) break;
            walk = (View) walk.getParent();
        }
        return y;
    }

    private void addRows(List<File> files, int limit) {
        int shown = 0;
        for (File f : files) {
            if (shown >= limit) break;
            list.addView(row(f, false));
            shown++;
        }
        if (limit > 0 && files.size() > shown) note("还有 " + (files.size() - shown) + " 项未显示");
    }

    private void note(String text) {
        TextView more = new TextView(this);
        more.setText(text);
        more.setTextColor(0x66FFFFFF);
        more.setTextSize(16);
        more.setPadding(dp(18), dp(18), 0, 0);
        list.addView(more);
    }

    private View rowFor(String name) {
        if (name == null) return null;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            View found = findTagged(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static View findTagged(View view, String name) {
        if (name.equals(view.getTag())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTagged(group.getChildAt(i), name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View row(final File file, final boolean isDir) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setClickable(true);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setBackground(plate(false));

        ImageView mark = new ImageView(this);
        Drawable glyph = getDrawable(isDir ? R.drawable.mark_folder : iconFor(file)).mutate();
        glyph.setTint(isDir ? 0xFF7FC7FF : 0x88FFFFFF);
        mark.setImageDrawable(glyph);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams mkp = new LinearLayout.LayoutParams(dp(23), dp(23));
        mkp.leftMargin = dp(2);
        mkp.rightMargin = dp(10);
        row.addView(mark, mkp);

        TextView name = new TextView(this);
        name.setText(file.getName());
        name.setTextSize(isDir ? 27 : 25);
        name.setTextColor(isDir ? 0xFFFFFFFF : 0xD9FFFFFF);
        name.setIncludeFontPadding(false);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.leftMargin = dp(6);
        row.addView(name, np);

        if (!isDir) {
            TextView meta = new TextView(this);
            meta.setText(size(file.length()));
            meta.setTextSize(15);
            meta.setTextColor(0x66FFFFFF);
            meta.setIncludeFontPadding(false);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
            mp.leftMargin = dp(20);
            row.addView(meta, mp);
        }

        row.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(plate(focused));
            name.setTextColor(focused ? 0xFFFFFFFF : (isDir ? 0xFFFFFFFF : 0xD9FFFFFF));
            if (focused) centre(v);
        });
        row.setOnClickListener(v -> {
            if (isDir) load(file.getAbsolutePath());
            else open(file);
        });

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(56));
        rp.bottomMargin = dp(2);
        row.setLayoutParams(rp);
        row.setTag(file.getName());
        return row;
    }

    /** Keeps the highlighted row centred; the scroller does not always follow on this firmware. */
    private void centre(View row) {
        if (scroll == null || row == null) return;
        int top = offsetInList(row);
        int target = top - Math.max(0, (scroll.getHeight() - row.getHeight()) / 2);
        scroll.scrollTo(0, Math.max(0, target));
    }

    private GradientDrawable plate(boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(12));
        if (focused) {
            shape.setColor(0x26FFFFFF);
            shape.setStroke(dp(2), 0xF2FFFFFF);
        } else {
            shape.setColor(Color.TRANSPARENT);
        }
        return shape;
    }

    private void open(File file) {
        String type = mime(file);
        if (type.startsWith("image")) {
            Intent viewer = new Intent(this, ImageViewerActivity.class);
            viewer.putExtra(ImageViewerActivity.EXTRA_FILE, file.getAbsolutePath());
            try {
                startActivity(viewer);
                return;
            } catch (RuntimeException unavailable) {
                android.util.Log.w("TvHome.Folder", "viewer failed", unavailable);
            }
        }
        // Audio must not go to the stock player: it is a video surface, so a song
        // plays with a black screen and no indication of what is running.
        if (type.startsWith("audio")) {
            Intent sound = new Intent(this, AudioPlayerActivity.class);
            sound.putExtra(AudioPlayerActivity.EXTRA_FILE, file.getAbsolutePath());
            try {
                startActivity(sound);
                return;
            } catch (RuntimeException unavailable) {
                android.util.Log.w("TvHome.Folder", "audio player failed", unavailable);
            }
        }
        // The set's own player only starts on its private action with a playInfo
        // payload; a plain ACTION_VIEW makes it fail with error 1001.
        if (playWithSystemPlayer(file)) return;

        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(Uri.fromFile(file), type);
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(view);
        } catch (RuntimeException unsupported) {
            Intent any = new Intent(Intent.ACTION_VIEW);
            any.setDataAndType(Uri.fromFile(file), "*/*");
            any.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(any);
            } catch (RuntimeException none) {
                Toast.makeText(this, "没有应用能打开这个文件", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Mirrors the payload Xiaomi's own file browser sends, so playback just works. */
    private boolean playWithSystemPlayer(File file) {
        try {
            org.json.JSONObject info = new org.json.JSONObject();
            info.put("title", file.getName());
            info.put("url", file.getAbsolutePath());
            info.put("index", 0);
            info.put("rootPath", file.getParent());
            Intent play = new Intent("tv.intent.action.external.PLAY_URL");
            play.setComponent(new android.content.ComponentName("com.mitv.videoplayer",
                    "com.mitv.videoplayer.VideoPlayerActivity"));
            play.putExtra("playInfo", info.toString());
            startActivity(play);
            return true;
        } catch (RuntimeException | org.json.JSONException unavailable) {
            android.util.Log.w("TvHome.Folder", "system player unavailable", unavailable);
            return false;
        }
    }

    private static int iconFor(File file) {
        String type = mime(file);
        if (type.startsWith("video")) return R.drawable.mark_monitor_play;
        if (type.startsWith("image")) return R.drawable.mark_image;
        return R.drawable.mark_app_window;
    }

    private static String kind(File file) {
        String type = mime(file);
        if (type.startsWith("video")) return "视频";
        if (type.startsWith("image")) return "图片";
        if (type.startsWith("audio")) return "音频";
        return "文件";
    }

    private static String mime(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.US);
        String known = MIME.get(ext);
        if (known != null) return known;
        String system = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return system != null ? system : "*/*";
    }

    private void error(String heading, String detail) {
        title.setText(heading);
        subtitle.setText("");
        list.removeAllViews();
        TextView body = new TextView(this);
        body.setText(detail);
        body.setTextColor(0xCCFFFFFF);
        body.setTextSize(19);
        body.setLineSpacing(dp(6), 1f);
        body.setPadding(dp(18), dp(26), dp(18), 0);
        list.addView(body);
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.0f MB", bytes / 1048576.0);
        return String.format(Locale.US, "%.1f GB", bytes / 1073741824.0);
    }

    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        boolean vertical = keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP;
        View focus = getCurrentFocus();
        if (vertical && (focus == null || !withinList(focus))) {
            View first = firstFocusable(list);
            if (first != null) { first.requestFocus(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (current == null || current.equals(root)) { finish(); return; }
        File parent = new File(current).getParentFile();
        if (parent == null || !parent.getAbsolutePath().startsWith(root)) { finish(); return; }
        load(parent.getAbsolutePath(), new File(current).getName());
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
