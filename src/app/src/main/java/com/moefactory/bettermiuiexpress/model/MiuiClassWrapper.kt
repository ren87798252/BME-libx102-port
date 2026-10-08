package com.moefactory.bettermiuiexpress.model

import android.os.Parcelable
import com.moefactory.bettermiuiexpress.ktx.BooleanPrimitiveType
import com.moefactory.bettermiuiexpress.ktx.JavaStringClass
import kotlinx.parcelize.Parcelize
import java.lang.reflect.Field
import java.lang.reflect.Method

internal fun Any.toExpressInfoWrapper() = ExpressInfoWrapper(this)

fun Any.toExpressInfoJumpListWrapper(): ExpressInfoJumpListWrapper {
    val thirdPartyUriClass = this.javaClass

    val link = thirdPartyUriClass.getMethod("getLink").invoke(this) as? String
    val type = runCatching { thirdPartyUriClass.getMethod("getType").invoke(this) as? String }.getOrNull()
    val priority = runCatching { thirdPartyUriClass.getMethod("getPriority").invoke(this) as? Int }.getOrNull()

    return ExpressInfoJumpListWrapper(link, type, priority)
}

internal fun Any.toExpressEntryWrapper() = ExpressEntryWrapper(this)

internal fun Any.toExpressInfoDetailWrapper() = ExpressInfoDetailWrapper(this)

/**
 * 反射包装器的通用辅助。
 *
 * 目标类来自被 hook 的应用（智能助理），字段/方法可能随版本增减：
 *  - 查找结果按名字缓存（同一 wrapper 实例生命周期内只查一次，列表场景下
 *    一个单号几十次访问的反射查找开销才降得下来）；
 *  - 任何缺失/调用失败都返回 null 而不是抛异常。hook 侧虽有
 *    ExceptionMode.PROTECTIVE 兜底（不会崩目标应用），但抛异常意味着整个
 *    hook 流程中断——这里返回默认值能把损失限制在单个字段上。
 */
internal abstract class ReflectiveWrapper(protected val target: Any) {

    protected val targetClass: Class<*> = target.javaClass

    private val methods = HashMap<String, Method?>()
    private val fields = HashMap<String, Field?>()

    protected fun method(name: String, vararg parameterTypes: Class<*>): Method? =
        synchronized(methods) { methods.getOrPut(name) { findMethod(name, *parameterTypes) } }

    protected fun field(name: String): Field? =
        synchronized(fields) { fields.getOrPut(name) { findField(name) } }

    private fun findMethod(name: String, vararg parameterTypes: Class<*>): Method? =
        runCatching {
            targetClass.getMethod(name, *parameterTypes).apply { isAccessible = true }
        }.getOrNull()

    private fun findField(name: String): Field? =
        runCatching {
            targetClass.getField(name).apply { isAccessible = true }
        }.getOrNull()

    protected fun invokeString(name: String): String? =
        method(name)?.invoke(target) as? String

    protected fun fieldString(name: String): String? =
        field(name)?.get(target) as? String

    protected fun invokeBoolean(name: String): Boolean? =
        method(name)?.invoke(target) as? Boolean
}

internal class ExpressInfoWrapper(expressInfoObject: Any) : ReflectiveWrapper(expressInfoObject) {

    val provider: String? get() = invokeString("getProvider")

    // companyCode / orderNumber 参与主流程（展示、跳转、查询），目标类缺失这些
    // 字段时 hook 无法工作；保留抛异常语义，由 PROTECTIVE 模式兜底降级。
    val companyCode: String get() = fieldString("companyCode")!!
    val orderNumber: String get() = fieldString("orderNumber")!!

    var clickDisappear: Boolean
        get() = invokeBoolean("isClickDisappear") ?: false
        set(value) {
            method("setClickDisappear", BooleanPrimitiveType)?.invoke(target, value)
        }

    val phone: String? get() = invokeString("getPhone")
    val sendPhone: String? get() = invokeString("getSendPhone")

    @Suppress("UNCHECKED_CAST")
    var details: ArrayList<Any>?
        get() = field("details")?.get(target) as? ArrayList<Any>
        set(value) {
            method("setDetails", java.util.ArrayList::class.java)?.invoke(target, value)
        }

    override fun toString(): String =
        method("toString")?.invoke(target) as? String ?: super.toString()
}

internal val ExpressInfoWrapper.isXiaomi: Boolean
    get() = provider == "Miguo" || provider == "MiMall"
internal val ExpressInfoWrapper.isJingDong: Boolean
    get() = companyCode == "JDKD"
internal val ExpressInfoWrapper.isXiaomiOrJingDong: Boolean
    get() = isXiaomi || isJingDong

@Parcelize
data class ExpressInfoJumpListWrapper(
    val link: String?,
    val type: String?,
    val priority: Int?
) : Parcelable, Comparable<ExpressInfoJumpListWrapper> {

    override fun compareTo(other: ExpressInfoJumpListWrapper): Int {
        return if (priority != null && other.priority != null) {
            priority.compareTo(other.priority)
        } else if (priority != null) {
            -1
        } else {
            1
        }
    }
}

internal class ExpressEntryWrapper(expressEntryObject: Any) : ReflectiveWrapper(expressEntryObject) {

    val companyCode: String get() = fieldString("companyCode")!!
    val companyName: String get() = fieldString("companyName") ?: ""
    val orderNumber: String get() = fieldString("orderNumber")!!
    val phone: String? get() = fieldString("phone")

    // getJumpList/getUris 在旧版智能助理上可能不存在，缺失视为无跳转项。
    val jumpList: List<*>?
        get() = runCatching { method("getJumpList")?.invoke(target) as List<*>? }.getOrNull()
    val uris: List<*>? // For older versions compatible
        get() = runCatching { method("getUris")?.invoke(target) as List<*>? }.getOrNull()
    val provider: String? get() = invokeString("getProvider")
}

internal val ExpressEntryWrapper.isXiaomi: Boolean
    get() = provider == "Miguo" || provider == "MiMall"
internal val ExpressEntryWrapper.isJingDong: Boolean
    get() = companyCode == "JDKD"
internal val ExpressEntryWrapper.isShunfeng: Boolean
    get() = provider == "ShunFeng"
internal val ExpressEntryWrapper.isJiTu: Boolean
    get() = provider == "JiTu"

internal fun ExpressEntryWrapper.shouldUseNativeUI(): Boolean {
    return isXiaomi || isJingDong || isJiTu
}

internal class ExpressInfoDetailWrapper(expressInfoDetailObject: Any) :
    ReflectiveWrapper(expressInfoDetailObject) {

    var desc: String
        get() = invokeString("getDesc") ?: ""
        set(value) {
            method("setDesc", JavaStringClass)?.invoke(target, value)
        }
    var time: String
        get() = invokeString("getTime") ?: ""
        set(value) {
            method("setTime", JavaStringClass)?.invoke(target, value)
        }
}
