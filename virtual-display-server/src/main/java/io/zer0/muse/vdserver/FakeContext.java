package io.zer0.muse.vdserver;

import android.content.Context;
import android.content.ContextWrapper;

/**
 * v2.2.1 虚拟屏:进程伪装上下文。
 *
 * 服务端以 shell uid 运行,但 framework 侧(DisplayManagerService / WMS)会校验
 * "调用方包名与调用 uid 一致"。本类把自己伪装成 com.android.shell(uid 2000 的
 * 官方包),让反射构造的 DisplayManager 通过校验。
 */
public final class FakeContext extends ContextWrapper {

    /** shell (adb) uid,与 com.android.shell 包匹配。 */
    public static final int SHELL_UID = 2000;

    private static final String SHELL_PACKAGE = "com.android.shell";

    public FakeContext() {
        super(null);
    }

    @Override
    public String getPackageName() {
        return SHELL_PACKAGE;
    }

    @Override
    public String getOpPackageName() {
        return SHELL_PACKAGE;
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public Context createDisplayContext(android.view.Display display) {
        return this;
    }

    /**
     * ContextWrapper 默认把 getSystemServiceName/getSystemService 委派给 mBase,
     * 而 mBase 为 null 会抛 NPE。这里除 DisplayManager 外,统一回退到"系统上下文"
     * (由 Workarounds 修补后的 ActivityThread 提供)解析真实系统服务
     * (如 UserManager —— DisplayManager 内部会查 isVisibleBackgroundUsersSupported)。
     */
    @Override
    public String getSystemServiceName(Class<?> serviceClass) {
        if (serviceClass == android.hardware.display.DisplayManager.class) {
            return android.content.Context.DISPLAY_SERVICE;
        }
        android.content.Context ctx = systemContext();
        return ctx != null ? ctx.getSystemServiceName(serviceClass) : null;
    }

    @Override
    public Object getSystemService(String name) {
        android.content.Context ctx = systemContext();
        return ctx != null ? ctx.getSystemService(name) : null;
    }

    // 注:Context.getSystemService(Class) 为 final(内部会调上面的 Name/String 两个覆写),
    // 因此不需要(也不能)在本类覆写。

    /** 借修补后的 ActivityThread 拿系统上下文(仅用于解析系统服务对象)。 */
    private static android.content.Context systemContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Field field = activityThreadClass.getDeclaredField("sCurrentActivityThread");
            field.setAccessible(true);
            Object activityThread = field.get(null);
            if (activityThread == null) {
                return null;
            }
            java.lang.reflect.Method method = activityThreadClass.getMethod("getSystemContext");
            return (android.content.Context) method.invoke(activityThread);
        } catch (Throwable t) {
            Main.log("systemContext unavailable: " + t);
            return null;
        }
    }

    @Override
    public android.content.AttributionSource getAttributionSource() {
        try {
            android.content.AttributionSource.Builder builder =
                    new android.content.AttributionSource.Builder(SHELL_UID);
            builder.setPackageName(SHELL_PACKAGE);
            return builder.build();
        } catch (Throwable t) {
            return null;
        }
    }
}
