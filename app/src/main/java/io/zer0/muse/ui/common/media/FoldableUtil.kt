package io.zer0.muse.ui.common.media

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * v2.x (C9): 折叠屏检测 — 基于公开 Android API([Sensor.TYPE_HINGE_ANGLE],API 30+)。
 *
 * 背景:平台公开 SDK 不暴露折叠线位置(Display.getFeatures / DisplayFeature 均为系统接口,
 * 普通应用不可用);官方可用途径是**铰链角度传感器**——可判断"是否为折叠设备"与
 * "当前展开角度",足以支撑 tabletop(半折竖直摆放)场景判定。
 *
 * 局限:拿不到折叠线在屏幕上的位置/边界;若后续需要精确分区布局,
 * 需评估引入 androidx.window(WindowInfoTracker + FoldingFeature)依赖。
 */
object MuseFoldable {

    /** 是否配备铰链角度传感器(折叠设备);API < 30 恒为 false。 */
    fun hasHingeSensor(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
    }
}

/**
 * 订阅铰链角度(0 = 合拢,180 = 完全展开);非折叠设备 / API < 30 / 无传感器事件时保持 null。
 *
 * 组合进入时注册传感器监听,离开时自动注销。
 * 用法(未来布局分支):
 * ```
 * val angle by rememberHingeAngle()
 * val isTabletop = angle?.let { it in 60f..120f } == true
 * ```
 */
@Composable
fun rememberHingeAngle(): State<Float?> {
    val context = LocalContext.current
    val angle = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context) {
        var listener: SensorEventListener? = null
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        } else {
            null
        }
        if (sm != null && sensor != null) {
            val l = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    angle.value = event.values.firstOrNull()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            listener = l
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose {
            listener?.let { sm?.unregisterListener(it) }
        }
    }
    return angle
}
