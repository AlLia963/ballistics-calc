package com.mil.ballistics.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mil.ballistics.core.core.AmmoDatabase
import com.mil.ballistics.core.core.DragModel

@Composable
fun InputScreen(
    onNavigateResult: () -> Unit,
    onNavigateHistory: () -> Unit,
    viewModel: CalcViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    // auto-navigate to result page after calculation completes (counter incremented)
    LaunchedEffect(state.resultNavigationCounter) {
        if (state.resultNavigationCounter > 0) {
            onNavigateResult()
            viewModel.consumeNavigation()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Ballistics Calculator", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text("G1/G7 Drag Model · Atmosphere Correction · Sensor Calibration", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onNavigateHistory) { Text("History") }
        }

        // ---- Projectile (custom only; no presets in public build) ----
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Text("Projectile", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Enter projectile parameters manually.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                NumField("Name", state.customName, "Custom", { viewModel.update("customName", it) }, false)
                NumField("Caliber (mm)", state.caliberMm, "10.0", { viewModel.update("caliberMm", it) })
                NumField("Mass (gr)", state.bulletMassGrain, "62", { viewModel.update("bulletMassGrain", it) })
                NumField("Muzzle Velocity (m/s)", state.muzzleVelocityMps, "945", { viewModel.update("muzzleVelocityMps", it) })
                NumField("Ballistic Coefficient", state.ballisticCoeff, "0.307", { viewModel.update("ballisticCoeff", it) })
                Spacer(Modifier.height(4.dp))
                Row {
                    DragModel.entries.forEach { m ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.setDragModel(m) }
                        ) {
                            RadioButton(
                                selected = state.dragModel == m,
                                onClick = { viewModel.setDragModel(m) }
                            )
                            Text(m.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Sight / Zero ----
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Sight & Zero", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                NumField("Zero Distance (m)", state.zeroDistanceM, "100", { viewModel.update("zeroDistanceM", it) })
                NumField("Sight Height (mm)", state.sightHeightMm, "70", { viewModel.update("sightHeightMm", it) })
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Environment ----
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Environment", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                NumField("Temperature (°C)", state.temperatureC, "15", { viewModel.update("temperatureC", it) })
                NumField("Humidity (%)", state.humidityPct, "50", { viewModel.update("humidityPct", it) })
                NumField("Pressure (kPa, 0=ISA auto)", state.pressureKpa, "0", { viewModel.update("pressureKpa", it) })
                NumField("Altitude (m)", state.muzzleAltitudeM, "0", { viewModel.update("muzzleAltitudeM", it) })
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Wind ----
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Wind", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                NumField("Wind Speed (m/s)", state.windSpeedMps, "0", { viewModel.update("windSpeedMps", it) })
                NumField("Wind Angle (°, 0=tail,90=L,180=head,270=R)", state.windAngleDeg, "0", { viewModel.update("windAngleDeg", it) })
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Target ----
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Target", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                NumField("Target Distance (m)", state.targetDistanceM, "500", { viewModel.update("targetDistanceM", it) })
                NumField("Target Alt. Delta (m, +up)", state.targetAltitudeDeltaM, "0", { viewModel.update("targetAltitudeDeltaM", it) })
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Sensor (max 1) ----
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Data Sensor", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Enter measured impact to generate a 2nd corrected trajectory (orange).\nLow / right can be left blank (blank dimension is not corrected).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (state.sensors.isEmpty()) {
                    Text("No sensor added (predicted line only)", style = MaterialTheme.typography.bodySmall)
                }
                state.sensors.forEach { s ->
                    SensorRow(s, viewModel)
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.addSensor() },
                    enabled = state.sensors.size < 1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add Sensor (max 1)")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.calculate() },
            enabled = !state.calculating,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (state.calculating) {
                CircularProgressIndicator(Modifier.width(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Calculate", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SensorRow(entry: SensorEntry, viewModel: CalcViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NumField("Sensor Distance (m)", entry.distanceM, "200", { viewModel.updateSensor(entry.key, "distance", it) })
                NumField("Measured Low (m, +below LOS)", entry.measuredDropM, "0", { viewModel.updateSensor(entry.key, "drop", it) })
                NumField("Measured Right (m, +right)", entry.measuredWindageM, "0", { viewModel.updateSensor(entry.key, "windage", it) })
            }
            TextButton(onClick = { viewModel.removeSensor(entry.key) }) { Text("Remove") }
        }
    }
}

@Composable
private fun NumField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    numeric: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}
