package com.appna.briefpopup

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

class BriefPopupActivity : ComponentActivity() {

    private var wakeLock: PowerManager.WakeLock? = null

    private var pkgState by mutableStateOf("")
    private var appNameState by mutableStateOf("")
    private var titleState by mutableStateOf("")
    private var messageState by mutableStateOf("")
    private var accentColorState by mutableIntStateOf(0xFF38BDF8.toInt())
    private var triggerTimestampState by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenAndFullscreen()
        extractIntentData(intent)
        acquireTemporaryWakeLock()

        setContent {
            val durationSeconds = remember(triggerTimestampState) {
                AppPreferences.getDurationSeconds(this@BriefPopupActivity)
            }
            val showEdgeGlow = remember(triggerTimestampState) {
                AppPreferences.isShowEdgeGlow(this@BriefPopupActivity)
            }
            val hideSensitive = remember(triggerTimestampState) {
                AppPreferences.isHideSensitive(this@BriefPopupActivity)
            }

            BriefPopupOverlayScreen(
                packageName = pkgState,
                appName = appNameState,
                title = titleState,
                message = if (hideSensitive) "Nueva notificación recibida" else messageState,
                accentColor = Color(accentColorState),
                durationSeconds = durationSeconds,
                showEdgeGlow = showEdgeGlow,
                triggerKey = triggerTimestampState,
                onOpenNotification = { openNotificationAndFinish() },
                onDismiss = { closeOverlay() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
        acquireTemporaryWakeLock()
    }

    private fun extractIntentData(intent: Intent?) {
        if (intent == null) return
        pkgState = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: packageName
        appNameState = intent.getStringExtra(EXTRA_APP_NAME) ?: "Notificación"
        titleState = intent.getStringExtra(EXTRA_TITLE) ?: appNameState
        messageState = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        accentColorState = intent.getIntExtra(EXTRA_ACCENT_COLOR, 0xFF38BDF8.toInt())
        triggerTimestampState = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
    }

    private fun configureLockScreenAndFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireTemporaryWakeLock() {
        try {
            releaseWakeLock()
            val durationSec = AppPreferences.getDurationSeconds(this)
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "BriefWakePopup::ScreenOffBriefWakeLock"
            ).apply {
                acquire((durationSec + 2) * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }
    }

    private fun openNotificationAndFinish() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val launchAction = {
            try {
                val pending = RecentNotificationActionStore.lastContentIntent
                if (pending != null) {
                    pending.send()
                } else {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkgState)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                }
            } catch (_: Exception) {
            } finally {
                closeOverlay()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguardManager.isKeyguardLocked) {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        launchAction()
                    }

                    override fun onDismissCancelled() {
                        closeOverlay()
                    }

                    override fun onDismissError() {
                        launchAction()
                    }
                }
            )
        } else {
            launchAction()
        }
    }

    private fun closeOverlay() {
        releaseWakeLock()
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ACCENT_COLOR = "extra_accent_color"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
    }
}

@Composable
private fun BriefPopupOverlayScreen(
    packageName: String,
    appName: String,
    title: String,
    message: String,
    accentColor: Color,
    durationSeconds: Int,
    showEdgeGlow: Boolean,
    triggerKey: Long,
    onOpenNotification: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var visible by remember(triggerKey) { mutableStateOf(false) }
    var progressTarget by remember(triggerKey) { mutableStateOf(1f) }

    val appIconBitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable, 96)
        } catch (_: Exception) {
            null
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = durationSeconds * 1000, easing = LinearEasing),
        label = "countdown_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "edge_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    LaunchedEffect(triggerKey) {
        visible = true
        progressTarget = 0f
        delay(durationSeconds * 1000L)
        visible = false
        delay(280L)
        onDismiss()
    }

    // Fondo 100% negro AMOLED: los píxeles negros quedan apagados en pantallas OLED/AMOLED
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onDismiss() }
            )
    ) {
        // Iluminación sutil superior (Edge Lighting estilo Samsung One UI)
        if (showEdgeGlow && visible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                accentColor.copy(alpha = glowAlpha),
                                Color.White.copy(alpha = glowAlpha * 0.7f),
                                accentColor.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Píldora / Pastilla de Resumen emergente (Posición superior estilo One UI Brief Popup)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) +
                scaleIn(initialScale = 0.82f, animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                expandHorizontally(expandFrom = Alignment.CenterHorizontally, animationSpec = tween(280)),
            exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.88f, animationSpec = tween(220)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp, start = 20.dp, end = 20.dp)
        ) {
            val pillShape = RoundedCornerShape(32.dp)
            Column(
                modifier = Modifier
                    .widthIn(min = 240.dp, max = 380.dp)
                    .shadow(
                        elevation = if (showEdgeGlow) 20.dp else 6.dp,
                        shape = pillShape,
                        ambientColor = accentColor,
                        spotColor = accentColor
                    )
                    .clip(pillShape)
                    .background(Color(0xFF12161F))
                    .border(
                        width = if (showEdgeGlow) 1.6.dp else 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = if (showEdgeGlow) {
                                listOf(
                                    accentColor.copy(alpha = glowAlpha),
                                    Color.White.copy(alpha = 0.55f),
                                    accentColor.copy(alpha = glowAlpha)
                                )
                            } else {
                                listOf(Color(0xFF2A3142), Color(0xFF2A3142))
                            }
                        ),
                        shape = pillShape
                    )
                    .clickable { onOpenNotification() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Icono de la aplicación
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIconBitmap != null) {
                            Image(
                                bitmap = appIconBitmap.asImageBitmap(),
                                contentDescription = appName,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = appName,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Texto de resumen (Título + Mensaje compacto estilo Brief)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title.ifBlank { appName },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (title.isNotBlank() && !title.equals(appName, ignoreCase = true)) {
                                Text(
                                    text = " • $appName",
                                    color = accentColor.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }

                        if (message.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = message,
                                color = Color(0xFFD1D5DB),
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Barra inferior de tiempo restante antes de volver a apagar la pantalla
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(Color(0xFF1E2433))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                            .height(2.5.dp)
                            .background(accentColor)
                    )
                }
            }
        }
    }
}

fun drawableToBitmap(drawable: Drawable, maxSizePx: Int): Bitmap {
    val width = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(maxSizePx)
    val height = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(maxSizePx)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

