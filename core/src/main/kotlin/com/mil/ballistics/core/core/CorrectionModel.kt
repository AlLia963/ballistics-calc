package com.mil.ballistics.core.core

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Data sensor: a measurement point along the trajectory providing observed ballistic deviation, used to generate a "correction line".
 *
 * Physical meaning: at horizontal range x, the observed vertical offset drop and
 * lateral offset windage differ from the theoretical prediction. Used to correct the impact prediction.
 *
 * drop / windage may be null: a blank dimension is not corrected.
 * The correction line is anchored at the origin (firing point, zero deviation at range 0), so it starts at the firing point.
 */
@Serializable
data class DataSensor(
    val distanceM: Double,      // horizontal distance from firing point
    val measuredDropM: Double? = null,  // observed impact low (m, positive = below LOS); null = not provided
    val measuredWindageM: Double? = null, // observed impact right (m, positive = right); null = not provided
    val note: String = ""
) {
    init {
        require(distanceM > 0.0) { "sensor distance must be > 0" }
    }
}

/**
 * Serializable snapshot of the correction line (for history records).
 */
@Serializable
data class CorrectionSnapshot(
    val exists: Boolean,
    val sensorCount: Int,
    val description: String,
    val sensorDistances: List<Double>,
    val adjustedImpactDropM: Double,
    val adjustedImpactWindageM: Double
) {
    companion object {
        val NONE = CorrectionSnapshot(
            exists = false, sensorCount = 0,
            description = "No sensor",
            sensorDistances = emptyList(),
            adjustedImpactDropM = 0.0, adjustedImpactWindageM = 0.0
        )
    }
}

/** Correction line result (with callable offset functions). */
class CorrectionResult(
    val exists: Boolean,
    val dropOffsetAt: (Double) -> Double,     // returns drop correction at x (added to predicted drop)
    val windageOffsetAt: (Double) -> Double,  // returns windage correction at x
    val adjustedImpactDropM: Double,
    val adjustedImpactWindageM: Double,
    val description: String,
    val sensorCount: Int
) {
    fun snapshot(sensorDistances: List<Double> = emptyList()) = CorrectionSnapshot(
        exists = exists,
        sensorCount = sensorCount,
        description = description,
        sensorDistances = sensorDistances,
        adjustedImpactDropM = adjustedImpactDropM,
        adjustedImpactWindageM = adjustedImpactWindageM
    )

    companion object {
        val NONE = CorrectionResult(
            exists = false,
            dropOffsetAt = { 0.0 },
            windageOffsetAt = { 0.0 },
            adjustedImpactDropM = 0.0,
            adjustedImpactWindageM = 0.0,
            description = "No sensor",
            sensorCount = 0
        )
    }
}

object CorrectionModel {

    /**
     * Computes the correction line from data sensors and the predicted trajectory.
     *
     * Method:
     *  1. For each sensor, get the predicted drop/windage at that point (interpolated from BallisticResult rows),
     *        compute delta = observed - predicted. Blank drop/windage disables that dimension.
     *  2. The correction line is anchored at the origin: deviation is 0 at range 0, so the 2nd line starts at the firing point.
     *  3. One sensor -> linear interpolation from origin to that point, then hold;
     *        multiple sensors -> piecewise linear through points from origin.
     *
     * @param sensors sensors sorted by distance
     * @param predict predicted trajectory table
     */
    fun buildCorrection(
        sensors: List<DataSensor>,
        predict: BallisticResult,
        targetDistanceM: Double
    ): CorrectionResult {
        if (sensors.isEmpty()) return CorrectionResult.NONE

        val sorted = sensors.sortedBy { it.distanceM }
        val n = sorted.size

        // get predicted drop/windage at sensor range x (linear interpolation)
        fun interpPredict(x: Double): Pair<Double, Double> {
            val rows = predict.rows
            if (x <= rows.first().rangeM) {
                return rows.first().dropM to rows.first().windageM
            }
            for (i in 1 until rows.size) {
                val a = rows[i - 1]; val b = rows[i]
                if (x <= b.rangeM) {
                    val f = if (b.rangeM - a.rangeM < 1e-12) 1.0 else (x - a.rangeM) / (b.rangeM - a.rangeM)
                    return (a.dropM + (b.dropM - a.dropM) * f) to
                            (a.windageM + (b.windageM - a.windageM) * f)
                }
            }
            val last = rows.last()
            return last.dropM to last.windageM
        }

        // per-sensor deviation (only non-null fields create a node for that dimension)
        val dropNodes = ArrayList<Delta>()
        val windNodes = ArrayList<Delta>()
        sorted.forEach { s ->
            val (pd, pw) = interpPredict(s.distanceM)
            s.measuredDropM?.let { dropNodes.add(Delta(s.distanceM, it - pd)) }
            s.measuredWindageM?.let { windNodes.add(Delta(s.distanceM, it - pw)) }
        }

        // anchor at origin: always prepend (0,0) node
        dropNodes.add(0, Delta(0.0, 0.0))
        windNodes.add(0, Delta(0.0, 0.0))

        val offsetDrop: (Double) -> Double
        val offsetWindage: (Double) -> Double
        val desc: String

        if (dropNodes.size <= 1 && windNodes.size <= 1) {
            // no valid correction data
            return CorrectionResult(
                exists = false,
                dropOffsetAt = { 0.0 },
                windageOffsetAt = { 0.0 },
                adjustedImpactDropM = 0.0,
                adjustedImpactWindageM = 0.0,
                description = "Sensor has no valid data (fill at least one)",
                sensorCount = 0
            )
        }

        offsetDrop = { x -> piecewise(dropNodes, x) { it.value } }
        offsetWindage = { x -> piecewise(windNodes, x) { it.value } }

        val descParts = ArrayList<String>()
        if (dropNodes.size > 1) descParts.add("drop corrected by %d pt".format(dropNodes.size - 1))
        if (windNodes.size > 1) descParts.add("windage corrected by %d pt".format(windNodes.size - 1))
        desc = if (descParts.isEmpty()) "Corrected" else descParts.joinToString(", ")

        // corrected impact at target
        val pdTarget = interpPredict(targetDistanceM).first
        val pwTarget = interpPredict(targetDistanceM).second
        val adjDrop = pdTarget + offsetDrop(targetDistanceM)
        val adjWindage = pwTarget + offsetWindage(targetDistanceM)

        return CorrectionResult(
            exists = true,
            dropOffsetAt = offsetDrop,
            windageOffsetAt = offsetWindage,
            adjustedImpactDropM = adjDrop,
            adjustedImpactWindageM = adjWindage,
            description = desc,
            sensorCount = n
        )
    }

    private data class Delta(val x: Double, val value: Double)

    /**
     * Piecewise linear: linear interpolation from origin (0,0) through nodes;
     * holds the value after the last node (no unbounded extrapolation).
     * Nodes sorted by ascending x.
     */
    private fun piecewise(
        deltas: List<Delta>,
        x: Double,
        sel: (Delta) -> Double
    ): Double {
        val n = deltas.size
        if (n == 0) return 0.0
        if (x <= deltas.first().x) return sel(deltas.first())
        if (x >= deltas.last().x) return sel(deltas.last())   // hold at end
        for (i in 1 until n) {
            val a = deltas[i - 1]; val b = deltas[i]
            if (x <= b.x) {
                val dx = b.x - a.x
                val f = if (abs(dx) < 1e-12) 1.0 else (x - a.x) / dx
                return sel(a) + (sel(b) - sel(a)) * f
            }
        }
        return sel(deltas.last())
    }
}
