package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

// a cassette tape: the song picture and title on the label, two reels that spin while playing.
// The tape moves from the left reel to the right reel as the song goes on.
public class CassetteView extends View {

    private static final float SPIN_MS = 1800f;   // one full turn of the reels at full speed

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF coverRect = new RectF();
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();

    private Bitmap cover;
    private BitmapShader coverShader;
    private int fallbackColor = 0xFF7C5CE6;
    private int accent = 0xFFA78BFA;
    private String title = "";
    private String artist = "";

    private float angle = 0f;          // how far the reels have turned
    private float speed = 0f;          // 0 = stopped, 1 = full speed
    private float playProgress = 0f;   // 0..1 through the song
    private boolean playing = false;
    private boolean framesRunning = false;
    private long lastFrame = 0L;

    public CassetteView(Context context) {
        super(context);
    }

    public CassetteView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

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
        invalidate();
    }

    // the text written on the label
    public void setTexts(String songTitle, String songArtist) {
        title = songTitle == null ? "" : songTitle;
        artist = songArtist == null ? "" : songArtist;
        invalidate();
    }

    // how far the song is (0..1): moves the tape from the left reel to the right reel
    public void setPlayProgress(float fraction) {
        playProgress = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    public void setPlaying(boolean play) {
        if (play == playing) return;
        playing = play;
        startFrames();
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            float dt = (lastFrame == 0L) ? 0f : Math.min(now - lastFrame, 64);
            lastFrame = now;
            speed += ((playing ? 1f : 0f) - speed) * Math.min(1f, dt / 350f);
            angle = (angle + dt * 360f / SPIN_MS * speed) % 360f;
            invalidate();
            if (playing || speed > 0.01f) {
                postOnAnimation(this);
            } else {
                framesRunning = false;
                lastFrame = 0L;
                speed = 0f;
            }
        }
    };

    private void startFrames() {
        if (framesRunning) return;
        framesRunning = true;
        lastFrame = 0L;
        postOnAnimation(frame);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (playing) startFrames();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        framesRunning = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float d = getResources().getDisplayMetrics().density;

        // the cassette keeps a 1.6 : 1 shape and sits in the middle
        float bw = Math.min(w * 0.96f, h * 1.6f);
        float bh = bw / 1.6f;
        float cx = w / 2f;
        float L = cx - bw / 2f;
        float R = cx + bw / 2f;
        float T = (h - bh) / 2f;
        float B = T + bh;

        // ----- body -----
        rect.set(L, T, R, B);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF23232B);
        canvas.drawRoundRect(rect, bh * 0.07f, bh * 0.07f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * d);
        paint.setColor(0xFF3C3C48);
        canvas.drawRoundRect(rect, bh * 0.07f, bh * 0.07f, paint);

        // four screws
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF4A4A57);
        float sr = bh * 0.022f;
        float inset = bh * 0.06f;
        canvas.drawCircle(L + inset, T + inset, sr, paint);
        canvas.drawCircle(R - inset, T + inset, sr, paint);
        canvas.drawCircle(L + inset, B - inset, sr, paint);
        canvas.drawCircle(R - inset, B - inset, sr, paint);

        // ----- label -----
        float lt = T + bh * 0.07f;
        float lb = T + bh * 0.44f;
        float ll = L + bw * 0.07f;
        float lr = R - bw * 0.07f;
        rect.set(ll, lt, lr, lb);
        paint.setColor(0xFFF1EFE8);
        canvas.drawRoundRect(rect, bh * 0.03f, bh * 0.03f, paint);

        // colored stripe along the top of the label
        float stripe = bh * 0.035f;
        path.reset();
        path.addRoundRect(rect, bh * 0.03f, bh * 0.03f, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(path);
        paint.setColor(accent);
        canvas.drawRect(ll, lt, lr, lt + stripe, paint);
        canvas.restore();

        // the song picture, left side of the label
        float pad = bh * 0.03f;
        float innerTop = lt + stripe + pad;
        float innerBottom = lb - pad;
        float size = innerBottom - innerTop;
        coverRect.set(ll + pad, innerTop, ll + pad + size, innerBottom);
        if (coverShader != null) {
            float cw = cover.getWidth();
            float ch = cover.getHeight();
            float scale = Math.max(size / cw, size / ch);
            matrix.reset();
            matrix.setScale(scale, scale);
            matrix.postTranslate(coverRect.centerX() - cw * scale / 2f,
                    coverRect.centerY() - ch * scale / 2f);
            coverShader.setLocalMatrix(matrix);
            coverPaint.setShader(coverShader);
        } else {
            coverPaint.setShader(null);
            coverPaint.setColor(fallbackColor);
        }
        canvas.drawRoundRect(coverRect, bh * 0.02f, bh * 0.02f, coverPaint);

        // title, artist and a thin writing line
        float tx = coverRect.right + pad * 1.4f;
        float avail = (lr - pad) - tx;
        if (avail > 10 * d) {
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(bh * 0.075f);
            textPaint.setColor(0xFF2C2C2A);
            float ty = innerTop + bh * 0.085f;
            canvas.drawText(TextUtils.ellipsize(title, textPaint, avail,
                    TextUtils.TruncateAt.END).toString(), tx, ty, textPaint);

            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(bh * 0.058f);
            textPaint.setColor(0xFF5F5E5A);
            canvas.drawText(TextUtils.ellipsize(artist, textPaint, avail,
                    TextUtils.TruncateAt.END).toString(), tx, ty + bh * 0.085f, textPaint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d);
            paint.setColor(0xFFC9C6BC);
            float ly = innerBottom - bh * 0.05f;
            canvas.drawLine(tx, ly, lr - pad, ly, paint);
        }

        // ----- tape window with the two reels -----
        float wt = T + bh * 0.48f;
        float wb = T + bh * 0.80f;
        rect.set(cx - bw * 0.30f, wt, cx + bw * 0.30f, wb);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF111116);
        canvas.drawRoundRect(rect, bh * 0.05f, bh * 0.05f, paint);

        float wh = wb - wt;
        float cy = (wt + wb) / 2f;
        float lcx = cx - bw * 0.17f;
        float rcx = cx + bw * 0.17f;
        float hubR = wh * 0.27f;
        float maxR = wh * 0.58f;
        float minR = hubR * 1.25f;
        float leftR = maxR + (minR - maxR) * playProgress;    // the left spool gets thinner
        float rightR = minR + (maxR - minR) * playProgress;   // the right spool gets fatter

        path.reset();
        path.addRoundRect(rect, bh * 0.05f, bh * 0.05f, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(path);

        // the tape between the reels, then the two spools of tape
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(bh * 0.012f);
        paint.setColor(0xFF3B2A22);
        canvas.drawLine(lcx, cy + Math.min(leftR, wh * 0.46f),
                rcx, cy + Math.min(rightR, wh * 0.46f), paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(lcx, cy, leftR, paint);
        canvas.drawCircle(rcx, cy, rightR, paint);

        drawHub(canvas, lcx, cy, hubR, angle);
        drawHub(canvas, rcx, cy, hubR, angle);
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * d);
        paint.setColor(0xFF3C3C48);
        canvas.drawRoundRect(rect, bh * 0.05f, bh * 0.05f, paint);

        // ----- bottom part (the trapezoid with two holes) -----
        float tt = T + bh * 0.84f;
        float tb = B - bh * 0.03f;
        path.reset();
        path.moveTo(cx - bw * 0.26f, tt);
        path.lineTo(cx + bw * 0.26f, tt);
        path.lineTo(cx + bw * 0.36f, tb);
        path.lineTo(cx - bw * 0.36f, tb);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF30303B);
        canvas.drawPath(path, paint);
        paint.setColor(0xFF111116);
        float holeY = (tt + tb) / 2f;
        canvas.drawCircle(cx - bw * 0.19f, holeY, bh * 0.022f, paint);
        canvas.drawCircle(cx + bw * 0.19f, holeY, bh * 0.022f, paint);
    }

    // a white reel hub with 6 teeth that turns
    private void drawHub(Canvas c, float x, float y, float r, float deg) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE9E7F2);
        c.drawCircle(x, y, r, paint);
        paint.setColor(0xFF111116);
        c.drawCircle(x, y, r * 0.70f, paint);

        c.save();
        c.rotate(deg, x, y);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(r * 0.22f);
        paint.setColor(0xFFE9E7F2);
        for (int i = 0; i < 6; i++) {
            c.drawLine(x, y - r * 0.38f, x, y - r * 0.72f, paint);
            c.rotate(60f, x, y);
        }
        c.restore();
    }
}