package io.github.sinshorts;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pantalla principal, construida en código para no depender de librerías externas. */
public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences prefs;
    private Ui ui;

    private TextView statusTitle, statusBody, countText, diagOutput, customFeedback;
    private View statusDot;
    private Button fixButton;
    private EditText customRules;
    private TextView pauseTitle, pauseBody;
    private LinearLayout chips;
    private Button pauseButton;
    private final java.util.Map<String, Switch> switches = new java.util.HashMap<String, Switch>();
    private boolean syncing;
    private final android.os.Handler ticker = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshPause();
            refreshStatus();
            ticker.postDelayed(this, 15_000);
        }
    };
    private boolean createdInSpanish;

    private static final Map<String, String> FRIENDLY = new HashMap<String, String>();

    static {
        FRIENDLY.put("com.google.android.youtube", "YouTube");
        FRIENDLY.put("com.instagram.android", "Instagram");
        FRIENDLY.put("com.facebook.katana", "Facebook");
        FRIENDLY.put("com.snapchat.android", "Snapchat");
        FRIENDLY.put("com.android.chrome", "Chrome");
        FRIENDLY.put("org.mozilla.firefox", "Firefox");
        FRIENDLY.put("com.zhiliaoapp.musically", "TikTok");
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        L.init(this);
        createdInSpanish = L.es();
        prefs = getSharedPreferences(Rules.PREFS, MODE_PRIVATE);
        Pause.migrate(this);
        ui = new Ui(this);

        // La primera vez, la guía de configuración va delante
        if (state == null && !prefs.getBoolean(Setup.KEY_ONBOARDING_DONE, false)) {
            startActivity(new Intent(this, OnboardingActivity.class));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page);
        ui.edgeToEdge(scroll, page, ui.dp(18), ui.dp(28), ui.dp(18), ui.dp(32));
        setContentView(scroll);

        // ---- Cabecera
        page.addView(ui.text("Sin Shorts", 30, ui.fg, true));
        TextView sub = ui.text(L.t("Blocks Shorts, Reels and Spotlight. Everything else in each app keeps working.",
                "Bloquea Shorts, Reels y Spotlight. El resto de cada app sigue funcionando."), 15, ui.fgMuted, false);
        sub.setPadding(0, ui.dp(4), 0, ui.dp(18));
        page.addView(sub);

        // ---- Estado
        LinearLayout status = ui.card(page);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(ui.dp(12), ui.dp(12));
        dotLp.rightMargin = ui.dp(10);
        row.addView(statusDot, dotLp);
        statusTitle = ui.text("", 18, ui.fg, true);
        row.addView(statusTitle);
        status.addView(row);
        statusBody = ui.text("", 14, ui.fgMuted, false);
        statusBody.setPadding(0, ui.dp(6), 0, 0);
        status.addView(statusBody);
        fixButton = ui.primary(L.t("Set up now", "Configurar ahora"));
        fixButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, OnboardingActivity.class));
            }
        });
        status.addView(fixButton, ui.fullWidth(12));

        // ---- Pausa (ya no hay interruptor de apagar)
        LinearLayout main = ui.card(page);
        pauseTitle = ui.text("", 16, ui.fg, true);
        main.addView(pauseTitle);
        pauseBody = ui.text("", 13, ui.fgMuted, false);
        pauseBody.setPadding(0, ui.dp(4), 0, ui.dp(4));
        main.addView(pauseBody);
        chips = new LinearLayout(this);
        for (final int m : Pause.OPTIONS) {
            Button chip = ui.button(m + " min");
            chip.setTag(m);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Pause.setMinutes(MainActivity.this, m);
                    refreshPause();
                }
            });
            chips.addView(chip, ui.weight());
        }
        main.addView(chips, ui.fullWidth(0));
        pauseButton = ui.primary("");
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Pause.isPaused(MainActivity.this)) {
                    Pause.resume(MainActivity.this); // volver a bloquear: siempre al instante
                    refreshPause();
                    refreshStatus();
                } else {
                    startActivity(PauseActivity.pause(MainActivity.this));
                }
            }
        });
        main.addView(pauseButton, ui.fullWidth(8));
        countText = ui.text("", 14, ui.fgMuted, false);
        countText.setPadding(0, ui.dp(10), 0, 0);
        main.addView(countText);

        // ---- Categorías
        section(page, L.t("What to block", "Qué bloquear"));
        LinearLayout cats = ui.card(page);
        for (int i = 0; i < Rules.CATEGORIES.length; i++) {
            Rules.Category c = Rules.CATEGORIES[i];
            if (i > 0) ui.divider(cats);
            toggle(cats, c.title(), c.subtitle(), c.key, c.defaultOn);
        }

        // ---- Opciones
        section(page, L.t("Options", "Opciones"));
        LinearLayout opts = ui.card(page);
        // Idioma de la app
        LinearLayout langRow = new LinearLayout(this);
        langRow.setGravity(Gravity.CENTER_VERTICAL);
        langRow.addView(ui.text(L.t("Language", "Idioma"), 16, ui.fg, false), ui.weight());
        langRow.addView(langButton("English", false));
        langRow.addView(langButton("Español", true));
        opts.addView(langRow, ui.fullWidth(0));
        ui.divider(opts);
        toggle(opts, L.t("Allow videos people send you", "Permitir los vídeos que te mandan"),
                L.t("A Reel, Short or TikTok opened from a chat or another app can be watched. Swiping to the next one is blocked.",
                        "Un Reel, Short o TikTok abierto desde una conversación u otra app se puede ver. Pasar al siguiente está bloqueado."),
                Rules.KEY_SHARED, true);
        ui.divider(opts);
        toggle(opts, L.t("Notify when blocking", "Avisar al bloquear"), L.t("Shows a short message each time it takes you out of a short video.",
                        "Muestra un mensaje breve cada vez que te saca de un vídeo corto."),
                Rules.KEY_TOAST, true);
        ui.divider(opts);
        Button guide = ui.button(L.t("Repeat the setup guide", "Repetir la configuración guiada"));
        guide.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        guide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, OnboardingActivity.class));
            }
        });
        opts.addView(guide, ui.fullWidth(0));

        // ---- Reglas personalizadas
        section(page, L.t("Custom rules", "Reglas personalizadas"));
        LinearLayout custom = ui.card(page);
        custom.addView(ui.text(L.t("If an app changes and stops being blocked, add a rule here with the ID you see in diagnostic mode. "
                        + "Format: package | type:value. Types: id, idsel (selected tab), text, textsel, url, app.",
                "Si una app cambia y deja de bloquearse, añade aquí una regla con el ID que veas en el modo diagnóstico. "
                        + "Formato: paquete | tipo:valor. Tipos: id, idsel (pestaña seleccionada), text, textsel, url, app."), 14, ui.fgMuted, false));
        customRules = new EditText(this);
        customRules.setTypeface(Typeface.MONOSPACE);
        customRules.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        customRules.setTextColor(ui.fg);
        customRules.setHintTextColor(ui.fgMuted);
        customRules.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        customRules.setHorizontallyScrolling(false);
        customRules.setMinLines(4);
        customRules.setGravity(Gravity.TOP | Gravity.START);
        customRules.setHint("com.google.android.youtube | id:reel_recycler");
        String saved = prefs.getString(Rules.KEY_CUSTOM, "");
        customRules.setText(saved.length() > 0 ? saved : Rules.customHelp());
        custom.addView(customRules, ui.fullWidth(8));
        customFeedback = ui.text("", 13, ui.fgMuted, false);
        custom.addView(customFeedback);
        Button save = ui.button(L.t("Save rules", "Guardar reglas"));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = customRules.getText().toString();
                prefs.edit().putString(Rules.KEY_CUSTOM, t).apply();
                int bad = Rules.countInvalid(t);
                customFeedback.setTextColor(bad == 0 ? ui.ok : ui.warn);
                customFeedback.setText(bad == 0 ? L.t("Saved.", "Guardado.") : L.t(
                        "Saved, but " + bad + (bad == 1 ? " line is" : " lines are") + " not in a valid format and will be ignored.",
                        "Guardado, pero " + bad + (bad == 1 ? " línea no tiene" : " líneas no tienen")
                                + " un formato válido y se ignora" + (bad == 1 ? "." : "n.")));
            }
        });
        custom.addView(save);

        // ---- Diagnóstico
        section(page, L.t("Diagnostic mode", "Modo diagnóstico"));
        LinearLayout diag = ui.card(page);
        diag.addView(ui.text(L.t("Turn it on, open the section you want to block (for example, the Reels tab) and come back here: "
                        + "you’ll see the IDs on that screen. Whatever is marked “Selected” is usually the active tab, ideal for an idsel rule. "
                        + "Everything stays on the phone. Turn it off when you’re done.",
                "Actívalo, abre la sección que quieras bloquear (por ejemplo, la pestaña Reels) y vuelve aquí: "
                        + "verás los IDs de esa pantalla. Lo marcado como «Seleccionado» suele ser la pestaña activa, ideal para una regla idsel. "
                        + "Todo se queda en el teléfono. Apágalo cuando termines."), 14, ui.fgMuted, false));
        toggle(diag, L.t("Capture screen IDs", "Capturar IDs de pantalla"), null, Rules.KEY_DIAG, false);
        LinearLayout diagButtons = new LinearLayout(this);
        Button refresh = ui.button(L.t("Refresh", "Actualizar"));
        Button copy = ui.button(L.t("Copy", "Copiar"));
        Button clear = ui.button(L.t("Clear", "Borrar"));
        diagButtons.addView(refresh, ui.weight());
        diagButtons.addView(copy, ui.weight());
        diagButtons.addView(clear, ui.weight());
        diag.addView(diagButtons);
        diagOutput = ui.text("", 12, ui.fg, false);
        diagOutput.setTypeface(Typeface.MONOSPACE);
        diagOutput.setTextIsSelectable(true);
        diagOutput.setPadding(0, ui.dp(8), 0, 0);
        diag.addView(diagOutput);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshDiagnostics();
            }
        });
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Sin Shorts diagnostics", diagOutput.getText()));
                Toast.makeText(MainActivity.this, L.t("Copied", "Copiado"), Toast.LENGTH_SHORT).show();
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Diagnostics.clear(MainActivity.this);
                refreshDiagnostics();
            }
        });

        TextView footer = ui.text(L.t("No Internet permission: nothing leaves your phone. Open source, MIT licence.",
                "Sin permiso de Internet: nada sale de tu teléfono. Código abierto, licencia MIT."), 13, ui.fgMuted, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, ui.dp(24), 0, 0);
        page.addView(footer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si se cambió el idioma en la guía, volver a construir la pantalla
        L.init(this);
        if (L.es() != createdInSpanish) {
            recreate();
            return;
        }
        prefs.registerOnSharedPreferenceChangeListener(this);
        syncSwitches();
        refreshStatus();
        refreshCount();
        refreshDiagnostics();
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    @Override
    protected void onPause() {
        ticker.removeCallbacks(tick);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        refreshCount();
        if (Pause.KEY_UNTIL.equals(key)) {
            refreshPause();
            refreshStatus();
        }
        if (key != null && switches.containsKey(key)) syncSwitches();
    }

    private void refreshStatus() {
        boolean on = Setup.serviceEnabled(this);
        boolean active = on && !Pause.isPaused(this);
        statusDot.setBackground(ui.dot(active ? ui.ok : ui.warn));
        if (!on) {
            statusTitle.setText(L.t("Protection needs to be turned on", "Falta activar la protección"));
            statusBody.setText(L.t("Without the accessibility service nothing can be blocked. The guide walks you through it.",
                    "Sin el servicio de accesibilidad no se puede bloquear nada. La guía te lleva paso a paso."));
        } else if (!active) {
            statusTitle.setText(L.t("Paused", "En pausa"));
            statusBody.setText(L.t("Blocking comes back on by itself when the pause ends.",
                    "El bloqueo vuelve solo cuando acaba la pausa."));
        } else {
            statusTitle.setText(L.t("Protection active", "Protección activa"));
            statusBody.setText(L.t("It works on its own in the background. You don’t need to keep the app open.",
                    "Funciona sola en segundo plano. No hace falta tener la app abierta."));
        }
        fixButton.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    private void refreshPause() {
        if (pauseButton == null) return;
        long left = Pause.remainingMs(this);
        if (left > 0) {
            int mins = (int) Math.ceil(left / 60_000.0);
            String at = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(Pause.until(this)));
            pauseTitle.setText(L.t("Paused · back at " + at, "En pausa · vuelve a las " + at));
            pauseBody.setText(L.t(mins + (mins == 1 ? " minute" : " minutes") + " left. Blocking comes back on by itself.",
                    "Queda" + (mins == 1 ? " 1 minuto" : "n " + mins + " minutos") + ". El bloqueo vuelve solo."));
            chips.setVisibility(View.GONE);
            pauseButton.setText(L.t("Resume blocking now", "Volver a bloquear ya"));
        } else {
            int sel = Pause.minutes(this);
            pauseTitle.setText(L.t("Need a break?", "¿Necesitas un respiro?"));
            pauseBody.setText(L.t("Blocking can’t be turned off, only paused. It comes back on by itself, and pausing takes a 30-second wait.",
                    "El bloqueo no se apaga, solo se pausa. Vuelve solo, y pausarlo requiere esperar 30 segundos."));
            chips.setVisibility(View.VISIBLE);
            for (int i = 0; i < chips.getChildCount(); i++) {
                Button b = (Button) chips.getChildAt(i);
                boolean on = ((Integer) b.getTag()) == sel;
                b.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                b.setTextColor(on ? ui.accent : ui.fgMuted);
                b.setBackground(on ? ui.rounded(ui.dark ? 0x22FF6B6E : 0x14E5383B, 18) : null);
            }
            pauseButton.setText(L.t("Pause for " + sel + " min", "Pausar " + sel + " min"));
        }
    }

    /** Ajustes que «aflojan» el bloqueo: cambiarlos exige la espera de 30 s. */
    private static Boolean looseValue(String key) {
        if (key.startsWith("cat_")) return false;          // desactivar una categoría
        if (Rules.KEY_SHARED.equals(key)) return true;     // permitir vídeos compartidos
        return null;
    }

    private void syncSwitches() {
        syncing = true;
        for (java.util.Map.Entry<String, Switch> e : switches.entrySet()) {
            Rules.Category c = null;
            for (Rules.Category k : Rules.CATEGORIES) if (k.key.equals(e.getKey())) c = k;
            boolean def = c != null ? c.defaultOn : Rules.KEY_SHARED.equals(e.getKey());
            e.getValue().setChecked(prefs.getBoolean(e.getKey(), def));
        }
        syncing = false;
    }

    private void refreshCount() {
        if (countText == null) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        int c = today.equals(prefs.getString(Rules.KEY_COUNT_DAY, "")) ? prefs.getInt(Rules.KEY_COUNT, 0) : 0;
        int total = prefs.getInt(Rules.KEY_TOTAL, 0);
        countText.setText(L.t("Blocked today: " + c + "   ·   Total: " + total, "Bloqueados hoy: " + c + "   ·   En total: " + total));
    }

    private void refreshDiagnostics() {
        List<Diagnostics.Entry> list = Diagnostics.recent(this, 5);
        if (list.isEmpty()) {
            diagOutput.setText(prefs.getBoolean(Rules.KEY_DIAG, false)
                    ? L.t("No captures yet. Open another app, browse a little and come back.", "Aún no hay capturas. Abre otra app, navega un poco y vuelve.")
                    : L.t("No captures.", "Sin capturas."));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Diagnostics.Entry e : list) {
            String name = Diagnostics.FOLLOWING_DEBUG.equals(e.pkg)
                    ? L.t("Sin Shorts · “Open Following” failure", "Sin Shorts · fallo al abrir Siguiendo")
                    : FRIENDLY.containsKey(e.pkg) ? FRIENDLY.get(e.pkg) + " (" + e.pkg + ")" : e.pkg;
            sb.append("━━ ").append(name).append('\n').append(e.text).append('\n');
        }
        diagOutput.setText(sb.toString().trim());
    }

    private Button langButton(String label, final boolean spanish) {
        boolean on = L.es() == spanish;
        Button b = on ? ui.primary(label) : ui.button(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setMinHeight(ui.dp(38));
        b.setMinimumHeight(ui.dp(38));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (L.es() == spanish) return;
                L.set(MainActivity.this, spanish);
                recreate();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = ui.dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private void section(LinearLayout parent, String title) {
        TextView t = ui.text(title.toUpperCase(Locale.getDefault()), 12, ui.accent, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(ui.dp(4), ui.dp(14), 0, ui.dp(8));
        parent.addView(t);
    }

    private Switch toggle(LinearLayout parent, String title, String subtitle, final String key, boolean def) {
        Switch s = new Switch(this);
        s.setText(title);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        s.setTextColor(ui.fg);
        s.setChecked(prefs.getBoolean(key, def));
        s.setPadding(0, ui.dp(6), 0, ui.dp(2));
        final Boolean loose = looseValue(key);
        if (loose != null) switches.put(key, s);
        final String label = title;
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (syncing) return;
                if (loose != null && checked == loose) {
                    // Aflojar el bloqueo: deshacer y pasar por la espera de 30 s
                    syncing = true;
                    b.setChecked(!checked);
                    syncing = false;
                    startActivity(PauseActivity.setting(MainActivity.this, key, checked, label));
                    return;
                }
                prefs.edit().putBoolean(key, checked).apply();
                if (Rules.KEY_DIAG.equals(key)) refreshDiagnostics();
            }
        });
        parent.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (subtitle != null) {
            TextView t = ui.text(subtitle, 13, ui.fgMuted, false);
            t.setPadding(0, 0, ui.dp(56), 0);
            parent.addView(t);
        }
        return s;
    }
}
