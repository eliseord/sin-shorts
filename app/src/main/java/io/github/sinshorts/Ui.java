package io.github.sinshorts;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colores y piezas de interfaz compartidas por las pantallas de la app. */
public final class Ui {

    public final Activity a;
    public final boolean dark;
    public final int fg, fgMuted, cardBg, accent, onAccent, ok, warn, line;

    public Ui(Activity a) {
        this.a = a;
        dark = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        fg = dark ? 0xFFF2F2F2 : 0xFF1A1A1A;
        fgMuted = dark ? 0xFFA8A8A8 : 0xFF666666;
        cardBg = dark ? 0xFF1E1E1E : 0xFFF4F1EE;
        accent = dark ? 0xFFFF6B6E : 0xFFE5383B;
        onAccent = dark ? 0xFF1A0505 : 0xFFFFFFFF;
        ok = dark ? 0xFF5DD39E : 0xFF1E9E63;
        warn = dark ? 0xFFFFB347 : 0xFFC86F1A;
        line = dark ? 0x22FFFFFF : 0x14000000;
    }

    /** Barra de estado del color del fondo y márgenes para las barras del sistema (Android 15+). */
    public void edgeToEdge(View root, final View padded, final int l, final int t, final int r, final int b) {
        a.getWindow().setStatusBarColor(dark ? Color.BLACK : Color.WHITE);
        if (!dark) a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        padded.setPadding(l, t, r, b);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                padded.setPadding(l + in.getSystemWindowInsetLeft(), t + in.getSystemWindowInsetTop(),
                        r + in.getSystemWindowInsetRight(), b + in.getSystemWindowInsetBottom());
                return in;
            }
        });
    }

    public int dp(int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    public TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(rounded(cardBg, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    public GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        return bg;
    }

    public GradientDrawable dot(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    public void divider(LinearLayout parent) {
        View d = new View(a);
        d.setBackgroundColor(line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(10);
        parent.addView(d, lp);
    }

    /** Botón de texto, discreto. */
    public Button button(String label) {
        Button b = new Button(a, null, android.R.attr.borderlessButtonStyle);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(accent);
        return b;
    }

    /** Botón relleno, para la acción principal de cada paso. */
    public Button primary(String label) {
        Button b = new Button(a, null, android.R.attr.borderlessButtonStyle);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(onAccent);
        b.setBackground(rounded(accent, 24));
        b.setMinHeight(dp(52));
        b.setPadding(dp(20), 0, dp(20), 0);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    public LinearLayout.LayoutParams fullWidth(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    public LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }
}
