# BriefWake Popup 📱✨

App nativa de Android desarrollada en **Kotlin** y **Jetpack Compose** diseñada para mostrar notificaciones emergentes de estilo **Resumen (Brief Popup / Edge Lighting)** con la **pantalla apagada**, permitiéndote elegir individualmente qué aplicaciones activan esta función (por ejemplo: WhatsApp, Telegram, Instagram, Gmail, etc.).

---

## ✨ Características

- 🖤 **Fondo Negro Puro AMOLED**: En pantallas OLED/AMOLED los píxeles negros permanecen físicamente apagados, minimizando el consumo de batería.
- 💊 **Pastilla emergente animada (Estilo Resumen / One UI Brief)**:
  - Icono dinámico de la app con forma redondeada.
  - Título / remitente y resumen del mensaje compacto.
  - Color de acento según la aplicación.
  - Halo luminoso superior (*Edge Lighting*) animado.
  - Barra de tiempo restante animada antes de apagar la pantalla.
  - Toca la pastilla para desbloquear y abrir la conversación directa.
- ⚙️ **Personalización y Control Total**:
  - Selector individual de aplicaciones instaladas (con búsqueda, filtros y botón rápido para apps de mensajería).
  - Duración configurable de la pastilla antes de volver a apagar la pantalla (3s, 5s, 8s, 12s).
  - Opción de ocultar el texto del mensaje en pantalla apagada por privacidad.
  - Opción de activar/desactivar halo luminoso.
  - Opción de funcionar exclusivamente cuando la pantalla esté apagada (`PowerManager.isInteractive == false`).
  - Botón de **"Probar en 4s"** en la app para bloquear la pantalla y validar el funcionamiento de inmediato.

---

## 🛠️ Requisitos e Instalación

### Requisitos del sistema
- Android 8.0 (API 26) o superior (probado con Android 14 / 15 / One UI 6 & 7).
- Permisos requeridos (se pueden conceder desde la app o por ADB):
  1. **Acceso a notificaciones** (`NotificationListenerService`).
  2. **Mostrar sobre otras apps** (`SYSTEM_ALERT_WINDOW`).
  3. **Batería sin restricciones** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).

### Instalación Rápida con ADB (Samsung Galaxy)
Conecta tu dispositivo con **Depuración USB** activada y ejecuta en PowerShell:
```powershell
.\samsung_brief_popup_adb.ps1
```
Este script:
1. Instala el APK compilado (`app-debug.apk`).
2. Concede los permisos de listener de notificaciones y superposición.
3. Excluye la app del ahorro agresivo de batería de One UI.
4. Ajusta los parámetros del sistema Samsung para habilitar el estilo resumen con pantalla apagada.
5. Inicia la app.

---

## 📂 Estructura del Proyecto

```
APP-NA/
├── app/
│   ├── src/main/
│   │   ├── java/com/appna/briefpopup/
│   │   │   ├── MainActivity.kt                      # UI principal en Jetpack Compose
│   │   │   ├── BriefPopupActivity.kt                # Pantalla emergente AMOLED sobre Lockscreen
│   │   │   ├── BriefNotificationListenerService.kt  # Interceptor y despachador de notificaciones
│   │   │   └── AppPreferences.kt                    # Gestión de preferencias y apps activas
│   │   ├── res/                                     # Recursos gráficos, temas y cadenas
│   │   └── AndroidManifest.xml                      # Permisos y actividades sobre bloqueo
│   └── build.gradle.kts
├── samsung_brief_popup_adb.ps1                      # Automatización ADB
└── build.gradle.kts
```

