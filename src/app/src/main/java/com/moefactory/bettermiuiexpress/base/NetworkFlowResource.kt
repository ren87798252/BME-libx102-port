package com.moefactory.bettermiuiexpress.base

import android.util.Log
import kotlinx.coroutines.flow.flow

@Suppress("FunctionName")
inline fun <RequestType> NetworkBoundResource(
    crossinline fetch: suspend () -> RequestType,
    crossinline onFetchFailed: (Throwable) -> Unit = { }
) = flow {
    val data = try {
        val result = fetch()
        Result.success(result)
    } catch (throwable: Throwable) {
        // Replaces YukiHookAPI's loggerE, which is only usable inside hooks.
        // NOTE: the tag is inlined as a literal instead of a file-private const,
        // because a public inline function may not read a non-public property.
        Log.e("BetterMiuiExpress", "network request failed", throwable)
        onFetchFailed(throwable)
        Result.failure(throwable)
    }

    emit(data)
}
