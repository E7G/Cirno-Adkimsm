package nep.timeline.cirno.nativecore;

import android.content.Context;

/** Process-local application context without a UI toolkit dependency. */
public final class AppRuntime {
    private static volatile Context context;

    private AppRuntime() { }

    public static void init(Context value) {
        context = value.getApplicationContext();
    }

    public static Context context() {
        Context value = context;
        if (value == null) throw new IllegalStateException("AppRuntime is not initialized");
        return value;
    }
}
