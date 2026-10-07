package com.moefactory.bettermiuiexpress.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.moefactory.bettermiuiexpress.R
import com.moefactory.bettermiuiexpress.base.app.ModulePreferences
import com.moefactory.bettermiuiexpress.base.app.RemoteConfig
import com.moefactory.bettermiuiexpress.base.ui.BaseActivity
import com.moefactory.bettermiuiexpress.databinding.ActivityMainBinding
import com.moefactory.bettermiuiexpress.ktx.hideLauncherIcon
import com.moefactory.bettermiuiexpress.ktx.isLauncherIconEnabled
import com.moefactory.bettermiuiexpress.repository.ExpressActualRepository
import com.moefactory.bettermiuiexpress.xposed.XposedServiceManager
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@SuppressLint("WorldReadableFiles")
class MainActivity : BaseActivity<ActivityMainBinding>(false) {

    override val viewBinding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(viewBinding.mtToolbar)

        viewBinding.btnGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).setData("https://github.com/Robotxm/BetterMiuiExpress".toUri()))
        }
        viewBinding.btnBlog.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).setData("https://moefactory.com".toUri()))
        }
        viewBinding.mcvYuki.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).setData("https://github.com/libxposed/api".toUri()))
        }

        viewBinding.tvYukiVersion.text =
            getString(R.string.yuki_version, XposedService.API_102.toString())

        // The framework connection is reported by the bound Xposed service rather
        // than by YukiHookAPI.Status. Observe it so the UI also updates if the
        // service connects after this activity was created.
        XposedServiceManager.service.observe(this) { service ->
            renderStatus(service != null)
            if (service != null) {
                viewBinding.tvStatusDescription.text = getString(
                    R.string.active_hook_framework_version,
                    service.frameworkName,
                    service.apiVersion
                )
                initTrackIdIfNeeded()
            }
        }
    }

    private fun renderStatus(isActive: Boolean) {
        if (isActive) {
            viewBinding.tvStatus.setText(R.string.active)
            viewBinding.ivStatus.setImageResource(R.drawable.ic_active)
        } else {
            viewBinding.tvStatus.setText(R.string.inactive)
            viewBinding.tvStatusDescription.setText(R.string.inactive_description)
            viewBinding.ivStatus.setImageResource(R.drawable.ic_inactive)
        }
    }

    /**
     * Registers a device track id on first activation and publishes it so the
     * hooked process can read it through remote preferences.
     */
    private fun initTrackIdIfNeeded() {
        if (ModulePreferences.getTrackId().isNotEmpty()) return
        if (trackIdBeingGenerated) return
        trackIdBeingGenerated = true

        lifecycleScope.launch(Dispatchers.IO) {
            val generatedTrackId = UUID.randomUUID().toString()
            if (ExpressActualRepository.registerDeviceTrackIdActual(generatedTrackId)) {
                // Stores locally and mirrors to the framework remote preferences.
                RemoteConfig.setDeviceTrackId(generatedTrackId)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.init_success_and_hide, Toast.LENGTH_SHORT).show()
                }

                delay(5000)

                if (isLauncherIconEnabled()) {
                    withContext(Dispatchers.Main) { hideLauncherIcon() }
                }
            } else {
                withContext(Dispatchers.Main) { trackIdBeingGenerated = false }
            }
        }
    }

    private var trackIdBeingGenerated = false
}
