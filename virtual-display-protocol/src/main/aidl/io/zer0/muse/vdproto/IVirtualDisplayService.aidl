// v2.2.1 虚拟屏:服务端契约(AIDL)。
//
// 服务端: io.zer0.muse.vdserver.Main 起的 shell uid app_process 进程(独立 jar)。
// 客户端: io.zer0.muse.automation.vdisplay.VirtualDisplayClient(主应用进程),
//         经广播手递手拿到 IBinder 后通过本接口调用。
//
// 设计原则:
//  - 全部同步返回(无 oneway),调用方拿到确定结果
//  - 截图返回图像字节(高分辨率下用 JPEG 编码,避开 Binder 1MB 事务上限;
//    失败返回 null);建议虚拟屏宽度 <= 1080、高度 <= 1920
//  - 输入注入不走本接口(用 `input -d <displayId>` shell 命令,复用既有执行器)
package io.zer0.muse.vdproto;

interface IVirtualDisplayService {

    // 确保虚拟屏存在(同尺寸复用;尺寸变化时重建)。返回 displayId;失败返回 -1。
    int ensureDisplay(int width, int height, int dpi);

    // 在指定虚拟屏里启动应用(服务端以 shell 身份解析 launcher 活动并 am start --display)。
    // 成功返回 true。
    boolean launchApp(String packageName, int displayId);

    // 销毁指定虚拟屏(不存在则忽略)。
    void destroyDisplay(int displayId);

    // 抓取指定虚拟屏的一帧 PNG(超时/失败返回 null)。同时用于保活。
    byte[] requestScreenshot(int displayId);

    // 服务端存活探测(同时刷新保活时间戳)。
    boolean isAlive();
}
