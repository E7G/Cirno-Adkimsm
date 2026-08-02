package nep.timeline.cirno;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import nep.timeline.cirno.master.AndroidHooks;
import nep.timeline.cirno.master.SystemUIHooks;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.CachedAppOptimizer;
import nep.timeline.cirno.services.GreezeManagerServiceWrapper;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.NetworkManagementService;
import nep.timeline.cirno.services.NetworkSpeedMonitor;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.framework.XposedInstance;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.utils.AutofillData;
import nep.timeline.cirno.utils.CredentialData;
import nep.timeline.cirno.utils.ForceAppStandbyListener;
import nep.timeline.cirno.utils.InputMethodData;

public class HookInit extends XposedModule {
    private static final int MIN_XPOSED_API = 101;
    private static final String PROCESS_SYSTEM_SERVER = "system_server";
    private static final String PROCESS_SYSTEM_UI = "com.android.systemui";

    private boolean unsupportedXposedApi;
    private boolean systemUIHooksStarted;
    private boolean systemServerHooksStarted;
    private String processName;
    private ClassLoader hostClassLoader;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        processName = param.getProcessName();
        int apiVersion = getApiVersion();
        unsupportedXposedApi = apiVersion < MIN_XPOSED_API;
        if (unsupportedXposedApi) {
            Log.w("Cirno requires Xposed API " + MIN_XPOSED_API + " or later, current=" + apiVersion);
        }
        XposedInstance.setModule(this);
        CakeHooker.setXposedModule(this);
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        String packageName = param.getPackageName();
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (unsupportedXposedApi) {
            return;
        }

        String packageName = param.getPackageName();
        if (!PROCESS_SYSTEM_UI.equals(packageName) || systemUIHooksStarted) {
            return;
        }

        startSystemUIHooks(param.getClassLoader(), packageName);
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        if (unsupportedXposedApi) {
            return;
        }

        startSystemServerHooks(param.getClassLoader(), true);
    }

    private void startSystemUIHooks(ClassLoader classLoader, String packageName) {
        systemUIHooksStarted = true;
        hostClassLoader = GlobalVars.classLoader = classLoader;
        processName = packageName;
        CakeHooker.setHostClassLoader(classLoader);

        try {
            SystemUIHooks.start(classLoader);
        } catch (Throwable throwable) {
            Log.e("Cirno (" + packageName + ") -> Hook failed", throwable);
        }
    }

    private void startSystemServerHooks(ClassLoader classLoader, boolean rotateLog) {
        systemServerHooksStarted = true;
        hostClassLoader = GlobalVars.classLoader = classLoader;
        processName = PROCESS_SYSTEM_SERVER;
        CakeHooker.setHostClassLoader(classLoader);

        try {
            if (rotateLog) {
                File source = new File(GlobalVars.LOG_DIR, "current.log");
                File dest = new File(GlobalVars.LOG_DIR, "last.log");
                boolean ignoredDelete = dest.delete();
                boolean ignoredRename = source.renameTo(dest);
            }
            AndroidHooks.start(classLoader);
        } catch (Throwable throwable) {
            Log.e("Cirno (android) -> Hook failed", throwable);
        }
    }

}
