package com.jktv.launcher;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * 信号源图标的挑选规则。
 *
 * `HDMI 1` 这种自动名看不出接的是什么，所以图标只能跟着用户起的名字走。
 * 之前 hdmi:1 固定是 Apple TV 的标，接了机顶盒也照旧，看着不对。
 */
public class HomeIconsTest {
    @Test public void gameNamesGetTheController() {
        assertEquals(R.drawable.mark_playstation, HomeIcons.sourceMark("游戏机"));
        assertEquals(R.drawable.mark_playstation, HomeIcons.sourceMark("Switch"));
        assertEquals(R.drawable.mark_playstation, HomeIcons.sourceMark("Xbox"));
    }

    @Test public void appleBoxKeepsItsOwnMark() {
        assertEquals(R.drawable.mark_appletv, HomeIcons.sourceMark("Apple TV"));
        assertEquals(R.drawable.mark_appletv, HomeIcons.sourceMark("苹果盒子"));
    }

    @Test public void everythingElseIsAMonitor() {
        assertEquals(R.drawable.mark_monitor_play, HomeIcons.sourceMark("机顶盒"));
        assertEquals(R.drawable.mark_monitor_play, HomeIcons.sourceMark("HDMI 1"));
        assertEquals(R.drawable.mark_monitor_play, HomeIcons.sourceMark(""));
        assertEquals(R.drawable.mark_monitor_play, HomeIcons.sourceMark(null));
    }
}
