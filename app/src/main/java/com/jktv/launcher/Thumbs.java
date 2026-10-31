package com.jktv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cover art for folder tiles. A folder shows the first photo inside it, or a
 * still grabbed from its first video, which is how the stock gallery reads too.
 *
 * Decoding a large JPEG or pulling a frame off an NTFS volume is far too slow
 * for the main thread, so everything runs on a small pool and the bitmap is
 * cached on disk - the second visit to a folder is instant.
 */
final class Thumbs {
    interface Callback { void done(Bitmap cover, int count); }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private static final String[] PHOTO = {"jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"};
    private static final String[] MOVIE = {"mp4", "mkv", "avi", "ts", "m2ts", "mov", "wmv", "flv",
            "rmvb", "rm", "mpg", "mpeg", "m4v", "3gp", "webm", "vob", "iso"};

    private Thumbs() { }

    /** Resolves a cover for a folder (first photo / first video frame) or for a single file. */
    static void inspect(Context context, File entry, boolean folder, int width, int height, Callback cb) {
        final File cache = cacheFile(context, entry, folder, width, height);
        final String key = cache.getAbsolutePath();
        Bitmap hit = MEM.get(key);
        if (hit != null) {
            cb.done(hit, folder ? count(entry) : -1);
            return;
        }
        POOL.execute(() -> {
            Bitmap cover = null;
            int count = -1;
            try {
                if (folder) count = count(entry);
                cover = read(cache);
                if (cover == null) {
                    cover = folder ? folderCover(entry, width, height) : single(entry, width, height);
                    if (cover != null) write(cover, cache);
                }
            } catch (Throwable broken) {
                android.util.Log.w("TvHome.Thumbs", "no cover for " + entry, broken);
                cover = null;
            }
            final Bitmap result = cover;
            final int total = count;
            if (result != null) MEM.put(key, result);
            UI.post(() -> cb.done(result, total));
        });
    }

    /** Artwork first; a video still only when the folder holds no photo at all. */
    private static Bitmap folderCover(File dir, int width, int height) {
        List<File> photos = new ArrayList<>();
        List<File> movies = new ArrayList<>();
        collect(dir, 2, photos, movies);
        for (File photo : photos) {
            Bitmap cover = decode(photo, width, height);
            if (cover != null) return cover;
        }
        int tries = 0;
        for (File movie : movies) {
            if (tries++ >= 3) break;
            Bitmap cover = decode(movie, width, height);
            if (cover != null) return cover;
        }
        return null;
    }

    private static Bitmap single(File file, int width, int height) {
        return decode(file, width, height);
    }

    static int count(File dir) {
        File[] kids = dir.listFiles();
        return kids == null ? 0 : kids.length;
    }

    /**
     * Photos before videos, shallow before deep: covers built from artwork read
     * better than a random frame, and stopping early keeps a big disk quick.
     */
    private static void collect(File dir, int depth, List<File> photos, List<File> movies) {
        if (photos.size() >= 40) return;
        List<File> kids = children(dir);
        for (File f : kids) {
            if (f.isFile() && hasExt(f, PHOTO)) {
                photos.add(f);
                if (photos.size() >= 40) break;
            }
        }
        for (File f : kids) if (f.isFile() && hasExt(f, MOVIE) && movies.size() < 8) movies.add(f);
        if (depth <= 0) return;
        for (File f : kids) if (f.isDirectory()) collect(f, depth - 1, photos, movies);
    }

    private static List<File> children(File dir) {
        File[] all = dir.listFiles();
        if (all == null) return Collections.emptyList();
        List<File> kept = new ArrayList<>();
        for (File f : all) {
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (name.equalsIgnoreCase("$RECYCLE.BIN")) continue;
            if (name.equalsIgnoreCase("System Volume Information")) continue;
            kept.add(f);
        }
        Collections.sort(kept, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return kept;
    }

    private static boolean hasExt(File file, String[] list) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        for (String known : list) if (known.equals(ext)) return true;
        return false;
    }

    private static Bitmap decode(File file, int width, int height) {
        if (file == null || width <= 0 || height <= 0) return null;
        Bitmap raw = hasExt(file, PHOTO) ? decodePhoto(file, width, height) : decodeFrame(file);
        if (raw == null) return null;
        Bitmap cropped = crop(raw, width, height);
        if (cropped != raw) raw.recycle();
        return cropped;
    }

    private static Bitmap decodePhoto(File file, int width, int height) {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), probe);
        if (probe.outWidth <= 0 || probe.outHeight <= 0) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample(probe.outWidth, probe.outHeight, width * 2, height * 2);
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (OutOfMemoryError low) {
            return null;
        }
        return bitmap == null ? null : upright(bitmap, file);
    }

    /** Phone shots are stored sideways far more often than not. */
    private static Bitmap upright(Bitmap source, File file) {
        int turn = 0;
        try {
            android.media.ExifInterface exif = new android.media.ExifInterface(file.getAbsolutePath());
            int tag = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            if (tag == 6) turn = 90;
            else if (tag == 3) turn = 180;
            else if (tag == 8) turn = 270;
        } catch (Throwable unreadable) {
            return source;
        }
        if (turn == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(turn);
        try {
            Bitmap fixed = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            if (fixed != source) source.recycle();
            return fixed;
        } catch (Throwable failed) {
            return source;
        }
    }

    private static Bitmap decodeFrame(File file) {
        MediaMetadataRetriever reader = new MediaMetadataRetriever();
        try {
            reader.setDataSource(file.getAbsolutePath());
            long at = 2_000_000L;
            try {
                String duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long total = duration == null ? 0 : Long.parseLong(duration);
                // A fifth of the way in skips titles and black leader frames.
                if (total > 0) at = Math.min(total / 5, 120_000L) * 1000L;
            } catch (Throwable noMetadata) {
                at = 2_000_000L;
            }
            Bitmap frame = reader.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) frame = reader.getFrameAtTime(0);
            if (frame == null) android.util.Log.w("TvHome.Thumbs", "empty frame: " + file.getName());
            return frame;
        } catch (Throwable unsupported) {
            android.util.Log.w("TvHome.Thumbs", "no frame: " + file.getName() + " (" + unsupported + ")");
            return null;
        } finally {
            try { reader.release(); } catch (Throwable ignored) { }
        }
    }

    private static int sample(int width, int height, int wantW, int wantH) {
        int scale = 1;
        while (width / (scale * 2) >= wantW && height / (scale * 2) >= wantH) scale *= 2;
        return scale;
    }

    /** Centre-crops into the exact tile ratio so covers line up in the grid. */
    private static Bitmap crop(Bitmap source, int width, int height) {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        float scale = Math.max((float) width / source.getWidth(), (float) height / source.getHeight());
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate((width - source.getWidth() * scale) / 2f,
                (height - source.getHeight() * scale) / 2f);
        new Canvas(out).drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    private static File cacheFile(Context context, File entry, boolean folder, int width, int height) {
        File base = context.getExternalCacheDir();
        if (base == null) base = context.getCacheDir();
        File dir = new File(base, "covers");
        if (!dir.exists()) dir.mkdirs();
        String stamp = "v2|" + entry.getAbsolutePath() + "|" + entry.lastModified() + "|" + width + "x" + height;
        return new File(dir, digest(stamp) + ".img");
    }

    private static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("MD5").digest(text.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b));
            return out.toString();
        } catch (Exception fallback) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static Bitmap read(File file) {
        if (!file.isFile() || file.length() == 0) return null;
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable broken) {
            return null;
        }
    }

    private static void write(Bitmap bitmap, File file) {
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out);
        } catch (Throwable ignored) {
            if (file.exists()) file.delete();
        } finally {
            if (out != null) try { out.close(); } catch (Throwable ignored) { }
        }
    }
}
