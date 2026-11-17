package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Random;

// a small round cover in the middle with tiny music notes drifting up while the song plays.
// It is see-through everywhere else, so the background effect stays visible.
public class NotesView extends View {

    private static final int MAX_NOTES = 14;
    private static final float SPAWN_MS = 420f;   // a new note about every 0.4 seconds

    private static class Note {
        float x0, y0;        // where it starts (on the top edge of the cover)
        float rise;          // how far it climbs
        float sway;          // size of the sideways wave
        float swayPhase;
        float size;
        float rot;           // starting tilt in degrees
        float age, life;     // milliseconds
        boolean useAccent;   // accent color or white
    }

    private final ArrayList<Note> notes = new ArrayList<>();
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();

    private static final int NOTE_PX = 96;   // size of the ready-made note bitmaps
    private Drawable noteIcon;               // only used to build the bitmaps
    private Bitmap noteWhite, noteAccent;
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap cover;
    private BitmapShader coverShader;
    private int fallbackColor = 0xFF7C5CE6;
    private int accent = 0xFFA78BFA;

    private boolean playing = false;
    private boolean framesRunning = false;
    private long lastFrame = 0L;
    private float spawnTimer = 0f;
    private float clock = 0f;   // for the gentle breathing of the cover
    private float live = 0f;    // 0 = paused, 1 = playing (eases between the two)

    public NotesView(Context context) {
        super(context);
        init(context);
    }

    public NotesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        Drawable d = ContextCompat.getDrawable(context, R.drawable.ic_music_note);
        if (d != null) {
            noteIcon = d.mutate();
            noteWhite = renderNote(0xFFFFFFFF);
            noteAccent = renderNote(accent);
        }
    }

    // draws the note icon once into a bitmap in the given color
    private Bitmap renderNote(int color) {
        Bitmap b = Bitmap.createBitmap(NOTE_PX, NOTE_PX, Bitmap.Config.ARGB_8888);
        noteIcon.setTint(color);
        noteIcon.setBounds(0, 0, NOTE_PX, NOTE_PX);
        noteIcon.draw(new Canvas(b));
        return b;
    }

    // the song picture shown in the small round cover
    public void setCover(Bitmap bitmap, int fallback) {
        cover = bitmap;
        fallbackColor = fallback;
        coverShader = (bitmap != null)
                ? new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                : null;
        invalidate();
    }

    public void setAccent(int color) {
        accent = color;
        if (noteIcon != null) noteAccent = renderNote(color);   // rebuild with the new color
        invalidate();
    }

    // true: notes start floating. false: no new notes, the ones in the air finish their trip.
    public void setPlaying(boolean play) {
        if (play == playing) return;
        playing = play;
        if (play) spawnTimer = SPAWN_MS;   // the first note comes right away
        startFrames();
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = (lastFrame == 0L) ? 0f : Math.min(now - lastFrame, 64);
            lastFrame = now;
            step(dt);
            invalidate();
            if (playing || !notes.isEmpty() || live > 0.01f) {
                postOnAnimation(this);
            } else {
                framesRunning = false;
                lastFrame = 0L;
            }
        }
    };

    private void startFrames() {
        if (framesRunning) return;
        framesRunning = true;
        lastFrame = 0L;
        postOnAnimation(frame);
    }

    private void step(float dt) {
        live += ((playing ? 1f : 0f) - live) * Math.min(1f, dt / 250f);
        if (playing) {
            clock += dt;
            spawnTimer += dt;
            if (spawnTimer >= SPAWN_MS && notes.size() < MAX_NOTES) {
                spawnTimer = 0f;
                spawn();
            }
        }
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            n.age += dt;
            if (n.age >= n.life) notes.remove(i);
        }
    }

    private void spawn() {
        float s = Math.min(getWidth(), getHeight());
        if (s <= 0) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = s * 0.30f;

        // a random spot on the top half of the cover's edge
        double ang = Math.toRadians(-160 + rnd.nextInt(121));
        Note n = new Note();
        n.x0 = cx + (float) Math.cos(ang) * r;
        n.y0 = cy + (float) Math.sin(ang) * r;
        n.size = s * (0.09f + rnd.nextFloat() * 0.05f);
        float room = Math.max(s * 0.1f, n.y0 - n.size);   // never float out of the top
        n.rise = Math.min(s * (0.35f + rnd.nextFloat() * 0.2f), room);
        n.sway = s * (0.03f + rnd.nextFloat() * 0.04f);
        n.swayPhase = rnd.nextFloat() * 6.28f;
        n.rot = -20 + rnd.nextInt(41);
        n.life = 3200 + rnd.nextInt(1400);
        n.useAccent = rnd.nextBoolean();
        notes.add(n);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (playing || !notes.isEmpty()) startFrames();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        framesRunning = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight());
        if (s <= 0) return;
        float d = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = s * 0.30f * (1f + 0.025f * (float) Math.sin(clock / 420f) * live);

        // thin ring around the cover
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * d);
        paint.setColor((accent & 0x00FFFFFF) | 0x88000000);
        canvas.drawCircle(cx, cy, r + 6 * d, paint);

        // the round cover
        if (coverShader != null) {
            float bw = cover.getWidth();
            float bh = cover.getHeight();
            float scale = Math.max(2 * r / bw, 2 * r / bh);
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate(cx - bw * scale / 2f, cy - bh * scale / 2f);
            coverShader.setLocalMatrix(matrix);
            coverPaint.setShader(coverShader);
        } else {
            coverPaint.setShader(null);
            coverPaint.setColor(fallbackColor);
        }
        canvas.drawCircle(cx, cy, r, coverPaint);

        // the floating notes
        if (noteWhite == null || noteAccent == null) return;
        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            float t = n.age / n.life;                  // 0..1 through its trip
            float p = 1f - (1f - t) * (1f - t);        // fast at first, slower near the top
            float x = n.x0 + (float) Math.sin(n.swayPhase + t * 5f) * n.sway;
            float y = n.y0 - n.rise * p;
            float a = t < 0.15f ? t / 0.15f : (t > 0.6f ? (1f - t) / 0.4f : 1f);
            float k = n.size / NOTE_PX;

            notePaint.setAlpha((int) (Math.max(0f, Math.min(1f, a)) * 230));
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(n.rot + t * 25f);
            canvas.scale(k, k);
            canvas.drawBitmap(n.useAccent ? noteAccent : noteWhite,
                    -NOTE_PX / 2f, -NOTE_PX / 2f, notePaint);
            canvas.restore();
        }
    }
}