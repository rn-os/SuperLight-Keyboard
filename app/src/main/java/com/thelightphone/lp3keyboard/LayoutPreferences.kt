package com.thelightphone.lp3keyboard

import android.content.Context
import com.thelightphone.lp3Keyboard.ui.layout.LayoutRegistryItem

/**
 * Persistent storage for the keyboard app
 * Right now, values in here only affect the android system keyboard, NOT those embedded in
 * LightOS/community tools.
 */
object LayoutPreferences {
    private const val PREFS_NAME = "lp3_keyboard_prefs"
    const val KEY_ACTIVE_LAYOUT = "active_layout_id"
    const val KEY_VOICE_ENABLED = "voice_enabled"
    const val KEY_AUTOCORRECT_ENABLED = "autocorrect_enabled"
    const val KEY_AUTO_CAPITALIZE_ENABLED = "auto_capitalize_enabled"
    const val KEY_AUTO_PERIOD_ENABLED = "auto_period_enabled"
    const val KEY_CLIPBOARD_ENABLED = "clipboard_enabled"

    private val DEFAULT_LAYOUT = LayoutRegistryItem.EnQwerty
    private const val DEFAULT_VOICE_ENABLED = false
    private const val DEFAULT_AUTOCORRECT_ENABLED = true
    private const val DEFAULT_AUTO_CAPITALIZE_ENABLED = true
    private const val DEFAULT_AUTO_PERIOD_ENABLED = true
    private const val DEFAULT_CLIPBOARD_ENABLED = true

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActiveLayout(context: Context): LayoutRegistryItem {
        val id = prefs(context).getString(KEY_ACTIVE_LAYOUT, null)
        return LayoutRegistryItem.entries.firstOrNull { it.uniqueId == id } ?: DEFAULT_LAYOUT
    }

    fun setActiveLayout(context: Context, item: LayoutRegistryItem) {
        prefs(context).edit().putString(KEY_ACTIVE_LAYOUT, item.uniqueId).apply()
    }

    fun isVoiceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOICE_ENABLED, DEFAULT_VOICE_ENABLED)

    fun setVoiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
    }

    fun isAutocorrectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOCORRECT_ENABLED, DEFAULT_AUTOCORRECT_ENABLED)

    fun setAutocorrectEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOCORRECT_ENABLED, enabled).apply()
    }

    fun isAutoCapitalizeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CAPITALIZE_ENABLED, DEFAULT_AUTO_CAPITALIZE_ENABLED)

    fun setAutoCapitalizeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CAPITALIZE_ENABLED, enabled).apply()
    }

    fun isAutoPeriodEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_PERIOD_ENABLED, DEFAULT_AUTO_PERIOD_ENABLED)

    fun setAutoPeriodEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_PERIOD_ENABLED, enabled).apply()
    }

    fun isClipboardEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLIPBOARD_ENABLED, DEFAULT_CLIPBOARD_ENABLED)

    fun setClipboardEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLIPBOARD_ENABLED, enabled).apply()
    }

    fun registerOnChange(
        context: Context,
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener,
    ): android.content.SharedPreferences =
        prefs(context).also { it.registerOnSharedPreferenceChangeListener(listener) }
}
