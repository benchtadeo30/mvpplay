package com.appdev.mvpplay;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

// gives a pop-up a solid background that matches the theme the user picked
public class DialogBg {

    public static void apply(Context context, View root) {
        float d = context.getResources().getDisplayMetrics().density;

        int fill;       // background color
        int border;     // border color
        int borderDp;   // border thickness
        int radiusDp;   // corner roundness

        switch (ThemeStore.get(context)) {
            case ThemeStore.LIGHT:
                fill = 0xFFFFFFFF;
                border = 0x33000000;
                borderDp = 1;
                radiusDp = 20;
                break;
            case ThemeStore.SPOTIFY:
                fill = 0xFF181818;
                border = 0xFF1DB954;
                borderDp = 2;
                radiusDp = 12;
                break;
            case ThemeStore.GLASS:
                fill = 0xF0231A4A;      // purple, almost solid so text stays readable
                border = 0xAAB8A4FF;
                borderDp = 2;
                radiusDp = 28;
                break;
            case ThemeStore.HALLOWEEN:
                fill = 0xFF1C1020;
                border = 0xFFFF7A1A;
                borderDp = 2;
                radiusDp = 18;
                break;
            case ThemeStore.CHRISTMAS:
                fill = 0xFF10261A;
                border = 0xFFE23B4A;
                borderDp = 2;
                radiusDp = 22;
                break;
            case ThemeStore.SUMMER:
                fill = 0xFFFFF8E7;
                border = 0xFF14A8C4;
                borderDp = 2;
                radiusDp = 24;
                break;
            case ThemeStore.DARK:
            default:
                fill = 0xFF141A2B;
                border = 0x33FFFFFF;
                borderDp = 1;
                radiusDp = 24;
                break;
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(radiusDp * d);
        bg.setStroke((int) (borderDp * d), border);
        root.setBackground(bg);
    }
}