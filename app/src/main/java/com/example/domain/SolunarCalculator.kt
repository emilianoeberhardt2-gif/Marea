package com.example.domain

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

data class SolunarWindow(
    val title: String,
    val isMajor: Boolean,
    val startHour: Double,
    val endHour: Double,
) {
    fun formattedRange(): String = "${formatHour(startHour)} – ${formatHour(endHour)}"

    private fun formatHour(hour: Double): String {
        val wrapped = ((hour % 24.0) + 24.0) % 24.0
        val h = wrapped.toInt()
        val m = (((wrapped - h) * 60.0).roundToInt()) % 60
        return "%02d:%02d".format(h, m)
    }
}

data class SolunarForecast(
    val moonPhaseName: String,
    val illuminationPercent: Int,
    val moonAgeDays: Double,
    val activityScore: Int,
    val activityLabel: String,
    val windows: List<SolunarWindow>,
    val hourlyActivity: List<Float>,
    val currentHourDecimal: Double,
)

object SolunarCalculator {
    private const val SYNODIC_MONTH = 29.53058867
    // Known New Moon reference: 2000-01-06 18:14 UTC in epoch ms = 947182440000L
    private const val NEW_MOON_EPOCH_MS = 947182440000L

    fun calculate(
        nowMs: Long = System.currentTimeMillis(),
        longitudeDeg: Double = -57.54,
        sunriseHour: Double = 6.5,
        sunsetHour: Double = 19.0,
    ): SolunarForecast {
        val daysSinceRef = (nowMs - NEW_MOON_EPOCH_MS) / 86_400_000.0
        val moonAge = ((daysSinceRef % SYNODIC_MONTH) + SYNODIC_MONTH) % SYNODIC_MONTH
        val phaseAngle = (moonAge / SYNODIC_MONTH) * 2.0 * PI
        val illumination = ((1.0 - cos(phaseAngle)) * 50.0).roundToInt().coerceIn(0, 100)

        val phaseName = when {
            moonAge < 1.84 -> "Luna Nueva"
            moonAge < 5.53 -> "Creciente Iluminante"
            moonAge < 9.22 -> "Cuarto Creciente"
            moonAge < 12.91 -> "Gibosa Creciente"
            moonAge < 16.61 -> "Luna Llena"
            moonAge < 20.30 -> "Gibosa Menguante"
            moonAge < 23.99 -> "Cuarto Menguante"
            moonAge < 27.68 -> "Menguante"
            else -> "Luna Nueva"
        }

        // Approximate lunar transit hour locally based on moon age (~50 min later per day)
        // New moon transits near local noon (12.0), Full moon near midnight (0.0 / 24.0)
        val lonOffsetHours = (longitudeDeg % 15.0) / 15.0
        val transit1 = ((12.0 + (moonAge / SYNODIC_MONTH) * 24.0 - lonOffsetHours) % 24.0 + 24.0) % 24.0
        val transit2 = (transit1 + 12.42) % 24.0
        val minor1 = (transit1 + 6.21) % 24.0
        val minor2 = (transit1 + 18.63) % 24.0

        val windows = listOf(
            SolunarWindow("Período Mayor I", true, transit1 - 1.0, transit1 + 1.0),
            SolunarWindow("Período Mayor II", true, transit2 - 1.0, transit2 + 1.0),
            SolunarWindow("Período Menor I", false, minor1 - 0.5, minor1 + 0.5),
            SolunarWindow("Período Menor II", false, minor2 - 0.5, minor2 + 0.5),
        ).sortedBy { ((it.startHour % 24.0) + 24.0) % 24.0 }

        // Syzygy bonus (highest around New & Full moon)
        val syzygyStrength = abs(cos(phaseAngle))
        val baseScore = (62 + (syzygyStrength * 33)).roundToInt().coerceIn(50, 96)
        val label = when {
            baseScore >= 86 -> "Actividad Óptima"
            baseScore >= 75 -> "Pique Alto"
            baseScore >= 65 -> "Buena Actividad"
            else -> "Actividad Moderada"
        }

        val hourly = (0..23).map { hour ->
            val h = hour.toDouble()
            val dawnBoost = 0.25 * gaussianHourDist(h, sunriseHour, 1.8)
            val duskBoost = 0.28 * gaussianHourDist(h, sunsetHour, 1.8)
            val majorBoost = 0.52 * maxOf(
                gaussianHourDist(h, transit1, 1.3),
                gaussianHourDist(h, transit2, 1.3)
            )
            val minorBoost = 0.30 * maxOf(
                gaussianHourDist(h, minor1, 0.9),
                gaussianHourDist(h, minor2, 0.9)
            )
            val raw = (0.18 + (syzygyStrength * 0.10) + dawnBoost + duskBoost + majorBoost + minorBoost)
            raw.toFloat().coerceIn(0.12f, 1.0f)
        }

        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = nowMs }
        val currentHourDec = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0

        return SolunarForecast(
            moonPhaseName = phaseName,
            illuminationPercent = illumination,
            moonAgeDays = moonAge,
            activityScore = baseScore,
            activityLabel = label,
            windows = windows,
            hourlyActivity = hourly,
            currentHourDecimal = currentHourDec,
        )
    }

    private fun gaussianHourDist(h: Double, center: Double, sigma: Double): Double {
        val diff = abs(h - center)
        val wrappedDiff = minOf(diff, 24.0 - diff)
        return exp(-(wrappedDiff * wrappedDiff) / (2.0 * sigma * sigma))
    }
}
