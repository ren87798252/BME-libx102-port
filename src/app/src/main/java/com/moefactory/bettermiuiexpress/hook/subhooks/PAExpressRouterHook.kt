package com.moefactory.bettermiuiexpress.hook.subhooks

import android.content.Context
import com.moefactory.bettermiuiexpress.activity.ExpressDetailsActivity
import com.moefactory.bettermiuiexpress.base.app.PA_EXPRESS_ENTRY
import com.moefactory.bettermiuiexpress.base.app.PA_EXPRESS_ROUTER
import com.moefactory.bettermiuiexpress.hook.HookEntry
import com.moefactory.bettermiuiexpress.hook.findMethodByShapeOrNull
import com.moefactory.bettermiuiexpress.hook.findMethodOrNull
import com.moefactory.bettermiuiexpress.hook.loadClassOrNull
import com.moefactory.bettermiuiexpress.model.ExpressEntryWrapper
import com.moefactory.bettermiuiexpress.model.MiuiExpress
import com.moefactory.bettermiuiexpress.model.isJingDong
import com.moefactory.bettermiuiexpress.model.shouldUseNativeUI
import com.moefactory.bettermiuiexpress.model.toExpressEntryWrapper
import com.moefactory.bettermiuiexpress.model.toExpressInfoJumpListWrapper
import io.github.libxposed.api.XposedInterface

/**
 * Hooks `ExpressRouter#route`, see [PA_EXPRESS_ROUTER].
 *
 * From PA 5.5.55:
 * `public static void route(Context context, Object obj, ExpressEntry expressEntry)`
 *
 * Converted from the YukiHookAPI `replaceAny` style to the API 102 interceptor
 * chain: returning a value from `intercept` replaces the method result (null for
 * void methods), while `chain.proceed()` falls through to the original method.
 */
class PAExpressRouterHook(private val module: HookEntry) {

    fun install(classLoader: ClassLoader) {
        val routerClass = loadClassOrNull(PA_EXPRESS_ROUTER, classLoader)
        if (routerClass == null) {
            module.warn("ExpressRouter not found, skipping router hook")
            return
        }
        val expressEntryClass = loadClassOrNull(PA_EXPRESS_ENTRY, classLoader)
        if (expressEntryClass == null) {
            module.warn("ExpressEntry not found, skipping router hook")
            return
        }

        val routeMethod = routerClass.findMethodOrNull(
            "route", Context::class.java, Any::class.java, expressEntryClass
        )
        if (routeMethod == null) {
            module.warn("ExpressRouter#route not found, skipping router hook")
            return
        }

        // The `gotoNative` fallback only exists on newer PA versions. It is invoked
        // with plain reflection (as the rest of this module does) rather than with a
        // framework invoker: the method is public on a normal app class, so no
        // hidden-API bypass is required.
        val gotoNativeMethod = routerClass.findMethodByShapeOrNull(
            name = "gotoNative",
            arity = 3,
            assignableFrom = expressEntryClass,
            atIndex = 2
        )?.apply { isAccessible = true }

        module.hook(routeMethod).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
            val context = chain.getArg(0) as? Context
            val arg1 = chain.getArg(1)
            val expressEntry = chain.getArg(2)
            if (context == null || expressEntry == null) {
                module.info("unexpected route() arguments, falling through")
                return@intercept chain.proceed()
            }

            val expressEntryWrapper = expressEntry.toExpressEntryWrapper()

            if (jumpToDetailsActivity(context, expressEntryWrapper)) {
                // Details are shown by our own activity, swallow the original call.
                return@intercept null
            }

            if (expressEntryWrapper.shouldUseNativeUI() && expressEntryWrapper.isJingDong && arg1 != null) {
                // From new versions of PA, details of packages from JingDong will be
                // display in JD app by default, which is unexpected. Here we just
                // intercept it and route through the native UI instead.
                if (gotoNativeMethod != null) {
                    runCatching { gotoNativeMethod.invoke(null, context, arg1, expressEntry) }
                        .onFailure { module.error("gotoNative invocation failed", it) }
                    return@intercept null
                }
                module.warn("gotoNative not found, JingDong route not intercepted")
            }

            // Other details will be processed normally
            chain.proceed()
        }

        module.info("ExpressRouter#route hooked")
    }

    private fun jumpToDetailsActivity(
        context: Context, expressEntryWrapper: ExpressEntryWrapper
    ): Boolean {
        val companyCode = expressEntryWrapper.companyCode
        val companyName = expressEntryWrapper.companyName
        val mailNumber = expressEntryWrapper.orderNumber
        val phoneNumber = expressEntryWrapper.phone
        // Check if the details will be showed in third-party apps(taobao, cainiao, etc.)
        val jumpList = expressEntryWrapper.jumpList?.mapNotNull { it?.toExpressInfoJumpListWrapper() }
        val uris = expressEntryWrapper.uris?.mapNotNull { it?.toExpressInfoJumpListWrapper() } // For older versions, `uris` is used
        if (!expressEntryWrapper.shouldUseNativeUI()) {
            val summary = MiuiExpress(companyCode, companyName, mailNumber, phoneNumber)
            when {
                !jumpList.isNullOrEmpty() ->
                    ExpressDetailsActivity.gotoDetailsActivity(context, summary, ArrayList(jumpList))
                !uris.isNullOrEmpty() ->
                    ExpressDetailsActivity.gotoDetailsActivity(context, summary, ArrayList(uris))
                else ->
                    ExpressDetailsActivity.gotoDetailsActivity(context, summary, null)
            }
            return true
        }
        return false
    }
}
