package com.appna.briefpopup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

class BriefNotificationListenerService : NotificationListenerService() {

    private var lastSignature: String = ""
    private var lastTimestampMs: Long = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val context = applicationContext
        if (!AppPreferences.isMasterEnabled(context)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras

        val isTest = extras?.getBoolean("is_brief_test", false) == true

        // Ignorar notificaciones permanentes/en curso (reproductores, descargas, servicios activos)
        if (!isTest && sbn.isOngoing) return
        if (!isTest && (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        // Ignorar encabezados vacíos de resumen de grupo (por ej. WhatsApp envía el mensaje y aparte un group summary)
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (!isTest && isGroupSummary) return

        val pkg = sbn.packageName ?: return
        if (!isTest && pkg == context.packageName) return

        // Verificar si la app está en la lista elegida por el usuario
        if (!isTest && !AppPreferences.isPackageEnabled(context, pkg)) return

        // Verificar si la pantalla está apagada (a menos que el usuario haya desactivado "Solo pantalla apagada")
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenInteractive = powerManager.isInteractive
        if (AppPreferences.isOnlyWhenScreenOff(context) && isScreenInteractive && !isTest) {
            return
        }

        val rawTitle = extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: getAppLabel(context, pkg)

        val rawText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: ""

        if (rawTitle.isBlank() && rawText.isBlank()) return

        // Evitar duplicados en ráfaga (< 1200 ms con exactamente el mismo texto)
        val signature = "$pkg|$rawTitle|$rawText"
        val now = System.currentTimeMillis()
        if (!isTest && signature == lastSignature && (now - lastTimestampMs) < 1200L) {
            return
        }
        lastSignature = signature
        lastTimestampMs = now

        val accentColor = if (notification.color != Notification.COLOR_DEFAULT && notification.color != 0) {
            notification.color
        } else {
            defaultAccentForPackage(pkg)
        }

        triggerBriefPopup(
            context = context,
            packageName = pkg,
            appName = getAppLabel(context, pkg),
            title = rawTitle.trim(),
            message = rawText.trim(),
            accentColor = accentColor,
            contentIntent = notification.contentIntent
        )
    }

    companion object {
        private const val TAG = "BriefWakeListener"
        private const val CHANNEL_ID = "brief_wake_popup_channel"
        private const val NOTIFICATION_ID = 9911

        fun getAppLabel(context: Context, packageName: String): String {
            return try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                packageName
            }
        }

        fun defaultAccentForPackage(packageName: String): Int {
            return when {
                packageName.contains("whatsapp") -> 0xFF25D366.toInt()
                packageName.contains("telegram") -> 0xFF2AABEE.toInt()
                packageName.contains("instagram") -> 0xFFE1306C.toInt()
                packageName.contains("gm") || packageName.contains("mail") -> 0xFFEA4335.toInt()
                packageName.contains("orca") || packageName.contains("facebook") -> 0xFF0084FF.toInt()
                packageName.contains("discord") -> 0xFF5865F2.toInt()
                packageName.contains("messaging") -> 0xFF38BDF8.toInt()
                else -> 0xFF38BDF8.toInt()
            }
        }

        @Suppress("DEPRECATION")
        fun triggerBriefPopup(
            context: Context,
            packageName: String,
            appName: String,
            title: String,
            message: String,
            accentColor: Int,
            contentIntent: PendingIntent? = null
        ) {
            RecentNotificationActionStore.lastContentIntent = contentIntent
            RecentNotificationActionStore.lastPackageName = packageName

            val popupIntent = Intent(context, BriefPopupActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(BriefPopupActivity.EXTRA_PACKAGE_NAME, packageName)
                putExtra(BriefPopupActivity.EXTRA_APP_NAME, appName)
                putExtra(BriefPopupActivity.EXTRA_TITLE, title)
                putExtra(BriefPopupActivity.EXTRA_MESSAGE, message)
                putExtra(BriefPopupActivity.EXTRA_ACCENT_COLOR, accentColor)
                putExtra(BriefPopupActivity.EXTRA_TIMESTAMP, System.currentTimeMillis())
            }

            // 1. Intentar lanzar directamente la actividad
            var directLaunchSuccess = false
            try {
                context.startActivity(popupIntent)
                directLaunchSuccess = true
            } catch (e: Exception) {
                Log.w(TAG, "Direct startActivity falló o fue bloqueado por política de segundo plano", e)
            }

            // 2. Mecanismo de respaldo Full Screen Intent (requerido en Android 10+ / Samsung One UI para arrancar sobre pantalla bloqueada)
            try {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val fullScreenPendingIntent = PendingIntent.getActivity(
                    context,
                    System.currentTimeMillis().toInt(),
                    popupIntent,
                    flags
                )

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "BriefWake Overlay Service",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Canal de lanzamiento para pantalla bloqueada"
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val headsUpNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setAutoCancel(true)
                    .setTimeoutAfter(2500L)
                    .setSilent(true)
                    .build()

                notificationManager.notify(NOTIFICATION_ID, headsUpNotification)
                // Cancelar la notificación auxiliar brevemente después para no dejar residuo en la barra
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        notificationManager.cancel(NOTIFICATION_ID)
                    } catch (_: Exception) {}
                }, 2000L)
            } catch (e: Exception) {
                Log.e(TAG, "Error disparando FullScreenIntent de respaldo", e)
            }
        }
    }
}
