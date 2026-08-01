package com.mil.ballistics.core.core

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Ballistic input model. Unit conventions:
 *  - distance/altitude-delta/muzzle-height: m
 *  - velocity: m/s
 *  - wind: horizontal m/s. Wind angle: 0=tail, 90=left, 180=head, 270=right
 *  - temperature C / relative humidity % / pressure kPa (<=0 uses ISA auto by altitude)
 *  - drop: vertical offset relative to line of sight (positive = below LOS, i.e. needs holdover)
 *  - windage: positive = projectile drifts right (shooter's perspective toward target)
 */
@Serializable
data class BallisticInput(
    val caliberMm: Double,
    val bulletMassGrain: Double,
    val muzzleVelocityMps: Double,
    val ballisticCoeff: Double,
    val dragModel: DragModel,
    val zeroDistanceM: Double,
    val sightHeightMm: Double,
    val muzzleAltitudeM: Double,
    val temperatureC: Double,
    val humidityPct: Double,
    val pressureKpa: Double,
    val windSpeedMps: Double,
    val windAngleDeg: Double,
    val targetDistanceM: Double,
    val targetAltitudeDeltaM: Double
) {
    /** Crosswind component (air moving along +z / rightward): positive when wind comes from the left. */
    val crosswindMps: Double
        get() = windSpeedMps * sin(windAngleDeg * PI / 180.0)

    /** Headwind component (positive = tailwind, air moving along +x). */
    val headwindMps: Double
        get() = -windSpeedMps * cos(windAngleDeg * PI / 180.0)

    val sightHeightM: Double get() = sightHeightMm / 1000.0
}

@Serializable
enum class DragModel(val id: String, val label: String) {
    G1("g1", "G1 drag model"),
    G7("g7", "G7 drag model")
}

/** Ballistic table entry (for display / save / plotting). */
@Serializable
data class BallisticRow(
    val rangeM: Double,
    val dropM: Double,
    val windageM: Double,
    val velocityMps: Double,
    val mach: Double,
    val flightTimeS: Double,
    val energyJ: Double,
    val elevationDeg: Double
)

/** Complete ballistics solution result. */
@Serializable
data class BallisticResult(
    val input: BallisticInput,
    val rows: List<BallisticRow>,
    val maxRangeM: Double,
    val flightTimeToTargetS: Double,
    val impactDropM: Double,
    val impactWindageM: Double,
    val impactVelocityMps: Double,
    val impactEnergyJ: Double,
    val airDensity: Double,
    val speedOfSound: Double,
    val solvedElevationDeg: Double,
    val zeroAngleRad: Double,
    val trajectoryPoints: List<BallisticRow>,
    /** Vertical impact offset at target when aiming directly with zeroed sights (positive = high). */
    val zeroHoldImpactDropM: Double,
    /** Lateral impact offset at target when aiming directly with zeroed sights (positive = right). */
    val zeroHoldImpactWindageM: Double,
    /** Whether the projectile reaches the target range. false = it lands early; impact is rows.last(). */
    val hitsTarget: Boolean = true
)

/**
 * Ballistics solver: point-mass model, fixed-step 4th-order Runge-Kutta integration.
 *
 * Coordinates: X=horizontal toward target, Y=up, Z=lateral (right positive).
 * Drag opposes projectile-air relative velocity, driving drop and crosswind drift.
 *
 * G1/G7 BC definition (classic US): BC = m / (d^2 * i) [lb/in^2], drag acceleration
 *     a_drag = 0.5 · ρ · Cd_table(M) · |v_rel|² · K / BC
 * where K = (pi/4) / 703.07 ~ 0.001117 is the US-to-SI unit conversion constant,
 * and Cd_table(M) is the G1/G7 standard drag function value.
 */
class BallisticSolver {

    companion object {
        const val GRAV = 9.80665
        // K = (π/4) / 703.0696 (0.453592 kg/lb / 0.0254² m²/in²)
        private const val DRAG_CONST = 0.001117
        private const val STEP = 0.005  // integration step 5 ms
    }

    private class State {
        var x = 0.0; var y = 0.0; var z = 0.0
        var vx = 0.0; var vy = 0.0; var vz = 0.0
        var t = 0.0
    }

    private data class AtmosphereData(val rho: Double, val sos: Double)

    private fun atmosphereFor(input: BallisticInput): AtmosphereData {
        val p = Atmosphere.effectivePressureKpa(input.muzzleAltitudeM, input.pressureKpa)
        val rho = Atmosphere.airDensity(input.temperatureC, p, input.humidityPct)
        val sos = Atmosphere.speedOfSound(input.temperatureC, input.humidityPct)
        return AtmosphereData(rho, sos)
    }

    private fun dragTable(input: BallisticInput) =
        if (input.dragModel == DragModel.G1) DragTables.G1 else DragTables.G7

    private fun derivatives(input: BallisticInput, atm: AtmosphereData, st: State, out: DoubleArray) {
        // air velocity: awx = -headwindMps (tail positive), awz = +crosswindMps
        // projectile-relative air velocity vrel = v - aw
        //  headwindMps>0 means headwind, awx<0, vrel.x = vx - awx = vx + headwind
        val vxRel = st.vx + input.headwindMps
        val vyRel = st.vy
        val vzRel = st.vz - input.crosswindMps
        val speedRelSq = vxRel * vxRel + vyRel * vyRel + vzRel * vzRel
        val speedRel = sqrt(speedRelSq)

        val mach = if (atm.sos > 0.0) speedRel / atm.sos else 0.0
        val cd = DragTables.lookup(dragTable(input), mach)

        // a_drag = 0.5 * rho * Cd(M) * v_rel^2 * DRAG_CONST / BC, opposite to v_rel
        val scale = 0.5 * atm.rho * cd * DRAG_CONST / input.ballisticCoeff

        out[0] = st.vx
        out[1] = st.vy
        out[2] = st.vz
        out[3] = -scale * speedRel * vxRel
        out[4] = -scale * speedRel * vyRel - GRAV
        out[5] = -scale * speedRel * vzRel
        out[6] = 1.0
    }

    private fun copyInto(dst: State, src: State, k: DoubleArray, h: Double) {
        dst.x = src.x + h * k[0]; dst.y = src.y + h * k[1]; dst.z = src.z + h * k[2]
        dst.vx = src.vx + h * k[3]; dst.vy = src.vy + h * k[4]; dst.vz = src.vz + h * k[5]
        dst.t = src.t + h * k[6]
    }

    /**
     * Launches from muzzle (height 0) at given elevation, integrates to horizontal range targetX, returns state and arrival height.
     * @param stopAtGround true stops at ground impact (for impact/max range); false continues to targetX
     *                     (for drop tables: extrapolate trajectory height even after ground impact).
     */
    private fun flyToDistance(
        input: BallisticInput,
        atm: AtmosphereData,
        elevRad: Double,
        targetX: Double,
        stopAtGround: Boolean = true
    ): State {
        val st = State()
        st.vx = input.muzzleVelocityMps * cos(elevRad)
        st.vy = input.muzzleVelocityMps * sin(elevRad)
        val k = DoubleArray(7)
        var guard = 0
        while (guard < 2000000 && st.x < targetX &&
            !(stopAtGround && st.y < 0.0 && st.vy < 0.0)) {
            val k1 = DoubleArray(7); derivatives(input, atm, st, k1)
            val s2 = State().also { copyInto(it, st, k1, STEP / 2) }
            val k2 = DoubleArray(7); derivatives(input, atm, s2, k2)
            val s3 = State().also { copyInto(it, st, k2, STEP / 2) }
            val k3 = DoubleArray(7); derivatives(input, atm, s3, k3)
            val s4 = State().also { copyInto(it, st, k3, STEP) }
            val k4 = DoubleArray(7); derivatives(input, atm, s4, k4)
            for (i in 0 until 7) k[i] = (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]) / 6.0
            st.x += STEP * k[0]; st.y += STEP * k[1]; st.z += STEP * k[2]
            st.vx += STEP * k[3]; st.vy += STEP * k[4]; st.vz += STEP * k[5]
            st.t += STEP * k[6]
            guard++
        }
        return st
    }

    /**
     * Binary search for elevation satisfying height = targetY at horizontal range x.
     */
    private fun solveElevation(
        input: BallisticInput,
        atm: AtmosphereData,
        x: Double,
        targetY: Double,
        loHint: Double,
        hiHint: Double
    ): Double {
        var lo = loHint
        var hi = hiHint
        repeat(120) {
            val mid = (lo + hi) / 2.0
            val st = flyToDistance(input, atm, mid, x)
            // height at x below targetY -> insufficient elevation -> increase
            if (st.y < targetY || (st.x < x - 1e-9 && st.y < 0.0)) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    private fun heightAt(input: BallisticInput, atm: AtmosphereData, elev: Double, x: Double): Double =
        flyToDistance(input, atm, elev, x).y

    /**
     * Public helper: launch at given elevation (rad) from muzzle, returns trajectory height at horizontal range x.
     * For verification and UI plotting.
     */
    fun flyHeightAt(
        elevationRad: Double,
        x: Double,
        input: BallisticInput
    ): Double {
        val atm = atmosphereFor(input)
        return flyToDistance(input, atm, elevationRad, x).y
    }

    fun solve(input: BallisticInput): BallisticResult {
        val atm = atmosphereFor(input)
        val D = input.targetDistanceM
        val dH = input.targetAltitudeDeltaM
        val Z = input.zeroDistanceM
        val sh = input.sightHeightM

        // 1) Zero angle: trajectory returns to muzzle plane (height 0) at Z (crosses ground zero point).
        val zeroElev = solveElevation(input, atm, Z, 0.0, -0.2, 0.5)

        // 2) Aim elevation: trajectory height = dH at D (hits target point).
        var aimElev = solveElevation(input, atm, D, dH, zeroElev - 0.3, zeroElev + 0.3)
        if (aimElev !in -0.5..0.9) {
            aimElev = solveElevation(input, atm, D, dH, -0.5, 0.9)
        }

        // 3) Main trajectory: launched at aim elevation. drop relative to target LOS (sight(0,sh)->target(D,dH)).
        val trajectory = computeTrajectory(input, atm, aimElev, D, dH)
        val rows = buildRows(input, atm, trajectory, aimElev)

        val impact = rows.last()
        // whether projectile actually reaches target range: last trajectory x should approach D
        // (computeTrajectory stops early when y<0 on ground impact, then last.x < D)
        val actualReach = trajectory.last().rangeM
        val hitsTarget = actualReach >= D - 1.0   // tolerance 1m
        // effective range = min(target range, actual reach)
        val effectiveRange = if (hitsTarget) D else actualReach

        // 4) Zero-hold trajectory: shooter aims zeroed sights directly at target, barrel follows zero offset.
        //    LOS at (D,dH), barrel elevation = LOS angle + zero offset.
        val alphaLos = atan2(dH - sh, D)
        val sightBarrelOffset = zeroElev - atan2(0.0 - sh, Z)   // LOS angle (negative) minus zero angle
        val thetaZeroHold = alphaLos + sightBarrelOffset
        val zt = flyToDistance(input, atm, thetaZeroHold, D, stopAtGround = false)
        // impact offset relative to aim point (aim point = target (D, sh+alphaLos*D=dH))
        val zeroHoldDrop = zt.y - dH
        val zeroHoldWindage = zt.z

        return BallisticResult(
            input = input,
            rows = rows,
            maxRangeM = effectiveRange,
            flightTimeToTargetS = impact.flightTimeS,
            impactDropM = impact.dropM,
            impactWindageM = impact.windageM,
            impactVelocityMps = impact.velocityMps,
            impactEnergyJ = impact.energyJ,
            airDensity = atm.rho,
            speedOfSound = atm.sos,
            solvedElevationDeg = aimElev * 180.0 / PI,
            zeroAngleRad = zeroElev,
            trajectoryPoints = trajectory,
            zeroHoldImpactDropM = zeroHoldDrop,
            zeroHoldImpactWindageM = zeroHoldWindage,
            hitsTarget = hitsTarget
        )
    }

    private fun computeTrajectory(
        input: BallisticInput,
        atm: AtmosphereData,
        elev: Double,
        targetDist: Double,
        dH: Double
    ): List<BallisticRow> {
        val rows = ArrayList<BallisticRow>()
        val st = State()
        st.vx = input.muzzleVelocityMps * cos(elev)
        st.vy = input.muzzleVelocityMps * sin(elev)
        // line of sight: sight (0, sightHeight) -> target (D, dH)
        val losSlope = (dH - input.sightHeightM) / targetDist
        val massKg = input.bulletMassGrain / 7000.0 * 0.45359237
        val k = DoubleArray(7)
        rows.add(makeRow(input, atm, st, losSlope, massKg))
        var guard = 0
        while (guard < 2000000 && st.x < targetDist && !(st.y < 0.0 && st.vy < 0.0)) {
            val k1 = DoubleArray(7); derivatives(input, atm, st, k1)
            val s2 = State().also { copyInto(it, st, k1, STEP / 2) }
            val k2 = DoubleArray(7); derivatives(input, atm, s2, k2)
            val s3 = State().also { copyInto(it, st, k2, STEP / 2) }
            val k3 = DoubleArray(7); derivatives(input, atm, s3, k3)
            val s4 = State().also { copyInto(it, st, k3, STEP) }
            val k4 = DoubleArray(7); derivatives(input, atm, s4, k4)
            for (i in 0 until 7) k[i] = (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]) / 6.0
            st.x += STEP * k[0]; st.y += STEP * k[1]; st.z += STEP * k[2]
            st.vx += STEP * k[3]; st.vy += STEP * k[4]; st.vz += STEP * k[5]
            st.t += STEP * k[6]
            rows.add(makeRow(input, atm, st, losSlope, massKg))
            guard++
        }
        return rows
    }

    private fun makeRow(
        input: BallisticInput,
        atm: AtmosphereData,
        st: State,
        losSlope: Double,
        massKg: Double
    ): BallisticRow {
        val speed = sqrt(st.vx * st.vx + st.vy * st.vy + st.vz * st.vz)
        val mach = if (atm.sos > 0.0) speed / atm.sos else 0.0
        val drop = st.y - (input.sightHeightM + losSlope * st.x)
        val energy = 0.5 * massKg * speed * speed
        return BallisticRow(
            rangeM = st.x,
            dropM = drop,
            windageM = st.z,
            velocityMps = speed,
            mach = mach,
            flightTimeS = st.t,
            energyJ = energy,
            elevationDeg = atan2(st.vy, st.vx) * 180.0 / PI
        )
    }

    private fun buildRows(
        input: BallisticInput,
        atm: AtmosphereData,
        trajectory: List<BallisticRow>,
        elevRad: Double
    ): List<BallisticRow> {
        // trajectory sampled at 5 ms (dense enough for plotting); decimate table to ~every 10-20 m
        val targetDist = input.targetDistanceM
        val step = if (targetDist > 800.0) 20.0 else 10.0
        val sampled = ArrayList<BallisticRow>()
        var next = 0.0
        for (row in trajectory) {
            if (row.rangeM >= next) {
                sampled.add(row)
                next += step
            }
        }
        // ensure a row at the target/end
        val last = trajectory.last()
        if (sampled.isEmpty() || sampled.last().rangeM < last.rangeM - 1e-9) {
            sampled.add(last)
        }
        return sampled
    }
}
