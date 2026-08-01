package com.mil.ballistics.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Calculation record entity. Stores the full input parameters + result snapshot (JSON serialized),
 * satisfying "save past calculations, reopen with all parameters".
 */
@androidx.room.Entity(tableName = "calc_records")
data class CalcRecord(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,                 // record name (e.g. projectile name + range)
    val createdAt: Long,              // epoch millis
    val inputJson: String,            // BallisticInput serialized
    val resultJson: String,           // BallisticResult serialized
    val sensorsJson: String,          // List<DataSensor> serialized
    val correctionJson: String        // CorrectionSnapshot serialized
)

@Dao
interface CalcRecordDao {
    @Query("SELECT * FROM calc_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CalcRecord>>

    @Query("SELECT * FROM calc_records WHERE id = :id")
    suspend fun getById(id: Long): CalcRecord?

    @Insert
    suspend fun insert(record: CalcRecord): Long

    @Query("DELETE FROM calc_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM calc_records")
    suspend fun clearAll()
}

@Dao
interface SensorPresetDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(sensor: SensorPreset)

    @androidx.room.Query("SELECT * FROM sensor_presets ORDER BY name")
    fun observeAll(): Flow<List<SensorPreset>>

    @androidx.room.Query("DELETE FROM sensor_presets WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/**
 * Sensor preset (savable common config). Stores only the sensor list snapshot.
 */
@androidx.room.Entity(tableName = "sensor_presets")
data class SensorPreset(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val sensorsJson: String
)
