package com.jktv.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 入口名的取值规则。
 *
 * 自动名（"移动存储 1"）一旦被当成用户起的名字存下来，换盘或改顺序之后首页
 * 就会显示错的名字，所以这两条规则是升级迁移的关键。
 */
public class HomeItemsNameTest {
    @Test public void customNameWins() {
        assertEquals("电影动画", HomeItems.displayName("电影动画", "移动存储 1"));
    }

    @Test public void fallsBackToAutomaticName() {
        assertEquals("移动存储 1", HomeItems.displayName(null, "移动存储 1"));
        assertEquals("内部存储", HomeItems.displayName("  ", "内部存储"));
    }

    @Test public void numberedVolumeNamesAreAutomatic() {
        assertTrue(HomeItems.isAutoLabel("移动存储 1", "移动存储 1"));
        assertTrue(HomeItems.isAutoLabel("移动存储 2", "移动存储 1"));
        assertTrue(HomeItems.isAutoLabel("内部存储", "移动存储 1"));
        assertTrue(HomeItems.isAutoLabel("存储", "移动存储 1"));
    }

    @Test public void emptyValueIsAutomatic() {
        assertTrue(HomeItems.isAutoLabel("", "移动存储 1"));
        assertTrue(HomeItems.isAutoLabel("   ", "移动存储 1"));
        assertTrue(HomeItems.isAutoLabel(null, "移动存储 1"));
    }

    @Test public void plainFolderNamesSurviveMigration() {
        // 内部存储的普通子目录会被自动命名为目录名，它同样是"自动名"。
        assertTrue(HomeItems.isAutoLabel("Movies", "Movies"));
        // 用户起的名字不能被当成自动名抹掉。
        assertFalse(HomeItems.isAutoLabel("电影动画", "移动存储 1"));
        assertFalse(HomeItems.isAutoLabel("家庭照片", "移动存储 1"));
        assertFalse(HomeItems.isAutoLabel("电影", "移动存储 1"));
    }

    @Test public void pageNamesResolveBackToEntryIds() {
        assertEquals("dir:/storage/9E2C13BF2C1390FF", HomeSettings.entryOf("rename:dir:/storage/9E2C13BF2C1390FF"));
        assertEquals("dir:/storage/9E2C13BF2C1390FF", HomeSettings.entryOf("pick:dir:/storage/9E2C13BF2C1390FF"));
        assertEquals("com.mitv.gallery", HomeSettings.entryOf("rename:com.mitv.gallery"));
    }
}
