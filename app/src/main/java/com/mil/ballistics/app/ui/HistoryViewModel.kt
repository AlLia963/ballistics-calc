package com.mil.ballistics.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mil.ballistics.app.data.CalcRecord
import com.mil.ballistics.app.data.CalcRepository
import com.mil.ballistics.app.data.JsonCodec
import com.mil.ballistics.core.core.BallisticResult
import com.mil.ballistics.core.core.DataSensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryItemUi(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val targetDistanceM: String,
    val caliber: String
)

data class HistoryDetail(
    val record: CalcRecord,
    val input: com.mil.ballistics.core.core.BallisticInput,
    val result: BallisticResult,
    val sensors: List<DataSensor>
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = CalcRepository.get(app)

    private val _items = MutableStateFlow<List<HistoryItemUi>>(emptyList())
    val items: StateFlow<List<HistoryItemUi>> = _items.asStateFlow()

    private val _detail = MutableStateFlow<HistoryDetail?>(null)
    val detail: StateFlow<HistoryDetail?> = _detail.asStateFlow()

    init {
        viewModelScope.launch {
            repository.records().collect { records ->
                _items.value = records.map { r ->
                    val input = JsonCodec.inputFromString(r.inputJson)
                    HistoryItemUi(
                        id = r.id,
                        name = r.name,
                        createdAt = r.createdAt,
                        targetDistanceM = input.targetDistanceM.toString(),
                        caliber = input.caliberMm.toString()
                    )
                }
            }
        }
    }

    fun loadDetail(id: Long) {
        viewModelScope.launch {
            val r = repository.getRecord(id) ?: return@launch
            _detail.value = HistoryDetail(
                record = r,
                input = JsonCodec.inputFromString(r.inputJson),
                result = JsonCodec.resultFromString(r.resultJson),
                sensors = JsonCodec.sensorsFromString(r.sensorsJson)
            )
        }
    }

    fun clearDetail() {
        _detail.value = null
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.deleteRecord(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
