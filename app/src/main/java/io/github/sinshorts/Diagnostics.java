package io.github.sinshorts;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Capturas del modo diagnóstico, guardadas por app. */
public final class Diagnostics {

    public static final String PREFS = "sinshorts_diag";
    public static final String TIME_SUFFIX = "#time";
    /** Último intento fallido del botón «Abrir Siguiendo». */
    public static final String FOLLOWING_DEBUG = "Sin Shorts · fallo al abrir Siguiendo";

    public static final class Entry {
        public final String pkg, text;
        public final long time;

        Entry(String pkg, String text, long time) {
            this.pkg = pkg;
            this.text = text;
            this.time = time;
        }
    }

    private Diagnostics() {
    }

    /** Las capturas más recientes primero. */
    public static List<Entry> recent(Context c, int max) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<Entry> list = new ArrayList<Entry>();
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String key = e.getKey();
            if (key.endsWith(TIME_SUFFIX) || !(e.getValue() instanceof String)) continue;
            list.add(new Entry(key, (String) e.getValue(), p.getLong(key + TIME_SUFFIX, 0)));
        }
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.time, a.time);
            }
        });
        return list.size() > max ? list.subList(0, max) : list;
    }

    public static void clear(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
