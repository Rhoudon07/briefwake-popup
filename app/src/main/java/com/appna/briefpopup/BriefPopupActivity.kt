package com.appna.briefpopup

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
    @Volatile
    private var isUserInteracted = false

    private var userPresentReceiver: BroadcastReceiver? = null

    private var pkgState by mutableStateOf("")
    private var appNameState by mutableStateOf("")
    private var titleState by mutableStateOf("")
    private var messageState by mutableStateOf("")
    private var accentColorState by mutableIntStateOf(0xFF38BDF8.toInt())
    private var triggerTimestampState by mutableLongStateOf(0L)
    private var isPreviewMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isUserInteracted = false

        configureAodLockScreenMode()
        extractIntentData(intent)
        acquireTemporaryWakeLock()
        setupUnlockDetection()

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
            val isWhatsappCompact = remember(triggerTimestampState, pkgState) {
                pkgState.contains("whatsapp", ignoreCase = true) &&
                    AppPreferences.isWhatsappCompactPrivate(this@BriefPopupActivity)
            }
            val popupAnimation = remember(triggerTimestampState) {
                AppPreferences.getPopupAnimation(this@BriefPopupActivity)
            }

            val batteryStatus by rememberBatteryStatus()

            BriefPopupOverlayScreen(
                packageName = pkgState,
                appName = if (isWhatsappCompact) "WhatsApp" else appNameState,
                title = if (isWhatsappCompact) "WhatsApp" else titleState,
                message = when {
                    isWhatsappCompact -> "Nuevo mensaje"
                    hideSensitive -> "Nueva notificación recibida"
                    else -> messageState
                },
                accentColor = Color(accentColorState),
                durationSeconds = durationSeconds,
                showEdgeGlow = showEdgeGlow,
                batteryStatus = batteryStatus,
                isCompactMode = isWhatsappCompact,
                animationType = popupAnimation,
                triggerKey = triggerTimestampState,
                onOpenNotification = {
                    isUserInteracted = true
                    openNotificationAndProceedUnlock()
                },
                onManualDismiss = {
                    isUserInteracted = false
                    dismissAndTurnOff()
                },
                onTimeoutExpired = {
                    if (!isUserInteracted) {
                        dismissAndTurnOff()
                    }
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isUserInteracted = false
        extractIntentData(intent)
        acquireTemporaryWakeLock()
    }

    override fun onResume() {
        super.onResume()
        if (!isPreviewMode) {
            checkIfAlreadyUnlockedAndFinish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Si la ventana pierde foco porque el sistema inició el desbloqueo hacia el Launcher o app
        if (!hasFocus && !isPreviewMode) {
            checkIfAlreadyUnlockedAndFinish()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        isUserInteracted = true
    }

    /**
     * Detección reactiva de desbloqueo por huella dactilar, rostro o PIN:
     * 1. BroadcastReceiver para ACTION_USER_PRESENT emitido por el sistema al desbloquear.
     * 2. onWindowFocusChanged / onResume para cambios inmediatos de foco.
     * Cero bucles de polling para permitir que la CPU permanezca en reposo.
     */
    private fun setupUnlockDetection() {
        if (isPreviewMode) return
        userPresentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (Intent.ACTION_USER_PRESENT == intent?.action) {
                    closeActivitySilently()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(userPresentReceiver, filter)
    }

    private fun checkIfAlreadyUnlockedAndFinish() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked && !isFinishing && !isDestroyed) {
            closeActivitySilently()
        }
    }

    private fun extractIntentData(intent: Intent?) {
        if (intent == null) return
        pkgState = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: packageName
        appNameState = intent.getStringExtra(EXTRA_APP_NAME) ?: "Notificación"
        titleState = intent.getStringExtra(EXTRA_TITLE) ?: appNameState
        messageState = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        accentColorState = intent.getIntExtra(EXTRA_ACCENT_COLOR, 0xFF38BDF8.toInt())
        triggerTimestampState = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        isPreviewMode = intent.getBooleanExtra(EXTRA_IS_PREVIEW, false)
    }

    private fun configureAodLockScreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val layoutParams = window.attributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = layoutParams

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
                    PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "BriefWakePopup::AodScreenOffWakeLock"
            ).apply {
                acquire((durationSec + 1) * 1000L)
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

    /**
     * Si el usuario toca directamente la pastilla
     */
    private fun openNotificationAndProceedUnlock() {
        isUserInteracted = true
        if (isPreviewMode) {
            closeActivitySilently()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val launchTargetApp = {
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
                closeActivitySilently()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguardManager.isKeyguardLocked) {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        launchTargetApp()
                    }

                    override fun onDismissCancelled() {
                        closeActivitySilently()
                    }

                    override fun onDismissError() {
                        launchTargetApp()
                    }
                }
            )
        } else {
            launchTargetApp()
        }
    }

    /**
     * Al expirar el tiempo sin interacción: apaga la pantalla
     */
    private fun dismissAndTurnOff() {
        if (isUserInteracted) return

        releaseWakeLock()

        // En modo vista previa ("Ver ahora"), simplemente cerramos la actividad sin apagar la pantalla
        if (isPreviewMode) {
            closeActivitySilently()
            return
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // Si el teléfono ya fue desbloqueado, no apagar la pantalla
        if (!km.isKeyguardLocked) {
            closeActivitySilently()
            return
        }

        // Si sigue en bloqueo y no hubo toque, apagar pantalla con Accesibilidad
        if (BriefAccessibilityService.isRunning() && pm.isInteractive) {
            BriefAccessibilityService.lockScreen()
        }

        closeActivitySilently()
    }

    private fun closeActivitySilently() {
        stopUnlockDetection()
        releaseWakeLock()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setTurnScreenOn(false)
            }
            @Suppress("DEPRECATION")
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        } catch (_: Exception) {}

        finish()
        overridePendingTransition(0, 0)
    }

    private fun stopUnlockDetection() {
        userPresentReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        userPresentReceiver = null
    }

    override fun onDestroy() {
        stopUnlockDetection()
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
        const val EXTRA_IS_PREVIEW = "extra_is_preview"

        // Caché en memoria para evitar llamadas IPC reiteradas y decodificación de bitmaps
        private val iconCache = android.util.LruCache<String, Bitmap>(30)

        fun getAppIcon(context: Context, packageName: String): Bitmap? {
            val cached = iconCache.get(packageName)
            if (cached != null) return cached
            return try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawableToBitmap(drawable, 96)
                iconCache.put(packageName, bitmap)
                bitmap
            } catch (_: Exception) {
                null
            }
        }
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
    batteryStatus: BatteryStatus,
    isCompactMode: Boolean = false,
    animationType: String = AppPreferences.ANIMATION_SLIDE,
    triggerKey: Long,
    onOpenNotification: () -> Unit,
    onManualDismiss: () -> Unit,
    onTimeoutExpired: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var visible by remember(triggerKey) { mutableStateOf(false) }
    var progressTarget by remember(triggerKey) { mutableStateOf(1f) }

    val appIconBitmap = remember(packageName) {
        BriefPopupActivity.getAppIcon(context, packageName)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = durationSeconds * 1000, easing = LinearEasing),
        label = "countdown_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "edge_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val topGlowBrush = remember(accentColor) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                accentColor.copy(alpha = 0.7f),
                Color.White.copy(alpha = 0.5f),
                accentColor.copy(alpha = 0.7f),
                Color.Transparent
            )
        )
    }

    LaunchedEffect(triggerKey) {
        visible = true
        progressTarget = 0f
        delay(durationSeconds * 1000L)
        visible = false
        delay(120L)
        onTimeoutExpired()
    }

    // Lienzo AOD Negro Absoluto (#000000): En Super AMOLED los píxeles negros están físicamente apagados
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onManualDismiss() }
            )
    ) {
        // Línea sutil superior Edge Lighting sobre la pantalla (dibujada en GPU sin recomponer la UI)
        if (showEdgeGlow && visible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = glowAlpha }
                    .background(topGlowBrush)
            )
        }

        // Transiciones configurables de entrada y salida
        val (enterTransition, exitTransition) = remember(animationType) {
            when (animationType) {
                AppPreferences.ANIMATION_SLIDE -> {
                    (slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(180))) to (slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(160)))
                }
                AppPreferences.ANIMATION_SCALE -> {
                    (scaleIn(
                        initialScale = 0.72f,
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(180))) to (scaleOut(
                        targetScale = 0.82f,
                        animationSpec = tween(180)
                    ) + fadeOut(tween(160)))
                }
                AppPreferences.ANIMATION_FADE -> {
                    fadeIn(tween(280, easing = LinearEasing)) to
                        fadeOut(tween(200, easing = LinearEasing))
                }
                AppPreferences.ANIMATION_EXPAND -> {
                    (expandHorizontally(
                        expandFrom = Alignment.CenterHorizontally,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + scaleIn(initialScale = 0.88f, animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(180))) to
                        (fadeOut(tween(160)) + scaleOut(targetScale = 0.90f, animationSpec = tween(160)))
                }
                else -> {
                    (slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(180))) to (slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(160)))
                }
            }
        }

        // Pastilla de notificación estilo Resumen emergente
        AnimatedVisibility(
            visible = visible,
            enter = enterTransition,
            exit = exitTransition,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 58.dp, start = 18.dp, end = 18.dp)
        ) {
            val pillShape = RoundedCornerShape(if (isCompactMode) 20.dp else 26.dp)
            val borderBrush = remember(showEdgeGlow, accentColor) {
                if (showEdgeGlow) {
                    Brush.horizontalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.6f),
                            accentColor.copy(alpha = 0.85f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF334155)))
                }
            }

            Box(
                modifier = Modifier
                    .widthIn(
                        min = if (isCompactMode) 170.dp else 230.dp,
                        max = if (isCompactMode) 280.dp else 380.dp
                    )
                    .clip(pillShape)
                    .background(Color(0xFF11141C))
                    .clickable { onOpenNotification() }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (isCompactMode) 10.dp else 14.dp,
                                end = if (isCompactMode) 8.dp else 10.dp,
                                top = if (isCompactMode) 6.dp else 10.dp,
                                bottom = if (isCompactMode) 6.dp else 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                    // Icono de la aplicación
                    Box(
                        modifier = Modifier
                            .size(if (isCompactMode) 26.dp else 34.dp)
                            .clip(CircleShape)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIconBitmap != null) {
                            Image(
                                bitmap = appIconBitmap.asImageBitmap(),
                                contentDescription = appName,
                                modifier = Modifier
                                    .size(if (isCompactMode) 24.dp else 32.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = appName,
                                tint = accentColor,
                                modifier = Modifier.size(if (isCompactMode) 16.dp else 20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(if (isCompactMode) 8.dp else 10.dp))

                    // Textos de notificación: modo compacto estilizado o modo completo estándar
                    if (isCompactMode) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "WhatsApp",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = " • Nuevo mensaje",
                                color = accentColor.copy(alpha = 0.95f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
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
                                        color = accentColor.copy(alpha = 0.95f),
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
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(if (isCompactMode) 4.dp else 6.dp))

                    IconButton(
                        onClick = onManualDismiss,
                        modifier = Modifier.size(if (isCompactMode) 22.dp else 26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(if (isCompactMode) 13.dp else 15.dp)
                        )
                    }
                }

                // Línea inferior de cuenta regresiva (dibujada en canvas de GPU sin provocar recomposición)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.Black)
                        .drawBehind {
                            val progress = animatedProgress.coerceIn(0f, 1f)
                            if (progress > 0f) {
                                drawRect(
                                    color = accentColor,
                                    size = androidx.compose.ui.geometry.Size(size.width * progress, size.height)
                                )
                            }
                        }
                )
            }

            // Halo / Borde exterior animado en la fase de dibujo de la GPU
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = if (showEdgeGlow) glowAlpha else 1f }
                    .border(
                        width = 1.2.dp,
                        brush = borderBrush,
                        shape = pillShape
                    )
            )
        }
    }

        // Indicador inferior de carga de batería (Estilo AOD Samsung)
        if (batteryStatus.isCharging && batteryStatus.level >= 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Cargando",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${batteryStatus.level}% Carga de batería",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
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
