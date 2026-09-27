package io.zer0.muse.a11y

/**
 * v2.2.1: 无障碍独立 Provider 的无障碍服务组件。
 *
 * 继承 :accessibility 模块的共享实现(读屏/手势/输入/截图),组件名独立于主应用,
 * 系统设置中的授权项与本 APK 生命周期绑定 —— 主应用更新/重装不影响其运行。
 *
 * 服务实例由基类 companion(`MuseAccessibilityService.instance`)在本进程内注册,
 * [A11yBridgeService] 通过该实例向主应用转发 AIDL 调用。
 */
class A11yProviderAccessibilityService : io.zer0.muse.accessibility.MuseAccessibilityService()
