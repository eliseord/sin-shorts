package io.github.sinshorts;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Servicio de accesibilidad que vigila las apps configuradas y, cuando reconoce
 * una sección de vídeos cortos, pulsa "Atrás" por ti. Si la app insiste en volver
 * a mostrarla, te manda a la pantalla de inicio.
 *
 * En Instagram además puede tapar el feed de inicio (sugerencias sin fin) y
 * Explorar con una «cortina», dejando libres historias, mensajes y búsqueda.
 */
public class BlockerService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener, Curtain.Actions {

    private static final long SCAN_DELAY_STATE = 120;    // ms tras cambiar de pantalla
    private static final long SCAN_DELAY_CONTENT = 300;  // ms de margen mientras hay cambios
    private static final long ACTION_COOLDOWN = 700;     // ms mínimos entre dos "Atrás"
    private static final long RETRY_WINDOW = 4000;       // ventana para contar reintentos
    private static final int BACKS_BEFORE_HOME = 3;
    private static final long DIAG_INTERVAL = 1000;
    private static final int DIAG_MAX_NODES = 2500;

    private static final String IG = Rules.INSTAGRAM;
    private static final String IG_ID = IG + ":id/";

    /** Cómo se llama el feed de seguidos en el menú del logo, según el idioma. */
    private static final String[] FOLLOWING_LABELS = {
            "Siguiendo", "Following", "Seguindo", "Abonnements", "Gefolgt", "Seguiti", "Persone che segui", "Volgend",
    };

    /** La otra opción del mismo menú: si se ve junto a «Siguiendo», el menú está abierto. */
    private static final String[] FAVORITES_LABELS = {
            "Favoritos", "Favorites", "Favoris", "Favoriten", "Preferiti", "Favorieten",
    };

    private static final String[] URL_BAR_IDS = {
            "url_bar",                          // Chrome, Brave, Edge
            "mozac_browser_toolbar_url_view",   // Firefox
            "omnibarTextInput",                 // DuckDuckGo
            "location_bar_edit_text",
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private Curtain curtain;
    private Notice notice;

    private volatile List<Rules.Rule> rules;
    private volatile Set<String> watched = new HashSet<String>();
    private volatile boolean enabled, toast, diag, igFollowing, igExplore;

    private boolean scanPending;
    private long lastAction, retryStart;
    private int retries;
    private long curtainPausedUntil;


    private long lastDiag;
    private final Set<String> ignoredForDiag = new HashSet<String>();

    /** Tras un cambio en Instagram, volver a mirar unas veces aunque no lleguen más eventos. */
    private int settleRuns;
    private final Runnable settleTask = new Runnable() {
        @Override
        public void run() {
            scan();
        }
    };

    private final Runnable scanTask = new Runnable() {
        @Override
        public void run() {
            scanPending = false;
            scan();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        prefs = getSharedPreferences(Rules.PREFS, MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);
        curtain = new Curtain(this, this);
        notice = new Notice(this);
        reload();

        ignoredForDiag.add(getPackageName());
        ignoredForDiag.add("com.android.systemui");
        ignoredForDiag.add("com.android.settings");
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, 0)) {
                ignoredForDiag.add(ri.activityInfo.packageName);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Rules.KEY_COUNT.equals(key) || Rules.KEY_TOTAL.equals(key) || Rules.KEY_COUNT_DAY.equals(key)) {
            return;
        }
        reload();
    }

    private void reload() {
        L.init(this);
        List<Rules.Rule> r = Rules.load(prefs);
        Set<String> pk = new HashSet<String>();
        for (Rules.Rule rule : r) pk.addAll(rule.packages);
        igFollowing = prefs.getBoolean(Rules.KEY_IG_FOLLOWING, true);
        igExplore = prefs.getBoolean(Rules.KEY_IG_EXPLORE, true);
        if (igFollowing || igExplore) pk.add(IG);
        rules = r;
        watched = pk;
        enabled = prefs.getBoolean(Rules.KEY_ENABLED, true);
        toast = prefs.getBoolean(Rules.KEY_TOAST, true);
        diag = prefs.getBoolean(Rules.KEY_DIAG, false);
        if (curtain != null && (!enabled || (!igFollowing && !igExplore))) curtain.hide();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        // Si cambias a otra app (o bajas las notificaciones), quitar la cortina
        if (curtain != null && curtain.mode() != Curtain.NONE
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && !pkg.equals(IG) && !pkg.equals(getPackageName())) {
            curtain.hide();
        }

        if (diag && !ignoredForDiag.contains(pkg)) {
            long now = SystemClock.uptimeMillis();
            if (now - lastDiag > DIAG_INTERVAL) {
                lastDiag = now;
                captureDiagnostics();
            }
        }

        if (!enabled || !watched.contains(pkg)) return;

        if (pkg.equals(IG)) {
            settleRuns = 0;
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED && curtain.mode() != Curtain.NONE) {
                handler.removeCallbacks(scanTask);
                scanPending = true;
                if (isHomeTabClick(event)) {
                    // Tocar «Inicio» te deja en el inicio: la cortina se queda, solo se recoloca
                    handler.postDelayed(scanTask, 80);
                } else {
                    // Mensajes, historias, otra pestaña…: fuera la cortina ya; luego se comprueba
                    curtain.hide();
                    softHideSince = 0;
                    handler.postDelayed(scanTask, 250);
                }
                return;
            }
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handler.removeCallbacks(scanTask);
            scanPending = true;
            handler.postDelayed(scanTask, pkg.equals(IG) ? 60 : SCAN_DELAY_STATE);
        } else if (!scanPending) {
            scanPending = true;
            handler.postDelayed(scanTask, SCAN_DELAY_CONTENT);
        }
    }

    private void scan() {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (Exception e) {
            return;
        }
        if (root == null) return;
        try {
            CharSequence cs = root.getPackageName();
            if (cs == null) return;
            String pkg = cs.toString();
            if (!enabled) {
                curtain.hide();
                return;
            }
            if (!watched.contains(pkg)) {
                // Puede ser un instante de una animación: confirmar antes de quitarla
                if (!pkg.equals(getPackageName())) softHide();
                return;
            }

            List<Rules.Rule> current = rules;
            for (Rules.Rule rule : current) {
                if (!rule.packages.contains(pkg)) continue;
                if (matches(root, pkg, rule)) {
                    curtain.hide();
                    block(rule);
                    return;
                }
            }

            if (pkg.equals(IG)) updateCurtain(root);
            else curtain.hide();
        } catch (Exception ignored) {
            // La pantalla cambió mientras la leíamos; el siguiente evento lo reintentará.
        }
    }

    private boolean matches(AccessibilityNodeInfo root, String pkg, Rules.Rule rule) {
        switch (rule.type) {
            case Rules.APP:
                return true;

            case Rules.ID:
            case Rules.ID_SEL: {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(fullId(pkg, rule.value));
                if (nodes == null) return false;
                for (AccessibilityNodeInfo n : nodes) {
                    if (n == null || !n.isVisibleToUser()) continue;
                    if (rule.type == Rules.ID || isSelected(n)) return true;
                }
                return false;
            }

            case Rules.TEXT:
            case Rules.TEXT_SEL: {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(rule.value);
                if (nodes == null) return false;
                for (AccessibilityNodeInfo n : nodes) {
                    if (n == null || !n.isVisibleToUser()) continue;
                    if (rule.type == Rules.TEXT || isSelected(n)) return true;
                }
                return false;
            }

            case Rules.URL: {
                for (String id : URL_BAR_IDS) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(pkg + ":id/" + id);
                    if (nodes == null) continue;
                    for (AccessibilityNodeInfo n : nodes) {
                        if (n == null || n.isFocused()) continue; // no molestar mientras escribes
                        CharSequence t = n.getText();
                        if (t != null && t.toString().toLowerCase(Locale.ROOT).contains(rule.value)) return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    private static String fullId(String pkg, String value) {
        return value.contains(":id/") ? value : pkg + ":id/" + value;
    }

    /** Una pestaña suele marcar como seleccionado el propio nodo, su contenedor o un hijo. */
    private static boolean isSelected(AccessibilityNodeInfo n) {
        if (n.isSelected()) return true;
        AccessibilityNodeInfo parent = n.getParent();
        if (parent != null && parent.isSelected()) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c == null) continue;
            if (c.isSelected()) return true;
            for (int j = 0; j < c.getChildCount(); j++) {
                AccessibilityNodeInfo g = c.getChild(j);
                if (g != null && g.isSelected()) return true;
            }
        }
        return false;
    }

    private void block(Rules.Rule rule) {
        long now = SystemClock.uptimeMillis();
        if (now - lastAction < ACTION_COOLDOWN) {
            scheduleRecheck();
            return;
        }
        if (now - retryStart > RETRY_WINDOW) {
            retryStart = now;
            retries = 0;
        }
        retries++;
        lastAction = now;

        boolean goHome = rule.type == Rules.APP || retries >= BACKS_BEFORE_HOME;
        performGlobalAction(goHome ? GLOBAL_ACTION_HOME : GLOBAL_ACTION_BACK);
        if (goHome) retries = 0;

        countBlock();
        if (toast) {
            notice.show(goHome && rule.type == Rules.APP
                    ? L.t("Sin Shorts: app blocked", "Sin Shorts: app bloqueada")
                    : L.t("Sin Shorts: short video blocked", "Sin Shorts: vídeo corto bloqueado"), 1800);
        }
        scheduleRecheck();
    }

    /** Comprobar otra vez poco después por si "Atrás" no bastó. */
    private void scheduleRecheck() {
        handler.removeCallbacks(scanTask);
        scanPending = true;
        handler.postDelayed(scanTask, ACTION_COOLDOWN + 100);
    }

    private void countBlock() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        int count = today.equals(prefs.getString(Rules.KEY_COUNT_DAY, "")) ? prefs.getInt(Rules.KEY_COUNT, 0) : 0;
        prefs.edit()
                .putString(Rules.KEY_COUNT_DAY, today)
                .putInt(Rules.KEY_COUNT, count + 1)
                .putInt(Rules.KEY_TOTAL, prefs.getInt(Rules.KEY_TOTAL, 0) + 1)
                .apply();
    }

    // ------------------------------------------------------- Instagram: cortinas

    private void updateCurtain(AccessibilityNodeInfo root) {
        if (settleRuns < 3) {
            settleRuns++;
            handler.removeCallbacks(settleTask);
            handler.postDelayed(settleTask, settleRuns == 1 ? 250 : 500);
        }
        updateCurtainNow(root);
    }

    private void updateCurtainNow(AccessibilityNodeInfo root) {
        if (SystemClock.uptimeMillis() < curtainPausedUntil) {
            curtain.hide();
            return;
        }
        // Con el menú del logo abierto (Siguiendo / Favoritos), no taparlo
        if (igFollowing && menuOpen()) {
            curtain.hide();
            return;
        }
        Rect win = new Rect();
        root.getBoundsInScreen(win);

        // Visor de historias abierto: nunca taparlo
        if (storyViewerOpen()) {
            curtain.hide();
            return;
        }

        // Una pantalla con botón «Atrás» (feed Siguiendo, un post, mensajes…) nunca se tapa
        if (visible(root, "action_bar_button_back") != null) {
            curtain.hide();
            softHideSince = 0;
            return;
        }
        AccessibilityNodeInfo tabBar = visible(root, "tab_bar");
        int bottom = tabBar != null ? boundsOf(tabBar).top : win.bottom;

        if (igFollowing && selected(root, "feed_tab")) {
            AccessibilityNodeInfo header = visible(root, "main_feed_action_bar");
            AccessibilityNodeInfo stories = visible(root, "reels_tray_container");
            boolean feedVisible = header != null || stories != null || visible(root, "row_feed_profile_header") != null;
            if (feedVisible) {
                int top = topLimit(win);
                if (header != null) top = Math.max(top, boundsOf(header).bottom);
                if (stories != null) top = Math.max(top, boundsOf(stories).bottom);
                showCurtain(Curtain.HOME_FEED, new Rect(win.left, top, win.right, bottom));
                return;
            }
        }

        if (igExplore && selected(root, "search_tab") && visible(root, "grid_card_layout_container") != null) {
            AccessibilityNodeInfo field = visible(root, "action_bar_search_edit_text");
            if (field == null || !field.isFocused()) {
                int top = topLimit(win);
                AccessibilityNodeInfo bar = visible(root, "explore_action_bar");
                if (bar != null) top = Math.max(top, boundsOf(bar).bottom);
                showCurtain(Curtain.EXPLORE, new Rect(win.left, top, win.right, bottom));
                return;
            }
        }
        // Ni Inicio ni Explorar (historias, mensajes, otra pestaña…): quitarla al instante
        curtain.hide();
        softHideSince = 0;
    }

    private void showCurtain(int mode, Rect r) {
        if (r.height() < dp(120)) {
            curtain.hide();
            return;
        }
        softHideSince = 0;
        curtain.show(mode, r);
    }

    /**
     * Quitar la cortina solo si la situación dudosa (mitad de una animación, la
     * pantalla aún sin dibujar…) dura más de un instante. Evita que parpadee.
     */
    private long softHideSince;
    private static final long SOFT_HIDE_CONFIRM = 300;

    private void softHide() {
        if (curtain.mode() == Curtain.NONE) {
            softHideSince = 0;
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (softHideSince == 0) {
            softHideSince = now;
            handler.removeCallbacks(settleTask);
            handler.postDelayed(settleTask, SOFT_HIDE_CONFIRM + 20);
        } else if (now - softHideSince >= SOFT_HIDE_CONFIRM) {
            softHideSince = 0;
            curtain.hide();
        }
    }

    /** IDs del visor de historias de Instagram (internamente las historias se llaman «reel»). */
    private static final String[] STORY_VIEWER_IDS = {
            "reel_viewer_root", "reel_viewer_media_container", "story_viewer_container", "reel_viewer_progress_bar",
    };

    private boolean storyViewerOpen() {
        for (AccessibilityNodeInfo r : instagramRoots()) {
            for (String id : STORY_VIEWER_IDS) {
                if (visible(r, id) != null) return true;
            }
        }
        return false;
    }

    /** Tocaste fuera de la cortina: quitarla en ese mismo instante y comprobar poco después. */
    @Override
    public void onOutsideTouch() {
        settleRuns = 0;
        pauseCurtain(400);
    }

    private static boolean isHomeTabClick(AccessibilityEvent event) {
        try {
            AccessibilityNodeInfo src = event.getSource();
            if (src == null) return false;
            for (int i = 0; i < 3 && src != null; i++) {
                String id = src.getViewIdResourceName();
                if (id != null && id.endsWith(":id/feed_tab")) return true;
                src = src.getParent();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** No tapar nunca la barra de estado. */
    private int topLimit(Rect win) {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int status = id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
        return Math.max(win.top, status);
    }

    private AccessibilityNodeInfo visible(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(IG_ID + id);
        if (nodes == null) return null;
        for (AccessibilityNodeInfo n : nodes) {
            if (n != null && n.isVisibleToUser()) return n;
        }
        return null;
    }

    private boolean selected(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(IG_ID + id);
        if (nodes == null) return false;
        for (AccessibilityNodeInfo n : nodes) {
            if (n != null && n.isVisibleToUser() && isSelected(n)) return true;
        }
        return false;
    }

    private static Rect boundsOf(AccessibilityNodeInfo n) {
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        return r;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Botón «Abrir Siguiendo»: toca el logo de Instagram y luego «Siguiendo» en el menú. */
    @Override
    public void openFollowingFeed() {
        // Quitar la cortina primero: el menú del logo se abre justo encima del feed
        pauseCurtain(4500);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                tapLogo(true);
            }
        }, 50);
    }

    private void tapLogo(boolean firstTry) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo header = visible(root, "main_feed_action_bar");
        if (header == null) {
            // La cabecera se esconde al bajar: tocar «Inicio» vuelve arriba y la muestra
            AccessibilityNodeInfo homeTab = visible(root, "feed_tab");
            if (firstTry && homeTab != null && tapNode(homeTab)) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        tapLogo(false);
                    }
                }, 700);
                return;
            }
            recordDebug(L.t("Header main_feed_action_bar not found.\n", "No se encontró la cabecera main_feed_action_bar.\n") + describe(root, 60));
            manualFollowingHint();
            return;
        }
        AccessibilityNodeInfo logo = findLogo(header);
        if (logo == null) {
            recordDebug(L.t("Logo not found in the header.\n", "No se encontró el logo en la cabecera.\n") + describe(header, 40));
            manualFollowingHint();
            return;
        }
        // Lo que ya pone «Siguiendo» antes de tocar el logo NO es el menú: nunca se toca
        final Set<String> before = followingMatches();
        if (!tapNode(logo)) {
            manualFollowingHint();
            return;
        }
        final String headerInfo = L.t("Logo tapped: ", "Logo tocado: ") + nodeLine(logo) + "\n" + describe(header, 30);
        MenuAttempt m = new MenuAttempt();
        m.deadline = SystemClock.uptimeMillis() + 3200;
        m.started = SystemClock.uptimeMillis();
        m.before = before;
        m.headerInfo = headerInfo;
        m.logo = boundsOf(logo);
        pollFollowingOption(m, MENU_FIRST_CHECK);
    }

    private static final long MENU_FIRST_CHECK = 120;  // ms: lo antes que suele empezar a verse el menú
    private static final long MENU_CHECK_EVERY = 70;   // ms entre comprobaciones
    private static final long TAP_RESULT_WAIT = 350;   // ms para ver si el toque cerró el menú
    private static final long LOGO_RETRY_AFTER = 1100; // ms sin menú → volver a tocar el logo

    /** Estado de un intento de abrir «Siguiendo». */
    private static final class MenuAttempt {
        long deadline, started, lastTapAt;
        Set<String> before;
        String headerInfo;
        Rect logo;
        String lastBounds;  // posición de «Siguiendo» en la lectura anterior
        int taps;
        boolean logoRetried;
    }

    /**
     * Espera a que el menú del logo esté abierto y QUIETO (misma posición en dos
     * lecturas seguidas: la animación de apertura ya terminó), toca «Siguiendo»
     * y comprueba que el menú se cerró; si no, lo reintenta.
     */
    private void pollFollowingOption(final MenuAttempt m, long delay) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long now = SystemClock.uptimeMillis();

                if (m.taps > 0) {
                    // Ya se tocó: ¿se cerró el menú?
                    if (!menuOpen()) {
                        pauseCurtain(1500);
                        return;
                    }
                    if (now - m.lastTapAt < TAP_RESULT_WAIT) {
                        retry();
                        return;
                    }
                }

                AccessibilityNodeInfo option = m.taps == 0 ? findFollowingOption(m.before) : firstFollowingLabel();
                if (option != null) {
                    String bounds = boundsOf(option).flattenToString();
                    if (bounds.equals(m.lastBounds) && m.taps < 3) {
                        // Primer intento: toque como un dedo. Siguientes: acción de accesibilidad.
                        boolean ok = m.taps == 0 ? tapNode(option) : (click(option) || tapNode(option));
                        if (ok) {
                            m.taps++;
                            m.lastTapAt = now;
                        }
                    }
                    m.lastBounds = bounds;
                } else if (m.taps == 0 && !m.logoRetried && now - m.started > LOGO_RETRY_AFTER) {
                    // El menú no llegó a abrirse: tocar el logo otra vez
                    m.logoRetried = true;
                    tapAt(m.logo);
                }

                if (now < m.deadline) {
                    retry();
                    return;
                }
                recordDebug(L.t("“Following” didn’t open after tapping the logo (taps on the option: ",
                        "No se abrió «Siguiendo» tras tocar el logo (toques en la opción: ") + m.taps + ").\n"
                        + m.headerInfo + L.t("\nWindows:\n", "\nVentanas:\n") + describeWindows());
                manualFollowingHint();
            }

            private void retry() {
                pollFollowingOption(m, MENU_CHECK_EVERY);
            }
        }, delay);
    }

    /** «Siguiendo» del menú ya abierto (para reintentar el toque). */
    private AccessibilityNodeInfo firstFollowingLabel() {
        List<AccessibilityNodeInfo> nodes = labelNodes(FOLLOWING_LABELS);
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    private boolean tapAt(Rect b) {
        if (b == null || b.isEmpty() || android.os.Build.VERSION.SDK_INT < 24) return false;
        try {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(b.centerX(), b.centerY());
            return dispatchGesture(new android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 60))
                    .build(), null, null);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * El logo de la cabecera: primero lo que se describe como «Instagram» o tiene «logo»
     * en su ID; si no, el elemento más ancho que no sea la cabecera entera (el logotipo
     * es bastante más ancho que los iconos de corazón o mensajes).
     */
    private AccessibilityNodeInfo findLogo(AccessibilityNodeInfo header) {
        Rect hb = boundsOf(header);
        AccessibilityNodeInfo byName = null, byId = null, widest = null;
        int widestW = 0;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<AccessibilityNodeInfo>();
        q.add(header);
        int seen = 0;
        while (!q.isEmpty() && seen < 200) {
            AccessibilityNodeInfo n = q.poll();
            seen++;
            if (n != header && n.isVisibleToUser()) {
                CharSequence d = n.getContentDescription();
                CharSequence t = n.getText();
                String s = ((d != null ? d : "") + " " + (t != null ? t : "")).toLowerCase(Locale.ROOT);
                String id = n.getViewIdResourceName();
                Rect b = boundsOf(n);
                if (byName == null && s.contains("instagram")) byName = n;
                if (byId == null && id != null && id.contains("logo")) byId = n;
                int w = b.width();
                if (w > widestW && w >= hb.width() * 0.15 && w <= hb.width() * 0.75 && b.height() <= hb.height()) {
                    widest = n;
                    widestW = w;
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) q.add(c);
            }
        }
        if (byName != null) return byName;
        if (byId != null) return byId;
        return widest;
    }

    /** Ventanas de Instagram en pantalla (el menú del logo puede ser una ventana aparte). */
    private List<AccessibilityNodeInfo> instagramRoots() {
        List<AccessibilityNodeInfo> roots = new ArrayList<AccessibilityNodeInfo>();
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && active.getPackageName() != null && IG.equals(active.getPackageName().toString())) {
            roots.add(active);
        }
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null || r.getPackageName() == null || !IG.equals(r.getPackageName().toString())) continue;
                if (active != null && r.equals(active)) continue;
                roots.add(r);
            }
        } catch (Exception ignored) {
        }
        return roots;
    }

    /** Nodos visibles cuyo texto es una de las etiquetas («Siguiendo» a secas, no «Siguiendo a…»). */
    private List<AccessibilityNodeInfo> labelNodes(String[] labels) {
        List<AccessibilityNodeInfo> out = new ArrayList<AccessibilityNodeInfo>();
        for (AccessibilityNodeInfo r : instagramRoots()) {
            for (String label : labels) {
                List<AccessibilityNodeInfo> nodes = r.findAccessibilityNodeInfosByText(label);
                if (nodes == null) continue;
                String want = label.toLowerCase(Locale.ROOT);
                for (AccessibilityNodeInfo n : nodes) {
                    if (n == null || !n.isVisibleToUser()) continue;
                    CharSequence t = n.getText() != null ? n.getText() : n.getContentDescription();
                    if (t == null) continue;
                    String s = t.toString().trim().toLowerCase(Locale.ROOT);
                    if (s.equals(want) || s.startsWith(want + ",")) out.add(n);
                }
            }
        }
        return out;
    }

    private Set<String> followingMatches() {
        Set<String> out = new HashSet<String>();
        for (AccessibilityNodeInfo n : labelNodes(FOLLOWING_LABELS)) out.add(boundsOf(n).flattenToString());
        return out;
    }

    /** «Siguiendo» que ha aparecido después de tocar el logo (el del menú). */
    private AccessibilityNodeInfo findFollowingOption(Set<String> before) {
        List<AccessibilityNodeInfo> nodes = labelNodes(FOLLOWING_LABELS);
        if (nodes.isEmpty()) return null;
        for (AccessibilityNodeInfo n : nodes) {
            if (!before.contains(boundsOf(n).flattenToString())) return n;
        }
        // Instagram puede reutilizar el menú de otra vez: si se ve también «Favoritos», es el menú
        if (!labelNodes(FAVORITES_LABELS).isEmpty()) return nodes.get(0);
        return null;
    }

    /** El menú del logo está abierto: se ven a la vez «Siguiendo» y «Favoritos». */
    private boolean menuOpen() {
        return !labelNodes(FOLLOWING_LABELS).isEmpty() && !labelNodes(FAVORITES_LABELS).isEmpty();
    }

    /** Toca un elemento como lo haría un dedo; si no se puede, usa la acción de accesibilidad. */
    private boolean tapNode(AccessibilityNodeInfo n) {
        Rect b = boundsOf(n);
        if (!b.isEmpty() && android.os.Build.VERSION.SDK_INT >= 24) {
            try {
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(b.centerX(), b.centerY());
                android.accessibilityservice.GestureDescription g = new android.accessibilityservice.GestureDescription.Builder()
                        .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 60))
                        .build();
                if (dispatchGesture(g, null, null)) return true;
            } catch (Exception ignored) {
            }
        }
        return click(n);
    }

    /** Pulsa el nodo o, si no es pulsable, el primer contenedor que lo sea. */
    private static boolean click(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; i < 5 && cur != null; i++) {
            if (cur.isClickable()) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            cur = cur.getParent();
        }
        return false;
    }

    // ------------------------------------------- registro para poder arreglar fallos

    private static String nodeLine(AccessibilityNodeInfo n) {
        String id = n.getViewIdResourceName();
        if (id != null && id.contains(":id/")) id = id.substring(id.indexOf(":id/") + 4);
        CharSequence cls = n.getClassName();
        String c = cls == null ? "?" : cls.toString().substring(cls.toString().lastIndexOf('.') + 1);
        Rect b = boundsOf(n);
        StringBuilder sb = new StringBuilder();
        sb.append(id != null ? id : "-").append(" [").append(c).append("] ");
        if (n.getContentDescription() != null) sb.append("desc«").append(n.getContentDescription()).append("» ");
        if (n.getText() != null) sb.append("text«").append(n.getText()).append("» ");
        if (n.isClickable()) sb.append("clic ");
        if (n.isSelected()) sb.append("sel ");
        sb.append(b.left).append(',').append(b.top).append('-').append(b.right).append(',').append(b.bottom);
        return sb.toString();
    }

    private static String describe(AccessibilityNodeInfo root, int max) {
        StringBuilder sb = new StringBuilder();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<AccessibilityNodeInfo>();
        q.add(root);
        int n = 0;
        while (!q.isEmpty() && n < max) {
            AccessibilityNodeInfo x = q.poll();
            if (x.isVisibleToUser()) {
                sb.append("  ").append(nodeLine(x)).append('\n');
                n++;
            }
            for (int i = 0; i < x.getChildCount(); i++) {
                AccessibilityNodeInfo c = x.getChild(i);
                if (c != null) q.add(c);
            }
        }
        return sb.toString();
    }

    private String describeWindows() {
        StringBuilder sb = new StringBuilder();
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                sb.append(L.t("· window type ", "· ventana tipo ")).append(w.getType()).append(' ')
                        .append(r != null && r.getPackageName() != null ? r.getPackageName() : "?").append('\n');
                if (r != null && r.getPackageName() != null && IG.equals(r.getPackageName().toString())) {
                    sb.append(describe(r, 25));
                }
            }
        } catch (Exception e) {
            sb.append(L.t("(couldn’t read the windows)\n", "(no se pudieron leer las ventanas)\n"));
        }
        return sb.toString();
    }

    /** Guarda el intento fallido para verlo en el modo diagnóstico (no sale del teléfono). */
    private void recordDebug(String text) {
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        getSharedPreferences(Diagnostics.PREFS, MODE_PRIVATE).edit()
                .putString(Diagnostics.FOLLOWING_DEBUG, stamp + "\n" + text)
                .putLong(Diagnostics.FOLLOWING_DEBUG + Diagnostics.TIME_SUFFIX, System.currentTimeMillis())
                .apply();
    }

    /** Quita la cortina un momento y vuelve a comprobar la pantalla al terminar. */
    private void pauseCurtain(long ms) {
        // Nunca acortar una pausa más larga que ya esté en marcha (p. ej. la del botón)
        curtainPausedUntil = Math.max(curtainPausedUntil, SystemClock.uptimeMillis() + ms);
        curtain.hide();
        handler.removeCallbacks(scanTask);
        scanPending = true;
        handler.postDelayed(scanTask, ms + 100);
    }

    private void manualFollowingHint() {
        notice.show(L.t("Tap the Instagram logo at the top and choose “Following”",
                "Toca el logo de Instagram arriba y elige «Siguiendo»"), 3500);
    }

    /** Botón «Buscar» de la cortina de Explorar: abre la barra de búsqueda. */
    @Override
    public void openSearch() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo field = visible(root, "action_bar_search_edit_text");
        if (field == null) field = visible(root, "explore_action_bar");
        pauseCurtain(1500);
        if (field == null || !click(field)) {
            notice.show(L.t("Tap the search bar at the top", "Toca la barra de búsqueda de arriba"), 2500);
        }
    }

    // ---------------------------------------------------------------- diagnóstico

    /**
     * Guarda los IDs de vista de la pantalla actual para que puedas crear reglas
     * nuevas cuando una app cambie su diseño. Solo se ejecuta con el modo
     * diagnóstico activado y se queda en el teléfono.
     */
    private void captureDiagnostics() {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (Exception e) {
            return;
        }
        if (root == null || root.getPackageName() == null) return;
        String pkg = root.getPackageName().toString();
        if (ignoredForDiag.contains(pkg)) return;

        Set<String> ids = new LinkedHashSet<String>();
        Set<String> selected = new LinkedHashSet<String>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<AccessibilityNodeInfo>();
        queue.add(root);
        int seen = 0;
        try {
            while (!queue.isEmpty() && seen < DIAG_MAX_NODES) {
                AccessibilityNodeInfo n = queue.poll();
                seen++;
                String id = n.getViewIdResourceName();
                String shortId = null;
                if (id != null) {
                    int k = id.indexOf(":id/");
                    shortId = k >= 0 ? id.substring(k + 4) : id;
                    if (n.isVisibleToUser()) ids.add(shortId);
                }
                if (n.isSelected()) {
                    CharSequence d = n.getContentDescription();
                    CharSequence t = n.getText();
                    String label = d != null ? d.toString() : (t != null ? t.toString() : "");
                    if (label.length() > 40) label = label.substring(0, 40) + "…";
                    selected.add((shortId != null ? shortId : L.t("(no id)", "(sin id)")) + (label.length() > 0 ? "  «" + label + "»" : ""));
                }
                for (int i = 0; i < n.getChildCount(); i++) {
                    AccessibilityNodeInfo c = n.getChild(i);
                    if (c != null) queue.add(c);
                }
            }
        } catch (Exception ignored) {
        }

        StringBuilder sb = new StringBuilder();
        sb.append(new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()))
                .append("  ·  ").append(ids.size()).append(" IDs\n");
        if (!selected.isEmpty()) {
            sb.append(L.t("Selected (active tab):\n", "Seleccionado (pestaña activa):\n"));
            for (String s : selected) sb.append("  ").append(s).append('\n');
        }
        sb.append(L.t("Visible IDs:\n", "IDs visibles:\n"));
        for (String s : ids) sb.append("  ").append(s).append('\n');

        getSharedPreferences(Diagnostics.PREFS, MODE_PRIVATE).edit()
                .putString(pkg, sb.toString())
                .putLong(pkg + Diagnostics.TIME_SUFFIX, System.currentTimeMillis())
                .apply();
    }

    @Override
    public void onInterrupt() {
        if (curtain != null) curtain.hide();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (curtain != null) curtain.hide();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (curtain != null) curtain.hide();
        if (notice != null) notice.destroy();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }
}
