package com.jktv.launcher;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Audio gets its own screen because the stock player is a video surface: it
 * plays the sound but leaves nothing on screen, so the listener cannot even tell
 * which track is running. This shows the file name, position and transport keys,
 * and walks the folder so an album plays through without leaving the remote.
 */
public final class AudioPlayerActivity extends Activity {
    static final String EXTRA_FILE = "file";
    private static final String[] EXTENSIONS = {"mp3", "flac", "wav", "aac", "m4a", "ogg", "ape", "wma", "mka"};
    private static final long SEEK_STEP = 10_000L;

    private final List<File> playlist = new ArrayList<>();
    private final Handler tick = new Handler(Looper.getMainLooper());
    private int index;
    private MediaPlayer player;
    private boolean paused, finished;
    private TextView title, state, elapsed, total, position;
    private ProgressBar bar;

    private final Runnable progress = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            int duration = 0, current = 0;
            try {
                duration = player.getDuration();
                current = player.getCurrentPosition();
            } catch (IllegalStateException gone) {
                return;
            }
            if (duration > 0) {
                bar.setProgress((int) (current * 1000L / duration));
                elapsed.setText(clock(current));
                total.setText(clock(duration));
            }
            if (!paused && !finished) tick.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        String path = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_FILE);
        if (TextUtils.isEmpty(path)) { finish(); return; }
        File target = new File(path);
        if (!collect(target)) { finish(); return; }
        chrome();
        play(index);
    }

    private boolean collect(File target) {
        File parent = target.getParentFile();
        File[] siblings = parent == null ? null : parent.listFiles();
        if (siblings != null) {
            for (File f : siblings) if (f.isFile() && isAudio(f.getName())) playlist.add(f);
        }
        if (playlist.isEmpty()) playlist.add(target);
        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(playlist, (a, b) -> collator.compare(a.getName(), b.getName()));
        for (int i = 0; i < playlist.size(); i++) {
            if (playlist.get(i).getAbsolutePath().equals(target.getAbsolutePath())) { index = i; break; }
        }
        return true;
    }

    private static boolean isAudio(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        for (String known : EXTENSIONS) if (known.equals(ext)) return true;
        return false;
    }

    private void chrome() {
        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(0xFF0B0D11);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams col = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        col.leftMargin = dp(96);
        col.rightMargin = dp(96);
        stage.addView(column, col);

        // A glyph would render as a tofu box: this firmware's font has no music note.
        android.widget.ImageView note = new android.widget.ImageView(this);
        note.setImageResource(R.drawable.note_music);
        note.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        column.addView(note, new LinearLayout.LayoutParams(dp(64), dp(64)));

        title = new TextView(this);
        title.setTextColor(0xFFF2F5FA);
        title.setTextSize(30);
        title.setIncludeFontPadding(false);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setLineSpacing(dp(6), 1f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(28);
        column.addView(title, tp);

        state = new TextView(this);
        state.setTextColor(0xB3FFFFFF);
        state.setTextSize(16);
        state.setIncludeFontPadding(false);
        state.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(14);
        column.addView(state, sp);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(0);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(4));
        bp.topMargin = dp(34);
        column.addView(bar, bp);

        LinearLayout clocks = new LinearLayout(this);
        clocks.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(10);
        column.addView(clocks, cp);

        elapsed = small();
        total = small();
        total.setGravity(Gravity.END);
        clocks.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1f));
        clocks.addView(total, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView hint = new TextView(this);
        hint.setText("确认 播放/暂停 · ← → 快退快进 · ↑ ↓ 上下一首 · 返回 退出");
        hint.setTextColor(0x66FFFFFF);
        hint.setTextSize(14);
        hint.setIncludeFontPadding(false);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hp.bottomMargin = dp(30);
        stage.addView(hint, hp);

        position = new TextView(this);
        position.setTextColor(0x59FFFFFF);
        position.setTextSize(13);
        position.setIncludeFontPadding(false);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        pp.topMargin = dp(26);
        pp.rightMargin = dp(40);
        stage.addView(position, pp);

        setContentView(stage);
    }

    private TextView small() {
        TextView t = new TextView(this);
        t.setTextColor(0x8AFFFFFF);
        t.setTextSize(14);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void play(int where) {
        release();
        if (playlist.isEmpty()) { finish(); return; }
        index = (where + playlist.size()) % playlist.size();
        File file = playlist.get(index);
        paused = false;
        finished = false;

        title.setText(file.getName());
        position.setText((index + 1) + " / " + playlist.size());
        state.setText("正在播放");
        elapsed.setText("--:--");
        total.setText("--:--");
        bar.setProgress(0);

        player = new MediaPlayer();
        try {
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> {
                finished = true;
                state.setText("播放完毕");
                bar.setProgress(1000);
                tick.removeCallbacks(progress);
                if (playlist.size() > 1) {
                    tick.postDelayed(() -> play(index + 1), 1200);
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                state.setText("这个格式无法播放（" + file.getName() + "）");
                release();
                return true;
            });
            player.prepare();
            player.start();
            tick.removeCallbacks(progress);
            tick.post(progress);
        } catch (Exception unsupported) {
            android.util.Log.w("TvHome.Audio", "cannot play " + file.getName(), unsupported);
            state.setText("这个格式无法播放，试试 mp3 或 m4a");
            release();
        }
    }

    private void toggle() {
        if (player == null) return;
        try {
            if (player.isPlaying()) {
                player.pause();
                paused = true;
                state.setText("已暂停");
                tick.removeCallbacks(progress);
            } else {
                player.start();
                paused = false;
                finished = false;
                state.setText("正在播放");
                tick.removeCallbacks(progress);
                tick.post(progress);
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private void seek(long delta) {
        if (player == null) return;
        try {
            int duration = player.getDuration();
            long target = player.getCurrentPosition() + delta;
            if (target < 0) target = 0;
            if (duration > 0 && target > duration) target = duration;
            player.seekTo((int) target);
            if (!paused) tick.post(progress);
        } catch (IllegalStateException ignored) {
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                toggle();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                seek(-SEEK_STEP);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seek(SEEK_STEP);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (playlist.size() > 1) play(index - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (playlist.size() > 1) play(index + 1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (playlist.size() > 1) play(index + 1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (playlist.size() > 1) play(index - 1);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private static String clock(int millis) {
        if (millis <= 0) return "--:--";
        int seconds = millis / 1000;
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void release() {
        tick.removeCallbacks(progress);
        if (player != null) {
            try { player.stop(); } catch (IllegalStateException ignored) { }
            player.release();
            player = null;
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (player != null && player.isPlaying()) {
            try { player.pause(); } catch (IllegalStateException ignored) { }
            paused = true;
            if (state != null) state.setText("已暂停");
            tick.removeCallbacks(progress);
        }
    }

    @Override protected void onDestroy() {
        release();
        super.onDestroy();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
