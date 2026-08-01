package com.mil.ballistics.app.data

import android.content.Context
import com.mil.ballistics.core.core.BallisticInput
import com.mil.ballistics.core.core.BallisticResult
import com.mil.ballistics.core.core.CorrectionSnapshot
import com.mil.ballistics.core.core.DataSensor
import kotlinx.coroutines.flow.Flow

/**
 * History repository: save/read calculation records and sensor presets.
 */
class CalcRepository(private val db: AppDatabase) {

    fun records(): Flow<List<CalcRecord>> = db.calcRecordDao().observeAll()

    suspend fun getRecord(id: Long): CalcRecord? = db.calcRecordDao().getById(id)

    suspend fun saveRecord(
        name: String,
        input: BallisticInput,
        result: BallisticResult,
        sensors: List<DataSensor>,
        correction: CorrectionSnapshot
    ): Long {
        val record = CalcRecord(
            name = name,
            createdAt = System.currentTimeMillis(),
            inputJson = JsonCodec.inputToString(input),
            resultJson = JsonCodec.resultToString(result),
            sensorsJson = JsonCodec.sensorsToString(sensors),
            correctionJson = JsonCodec.correctionToString(correction)
        )
        return db.calcRecordDao().insert(record)
    }

    suspend fun deleteRecord(id: Long) = db.calcRecordDao().deleteById(id)

    suspend fun clearAll() = db.calcRecordDao().clearAll()

    fun sensorPresets(): Flow<List<SensorPreset>> = db.sensorPresetDao().observeAll()

    suspend fun saveSensorPreset(name: String, sensors: List<DataSensor>) {
        db.sensorPresetDao().upsert(SensorPreset(name = name, sensorsJson = JsonCodec.sensorsToString(sensors)))
    }

    suspend fun deleteSensorPreset(id: Long) = db.sensorPresetDao().deleteById(id)

    companion object {
        @Volatile
        private var INSTANCE: CalcRepository? = null

        fun get(context: Context): CalcRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CalcRepository(AppDatabase.get(context)).also { INSTANCE = it }
            }
        }
    }
}
