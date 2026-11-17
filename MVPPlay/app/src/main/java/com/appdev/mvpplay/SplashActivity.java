package com.appdev.mvpplay;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.content.Intent;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.view.WindowManager;
import android.widget.VideoView;
import java.util.Random;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends BaseActivity {

    private static final String[] INTROS = {
            "mvpplay_intro_d_particle_assemble_sfx.mp4",
            "mvpplay_intro_c_equalizer_burst_sfx.mp4",
            "mvpplay_intro_f_neon_flicker_sfx.mp4",
            "mvpplay_intro_h_spotlight_rays_sfx.mp4",
            "mvpplay_intro_g_glitch_lockin_sfx.mp4",
            "mvpplay_ultimate_intro.mp4",
            "mvpplay_intro_glass.mp4"
    };

    private VideoView videoView;
    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

// ---- NEW CODE STARTS HERE ----
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // Let the video draw behind the camera notch
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
// ---- NEW CODE ENDS HERE ----

        videoView = findViewById(R.id.introVideo);

        String file = pickRandomIntro();
        new Thread(() -> {
            File out = copyIntroToCache(file);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (out == null) {
                    goNext();   // couldn't read the video, don't get stuck
                    return;
                }
                videoView.setVideoPath(out.getAbsolutePath());
            });
        }).start();

        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);

            // Scale video so it fills the whole screen (center crop)
            float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
            float screenRatio = videoView.getWidth() / (float) videoView.getHeight();
            float scale = Math.max(screenRatio / videoRatio, videoRatio / screenRatio);
            videoView.setScaleX(scale);
            videoView.setScaleY(scale);

            videoView.start();
        });
        videoView.setOnCompletionListener(mp -> goNext());
        videoView.setOnErrorListener((mp, what, extra) -> {
            goNext();   // never get stuck if a video fails
            return true;
        });

        // Tap anywhere to skip
        findViewById(R.id.root).setOnClickListener(v -> goNext());
    }

    // copies the intro from assets/intros into the cache so VideoView can play it
    private File copyIntroToCache(String name) {
        File out = new File(getCacheDir(), name);
        if (out.exists() && out.length() > 0) return out;   // already copied before
        try (InputStream in = getAssets().open("intros/" + name);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            return out;
        } catch (IOException e) {
            out.delete();
            return null;
        }
    }

    /** Random pick, avoiding the same intro twice in a row. */
    private String pickRandomIntro() {
        SharedPreferences prefs = getSharedPreferences("intro_prefs", MODE_PRIVATE);
        int last = prefs.getInt("last_intro", -1);
        int index;
        Random random = new Random();
        do {
            index = random.nextInt(INTROS.length);
        } while (INTROS.length > 1 && index == last);
        prefs.edit().putInt("last_intro", index).apply();
        return INTROS[index];
    }

    private void goNext() {
        if (navigated) return;
        navigated = true;
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) videoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null && !navigated && !videoView.isPlaying()) videoView.start();
    }
    @Override
    protected boolean showThemeDecor() {
        return false;   // no snow, lights or glow during the intro
    }
}