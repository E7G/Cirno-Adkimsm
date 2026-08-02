package nep.timeline.cirno.nativecore;

import android.os.Build;

/** Rust-backed, allocation-free policy decisions used from hot paths. */
public final class NativePolicy {
    public static final int SYSTEM = 1;
    public static final int VISIBLE = 1 << 1;
    public static final int LOCATION = 1 << 2;
    public static final int AUDIO = 1 << 3;
    public static final int RECORDING = 1 << 4;
    public static final int VPN = 1 << 5;
    public static final int WHITELISTED = 1 << 6;
    public static final int CLOVER = 1 << 7;
    public static final int LOW_MEMORY = 1 << 8;
    public static final int NETWORK_ACTIVE = 1 << 9;

    private static final boolean LOADED;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("cirno_native");
            loaded = nativeCoreVersion() > 0;
        } catch (Throwable ignored) {
            // Java fallback keeps old releases and unsupported ABIs functional.
        }
        LOADED = loaded;
    }

    private NativePolicy() { }

    public static boolean shouldFreeze(int flags, int processCount) {
        if (LOADED) return nativeShouldFreeze(flags, processCount);
        if (processCount <= 0) return false;
        return (flags & (SYSTEM | VISIBLE | LOCATION | AUDIO | RECORDING | VPN | WHITELISTED | NETWORK_ACTIVE)) == 0;
    }

    public static long delayMs(int configuredSeconds, int flags) {
        if (LOADED) return nativeDelayMs(configuredSeconds, flags);
        return Math.max(1L, Math.min(60L, configuredSeconds)) * 1000L;
    }

    public static boolean isClover() {
        return "xiaomi".equalsIgnoreCase(Build.MANUFACTURER)
                && ("clover".equalsIgnoreCase(Build.DEVICE)
                || "clover".equalsIgnoreCase(Build.PRODUCT)
                || Build.MODEL.toLowerCase().contains("mi pad 4"));
    }

    public static String backend() {
        return LOADED ? "rust" : "java-fallback";
    }

    private static native boolean nativeShouldFreeze(int flags, int processCount);
    private static native long nativeDelayMs(int configuredSeconds, int flags);
    private static native int nativeCoreVersion();
}
