package com.appna.briefpopup

import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences

object RecentNotificationActionStore {
    @Volatile
    var lastContentIntent: PendingIntent? = null

    @Volatile
    var lastPackageName: String? = null
}

object AppPreferences {
    private const val PREFS_NAME = "brief_wake_prefs"
    private const val KEY_MASTER_ENABLED = "master_enabled"
    private const val KEY_ENABLED_PACKAGES = "enabled_packages"
    private const val KEY_ONLY_SCREEN_OFF = "only_screen_off"
    private const val KEY_DURATION_SECONDS = "duration_seconds"
    private const val KEY_SHOW_EDGE_GLOW = "show_edge_glow"
    private const val KEY_HIDE_SENSITIVE = "hide_sensitive"
    private const val KEY_INITIALIZED_DEFAULTS = "initialized_defaults"

    val COMMON_MESSAGING_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.instagram.android",
        "com.facebook.orca",
        "com.google.android.gm",
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.discord",
        "com.twitter.android",
        "com.microsoft.teams",
        "com.Slack"
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMasterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASTER_ENABLED, true)

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun getEnabledPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ENABLED_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun setEnabledPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_ENABLED_PACKAGES, packages.toSet()).apply()
    }

    fun togglePackage(context: Context, packageName: String, enabled: Boolean): Set<String> {
        val current = getEnabledPackages(context).toMutableSet()
        if (enabled) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        setEnabledPackages(context, current)
        return current
    }

    fun isPackageEnabled(context: Context, packageName: String): Boolean =
        getEnabledPackages(context).contains(packageName)

    fun isOnlyWhenScreenOff(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONLY_SCREEN_OFF, true)

    fun setOnlyWhenScreenOff(context: Context, onlyOff: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONLY_SCREEN_OFF, onlyOff).apply()
    }

    fun getDurationSeconds(context: Context): Int =
        prefs(context).getInt(KEY_DURATION_SECONDS, 5).coerceIn(2, 20)

    fun setDurationSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_DURATION_SECONDS, seconds.coerceIn(2, 20)).apply()
    }

    fun isShowEdgeGlow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_EDGE_GLOW, true)

    fun setShowEdgeGlow(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_EDGE_GLOW, show).apply()
    }

    fun isHideSensitive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_SENSITIVE, false)

    fun setHideSensitive(context: Context, hide: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_SENSITIVE, hide).apply()
    }

    fun initializeDefaultAppsIfNeeded(context: Context, installedPackages: Set<String>): Set<String> {
        val p = prefs(context)
        if (!p.getBoolean(KEY_INITIALIZED_DEFAULTS, false)) {
            val defaults = installedPackages.intersect(COMMON_MESSAGING_PACKAGES)
            p.edit()
                .putStringSet(KEY_ENABLED_PACKAGES, defaults)
                .putBoolean(KEY_INITIALIZED_DEFAULTS, true)
                .apply()
            return defaults
        }
        return getEnabledPackages(context)
    }
}

