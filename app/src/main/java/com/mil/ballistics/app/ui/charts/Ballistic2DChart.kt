package com.mil.ballistics.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mil.ballistics.core.core.BallisticRow

/** Two trajectory lines: predicted (blue) + corrected (orange). */
data class TrajectoryLines(
    val predict: List<BallisticRow>,
    val correction: List<BallisticRow>?   // null when no sensor
)

/**
 * 2D ballistics chart.
 * top = vertical trajectory (x range, y drop);
 * bottom = horizontal trajectory (x windage, y range).
 * Each line marks time points (1s interval + end); origin (0,0) marked with a red dot.
 */
@Composable
fun Ballistic2DChart(
    lines: TrajectoryLines,
    modifier: Modifier = Modifier,
    heightDp: Int = 280
) {
    val colorPredict = Color(0xFF1565C0)
    val colorCorrection = Color(0xFFEF6C00)
    val gridColor = Color(0xFFB0BEC5)
    val textColor = Color(0xFF37474F)
    val timeColor = Color(0xFF4A148C)
    val originColor = Color(0xFFD32F2F)
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        // ---- Vertical trajectory: x=range, y=drop ----
        Text("Vertical Ballistics (Drop vs Range)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
        ) {
            // data range (includes 0 so the origin is visible)
            val maxDist = maxOf(
                lines.predict.lastOrNull()?.rangeM ?: 0.0,
                lines.correction?.lastOrNull()?.rangeM ?: 0.0
            ).coerceAtLeast(1.0)
            val allY = lines.predict.map { it.dropM } +
                (lines.correction?.map { it.dropM } ?: emptyList()) + listOf(0.0)
            val maxY = allY.maxOrNull() ?: 0.0
            val minY = allY.minOrNull() ?: 0.0
            val pad = ((maxY - minY).coerceAtLeast(0.01)) * 0.1

            drawChartBackground(
                gridColor, textColor, textMeasurer,
                xMin = 0.0, xMax = maxDist, yMin = minY - pad, yMax = maxY + pad,
                xLabel = "Range (m)", yLabel = "Drop (m)",
                originColor = originColor, showYAxisAtX = 0.0
            )

            drawTrajectoryPath(
                lines.predict, 0.0, maxDist, minY - pad, maxY + pad,
                colorPredict, textMeasurer, { it.rangeM }, { it.dropM },
                timeColor
            )
            lines.correction?.let {
                drawTrajectoryPath(
                    it, 0.0, maxDist, minY - pad, maxY + pad,
                    colorCorrection, textMeasurer, { it.rangeM }, { it.dropM },
                    timeColor
                )
            }
            // horizontal legend
            drawLegendHorizontal(
                textMeasurer,
                entries = listOf(
                    LegendEntry(colorPredict, "Predicted"),
                    LegendEntry(colorCorrection, "Corrected")
                ).filter { lines.correction != null || it.label == "Predicted" }
            )
        }

        // ---- Horizontal trajectory: x=windage, y=range ----
        Text("Horizontal Ballistics (Windage vs Range)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
        ) {
            val maxDist = maxOf(
                lines.predict.lastOrNull()?.rangeM ?: 0.0,
                lines.correction?.lastOrNull()?.rangeM ?: 0.0
            ).coerceAtLeast(1.0)
            // x=windage, y=range
            val allW = lines.predict.map { it.windageM } +
                (lines.correction?.map { it.windageM } ?: emptyList()) + listOf(0.0)
            val maxW = allW.maxOrNull() ?: 0.0
            val minW = allW.minOrNull() ?: 0.0
            val wpad = ((maxW - minW).coerceAtLeast(0.01)) * 0.1

            drawChartBackground(
                gridColor, textColor, textMeasurer,
                xMin = minW - wpad, xMax = maxW + wpad, yMin = 0.0, yMax = maxDist,
                xLabel = "Windage (m)", yLabel = "Range (m)",
                originColor = originColor, showYAxisAtX = 0.0
            )

            drawTrajectoryPath(
                lines.predict, minW - wpad, maxW + wpad, 0.0, maxDist,
                colorPredict, textMeasurer, { it.windageM }, { it.rangeM },
                timeColor
            )
            lines.correction?.let {
                drawTrajectoryPath(
                    it, minW - wpad, maxW + wpad, 0.0, maxDist,
                    colorCorrection, textMeasurer, { it.windageM }, { it.rangeM },
                    timeColor
                )
            }
            // horizontal legend
            drawLegendHorizontal(
                textMeasurer,
                entries = listOf(
                    LegendEntry(colorPredict, "Predicted"),
                    LegendEntry(colorCorrection, "Corrected")
                ).filter { lines.correction != null || it.label == "Predicted" }
            )
        }
    }
}

/**
 * Draws one trajectory line with its time markers.
 * @param xOf extracts x value from a row (range or windage)
 * @param yOf extracts y value from a row (drop or range)
 */
private fun DrawScope.drawTrajectoryPath(
    rows: List<BallisticRow>,
    xMin: Double, xMax: Double,
    yMin: Double, yMax: Double,
    color: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    xOf: (BallisticRow) -> Double,
    yOf: (BallisticRow) -> Double,
    timeColor: Color
) {
    if (rows.size < 2) return
    val path = Path()
    rows.forEachIndexed { i, row ->
        val x = mapX(xOf(row), xMin, xMax)
        val y = mapY(yOf(row), yMin, yMax)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = 3f))

    // time markers (1s interval + end)
    val tps = computeTimePoints(rows)
    tps.forEach { tp ->
        val px = mapX(interpX(rows, tp, xOf), xMin, xMax)
        val py = mapY(interpY(rows, tp, yOf), yMin, yMax)
        drawCircle(color = timeColor, radius = 5f, center = Offset(px, py))
        val l = textMeasurer.measure(formatTimeLabel(tp.timeS), style = TextStyle(color = timeColor, fontSize = 9.sp))
        drawText(l, topLeft = Offset(px - l.size.width / 2f, py - l.size.height - 3f))
    }
}

/** Interpolates the x value at a time marker. */
private fun interpX(rows: List<BallisticRow>, tp: TimePoint, xOf: (BallisticRow) -> Double): Double {
    if (rows.size < 2) return 0.0
    val t = tp.timeS
    for (i in 1 until rows.size) {
        val a = rows[i - 1]; val b = rows[i]
        if (t <= b.flightTimeS) {
            val dt = b.flightTimeS - a.flightTimeS
            if (dt < 1e-9) return xOf(a)
            val f = (t - a.flightTimeS) / dt
            return xOf(a) + (xOf(b) - xOf(a)) * f
        }
    }
    return xOf(rows.last())
}

/** Interpolates the y value at a time marker. */
private fun interpY(rows: List<BallisticRow>, tp: TimePoint, yOf: (BallisticRow) -> Double): Double {
    if (rows.size < 2) return 0.0
    val t = tp.timeS
    for (i in 1 until rows.size) {
        val a = rows[i - 1]; val b = rows[i]
        if (t <= b.flightTimeS) {
            val dt = b.flightTimeS - a.flightTimeS
            if (dt < 1e-9) return yOf(a)
            val f = (t - a.flightTimeS) / dt
            return yOf(a) + (yOf(b) - yOf(a)) * f
        }
    }
    return yOf(rows.last())
}

/**
 * Chart background: grid, axes, closed border, tick labels, axis titles, origin red dot.
 * @param showYAxisAtX draws the zero axis at this x data value (the origin axis)
 */
private fun DrawScope.drawChartBackground(
    gridColor: Color,
    textColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    xMin: Double, xMax: Double,
    yMin: Double, yMax: Double,
    xLabel: String,
    yLabel: String,
    originColor: Color,
    showYAxisAtX: Double
) {
    val h = size.height
    val w = size.width
    val gridH = h * 0.80f
    val gridW = w * 0.78f
    val leftPad = w * 0.10f
    val bottomPad = h * 0.14f

    // grid lines
    repeat(5) { i ->
        val y = bottomPad + (gridH * i / 4f)
        drawLine(gridColor.copy(alpha = 0.4f), Offset(leftPad, y), Offset(leftPad + gridW, y), strokeWidth = 1f)
    }
    repeat(6) { i ->
        val x = leftPad + (gridW * i / 5f)
        drawLine(gridColor.copy(alpha = 0.4f), Offset(x, bottomPad), Offset(x, bottomPad + gridH), strokeWidth = 1f)
    }

    // axes + closed border
    val axisColor = textColor
    drawLine(axisColor, Offset(leftPad, bottomPad), Offset(leftPad, bottomPad + gridH), strokeWidth = 2f)
    drawLine(axisColor, Offset(leftPad, bottomPad + gridH), Offset(leftPad + gridW, bottomPad + gridH), strokeWidth = 2f)
    drawLine(axisColor.copy(alpha = 0.7f), Offset(leftPad, bottomPad), Offset(leftPad + gridW, bottomPad), strokeWidth = 1.5f)
    drawLine(axisColor.copy(alpha = 0.7f), Offset(leftPad + gridW, bottomPad), Offset(leftPad + gridW, bottomPad + gridH), strokeWidth = 1.5f)

    // Y-axis ticks (5, minimal)
    val yTicks = 5
    for (i in 0 until yTicks) {
        val value = yMax - (yMax - yMin) * i / (yTicks - 1)
        val y = bottomPad + gridH * i / (yTicks - 1)
        val l = textMeasurer.measure("%.2f".format(value), style = TextStyle(color = textColor, fontSize = 9.sp))
        drawText(l, topLeft = Offset(2f, y - l.size.height / 2f))
    }
    // X-axis ticks (rounded)
    val xTicks = 5
    for (i in 0 until xTicks) {
        val value = xMin + (xMax - xMin) * i / (xTicks - 1)
        val x = leftPad + gridW * i / (xTicks - 1)
        val l = textMeasurer.measure("%.2f".format(value), style = TextStyle(color = textColor, fontSize = 9.sp))
        drawText(l, topLeft = Offset(x - l.size.width / 2f, bottomPad + gridH + 2f))
    }

    // axis titles (away from ticks)
    val xTitle = textMeasurer.measure(xLabel, style = TextStyle(color = textColor, fontSize = 11.sp))
    drawText(xTitle, topLeft = Offset(leftPad + gridW - xTitle.size.width, bottomPad + gridH + 16f))
    val yTitle = textMeasurer.measure(yLabel, style = TextStyle(color = textColor, fontSize = 11.sp))
    drawText(yTitle, topLeft = Offset(2f, bottomPad - yTitle.size.height - 10f))

    // origin red dot: (showYAxisAtX, 0)
    val ox = mapX(showYAxisAtX, xMin, xMax)
    val oy = mapY(0.0, yMin, yMax)
    // draw only if origin is inside the plot
    if (ox in leftPad..(leftPad + gridW) && oy in bottomPad..(bottomPad + gridH)) {
        drawCircle(color = originColor, radius = 5f, center = Offset(ox, oy))
        drawCircle(color = Color.White, radius = 5f, center = Offset(ox, oy), style = Stroke(width = 2f))
    }
}

private fun DrawScope.mapX(x: Double, xMin: Double, xMax: Double): Float {
    val leftPad = size.width * 0.10f
    val gridW = size.width * 0.78f
    val range = (xMax - xMin).coerceAtLeast(1e-9)
    return leftPad + ((x - xMin) / range).toFloat() * gridW
}

private fun DrawScope.mapY(y: Double, yMin: Double, yMax: Double): Float {
    val bottomPad = size.height * 0.14f
    val gridH = size.height * 0.80f
    val range = (yMax - yMin).coerceAtLeast(1e-9)
    val frac = ((y - yMin) / range).toFloat()
    return bottomPad + gridH * (1f - frac)
}

/** Legend entry. */
private data class LegendEntry(val color: Color, val label: String)

/**
 * Draws a horizontal legend at the top-right: swatch + text, left to right, no overlap.
 */
private fun DrawScope.drawLegendHorizontal(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    entries: List<LegendEntry>
) {
    val style = TextStyle(color = textColorDefault, fontSize = 11.sp)
    val swatch = 10f
    val gap = 6f
    // layout right-to-left, each item takes (swatch + gap + textWidth)
    val widths = entries.map { e ->
        val l = textMeasurer.measure(e.label, style = style)
        swatch + gap + l.size.width
    }
    var x = size.width - 12f
    entries.forEachIndexed { i, e ->
        val l = textMeasurer.measure(e.label, style = style)
        val w = widths[i]
        x -= w
        // swatch
        drawCircle(color = e.color, radius = swatch / 2f, center = Offset(x + swatch / 2f, 18f))
        // text
        drawText(l, topLeft = Offset(x + swatch + gap, 18f - l.size.height / 2f))
        x -= gap
    }
}

/** Default legend text color (matches chart text). */
private val textColorDefault: Color = Color(0xFF37474F)
