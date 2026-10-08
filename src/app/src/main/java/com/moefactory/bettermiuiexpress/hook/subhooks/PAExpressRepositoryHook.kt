package com.moefactory.bettermiuiexpress.hook.subhooks

import android.content.SharedPreferences
import com.moefactory.bettermiuiexpress.base.app.PA_EXPRESS_INFO_DETAIL
import com.moefactory.bettermiuiexpress.base.app.PA_EXPRESS_REPOSITOIRY
import com.moefactory.bettermiuiexpress.base.app.PREF_KEY_DEVICE_TRACK_ID
import com.moefactory.bettermiuiexpress.hook.HookEntry
import com.moefactory.bettermiuiexpress.hook.findMethodOrNull
import com.moefactory.bettermiuiexpress.hook.loadClassOrNull
import com.moefactory.bettermiuiexpress.model.ExpressInfoWrapper
import com.moefactory.bettermiuiexpress.model.ExpressTrace
import com.moefactory.bettermiuiexpress.model.isXiaomiOrJingDong
import com.moefactory.bettermiuiexpress.model.toExpressInfoDetailWrapper
import com.moefactory.bettermiuiexpress.model.toExpressInfoWrapper
import com.moefactory.bettermiuiexpress.model.toExpressTrace
import com.moefactory.bettermiuiexpress.repository.ExpressActualRepository
import com.moefactory.bettermiuiexpress.utils.ExpressCompanyUtils
import io.github.libxposed.api.XposedInterface
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hooks `ExpressRepository#saveExpress` to store the latest trace fetched from
 * the KuaiDi100 API, see [PA_EXPRESS_REPOSITOIRY].
 *
 * Converted from the YukiHookAPI `before {}` style: the API 102 equivalent of a
 * before hook is `intercept { chain -> ...; chain.proceed() }`.
 */
class PAExpressRepositoryHook(private val module: HookEntry) {

    fun install(classLoader: ClassLoader, prefs: SharedPreferences?) {
        val repositoryClass = loadClassOrNull(PA_EXPRESS_REPOSITOIRY, classLoader)
        if (repositoryClass == null) {
            module.warn("ExpressRepository not found, skipping repository hook")
            return
        }

        val saveExpressMethod = repositoryClass.findMethodOrNull(
            "saveExpress", java.util.List::class.java
        )
        if (saveExpressMethod == null) {
            module.warn("ExpressRepository#saveExpress not found, skipping repository hook")
            return
        }

        val detailClass = loadClassOrNull(PA_EXPRESS_INFO_DETAIL, classLoader)

        module.hook(saveExpressMethod).setExceptionMode(
            XposedInterface.ExceptionMode.PROTECTIVE
        ).intercept { chain ->
            runCatching {
                runBlocking {
                    // 这里的网络请求运行在被 hook 的调用线程上（runBlocking 阻塞
                    // 它直到完成）。加上限防止智能助理侧长时间等待：单号最多
                    // 15 秒，超时后放弃本次补全，原方法照常执行。
                    withTimeout(15_000L) {
                        val expressInfoList = (chain.getArg(0) as? List<*>)
                            // 元素类型是 Any?（List<*> 的星投影），而 toExpressInfoWrapper()
                            // 定义在非空 Any 上，所以这里必须先滤掉 null 再包装。
                            ?.mapNotNull { it?.toExpressInfoWrapper() }
                            ?.filter { !it.isXiaomiOrJingDong } // Skip packages from Xiaomi and JingDong
                            ?: return@withTimeout
                        for (expressInfoWrapper in expressInfoList) {
                            val companyCode = expressInfoWrapper.companyCode
                            val mailNumber = expressInfoWrapper.orderNumber
                            val phoneNumber = expressInfoWrapper.phone ?: expressInfoWrapper.sendPhone

                            val detailList = fetchExpressDetails(
                                mailNumber, companyCode, phoneNumber, prefs
                            )

                            // Ignore invalid result
                            if (detailList.isNullOrEmpty()) continue

                            // Save latest trace
                            if (detailClass != null) {
                                saveLatestExpressTrace(expressInfoWrapper, detailClass, detailList)
                            }
                        }
                    }
                }
            }.onFailure { module.error("saveExpress hook failed", it) }

            chain.proceed()
        }

        module.info("ExpressRepository#saveExpress hooked")
    }

    private fun saveLatestExpressTrace(
        expressInfoWrapper: ExpressInfoWrapper,
        detailClass: Class<*>,
        detailList: List<ExpressTrace>
    ) {
        // Prevent detail from disappearing
        expressInfoWrapper.clickDisappear = false
        val originalDetails = expressInfoWrapper.details
        when {
            originalDetails == null -> {
                // Null list, create a new instance and put the latest detail
                val newDetail = detailClass.getDeclaredConstructor().newInstance()
                val newDetailWrapper = newDetail.toExpressInfoDetailWrapper()
                newDetailWrapper.desc = detailList[0].description
                newDetailWrapper.time = detailList[0].fullDateTime
                val newDetails = ArrayList<Any>(1)
                newDetails.add(newDetail)
                expressInfoWrapper.details = newDetails
            }
            originalDetails.isEmpty() -> {
                // Empty list, put the latest detail
                val newDetail = detailClass.getDeclaredConstructor().newInstance()
                val newDetailWrapper = newDetail.toExpressInfoDetailWrapper()
                newDetailWrapper.desc = detailList[0].description
                newDetailWrapper.time = detailList[0].fullDateTime
                originalDetails.add(newDetail)
            }
            else -> {
                // Normally, the original details contains one item
                expressInfoWrapper.details?.getOrNull(0)
                    ?.toExpressInfoDetailWrapper()
                    ?.desc = detailList[0].description
            }
        }
    }

    private suspend fun fetchExpressDetails(
        mailNumber: String,
        originalCompanyCode: String,
        phoneNumber: String?,
        prefs: SharedPreferences?
    ): List<ExpressTrace>? {
        val deviceTrackId = prefs?.getString(PREF_KEY_DEVICE_TRACK_ID, "") ?: ""

        val convertedCompanyCode = ExpressCompanyUtils.convertCode(originalCompanyCode)
            ?: ExpressActualRepository.queryCompanyActual(mailNumber).firstOrNull()?.companyCode
                ?: return null

        if (deviceTrackId.isEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            val currentDateTimeString = sdf.format(Date())

            return listOf(
                ExpressTrace(
                    fullDateTime = currentDateTimeString,
                    date = currentDateTimeString.split(" ")[0],
                    time = currentDateTimeString.split(" ")[1],
                    description = "请先打开模块主界面完成初始化"
                )
            )
        }

        val response = ExpressActualRepository.queryExpressDetailsFromKuaiDi100Actual(
            convertedCompanyCode, mailNumber, phoneNumber, deviceTrackId
        )
        return response?.lastResult?.data?.map { it.toExpressTrace() }?.sortedDescending()
    }
}
