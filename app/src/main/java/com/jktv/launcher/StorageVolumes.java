package com.jktv.launcher;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 存储卷发现。
 *
 * 卷 ID 由运行时探测得出，不写死任何设备路径：写死会让别的机器上装出来的入口
 * 全部指向不存在的目录，新用户第一次打开就是两个死链。
 *
 * 不能直接列 {@code /storage}——那个目录对普通应用没有读权限，
 * 列表会是空的。改用 {@link Context#getExternalFilesDirs}，它返回每个已挂载卷上
 * 的应用私有目录，从中截出卷根。
 */
final class StorageVolumes {
    /** 内部存储的固定挂载点；Environment 在 API 23 上返回的也是它。 */
    static final String PRIMARY = "/storage/emulated/0";
    private StorageVolumes() {}

    /** 可移动卷（U 盘、SD 卡）的挂载根，按路径排序保证顺序稳定。 */
    static List<String> removable(Context c) {
        List<String> paths = new ArrayList<>();
        File[] dirs;
        try { dirs = c.getExternalFilesDirs(null); } catch (RuntimeException unavailable) { dirs = null; }
        if (dirs != null) for (File dir : dirs) if (dir != null) paths.add(dir.getAbsolutePath());
        return rootsFrom(paths);
    }

    /**
     * 纯逻辑部分：从 {@code .../Android/data/<pkg>/files} 这类路径里截出卷根，
     * 去掉内部存储，去重并按字典序排序（同一台机器每次启动顺序一致，入口不会自己换位置）。
     */
    static List<String> rootsFrom(List<String> appDirs) {
        List<String> roots = new ArrayList<>();
        for (String path : appDirs) {
            if (path == null) continue;
            int cut = path.indexOf("/Android/");
            if (cut <= 0) continue;
            String root = path.substring(0, cut);
            if (root.startsWith(PRIMARY) || roots.contains(root)) continue;
            roots.add(root);
        }
        Collections.sort(roots);
        return roots;
    }

    /** 内部存储根目录，形如 /storage/emulated/0。 */
    static String primary() {
        File dir = Environment.getExternalStorageDirectory();
        return dir == null ? null : dir.getAbsolutePath();
    }

    /** 是不是某个卷的根（{@code /storage/xxx} 或内部存储根）；子目录不算。 */
    static boolean isVolumeRoot(String path) {
        if (path == null || path.isEmpty()) return false;
        return path.equals(PRIMARY) || path.matches("/storage/[^/]+");
    }

    /**
     * 入口显示名：卷根按介质类型称呼，卷里的子目录用它自己的目录名。
     * 子目录名很重要——入口指到 {@code /U盘/动画} 时，叫"移动存储"就分不出是什么了。
     */
    static String label(String path) {
        if (path == null || path.isEmpty()) return "存储";
        if (path.startsWith(PRIMARY)) {
            String tail = path.substring(PRIMARY.length());
            if (tail.startsWith("/")) tail = tail.substring(1);
            return tail.isEmpty() ? "内部存储" : tail;
        }
        if (isVolumeRoot(path)) return "移动存储";
        int cut = path.lastIndexOf('/');
        return cut < 0 || cut == path.length() - 1 ? "移动存储" : path.substring(cut + 1);
    }

    /** {@code path} 落在哪个卷里；认不出来就返回它自己。 */
    static String volumeOf(String path, List<String> removable, String primary) {
        if (path == null || path.isEmpty()) return path;
        if (primary != null && within(path, primary)) return primary;
        if (removable != null) for (String root : removable) if (within(path, root)) return root;
        return path;
    }

    private static boolean within(String path, String root) {
        return root != null && (path.equals(root) || path.startsWith(root + "/"));
    }
}
