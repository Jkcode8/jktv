package com.jktv.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;

/** Monochrome SVG marks, converted losslessly to Android vectors. See assets/icons/SOURCES.json. */
final class HomeIcons {
    static Drawable get(Context context,String id,int color){return get(context,id,"",color);}

    /**
     * A disk entry is identified by its /storage path, which says nothing about
     * what the disk holds, so the mark follows the name the entry is shown under.
     * HDMI sources behave the same way: "HDMI 1" tells you nothing about what is
     * plugged in, so the mark follows the name too.
     */
    static Drawable get(Context context,String id,String label,int color){
        int resource;
        if(id.startsWith("hdmi:"))resource=sourceMark(label);
        else switch(id){
            case "com.xiaodianshi.tv.yst":resource=R.drawable.mark_bilibili;break;
            case "com.xiaomi.mitv.smartshare":resource=R.drawable.mark_broadcast;break;
            case "com.mitv.gallery":resource=R.drawable.mark_image;break;
            case "com.mitv.tvhome":resource=R.drawable.mark_house;break;
            case "control:assistant":resource=R.drawable.mark_chat_circle_dots;break;
            case "control:settings":resource=R.drawable.mark_gear_six;break;
            case "control:inputs":resource=R.drawable.mark_monitor_play;break;
            case "control:apps":resource=R.drawable.mark_squares_four;break;
            default:resource=id.startsWith("dir:")?diskMark(label):R.drawable.mark_app_window;
        }
        Drawable mark=context.getDrawable(resource).mutate();mark.setTint(color);return mark;
    }

    /** 信号源没有可探测的身份，只能看名字：游戏机给手柄，其余给显示器。 */
    static int sourceMark(String label){
        String name=label==null?"":label;
        if(name.contains("游戏")||name.contains("PlayStation")||name.contains("Switch")
                ||name.contains("Xbox")||name.contains("手柄"))return R.drawable.mark_playstation;
        if(name.contains("Apple")||name.contains("苹果"))return R.drawable.mark_appletv;
        return R.drawable.mark_monitor_play;
    }

    private static int diskMark(String label){
        String name=label==null?"":label;
        if(name.contains("电影")||name.contains("动画")||name.contains("影视")||name.contains("视频"))
            return R.drawable.mark_video;
        if(name.contains("照片")||name.contains("相册")||name.contains("图片"))return R.drawable.mark_photo;
        return R.drawable.mark_folder;
    }
}
