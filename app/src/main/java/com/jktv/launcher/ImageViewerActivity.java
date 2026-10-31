package com.jktv.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen photo viewer with left/right paging inside the current folder.
 * No stock app on this firmware accepts an image intent, so the entry point has
 * to render the picture itself.
 */
public final class ImageViewerActivity extends Activity {
    static final String EXTRA_FILE = "file";
    private static final String[] EXTENSIONS = {"jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"};

    private final List<File> gallery = new ArrayList<>();
    private int index;
    private ImageView picture;
    private TextView caption;
    private Bitmap shown;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        String path = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_FILE);
        if (TextUtils.isEmpty(path)) { finish(); return; }
        File target = new File(path);
        if (!collect(target)) { finish(); return; }
        chrome();
        show(index);
    }

    private boolean collect(File target) {
        File parent = target.getParentFile();
        File[] siblings = parent == null ? null : parent.listFiles();
        if (siblings != null) {
            for (File f : siblings) if (f.isFile() && isImage(f.getName())) gallery.add(f);
        }
        if (gallery.isEmpty()) gallery.add(target);
        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(gallery, (a, b) -> collator.compare(a.getName(), b.getName()));
        for (int i = 0; i < gallery.size(); i++) {
            if (gallery.get(i).getAbsolutePath().equals(target.getAbsolutePath())) { index = i; break; }
        }
        return true;
    }

    private static boolean isImage(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        for (String known : EXTENSIONS) if (known.equals(ext)) return true;
        return false;
    }

    private void chrome() {
        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(0xFF000000);

        picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stage.addView(picture, new FrameLayout.LayoutParams(-1, -1));

        caption = new TextView(this);
        caption.setTextColor(0xB3FFFFFF);
        caption.setTextSize(16);
        caption.setIncludeFontPadding(false);
        caption.setShadowLayer(dp(6), 0, dp(1), 0xCC000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.START);
        cp.leftMargin = dp(40);
        cp.topMargin = dp(28);
        stage.addView(caption, cp);

        TextView hint = new TextView(this);
        hint.setText("← → 翻看 · 返回 回列表");
        hint.setTextColor(0x66FFFFFF);
        hint.setTextSize(14);
        hint.setIncludeFontPadding(false);
        hint.setShadowLayer(dp(6), 0, dp(1), 0xCC000000);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
        hp.leftMargin = dp(40);
        hp.bottomMargin = dp(24);
        stage.addView(hint, hp);

        setContentView(stage);
    }

    private void show(int position) {
        if (gallery.isEmpty()) return;
        index = (position + gallery.size()) % gallery.size();
        File file = gallery.get(index);
        Bitmap bitmap;
        try {
            bitmap = orient(file, decode(file));
        } catch (OutOfMemoryError | RuntimeException tooBig) {
            android.util.Log.w("TvHome.Photos", "cannot decode " + file.getName(), tooBig);
            bitmap = null;
        }
        picture.setImageBitmap(bitmap);
        if (shown != null && shown != bitmap) shown.recycle();
        shown = bitmap;
        caption.setText(file.getName() + "    " + (index + 1) + " / " + gallery.size());
    }

    private Bitmap decode(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        DisplayMetrics screen = getResources().getDisplayMetrics();
        int scale = 1;
        while (bounds.outWidth > 0 && bounds.outHeight > 0
                && bounds.outWidth / (scale * 2) >= screen.widthPixels
                && bounds.outHeight / (scale * 2) >= screen.heightPixels) {
            scale *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = scale;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private Bitmap orient(File file, Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int value = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees = 0;
            if (value == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
            else if (value == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
            else if (value == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            if (degrees == 0) return bitmap;
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (Exception unreadable) {
            return bitmap;
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (gallery.size() > 1) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                show(index - 1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                show(index + 1);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        if (shown != null) { shown.recycle(); shown = null; }
        super.onDestroy();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
