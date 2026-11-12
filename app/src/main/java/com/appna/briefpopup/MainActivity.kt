package com.appna.briefpopup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val iconBitmap: Bitmap?
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF818CF8),
    background = Color(0xFF090D16),
    surface = Color(0xFF131926),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AmoledColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BriefWakeMainScreen()
                }
            }
        }
    }
}

@Composable
fun BriefWakeMainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estado de permisos
    var hasNotificationAccess by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var ignoresBatteryOptimizations by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    // Preferencias de usuario
    var masterEnabled by remember { mutableStateOf(AppPreferences.isMasterEnabled(context)) }
    var onlyScreenOff by remember { mutableStateOf(AppPreferences.isOnlyWhenScreenOff(context)) }
    var durationSeconds by remember { mutableIntStateOf(AppPreferences.getDurationSeconds(context)) }
    var showEdgeGlow by remember { mutableStateOf(AppPreferences.isShowEdgeGlow(context)) }
    var hideSensitive by remember { mutableStateOf(AppPreferences.isHideSensitive(context)) }
    var enabledPackages by remember { mutableStateOf(AppPreferences.getEnabledPackages(context)) }

    // Lista de apps instaladas
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    // Filtros del selector de apps
    var searchQuery by remember { mutableStateOf("") }
    var showOnlySelected by remember { mutableStateOf(false) }
    var includeSystemApps by remember { mutableStateOf(false) }

    // Cuenta regresiva para probar con pantalla apagada
    var testCountdown by remember { mutableIntStateOf(0) }

    // Actualizar permisos cada vez que el usuario vuelve de la pantalla de Ajustes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = isNotificationListenerEnabled(context)
                hasOverlayPermission = Settings.canDrawOverlays(context)
                ignoresBatteryOptimizations = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Cargar aplicaciones instaladas en segundo plano
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = loadInstalledApps(context)
            val initializedSet = AppPreferences.initializeDefaultAppsIfNeeded(
                context,
                apps.map { it.packageName }.toSet()
            )
            withContext(Dispatchers.Main) {
                installedApps = apps
                enabledPackages = initializedSet
                isLoadingApps = false
            }
        }
    }

    // Temporizador para el botón de prueba con pantalla apagada
    LaunchedEffect(testCountdown) {
        if (testCountdown > 0) {
            delay(1000L)
            testCountdown -= 1
            if (testCountdown == 0) {
                Handler(Looper.getMainLooper()).post {
                    BriefNotificationListenerService.triggerBriefPopup(
                        context = context.applicationContext,
                        packageName = context.packageName,
                        appName = "WhatsApp",
                        title = "Mensaje de Prueba",
                        message = "¡Así se ve el resumen emergente con la pantalla apagada!",
                        accentColor = 0xFF25D366.toInt()
                    )
                }
            }
        }
    }

    val filteredApps = remember(installedApps, searchQuery, showOnlySelected, includeSystemApps, enabledPackages) {
        installedApps.filter { app ->
            val matchesSystem = includeSystemApps || !app.isSystemApp || enabledPackages.contains(app.packageName)
            val matchesSelected = !showOnlySelected || enabledPackages.contains(app.packageName)
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesSystem && matchesSelected && matchesQuery
        }
    }

    Scaffold(
        containerColor = Color(0xFF090D16)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Encabezado
            item {
                Column {
                    Text(
                        text = "BriefWake Popup",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Notificaciones emergentes tipo resumen con la pantalla apagada para las apps que tú elijas.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Tarjeta de Permisos Requeridos
            item {
                PermissionsCard(
                    hasNotificationAccess = hasNotificationAccess,
                    hasOverlayPermission = hasOverlayPermission,
                    ignoresBatteryOptimizations = ignoresBatteryOptimizations,
                    onOpenNotificationAccess = { openNotificationListenerSettings(context) },
                    onOpenOverlaySettings = { openOverlaySettings(context) },
                    onRequestBatteryExemption = { requestIgnoreBatteryOptimizations(context) }
                )
            }

            // Tarjeta de Prueba en Vivo (con bloqueo de pantalla)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131926)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Probar pastilla emergente",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (testCountdown > 0) {
                                "Apaga la pantalla de tu celular AHORA... Mostrando resumen en ${testCountdown}s"
                            } else {
                                "Pulsa el botón y bloquea/apaga tu pantalla de inmediato. En 4 segundos se encenderá la pastilla estilo resumen sobre fondo negro AMOLED."
                            },
                            fontSize = 12.sp,
                            color = if (testCountdown > 0) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { testCountdown = 4 },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF38BDF8),
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (testCountdown > 0) "Esperando (${testCountdown}s)..." else "Probar en 4s (Apagar pantalla)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    BriefNotificationListenerService.triggerBriefPopup(
                                        context = context.applicationContext,
                                        packageName = context.packageName,
                                        appName = "Telegram",
                                        title = "Vista Previa Inmediata",
                                        message = "Pastilla emergente tipo resumen con iluminación Edge.",
                                        accentColor = 0xFF2AABEE.toInt()
                                    )
                                }
                            ) {
                                Text("Ver ahora", color = Color(0xFF38BDF8), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Ajustes de Comportamiento y Estilo
            item {
                SettingsCard(
                    masterEnabled = masterEnabled,
                    onMasterEnabledChange = {
                        masterEnabled = it
                        AppPreferences.setMasterEnabled(context, it)
                    },
                    onlyScreenOff = onlyScreenOff,
                    onOnlyScreenOffChange = {
                        onlyScreenOff = it
                        AppPreferences.setOnlyWhenScreenOff(context, it)
                    },
                    showEdgeGlow = showEdgeGlow,
                    onShowEdgeGlowChange = {
                        showEdgeGlow = it
                        AppPreferences.setShowEdgeGlow(context, it)
                    },
                    hideSensitive = hideSensitive,
                    onHideSensitiveChange = {
                        hideSensitive = it
                        AppPreferences.setHideSensitive(context, it)
                    },
                    durationSeconds = durationSeconds,
                    onDurationChange = {
                        durationSeconds = it
                        AppPreferences.setDurationSeconds(context, it)
                    }
                )
            }

            // Encabezado y filtros de selección de Apps
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131926)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Apps permitidas (${enabledPackages.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Elige qué apps encenderán el resumen con pantalla apagada",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar app (ej. WhatsApp, Instagram...)", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8))
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF263044),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = showOnlySelected,
                                onClick = { showOnlySelected = !showOnlySelected },
                                label = { Text("Solo activas (${enabledPackages.size})", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF38BDF8)
                                )
                            )

                            FilterChip(
                                selected = includeSystemApps,
                                onClick = { includeSystemApps = !includeSystemApps },
                                label = { Text("Apps del sistema", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF38BDF8)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val installedSet = installedApps.map { it.packageName }.toSet()
                                    val updated = enabledPackages + installedSet.intersect(AppPreferences.COMMON_MESSAGING_PACKAGES)
                                    AppPreferences.setEnabledPackages(context, updated)
                                    enabledPackages = updated
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Activar Mensajería", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            }

                            OutlinedButton(
                                onClick = {
                                    AppPreferences.setEnabledPackages(context, emptySet())
                                    enabledPackages = emptySet()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Desmarcar todas", fontSize = 12.sp, color = Color(0xFFF87171))
                            }
                        }
                    }
                }
            }

            if (isLoadingApps) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cargando aplicaciones instaladas...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isSelected = enabledPackages.contains(app.packageName)
                    AppItemRow(
                        app = app,
                        isSelected = isSelected,
                        onToggle = { checked ->
                            enabledPackages = AppPreferences.togglePackage(context, app.packageName, checked)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    hasNotificationAccess: Boolean,
    hasOverlayPermission: Boolean,
    ignoresBatteryOptimizations: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit
) {
    val allGranted = hasNotificationAccess && hasOverlayPermission && ignoresBatteryOptimizations
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (allGranted) Color(0xFF0F241D) else Color(0xFF1E1B18)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (allGranted) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (allGranted) {
                    "Permisos listos para funcionar con pantalla apagada"
                } else {
                    "Permisos necesarios (Actívalos una sola vez)"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White
            )

            PermissionRow(
                icon = Icons.Default.NotificationsActive,
                title = "1. Acceso a Notificaciones",
                subtitle = "Permite detectar cuándo llega un mensaje de las apps elegidas",
                granted = hasNotificationAccess,
                onClick = onOpenNotificationAccess
            )

            PermissionRow(
                icon = Icons.Default.Layers,
                title = "2. Mostrar sobre otras apps",
                subtitle = "Requerido en Android 10+ para encender la pastilla desde segundo plano",
                granted = hasOverlayPermission,
                onClick = onOpenOverlaySettings
            )

            PermissionRow(
                icon = Icons.Default.BatteryChargingFull,
                title = "3. Batería sin restricciones (One UI)",
                subtitle = "Evita que Samsung cierre el detector cuando la pantalla lleva rato apagada",
                granted = ignoresBatteryOptimizations,
                onClick = onRequestBatteryExemption
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131926))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (granted) Color(0xFF10B981) else Color(0xFFF59E0B),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (granted) Color(0xFF10B981) else Color(0xFFF59E0B),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsCard(
    masterEnabled: Boolean,
    onMasterEnabledChange: (Boolean) -> Unit,
    onlyScreenOff: Boolean,
    onOnlyScreenOffChange: (Boolean) -> Unit,
    showEdgeGlow: Boolean,
    onShowEdgeGlowChange: (Boolean) -> Unit,
    hideSensitive: Boolean,
    onHideSensitiveChange: (Boolean) -> Unit,
    durationSeconds: Int,
    onDurationChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131926)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Comportamiento y Estilo Resumen",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.White
            )

            SettingSwitchRow(
                title = "Activar BriefWake Popup",
                subtitle = "Interruptor general del servicio",
                checked = masterEnabled,
                onCheckedChange = onMasterEnabledChange
            )

            SettingSwitchRow(
                title = "Mostrar solo con pantalla apagada",
                subtitle = "Si lo desactivas, también mostrará la pastilla con la pantalla encendida",
                checked = onlyScreenOff,
                onCheckedChange = onOnlyScreenOffChange
            )

            SettingSwitchRow(
                title = "Halo luminoso + Edge Lighting",
                subtitle = "Ilumina el borde de la pastilla con el color de la app",
                checked = showEdgeGlow,
                onCheckedChange = onShowEdgeGlowChange
            )

            SettingSwitchRow(
                title = "Ocultar mensaje en pantalla apagada",
                subtitle = "Muestra quién escribe o la app, pero oculta el texto del mensaje",
                checked = hideSensitive,
                onCheckedChange = onHideSensitiveChange
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Duración del resumen antes de apagar pantalla: ${durationSeconds}s",
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 8, 12).forEach { sec ->
                    FilterChip(
                        selected = durationSeconds == sec,
                        onClick = { onDurationChange(sec) },
                        label = { Text("${sec}s", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.25f),
                            selectedLabelColor = Color(0xFF38BDF8)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF38BDF8)
            )
        )
    }
}

@Composable
private fun AppItemRow(
    app: InstalledAppItem,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Color(0xFF172236) else Color(0xFF131926))
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.45f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onToggle(!isSelected) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.iconBitmap != null) {
            Image(
                bitmap = app.iconBitmap.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF263044)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = app.label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = isSelected,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF38BDF8)
            )
        )
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val cn = ComponentName(context, BriefNotificationListenerService::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(cn.flattenToString())
}

private fun openNotificationListenerSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
    }
}

private fun openOverlaySettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

private fun loadInstalledApps(context: Context): List<InstalledAppItem> {
    val pm = context.packageManager
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return packages
        .asSequence()
        .filter { it.packageName != context.packageName }
        .map { appInfo ->
            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                appInfo.packageName
            }
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
                !AppPreferences.COMMON_MESSAGING_PACKAGES.contains(appInfo.packageName)

            val icon = try {
                drawableToBitmap(pm.getApplicationIcon(appInfo), 84)
            } catch (_: Exception) {
                null
            }
            InstalledAppItem(
                packageName = appInfo.packageName,
                label = label,
                isSystemApp = isSystem,
                iconBitmap = icon
            )
        }
        .sortedWith(
            compareByDescending<InstalledAppItem> {
                AppPreferences.COMMON_MESSAGING_PACKAGES.contains(it.packageName)
            }.thenBy { it.label.lowercase() }
        )
        .toList()
}

