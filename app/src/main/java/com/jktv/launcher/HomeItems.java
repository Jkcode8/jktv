package com.jktv.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import org.json.JSONArray;
import java.util.*;

/** Stable app/source identities; removing an entry never uninstalls an app. */
final class HomeItems {
    /** 确认存在的系统自带入口；装上才出现在首页，没装就跳过。 */
    private static final String[] SHELL_PACKAGES = {
            "com.mitv.tvhome", "com.mitv.gallery", "com.xiaomi.mitv.smartshare"};
    final Context context;
    final LinkedHashMap<String,String> names = new LinkedHashMap<>();
    final List<String> order = new ArrayList<>();
    private final List<String> removable = new ArrayList<>();
    HomeItems(Context c) {
        context = c;
        discover();
        removable.addAll(StorageVolumes.removable(c));
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
     * 首次进入时的首页条目：外接盘优先，其次内部存储，最后才是系统应用。
     * 全部来自运行时探测——写死卷 ID 或第三方包名，等于把开发者自己的设备
     * 布置推给别人，别人装完首页全是点了没反应的入口。
     */
    private List<String> defaults() {
        List<String> out = new ArrayList<>();
        for (String root : removable) out.add("dir:" + root);
        String primary = StorageVolumes.primary();
        if (primary != null) out.add("dir:" + primary);
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
        // 文件夹入口的名字每次按当前挂载情况重新算，不落盘：
        // 存下来的名字在换盘或增删卷之后就会与实际不符。
        if (id.startsWith("dir:")) return folderLabel(id.substring(4));
        return names.getOrDefault(id, context.getSharedPreferences("home-items",0).getString("name:"+id,id));
    }
    static boolean validId(String id) {
        return id.equals("hdmi:1") || id.equals("hdmi:2") || id.startsWith("dir:") && id.length() > 4
                || id.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+");
    }
    boolean save(List<String> next) {
        if (next.isEmpty() || next.size()>9 || new HashSet<>(next).size()!=next.size()) return false;
        for(String id : next) if(!validId(id)) return false;
        android.content.SharedPreferences.Editor e = context.getSharedPreferences("home-items",0).edit()
                .putString("order",new JSONArray(next).toString());
        for(String id:next) if(!id.startsWith("dir:")) e.putString("name:"+id,name(id));
        if(!e.commit()) return false;
        order.clear(); order.addAll(next); return true;
    }
}
