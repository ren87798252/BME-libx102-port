package com.moefactory.bettermiuiexpress.hook

import android.content.SharedPreferences
import android.util.Log
import com.moefactory.bettermiuiexpress.BuildConfig
import com.moefactory.bettermiuiexpress.base.app.PA_PACKAGE_NAME
import com.moefactory.bettermiuiexpress.hook.subhooks.PAExpressRepositoryHook
import com.moefactory.bettermiuiexpress.hook.subhooks.PAExpressRouterHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Module entry class for the modern (libxposed) Xposed API, targeting API 102.
 *
 * This class is registered in `META-INF/xposed/java_init.list` and instantiated
 * by the framework. The framework attaches the live [XposedInterface] before any
 * lifecycle callback is dispatched, so the members of that interface (hook(),
 * getRemotePreferences(), log()) can be used from the callbacks below.
 *
 * It replaces the previous `IYukiHookXposedInit` entry point: `onInit()` and
 * `onHook()` map onto [onModuleLoaded] and [onPackageReady] respectively.
 */
class HookEntry : XposedModule() {

    private companion object {
        const val TAG = "BetterMiuiExpress"

        /**
         * Group name of the remote preferences shared with the module app.
         * The app writes the device track id there, the hooked process reads it.
         */
        const val REMOTE_PREFS_GROUP = "better_miui_express"

        /** True for the main process of the target package and its sub processes. */
        fun isTargetProcess(processName: String) =
            processName == PA_PACKAGE_NAME || processName.startsWith("$PA_PACKAGE_NAME:")
    }

    private val routerHook by lazy { PAExpressRouterHook(this) }
    private val repositoryHook by lazy { PAExpressRepositoryHook(this) }

    /** Shared preferences published by the module app, or null if unsupported. */
    @Volatile
    private var remotePrefs: SharedPreferences? = null

    /**
     * Called once per module generation as soon as the module is loaded into a
     * process. The target package's classloader is not ready yet, so hooks are
     * installed in [onPackageReady] instead.
     *
     * The process name is definitive here, which makes it a safe place to opt out
     * of processes we are not interested in: [detach] stops all further lifecycle
     * callbacks for this entry only, so we do not keep the module classloader
     * alive in unrelated processes.
     */
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        if (param.isSystemServer || !isTargetProcess(param.processName)) {
            info("not a target process (${param.processName}), detaching")
            detach()
            return
        }
        info("module loaded into ${param.processName}")
    }

    /**
     * Called once the classloader of a loaded package is ready. This replaces the
     * old `loadApp(name = PA_PACKAGE_NAME, ...)` call.
     */
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        // A process can load more than one package (sharedUserId, createPackageContext).
        // Only the first load of the target package is interesting, hooking it more
        // than once would stack duplicate hooks.
        if (param.packageName != PA_PACKAGE_NAME || !param.isFirstPackage) return

        val classLoader = param.classLoader

        // Remote preferences replace XSharedPreferences from the legacy API. They
        // are only provided by frameworks advertising PROP_CAP_REMOTE, so absence
        // is handled gracefully rather than treated as fatal.
        remotePrefs = if (frameworkProperties and XposedInterface.PROP_CAP_REMOTE != 0L) {
            runCatching { getRemotePreferences(REMOTE_PREFS_GROUP) }
                .onFailure { error("failed to obtain remote preferences", it) }
                .getOrNull()
        } else {
            warn("framework does not support remote preferences, falling back to empty track id")
            null
        }

        runCatching {
            routerHook.install(classLoader)
            repositoryHook.install(classLoader, remotePrefs)
        }.onFailure { error("failed to install hooks", it) }
    }

    /**
     * Logs through the framework log, in the same spirit as the debug logging the
     * YukiHookAPI based version enabled in debug builds.
     */
    internal fun info(message: String) {
        if (isVerbose) log(Log.DEBUG, TAG, message)
    }

    internal fun warn(message: String) {
        log(Log.WARN, TAG, message)
    }

    internal fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) log(Log.ERROR, TAG, message, throwable)
        else log(Log.ERROR, TAG, message)
    }

    internal val isVerbose: Boolean get() = BuildConfig.DEBUG
}
