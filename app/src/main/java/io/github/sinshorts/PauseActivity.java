package io.github.sinshorts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * The 30-second wait before loosening the block: pausing it, or switching off a
 * category or option. If you leave this screen, the wait starts over.
 */
public class PauseActivity extends Activity {

    public static final String EXTRA_KEY = "key";          // a setting to change after the wait
    public static final String EXTRA_VALUE = "value";      // the value it gets (default false)
    public static final String EXTRA_LABEL = "label";      // what is being loosened, for the text

    private Ui ui;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long endsAt;
    private boolean done;
    private TextView count, title;
    private ProgressBar bar;
    private Button confirm;
    private String key;

    /** Pause the whole block, with the minutes chosen in the app. */
    public static Intent pause(Context c) {
        return new Intent(c, PauseActivity.class);
    }

    /** Change one setting (switch a category off, etc.) after the wait. */
    public static Intent setting(Context c, String key, boolean value, String label) {
        return new Intent(c, PauseActivity.class)
                .putExtra(EXTRA_KEY, key).putExtra(EXTRA_VALUE, value).putExtra(EXTRA_LABEL, label);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long left = endsAt - SystemClock.uptimeMillis();
            int secs = (int) Math.ceil(Math.max(0, left) / 1000.0);
            count.setText(String.valueOf(secs));
            bar.setProgress((int) (1000 * (1 - Math.max(0, left) / (Pause.WAIT_SECONDS * 1000.0))));
            if (left <= 0) {
                done = true;
                confirm.setEnabled(true);
                confirm.setAlpha(1f);
                count.setText("✓");
                title.setText(L.t("You can continue", "Ya puedes continuar"));
                return;
            }
            handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        L.init(this);
        ui = new Ui(this);
        key = getIntent().getStringExtra(EXTRA_KEY);
        final boolean value = getIntent().getBooleanExtra(EXTRA_VALUE, false);
        String label = getIntent().getStringExtra(EXTRA_LABEL);
        final int minutes = Pause.minutes(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        ui.edgeToEdge(page, page, ui.dp(28), ui.dp(56), ui.dp(28), ui.dp(28));
        setContentView(page);

        TextView eyebrow = ui.text("SIN SHORTS", 12, ui.accent, true);
        eyebrow.setLetterSpacing(0.1f);
        page.addView(eyebrow);

        title = ui.text(L.t("Take a breath", "Respira un momento"), 28, ui.fg, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, ui.dp(10), 0, ui.dp(10));
        page.addView(title);

        String what = key == null
                ? L.t("pause blocking for " + minutes + " minutes", "pausar el bloqueo " + minutes + " minutos")
                : L.t("turn off “" + label + "”", "desactivar «" + label + "»");
        TextView body = ui.text(L.t("You’re about to " + what + ". Do you really need it right now? "
                        + "If you still want to after the countdown, go ahead.",
                "Vas a " + what + ". ¿De verdad lo necesitas ahora? Si al terminar la cuenta atrás "
                        + "sigues queriendo, adelante."), 16, ui.fgMuted, false);
        body.setGravity(Gravity.CENTER);
        page.addView(body);

        count = ui.text(String.valueOf(Pause.WAIT_SECONDS), 84, ui.fg, false);
        count.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        count.setGravity(Gravity.CENTER);
        count.setPadding(0, ui.dp(36), 0, ui.dp(8));
        page.addView(count);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.accent));
        page.addView(bar, ui.fullWidth(8));

        TextView hint = ui.text(key == null
                ? L.t("Blocking comes back on by itself when the pause ends. Videos people send you are allowed anyway.",
                "El bloqueo vuelve solo al acabar la pausa. Los vídeos que te mandan se pueden ver igualmente.")
                : L.t("If you leave this screen, the countdown starts over.", "Si sales de esta pantalla, la cuenta atrás vuelve a empezar."),
                13, ui.fgMuted, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, ui.dp(16), 0, 0);
        page.addView(hint);

        page.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        confirm = ui.primary(key == null
                ? L.t("Pause for " + minutes + " min", "Pausar " + minutes + " min")
                : L.t("Turn off", "Desactivar"));
        confirm.setEnabled(false);
        confirm.setAlpha(0.35f);
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!done) return;
                if (key == null) {
                    Pause.start(PauseActivity.this, minutes);
                } else {
                    getSharedPreferences(Rules.PREFS, MODE_PRIVATE).edit().putBoolean(key, value).apply();
                }
                setResult(RESULT_OK);
                finish();
            }
        });
        page.addView(confirm, ui.fullWidth(0));

        Button cancel = ui.button(L.t("Never mind, keep blocking", "Mejor no, seguir bloqueando"));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        page.addView(cancel, ui.fullWidth(6));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!done) {
            endsAt = SystemClock.uptimeMillis() + Pause.WAIT_SECONDS * 1000L;
            handler.post(tick);
        }
    }

    @Override
    protected void onPause() {
        // Leaving the screen resets the wait
        handler.removeCallbacks(tick);
        if (!done) count.setText(String.valueOf(Pause.WAIT_SECONDS));
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
