package com.mil.ballistics.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mil.ballistics.app.data.CalcRepository
import com.mil.ballistics.core.core.AmmoDatabase
import com.mil.ballistics.core.core.AmmoPreset
import com.mil.ballistics.core.core.BallisticInput
import com.mil.ballistics.core.core.BallisticResult
import com.mil.ballistics.core.core.BallisticSolver
import com.mil.ballistics.core.core.CorrectionModel
import com.mil.ballistics.core.core.CorrectionResult
import com.mil.ballistics.core.core.DataSensor
import com.mil.ballistics.core.core.DragModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sensor edit entry. */
data class SensorEntry(
    val key: Long,
    val distanceM: String,
    val measuredDropM: String,
    val measuredWindageM: String,
    val note: String
)

/**
 * Calculation input form state (editable strings for text field binding).
 */
data class FormState(
    // Projectile (custom; no presets in public build)
    val selectedPresetId: String = "",
    val isCustom: Boolean = true,
    val customName: String = "",
    val caliberMm: String = "10.0",
    val bulletMassGrain: String = "62",
    val muzzleVelocityMps: String = "945",
    val ballisticCoeff: String = "0.307",
    val dragModel: DragModel = DragModel.G1,
    // sight / zero
    val zeroDistanceM: String = "100",
    val sightHeightMm: String = "70",
    // environment
    val temperatureC: String = "15",
    val humidityPct: String = "50",
    val pressureKpa: String = "0",
    val muzzleAltitudeM: String = "0",
    // wind
    val windSpeedMps: String = "0",
    val windAngleDeg: String = "0",
    // target
    val targetDistanceM: String = "500",
    val targetAltitudeDeltaM: String = "0",
    // data sensor
    val sensors: List<SensorEntry> = emptyList(),
    // result
    val calculating: Boolean = false,
    val error: String? = null,
    val lastResult: BallisticResult? = null,
    val correction: CorrectionResult? = null,
    val lastSensors: List<DataSensor> = emptyList(),
    /** Navigation token: incremented on each completed calculation; UI navigates once per token (no repeat on back). */
    val resultNavigationCounter: Int = 0
) {
    fun withPreset(p: AmmoPreset): FormState = copy(
        selectedPresetId = p.id,
        isCustom = false,
        customName = p.name,
        caliberMm = fmt(p.caliberMm),
        bulletMassGrain = fmt(p.bulletMassGrain),
        muzzleVelocityMps = fmt(p.muzzleVelocityMps),
        ballisticCoeff = fmt(p.ballisticCoeff),
        dragModel = p.dragModel
    )

    companion object {
        fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    }
}

class CalcViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = CalcRepository.get(app)
    private val solver = BallisticSolver()

    private val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state.asStateFlow()

    fun applyPreset(presetId: String) {
        val p = AmmoDatabase.byId(presetId) ?: return
        _state.value = _state.value.withPreset(p)
    }

    fun setCustomEnabled(v: Boolean) {
        _state.value = _state.value.copy(isCustom = v)
    }

    /** Consume navigation event: reset counter after navigating, to avoid re-triggering on back. */
    fun consumeNavigation() {
        _state.value = _state.value.copy(resultNavigationCounter = 0)
    }

    fun update(field: String, value: String) {
        val s = _state.value
        _state.value = when (field) {
            "customName" -> s.copy(customName = value)
            "caliberMm" -> s.copy(caliberMm = value)
            "bulletMassGrain" -> s.copy(bulletMassGrain = value)
            "muzzleVelocityMps" -> s.copy(muzzleVelocityMps = value)
            "ballisticCoeff" -> s.copy(ballisticCoeff = value)
            "zeroDistanceM" -> s.copy(zeroDistanceM = value)
            "sightHeightMm" -> s.copy(sightHeightMm = value)
            "temperatureC" -> s.copy(temperatureC = value)
            "humidityPct" -> s.copy(humidityPct = value)
            "pressureKpa" -> s.copy(pressureKpa = value)
            "muzzleAltitudeM" -> s.copy(muzzleAltitudeM = value)
            "windSpeedMps" -> s.copy(windSpeedMps = value)
            "windAngleDeg" -> s.copy(windAngleDeg = value)
            "targetDistanceM" -> s.copy(targetDistanceM = value)
            "targetAltitudeDeltaM" -> s.copy(targetAltitudeDeltaM = value)
            else -> s
        }
    }

    fun setDragModel(m: DragModel) {
        _state.value = _state.value.copy(dragModel = m)
    }

    // ---- sensor editing (max 1: one correction point defines the 2nd line) ----
    fun addSensor() {
        val s = _state.value
        if (s.sensors.size >= 1) return  // at most 1
        val nextKey = (s.sensors.maxOfOrNull { it.key } ?: 0) + 1
        _state.value = s.copy(sensors = s.sensors + SensorEntry(nextKey, "", "", "", ""))
    }

    fun updateSensor(key: Long, field: String, value: String) {
        val s = _state.value
        _state.value = s.copy(
            sensors = s.sensors.map { e ->
                if (e.key != key) e
                else when (field) {
                    "distance" -> e.copy(distanceM = value)
                    "drop" -> e.copy(measuredDropM = value)
                    "windage" -> e.copy(measuredWindageM = value)
                    "note" -> e.copy(note = value)
                    else -> e
                }
            }
        )
    }

    fun removeSensor(key: Long) {
        val s = _state.value
        _state.value = s.copy(sensors = s.sensors.filterNot { it.key == key })
    }

    // ---- Calculate ----
    fun calculate() {
        val s = _state.value
        val input = buildInput(s)
        if (input == null) {
            _state.value = s.copy(error = "Invalid parameters, please check input")
            return
        }
        _state.value = s.copy(calculating = true, error = null)
        viewModelScope.launch {
            val sensors = parseSensors(s)
            val result = withContext(Dispatchers.Default) {
                val r = solver.solve(input)
                val corr = CorrectionModel.buildCorrection(sensors, r, input.targetDistanceM)
                Pair(r, corr)
            }
            _state.value = _state.value.copy(
                calculating = false,
                lastResult = result.first,
                correction = result.second,
                lastSensors = sensors,
                resultNavigationCounter = _state.value.resultNavigationCounter + 1
            )
        }
    }

    private fun buildInput(s: FormState): BallisticInput? {
        return try {
            val caliber = s.caliberMm.toDoubleOrNull() ?: return null
            val mass = s.bulletMassGrain.toDoubleOrNull() ?: return null
            val mv = s.muzzleVelocityMps.toDoubleOrNull() ?: return null
            val bc = s.ballisticCoeff.toDoubleOrNull() ?: return null
            BallisticInput(
                caliberMm = caliber,
                bulletMassGrain = mass,
                muzzleVelocityMps = mv,
                ballisticCoeff = bc,
                dragModel = s.dragModel,
                zeroDistanceM = s.zeroDistanceM.toDoubleOrNull() ?: return null,
                sightHeightMm = s.sightHeightMm.toDoubleOrNull() ?: return null,
                muzzleAltitudeM = s.muzzleAltitudeM.toDoubleOrNull() ?: return null,
                temperatureC = s.temperatureC.toDoubleOrNull() ?: return null,
                humidityPct = s.humidityPct.toDoubleOrNull() ?: return null,
                pressureKpa = s.pressureKpa.toDoubleOrNull() ?: return null,
                windSpeedMps = s.windSpeedMps.toDoubleOrNull() ?: return null,
                windAngleDeg = s.windAngleDeg.toDoubleOrNull() ?: return null,
                targetDistanceM = s.targetDistanceM.toDoubleOrNull() ?: return null,
                targetAltitudeDeltaM = s.targetAltitudeDeltaM.toDoubleOrNull() ?: return null
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSensors(s: FormState): List<DataSensor> {
        return s.sensors.mapNotNull { e ->
            val d = e.distanceM.toDoubleOrNull() ?: return@mapNotNull null
            // blank/whitespace -> null (dimension not corrected); only valid numbers are used
            val drop = e.measuredDropM.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val w = e.measuredWindageM.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            try {
                DataSensor(distanceM = d, measuredDropM = drop, measuredWindageM = w, note = e.note)
            } catch (ex: IllegalArgumentException) {
                null
            }
        }.sortedBy { it.distanceM }
    }

    // ---- Save ----
    fun saveRecord() {
        val s = _state.value
        val result = s.lastResult ?: return
        val correction = s.correction ?: CorrectionResult.NONE
        val name = buildString {
            append(if (s.isCustom) s.customName.ifEmpty { "Custom" } else AmmoDatabase.byId(s.selectedPresetId)?.name ?: "Custom")
            append(" @ ")
            append(s.targetDistanceM)
            append("m")
        }
        viewModelScope.launch {
            repository.saveRecord(
                name = name,
                input = result.input,
                result = result,
                sensors = s.lastSensors,
                correction = correction.snapshot(s.lastSensors.map { it.distanceM })
            )
        }
    }
}
