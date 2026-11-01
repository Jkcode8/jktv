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
}
