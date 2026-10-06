package io.zer0.muse.vdserver;

import android.content.Intent;
import android.os.IBinder;
import io.zer0.muse.vdproto.VdContract;

import java.lang.reflect.Method;

/**
 * v2.2.1 虚拟屏:Binder 手递手。
 *
 * 服务端(shell uid)无法注册系统服务,改为把 IVirtualDisplayService 的 binder
 * 经 package 定向广播发给宿主(主应用)的 exported receiver:
 * 反射调 IActivityManager.broadcastIntent / broadcastIntentWithFeature,
 * 参数按"名字 + 数量 + 类型"两套已知签名布局填默认值。
 */
public final class BinderHandoff {

    private BinderHandoff() {
    }

    public static void publish(IBinder binder, String hostPackage, String handoffToken) {
        if (hostPackage == null || hostPackage.isEmpty()
                || handoffToken == null || handoffToken.isEmpty()) {
            Main.log("host package or handoff token missing; binder not published");
            return;
        }
        Intent intent = new Intent(VdContract.ACTION_BINDER);
        intent.setPackage(hostPackage);
        intent.putExtra(VdContract.EXTRA_HOST_PACKAGE, hostPackage);
        intent.putExtra(VdContract.EXTRA_HANDOFF_TOKEN, handoffToken);
        android.os.Bundle extras = new android.os.Bundle();
        extras.putBinder(VdContract.EXTRA_BINDER, binder);
        intent.putExtras(extras);
        try {
            broadcastViaActivityManager(intent);
            Main.log("binder published to " + hostPackage);
        } catch (Throwable t) {
            Main.log("binder publish failed: " + t);
        }
    }

    private static void broadcastViaActivityManager(Intent intent) throws Exception {
        Class<?> activityManager = Class.forName("android.app.ActivityManager");
        Method getService = activityManager.getMethod("getService");
        Object service = getService.invoke(null);

        Method target = null;
        for (Method method : service.getClass().getMethods()) {
            String name = method.getName();
            if (!"broadcastIntentWithFeature".equals(name) && !"broadcastIntent".equals(name)) {
                continue;
            }
            int count = method.getParameterTypes().length;
            // 旧签名 13 参;Android 13+ 带 feature 的 16 参
            if (count == 13 || count == 16) {
                target = method;
                if (count == 16) {
                    break;
                }
            }
        }
        if (target == null) {
            throw new IllegalStateException("broadcastIntent method not found");
        }

        Class<?>[] types = target.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == Intent.class) {
                args[i] = intent;
            } else if (type == int.class) {
                args[i] = isMinusOneInt(types.length, i) ? -1 : 0;
            } else if (type == boolean.class) {
                args[i] = false;
            } else {
                // IApplicationThread / String / IIntentReceiver / Bundle / String[] 一律 null
                args[i] = null;
            }
        }
        target.invoke(service, args);
    }

    /** appOp / userId 位置按签名布局取 -1(AppOpsManager.OP_NONE / UserHandle.USER_ALL)。 */
    private static boolean isMinusOneInt(int count, int index) {
        if (count == 13) {
            return index == 8 || index == 12;
        }
        return index == 11 || index == 15;
    }
}
