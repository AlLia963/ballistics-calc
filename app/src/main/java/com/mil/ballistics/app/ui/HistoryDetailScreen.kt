package com.mil.ballistics.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mil.ballistics.app.data.JsonCodec
import com.mil.ballistics.app.ui.charts.Ballistic2DChart
import com.mil.ballistics.app.ui.charts.TrajectoryBuilder
import com.mil.ballistics.core.core.BallisticInput
import com.mil.ballistics.core.core.BallisticResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val detail by viewModel.detail.collectAsState()

    LaunchedEffect(recordId) {
        viewModel.loadDetail(recordId)
    }

    if (detail == null) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Loading…")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    val input = detail!!.input
    val result = detail!!.result
    val correction = JsonCodec.correctionFromString(detail!!.record.correctionJson)
    val sensors = detail!!.sensors

    val lines = if (correction.exists) {
        val corrObj = com.mil.ballistics.core.core.CorrectionResult(
            exists = true,
            dropOffsetAt = { x ->
                interpolateOffset(x, sensors, result) { it.measuredDropM ?: 0.0 } - predictDrop(x, result)
            },
            windageOffsetAt = { x ->
                interpolateOffset(x, sensors, result) { it.measuredWindageM ?: 0.0 } - predictWindage(x, result)
            },
            adjustedImpactDropM = correction.adjustedImpactDropM,
            adjustedImpactWindageM = correction.adjustedImpactWindageM,
            description = correction.description,
            sensorCount = correction.sensorCount
        )
        TrajectoryBuilder.build(result, corrObj)
    } else {
        TrajectoryBuilder.build(result, null)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("← Back") }
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            Text(
                fmt.format(Date(detail!!.record.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(detail!!.record.name, style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(12.dp))

        // Summary
        Card {
            Column(Modifier.padding(12.dp)) {
                StatRowD("Target Range", "%.0f m".format(input.targetDistanceM))
                StatRowD("Required Elevation", "%.4f°".format(result.solvedElevationDeg))
                StatRowD("Time of Flight", "%.2f s".format(result.flightTimeToTargetS))
                StatRowD("Impact Velocity", "%.1f m/s".format(result.impactVelocityMps))
                StatRowD("Impact Energy", "%.0f J".format(result.impactEnergyJ))
                StatRowD("Windage", "%+.3f m".format(result.impactWindageM))
                if (correction.exists) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Corrected", color = Color(0xFFEF6C00), style = MaterialTheme.typography.titleSmall)
                    StatRowD("Corr. Impact Offset", "%+.3f m".format(correction.adjustedImpactDropM))
                    StatRowD("Corr. Windage", "%+.3f m".format(correction.adjustedImpactWindageM))
                    Text(correction.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Ballistic2DChart(lines)

        Spacer(Modifier.height(16.dp))

        // Full parameters
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("All Parameters", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ParamSectionD("Projectile", input)
                ParamSectionD("Sight / Zero", input)
                ParamSectionD("Environment", input)
                ParamSectionD("Wind", input)
                ParamSectionD("Target", input)
                if (sensors.isNotEmpty()) {
                    Text("Sensors", style = MaterialTheme.typography.titleSmall)
                    sensors.forEachIndexed { i, s ->
                        Text("  Sensor ${i + 1}: dist ${s.distanceM}m · drop ${s.measuredDropM}m · windage ${s.measuredWindageM}m", fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun predictDrop(x: Double, result: BallisticResult): Double =
    interpValue(result.rows.map { it.rangeM to it.dropM }, x)

private fun predictWindage(x: Double, result: BallisticResult): Double =
    interpValue(result.rows.map { it.rangeM to it.windageM }, x)

private fun interpolateOffset(
    x: Double,
    sensors: List<com.mil.ballistics.core.core.DataSensor>,
    result: BallisticResult,
    sel: (com.mil.ballistics.core.core.DataSensor) -> Double
): Double {
    if (sensors.isEmpty()) return 0.0
    val sorted = sensors.sortedBy { it.distanceM }
    val values = sorted.map { s -> s.distanceM to sel(s) }
    return interpValue(values, x)
}

private fun interpValue(points: List<Pair<Double, Double>>, x: Double): Double {
    if (points.isEmpty()) return 0.0
    if (x <= points.first().first) return points.first().second
    if (x >= points.last().first) {
        if (points.size >= 2) {
            val a = points[points.size - 2]; val b = points.last()
            val dx = b.first - a.first
            if (kotlin.math.abs(dx) > 1e-9) {
                return b.second + (b.second - a.second) / dx * (x - b.first)
            }
        }
        return points.last().second
    }
    for (i in 1 until points.size) {
        val a = points[i - 1]; val b = points[i]
        if (x <= b.first) {
            val dx = b.first - a.first
            val f = if (kotlin.math.abs(dx) < 1e-9) 1.0 else (x - a.first) / dx
            return a.second + (b.second - a.second) * f
        }
    }
    return points.last().second
}

@Composable
private fun StatRowD(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ParamSectionD(title: String, input: BallisticInput) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    when (title) {
        "Projectile" -> {
            ParamRowD("Caliber", "%.2f mm".format(input.caliberMm))
            ParamRowD("Mass", "%.1f gr".format(input.bulletMassGrain))
            ParamRowD("Muzzle Velocity", "%.1f m/s".format(input.muzzleVelocityMps))
            ParamRowD("Ballistic Coeff", "%.4f (%s)".format(input.ballisticCoeff, input.dragModel.label))
        }
        "Sight / Zero" -> {
            ParamRowD("Zero Distance", "%.0f m".format(input.zeroDistanceM))
            ParamRowD("Sight Height", "%.1f mm".format(input.sightHeightMm))
        }
        "Environment" -> {
            ParamRowD("Temperature", "%.1f °C".format(input.temperatureC))
            ParamRowD("Humidity", "%.0f %%".format(input.humidityPct))
            ParamRowD("Pressure", if (input.pressureKpa > 0) "%.2f kPa".format(input.pressureKpa) else "ISA auto")
            ParamRowD("Altitude", "%.0f m".format(input.muzzleAltitudeM))
        }
        "Wind" -> {
            ParamRowD("Wind Speed", "%.1f m/s".format(input.windSpeedMps))
            ParamRowD("Wind Angle", "%.0f°".format(input.windAngleDeg))
        }
        "Target" -> {
            ParamRowD("Target Range", "%.0f m".format(input.targetDistanceM))
            ParamRowD("Target Alt. Delta", "%+.0f m".format(input.targetAltitudeDeltaM))
        }
    }
}

@Composable
private fun ParamRowD(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
