package io.github.sinshorts;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.Locale;

/** Comprobaciones y accesos directos a los ajustes del sistema que necesita la app. */
public final class Setup {

    public static final String KEY_ONBOARDING_DONE = "onboarding_done";

    public static final int UNKNOWN = -1, ALLOWED = 0, RESTRICTED = 1;

    private Setup() {
    }

    public static ComponentName service(Context c) {
        return new ComponentName(c, BlockerService.class);
    }

    /** ¿Está encendido el servicio de accesibilidad de Sin Shorts? */
    public static boolean serviceEnabled(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        ComponentName cn = service(c);
        String lower = list.toLowerCase(Locale.ROOT);
        return lower.contains(cn.flattenToString().toLowerCase(Locale.ROOT))
                || lower.contains(cn.flattenToShortString().toLowerCase(Locale.ROOT));
    }

    /**
     * Desde Android 13, las apps instaladas fuera de Play Store no pueden activar la
     * accesibilidad hasta que el usuario permite los «ajustes restringidos».
     * Android no ofrece una API pública para saberlo; se intenta leer el permiso interno
     * y, si no se puede, se devuelve UNKNOWN.
     */
    public static int restrictedState(Context c) {
        if (Build.VERSION.SDK_INT < 33) return ALLOWED;
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            @SuppressWarnings("deprecation")
            int mode = ops.checkOpNoThrow("android:access_restricted_settings",
                    c.getApplicationInfo().uid, c.getPackageName());
            if (mode == AppOpsManager.MODE_ERRORED || mode == AppOpsManager.MODE_IGNORED) return RESTRICTED;
            return ALLOWED;
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    /** Abre directamente el interruptor de Sin Shorts; si no se puede, la lista de accesibilidad. */
    public static void openServiceToggle(Activity a) {
        try {
            Intent i = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
            i.putExtra("android.intent.extra.COMPONENT_NAME", service(a).flattenToString());
            a.startActivity(i);
            return;
        } catch (Exception ignored) {
        }
        a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    public static void openAppInfo(Activity a) {
        a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.getPackageName())));
    }

    public static boolean batteryUnrestricted(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    public static void requestBatteryUnrestricted(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (Exception e) {
            openAppInfo(a);
        }
    }

    /** ¿Se instaló desde Google Play? Entonces no hizo falta pausar Play Protect. */
    @SuppressWarnings("deprecation")
    public static boolean installedFromPlayStore(Context c) {
        try {
            return "com.android.vending".equals(c.getPackageManager().getInstallerPackageName(c.getPackageName()));
        } catch (Exception e) {
            return false;
        }
    }

    /** Abre los ajustes de Play Protect; si no se puede, la Play Store. */
    public static boolean openPlayProtect(Activity a) {
        try {
            Intent i = new Intent();
            i.setClassName("com.google.android.gms", "com.google.android.gms.security.settings.VerifyAppsSettingsActivity");
            a.startActivity(i);
            return true;
        } catch (Exception ignored) {
        }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.android.vending");
            a.startActivity(i);
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Abre un enlace en la app que lo maneje (YouTube, Instagram) o en el navegador. */
    public static boolean openLink(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
