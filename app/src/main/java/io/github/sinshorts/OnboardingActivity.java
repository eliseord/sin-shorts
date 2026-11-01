package io.github.sinshorts;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Asistente que se abre la primera vez y guía por todos los ajustes necesarios.
 * Cada paso comprueba solo si ya está hecho al volver de los Ajustes de Android.
 */
public class OnboardingActivity extends Activity {

    private static final int WELCOME = 0, ACTIVATE = 1, BATTERY = 2, CHOOSE = 3, TEST = 4, DONE = 5;
    private static final int STEPS = 6;
    private static final String STATE_STEP = "step";

    private Ui ui;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int step = WELCOME;
    private LinearLayout content, dots;
    private ScrollView scroll;
    private TextView stepLabel;
    private Button back, next;

    // Estado de cada paso
    private boolean triedToggle;        // ya intentó encender el interruptor
    private boolean sawRestricted;      // vimos el ajuste bloqueado en esta sesión
    private boolean wasEnabled;         // para detectar el momento en que se activa
    private boolean testRunning;
    private int totalBeforeTest = -1;
    private boolean testPassed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        prefs = getSharedPreferences(Rules.PREFS, MODE_PRIVATE);
        if (state != null) {
            step = state.getInt(STATE_STEP, WELCOME);
            triedToggle = state.getBoolean("tried");
            testPassed = state.getBoolean("passed");
            sawRestricted = state.getBoolean("sawRestricted");
        }
        wasEnabled = Setup.serviceEnabled(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Cabecera: puntos de progreso
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ui.dp(22), ui.dp(14), ui.dp(22), ui.dp(4));
        dots = new LinearLayout(this);
        header.addView(dots, ui.weight());
        stepLabel = ui.text("", 13, ui.fgMuted, false);
        header.addView(stepLabel);
        root.addView(header);

        // Contenido del paso
        scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(22), ui.dp(16), ui.dp(22), ui.dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Barra inferior
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(14), ui.dp(8), ui.dp(18), ui.dp(14));
        back = ui.button("Atrás");
        back.setTextColor(ui.fgMuted);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                go(step - 1);
            }
        });
        bar.addView(back);
        // Separador horizontal: altura fija de 1 px (con wrap_content se comía toda la pantalla)
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        next = ui.primary("Siguiente");
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (step == DONE) finishOnboarding();
                else go(step + 1);
            }
        });
        bar.addView(next);
        root.addView(bar);

        // Cabecera arriba y barra abajo deben esquivar las barras del sistema
        ui.edgeToEdge(root, root, 0, 0, 0, 0);
        setContentView(root);
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_STEP, step);
        out.putBoolean("tried", triedToggle);
        out.putBoolean("passed", testPassed);
        out.putBoolean("sawRestricted", sawRestricted);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean enabled = Setup.serviceEnabled(this);

        if (step == TEST && testRunning) {
            testRunning = false;
            if (prefs.getInt(Rules.KEY_TOTAL, 0) > totalBeforeTest) testPassed = true;
        }
        render();

        // Recién activado: celebrarlo y pasar solo al siguiente paso
        if (step == ACTIVATE && enabled && !wasEnabled) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (step == ACTIVATE) go(BATTERY);
                }
            }, 1400);
        }
        wasEnabled = enabled;
    }

    @Override
    public void onBackPressed() {
        if (step > WELCOME) go(step - 1);
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void go(int target) {
        handler.removeCallbacksAndMessages(null);
        step = Math.max(WELCOME, Math.min(DONE, target));
        render();
        scroll.scrollTo(0, 0);
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(220).start();
    }

    private void finishOnboarding() {
        prefs.edit().putBoolean(Setup.KEY_ONBOARDING_DONE, true).apply();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    // ------------------------------------------------------------------ pintar

    private void render() {
        content.removeAllViews();
        renderDots();
        back.setVisibility(step == WELCOME ? View.INVISIBLE : View.VISIBLE);
        next.setEnabled(true);
        next.setAlpha(1f);

        switch (step) {
            case WELCOME: renderWelcome(); break;
            case ACTIVATE: renderActivate(); break;
            case BATTERY: renderBattery(); break;
            case CHOOSE: renderChoose(); break;
            case TEST: renderTest(); break;
            default: renderDone(); break;
        }
    }

    private void renderDots() {
        dots.removeAllViews();
        for (int i = 0; i < STEPS; i++) {
            View d = new View(this);
            boolean current = i == step;
            d.setBackground(ui.rounded(i <= step ? ui.accent : ui.line, 4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(current ? 22 : 8), ui.dp(8));
            lp.rightMargin = ui.dp(6);
            dots.addView(d, lp);
        }
        stepLabel.setText(step == WELCOME || step == DONE ? "" : "Paso " + step + " de " + (STEPS - 2));
    }

    private void title(String t) {
        TextView v = ui.text(t, 28, ui.fg, true);
        v.setPadding(0, ui.dp(8), 0, ui.dp(8));
        content.addView(v);
    }

    private void body(String t) {
        TextView v = ui.text(t, 16, ui.fgMuted, false);
        v.setPadding(0, 0, 0, ui.dp(16));
        content.addView(v);
    }

    /** Lista numerada dentro de una tarjeta. */
    private LinearLayout numbered(String heading, String... items) {
        LinearLayout c = ui.card(content);
        if (heading != null) {
            TextView h = ui.text(heading, 16, ui.fg, true);
            h.setPadding(0, 0, 0, ui.dp(8));
            c.addView(h);
        }
        for (int i = 0; i < items.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, ui.dp(5), 0, ui.dp(5));
            TextView n = ui.text(String.valueOf(i + 1), 13, ui.onAccent, true);
            n.setGravity(Gravity.CENTER);
            n.setBackground(ui.dot(ui.accent));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24));
            nlp.rightMargin = ui.dp(12);
            row.addView(n, nlp);
            row.addView(ui.text(items[i], 15, ui.fg, false), ui.weight());
            c.addView(row);
        }
        return c;
    }

    /** Tarjeta de resultado con un punto de color. */
    private void status(boolean good, String headline, String detail) {
        LinearLayout c = ui.card(content);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = ui.text(good ? "✓" : "!", 15, ui.onAccent, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(ui.dot(good ? ui.ok : ui.warn));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28));
        mlp.rightMargin = ui.dp(12);
        row.addView(mark, mlp);
        row.addView(ui.text(headline, 17, ui.fg, true), ui.weight());
        c.addView(row);
        if (detail != null) {
            TextView d = ui.text(detail, 14, ui.fgMuted, false);
            d.setPadding(0, ui.dp(8), 0, 0);
            c.addView(d);
        }
    }

    private Button action(String label, boolean primary, View.OnClickListener l) {
        Button b = primary ? ui.primary(label) : ui.button(label);
        b.setOnClickListener(l);
        content.addView(b, ui.fullWidth(primary ? 6 : 2));
        return b;
    }

    private void setNext(String label) {
        next.setText(label);
    }

    // ------------------------------------------------------------------ pasos

    private void renderWelcome() {
        title("Vamos a ponerla en marcha");
        body("Sin Shorts te saca automáticamente de Shorts, Reels y Spotlight, y te deja usar todo lo demás de cada app. "
                + "Te guío por los ajustes que hacen falta. Son unos 2 minutos.");
        numbered("Lo que vamos a hacer",
                "Activar la protección en los ajustes de Android",
                "Evitar que Android la detenga para ahorrar batería",
                "Elegir qué quieres bloquear",
                "Probar que funciona");
        LinearLayout c = ui.card(content);
        c.addView(ui.text("Tu privacidad", 16, ui.fg, true));
        TextView p = ui.text("La app no tiene permiso de Internet: no puede enviar nada fuera de tu teléfono. "
                + "Solo mira qué sección tienes abierta en las apps de la lista, nunca guarda lo que ves.", 14, ui.fgMuted, false);
        p.setPadding(0, ui.dp(6), 0, 0);
        c.addView(p);
        setNext("Empezar");
    }

    private void renderActivate() {
        title("Activa la protección");
        boolean enabled = Setup.serviceEnabled(this);
        if (enabled) {
            status(true, "Protección activada", "Sin Shorts ya puede reconocer los vídeos cortos.");
            setNext("Siguiente");
            return;
        }

        body("Android exige activar Sin Shorts como servicio de accesibilidad. Así puede saber si estás en Shorts o Reels.");

        int restricted = Setup.restrictedState(this);
        boolean newAndroid = Build.VERSION.SDK_INT >= 33;
        boolean justUnlocked = sawRestricted && restricted != Setup.RESTRICTED;

        if (restricted == Setup.RESTRICTED) {
            sawRestricted = true;
            // Android bloquea el interruptor en apps instaladas fuera de Play Store
            LinearLayout c = numbered("Primero, desbloquea el ajuste",
                    "Pulsa «Probar el interruptor» y toca el interruptor de Sin Shorts. Saldrá el aviso «Se ha denegado el acceso a la aplicación» "
                            + "(o «Ajuste restringido»): ciérralo y vuelve aquí con el botón Atrás.",
                    "Pulsa «Abrir info de la app», toca el menú ⋮ arriba a la derecha y elige «Permitir ajustes restringidos». "
                            + "Confirma con tu huella o PIN y vuelve aquí.",
                    "Cuando vuelvas, esta pantalla cambiará y te dirá el último paso.");
            TextView why = ui.text("Android hace esto con todas las apps que no vienen de Play Store. Es normal.", 13, ui.fgMuted, false);
            why.setPadding(0, ui.dp(8), 0, 0);
            c.addView(why);

            action(triedToggle ? "Abrir info de la app" : "Probar el interruptor", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (triedToggle) {
                        Setup.openAppInfo(OnboardingActivity.this);
                    } else {
                        triedToggle = true;
                        Setup.openServiceToggle(OnboardingActivity.this);
                    }
                }
            });
            action(triedToggle ? "Volver a probar el interruptor" : "Abrir info de la app", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (triedToggle) Setup.openServiceToggle(OnboardingActivity.this);
                    else Setup.openAppInfo(OnboardingActivity.this);
                }
            });
        } else {
            numbered(justUnlocked ? "Ajuste desbloqueado. Último paso:" : null,
                    "Pulsa «Activar».",
                    "Enciende el interruptor «Usar Sin Shorts».",
                    "En el aviso que aparece, toca «Permitir».",
                    "Vuelve aquí con el botón Atrás.");
            action("Activar", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    triedToggle = true;
                    Setup.openServiceToggle(OnboardingActivity.this);
                }
            });

            if (newAndroid && !justUnlocked) {
                // Android no siempre deja comprobarlo: ofrecer la solución por si acaso
                final LinearLayout help = numbered(null,
                        "Toca el interruptor y cierra el aviso que aparece.",
                        "Pulsa «Abrir info de la app», toca ⋮ arriba a la derecha y elige «Permitir ajustes restringidos».",
                        "Vuelve aquí y pulsa «Activar» otra vez.");
                Button b = ui.button("Abrir info de la app");
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Setup.openAppInfo(OnboardingActivity.this);
                    }
                });
                help.addView(b);
                help.setVisibility(triedToggle ? View.VISIBLE : View.GONE);
                final Button toggleHelp = ui.button("¿Sale «Se ha denegado el acceso» o «Ajuste restringido»?");
                toggleHelp.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                toggleHelp.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        help.setVisibility(help.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    }
                });
                content.removeView(help);
                content.addView(toggleHelp, ui.fullWidth(10));
                content.addView(help);
            }
        }
        setNext("Lo haré después");
    }

    private void renderBattery() {
        title("Que no se detenga");
        if (Setup.batteryUnrestricted(this)) {
            status(true, "Listo", "Android no detendrá Sin Shorts para ahorrar batería.");
            setNext("Siguiente");
            return;
        }
        body("Recomendado. Así Android no la detiene para ahorrar batería. Gasta muy poco: solo trabaja mientras usas las apps de la lista.");
        numbered(null,
                "Pulsa «Permitir».",
                "En el aviso «¿Permitir que la app se ejecute siempre en segundo plano?», toca «Permitir».");
        action("Permitir", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Setup.requestBatteryUnrestricted(OnboardingActivity.this);
            }
        });
        setNext("Omitir");
    }

    private void renderChoose() {
        title("Elige qué bloquear");
        body("Puedes cambiarlo cuando quieras desde la app.");
        LinearLayout cats = ui.card(content);
        for (int i = 0; i < Rules.CATEGORIES.length; i++) {
            final Rules.Category c = Rules.CATEGORIES[i];
            if (i > 0) ui.divider(cats);
            Switch s = new Switch(this);
            s.setText(c.title);
            s.setTextSize(16);
            s.setTextColor(ui.fg);
            s.setChecked(prefs.getBoolean(c.key, c.defaultOn));
            s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    prefs.edit().putBoolean(c.key, checked).apply();
                }
            });
            cats.addView(s, ui.fullWidth(0));
            TextView sub = ui.text(c.subtitle, 13, ui.fgMuted, false);
            sub.setPadding(0, 0, ui.dp(56), 0);
            cats.addView(sub);
        }
        setNext("Siguiente");
    }

    private void renderTest() {
        title("Pruébalo");
        boolean enabled = Setup.serviceEnabled(this);
        boolean active = prefs.getBoolean(Rules.KEY_ENABLED, true);

        if (!enabled) {
            status(false, "La protección no está activada",
                    "Sin ella no se puede bloquear nada. Vuelve al paso 1 para activarla.");
            action("Ir al paso 1", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    go(ACTIVATE);
                }
            });
            setNext("Saltar la prueba");
            return;
        }
        if (!active) {
            prefs.edit().putBoolean(Rules.KEY_ENABLED, true).apply();
        }

        body("Pulsa un botón: se abrirá un vídeo corto. Si todo va bien, Sin Shorts te sacará en un segundo y volverás aquí.");

        if (testPassed) {
            status(true, "¡Funciona!", "Sin Shorts te sacó del vídeo corto. Ya está todo preparado.");
        } else if (totalBeforeTest >= 0) {
            status(false, "No te sacó esta vez",
                    "Prueba a entrar tú en la pestaña Shorts de YouTube y espera un segundo. "
                            + "Si sigue sin funcionar, puede que la app haya cambiado: el modo diagnóstico te ayuda a arreglarlo.");
        }

        action("Abrir YouTube Shorts", !testPassed, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTest("https://www.youtube.com/shorts");
            }
        });
        action("Abrir Instagram Reels", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTest("https://www.instagram.com/reels/");
            }
        });
        setNext(testPassed ? "Siguiente" : "Saltar la prueba");
    }

    private void startTest(String url) {
        totalBeforeTest = prefs.getInt(Rules.KEY_TOTAL, 0);
        testRunning = true;
        if (!Setup.openLink(this, url)) {
            testRunning = false;
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderDone() {
        boolean enabled = Setup.serviceEnabled(this);
        title(enabled ? "¡Todo listo!" : "Casi listo");
        body(enabled
                ? "Sin Shorts ya está trabajando. No tienes que abrirla: funciona sola en segundo plano."
                : "Falta activar la protección. Sin ella no se bloquea nada.");

        LinearLayout c = ui.card(content);
        checkRow(c, enabled, "Protección activada");
        checkRow(c, Setup.batteryUnrestricted(this), "Sin restricciones de batería (recomendado)");
        checkRow(c, testPassed, "Prueba superada");

        if (!enabled) {
            action("Activar ahora", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    go(ACTIVATE);
                }
            });
        }

        LinearLayout tips = ui.card(content);
        tips.addView(ui.text("Bueno saberlo", 16, ui.fg, true));
        TextView t = ui.text("• Puedes pausar el bloqueo desde la app sin tocar los ajustes.\n"
                + "• Si alguna app se actualiza y deja de bloquearse, usa el modo diagnóstico.\n"
                + "• Puedes repetir esta guía cuando quieras desde la app.", 14, ui.fgMuted, false);
        t.setPadding(0, ui.dp(6), 0, 0);
        tips.addView(t);

        setNext("Ir a Sin Shorts");
    }

    private void checkRow(LinearLayout parent, boolean good, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(5), 0, ui.dp(5));
        TextView mark = ui.text(good ? "✓" : "–", 14, good ? ui.onAccent : ui.fgMuted, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(ui.dot(good ? ui.ok : ui.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24));
        lp.rightMargin = ui.dp(12);
        row.addView(mark, lp);
        row.addView(ui.text(label, 15, good ? ui.fg : ui.fgMuted, false), ui.weight());
        parent.addView(row);
    }
}
