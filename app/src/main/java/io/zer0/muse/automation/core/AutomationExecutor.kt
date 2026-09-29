package io.zer0.muse.automation.core

/**
 * UI 自动化动作原语。
 *
 * 三层执行器都实现这些原语,差异仅在于能力范围和实现方式:
 * - 无障碍: 通过 AccessibilityService 读取控件树 + dispatchGesture
 * - Shell: 通过 input 命令 + screencap + dumpsys
 * - Root: 在 Shell 基础上增加 /data/data 访问和系统修改
 *
 * 所有动作均为 suspend,便于在协程中串行/并行调度。
 */
interface AutomationExecutor {

    /** 该执行器对应的权限层级。 */
    val level: PermissionLevel

    /** 该执行器当前是否可用(服务已连接/Shizuku 已授权/root 可 su)。 */
    suspend fun isAvailable(): Boolean

    /** 执行器初始化(连接服务、检查授权等)。 */
    suspend fun initialize() {}

    /** 执行器释放。 */
    suspend fun dispose() {}

    // ── 屏幕读取 ──────────────────────────────────────────────

    /**
     * 截屏,返回 PNG 字节数组。失败返回 null。
     * 各层实现: 无障碍走 MediaProjection / Shell 走 screencap / Root 同 Shell。
     */
    suspend fun screenshot(): ByteArray?

    /**
     * 读取当前屏幕的控件树摘要(文本/描述/位置),供 AI 判断点击目标。
     * 无障碍层最完整;Shell/Root 通过 dumpsys activity 拿到的信息有限。
     */
    suspend fun readScreen(): ScreenInfo

    /** 获取当前前台应用包名。 */
    suspend fun currentPackage(): String?

    // ── 输入动作 ──────────────────────────────────────────────

    /** 点击屏幕坐标。 */
    suspend fun tap(x: Int, y: Int): Boolean

    /** 长按屏幕坐标。 */
    suspend fun longPress(x: Int, y: Int, durationMs: Long = 600): Boolean

    /** 从 (x1,y1) 滑动到 (x2,y2)。 */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 400): Boolean

    /**
     * v2.x: 双指缩放(pinch) — 两指以 (centerX, centerY) 为中心对称开合。
     *
     * @param startDistance 起始两指间距(像素)
     * @param endDistance 结束两指间距(像素);大 = 放大,小 = 缩小
     * 默认不支持(仅无障碍层实现;Shell/Root 的 input 命令无多指注入能力)。
     */
    suspend fun pinch(centerX: Int, centerY: Int, startDistance: Int, endDistance: Int, durationMs: Long = 300): Boolean = false

    /**
     * v2.x: 多段滑动 — 单指依次经过多个路径点(解锁图案/复杂拖拽)。
     * 默认实现降级为首点 → 末点单段滑动;无障碍层为精确逐点实现。
     */
    suspend fun swipePath(points: List<Pair<Int, Int>>, durationMs: Long = 400): Boolean {
        if (points.size < 2) return false
        val first = points.first()
        val last = points.last()
        return swipe(first.first, first.second, last.first, last.second, durationMs)
    }

    /** 输入文本(往当前聚焦的输入框写入)。 */
    suspend fun inputText(text: String): Boolean

    /** 发送按键事件(KEYCODE_*)。 */
    suspend fun pressKey(keyCode: Int): Boolean

    // ── 系统动作 ──────────────────────────────────────────────

    /** 启动指定包名的 App。 */
    suspend fun launchApp(packageName: String): Boolean

    /** 按下返回键。 */
    suspend fun back(): Boolean = pressKey(4) // KEYCODE_BACK

    /** 按下 Home 键。 */
    suspend fun home(): Boolean = pressKey(3) // KEYCODE_HOME

    /** 打开通知栏。 */
    suspend fun openNotifications(): Boolean

    /** 打开快速设置。 */
    suspend fun openQuickSettings(): Boolean
}
