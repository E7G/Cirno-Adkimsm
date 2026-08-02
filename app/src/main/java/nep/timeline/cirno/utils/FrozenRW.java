package nep.timeline.cirno.utils;

import android.os.Process;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.settings.GlobalSettings;

public class FrozenRW {
    public static final String cgroupV2 = "/sys/fs/cgroup";
    private static final String cgroupV2FrozenDir = cgroupV2 + "/frozen";
    private static final String cgroupV2UnfrozenDir = cgroupV2 + "/unfrozen";
    private static final String cgroupV2FrozenProcs = cgroupV2FrozenDir + "/cgroup.procs";
    private static final String cgroupV2UnfrozenProcs = cgroupV2UnfrozenDir + "/cgroup.procs";
    private static final boolean cgroupV2SysAppIsolated;

    static {
        String path = "/sys/fs/cgroup/uid_1000/cgroup.freeze";
        cgroupV2SysAppIsolated = !Files.exists(Paths.get(path));
    }

    private static boolean useFrozenMode() {
        GlobalSettings settings = GlobalVars.globalSettings;
        return settings != null
                && GlobalSettings.FREEZER_MODE_FROZEN.equals(settings.freezerMode)
                && Files.exists(Paths.get(cgroupV2FrozenProcs))
                && Files.exists(Paths.get(cgroupV2UnfrozenProcs));
    }

    private static boolean writeFrozen(int uid, int pid, int frozenState) {
        return writeFrozen(uid, pid, frozenState, true);
    }

    private static boolean writeFrozen(int uid, int pid, int frozenState, boolean logFailure) {
        if (useFrozenMode()) {
            String path = frozenState == 1 ? cgroupV2FrozenProcs : cgroupV2UnfrozenProcs;
            return RWUtils.writeFrozen(path, pid, logFailure);
        }

        return RWUtils.writeFrozen(processFreezePath(uid, pid), frozenState, logFailure);
    }

    public static boolean frozen(int uid, int pid) {
        return writeFrozen(uid, pid, 1) && isActuallyFrozen(uid, pid);
    }

    public static boolean thaw(int uid, int pid) {
        return writeFrozen(uid, pid, 0) && !isActuallyFrozen(uid, pid);
    }

    public static boolean thawQuietly(int uid, int pid) {
        return writeFrozen(uid, pid, 0, false);
    }

    /** Read back the kernel state; a successful write alone is not sufficient. */
    public static boolean isActuallyFrozen(int uid, int pid) {
        return readFreezeState(uid, pid) == 1;
    }

    /** Returns the backend that owns this process' freeze state. */
    public static String getFreezeType(int uid, int pid) {
        String path = currentCgroupFreezePath(pid);
        if (path == null) {
            path = processFreezePath(uid, pid);
        }
        if (path != null && path.startsWith(cgroupV2 + "/")) {
            return "V2";
        }
        if (path != null && path.contains("/freezer/")) {
            return "V1";
        }
        return "UNKNOWN";
    }

    private static int readFreezeState(int uid, int pid) {
        String path = currentCgroupFreezePath(pid);
        if (path == null) {
            path = processFreezePath(uid, pid);
        }
        if (path == null) {
            return -1;
        }
        try {
            return Integer.parseInt(Files.readString(Paths.get(path)).trim());
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String processFreezePath(int uid, int pid) {
        if (useFrozenMode()) {
            return cgroupV2FrozenDir + "/cgroup.freeze";
        }
        if (!cgroupV2SysAppIsolated) {
            return cgroupV2 + "/uid_" + uid + "/pid_" + pid + "/cgroup.freeze";
        }
        if (uid < Process.FIRST_APPLICATION_UID) {
            return cgroupV2 + "/system/uid_" + uid + "/pid_" + pid + "/cgroup.freeze";
        }
        return cgroupV2 + "/apps/uid_" + uid + "/pid_" + pid + "/cgroup.freeze";
    }

    private static String currentCgroupFreezePath(int pid) {
        if (pid <= 0) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/" + pid + "/cgroup"));
            for (String line : lines) {
                if (line == null || !line.startsWith("0::")) {
                    continue;
                }
                String relative = line.substring(3).trim();
                if (relative.isEmpty() || "/".equals(relative)) {
                    return cgroupV2 + "/cgroup.freeze";
                }
                if (!relative.startsWith("/")) {
                    relative = "/" + relative;
                }
                return cgroupV2 + relative + "/cgroup.freeze";
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
