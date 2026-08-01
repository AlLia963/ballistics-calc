package com.mil.ballistics.core

import com.mil.ballistics.core.core.Atmosphere
import com.mil.ballistics.core.core.BallisticInput
import com.mil.ballistics.core.core.BallisticResult
import com.mil.ballistics.core.core.BallisticRow
import com.mil.ballistics.core.core.BallisticSolver
import com.mil.ballistics.core.core.CorrectionModel
import com.mil.ballistics.core.core.DataSensor
import com.mil.ballistics.core.core.DragModel
import com.mil.ballistics.core.core.DragTables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Ballistics engine verification.
 * Run with: ./gradlew :core:test
 *
 * Covers:
 *   - Atmosphere (density / speed of sound / ISA pressure / humidity effect)
 *   - Drag tables (classic G1 Cd@M2.0)
 *   - typical high-velocity projectile trajectory (elevation / flight time / impact velocity)
 *   - Zeroing, crosswind, headwind, altitude delta, altitude correction direction
 *   - Sensor calibration line (single / multi-point, anchored at origin)
 *
 * Uses a synthetic reference projectile directly (no preset database dependency),
 * since the public build ships without preset ammunition.
 */
class BallisticCoreTest {

    /** Synthetic reference projectile (small-caliber high-velocity). */
    private fun referenceInput(targetDistanceM: Double = 500.0): BallisticInput = BallisticInput(
        caliberMm = 5.56,
        bulletMassGrain = 62.0,
        muzzleVelocityMps = 945.0,
        ballisticCoeff = 0.307,
        dragModel = DragModel.G1,
        zeroDistanceM = 100.0,
        sightHeightMm = 70.0,
        muzzleAltitudeM = 0.0,
        temperatureC = 15.0,
        humidityPct = 50.0,
        pressureKpa = 0.0,
        windSpeedMps = 0.0,
        windAngleDeg = 0.0,
        targetDistanceM = targetDistanceM,
        targetAltitudeDeltaM = 0.0
    )

    @Test
    fun `atmosphere standard values`() {
        val rho = Atmosphere.airDensity(15.0, 101.325, 0.0)
        assertEquals(1.225, rho, 0.02)

        val sos = Atmosphere.speedOfSound(15.0, 0.0)
        assertEquals(340.3, sos, 2.0)

        val p3k = Atmosphere.standardPressureKpa(3000.0)
        assertTrue("ISA 3000m pressure should be 60-75 kPa, got $p3k", p3k in 60.0..75.0)
    }

    @Test
    fun `humidity reduces air density`() {
        val dry = Atmosphere.airDensity(30.0, 101.325, 0.0)
        val humid = Atmosphere.airDensity(30.0, 101.325, 90.0)
        assertTrue("higher humidity should reduce density", humid < dry)
    }

    @Test
    fun `drag table G1 sanity`() {
        val cd = DragTables.lookup(DragTables.G1, 2.0)
        assertEquals(0.591, cd, 0.02)
        assertEquals(0.1571, DragTables.lookup(DragTables.G1, 0.0), 0.005)
    }

    @Test
    fun `reference 500m typical trajectory`() {
        val r = BallisticSolver().solve(referenceInput(500.0))

        // Elevation: reference projectile at 500m typical 0.2~0.35°
        assertTrue("elevation should be 0.15~0.5°, got ${r.solvedElevationDeg}", r.solvedElevationDeg in 0.15..0.5)
        // Flight time: typical 0.6~0.85s
        assertTrue("flight time should be 0.55~0.9s, got ${r.flightTimeToTargetS}", r.flightTimeToTargetS in 0.55..0.9)
        // Impact velocity: typical 480~560 m/s
        assertTrue("impact velocity should be 450~580, got ${r.impactVelocityMps}", r.impactVelocityMps in 450.0..580.0)
        // Aimed trajectory: impact drop ≈ 0 at target
        assertTrue("impact drop should be ≈0, got ${r.impactDropM}", abs(r.impactDropM) < 0.05)
    }

    @Test
    fun `out of range detection`() {
        val r = BallisticSolver().solve(referenceInput(30000.0))
        assertTrue("30000m is out of range, hitsTarget should be false", !r.hitsTarget)
        assertTrue("maxRange should equal actual impact, got ${r.maxRangeM}", r.maxRangeM == r.rows.last().rangeM)
    }

    @Test
    fun `zeroing correct`() {
        val input = referenceInput(500.0)
        val r = BallisticSolver().solve(input)
        val h100 = BallisticSolver().flyHeightAt(r.zeroAngleRad, 100.0, input)
        assertTrue("100m height should be ≈0, got $h100", abs(h100) < 0.05)
    }

    @Test
    fun `crosswind drift positive`() {
        val input = referenceInput(500.0).copy(windSpeedMps = 5.0, windAngleDeg = 90.0) // left crosswind
        val r = BallisticSolver().solve(input)
        assertTrue("left crosswind should drift right (>0.1), got ${r.impactWindageM}", r.impactWindageM > 0.1)
    }

    @Test
    fun `headwind increases elevation`() {
        val base = referenceInput(500.0)
        val noWind = BallisticSolver().solve(base).solvedElevationDeg
        val headwind = BallisticSolver().solve(base.copy(windSpeedMps = 10.0, windAngleDeg = 180.0)).solvedElevationDeg
        assertTrue("headwind should increase elevation", headwind > noWind)
    }

    @Test
    fun `altitude delta adjusts elevation`() {
        val base = referenceInput(500.0)
        val flat = BallisticSolver().solve(base).solvedElevationDeg
        val up = BallisticSolver().solve(base.copy(targetAltitudeDeltaM = 50.0)).solvedElevationDeg
        val down = BallisticSolver().solve(base.copy(targetAltitudeDeltaM = -50.0)).solvedElevationDeg
        assertTrue("target higher should increase elevation", up > flat)
        assertTrue("target lower should decrease elevation", down < flat)
    }

    @Test
    fun `high altitude reduces elevation`() {
        val base = referenceInput(500.0)
        val sea = BallisticSolver().solve(base).solvedElevationDeg
        val high = BallisticSolver().solve(base.copy(muzzleAltitudeM = 3000.0)).solvedElevationDeg
        assertTrue("thin air at high altitude should reduce elevation", high < sea)
    }

    // ---- helper: build a fake result with drop(x)=0.001x for correction tests ----

    private fun fakeResult(): BallisticResult {
        val fakeRows = (0..50).map { i ->
            val x = i * 10.0
            BallisticRow(
                rangeM = x, dropM = 0.001 * x, windageM = 0.0,
                velocityMps = 800.0, mach = 2.35, flightTimeS = x / 800.0,
                energyJ = 1800.0, elevationDeg = 0.0
            )
        }
        val fakeInput = referenceInput(500.0)
        return BallisticResult(
            input = fakeInput, rows = fakeRows, maxRangeM = 500.0,
            flightTimeToTargetS = 0.6, impactDropM = 0.5, impactWindageM = 0.0,
            impactVelocityMps = 800.0, impactEnergyJ = 1800.0,
            airDensity = 1.225, speedOfSound = 340.0,
            solvedElevationDeg = 0.0, zeroAngleRad = 0.0,
            trajectoryPoints = fakeRows, zeroHoldImpactDropM = 0.0, zeroHoldImpactWindageM = 0.0
        )
    }

    @Test
    fun `correction single point shifts trajectory`() {
        // Sensor at 400m measures drop 0.5 (predicted 0.4, delta +0.1)
        val s1 = DataSensor(400.0, measuredDropM = 0.5, measuredWindageM = 0.0)
        val c = CorrectionModel.buildCorrection(listOf(s1), fakeResult(), 500.0)
        // Anchored at origin: 0→400m(+0.1), holds after. Predicted 0.5 + 0.1 = 0.6
        assertEquals(0.6, c.adjustedImpactDropM, 0.05)
    }

    @Test
    fun `correction multi point`() {
        val s1 = DataSensor(300.0, measuredDropM = 0.35, measuredWindageM = 0.0)
        val s2 = DataSensor(400.0, measuredDropM = 0.5, measuredWindageM = 0.0)
        val c = CorrectionModel.buildCorrection(listOf(s1, s2), fakeResult(), 500.0)
        // Anchored at origin: 0→300m(+0.05)→400m(+0.1), holds. Predicted 0.5 + 0.1 = 0.6
        assertEquals(0.6, c.adjustedImpactDropM, 0.05)

        // Single-point also anchored: 300m delta +0.05, holds => 0.5+0.05=0.55
        val c1 = CorrectionModel.buildCorrection(listOf(s1), fakeResult(), 500.0)
        assertEquals(0.55, c1.adjustedImpactDropM, 0.05)
    }
}
