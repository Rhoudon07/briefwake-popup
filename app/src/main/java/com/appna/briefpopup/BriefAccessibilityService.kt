package com.appna.briefpopup

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class BriefAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No se requiere inspeccionar eventos de UI
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        private var instance: BriefAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        /**
         * Apaga la pantalla simulando el botón físico de encendido.
         * En Android 9 (API 28) o superior, GLOBAL_ACTION_LOCK_SCREEN NO bloquea la biometría,
         * permitiendo desbloquear inmediatamente con huella dactilar o reconocimiento facial.
         */
        fun lockScreen(): Boolean {
            val s = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                false
            }
        }
    }
}
