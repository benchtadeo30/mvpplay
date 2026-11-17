package com.appdev.mvpplay;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.graphics.Color;
import android.os.Build;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.appcompat.app.AppCompatActivity;

// every screen extends this, so the chosen theme is applied everywhere
public class BaseActivity extends AppCompatActivity {

    private int themeAtCreate;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        applySpotifyFlag();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySpotifyFlag();
    }

    // Spotify colors live in the "desk + night" resource folders,
    // so a Spotify screen is told it is in desk mode
    @SuppressWarnings("deprecation")
    private void applySpotifyFlag() {
        android.content.res.Resources res = getResources();
        Configuration config = new Configuration(res.getConfiguration());
        // Spotify = desk mode, every other theme = normal mode (this clears the old flag)
        int type = Configuration.UI_MODE_TYPE_NORMAL;
        if (ThemeStore.isSpotify(this)) type = Configuration.UI_MODE_TYPE_DESK;
        if (ThemeStore.isHalloween(this)) type = Configuration.UI_MODE_TYPE_CAR;
        if (ThemeStore.isChristmas(this)) type = Configuration.UI_MODE_TYPE_APPLIANCE;
        if (ThemeStore.isSummer(this)) type = Configuration.UI_MODE_TYPE_TELEVISION;
        if (ThemeStore.isGlass(this) && Build.VERSION.SDK_INT >= 26) {
            type = Configuration.UI_MODE_TYPE_VR_HEADSET;
        }
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_TYPE_MASK) | type;
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themeAtCreate = ThemeStore.get(this);

        // edge-to-edge: the page draws behind the status bar and the bottom bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);   // no gray strip
        }
        // also fill the area around a camera notch
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

        hideSystemBars();   // hide right away, before the screen is even shown
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();   // and again when coming back to this screen
        if (showThemeDecor()) {
            ChristmasOverlay.sync(this);   // snow + lights for the Christmas theme
            SummerOverlay.sync(this);      // waves, bubbles, sun glow, sparkles for Summer
            GlassBackdrop.sync(this);      // the moving glow behind the glass theme
        }
    }

    // screens like the splash can turn the theme decorations off
    protected boolean showThemeDecor() {
        return true;
    }
    // immersive: hide the status bar and the bottom buttons. A swipe from the edge
    // shows them for a moment, then they hide again.
    protected void hideSystemBars() {
        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        bars.hide(WindowInsetsCompat.Type.systemBars());
    }

    // dialogs, the keyboard and sheets can bring the bars back, so hide them again
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }
    @Override
    protected void onStart() {
        super.onStart();
        // the theme was changed on another screen: redraw this one
        if (ThemeStore.get(this) != themeAtCreate) recreate();
    }
}