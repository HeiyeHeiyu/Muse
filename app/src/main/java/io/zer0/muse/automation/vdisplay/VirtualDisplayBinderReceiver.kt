package io.zer0.muse.automation.vdisplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.zer0.common.Logger
import io.zer0.muse.vdproto.VdContract

/**
 * v2.2.1 虚拟屏:服务端 Binder 手递手接收器(exported=true)。
 *
 * 服务端进程以 shell uid 运行,经 AMS 广播(定向到本应用包)送出 binder。
 * 只有 action 与宿主包名同时匹配才接受,防止第三方伪造。
 */
class VirtualDisplayBinderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != VdContract.ACTION_BINDER) return
        val host = intent.getStringExtra(VdContract.EXTRA_HOST_PACKAGE)
        if (host != context.packageName) {
            Logger.w(TAG, "忽略来源不匹配的虚拟屏 binder 广播: host=$host")
            return
        }
        val binder = intent.extras?.getBinder(VdContract.EXTRA_BINDER) ?: return
        VirtualDisplayBinderRegistry.update(binder)
        Logger.i(TAG, "虚拟屏服务端 binder 已就位")
    }

    companion object {
        private const val TAG = "VdBinderReceiver"
    }
}
