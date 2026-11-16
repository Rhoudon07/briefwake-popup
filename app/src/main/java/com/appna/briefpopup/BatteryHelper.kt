package com.appna.briefpopup

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class BatteryStatus(
    val level: Int,
    val isCharging: Boolean
)

@Composable
fun rememberBatteryStatus(): State<BatteryStatus> {
    val context = LocalContext.current
    return remember {
        mutableStateOf(getCurrentBatteryStatus(context))
    }
}

/**
 * Consulta el estado de batería aprovechando el Sticky Intent del sistema.
 * Pasar null como receptor no crea un listener en ActivityManager,
 * evitando registrar y desregistrar receptores en cada notificación de 5 segundos.
 */
fun getCurrentBatteryStatus(context: Context): BatteryStatus {
    return try {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(null, filter)
        if (stickyIntent != null) {
            val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            BatteryStatus(pct, isCharging)
        } else {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = batteryManager?.isCharging == true
            BatteryStatus(level, isCharging)
        }
    } catch (_: Exception) {
        BatteryStatus(-1, false)
    }
}
