package io.zer0.muse.vdserver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * v2.2.1 虚拟屏:framework 兼容修补(best-effort,失败仅记日志)。
 *
 * app_process 进程没有经过 zygote 的常规 App 初始化路径。部分 framework 组件
 * (如 DisplayManager 内部依赖 ActivityThread 的线程本地状态)需要把标准的
 * ActivityThread 单例"补"进进程。所有步骤独立 try/catch —— 不同 ROM/版本字段
 * 缺失时自动跳过,而不是让服务端启动失败。
 */
public final class Workarounds {

    private Workarounds() {
    }

    public static void apply() {
        mendActivityThread();
    }

    private static void mendActivityThread() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Constructor<?> constructor = activityThreadClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object activityThread = constructor.newInstance();

            Field currentThread = activityThreadClass.getDeclaredField("sCurrentActivityThread");
            currentThread.setAccessible(true);
            currentThread.set(null, activityThread);

            try {
                Field systemThread = activityThreadClass.getDeclaredField("mSystemThread");
                systemThread.setAccessible(true);
                systemThread.setBoolean(activityThread, true);
            } catch (Throwable ignored) {
                // 字段不存在时忽略
            }

            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    Class<?> controllerClass = Class.forName("android.app.ConfigurationController");
                    Constructor<?> controllerCtor = controllerClass.getDeclaredConstructor(activityThreadClass);
                    controllerCtor.setAccessible(true);
                    Object controller = controllerCtor.newInstance(activityThread);
                    Field controllerField = activityThreadClass.getDeclaredField("mConfigurationController");
                    controllerField.setAccessible(true);
                    controllerField.set(activityThread, controller);
                } catch (Throwable ignored) {
                    // 高版本 ConfigurationController 形态变化时忽略
                }
            }

            Main.log("Workarounds: ActivityThread mended");
        } catch (Throwable t) {
            Main.log("Workarounds skipped: " + t);
        }
    }
}
