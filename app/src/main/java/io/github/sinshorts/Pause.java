package io.github.sinshorts;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Blocking can't be switched off, only paused for a few minutes. After that it
 * comes back on by itself. Starting a pause (or loosening any setting) goes through
 * a 30-second wait in {@link PauseActivity}; resuming is always immediate.
 */
public final class Pause {

    public static final String KEY_UNTIL = "paused_until";      // wall-clock ms
    public static final String KEY_MINUTES = "pause_minutes";
    public static final int[] OPTIONS = {5, 10, 15};
    public static final int DEFAULT_MINUTES = 10;
    public static final int WAIT_SECONDS = 30;

    private Pause() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(Rules.PREFS, Context.MODE_PRIVATE);
    }

    public static long until(Context c) {
        return prefs(c).getLong(KEY_UNTIL, 0);
    }

    public static boolean isPaused(Context c) {
        return remainingMs(c) > 0;
    }

    public static long remainingMs(Context c) {
        return Math.max(0, until(c) - System.currentTimeMillis());
    }

    public static int minutes(Context c) {
        int m = prefs(c).getInt(KEY_MINUTES, DEFAULT_MINUTES);
        for (int o : OPTIONS) if (o == m) return m;
        return DEFAULT_MINUTES;
    }

    public static void setMinutes(Context c, int m) {
        prefs(c).edit().putInt(KEY_MINUTES, m).apply();
    }

    public static void start(Context c, int minutes) {
        prefs(c).edit().putLong(KEY_UNTIL, System.currentTimeMillis() + minutes * 60_000L).apply();
    }

    public static void resume(Context c) {
        prefs(c).edit().putLong(KEY_UNTIL, 0).apply();
    }

    /**
     * Older versions had a permanent off switch. There is no "off" any more:
     * if it was left off, turn it back on.
     */
    public static void migrate(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(Rules.KEY_ENABLED, true)) p.edit().putBoolean(Rules.KEY_ENABLED, true).apply();
    }
}
