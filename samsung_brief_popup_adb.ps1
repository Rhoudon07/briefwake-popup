# ==============================================================================
# Script de Instalación + Activación Automática por ADB (Samsung Galaxy / Android)
# ==============================================================================
$ErrorActionPreference = "Continue"

$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adbPath)) {
    $adbPath = "adb"
}

$apkPath = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "com.appna.briefpopup"
$listenerComponent = "$pkg/$pkg.BriefNotificationListenerService"
$accessibilityComponent = "$pkg/$pkg.BriefAccessibilityService"

Write-Host "1. Verificando conexión con el celular por ADB..." -ForegroundColor Cyan
& $adbPath devices

# Si estaba activo el antiguo DeviceAdmin (que provocaba el bloqueo de huella), lo removemos
Write-Host "`nRemoviendo Device Admin anterior si estuviera activo (para liberar biometría)..." -ForegroundColor Yellow
& $adbPath shell dpm remove-active-admin "$pkg/$pkg.BriefDeviceAdminReceiver" 2>$null

if (Test-Path $apkPath) {
    Write-Host "`n2. Instalando APK BriefWake Popup ($apkPath)..." -ForegroundColor Cyan
    & $adbPath install -r $apkPath
} else {
    Write-Host "`n[Aviso] Aún no se encontró $apkPath. Compila primero el proyecto." -ForegroundColor Yellow
}

Write-Host "`n3. Concediendo permisos críticos automáticamente por ADB..." -ForegroundColor Cyan
# Conceder acceso a notificaciones al NotificationListenerService
& $adbPath shell cmd notification allow_listener $listenerComponent

# Conceder permiso de superposición (Mostrar sobre otras apps / Abrir desde segundo plano)
& $adbPath shell appops set $pkg SYSTEM_ALERT_WINDOW allow

# Excluir de optimización de batería Doze / Deep Sleep de Samsung One UI
& $adbPath shell dumpsys deviceidle whitelist +$pkg

# Habilitar servicio de Accesibilidad (simula botón físico sin deshabilitar huella ni rostro)
& $adbPath shell settings put secure enabled_accessibility_services $accessibilityComponent
& $adbPath shell settings put secure accessibility_enabled 1

Write-Host "`n4. (Opcional Samsung One UI) Activando soporte nativo de Resumen con pantalla apagada..." -ForegroundColor Cyan
& $adbPath shell settings put system edge_lighting 1
& $adbPath shell settings put system edge_lighting_show_condition 0

Write-Host "`n5. Abriendo BriefWake Popup en el dispositivo..." -ForegroundColor Green
& $adbPath shell am start -n "$pkg/$pkg.MainActivity"

Write-Host "`n¡Listo! Ahora la pantalla se apaga al terminar el resumen y la huella/rostro funcionan con normalidad." -ForegroundColor Green
