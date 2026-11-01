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
        prefs = getSharedPreferences(Rules.PREFS, MODE_PRIVATE);
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
        TextView sub = ui.text("Bloquea Shorts, Reels y Spotlight. El resto de cada app sigue funcionando.", 15, ui.fgMuted, false);
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
        fixButton = ui.primary("Configurar ahora");
        fixButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, OnboardingActivity.class));
            }
        });
        status.addView(fixButton, ui.fullWidth(12));

        // ---- Interruptor general y contador
        LinearLayout main = ui.card(page);
        Switch master = toggle(main, "Bloqueo activado", "Desactívalo para hacer una pausa sin tocar los ajustes de accesibilidad.",
                Rules.KEY_ENABLED, true);
        master.setTypeface(Typeface.DEFAULT_BOLD);
        countText = ui.text("", 14, ui.fgMuted, false);
        countText.setPadding(0, ui.dp(10), 0, 0);
        main.addView(countText);

        // ---- Categorías
        section(page, "Qué bloquear");
        LinearLayout cats = ui.card(page);
        for (int i = 0; i < Rules.CATEGORIES.length; i++) {
            Rules.Category c = Rules.CATEGORIES[i];
            if (i > 0) ui.divider(cats);
            toggle(cats, c.title, c.subtitle, c.key, c.defaultOn);
        }

        // ---- Opciones
        section(page, "Opciones");
        LinearLayout opts = ui.card(page);
        toggle(opts, "Avisar al bloquear", "Muestra un mensaje breve cada vez que te saca de un vídeo corto.",
                Rules.KEY_TOAST, true);
        ui.divider(opts);
        Button guide = ui.button("Repetir la configuración guiada");
        guide.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        guide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, OnboardingActivity.class));
            }
        });
        opts.addView(guide, ui.fullWidth(0));

        // ---- Reglas personalizadas
        section(page, "Reglas personalizadas");
        LinearLayout custom = ui.card(page);
        custom.addView(ui.text("Si una app cambia y deja de bloquearse, añade aquí una regla con el ID que veas en el modo diagnóstico. "
                + "Formato: paquete | tipo:valor. Tipos: id, idsel (pestaña seleccionada), text, textsel, url, app.", 14, ui.fgMuted, false));
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
        customRules.setText(saved.length() > 0 ? saved : Rules.CUSTOM_HELP);
        custom.addView(customRules, ui.fullWidth(8));
        customFeedback = ui.text("", 13, ui.fgMuted, false);
        custom.addView(customFeedback);
        Button save = ui.button("Guardar reglas");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = customRules.getText().toString();
                prefs.edit().putString(Rules.KEY_CUSTOM, t).apply();
                int bad = Rules.countInvalid(t);
                customFeedback.setTextColor(bad == 0 ? ui.ok : ui.warn);
                customFeedback.setText(bad == 0 ? "Guardado." : "Guardado, pero " + bad
                        + (bad == 1 ? " línea no tiene" : " líneas no tienen") + " un formato válido y se ignora"
                        + (bad == 1 ? "." : "n."));
            }
        });
        custom.addView(save);

        // ---- Diagnóstico
        section(page, "Modo diagnóstico");
        LinearLayout diag = ui.card(page);
        diag.addView(ui.text("Actívalo, abre la sección que quieras bloquear (por ejemplo, la pestaña Reels) y vuelve aquí: "
                + "verás los IDs de esa pantalla. Lo marcado como «Seleccionado» suele ser la pestaña activa, ideal para una regla idsel. "
                + "Todo se queda en el teléfono. Apágalo cuando termines.", 14, ui.fgMuted, false));
        toggle(diag, "Capturar IDs de pantalla", null, Rules.KEY_DIAG, false);
        LinearLayout diagButtons = new LinearLayout(this);
        Button refresh = ui.button("Actualizar");
        Button copy = ui.button("Copiar");
        Button clear = ui.button("Borrar");
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
                cm.setPrimaryClip(ClipData.newPlainText("Sin Shorts diagnóstico", diagOutput.getText()));
                Toast.makeText(MainActivity.this, "Copiado", Toast.LENGTH_SHORT).show();
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Diagnostics.clear(MainActivity.this);
                refreshDiagnostics();
            }
        });

        TextView footer = ui.text("Sin permiso de Internet: nada sale de tu teléfono. Código abierto, licencia MIT.", 13, ui.fgMuted, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, ui.dp(24), 0, 0);
        page.addView(footer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs.registerOnSharedPreferenceChangeListener(this);
        refreshStatus();
        refreshCount();
        refreshDiagnostics();
    }

    @Override
    protected void onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        refreshCount();
        if (Rules.KEY_ENABLED.equals(key)) refreshStatus();
    }

    private void refreshStatus() {
        boolean on = Setup.serviceEnabled(this);
        boolean active = on && prefs.getBoolean(Rules.KEY_ENABLED, true);
        statusDot.setBackground(ui.dot(active ? ui.ok : ui.warn));
        if (!on) {
            statusTitle.setText("Falta activar la protección");
            statusBody.setText("Sin el servicio de accesibilidad no se puede bloquear nada. La guía te lleva paso a paso.");
        } else if (!active) {
            statusTitle.setText("En pausa");
            statusBody.setText("El servicio está activado, pero el bloqueo está en pausa.");
        } else {
            statusTitle.setText("Protección activa");
            statusBody.setText("Funciona sola en segundo plano. No hace falta tener la app abierta.");
        }
        fixButton.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    private void refreshCount() {
        if (countText == null) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        int c = today.equals(prefs.getString(Rules.KEY_COUNT_DAY, "")) ? prefs.getInt(Rules.KEY_COUNT, 0) : 0;
        int total = prefs.getInt(Rules.KEY_TOTAL, 0);
        countText.setText("Bloqueados hoy: " + c + "   ·   En total: " + total);
    }

    private void refreshDiagnostics() {
        List<Diagnostics.Entry> list = Diagnostics.recent(this, 5);
        if (list.isEmpty()) {
            diagOutput.setText(prefs.getBoolean(Rules.KEY_DIAG, false)
                    ? "Aún no hay capturas. Abre otra app, navega un poco y vuelve."
                    : "Sin capturas.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Diagnostics.Entry e : list) {
            String name = FRIENDLY.containsKey(e.pkg) ? FRIENDLY.get(e.pkg) + " (" + e.pkg + ")" : e.pkg;
            sb.append("━━ ").append(name).append('\n').append(e.text).append('\n');
        }
        diagOutput.setText(sb.toString().trim());
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
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
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
