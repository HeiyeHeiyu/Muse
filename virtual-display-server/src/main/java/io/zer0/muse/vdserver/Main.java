package io.zer0.muse.vdserver;

import android.os.Looper;
import android.util.Log;

/**
 * v2.2.1 虚拟屏:服务端入口。
 *
 * 运行方式(shell uid):
 * <pre>
 * CLASSPATH=/data/local/tmp/muse-vd-server.jar app_process / io.zer0.muse.vdserver.Main &lt;hostPkg&gt;
 * </pre>
 *
 * 启动序列:
 *  1. prepareMainLooper(反射,app_process 没有默认 main looper)
 *  2. Workarounds.apply(ActivityThread 修补,过 framework 的包名/uid 校验)
 *  3. 构造 DisplayManager(FakeContext) + ServerService
 *  4. 广播手递手把 binder 发给宿主包
 *  5. 主线程 Looper.loop 吊住;后台 idle watcher 15s 无调用即退出
 */
public final class Main {

    private static final String TAG = "MuseVdServer";

    private Main() {
    }

    public static void main(String[] args) {
        String hostPackage = (args != null && args.length > 0) ? args[0] : null;
        log("main start, hostPackage=" + hostPackage);

        try {
            prepareMainLooper();
        } catch (Throwable t) {
            log("prepareMainLooper failed: " + t);
        }

        Workarounds.apply();

        ServerService service;
        try {
            service = new ServerService(new FakeContext());
        } catch (Throwable t) {
            log("ServerService creation failed: " + t);
            System.exit(1);
            return;
        }

        try {
            service.startPublishLoop(hostPackage);
        } catch (Throwable t) {
            log("binder handoff failed: " + t);
        }

        service.startIdleWatcher();
        Looper.loop();
    }

    /** app_process 环境没有现成 main looper,反射准备(不同版本方法名有差异)。 */
    private static void prepareMainLooper() throws Exception {
        Class<?> looper = Class.forName("android.os.Looper");
        try {
            looper.getDeclaredMethod("prepareMainLooper").invoke(null);
        } catch (NoSuchMethodException e) {
            looper.getDeclaredMethod("prepare").invoke(null);
        }
    }

    static void log(String message) {
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
            // logcat 不可用时保持静默
        }
    }
}
