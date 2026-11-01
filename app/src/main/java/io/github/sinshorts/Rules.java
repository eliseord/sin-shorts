package io.github.sinshorts;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas de bloqueo.
 *
 * Formato de cada línea:   paquete[,paquete2] | tipo:valor
 *
 *   id:NOMBRE       hay en pantalla una vista visible con ese ID (p. ej. reel_recycler)
 *   idsel:NOMBRE    hay una vista con ese ID y está seleccionada (p. ej. una pestaña activa)
 *   text:TEXTO      hay una vista visible cuyo texto o descripción contiene TEXTO
 *   textsel:TEXTO   igual que text, pero la vista (o su contenedor) está seleccionada
 *   url:TEXTO       la barra de direcciones del navegador contiene TEXTO
 *   app             bloquea la app entera (te manda a la pantalla de inicio)
 *
 * Las líneas que empiezan por # son comentarios.
 */
public final class Rules {

    public static final String PREFS = "sinshorts";

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_TOAST = "toast";
    public static final String KEY_DIAG = "diag";
    public static final String KEY_CUSTOM = "custom_rules";
    public static final String KEY_COUNT = "count";
    public static final String KEY_COUNT_DAY = "count_day";
    public static final String KEY_TOTAL = "total";
    /** Categorías especiales: no son reglas de «salir», sino cortinas sobre una zona de Instagram. */
    public static final String KEY_IG_FOLLOWING = "cat_ig_following";
    public static final String KEY_IG_EXPLORE = "cat_ig_explore";
    public static final String INSTAGRAM = "com.instagram.android";

    public static final int ID = 0, ID_SEL = 1, TEXT = 2, TEXT_SEL = 3, URL = 4, APP = 5;

    /** Una categoría que el usuario puede activar o desactivar. */
    public static final class Category {
        public final String key, title, subtitle;
        public final boolean defaultOn;
        public final String[] rules;

        Category(String key, String title, String subtitle, boolean defaultOn, String... rules) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.defaultOn = defaultOn;
            this.rules = rules;
        }
    }

    private static final String YT = "com.google.android.youtube";
    private static final String IG = "com.instagram.android";
    private static final String BROWSERS =
            "com.android.chrome,com.chrome.beta,org.mozilla.firefox,com.brave.browser,com.microsoft.emmx,com.duckduckgo.mobile.android";

    public static final Category[] CATEGORIES = {
            new Category("cat_youtube", "YouTube Shorts",
                    "Pestaña Shorts y Shorts abiertos desde el inicio o la búsqueda. Los vídeos normales siguen funcionando.",
                    true,
                    YT + " | id:reel_recycler",
                    YT + " | id:reel_watch_player",
                    YT + " | id:reel_progress_bar",
                    YT + " | id:reel_watch_fragment_root",
                    YT + " | id:shorts_player"),
            new Category("cat_instagram", "Instagram Reels",
                    "Pestaña Reels y el visor de Reels. Feed, historias y mensajes siguen funcionando.",
                    true,
                    IG + " | idsel:clips_tab",
                    IG + " | id:clips_viewer_view_pager",
                    IG + " | id:clips_viewer"),
            new Category(Rules.KEY_IG_FOLLOWING, "Instagram: solo publicaciones de seguidos",
                    "Tapa el feed de inicio, que mezcla sugerencias sin fin, y te lleva a «Siguiendo». Historias y mensajes siguen arriba.",
                    true),
            new Category(Rules.KEY_IG_EXPLORE, "Instagram: tapar Explorar",
                    "Tapa la cuadrícula de Explorar. Buscar personas sigue funcionando.",
                    true),
            new Category("cat_facebook", "Facebook Reels (experimental)",
                    "Pestaña Reels de Facebook. Si deja de funcionar, usa el modo diagnóstico.",
                    true,
                    "com.facebook.katana | textsel:reels"),
            new Category("cat_snapchat", "Snapchat Spotlight (experimental)",
                    "Pestaña Spotlight. Chats, cámara e historias siguen funcionando.",
                    true,
                    "com.snapchat.android | textsel:spotlight",
                    "com.snapchat.android | idsel:spotlight"),
            new Category("cat_browser", "Shorts y Reels en el navegador",
                    "youtube.com/shorts, Reels de Instagram y TikTok en Chrome, Firefox, Brave, Edge y DuckDuckGo.",
                    true,
                    BROWSERS + " | url:youtube.com/shorts",
                    BROWSERS + " | url:instagram.com/reels",
                    BROWSERS + " | url:instagram.com/reel/",
                    BROWSERS + " | url:facebook.com/reel",
                    BROWSERS + " | url:tiktok.com"),
            new Category("cat_tiktok", "TikTok completo",
                    "TikTok es todo vídeo corto, así que se bloquea la app entera.",
                    false,
                    "com.zhiliaoapp.musically | app",
                    "com.ss.android.ugc.trill | app",
                    "com.zhiliaoapp.musically.go | app"),
    };

    public static final String CUSTOM_HELP =
            "# Una regla por línea:  paquete | tipo:valor\n"
                    + "# Tipos: id, idsel, text, textsel, url, app\n"
                    + "# Ejemplo:\n"
                    + "# com.google.android.youtube | id:reel_recycler\n";

    /** Una regla ya interpretada. */
    public static final class Rule {
        public final Set<String> packages;
        public final int type;
        public final String value;      // en minúsculas para text/url
        public final String source;

        Rule(Set<String> packages, int type, String value, String source) {
            this.packages = packages;
            this.type = type;
            this.value = value;
            this.source = source;
        }
    }

    private Rules() {
    }

    /** Lee las preferencias y devuelve todas las reglas activas. */
    public static List<Rule> load(SharedPreferences p) {
        List<Rule> out = new ArrayList<Rule>();
        for (Category c : CATEGORIES) {
            if (p.getBoolean(c.key, c.defaultOn)) {
                for (String line : c.rules) {
                    Rule r = parse(line);
                    if (r != null) out.add(r);
                }
            }
        }
        String custom = p.getString(KEY_CUSTOM, "");
        for (String line : custom.split("\n")) {
            Rule r = parse(line);
            if (r != null) out.add(r);
        }
        return out;
    }

    /** Devuelve null si la línea está vacía, es un comentario o no es válida. */
    public static Rule parse(String raw) {
        if (raw == null) return null;
        String line = raw.trim();
        if (line.length() == 0 || line.startsWith("#")) return null;
        int bar = line.indexOf('|');
        if (bar <= 0) return null;

        Set<String> pkgs = new HashSet<String>();
        for (String s : line.substring(0, bar).split(",")) {
            String t = s.trim();
            if (t.length() > 0) pkgs.add(t);
        }
        if (pkgs.isEmpty()) return null;

        String spec = line.substring(bar + 1).trim();
        if (spec.equalsIgnoreCase("app")) return new Rule(pkgs, APP, "", line);

        int colon = spec.indexOf(':');
        if (colon <= 0) return null;
        String kind = spec.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String value = spec.substring(colon + 1).trim();
        if (value.length() == 0) return null;

        int type;
        if (kind.equals("id")) type = ID;
        else if (kind.equals("idsel")) type = ID_SEL;
        else if (kind.equals("text")) type = TEXT;
        else if (kind.equals("textsel")) type = TEXT_SEL;
        else if (kind.equals("url")) type = URL;
        else return null;

        if (type == TEXT || type == TEXT_SEL || type == URL) {
            value = value.toLowerCase(Locale.ROOT);
        }
        return new Rule(pkgs, type, value, line);
    }

    /** Cuenta las líneas del texto personalizado que no se pudieron entender. */
    public static int countInvalid(String text) {
        int bad = 0;
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.length() == 0 || t.startsWith("#")) continue;
            if (parse(t) == null) bad++;
        }
        return bad;
    }
}
