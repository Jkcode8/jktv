package com.jktv.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** Local profile verified on MiTV-MTSU0, OS3.0.119.0.UTSMATV. */
final class XiaomiControls {
    private XiaomiControls() { }

    /** Brand-level support. Used where no Context is at hand, e.g. label mapping. */
    static boolean enabled() {
        if ("MiTV-MTSU0".equals(Build.MODEL)) return true;
        return "xiaomi".equalsIgnoreCase(Build.BRAND)
                || "xiaomi".equalsIgnoreCase(Build.MANUFACTURER);
    }

    /**
     * Availability on this specific unit: Xiaomi brand plus the stock launcher package
     * actually installed. Upstream gated every entry point on one verified model, which
     * silently disabled them all on other Mi TV builds. Probing the package keeps the
     * same safety without hard-coding a model name.
     */
    static boolean enabled(Context context) {
        if (!enabled()) return false;
        try {
            context.getPackageManager().getPackageInfo("com.mitv.tvhome", 0);
            return true;
        } catch (PackageManager.NameNotFoundException absent) {
            return false;
        }
    }

    /**
     * Boot-start gate. The launch hook rides on the firmware's start_3rd_app handling,
     * which older Mi TV builds also expose, so any Xiaomi set may try it. Specialised
     * HDMI / settings entry points stay restricted to the verified model.
     */
    static boolean bootStartSupported() {
        return enabled() || "xiaomi".equalsIgnoreCase(Build.BRAND);
    }

    /**
     * 这台机器能不能从桌面切信号源：得有小系统，且 {@code ExternalSourceActivity}
     * 是启用状态、允许外部拉起、没有拦住我们的权限。设置页据此决定要不要列出
     * HDMI 端口——列一条点了没反应的入口，比不列更糟。
     */
    static boolean hdmiAvailable(Context context) {
        if (!enabled(context)) return false;
        ComponentName target = new ComponentName("com.xiaomi.mitv.tvplayer",
                "com.xiaomi.mitv.tvplayer.ExternalSourceActivity");
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(target, 0);
            return info.enabled && info.exported
                    && (info.permission == null
                    || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED);
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return false;
        }
    }

    /** Observed on this firmware: source 23 = HDMI 1, source 24 = HDMI 2. */
    static boolean openHdmi(Context context, int port) {
        if (port < 1 || port > 2 || !hdmiAvailable(context)) return false;
        ComponentName target = new ComponentName("com.xiaomi.mitv.tvplayer",
                "com.xiaomi.mitv.tvplayer.ExternalSourceActivity");
        try {
            context.startActivity(new Intent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY")
                    .setComponent(target).putExtra("input", 22 + port)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException unavailable) {
            android.util.Log.w("TvHome.Xiaomi", "HDMI entry unavailable", unavailable);
            return false;
        }
    }

    static Intent settings(Context context, String standardAction) {
        if (!enabled(context)) return new Intent(standardAction);
        String action, activity;
        if (Settings.ACTION_SETTINGS.equals(standardAction)) {
            action = "com.xiaomi.mitv.settings.SETTINGS";
            activity = ".entry.MainActivity";
        } else if (Settings.ACTION_WIFI_SETTINGS.equals(standardAction)) {
            action = "com.xiaomi.mitv.settings.WIFI_SETTINGS";
            activity = ".wifi.MitvWifiActivity";
        } else return null;
        Intent intent = new Intent(action).setComponent(ComponentName.unflattenFromString(
                "com.xiaomi.mitv.settings/" + activity));
        return intent.resolveActivity(context.getPackageManager()) != null ? intent : null;
    }

    static boolean showInputs(Context context) {
        if (!enabled(context)) return false;
        ComponentName receiver = new ComponentName("com.xiaomi.mitv.settings",
                "com.xiaomi.mitv.settings.SettingsBroadcastReceiver");
        try {
            ActivityInfo info = context.getPackageManager().getReceiverInfo(receiver, 0);
            if (!info.exported || !info.enabled) return false;
            if (info.permission != null
                    && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
                return false;
            context.sendBroadcast(new Intent("com.xiaomi.mitv.settings.INPUTSOURCE_POPUP")
                    .setComponent(receiver));
            return true;
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    static String label(String pkg, String original) {
        if (!enabled()) return original;
        switch (pkg) {
            case "com.xiaodianshi.tv.yst": return "哔哩哔哩";
            case "com.xiaomi.mitv.smartshare": return "小米投屏";
            case "com.mitv.gallery": return "相册";
            case "com.mitv.tvhome": return "小米原桌面";
            default: return original;
        }
    }

    static boolean startStockHome(Context context, ComponentName component) {
        if (!enabled(context) || !"com.mitv.tvhome".equals(component.getPackageName())) return false;
        // The public MAIN alias is exported without HOME_STATE. The directly enumerated
        // MainActivityUserMode target requires Xiaomi's signature permission on this firmware.
        ComponentName alias = new ComponentName("com.mitv.tvhome", "com.mitv.tvhome.MainActivity");
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(alias, 0);
            if (!info.enabled || !info.exported) return false;
            if (info.permission != null
                    && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
                return false;
            context.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(alias)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            android.util.Log.w("TvHome.Xiaomi", "Public Xiaomi Home entry unavailable", unavailable);
            return false;
        }
    }
}
