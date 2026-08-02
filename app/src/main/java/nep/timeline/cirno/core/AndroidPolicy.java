package nep.timeline.cirno.core;

import android.os.Build;

/** Small pure-Java policy helpers used by system_server hot paths. */
public final class AndroidPolicy {
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

    private AndroidPolicy() {
    }

    public static boolean shouldFreeze(int flags, int processCount) {
        if (processCount <= 0) {
            return false;
        }
        int exemptions = SYSTEM | VISIBLE | LOCATION | AUDIO | RECORDING | VPN
                | WHITELISTED | NETWORK_ACTIVE;
        return (flags & exemptions) == 0;
    }

    public static long delayMs(int configuredSeconds, int flags) {
        return Math.max(1L, Math.min(60L, configuredSeconds)) * 1000L;
    }

    public static boolean isClover() {
        return "xiaomi".equalsIgnoreCase(Build.MANUFACTURER)
                && ("clover".equalsIgnoreCase(Build.DEVICE)
                || "clover".equalsIgnoreCase(Build.PRODUCT)
                || Build.MODEL.toLowerCase().contains("mi pad 4"));
    }

    public static String backend() {
        return "android-java";
    }
}
