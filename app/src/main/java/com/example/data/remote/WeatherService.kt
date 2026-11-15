package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val current: CurrentWeatherDto? = null,
    val hourly: HourlyWeatherDto? = null,
    val daily: DailyWeatherDto? = null,
)

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    @param:Json(name = "time") val time: String = "",
    @param:Json(name = "temperature_2m") val temperatureC: Double = 0.0,
    @param:Json(name = "apparent_temperature") val apparentTempC: Double? = null,
    @param:Json(name = "relative_humidity_2m") val humidityPct: Int? = null,
    @param:Json(name = "surface_pressure") val pressureHpa: Double = 1013.0,
    @param:Json(name = "wind_speed_10m") val windSpeedKmh: Double = 0.0,
    @param:Json(name = "wind_gusts_10m") val windGustsKmh: Double? = null,
    @param:Json(name = "wind_direction_10m") val windDirectionDeg: Double = 0.0,
    @param:Json(name = "cloud_cover") val cloudCoverPct: Int = 0,
    @param:Json(name = "precipitation") val precipitationMm: Double? = null,
    @param:Json(name = "weather_code") val weatherCode: Int = 0,
)

@JsonClass(generateAdapter = true)
data class HourlyWeatherDto(
    @param:Json(name = "surface_pressure") val pressureSeries: List<Double?> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class DailyWeatherDto(
    @param:Json(name = "sunrise") val sunrise: List<String?> = emptyList(),
    @param:Json(name = "sunset") val sunset: List<String?> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OpenMeteoMarineResponse(
    val current: CurrentMarineDto? = null,
)

@JsonClass(generateAdapter = true)
data class CurrentMarineDto(
    @param:Json(name = "wave_height") val waveHeightM: Double? = null,
    @param:Json(name = "wave_direction") val waveDirectionDeg: Double? = null,
    @param:Json(name = "wave_period") val wavePeriodSec: Double? = null,
    @param:Json(name = "swell_wave_height") val swellHeightM: Double? = null,
    @param:Json(name = "sea_surface_temperature") val seaSurfaceTempC: Double? = null,
)

data class MarineSeaConditions(
    val waveHeightM: Double?,
    val wavePeriodSec: Double?,
    val waveDirectionCardinal: String?,
    val swellHeightM: Double?,
    val seaSurfaceTempC: Double?,
)

data class MarineWeatherSummary(
    val temperatureC: Double,
    val apparentTempC: Double,
    val humidityPct: Int,
    val pressureHpa: Double,
    val pressureTrend: String,
    val windSpeedKmh: Double,
    val windGustsKmh: Double,
    val windCardinal: String,
    val cloudCoverPct: Int,
    val precipitationMm: Double,
    val conditionLabel: String,
    val fishingTip: String,
    val sunriseLabel: String?,
    val sunsetLabel: String?,
    val sunriseHourDecimal: Double?,
    val sunsetHourDecimal: Double?,
    val marine: MarineSeaConditions?,
    val locationLabel: String,
    val latitude: Double,
    val longitude: Double,
    val fetchedAtEpochMs: Long,
    val apiObservationTime: String,
) {
    fun compactSummary(): String {
        val base = "${temperatureC.toInt()}°C · ${pressureHpa.toInt()} hPa · Viento ${windSpeedKmh.toInt()} km/h $windCardinal"
        val wave = marine?.waveHeightM?.let { " · Ola %.1f m".format(it) }.orEmpty()
        return base + wave
    }
}

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String =
            "temperature_2m,apparent_temperature,relative_humidity_2m,surface_pressure,wind_speed_10m,wind_gusts_10m,wind_direction_10m,cloud_cover,precipitation,weather_code",
        @Query("hourly") hourly: String = "surface_pressure",
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoResponse
}

interface OpenMeteoMarineApi {
    @GET("v1/marine")
    suspend fun getMarine(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String =
            "wave_height,wave_direction,wave_period,swell_wave_height,sea_surface_temperature",
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoMarineResponse
}

class WeatherRepository {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache")
                .build()
            chain.proceed(request)
        }
        .build()

    private val forecastApi: OpenMeteoApi = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoApi::class.java)

    private val marineApi: OpenMeteoMarineApi = Retrofit.Builder()
        .baseUrl("https://marine-api.open-meteo.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoMarineApi::class.java)

    suspend fun fetchConditions(
        latitude: Double,
        longitude: Double,
        locationLabel: String,
    ): Result<MarineWeatherSummary> {
        return runCatching {
            coroutineScope {
                val forecastDeferred = async {
                    forecastApi.getForecast(latitude = latitude, longitude = longitude)
                }
                val marineDeferred = async {
                    runCatching {
                        marineApi.getMarine(latitude = latitude, longitude = longitude)
                    }.getOrNull()
                }

                val response = forecastDeferred.await()
                val marineResp = marineDeferred.await()

                val current = response.current ?: error("Sin datos meteorológicos")
                val pressures = response.hourly?.pressureSeries?.filterNotNull().orEmpty()
                val delta = if (pressures.size >= 6) {
                    pressures.last() - pressures[pressures.size - 6]
                } else {
                    0.0
                }
                val trend = when {
                    delta >= 1.2 -> "Subiendo"
                    delta <= -1.2 -> "Bajando"
                    else -> "Estable"
                }

                val sunriseIso = response.daily?.sunrise?.firstOrNull()
                val sunsetIso = response.daily?.sunset?.firstOrNull()

                val marineDto = marineResp?.current
                val hasAnyMarineMetric = marineDto != null && (
                    marineDto.waveHeightM != null ||
                        marineDto.swellHeightM != null ||
                        marineDto.seaSurfaceTempC != null
                    )
                val marineConditions = if (hasAnyMarineMetric && marineDto != null) {
                    MarineSeaConditions(
                        waveHeightM = marineDto.waveHeightM,
                        wavePeriodSec = marineDto.wavePeriodSec,
                        waveDirectionCardinal = marineDto.waveDirectionDeg?.let { degreesToCardinal(it) },
                        swellHeightM = marineDto.swellHeightM,
                        seaSurfaceTempC = marineDto.seaSurfaceTempC,
                    )
                } else {
                    null
                }

                val cardinal = degreesToCardinal(current.windDirectionDeg)
                val condition = weatherCodeToSpanish(current.weatherCode)
                val gusts = current.windGustsKmh ?: current.windSpeedKmh
                val tip = buildFishingTip(
                    pressureHpa = current.pressureHpa,
                    trend = trend,
                    windSpeedKmh = current.windSpeedKmh,
                    cloudCover = current.cloudCoverPct,
                    waveHeightM = marineConditions?.waveHeightM,
                )

                MarineWeatherSummary(
                    temperatureC = current.temperatureC,
                    apparentTempC = current.apparentTempC ?: current.temperatureC,
                    humidityPct = current.humidityPct ?: 0,
                    pressureHpa = current.pressureHpa,
                    pressureTrend = trend,
                    windSpeedKmh = current.windSpeedKmh,
                    windGustsKmh = gusts,
                    windCardinal = cardinal,
                    cloudCoverPct = current.cloudCoverPct,
                    precipitationMm = current.precipitationMm ?: 0.0,
                    conditionLabel = condition,
                    fishingTip = tip,
                    sunriseLabel = extractClockLabel(sunriseIso),
                    sunsetLabel = extractClockLabel(sunsetIso),
                    sunriseHourDecimal = parseIsoHourDecimal(sunriseIso),
                    sunsetHourDecimal = parseIsoHourDecimal(sunsetIso),
                    marine = marineConditions,
                    locationLabel = locationLabel,
                    latitude = latitude,
                    longitude = longitude,
                    fetchedAtEpochMs = System.currentTimeMillis(),
                    apiObservationTime = extractClockLabel(current.time) ?: "",
                )
            }
        }
    }

    private fun extractClockLabel(isoDateTime: String?): String? {
        if (isoDateTime.isNullOrBlank()) return null
        val tIdx = isoDateTime.indexOf('T')
        if (tIdx == -1 || tIdx + 6 > isoDateTime.length) return null
        return isoDateTime.substring(tIdx + 1, tIdx + 6)
    }

    private fun parseIsoHourDecimal(isoDateTime: String?): Double? {
        val clock = extractClockLabel(isoDateTime) ?: return null
        val parts = clock.split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h + (m / 60.0)
    }

    private fun degreesToCardinal(deg: Double): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        val index = (((deg % 360) + 360) % 360 / 45.0).toInt() % 8
        return dirs[index]
    }

    private fun weatherCodeToSpanish(code: Int): String = when (code) {
        0 -> "Despejado"
        1, 2 -> "Poco nuboso"
        3 -> "Nublado"
        45, 48 -> "Niebla costera"
        51, 53, 55, 61, 63, 65 -> "Lluvia"
        80, 81, 82 -> "Chubascos"
        95, 96, 99 -> "Tormenta"
        else -> "Variable"
    }

    private fun buildFishingTip(
        pressureHpa: Double,
        trend: String,
        windSpeedKmh: Double,
        cloudCover: Int,
        waveHeightM: Double?,
    ): String {
        val pressureAdvice = when {
            trend == "Bajando" && pressureHpa in 1005.0..1018.0 ->
                "Presión en descenso: los depredadores suelen activarse antes del cambio."
            pressureHpa >= 1018.0 && trend == "Estable" ->
                "Alta presión estable: pesca más lenta, ideal trabajar fondo o media agua."
            trend == "Subiendo" ->
                "Presión en alza: busca zonas someras o con cobertura tras el frente."
            else ->
                "Presión equilibrada: prueba cambios de ritmo y colores naturales."
        }
        val windOrSeaAdvice = when {
            waveHeightM != null && waveHeightM >= 1.8 ->
                "Oleaje fuerte (%.1f m): extrema precaución en escolleras y usa plomadas pesadas.".format(waveHeightM)
            waveHeightM != null && waveHeightM in 0.5..1.4 ->
                "Mar movido (%.1f m): la espuma oxigena la rompiente y atrae depredadores costeros.".format(waveHeightM)
            windSpeedKmh > 25.0 -> "Viento fuerte: prioriza orillas protegidas y señuelos pesados."
            windSpeedKmh in 10.0..25.0 -> "Brisa favorable que oxigena la superficie y disimula la línea."
            cloudCover < 25 -> "Cielo muy despejado: apuesta por tonos traslúcidos o naturales."
            else -> "Buena visibilidad bajo el agua para señuelos de vibración."
        }
        return "$pressureAdvice $windOrSeaAdvice"
    }
}
