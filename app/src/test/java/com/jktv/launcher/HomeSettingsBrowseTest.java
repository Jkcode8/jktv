package com.jktv.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 目录浏览页的页面名解析。
 *
 * 页面名要同时带"确认时作用于谁"和"现在在哪个目录"，而目录路径本身含斜杠和冒号，
 * 所以用竖线分隔。这两段一旦解析错，就会把入口换到别的目录上，属于会静默丢配置的错误。
 */
public class HomeSettingsBrowseTest {
    private static final String VOLUME = "/storage/9E2C13BF2C1390FF";

    @Test public void entryTargetAndPathSurviveRoundTrip() {
        String page = HomeSettings.browseName("dir:" + VOLUME, VOLUME + "/动画");
        assertEquals("dir:" + VOLUME, HomeSettings.browseTarget(page));
        assertEquals(VOLUME + "/动画", HomeSettings.browsePath(page));
    }

    @Test public void addingFolderKeepsAddTarget() {
        String page = HomeSettings.browseName("add", VOLUME);
        assertEquals("add", HomeSettings.browseTarget(page));
        assertEquals(VOLUME, HomeSettings.browsePath(page));
    }

    @Test public void nestedPathKeepsEverySlash() {
        String deep = VOLUME + "/动画/2024/合集";
        assertEquals(deep, HomeSettings.browsePath(HomeSettings.browseName("add", deep)));
    }

    @Test public void targetIsNotTakenFromThePath() {
        // 路径里的冒号（例如 Windows 名字）不应把目标截歪。
        String page = HomeSettings.browseName("dir:" + VOLUME, VOLUME + "/A:B");
        assertEquals("dir:" + VOLUME, HomeSettings.browseTarget(page));
        assertEquals(VOLUME + "/A:B", HomeSettings.browsePath(page));
    }

    @Test public void missingPathYieldsEmpty() {
        assertEquals("", HomeSettings.browsePath("browse:add"));
    }
}
