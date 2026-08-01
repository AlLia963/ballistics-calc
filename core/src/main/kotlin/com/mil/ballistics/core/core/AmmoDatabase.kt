package com.mil.ballistics.core.core

import kotlinx.serialization.Serializable

/**
 * Ammunition database.
 *
 * This public build ships with NO preset ammunition data.
 * All projectile parameters are entered manually via the "Custom" form
 * (caliber, mass, muzzle velocity, ballistic coefficient, drag model).
 *
 * To add presets, populate the [presets] list with [AmmoPreset] entries
 * (see your local archive for the full reference dataset).
 */
@Serializable
data class AmmoPreset(
    val id: String,
    val name: String,
    val caliberMm: Double,
    val bulletMassGrain: Double,
    val muzzleVelocityMps: Double,
    val ballisticCoeff: Double,
    val dragModel: DragModel,
    val note: String = ""
) {
    val energyJ: Double
        get() {
            val kg = bulletMassGrain / 7000.0 * 0.45359237
            return 0.5 * kg * muzzleVelocityMps * muzzleVelocityMps
        }
}

object AmmoDatabase {

    /**
     * Preset ammunition list — intentionally empty in the public build.
     * Add entries here to restore presets.
     */
    val presets: List<AmmoPreset> = emptyList()

    fun byId(id: String): AmmoPreset? = presets.firstOrNull { it.id == id }

    /** Build default BallisticInput from a preset. */
    fun toInput(
        preset: AmmoPreset,
        zeroDistanceM: Double = 100.0,
        sightHeightMm: Double = 70.0,
        targetDistanceM: Double = 300.0
    ): BallisticInput {
        return BallisticInput(
            caliberMm = preset.caliberMm,
            bulletMassGrain = preset.bulletMassGrain,
            muzzleVelocityMps = preset.muzzleVelocityMps,
            ballisticCoeff = preset.ballisticCoeff,
            dragModel = preset.dragModel,
            zeroDistanceM = zeroDistanceM,
            sightHeightMm = sightHeightMm,
            muzzleAltitudeM = 0.0,
            temperatureC = 15.0,
            humidityPct = 50.0,
            pressureKpa = 0.0,   // ISA
            windSpeedMps = 0.0,
            windAngleDeg = 0.0,
            targetDistanceM = targetDistanceM,
            targetAltitudeDeltaM = 0.0
        )
    }
}
