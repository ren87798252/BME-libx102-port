package com.moefactory.bettermiuiexpress.base.app

import android.content.Context
import android.content.SharedPreferences
import com.moefactory.bettermiuiexpress.xposed.XposedServiceManager

/**
 * Local settings of the module app.
 *
 * Replaces `prefs()` from YukiHookAPI, which was backed by XSharedPreferences so
 * that the hooked process could read the same file. Under the modern API the
 * hooked process reads remote preferences instead, so writes have to be mirrored
 * to the framework - see [RemoteConfig].
 */
object ModulePreferences {

    private const val KEY_DEVICE_TRACK_ID = PREF_KEY_DEVICE_TRACK_ID

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val prefs: SharedPreferences?
        get() = appContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getString(key: String, defValue: String = ""): String =
        prefs?.getString(key, defValue) ?: defValue

    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    fun getTrackId(): String = getString(KEY_DEVICE_TRACK_ID)

    fun setTrackId(trackId: String) = putString(KEY_DEVICE_TRACK_ID, trackId)
}

/**
 * Bridges the module app settings to the hooked process.
 *
 * The device track id is written to local preferences *and* to the framework's
 * remote preferences, which is the only channel the hooked process can read
 * under the modern API. The group name must match the one used by the module
 * entry point (`HookEntry.REMOTE_PREFS_GROUP`).
 */
object RemoteConfig {

    /** Must match HookEntry.REMOTE_PREFS_GROUP. */
    private const val GROUP = "better_miui_express"

    /** Must match PREF_KEY_DEVICE_TRACK_ID. */
    private const val KEY_DEVICE_TRACK_ID = PREF_KEY_DEVICE_TRACK_ID

    /**
     * Persists the track id locally and publishes it to the framework when the
     * service is connected. Failures are non-fatal: the value stays available for
     * the next attempt after the service binds.
     */
    fun setDeviceTrackId(trackId: String) {
        ModulePreferences.setTrackId(trackId)
        val service = XposedServiceManager.current ?: return
        runCatching {
            service.getRemotePreferences(GROUP)
                .edit()
                .putString(KEY_DEVICE_TRACK_ID, trackId)
                .apply()
        }
    }

    /** Re-publishes the stored track id, used once the service becomes available. */
    fun syncDeviceTrackId() {
        val trackId = ModulePreferences.getTrackId()
        if (trackId.isNotEmpty()) setDeviceTrackId(trackId)
    }
}
