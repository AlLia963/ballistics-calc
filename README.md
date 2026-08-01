# Ballistics Calculator

A **ballistics calculator for Android** built with Kotlin + Jetpack Compose. It computes projectile impact points using the **G1/G7 drag model** with full atmospheric correction (temperature, humidity, pressure, altitude), wind, and target elevation.

![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![License](https://img.shields.io/badge/License-MIT-green)

---

## Features

| Feature | Description |
|---------|-------------|
| **G1 / G7 drag models** | Standard ballistic coefficient tables (Mach 0–5) |
| **Atmospheric correction** | Temperature, relative humidity, barometric pressure (or ISA auto), altitude |
| **Wind** | Wind speed + angle (0° tail, 90° left, 180° head, 270° right) |
| **Target elevation** | Vertical offset between shooter and target |
| **Custom projectile** | Define your own caliber / mass / muzzle velocity / BC / drag model |
| **No preset ammunition** | The public build ships without preset ammo data; all parameters are entered manually |
| **Data sensor calibration** | Enter one measured impact point to generate a corrected trajectory (orange) |
| **2D ballistics charts** | Vertical (Drop vs Range) + Horizontal (Windage vs Range), with 1-second time markers |
| **Ballistic table** | Range / Drop / Windage / Velocity / Mach / Time / Energy at each point |
| **History** | Save and reopen past calculations with full parameters |

## Screenshots

*(Add screenshots here — e.g. `docs/screenshot_input.png`, `docs/screenshot_result.png`)*

## Ballistics Engine

The core engine is a pure-Kotlin point-mass solver using **4th-order Runge-Kutta** integration:

```
dv/dt = -D/BC · |v_rel| · v_rel + g
D     = 0.5 · ρ · Cd(M) · K      (K = (π/4)/703.07 unit conversion)
M     = |v_rel| / c              (Mach number)
```

- **Drag tables**: G1 and G7 standard drag functions (Mach 0–5, linear interpolation)
- **Atmosphere**: ISA standard atmosphere + Magnus saturation vapor pressure for density (humidity-aware), humidity-corrected speed of sound
- **Wind**: relative-air-velocity correction; crosswind drives lateral drift
- **Aiming**: binary-search for the required elevation angle; separate zero-range solution; "hold-over" drop reporting
- **Out-of-range detection**: if the projectile lands before the target, the app reports the actual impact range instead of pretending to hit the target
- **Performance**: full trajectory solved in < 5 ms on a modern phone (pure Kotlin, no floating-point traps)

### Engine module

The `core` module is a **pure Kotlin JVM library** with **no Android dependencies**, so it can be tested and reused independently. It is covered by JUnit tests in `core/src/test/`.

## Project Structure

```
Ballistics Calculator/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── core/                                # Pure-Kotlin ballistics engine (JVM, no Android deps)
│   └── src/main/kotlin/com/mil/ballistics/core/core/
│       ├── DragTables.kt                # G1/G7 drag tables
│       ├── Atmosphere.kt                # Atmosphere model (density / speed of sound / ISA)
│       ├── BallisticSolver.kt           # RK4 trajectory solver
│       ├── CorrectionModel.kt           # Sensor calibration line
│       └── AmmoDatabase.kt              # Ammo data model (presets empty in public build)
└── app/                                 # Android app (Jetpack Compose)
    └── src/main/java/com/mil/ballistics/app/
        ├── MainActivity.kt
        ├── data/                        # Room persistence / JSON serialization
        └── ui/
            ├── CalcViewModel.kt
            ├── InputScreen.kt           # Parameter form + sensor editor
            ├── ResultScreen.kt          # 2D charts + ballistic table + full params
            ├── HistoryScreen.kt         # Saved calculations
            ├── HistoryDetailScreen.kt
            └── charts/                  # 2D chart drawing
```

## Build

Requirements: **JDK 17+**, Android Studio (or command line).

1. Open the project folder in **Android Studio** (JDK 17).
2. Let Gradle sync (Gradle 8.9 wrapper included).
3. Run the `app` configuration, or build the APK:

```bash
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

A prebuilt debug APK is also available in the [`release/`](release/) folder:

- `release/Ballistics-Calculator-v1.0.0.apk` — signed debug build, Android 8.0+ (API 26+)

### Run engine tests

```bash
./gradlew :core:test
```

## Input Parameters

| Parameter | Unit | Description |
|-----------|------|-------------|
| Zero distance | m | Range at which the trajectory crosses the line of sight |
| Sight height | mm | Distance from sight axis to bore axis |
| Temperature | °C | Ambient temperature |
| Humidity | % | Relative humidity (affects air density) |
| Pressure | kPa | Barometric pressure; 0 = ISA auto by altitude |
| Altitude | m | Shooter altitude |
| Wind speed | m/s | Wind speed |
| Wind angle | ° | 0=tail, 90=left, 180=head, 270=right |
| Target range | m | Horizontal distance to target |
| Target alt. delta | m | Target height relative to shooter (+up) |

## Data Sensor (Calibration)

A data sensor is an optional measured impact point along the trajectory. Entering one produces a **second trajectory line** (orange) calibrated to real-world observations:

- The correction is **anchored at the origin** (shooter), so the corrected line always starts at the firing point.
- **Low / right** fields may be left blank — a blank dimension is not corrected.
- At most **1 sensor** is used (one correction point defines the second reference line).

## License

MIT — see [LICENSE](LICENSE).

## Disclaimer

This public build ships **without any preset projectile data** — all projectile parameters must be entered manually. The engine is general-purpose physics (external ballistics); it does not distribute any specific weapon or ammunition dataset. Users are responsible for entering and validating their own data.
