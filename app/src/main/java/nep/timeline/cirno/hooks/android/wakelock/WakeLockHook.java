package nep.timeline.cirno.hooks.android.wakelock;

import android.os.Build;
import android.os.IBinder;
import android.os.WorkSource;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import nep.timeline.cirno.CommonConstants;
import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.utils.PKGUtils;
import nep.timeline.cirno.utils.SystemChecker;

public class WakeLockHook extends MethodHook {
    private static final ConcurrentHashMap<Integer, Set<IBinder>> UID_TOKENS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<IBinder, Set<Integer>> TOKEN_UIDS = new ConcurrentHashMap<>();
    private static volatile Object sPowerManagerService;

    public WakeLockHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.power.PowerManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "acquireWakeLockInternal";
    }

    @Override
    public Object[] getTargetParam() {
        if (SystemChecker.isSamsung(classLoader) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return new Object[] { IBinder.class, int.class, int.class, String.class, String.class, WorkSource.class,
                    String.class, int.class, int.class, "android.os.IWakeLockCallback", boolean.class };
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2)
            return new Object[] { IBinder.class, int.class, int.class, String.class, String.class, WorkSource.class,
                    String.class, int.class, int.class, "android.os.IWakeLockCallback" };
        return new Object[] { IBinder.class, int.class, int.class, String.class, String.class, WorkSource.class,
                String.class, int.class, int.class };
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                Object[] args = callback.getArgs();
                IBinder token = (IBinder) args[0];
                String packageName = (String) args[4];
                WorkSource workSource = (WorkSource) args[5];
                int uid = (int) args[7];
                sPowerManagerService = callback.getThisObject();

                if (CommonConstants.isTelephonyPackage(packageName, uid))
                    return;

                AppRecord appRecord = AppService.get(packageName, PKGUtils.getUserId(uid));
                Set<Integer> attributedUids = collectAttributedUids(uid, workSource);

                if ((appRecord != null && appRecord.isFrozen()
                        && !AppConfigs.isNetworkMessageAllowed(packageName, PKGUtils.getUserId(uid)))
                        || containsStrictlyFrozenUid(attributedUids)) {
                    onReleased(token);
                    callback.returnAndSkip(null);
                    return;
                }

                track(token, attributedUids);
            }
        };
    }

    private static Set<Integer> collectAttributedUids(int ownerUid, WorkSource workSource) {
        Set<Integer> result = new HashSet<>();
        if (ownerUid > android.os.Process.SYSTEM_UID && shouldTrackUid(ownerUid))
            result.add(ownerUid);
        if (workSource == null) return result;

        try {
            int size = (int) CakeReflection.callMethod(workSource, "size");
            for (int i = 0; i < size; i++) {
                int uid = (int) CakeReflection.callMethod(workSource, "getUid", i);
                if (uid > android.os.Process.SYSTEM_UID && shouldTrackUid(uid))
                    result.add(uid);
            }
        } catch (Throwable ignored) {
        }

        try {
            Object value = CakeReflection.callMethod(workSource, "getWorkChains");
            if (value instanceof List<?>) {
                for (Object chain : (List<?>) value) {
                    int[] uids = (int[]) CakeReflection.callMethod(chain, "getUids");
                    if (uids == null) continue;
                    for (int uid : uids) {
                        if (uid > android.os.Process.SYSTEM_UID && shouldTrackUid(uid))
                            result.add(uid);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    // acquireWakeLockInternal is a system hot path. Keep token state only for
    // UIDs whose apps Cirno may strictly freeze; tracking every Android app
    // wastes CPU and retains binder tokens that can never be acted on.
    private static boolean shouldTrackUid(int uid) {
        List<AppRecord> records = AppService.getByUid(uid);
        if (records.isEmpty()) return false;

        boolean managed = false;
        for (AppRecord record : records) {
            if (record == null) continue;
            if (CommonConstants.isTelephonyPackage(record.getPackageName(), uid)
                    || AppConfigs.isNetworkMessageAllowed(
                    record.getPackageName(), record.getUserId())) {
                return false;
            }
            if (AppConfigs.isBlackApp(record.getPackageName(), record.getUserId())) {
                managed = true;
                continue;
            }
            if (AppConfigs.isWhiteApp(record.getPackageName(), record.getUserId())
                    || PKGUtils.isSystemApp(record.getApplicationInfo())) {
                return false;
            }
            managed = true;
        }
        return managed;
    }

    private static boolean containsStrictlyFrozenUid(Set<Integer> uids) {
        for (int uid : uids) {
            List<AppRecord> records = AppService.getByUid(uid);
            boolean frozen = false;
            for (AppRecord record : records) {
                if (CommonConstants.isTelephonyPackage(record.getPackageName(), uid) || !record.isFrozen()) {
                    frozen = false;
                    break;
                }
                if (!AppConfigs.isNetworkMessageAllowed(record.getPackageName(), record.getUserId())) {
                    frozen = true;
                }
            }
            if (frozen) return true;
        }
        return false;
    }

    private static void track(IBinder token, Set<Integer> uids) {
        if (token == null) return;
        onReleased(token);
        if (uids.isEmpty()) return;
        Set<Integer> copy = Collections.unmodifiableSet(new HashSet<>(uids));
        TOKEN_UIDS.put(token, copy);
        for (int uid : copy) {
            UID_TOKENS.computeIfAbsent(uid,
                    ignored -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(token);
        }
    }

    public static void onReleased(IBinder token) {
        if (token == null) return;
        Set<Integer> uids = TOKEN_UIDS.remove(token);
        if (uids == null) return;
        for (int uid : uids) {
            Set<IBinder> tokens = UID_TOKENS.get(uid);
            if (tokens == null) continue;
            tokens.remove(token);
            if (tokens.isEmpty()) UID_TOKENS.remove(uid, tokens);
        }
    }

    public static void releaseForFrozenUid(int uid) {
        if (!containsStrictlyFrozenUid(Collections.singleton(uid))) return;
        Set<IBinder> tokens = UID_TOKENS.get(uid);
        Object service = sPowerManagerService;
        if (tokens == null || tokens.isEmpty() || service == null) return;

        for (IBinder token : new HashSet<>(tokens)) {
            try {
                CakeReflection.callMethod(service, "releaseWakeLockInternal",
                        new Class<?>[]{IBinder.class, int.class}, token, 0);
            } catch (Throwable ignored) {
            } finally {
                onReleased(token);
            }
        }
    }
}
