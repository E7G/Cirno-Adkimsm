package nep.timeline.cirno.utils;

import android.content.Context;

/** Process-local application context for the native Android UI and monitor. */
public final class AndroidRuntime {
    private static volatile Context context;

    private AndroidRuntime() {
    }

    public static void init(Context value) {
        context = value.getApplicationContext();
    }

    public static Context context() {
        Context value = context;
        if (value == null) {
            throw new IllegalStateException("AndroidRuntime is not initialized");
        }
        return value;
    }
}
