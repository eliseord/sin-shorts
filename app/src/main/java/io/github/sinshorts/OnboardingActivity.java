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

    private static final int WELCOME = 0, ACTIVATE = 1, BATTERY = 2, CHOOSE = 3, TEST = 4, PROTECT = 5, DONE = 6;
    private static final int STEPS = 7;
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
        L.init(this);
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
        back = ui.button(L.t("Back", "Atrás"));
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
        next = ui.primary(L.t("Next", "Siguiente"));
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
        if (target == PROTECT && Setup.installedFromPlayStore(this)) target = target > step ? DONE : TEST;
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
        back.setText(L.t("Back", "Atrás"));
        back.setVisibility(step == WELCOME ? View.INVISIBLE : View.VISIBLE);
        next.setEnabled(true);
        next.setAlpha(1f);

        switch (step) {
            case WELCOME: renderWelcome(); break;
            case ACTIVATE: renderActivate(); break;
            case BATTERY: renderBattery(); break;
            case CHOOSE: renderChoose(); break;
            case TEST: renderTest(); break;
            case PROTECT: renderProtect(); break;
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
        stepLabel.setText(step == WELCOME || step == DONE ? "" : L.t("Step " + step + " of " + stepCount(), "Paso " + step + " de " + stepCount()));
    }

    private int stepCount() {
        return Setup.installedFromPlayStore(this) ? STEPS - 3 : STEPS - 2;
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
        languagePicker();
        title(L.t("Let’s get it running", "Vamos a ponerla en marcha"));
        body(L.t("Sin Shorts automatically takes you out of Shorts, Reels and Spotlight, and lets you use everything else in each app. "
                        + "I’ll guide you through the settings it needs. It takes about 2 minutes.",
                "Sin Shorts te saca automáticamente de Shorts, Reels y Spotlight, y te deja usar todo lo demás de cada app. "
                        + "Te guío por los ajustes que hacen falta. Son unos 2 minutos."));
        numbered(L.t("What we’ll do", "Lo que vamos a hacer"),
                L.t("Turn on protection in Android settings", "Activar la protección en los ajustes de Android"),
                L.t("Stop Android from pausing it to save battery", "Evitar que Android la detenga para ahorrar batería"),
                L.t("Choose what to block", "Elegir qué quieres bloquear"),
                L.t("Test that it works", "Probar que funciona"));
        if (!Setup.installedFromPlayStore(this)) {
            LinearLayout pp = ui.card(content);
            pp.addView(ui.text(L.t("Installed from an APK?", "¿La instalaste desde un APK?"), 16, ui.fg, true));
            TextView ppt = ui.text(L.t("You probably paused Play Protect to install it. At the end of this guide I’ll remind you to turn it back on.",
                    "Seguramente pausaste Play Protect para instalarla. Al final de esta guía te recordaré volver a activarlo."), 14, ui.fgMuted, false);
            ppt.setPadding(0, ui.dp(6), 0, 0);
            pp.addView(ppt);
        }
        LinearLayout c = ui.card(content);
        c.addView(ui.text(L.t("Your privacy", "Tu privacidad"), 16, ui.fg, true));
        TextView p = ui.text(L.t("The app has no Internet permission: it cannot send anything off your phone. "
                        + "It only checks which section you have open in the listed apps, and never stores what you see.",
                "La app no tiene permiso de Internet: no puede enviar nada fuera de tu teléfono. "
                        + "Solo mira qué sección tienes abierta en las apps de la lista, nunca guarda lo que ves."), 14, ui.fgMuted, false);
        p.setPadding(0, ui.dp(6), 0, 0);
        c.addView(p);
        setNext(L.t("Start", "Empezar"));
    }

    private void renderActivate() {
        title(L.t("Turn on protection", "Activa la protección"));
        boolean enabled = Setup.serviceEnabled(this);
        if (enabled) {
            status(true, L.t("Protection on", "Protección activada"),
                    L.t("Sin Shorts can now recognise short videos.", "Sin Shorts ya puede reconocer los vídeos cortos."));
            setNext(L.t("Next", "Siguiente"));
            return;
        }

        body(L.t("Android requires Sin Shorts to be turned on as an accessibility service. That’s how it knows when you’re in Shorts or Reels.",
                "Android exige activar Sin Shorts como servicio de accesibilidad. Así puede saber si estás en Shorts o Reels."));

        int restricted = Setup.restrictedState(this);
        boolean newAndroid = Build.VERSION.SDK_INT >= 33;
        boolean justUnlocked = sawRestricted && restricted != Setup.RESTRICTED;

        if (restricted == Setup.RESTRICTED) {
            sawRestricted = true;
            // Android bloquea el interruptor en apps instaladas fuera de Play Store
            LinearLayout c = numbered(L.t("First, unlock the setting", "Primero, desbloquea el ajuste"),
                    L.t("Tap “Try the switch” and tap the Sin Shorts switch. You’ll see “App was denied access” "
                                    + "(or “Restricted setting”): close it and come back here with the Back button.",
                            "Pulsa «Probar el interruptor» y toca el interruptor de Sin Shorts. Saldrá el aviso «Se ha denegado el acceso a la aplicación» "
                                    + "(o «Ajuste restringido»): ciérralo y vuelve aquí con el botón Atrás."),
                    L.t("Tap “Open app info”, tap the ⋮ menu at the top right and choose “Allow restricted settings”. "
                                    + "Confirm with your fingerprint or PIN and come back here.",
                            "Pulsa «Abrir info de la app», toca el menú ⋮ arriba a la derecha y elige «Permitir ajustes restringidos». "
                                    + "Confirma con tu huella o PIN y vuelve aquí."),
                    L.t("When you return, this screen will change and show you the last step.",
                            "Cuando vuelvas, esta pantalla cambiará y te dirá el último paso."));
            TextView why = ui.text(L.t("Android does this with every app that doesn’t come from the Play Store. It’s normal.",
                    "Android hace esto con todas las apps que no vienen de Play Store. Es normal."), 13, ui.fgMuted, false);
            why.setPadding(0, ui.dp(8), 0, 0);
            c.addView(why);

            action(triedToggle ? L.t("Open app info", "Abrir info de la app") : L.t("Try the switch", "Probar el interruptor"), true, new View.OnClickListener() {
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
            action(triedToggle ? L.t("Try the switch again", "Volver a probar el interruptor") : L.t("Open app info", "Abrir info de la app"), false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (triedToggle) Setup.openServiceToggle(OnboardingActivity.this);
                    else Setup.openAppInfo(OnboardingActivity.this);
                }
            });
        } else {
            numbered(justUnlocked ? L.t("Setting unlocked. Last step:", "Ajuste desbloqueado. Último paso:") : null,
                    L.t("Tap “Turn on”.", "Pulsa «Activar»."),
                    L.t("Turn on the “Use Sin Shorts” switch.", "Enciende el interruptor «Usar Sin Shorts»."),
                    L.t("In the dialog that appears, tap “Allow”.", "En el aviso que aparece, toca «Permitir»."),
                    L.t("Come back here with the Back button.", "Vuelve aquí con el botón Atrás."));
            action(L.t("Turn on", "Activar"), true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    triedToggle = true;
                    Setup.openServiceToggle(OnboardingActivity.this);
                }
            });

            if (newAndroid && !justUnlocked) {
                // Android 13+ bloquea este ajuste en apps instaladas fuera de Play Store, y no siempre
                // se puede detectar: mostrar siempre cómo desbloquearlo, sin esconderlo
                LinearLayout help = numbered(L.t("If the switch is greyed out or you see “App was denied access”",
                                "Si el interruptor está gris o sale «Se ha denegado el acceso a la aplicación»"),
                        L.t("Close that dialog. Android does this with apps that don’t come from the Play Store.",
                                "Cierra ese aviso. Android lo hace con las apps que no vienen de Play Store."),
                        L.t("Tap “Open app info” below.", "Pulsa «Abrir info de la app» aquí abajo."),
                        L.t("Tap the ⋮ menu at the top right and choose “Allow restricted settings”. Confirm with your fingerprint or PIN.",
                                "Toca el menú ⋮ arriba a la derecha y elige «Permitir ajustes restringidos». Confirma con tu huella o PIN."),
                        L.t("Come back here and tap “Turn on” again: the switch will now work.",
                                "Vuelve aquí y pulsa «Activar» otra vez: ahora el interruptor funcionará."));
                Button b = ui.button(L.t("Open app info", "Abrir info de la app"));
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Setup.openAppInfo(OnboardingActivity.this);
                    }
                });
                help.addView(b);
                TextView note = ui.text(L.t("The ⋮ option only appears after you’ve tried the switch once.",
                        "La opción del menú ⋮ solo aparece después de haber probado el interruptor una vez."), 13, ui.fgMuted, false);
                help.addView(note);
            }
        }
        setNext(L.t("Do it later", "Lo haré después"));
    }

    private void renderBattery() {
        title(L.t("Keep it running", "Que no se detenga"));
        if (Setup.batteryUnrestricted(this)) {
            status(true, L.t("Done", "Listo"), L.t("Android won’t stop Sin Shorts to save battery.",
                    "Android no detendrá Sin Shorts para ahorrar batería."));
            setNext(L.t("Next", "Siguiente"));
            return;
        }
        body(L.t("Recommended, so Android doesn’t stop it to save battery. It uses very little: it only works while you use the listed apps.",
                "Recomendado. Así Android no la detiene para ahorrar batería. Gasta muy poco: solo trabaja mientras usas las apps de la lista."));
        numbered(null,
                L.t("Tap “Allow”.", "Pulsa «Permitir»."),
                L.t("In the “Let app always run in background?” dialog, tap “Allow”.",
                        "En el aviso «¿Permitir que la app se ejecute siempre en segundo plano?», toca «Permitir»."));
        action(L.t("Allow", "Permitir"), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Setup.requestBatteryUnrestricted(OnboardingActivity.this);
            }
        });
        setNext(L.t("Skip", "Omitir"));
    }

    private void renderChoose() {
        title(L.t("Choose what to block", "Elige qué bloquear"));
        body(L.t("You can change this any time from the app.", "Puedes cambiarlo cuando quieras desde la app."));
        LinearLayout cats = ui.card(content);
        for (int i = 0; i < Rules.CATEGORIES.length; i++) {
            final Rules.Category c = Rules.CATEGORIES[i];
            if (i > 0) ui.divider(cats);
            Switch s = new Switch(this);
            s.setText(c.title());
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
            TextView sub = ui.text(c.subtitle(), 13, ui.fgMuted, false);
            sub.setPadding(0, 0, ui.dp(56), 0);
            cats.addView(sub);
        }
        setNext(L.t("Next", "Siguiente"));
    }

    private void renderTest() {
        title(L.t("Try it", "Pruébalo"));
        boolean enabled = Setup.serviceEnabled(this);
        boolean active = prefs.getBoolean(Rules.KEY_ENABLED, true);

        if (!enabled) {
            status(false, L.t("Protection is off", "La protección no está activada"),
                    L.t("Nothing can be blocked without it. Go back to step 1 to turn it on.",
                            "Sin ella no se puede bloquear nada. Vuelve al paso 1 para activarla."));
            action(L.t("Go to step 1", "Ir al paso 1"), true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    go(ACTIVATE);
                }
            });
            setNext(L.t("Skip the test", "Saltar la prueba"));
            return;
        }
        if (!active) {
            prefs.edit().putBoolean(Rules.KEY_ENABLED, true).apply();
        }

        body(L.t("Tap a button: a short video will open. If all goes well, Sin Shorts will take you out within a second and you’ll be back here.",
                "Pulsa un botón: se abrirá un vídeo corto. Si todo va bien, Sin Shorts te sacará en un segundo y volverás aquí."));

        if (testPassed) {
            status(true, L.t("It works!", "¡Funciona!"), L.t("Sin Shorts took you out of the short video. You’re all set.",
                    "Sin Shorts te sacó del vídeo corto. Ya está todo preparado."));
        } else if (totalBeforeTest >= 0) {
            status(false, L.t("It didn’t take you out this time", "No te sacó esta vez"),
                    L.t("Try opening the Shorts tab in YouTube yourself and wait a second. "
                                    + "If it still doesn’t work, the app may have changed: diagnostic mode helps you fix it.",
                            "Prueba a entrar tú en la pestaña Shorts de YouTube y espera un segundo. "
                                    + "Si sigue sin funcionar, puede que la app haya cambiado: el modo diagnóstico te ayuda a arreglarlo."));
        }

        action(L.t("Open YouTube Shorts", "Abrir YouTube Shorts"), !testPassed, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTest("https://www.youtube.com/shorts");
            }
        });
        action(L.t("Open Instagram Reels", "Abrir Instagram Reels"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTest("https://www.instagram.com/reels/");
            }
        });
        setNext(testPassed ? L.t("Next", "Siguiente") : L.t("Skip the test", "Saltar la prueba"));
    }

    private void startTest(String url) {
        totalBeforeTest = prefs.getInt(Rules.KEY_TOTAL, 0);
        testRunning = true;
        if (!Setup.openLink(this, url)) {
            testRunning = false;
            Toast.makeText(this, L.t("Couldn’t open the link", "No se pudo abrir el enlace"), Toast.LENGTH_SHORT).show();
        }
    }

    private void renderProtect() {
        title(L.t("Turn Play Protect back on", "Vuelve a activar Play Protect"));
        body(L.t("To install Sin Shorts from an APK you had to pause Play Protect. Turn it back on now: it protects you from "
                        + "harmful apps and doesn’t affect Sin Shorts once it’s installed.",
                "Para instalar Sin Shorts desde un APK tuviste que pausar Play Protect. Vuelve a activarlo ahora: te protege de "
                        + "apps dañinas y no afecta a Sin Shorts una vez instalada."));
        numbered(null,
                L.t("Tap “Open Play Protect”.", "Pulsa «Abrir Play Protect»."),
                L.t("Tap the ⚙ gear at the top right.", "Toca el engranaje ⚙ arriba a la derecha."),
                L.t("Turn on “Scan apps with Play Protect”.", "Activa «Analizar apps con Play Protect»."),
                L.t("Come back here with the Back button.", "Vuelve aquí con el botón Atrás."));
        action(L.t("Open Play Protect", "Abrir Play Protect"), true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Setup.openPlayProtect(OnboardingActivity.this)) {
                    Toast.makeText(OnboardingActivity.this, L.t("Open the Play Store › your profile picture › Play Protect",
                            "Abre Play Store › tu foto de perfil › Play Protect"), Toast.LENGTH_LONG).show();
                }
            }
        });
        TextView note = ui.text(L.t("If Play Protect later shows a notice about Sin Shorts, it’s because of the accessibility permission. "
                        + "You can keep the app.",
                "Si más adelante Play Protect muestra un aviso sobre Sin Shorts, es por el permiso de accesibilidad. "
                        + "Puedes conservar la app."), 13, ui.fgMuted, false);
        note.setPadding(0, ui.dp(14), 0, 0);
        content.addView(note);
        setNext(L.t("Done, next", "Hecho, seguir"));
    }

    private void renderDone() {
        boolean enabled = Setup.serviceEnabled(this);
        title(enabled ? L.t("All set!", "¡Todo listo!") : L.t("Almost there", "Casi listo"));
        body(enabled
                ? L.t("Sin Shorts is already working. You don’t need to open it: it runs on its own in the background.",
                        "Sin Shorts ya está trabajando. No tienes que abrirla: funciona sola en segundo plano.")
                : L.t("Protection still needs to be turned on. Nothing is blocked without it.",
                        "Falta activar la protección. Sin ella no se bloquea nada."));

        LinearLayout c = ui.card(content);
        checkRow(c, enabled, L.t("Protection on", "Protección activada"));
        checkRow(c, Setup.batteryUnrestricted(this), L.t("No battery restrictions (recommended)", "Sin restricciones de batería (recomendado)"));
        checkRow(c, testPassed, L.t("Test passed", "Prueba superada"));

        if (!enabled) {
            action(L.t("Turn on now", "Activar ahora"), true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    go(ACTIVATE);
                }
            });
        }

        LinearLayout tips = ui.card(content);
        tips.addView(ui.text(L.t("Good to know", "Bueno saberlo"), 16, ui.fg, true));
        TextView t = ui.text(L.t("• You can pause blocking from the app without touching settings.\n"
                        + "• If an app updates and stops being blocked, use diagnostic mode.\n"
                        + "• You can repeat this guide any time from the app.",
                "• Puedes pausar el bloqueo desde la app sin tocar los ajustes.\n"
                        + "• Si alguna app se actualiza y deja de bloquearse, usa el modo diagnóstico.\n"
                        + "• Puedes repetir esta guía cuando quieras desde la app."), 14, ui.fgMuted, false);
        t.setPadding(0, ui.dp(6), 0, 0);
        tips.addView(t);

        setNext(L.t("Go to Sin Shorts", "Ir a Sin Shorts"));
    }

    /** English / Español, at the top of the first step. */
    private void languagePicker() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ui.text("Language · Idioma", 13, ui.fgMuted, false), ui.weight());
        row.addView(langButton("English", false));
        row.addView(langButton("Español", true));
        content.addView(row, ui.fullWidth(0));
    }

    private Button langButton(String label, final boolean spanish) {
        boolean on = L.es() == spanish;
        Button b = on ? ui.primary(label) : ui.button(label);
        b.setTextSize(14);
        b.setMinHeight(ui.dp(38));
        b.setMinimumHeight(ui.dp(38));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (L.es() == spanish) return;
                L.set(OnboardingActivity.this, spanish);
                next.setText(L.t("Next", "Siguiente"));
                render();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = ui.dp(6);
        b.setLayoutParams(lp);
        return b;
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
