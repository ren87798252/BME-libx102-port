package com.moefactory.bettermiuiexpress.base.app

import android.app.Application
import com.moefactory.bettermiuiexpress.ktx.hideLauncherIcon
import com.moefactory.bettermiuiexpress.ktx.isLauncherIconEnabled
import com.moefactory.bettermiuiexpress.xposed.XposedServiceManager

class BetterMiuiExpress : Application() {

    override fun onCreate() {
        super.onCreate()

        ModulePreferences.init(this)

        // Connect to the Xposed framework as early as possible, and republish the
        // track id once it is available.
        XposedServiceManager.init(this)
        XposedServiceManager.service.observeForever { service ->
            if (service != null) RemoteConfig.syncDeviceTrackId()
        }

        if (isLauncherIconEnabled() && ModulePreferences.getTrackId().isNotEmpty()) {
            hideLauncherIcon()
        }
    }
}
