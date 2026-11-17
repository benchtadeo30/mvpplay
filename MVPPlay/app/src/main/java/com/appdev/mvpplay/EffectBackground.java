package com.appdev.mvpplay;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.graphics.BitmapFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// the moving picture (GIF or silent mp4) behind the Now Playing screen
public class EffectBackground {

    public static final int KIND_GIF = 0, KIND_VIDEO = 1, KIND_IMAGE = 2;

    // one effect: built-in ones are files in res/raw, yours are saved files
    public static final class Fx {
        public final String key;
        public final String label;
        public final int resId;       // built-in: the file in res/raw (0 for yours)
        public final String path;     // yours: the saved file (null for built-in)
        public final int kind;        // GIF, video or still image
        public final boolean video;

        Fx(String key, String label, int resId, boolean video) {
            this(key, label, resId, null, video ? KIND_VIDEO : KIND_GIF);
        }

        Fx(String key, String label, int resId, String path, int kind) {
            this.key = key;
            this.label = label;
            this.resId = resId;
            this.path = path;
            this.kind = kind;
            this.video = (kind == KIND_VIDEO);
        }

        public boolean isCustom() {
            return path != null;
        }
    }

    // an effect the user added
    public static Fx custom(String key, String title, String path, int kind) {
        return new Fx(key, title, 0, path, kind);
    }

    // the "Add your own" tile in the picker (not a real effect)
    public static final Fx ADD = new Fx("__add", "Add your own", 0, false);

    // to add another effect: put the file in res/raw and add one line here
    public static final Fx[] ALL = {
            new Fx("wave", "Neon spiral", R.raw.fx_wave, true),
            new Fx("lights", "Rising lights", R.raw.fx_xponentialdesign, false),
            new Fx("space", "Space", R.raw.fx_space, true),
            new Fx("dance", "Dance", R.raw.fx_dance, false),
            new Fx("rain", "Rainy night", R.raw.fx_rain, false),
            new Fx("sky", "Blue sky", R.raw.fx_sky, true),
            new Fx("moon", "Night sky moon", R.raw.fx_moon, true),
            new Fx("liquid", "Pink liquid", R.raw.fx_liquid, true),
            new Fx("sunset", "Retro sunset", R.raw.fx_sunset, true),
            new Fx("swirl", "Color swirl", R.raw.fx_swirl, true),
            new Fx("clouds", "Sun through clouds", R.raw.fx_clouds, true),
            new Fx("redella", "Red and Stella", R.raw.fx_redella, true),
            new Fx("chibeach", "Chihuahua beach", R.raw.fx_chihuahua_beach, true)
    };

    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // how much the effect is shaded so the text stays readable:
    // almost clear at the top, stronger toward the bottom (where the text is)
    private static final int[] SCRIM_LIGHT = {0x1AFFFFFF, 0x2EFFFFFF, 0x8CFFFFFF, 0xB3FFFFFF};
    private static final int[] SCRIM_DARK = {0x4D000000, 0x66000000, 0x99000000, 0xB3000000};
    private static final float[] SCRIM_POS = {0f, 0.45f, 0.7f, 1f};

    private static final int GIF_MAX_WIDTH = 720;      // was 540: sharper GIFs
    private static final int IMAGE_MAX_WIDTH = 1080;   // was 720: sharper pictures

    // built-in effects first, then the ones the user added
    public static List<Fx> all(Context c) {
        List<Fx> list = new ArrayList<>(Arrays.asList(ALL));
        list.addAll(CustomEffectStore.load(c));
        return list;
    }

    public static Fx byKey(Context c, String key) {
        for (Fx f : all(c)) if (f.key.equals(key)) return f;
        return null;
    }

    // very low-memory phones skip the effects
    public static boolean isLowRam(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        return am != null && am.isLowRamDevice();
    }

    // GIFs need Android 9 or newer, the mp4 works everywhere
    public static boolean canPlay(Fx fx) {
        return fx.kind != KIND_GIF || Build.VERSION.SDK_INT >= 28;
    }

    // a small picture for the picker (call it on a background thread)
    public static Bitmap thumbnail(Context c, Fx fx, int widthPx) {
        try {
            if (fx.kind == KIND_VIDEO) return videoFrame(c, fx, widthPx);
            if (fx.isCustom()) return decodeFile(fx.path, widthPx);   // GIF or picture: first frame
            if (Build.VERSION.SDK_INT >= 28) return gifFrame(c, fx, widthPx);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Bitmap videoFrame(Context c, Fx fx, int widthPx) throws Exception {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            if (fx.isCustom()) {
                r.setDataSource(fx.path);
            } else {
                AssetFileDescriptor afd = c.getResources().openRawResourceFd(fx.resId);
                r.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            }
            Bitmap b = r.getFrameAtTime(0);
            if (b == null) b = r.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (b == null) b = r.getFrameAtTime(2000000, MediaMetadataRetriever.OPTION_CLOSEST);
            if (b == null) return null;
            int h = Math.max(1, Math.round(b.getHeight() * (widthPx / (float) b.getWidth())));
            return Bitmap.createScaledBitmap(b, widthPx, h, true);
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    // reads a picture file shrunk to about maxWidth, so big photos do not use much memory
    private static Bitmap decodeFile(String path, int maxWidth) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, options);
    }

    @RequiresApi(28)
    private static Bitmap gifFrame(Context c, Fx fx, int widthPx) throws Exception {
        ImageDecoder.Source src = ImageDecoder.createSource(c.getResources(), fx.resId);
        return ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            float scale = Math.min(1f, widthPx / (float) Math.max(1, w));
            decoder.setTargetSize(Math.max(1, Math.round(w * scale)),
                    Math.max(1, Math.round(h * scale)));
        });
    }

    // ---------- the live background ----------
    private final Activity activity;
    private final View npRoot;
    private FrameLayout container;
    private ImageView image;
    private TextureView texture;
    private View scrim;
    private MediaPlayer player;
    private Surface surface;
    private String playingKey = null;
    private int token = 0;      // a late answer from an old effect is ignored
    private int vw = 0, vh = 0; // the video size, for filling the screen

    public EffectBackground(Activity activity, View npRoot) {
        this.activity = activity;
        this.npRoot = npRoot;
    }

    // starts the effect behind the screen (does nothing if it is already playing)
    public void show(Fx fx) {
        if (fx.key.equals(playingKey) && container != null) {
            applyScrim();   // the theme may have changed since the effect started
            return;
        }
        stop();
        ensureContainer();
        playingKey = fx.key;
        final int my = ++token;
        applyScrim();
        npRoot.setBackgroundColor(Color.TRANSPARENT);   // so the effect shows through
        if (fx.kind == KIND_VIDEO) {
            playVideo(fx);
        } else if (fx.kind == KIND_IMAGE) {
            showImage(fx, my);
        } else if (Build.VERSION.SDK_INT >= 28) {
            playGif(fx, my);
        }
    }

    // stops everything and puts the normal background back
    public void hide() {
        stop();
        if (container != null) {
            ViewGroup parent = (ViewGroup) container.getParent();
            if (parent != null) parent.removeView(container);
            container = null;
            image = null;
            texture = null;
            scrim = null;
            npRoot.setBackgroundColor(ContextCompat.getColor(activity, R.color.home_bg));
        }
    }

    // paints the shade layer: light themes get a white shade, the others a dark one
    private void applyScrim() {
        if (scrim == null) return;
        final int[] colors = ThemeStore.isLight(activity) ? SCRIM_LIGHT : SCRIM_DARK;
        PaintDrawable shade = new PaintDrawable();
        shade.setShape(new RectShape());
        shade.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(0, 0, 0, height, colors, SCRIM_POS,
                        Shader.TileMode.CLAMP);
            }
        });
        scrim.setBackground(shade);
    }

    // the layer sits just under the screen, above any theme backdrop (like Glass)
    private void ensureContainer() {
        if (container != null) return;
        ViewGroup parent = (ViewGroup) npRoot.getParent();

        container = new FrameLayout(activity);
        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        container.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        texture = new TextureView(activity);
        texture.setVisibility(View.GONE);
        container.addView(texture, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // a soft shade so the text stays easy to read
        scrim = new View(activity);
        container.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        parent.addView(container, parent.indexOfChild(npRoot),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------- still picture ----------
    private void showImage(final Fx fx, final int my) {
        texture.setVisibility(View.GONE);
        image.setVisibility(View.VISIBLE);
        IO.execute(() -> {
            final Bitmap b = decodeFile(fx.path, IMAGE_MAX_WIDTH);
            MAIN.post(() -> {
                if (my != token || container == null || b == null) return;
                image.setImageBitmap(b);
            });
        });
    }

    // ---------- GIF ----------
    @RequiresApi(28)
    private void playGif(final Fx fx, final int my) {
        texture.setVisibility(View.GONE);
        image.setVisibility(View.VISIBLE);
        IO.execute(() -> {
            Drawable d = null;
            try {
                ImageDecoder.Source src = fx.isCustom()
                        ? ImageDecoder.createSource(new File(fx.path))
                        : ImageDecoder.createSource(activity.getResources(), fx.resId);
                d = ImageDecoder.decodeDrawable(src, (decoder, info, s) -> {
                    // big GIFs are shrunk while loading to save memory
                    int w = info.getSize().getWidth();
                    int h = info.getSize().getHeight();
                    float scale = Math.min(1f, GIF_MAX_WIDTH / (float) Math.max(1, w));
                    decoder.setTargetSize(Math.max(1, Math.round(w * scale)),
                            Math.max(1, Math.round(h * scale)));
                });
            } catch (Throwable ignored) {
            }
            final Drawable result = d;
            MAIN.post(() -> {
                if (my != token || container == null || result == null) return;
                image.setImageDrawable(result);
                startGif(result);
            });
        });
    }

    @RequiresApi(28)
    private void startGif(Drawable d) {
        if (d instanceof AnimatedImageDrawable) {
            AnimatedImageDrawable a = (AnimatedImageDrawable) d;
            a.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
            a.start();
        }
    }

    @RequiresApi(28)
    private void stopGif() {
        if (image != null && image.getDrawable() instanceof AnimatedImageDrawable) {
            ((AnimatedImageDrawable) image.getDrawable()).stop();
        }
    }

    // ---------- mp4 (silent, looping) ----------
    private void playVideo(final Fx fx) {
        image.setVisibility(View.GONE);
        texture.setVisibility(View.VISIBLE);
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                startPlayer(fx, st);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                fit();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        if (texture.isAvailable()) startPlayer(fx, texture.getSurfaceTexture());
    }

    private void startPlayer(Fx fx, SurfaceTexture st) {
        if (player != null || st == null) return;
        try {
            MediaPlayer mp = new MediaPlayer();
            if (fx.isCustom()) {
                mp.setDataSource(fx.path);
            } else {
                AssetFileDescriptor afd = activity.getResources().openRawResourceFd(fx.resId);
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            }
            surface = new Surface(st);
            mp.setSurface(surface);
            mp.setLooping(true);
            mp.setVolume(0f, 0f);   // silent: the music is the only sound
            mp.setOnVideoSizeChangedListener((m, w, h) -> {
                vw = w;
                vh = h;
                fit();
            });
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.prepareAsync();
            player = mp;
        } catch (Exception e) {
            player = null;
        }
    }

    // fills the whole screen without stretching the video (it is cropped a little)
    private void fit() {
        if (texture == null || vw == 0 || vh == 0) return;
        float w = texture.getWidth();
        float h = texture.getHeight();
        if (w == 0 || h == 0) return;
        float videoAspect = (float) vw / vh;
        float viewAspect = w / h;
        float sx = 1f;
        float sy = 1f;
        if (videoAspect > viewAspect) sx = videoAspect / viewAspect;
        else sy = viewAspect / videoAspect;
        Matrix m = new Matrix();
        m.setScale(sx, sy, w / 2f, h / 2f);
        texture.setTransform(m);
    }

    private void stop() {
        token++;
        playingKey = null;
        vw = 0;
        vh = 0;
        if (Build.VERSION.SDK_INT >= 28) stopGif();
        if (image != null) image.setImageDrawable(null);
        if (texture != null) texture.setSurfaceTextureListener(null);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }
}