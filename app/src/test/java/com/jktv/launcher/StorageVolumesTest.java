package com.jktv.launcher;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class StorageVolumesTest {
    private static final String APP = "/Android/data/com.jktv.launcher/files";

    @Test public void keepsRemovableVolumesSorted() {
        List<String> roots = StorageVolumes.rootsFrom(Arrays.asList(
                "/storage/9E2C13BF2C1390FF" + APP,
                "/storage/50E231E9" + APP));
        assertEquals(Arrays.asList("/storage/50E231E9", "/storage/9E2C13BF2C1390FF"), roots);
    }

    @Test public void internalStorageIsNotTreatedAsRemovable() {
        assertEquals(Collections.emptyList(),
                StorageVolumes.rootsFrom(Collections.singletonList("/storage/emulated/0" + APP)));
    }

    @Test public void ignoresPathsWithoutAndroidSegment() {
        assertEquals(Collections.singletonList("/storage/USB1"), StorageVolumes.rootsFrom(
                Arrays.asList("/data/data/com.jktv.launcher/files", null, "/storage/USB1" + APP)));
    }

    @Test public void duplicateVolumesAppearOnce() {
        assertEquals(Collections.singletonList("/storage/USB1"), StorageVolumes.rootsFrom(
                Arrays.asList("/storage/USB1" + APP, "/storage/USB1" + APP)));
    }

    @Test public void noMountedVolumesYieldsEmpty() {
        assertEquals(Collections.emptyList(), StorageVolumes.rootsFrom(Collections.emptyList()));
    }

    @Test public void labelsTellInternalFromRemovable() {
        assertEquals("内部存储", StorageVolumes.label("/storage/emulated/0"));
        assertEquals("移动存储", StorageVolumes.label("/storage/9E2C13BF2C1390FF"));
        assertEquals("存储", StorageVolumes.label(""));
    }

    @Test public void internalSubFolderUsesItsOwnName() {
        assertEquals("Movies", StorageVolumes.label("/storage/emulated/0/Movies"));
        assertEquals("DCIM", StorageVolumes.label("/storage/emulated/0/DCIM"));
    }

    /** 入口指到 U 盘里的子目录时，名字得是那个目录名，不然几条入口全叫"移动存储"。 */
    @Test public void removableSubFolderUsesItsOwnName() {
        assertEquals("动画", StorageVolumes.label("/storage/9E2C13BF2C1390FF/动画"));
        assertEquals("Movies", StorageVolumes.label("/storage/9E2C13BF2C1390FF/Movies"));
        // 卷根本身仍是介质类型；名字末尾多一个斜杠不该把空串当成目录名。
        assertEquals("移动存储", StorageVolumes.label("/storage/9E2C13BF2C1390FF"));
        assertEquals("移动存储", StorageVolumes.label("/storage/9E2C13BF2C1390FF/"));
    }

    @Test public void volumeRootsAreRecognised() {
        assertTrue(StorageVolumes.isVolumeRoot("/storage/emulated/0"));
        assertTrue(StorageVolumes.isVolumeRoot("/storage/9E2C13BF2C1390FF"));
        assertFalse(StorageVolumes.isVolumeRoot("/storage/9E2C13BF2C1390FF/动画"));
        assertFalse(StorageVolumes.isVolumeRoot("/storage/emulated/0/Movies"));
        assertFalse(StorageVolumes.isVolumeRoot("/storage"));
        assertFalse(StorageVolumes.isVolumeRoot(""));
    }

    @Test public void volumeOfFindsTheOwningRoot() {
        List<String> removable = Arrays.asList("/storage/50E231E9", "/storage/9E2C13BF2C1390FF");
        String primary = "/storage/emulated/0";
        assertEquals("/storage/9E2C13BF2C1390FF",
                StorageVolumes.volumeOf("/storage/9E2C13BF2C1390FF/动画", removable, primary));
        assertEquals(primary, StorageVolumes.volumeOf(primary + "/Movies", removable, primary));
        // 前缀相同的另一个卷不能被误认成同一块盘；认不出来就原样返回。
        assertEquals("/storage/9E2C13BF2C1390FF2/x",
                StorageVolumes.volumeOf("/storage/9E2C13BF2C1390FF2/x", removable, primary));
    }
}
