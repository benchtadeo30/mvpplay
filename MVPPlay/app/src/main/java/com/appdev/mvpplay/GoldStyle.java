package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

// the gold look of the special playlist (same in every theme)
public final class GoldStyle {

    public static final int TEXT_DARK = 0xFF3A2600;
    public static final int TEXT_DARK_SOFT = 0xCC3A2600;
    public static final int TEXT_LIGHT_GOLD = 0xFFFFE27A;
    public static final int TEXT_GOLD_A = 0xFFD99A0B;   // gold text, start of the gradient
    public static final int TEXT_GOLD_B = 0xFF9C6A06;   // gold text, end of the gradient

    // dark gold -> gold -> bright gold -> gold -> dark gold
    private static final int[] GOLD =
            {0xFF8A6208, 0xFFD9A520, 0xFFFFE27A, 0xFFD9A520, 0xFF8A6208};

    private GoldStyle() {
    }

    public static GradientDrawable background(Context c, float radiusDp) {
        float d = c.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, GOLD);
        g.setCornerRadius(radiusDp * d);
        g.setStroke((int) (1.5f * d), 0xFFFFE9A3);
        return g;
    }

    // the small dark tag (SPECIAL) on the gold
    public static GradientDrawable tagBackground(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xE63A2600);
        g.setCornerRadius(100 * d);
        return g;
    }
}