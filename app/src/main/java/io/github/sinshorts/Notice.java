package io.github.sinshorts;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Short message at the bottom of the screen ("short video blocked", hints…).
 * Drawn on the accessibility overlay layer instead of a Toast, because recent
 * Android versions may hide Toasts posted by apps running in the background.
 */
final class Notice {

    private final AccessibilityService service;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View view;

    private final Runnable hideTask = new Runnable() {
        @Override
        public void run() {
            fadeOut();
        }
    };

    Notice(AccessibilityService service) {
        this.service = service;
        this.wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
    }

    void show(String text, long durationMs) {
        handler.removeCallbacks(hideTask);
        removeNow();
        boolean dark = (service.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        LinearLayout pill = new LinearLayout(service);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(16), dp(11), dp(20), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? 0xF2F2F2F2 : 0xF21A1A1A);
        bg.setCornerRadius(dp(24));
        pill.setBackground(bg);

        View dot = new View(service);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0xFFE5383B);
        dot.setBackground(d);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dlp.rightMargin = dp(10);
        pill.addView(dot, dlp);

        TextView t = new TextView(service);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(dark ? 0xFF1A1A1A : 0xFFF2F2F2);
        t.setMaxWidth(service.getResources().getDisplayMetrics().widthPixels - dp(80));
        pill.addView(t);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        p.y = dp(110);
        try {
            pill.setAlpha(0f);
            wm.addView(pill, p);
            view = pill;
            pill.animate().alpha(1f).setDuration(150).start();
            handler.postDelayed(hideTask, durationMs);
        } catch (Exception ignored) {
            view = null;
        }
    }

    private void fadeOut() {
        final View v = view;
        if (v == null) return;
        view = null;
        v.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                try {
                    wm.removeView(v);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void removeNow() {
        if (view != null) {
            try {
                view.animate().cancel();
                wm.removeView(view);
            } catch (Exception ignored) {
            }
            view = null;
        }
    }

    void destroy() {
        handler.removeCallbacksAndMessages(null);
        removeNow();
    }

    private int dp(int v) {
        return Math.round(v * service.getResources().getDisplayMetrics().density);
    }
}
