package com.mil.ballistics.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mil.ballistics.app.ui.charts.Ballistic2DChart
import com.mil.ballistics.app.ui.charts.TrajectoryBuilder
import com.mil.ballistics.core.core.BallisticInput
import com.mil.ballistics.core.core.BallisticResult
import com.mil.ballistics.core.core.CorrectionResult

@Composable
fun ResultScreen(
    onBack: () -> Unit,
    viewModel: CalcViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val result = state.lastResult
    val correction = state.correction

    if (result == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text("No result yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack) { Text("Back to Input") }
        }
        return
    }

    val lines = TrajectoryBuilder.build(result, correction)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("← Back") }
            Button(onClick = { viewModel.saveRecord() }) { Text("Save") }
        }
        Spacer(Modifier.height(12.dp))

        // ---- Impact summary ----
        if (!result.hitsTarget) {
            Text(
                "⚠ Target out of range: projectile lands at ~%.0f m, cannot reach %.0f m".format(result.maxRangeM, result.input.targetDistanceM),
                color = Color(0xFFD32F2F),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(12.dp)) {
                Text("Impact Data", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                StatRow("Target Range", "%.0f m".format(result.input.targetDistanceM))
                if (!result.hitsTarget) {
                    StatRow("Actual Impact", "%.0f m".format(result.maxRangeM))
                }
                StatRow("Required Elevation", "%.4f°".format(result.solvedElevationDeg))
                StatRow("Time of Flight", "%.2f s".format(result.flightTimeToTargetS))
                StatRow("Impact Velocity", "%.1f m/s".format(result.impactVelocityMps))
                StatRow("Impact Energy", "%.0f J".format(result.impactEnergyJ))
                StatRow("Windage", "%+.3f m".format(result.impactWindageM))
                if (correction != null && correction.exists) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Corrected (sensor)", color = Color(0xFFEF6C00), style = MaterialTheme.typography.titleSmall)
                    StatRow("Corr. Impact Offset", "%+.3f m".format(correction.adjustedImpactDropM))
                    StatRow("Corr. Windage", "%+.3f m".format(correction.adjustedImpactWindageM))
                    Text(correction.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- 2D charts ----
        Ballistic2DChart(lines)

        Spacer(Modifier.height(16.dp))

        // ---- Ballistic table ----
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Ballistic Table", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    TableCell("Range", 0.12f, true)
                    TableCell("Drop", 0.16f, true)
                    TableCell("Windage", 0.14f, true)
                    TableCell("Velocity", 0.14f, true)
                    TableCell("Mach", 0.10f, true)
                    TableCell("Time", 0.12f, true)
                    TableCell("Energy", 0.22f, true)
                }
                result.rows.forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        TableCell("%.0f".format(row.rangeM), 0.12f)
                        TableCell("%+.3f".format(row.dropM), 0.16f)
                        TableCell("%+.3f".format(row.windageM), 0.14f)
                        TableCell("%.0f".format(row.velocityMps), 0.14f)
                        TableCell("%.2f".format(row.mach), 0.10f)
                        TableCell("%.2f".format(row.flightTimeS), 0.12f)
                        TableCell("%.0f".format(row.energyJ), 0.22f)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Full parameters (below charts) ----
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("All Parameters", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ParamSection("Projectile", result.input)
                ParamSection("Sight / Zero", result.input)
                ParamSection("Environment", result.input)
                ParamSection("Wind", result.input)
                ParamSection("Target", result.input)
                if (state.lastSensors.isNotEmpty()) {
                    Text("Sensors", style = MaterialTheme.typography.titleSmall)
                    state.lastSensors.forEachIndexed { i, s ->
                        Text("  Sensor ${i + 1}: dist ${s.distanceM}m · drop ${s.measuredDropM}m · windage ${s.measuredWindageM}m", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun RowScope.TableCell(text: String, weight: Float, header: Boolean = false) {
    Text(
        text,
        modifier = Modifier.weight(weight).padding(vertical = 3.dp),
        fontSize = 11.sp,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        maxLines = 1
    )
}

@Composable
private fun ParamSection(title: String, input: BallisticInput) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    when (title) {
        "Projectile" -> {
            ParamRow("Caliber", "%.2f mm".format(input.caliberMm))
            ParamRow("Mass", "%.1f gr".format(input.bulletMassGrain))
            ParamRow("Muzzle Velocity", "%.1f m/s".format(input.muzzleVelocityMps))
            ParamRow("Ballistic Coeff", "%.4f (%s)".format(input.ballisticCoeff, input.dragModel.label))
        }
        "Sight / Zero" -> {
            ParamRow("Zero Distance", "%.0f m".format(input.zeroDistanceM))
            ParamRow("Sight Height", "%.1f mm".format(input.sightHeightMm))
        }
        "Environment" -> {
            ParamRow("Temperature", "%.1f °C".format(input.temperatureC))
            ParamRow("Humidity", "%.0f %%".format(input.humidityPct))
            ParamRow("Pressure", if (input.pressureKpa > 0) "%.2f kPa".format(input.pressureKpa) else "ISA auto")
            ParamRow("Altitude", "%.0f m".format(input.muzzleAltitudeM))
        }
        "Wind" -> {
            ParamRow("Wind Speed", "%.1f m/s".format(input.windSpeedMps))
            ParamRow("Wind Angle", "%.0f°".format(input.windAngleDeg))
        }
        "Target" -> {
            ParamRow("Target Range", "%.0f m".format(input.targetDistanceM))
            ParamRow("Target Alt. Delta", "%+.0f m".format(input.targetAltitudeDeltaM))
        }
    }
}

@Composable
private fun ParamRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
