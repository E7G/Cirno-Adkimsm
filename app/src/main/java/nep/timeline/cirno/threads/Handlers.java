package nep.timeline.cirno.threads;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.log.Log;

public class Handlers {
    private static final List<HandlerThread> THREADS = new CopyOnWriteArrayList<>();

    // Most callbacks are tiny state updates. Sharing loopers avoids nine permanently resident
    // thread stacks while keeping potentially blocking network statistics isolated.
    private static final Looper CORE_LOOPER = makeLooper("Core");
    private static final Looper NETWORK_LOOPER = makeLooper("Network");
    private static final Looper BINDER_LOOPER = makeLooper("Binder");
    private static final Looper BACKGROUND_LOOPER = makeLooperBackground("Background");

    public static final Handler alarms = new Handler(CORE_LOOPER);
    public static final Handler network = new Handler(NETWORK_LOOPER);
    public static final Handler audio = new Handler(CORE_LOOPER);
    public static final Handler camera = new Handler(CORE_LOOPER);
    public static final Handler location = new Handler(CORE_LOOPER);
    public static final Handler notification = new Handler(CORE_LOOPER);
    public static final Handler rekernel = new Handler(CORE_LOOPER);
    public static final Handler binder = new Handler(BINDER_LOOPER);
    public static final Handler log = new Handler(BACKGROUND_LOOPER);
    public static final Handler config = new Handler(BACKGROUND_LOOPER);
    public static final Handler broadcast = new Handler(BACKGROUND_LOOPER);
    public static final Handler hookDebug = new Handler(BACKGROUND_LOOPER);

    public static Handler makeHandlerForeground(String str) {
        return makeHandlerForeground(str, false);
    }

    public static Handler makeHandlerForeground(String str, boolean async) {
        if (async)
            return Handler.createAsync(makeLooperForeground(str));
        else
            return new Handler(makeLooperForeground(str));
    }

    public static Handler makeHandler(String str) {
        return makeHandler(str, false);
    }

    public static Handler makeHandler(String str, boolean async) {
        if (async)
            return Handler.createAsync(makeLooper(str));
        else
            return new Handler(makeLooper(str));
    }

    public static Handler makeHandlerBackground(String str) {
        return makeHandlerBackground(str, false);
    }

    public static Handler makeHandlerBackground(String str, boolean async) {
        if (async)
            return Handler.createAsync(makeLooperBackground(str));
        else return new Handler(makeLooperBackground(str));
    }

    public static Looper makeLooperForeground(String str) {
        HandlerThread handlerThread = new HandlerThread(GlobalVars.TAG + "-" + str, Process.THREAD_PRIORITY_FOREGROUND);
        handlerThread.setUncaughtExceptionHandler((t, e) -> Log.e("线程 " + t.getName() + " 出现异常: " + e));
        handlerThread.start();
        THREADS.add(handlerThread);
        return handlerThread.getLooper();
    }

    public static Looper makeLooperBackground(String str) {
        HandlerThread handlerThread = new HandlerThread(GlobalVars.TAG + "-" + str, Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.setUncaughtExceptionHandler((t, e) -> {
            Log.e("线程 " + t.getName() + " 出现异常: " + e);
            // 🔧 调试：记录Hook相关的异常
            if (t.getName().contains("HookDebug")) {
                Log.d("HookDebug 异常详情");
            }
        });
        handlerThread.start();
        THREADS.add(handlerThread);
        return handlerThread.getLooper();
    }

    public static Looper makeLooper(String str) {
        HandlerThread handlerThread = new HandlerThread(GlobalVars.TAG + "-" + str);
        handlerThread.setUncaughtExceptionHandler((t, e) -> Log.e("线程 " + t.getName() + " 出现异常: " + e));
        handlerThread.start();
        THREADS.add(handlerThread);
        return handlerThread.getLooper();
    }

    public static void shutdownForHotReload() {
        for (HandlerThread thread : THREADS) {
            try {
                thread.getLooper().quitSafely();
            } catch (Throwable ignored) {
            }
        }
        THREADS.clear();
    }
}
