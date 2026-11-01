package io.github.sinshorts;

import android.content.Context;

/**
 * Language of the app's own texts. English by default; Spanish can be chosen
 * inside the app (setup wizard and Options), independently of the phone language.
 */
public final class L {

    public static final String KEY = "lang";
    private static volatile boolean es;

    private L() {
    }

    /** Reads the saved choice. Call it before building any screen. */
    public static void init(Context c) {
        es = "es".equals(c.getSharedPreferences(Rules.PREFS, Context.MODE_PRIVATE).getString(KEY, "en"));
    }

    public static void set(Context c, boolean spanish) {
        es = spanish;
        c.getSharedPreferences(Rules.PREFS, Context.MODE_PRIVATE).edit().putString(KEY, spanish ? "es" : "en").apply();
    }

    public static boolean es() {
        return es;
    }

    /** English text, or the Spanish one if Spanish is selected. */
    public static String t(String en, String spanish) {
        return es ? spanish : en;
    }
}
