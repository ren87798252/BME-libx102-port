package com.moefactory.bettermiuiexpress.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Locale

@Parcelize
data class MiuiExpress(
    val companyCode: String,
    val companyName: String,
    val mailNumber: String,
    val phoneNumber: String?
) : Parcelable

@Parcelize
data class ExpressDetails(
    val dataSource: String,
    val status: String,
    val traces: List<ExpressTrace>
) : Parcelable

@Parcelize
data class ExpressTrace(
    val fullDateTime: String,
    val date: String,
    val time: String,
    val description: String
) : Parcelable, Comparable<ExpressTrace> {

    // "yyyy-MM-dd HH:mm:ss" 是定宽格式，字典序与时间序一致。
    // 之前用 SimpleDateFormat.parse() 比较：慢（每次比较都新建解析器），
    // 且解析失败时 `!!` 会抛 NPE（快递100 对异常单可能返回非标准时间）。
    override operator fun compareTo(other: ExpressTrace): Int =
        fullDateTime.compareTo(other.fullDateTime)
}

fun KuaiDi100ExpressDetails.toExpressTrace(): ExpressTrace {
    val originalSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    val newSdf = SimpleDateFormat("MM-dd\nHH:mm", Locale.CHINA)
    // 快递100 偶发返回非标准时间，parse 失败时回退为原始串，避免 `!!` NPE。
    val dateTime = runCatching { originalSdf.parse(formattedTime) }.getOrNull()
        ?: return ExpressTrace(formattedTime, formattedTime, formattedTime, context)
    val newDateTime = newSdf.format(dateTime).split("\n")

    return ExpressTrace(formattedTime, newDateTime[0], newDateTime[1], context)
}
