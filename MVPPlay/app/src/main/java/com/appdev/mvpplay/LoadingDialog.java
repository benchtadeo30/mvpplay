package com.appdev.mvpplay;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

// a modern loading pop-up with the running dog
public class LoadingDialog {

    private final Dialog dialog;
    private final View card;
    private AnimatedImageDrawable animation;

    public LoadingDialog(Activity activity, String message) {
        card = activity.getLayoutInflater().inflate(R.layout.dialog_loading, null);
        ((TextView) card.findViewById(R.id.tvLoading)).setText(message);

        // the GIF plays by itself, forever
        ImageView dog = card.findViewById(R.id.ivDog);
        try {
            Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(
                    activity.getResources(), R.drawable.dog_transparent));
            dog.setImageDrawable(d);
            if (d instanceof AnimatedImageDrawable) {
                animation = (AnimatedImageDrawable) d;
                animation.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
            }
        } catch (Exception ignored) {
        }

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(card);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.65f);
            float density = activity.getResources().getDisplayMetrics().density;
            window.setLayout((int) (270 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public void show() {
        card.setAlpha(0f);
        card.setScaleX(0.85f);
        card.setScaleY(0.85f);
        dialog.show();
        if (animation != null) animation.start();
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
    }

    public void dismiss() {
        if (animation != null) animation.stop();
        try {
            dialog.dismiss();
        } catch (Exception ignored) {
        }
    }
}