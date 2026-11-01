package com.jktv.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import org.json.JSONArray;
import java.util.*;

/** Stable app/source identities; removing an entry never uninstalls an app. */
final class HomeItems {
    /** 确认存在的系统自带入口；装上才出现在首页，没装就跳过。 */
    private static final String[] SHELL_PACKAGES = {
            "com.mitv.tvhome", "com.mitv.gallery", "com.xiaomi.mitv.smartshare"};
    private static final String PREFS = "home-items";
    /** 用户给磁盘入口起的名字。旧版把名字存在 {@code name:dir:…}，见 {@link #migrateLegacyNames()}。 */
    private static final String ALIAS = "alias:";
    private static final String LEGACY = "name:";
    /** 首页一行放得下的大致字数，超过会被省略号切掉，保存时先截断。 */
    private static final int MAX_NAME = 12;
    final Context context;
    final LinkedHashMap<String,String> names = new LinkedHashMap<>();
    final List<String> order = new ArrayList<>();
    private final List<String> removable = new ArrayList<>();
    HomeItems(Context c) {
        context = c;
        discover();
        removable.addAll(StorageVolumes.removable(c));
        migrateLegacyNames();
        try {
            JSONArray data = new JSONArray(c.getSharedPreferences("home-items",0).getString("order","[]"));
            for (int i=0;i<data.length() && order.size()<9;i++) {
                String id = data.getString(i);
                if (validId(id) && !order.contains(id)) order.add(id);
            }
        } catch (Exception ignored) { order.clear(); }
        if (order.isEmpty()) order.addAll(defaults());
    }

    /**
     * 首次进入时的首页条目：外接盘优先，最后才是系统应用。全部来自运行时探测——
     * 写死卷 ID 或第三方包名，等于把开发者自己的设备布置推给别人，别人装完首页
     * 全是点了没反应的入口。
     *
     * 内部存储只在没有插任何外接盘时兜底：电视的看点在外接硬盘，多一条"内部存储"
     * 对绝大多数人只是占位。
     */
    private List<String> defaults() {
        List<String> out = new ArrayList<>();
        for (String root : removable) out.add("dir:" + root);
        if (out.isEmpty()) {
            String primary = StorageVolumes.primary();
            if (primary != null) out.add("dir:" + primary);
        }
        for (String pkg : SHELL_PACKAGES) if (installed(pkg)) out.add(pkg);
        return out;
    }

    /** 当前挂载的可移动卷，供设置页列出候选目录。 */
    List<String> removable() { return removable; }

    /**
     * 文件夹入口名。插着两个以上外接盘时带序号——否则首页上并排三个"移动存储"，
     * 分不出哪个是哪个。
     */
    String folderLabel(String path) {
        int index = removable.indexOf(path);
        if (index < 0 || removable.size() < 2) return StorageVolumes.label(path);
        return "移动存储 " + (index + 1);
    }

    private SharedPreferences prefs() { return context.getSharedPreferences(PREFS, 0); }

    /** 用户给这个入口起的名字；没起过就是 null。磁盘、HDMI、应用都能改名。 */
    String alias(String id) {
        String value = prefs().getString(ALIAS + id, "");
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** 没有任何自定义名时该显示什么。 */
    String autoName(String id) {
        if (id.startsWith("dir:")) return folderLabel(id.substring(4));
        if (isHdmi(id)) return hdmiLabel(id.substring(5));
        return names.getOrDefault(id, prefs().getString(LEGACY + id, id));
    }

    /** {@code hdmi:1} 这类信号源入口：名字固定，图标也固定，不跟名字变。 */
    static boolean isHdmi(String id) { return id.startsWith("hdmi:"); }

    static String hdmiLabel(String port) { return "HDMI " + port; }

    static String hdmiLabel(int port) { return hdmiLabel(String.valueOf(port)); }

    /** 能切换的 HDMI 端口；固件不支持时返回空表，设置页据此不列这一组。 */
    static List<Integer> hdmiPorts(Context context) {
        return XiaomiControls.hdmiAvailable(context) ? Arrays.asList(1, 2) : Collections.<Integer>emptyList();
    }

    /**
     * 改名。传空串、或传回自动名表示恢复默认——把自动名存下来没有意义，
     * 换盘或换语言之后它就错了。
     */
    boolean rename(String id, String name) {
        if (!validId(id)) return false;
        String value = name == null ? "" : name.trim();
        if (value.length() > MAX_NAME) value = value.substring(0, MAX_NAME);
        SharedPreferences.Editor e = prefs().edit();
        if (value.isEmpty() || value.equals(autoName(id))) e.remove(ALIAS + id);
        else e.putString(ALIAS + id, value);
        return e.commit();
    }

    static String displayName(String alias, String auto) {
        return alias == null || alias.trim().isEmpty() ? auto : alias.trim();
    }

    /**
     * 旧版把入口名一律落盘，连"移动存储 1"这种自动生成的也存。
     * 这些不能当成用户起的名字，否则迁移之后自动名被冻住，换盘或换顺序就错位。
     */
    static boolean isAutoLabel(String value, String auto) {
        if (value == null) return true;
        String v = value.trim();
        if (v.isEmpty() || v.equals(auto)) return true;
        return v.matches("移动存储( \\d+)?") || v.equals("内部存储") || v.equals("存储");
    }

    /** 把旧版 {@code name:dir:…} 迁成 {@code alias:dir:…}，迁完删掉旧键。幂等。 */
    private void migrateLegacyNames() {
        SharedPreferences p = prefs();
        Map<String, ?> all;
        try { all = p.getAll(); } catch (RuntimeException unavailable) { return; }
        SharedPreferences.Editor e = null;
        String prefix = LEGACY + "dir:";
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix) || !(entry.getValue() instanceof String)) continue;
            String path = key.substring(prefix.length());
            String id = "dir:" + path;
            String value = ((String) entry.getValue()).trim();
            if (e == null) e = p.edit();
            if (!p.contains(ALIAS + id) && !isAutoLabel(value, folderLabel(path))) {
                e.putString(ALIAS + id, value.length() > MAX_NAME ? value.substring(0, MAX_NAME) : value);
            }
            e.remove(key);
        }
        if (e != null) e.apply();
    }

    private boolean installed(String pkg) {
        try { context.getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (android.content.pm.PackageManager.NameNotFoundException absent) { return false; }
    }

    void discover() {
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String id = r.activityInfo.packageName;
                if (!id.equals(context.getPackageName()) && r.activityInfo.enabled && r.activityInfo.exported)
                    names.putIfAbsent(id, XiaomiControls.label(id, r.loadLabel(context.getPackageManager()).toString()));
            }
        }
    }
    String name(String id) {
        // 自定义名优先；没起过名按当前情况现算。自动名不落盘——
        // 存下来的名字在换盘、增删卷或换语言之后就会与实际不符。
        return displayName(alias(id), autoName(id));
    }
    static boolean validId(String id) {
        return id.equals("hdmi:1") || id.equals("hdmi:2") || id.startsWith("dir:") && id.length() > 4
                || id.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+");
    }
    boolean save(List<String> next) {
        if (next.isEmpty() || next.size()>9 || new HashSet<>(next).size()!=next.size()) return false;
        for(String id : next) if(!validId(id)) return false;
        // 名字只走 alias 一套键。旧版这里还会顺手把当前显示名写进 name:…，
        // 那样「恢复自动名称」删掉 alias 之后又会读到这份快照，等于恢复不了。
        SharedPreferences.Editor e = prefs().edit().putString("order",new JSONArray(next).toString());
        if(!e.commit()) return false;
        order.clear(); order.addAll(next); return true;
    }
}
