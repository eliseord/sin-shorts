package io.github.sinshorts;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * «Cortina»: una capa que tapa una zona concreta de otra app (el feed de inicio o
 * Explorar de Instagram) y bloquea el scroll ahí, dejando libre el resto de la pantalla.
 * Usa la capa propia de los servicios de accesibilidad, así que no necesita permisos extra.
 */
final class Curtain {

    static final int NONE = 0, HOME_FEED = 1, EXPLORE = 2;

    interface Actions {
        void openFollowingFeed();

        void openSearch();

        /** Has tocado la pantalla fuera de la cortina (historias, mensajes, pestañas…). */
        void onOutsideTouch();
    }

    private final AccessibilityService service;
    private final WindowManager wm;
    private final Actions actions;

    private View view;
    private int mode = NONE;
    private final Rect bounds = new Rect();

    Curtain(AccessibilityService service, Actions actions) {
        this.service = service;
        this.actions = actions;
        this.wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
    }

    int mode() {
        return mode;
    }

    void show(int newMode, Rect r) {
        if (view != null && mode == newMode) {
            if (!bounds.equals(r)) {
                bounds.set(r);
                try {
                    wm.updateViewLayout(view, params(r, mode));
                } catch (Exception e) {
                    hide();
                }
            }
            return;
        }
        hide();
        view = build(newMode);
        mode = newMode;
        bounds.set(r);
        try {
            wm.addView(view, params(r, newMode));
        } catch (Exception e) {
            view = null;
            mode = NONE;
        }
    }

    /** Quitar al momento (por ejemplo, al salir de Instagram). */
    void hide() {
        if (view != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
            }
        }
        view = null;
        mode = NONE;
        bounds.setEmpty();
    }

    private WindowManager.LayoutParams params(Rect r, int m) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                // Avisar al instante de cualquier toque fuera de la cortina
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                r.width(), r.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.x = r.left;
        p.y = r.top;
        try {
            // Android 9+: colocar la capa en coordenadas exactas de pantalla
            WindowManager.LayoutParams.class.getField("layoutInDisplayCutoutMode").setInt(p, 3);
        } catch (Exception ignored) {
        }
        return p;
    }

    private int dp(int v) {
        return Math.round(v * service.getResources().getDisplayMetrics().density);
    }

    private View build(int m) {
        boolean dark = (service.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int bg = dark ? 0xFF0F0F0F : 0xFFFAFAFA;
        int fg = dark ? 0xFFF2F2F2 : 0xFF1A1A1A;
        int muted = dark ? 0xFFA8A8A8 : 0xFF666666;
        int accent = dark ? 0xFFFF6B6E : 0xFFE5383B;
        int onAccent = dark ? 0xFF1A0505 : 0xFFFFFFFF;

        LinearLayout box = new LinearLayout(service);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(bg);
        box.setPadding(dp(28), dp(16), dp(28), dp(16));
        // Consumir los toques: así el feed de debajo no se puede deslizar
        box.setClickable(true);
        box.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_OUTSIDE) {
                    actions.onOutsideTouch();
                    return true;
                }
                return false;
            }
        });

        box.addView(label("SIN SHORTS", 12, accent, true));
        boolean home = m == HOME_FEED;
        TextView title = label(home ? L.t("Only people you follow", "Solo tus seguidos") : L.t("Explore is covered", "Explorar está tapado"), 22, fg, true);
        title.setPadding(0, dp(8), 0, dp(8));
        box.addView(title);
        box.addView(label(home
                ? L.t("Home mixes in endless suggested posts. “Following” shows only the people you follow, in order, and it ends.",
                        "El inicio mezcla publicaciones sugeridas sin fin. En «Siguiendo» ves solo a quien sigues, en orden, y se termina.")
                : L.t("This is only content from accounts you don’t follow. You can still search for people with the bar above.",
                        "Aquí solo hay contenido de cuentas que no sigues. Puedes seguir buscando personas con la barra de arriba."),
                15, muted, false));

        Button b = new Button(service, null, android.R.attr.borderlessButtonStyle);
        b.setText(home ? L.t("Open Following", "Abrir Siguiendo") : L.t("Search", "Buscar"));
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(onAccent);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(accent);
        shape.setCornerRadius(dp(24));
        b.setBackground(shape);
        b.setPadding(dp(28), 0, dp(28), 0);
        b.setMinHeight(dp(50));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mode == HOME_FEED) actions.openFollowingFeed();
                else actions.openSearch();
            }
        });
        box.addView(b, lp);

        if (home) {
            TextView hint = label(L.t("Or tap the Instagram logo at the top › Following. Stories and messages are still up there.",
                    "También: toca el logo de Instagram arriba › Siguiendo. Historias y mensajes siguen arriba."),
                    13, muted, false);
            hint.setPadding(0, dp(16), 0, 0);
            box.addView(hint);
        }
        return box;
    }

    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(service);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.15f);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        if (bold && sp <= 12) t.setLetterSpacing(0.1f);
        return t;
    }
}
