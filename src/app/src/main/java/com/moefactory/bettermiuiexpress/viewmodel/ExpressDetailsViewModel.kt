package com.moefactory.bettermiuiexpress.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moefactory.bettermiuiexpress.ktx.toLiveData
import com.moefactory.bettermiuiexpress.model.ExpressDetails
import com.moefactory.bettermiuiexpress.model.KuaiDi100ExpressState
import com.moefactory.bettermiuiexpress.model.toExpressTrace
import com.moefactory.bettermiuiexpress.repository.ExpressActualRepository
import com.moefactory.bettermiuiexpress.repository.ExpressRepository
import com.moefactory.bettermiuiexpress.utils.ExpressCompanyUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class ExpressDetailsViewModel : ViewModel() {

    private val _expressDetails = MutableLiveData<Result<ExpressDetails>>()
    val expressDetails = _expressDetails.toLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun queryExpressDetails(
        mailNumber: String,
        companyCode: String?,
        phoneNumber: String?,
        trackId: String?,
        onSaveTrackId: (String) -> Unit,
    ) {
        suspend fun doRequest(trackId: String) {
            if (!companyCode.isNullOrEmpty()) {
                val convertedCompanyCode = ExpressCompanyUtils.convertCode(companyCode)
                val flow = if (convertedCompanyCode != null) {
                    queryFromKuaiDi100(mailNumber, convertedCompanyCode, phoneNumber, trackId)
                } else {
                    ExpressRepository.queryCompanyFromKuaiDi100(mailNumber)
                        .flatMapConcat { result ->
                            // 列表可能为空、请求可能失败，都不能让协程直接死亡：
                            // 这里抛出的任何异常都会中断整个查询流程并让 UI 卡在
                            // loading，所以统一转成 Result.failure 交给 .catch。
                            val companyCodeFromApi = result.getOrNull()
                                ?.firstOrNull()
                                ?.companyCode
                                ?: throw Exception("无法识别快递公司（未在本地映射且自动识别无结果）")
                            queryFromKuaiDi100(mailNumber, companyCodeFromApi, phoneNumber, trackId)
                        }
                }
                flow.catch { _expressDetails.value = Result.failure(it) }
                    .collect { _expressDetails.value = it }
            }
        }

        viewModelScope.launch {
            if (trackId.isNullOrEmpty()) {
                val generatedTrackId = UUID.randomUUID().toString()
                try {
                    ExpressActualRepository.registerDeviceTrackIdActual(generatedTrackId)
                } catch (t: Throwable) {
                    // 注册失败时 UI 会永远停在 loading：必须把异常发出去，
                    // 让错误页显示出来（用户重试即可）。
                    _expressDetails.value = Result.failure(t)
                    return@launch
                }
                onSaveTrackId(generatedTrackId)

                doRequest(generatedTrackId)
            } else {
                doRequest(trackId)
            }
        }
    }

    private fun queryFromKuaiDi100(
        mailNumber: String, companyCode: String, phoneNumber: String?, trackId: String
    ) = ExpressRepository.queryExpressDetailsFromKuaiDi100(companyCode, mailNumber, phoneNumber, trackId)
        .map {
            val result = it.getOrNull()
            if (result == null) {
                Result.failure(Exception())
            } else {
                // state 可能为 null 或未知的新状态码，first{} 会抛
                // NoSuchElementException 导致整次查询判为失败——明明有轨迹却显示
                // 错误页。这里降级为"未知状态"，轨迹照常展示。
                val status = result.lastResult?.state
                    ?.let { code -> KuaiDi100ExpressState.entries.firstOrNull { s -> s.stateCode.toString() == code }?.stateName }
                    ?: "未知"
                Result.success(
                    ExpressDetails(
                        dataSource = "快递 100 ",
                        status = status,
                        traces = result.lastResult?.data?.map { d -> d.toExpressTrace() }
                            ?.sortedDescending() ?: listOf()
                    )
                )
            }
        }
}
