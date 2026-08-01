package com.mil.ballistics.core.core

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Atmosphere model: computes air density, speed of sound, and the resulting
 * pressure-density correction factor (sqrt(rho/rho0)), used in trajectory integration.
 *
 * Unit conventions:
 *   altitudeM   altitude (m)
 *   temperatureC temperature (Celsius)
 *   humidityPct  relative humidity (0-100)
 *   pressureKpa  barometric pressure (kPa), <=0 means estimate from altitude via ISA
 *
 * Standard conditions: sea level 15 C / 101.325 kPa / 0% RH / rho0 = 1.225 kg/m^3
 * Sound speed standard: sea level 15 C -> 340.294 m/s
 */
object Atmosphere {

    const val RHO_STD = 1.225      // kg/m^3 standard sea-level air density
    const val P_STD_KPA = 101.325  // standard sea-level pressure kPa
    const val T_STD_K = 288.15     // 15°C
    const val SOS_STD = 340.294    // m/s

    private const val R = 287.058        // J/(kg K) dry air gas constant
    private const val GRAV = 9.80665     // m/s^2
    private const val LAPSE = 0.0065     // K/m standard temperature lapse rate
    private const val TROPOPAUSE = 11000.0
    private const val TROPOPAUSE_T_K = 216.65
    private const val SVP_A = 6.1121     // hPa
    private const val SVP_M = 17.62
    private const val SVP_TN = 243.12
    private const val MW_RATIO = 0.622   // molar-mass ratio water vapor / dry air (approx 18.015/28.964)

    /** Standard atmosphere temperature (K) vs altitude: linear lapse to tropopause, then isothermal. */
    fun standardTempK(altitudeM: Double): Double {
        val alt = altitudeM.coerceAtLeast(0.0)
        return if (alt <= TROPOPAUSE) T_STD_K - LAPSE * alt else TROPOPAUSE_T_K
    }

    /** Standard atmosphere pressure (kPa) vs altitude (ISA model). */
    fun standardPressureKpa(altitudeM: Double): Double {
        val alt = altitudeM.coerceAtLeast(0.0)
        return if (alt <= TROPOPAUSE) {
            P_STD_KPA * (1.0 - LAPSE * alt / T_STD_K).pow(9.80665 / (LAPSE * R))
        } else {
            val p0 = P_STD_KPA * (1.0 - LAPSE * TROPOPAUSE / T_STD_K).pow(9.80665 / (LAPSE * R))
            val temp = TROPOPAUSE_T_K
            p0 * exp(-GRAV * (alt - TROPOPAUSE) / (R * temp))
        }
    }

    /** Saturation vapor pressure (kPa), Magnus formula. */
    fun saturationVaporPressureKpa(tempC: Double): Double {
        return SVP_A * exp(SVP_M * tempC / (SVP_TN + tempC)) / 10.0
    }

    /**
     * Compute air density (kg/m^3) considering temperature, pressure, humidity.
     * Uses ICAO standard gas-mixture density formula (with water vapor partial pressure correction).
     */
    fun airDensity(
        temperatureC: Double,
        pressureKpa: Double,
        humidityPct: Double
    ): Double {
        val tK = temperatureC + 273.15
        val pKpa = pressureKpa.coerceAtLeast(0.5)
        val rh = humidityPct.coerceIn(0.0, 100.0) / 100.0
        // water vapor partial pressure
        val eKpa = rh * saturationVaporPressureKpa(temperatureC)
        // dry air partial pressure
        val pdKpa = pKpa - eKpa
        // density = (pd*Md + e*Mw) / (R*T), Md/Mw expressed via MW_RATIO:
        // rho = (pd + e*MW_RATIO) / (R*T) * 1000  (kPa -> Pa)
        return (pdKpa + eKpa * MW_RATIO) * 1000.0 / (R * tK)
    }

    /** Speed of sound (m/s): humidity has small effect; applies humidity correction. */
    fun speedOfSound(temperatureC: Double, humidityPct: Double): Double {
        val tK = temperatureC + 273.15
        // approximate specific-heat ratio for humid air
        val rh = humidityPct.coerceIn(0.0, 100.0) / 100.0
        val gamma = if (rh <= 0.0001) 1.4 else 1.4 - 0.0018 * rh
        val mWater = rh * saturationVaporPressureKpa(temperatureC) / standardPressureKpa(0.0)
        val mAir = 1.0 - mWater * (1.0 - MW_RATIO)
        return sqrt(gamma * R / mAir * tK)
    }

    /** Combined density-ratio square-root correction factor (for BC scaling). */
    fun densityFactor(airDensity: Double): Double = sqrt(airDensity / RHO_STD)

    /** Get standard pressure from altitude; uses ISA when pressureKpa <= 0. */
    fun effectivePressureKpa(altitudeM: Double, pressureKpa: Double): Double {
        return if (pressureKpa > 0.0) pressureKpa else standardPressureKpa(altitudeM)
    }

    /** Total atmosphere correction factor for given conditions: sqrt(rho/rho0). */
    fun atmosphereFactor(
        altitudeM: Double,
        temperatureC: Double,
        humidityPct: Double,
        pressureKpa: Double
    ): Double {
        val p = effectivePressureKpa(altitudeM, pressureKpa)
        val rho = airDensity(temperatureC, p, humidityPct)
        return densityFactor(rho)
    }
}
