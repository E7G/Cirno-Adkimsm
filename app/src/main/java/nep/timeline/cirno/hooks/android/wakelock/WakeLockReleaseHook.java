package nep.timeline.cirno.hooks.android.wakelock;

import android.os.IBinder;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;

public class WakeLockReleaseHook extends MethodHook {
    public WakeLockReleaseHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.power.PowerManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "releaseWakeLockInternal";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{IBinder.class, int.class};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                WakeLockHook.onReleased((IBinder) callback.getArgs()[0]);
            }
        };
    }
}
