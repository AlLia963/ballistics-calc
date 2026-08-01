package com.mil.ballistics.app.data

import com.mil.ballistics.core.core.BallisticInput
import com.mil.ballistics.core.core.BallisticResult
import com.mil.ballistics.core.core.CorrectionSnapshot
import com.mil.ballistics.core.core.DataSensor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * JSON serialization helper: serializes input params, results, sensors, and correction snapshots
 * to strings stored in Room, and deserializes on read.
 */
object JsonCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val sensorList = ListSerializer(DataSensor.serializer())

    fun inputToString(input: BallisticInput): String = json.encodeToString(BallisticInput.serializer(), input)
    fun inputFromString(s: String): BallisticInput = json.decodeFromString(BallisticInput.serializer(), s)

    fun resultToString(result: BallisticResult): String = json.encodeToString(BallisticResult.serializer(), result)
    fun resultFromString(s: String): BallisticResult = json.decodeFromString(BallisticResult.serializer(), s)

    fun sensorsToString(sensors: List<DataSensor>): String = json.encodeToString(sensorList, sensors)
    fun sensorsFromString(s: String): List<DataSensor> = json.decodeFromString(sensorList, s)

    fun correctionToString(c: CorrectionSnapshot): String = json.encodeToString(CorrectionSnapshot.serializer(), c)
    fun correctionFromString(s: String): CorrectionSnapshot = json.decodeFromString(CorrectionSnapshot.serializer(), s)
}
