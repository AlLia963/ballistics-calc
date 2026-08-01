package com.mil.ballistics.app.ui.charts

import com.mil.ballistics.core.core.BallisticRow

/** Time markers on a trajectory line (1s interval + end). */
data class TimePoint(
    val timeS: Double,
    val rangeM: Double,
    val dropM: Double,
    val windageM: Double
)

/**
 * Computes time points to mark: every integer second + the end (marked even if <1s).
 * Linearly interpolates the position at time t between adjacent sampled rows.
 */
fun computeTimePoints(rows: List<BallisticRow>): List<TimePoint> {
    if (rows.size < 2) return emptyList()
    val out = ArrayList<TimePoint>()
    val endT = rows.last().flightTimeS
    var t = 1.0
    while (t < endT - 0.001) {
        interpolateTime(rows, t)?.let { out.add(it) }
        t += 1.0
    }
    // end point always marked
    val last = rows.last()
    out.add(TimePoint(last.flightTimeS, last.rangeM, last.dropM, last.windageM))
    return out
}

/** Interpolates the position at time t by flight time between sampled rows. */
fun interpolateTime(rows: List<BallisticRow>, t: Double): TimePoint? {
    if (rows.size < 2) return null
    if (t < rows.first().flightTimeS || t > rows.last().flightTimeS) return null
    for (i in 1 until rows.size) {
        val a = rows[i - 1]
        val b = rows[i]
        if (t <= b.flightTimeS) {
            val dt = b.flightTimeS - a.flightTimeS
            if (dt < 1e-9) return null
            val f = (t - a.flightTimeS) / dt
            return TimePoint(
                timeS = t,
                rangeM = a.rangeM + (b.rangeM - a.rangeM) * f,
                dropM = a.dropM + (b.dropM - a.dropM) * f,
                windageM = a.windageM + (b.windageM - a.windageM) * f
            )
        }
    }
    return null
}

/** Time label: integer seconds show "1s", non-integer show "1.2s". */
fun formatTimeLabel(t: Double): String {
    val rounded = kotlin.math.round(t)
    return if (kotlin.math.abs(t - rounded) < 0.05) "%.0fs".format(rounded) else "%.1fs".format(t)
}
