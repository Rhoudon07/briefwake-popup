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
    private const val KEY_WHATSAPP_COMPACT_PRIVATE = "whatsapp_compact_private"
    private const val KEY_POPUP_ANIMATION = "popup_animation"
    private const val KEY_INITIALIZED_DEFAULTS = "initialized_defaults"

    const val ANIMATION_SLIDE = "slide"
    const val ANIMATION_SCALE = "scale"
    const val ANIMATION_FADE = "fade"
    const val ANIMATION_EXPAND = "expand"

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

    // Caché en memoria RAM para evitar lecturas continuas a disco (SharedPreferences)
    // en cada notificación entrante del sistema
    @Volatile private var cachedMasterEnabled: Boolean? = null
    @Volatile private var cachedEnabledPackages: Set<String>? = null
    @Volatile private var cachedOnlyScreenOff: Boolean? = null
    @Volatile private var cachedDurationSeconds: Int? = null
    @Volatile private var cachedShowEdgeGlow: Boolean? = null
    @Volatile private var cachedHideSensitive: Boolean? = null
    @Volatile private var cachedWhatsappCompactPrivate: Boolean? = null
    @Volatile private var cachedPopupAnimation: String? = null

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMasterEnabled(context: Context): Boolean {
        val cached = cachedMasterEnabled
        if (cached != null) return cached
        val value = prefs(context).getBoolean(KEY_MASTER_ENABLED, true)
        cachedMasterEnabled = value
        return value
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        cachedMasterEnabled = enabled
        prefs(context).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun getEnabledPackages(context: Context): Set<String> {
        val cached = cachedEnabledPackages
        if (cached != null) return cached
        val value = prefs(context).getStringSet(KEY_ENABLED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        cachedEnabledPackages = value
        return value
    }

    fun setEnabledPackages(context: Context, packages: Set<String>) {
        val copy = packages.toSet()
        cachedEnabledPackages = copy
        prefs(context).edit().putStringSet(KEY_ENABLED_PACKAGES, copy).apply()
    }

    fun togglePackage(context: Context, packageName: String, enabled: Boolean): Set<String> {
        val current = getEnabledPackages(context).toMutableSet()
        if (enabled) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        val immutableSet = current.toSet()
        setEnabledPackages(context, immutableSet)
        return immutableSet
    }

    fun isPackageEnabled(context: Context, packageName: String): Boolean =
        getEnabledPackages(context).contains(packageName)

    fun isOnlyWhenScreenOff(context: Context): Boolean {
        val cached = cachedOnlyScreenOff
        if (cached != null) return cached
        val value = prefs(context).getBoolean(KEY_ONLY_SCREEN_OFF, true)
        cachedOnlyScreenOff = value
        return value
    }

    fun setOnlyWhenScreenOff(context: Context, onlyOff: Boolean) {
        cachedOnlyScreenOff = onlyOff
        prefs(context).edit().putBoolean(KEY_ONLY_SCREEN_OFF, onlyOff).apply()
    }

    fun getDurationSeconds(context: Context): Int {
        val cached = cachedDurationSeconds
        if (cached != null) return cached
        val value = prefs(context).getInt(KEY_DURATION_SECONDS, 5).coerceIn(2, 20)
        cachedDurationSeconds = value
        return value
    }

    fun setDurationSeconds(context: Context, seconds: Int) {
        val safeSec = seconds.coerceIn(2, 20)
        cachedDurationSeconds = safeSec
        prefs(context).edit().putInt(KEY_DURATION_SECONDS, safeSec).apply()
    }

    fun isShowEdgeGlow(context: Context): Boolean {
        val cached = cachedShowEdgeGlow
        if (cached != null) return cached
        val value = prefs(context).getBoolean(KEY_SHOW_EDGE_GLOW, true)
        cachedShowEdgeGlow = value
        return value
    }

    fun setShowEdgeGlow(context: Context, show: Boolean) {
        cachedShowEdgeGlow = show
        prefs(context).edit().putBoolean(KEY_SHOW_EDGE_GLOW, show).apply()
    }

    fun isHideSensitive(context: Context): Boolean {
        val cached = cachedHideSensitive
        if (cached != null) return cached
        val value = prefs(context).getBoolean(KEY_HIDE_SENSITIVE, false)
        cachedHideSensitive = value
        return value
    }

    fun setHideSensitive(context: Context, hide: Boolean) {
        cachedHideSensitive = hide
        prefs(context).edit().putBoolean(KEY_HIDE_SENSITIVE, hide).apply()
    }

    fun isWhatsappCompactPrivate(context: Context): Boolean {
        val cached = cachedWhatsappCompactPrivate
        if (cached != null) return cached
        val value = prefs(context).getBoolean(KEY_WHATSAPP_COMPACT_PRIVATE, false)
        cachedWhatsappCompactPrivate = value
        return value
    }

    fun setWhatsappCompactPrivate(context: Context, enabled: Boolean) {
        cachedWhatsappCompactPrivate = enabled
        prefs(context).edit().putBoolean(KEY_WHATSAPP_COMPACT_PRIVATE, enabled).apply()
    }

    fun getPopupAnimation(context: Context): String {
        val cached = cachedPopupAnimation
        if (cached != null) return cached
        val value = prefs(context).getString(KEY_POPUP_ANIMATION, ANIMATION_SLIDE) ?: ANIMATION_SLIDE
        cachedPopupAnimation = value
        return value
    }

    fun setPopupAnimation(context: Context, animation: String) {
        cachedPopupAnimation = animation
        prefs(context).edit().putString(KEY_POPUP_ANIMATION, animation).apply()
    }

    fun initializeDefaultAppsIfNeeded(context: Context, installedPackages: Set<String>): Set<String> {
        val p = prefs(context)
        if (!p.getBoolean(KEY_INITIALIZED_DEFAULTS, false)) {
            val defaults = installedPackages.intersect(COMMON_MESSAGING_PACKAGES)
            cachedEnabledPackages = defaults
            p.edit()
                .putStringSet(KEY_ENABLED_PACKAGES, defaults)
                .putBoolean(KEY_INITIALIZED_DEFAULTS, true)
                .apply()
            return defaults
        }
        return getEnabledPackages(context)
    }
}

