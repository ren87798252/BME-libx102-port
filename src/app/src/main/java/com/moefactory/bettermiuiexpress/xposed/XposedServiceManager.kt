package com.moefactory.bettermiuiexpress.xposed

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * Tracks the connection to the Xposed framework service.
 *
 * The legacy API exposed this through `YukiHookAPI.Status` together with
 * XSharedPreferences for reading module settings from the hooked process. The
 * modern API replaces both with a bound service: the framework calls the module
 * app's `XposedProvider` (merged from `io.github.libxposed:service`), which hands
 * over an [XposedService] used for framework info and remote preferences.
 */
object XposedServiceManager {

    private const val TAG = "XposedServiceManager"

    private val _service = MutableLiveData<XposedService?>(null)

    /** Observable framework service, null while the module is not active. */
    val service: LiveData<XposedService?> = _service

    /** Last received service instance, for non-UI callers. */
    @Volatile
    var current: XposedService? = null
        private set

    private var initialized = false

    /**
     * Registers the service listener. Must be called once, as early as possible,
     * so binders arriving before the UI is created are still captured.
     */
    @Synchronized
    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (initialized) return
        initialized = true
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    current = service
                    if (isMainThread()) _service.value = service else _service.postValue(service)
                }

                override fun onServiceDied(service: XposedService) {
                    if (current === service) current = null
                    if (_service.value === service) {
                        if (isMainThread()) _service.value = null else _service.postValue(null)
                    }
                }
            })
        }.onFailure { Log.e(TAG, "failed to register Xposed service listener", it) }
    }

    private fun isMainThread() =
        android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
}
