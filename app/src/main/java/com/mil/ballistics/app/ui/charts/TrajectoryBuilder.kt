package com.mil.ballistics.app.ui.charts

import com.mil.ballistics.core.core.BallisticResult
import com.mil.ballistics.core.core.BallisticRow
import com.mil.ballistics.core.core.CorrectionResult

/**
 * Builds the "predicted" and "corrected" lines from a calculation result.
 * Corrected line = predicted line + sensor-fitted offset (interpolated by range).
 */
object TrajectoryBuilder {

    fun build(
        result: BallisticResult,
        correction: CorrectionResult?
    ): TrajectoryLines {
        val predict = result.rows
        val corrected = if (correction != null && correction.exists) {
            predict.map { row ->
                row.copy(
                    dropM = row.dropM + correction.dropOffsetAt(row.rangeM),
                    windageM = row.windageM + correction.windageOffsetAt(row.rangeM)
                )
            }
        } else null
        return TrajectoryLines(predict = predict, correction = corrected)
    }
}
