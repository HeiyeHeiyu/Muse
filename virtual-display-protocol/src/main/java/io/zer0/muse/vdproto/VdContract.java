package io.zer0.muse.vdproto;

/**
 * v2.2.1 虚拟屏:服务端 ↔ 宿主(主应用)的广播手递手常量。
 *
 * 服务端(shell uid)通过 IActivityManager.broadcastIntent(反射)吧带
 * {@link #EXTRA_BINDER} IBinder 的广播发给 {@code setPackage(hostPkg)},
 * 主应用的 {@code VirtualDisplayBinderReceiver}(exported=true)接收后回填注册表。
 *
 * 全部 Java 且无依赖:服务端模块直接引用本类,dex 不携带 kotlin-stdlib。
 */
public final class VdContract {

    /** 手递手广播 action(package 定向,非隐式广播)。 */
    public static final String ACTION_BINDER = "io.zer0.muse.vdproto.BINDER";

    /** 目标(宿主)包名 extra,服务端回显主应用包名供校验收件人。 */
    public static final String EXTRA_HOST_PACKAGE = "host_package";

    /** IBinder extra(IVirtualDisplayService 的 Stub.asBinder())。 */
    public static final String EXTRA_BINDER = "binder";

    /** 服务端 jar 在 /data/local/tmp 下的固定路径(启动器与 pkill 共用)。 */
    public static final String SERVER_JAR_PATH = "/data/local/tmp/muse-vd-server.jar";

    /** 服务端入口类(CLASSPATH + app_process 第二参数)。 */
    public static final String SERVER_MAIN_CLASS = "io.zer0.muse.vdserver.Main";

    /** pkill 特征串(进程命令行包含则被视为旧实例)。 */
    public static final String SERVER_KILL_PATTERN = SERVER_MAIN_CLASS;

    private VdContract() {
    }
}
