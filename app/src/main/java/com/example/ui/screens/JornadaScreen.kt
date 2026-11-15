package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.location.LocationHelper
import com.example.data.model.CatchLog
import com.example.data.model.FishingSpot
import com.example.data.model.TackleItem
import com.example.data.remote.MarineWeatherSummary
import com.example.domain.SolunarForecast
import com.example.ui.theme.BrassGold
import com.example.ui.theme.ReleaseGreen
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos

@Composable
fun JornadaScreen(
    solunar: SolunarForecast,
    weatherState: UiState<MarineWeatherSummary>,
    catchesState: UiState<List<CatchLog>>,
    spotsState: UiState<List<FishingSpot>>,
    tackleState: UiState<List<TackleItem>>,
    isRefreshingWeather: Boolean,
    isAutoRefreshEnabled: Boolean,
    refreshIntervalSeconds: Int,
    secondsUntilNextRefresh: Int,
    lastUpdatedEpochMs: Long?,
    deviceBarometerHpa: Float?,
    onRefreshLocation: (Double, Double, String) -> Unit,
    onRefreshNow: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onSelectRefreshInterval: (Int) -> Unit,
    onQuickAddCatch: () -> Unit,
    onOpenSpeciesIdentifier: () -> Unit,
    onOpenAssistant: () -> Unit = {},
    aiQuestionsRemaining: Int = 5,
    onNavigateToBitacora: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locationHint by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                val coords = LocationHelper.getFreshOrLastCoordinates(context)
                if (coords != null) {
                    locationHint = null
                    onRefreshLocation(coords.first, coords.second, "Tu ubicación GPS")
                } else {
                    locationHint = "Ubicación activada, sin señal GPS reciente. Elige una zona guardada o reintenta."
                }
            }
        } else {
            locationHint = "Permiso de ubicación denegado. Mostrando zona de referencia."
        }
    }

    val catches = (catchesState as? UiState.Success)?.data.orEmpty()
    val spots = (spotsState as? UiState.Success)?.data.orEmpty()
    val tackle = (tackleState as? UiState.Success)?.data.orEmpty()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SolunarHeroCard(
                    solunar = solunar,
                    onQuickAddCatch = onQuickAddCatch,
                    onOpenSpeciesIdentifier = onOpenSpeciesIdentifier,
                    onOpenAssistant = onOpenAssistant,
                    aiQuestionsRemaining = aiQuestionsRemaining,
                )
            }

            item {
                SolunarActivityCurveCard(solunar = solunar)
            }

            item {
                WeatherBarometerSection(
                    weatherState = weatherState,
                    spots = spots,
                    locationHint = locationHint,
                    isRefreshingWeather = isRefreshingWeather,
                    isAutoRefreshEnabled = isAutoRefreshEnabled,
                    refreshIntervalSeconds = refreshIntervalSeconds,
                    secondsUntilNextRefresh = secondsUntilNextRefresh,
                    lastUpdatedEpochMs = lastUpdatedEpochMs,
                    deviceBarometerHpa = deviceBarometerHpa,
                    onUseDeviceGps = {
                        if (LocationHelper.hasLocationPermission(context)) {
                            scope.launch {
                                val coords = LocationHelper.getFreshOrLastCoordinates(context)
                                if (coords != null) {
                                    locationHint = null
                                    onRefreshLocation(coords.first, coords.second, "Tu ubicación GPS")
                                } else {
                                    locationHint = "No hay última ubicación GPS disponible en el dispositivo."
                                }
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    onSelectSpotWeather = { spot ->
                        val lat = spot.latitude
                        val lon = spot.longitude
                        if (lat != null && lon != null) {
                            locationHint = null
                            onRefreshLocation(lat, lon, spot.name)
                        }
                    },
                    onRefreshNow = onRefreshNow,
                    onToggleAutoRefresh = onToggleAutoRefresh,
                    onSelectRefreshInterval = onSelectRefreshInterval,
                )
            }

            item {
                SeasonSummaryCard(
                    catches = catches,
                    spotsCount = spots.size,
                    tackleCount = tackle.size,
                    onOpenBitacora = onNavigateToBitacora,
                )
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun SolunarHeroCard(
    solunar: SolunarForecast,
    onQuickAddCatch: () -> Unit,
    onOpenSpeciesIdentifier: () -> Unit,
    onOpenAssistant: () -> Unit,
    aiQuestionsRemaining: Int,
) {
    val heroGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF072D3E),
            Color(0xFF0B4F6C),
            Color(0xFF1A6D72),
        )
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .background(heroGradient)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PRONÓSTICO SOLUNAR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFBDE9F7),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = solunar.activityLabel,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                        )
                        Text(
                            text = "${solunar.moonPhaseName} · ${solunar.illuminationPercent}% iluminación",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD6EEF5),
                        )
                    }

                    MoonPhaseBadge(
                        moonAgeDays = solunar.moonAgeDays,
                        score = solunar.activityScore,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    solunar.windows.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { window ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (window.isMajor) Color(0x33FFFFFF) else Color(0x1EFFFFFF),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (window.isMajor) Color(0xFFFFB56B) else Color(0xFF7BE3D8)
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = window.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFE6F4F8),
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = window.formattedRange(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onQuickAddCatch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("hoy_add_catch_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD95D39),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Registrar captura ahora",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                OutlinedButton(
                    onClick = onOpenSpeciesIdentifier,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("hoy_identify_species_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Identificar especie por foto",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                OutlinedButton(
                    onClick = onOpenAssistant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("hoy_ai_assistant_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0x66FFD88A)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFFE7B0),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Asistente IA de Pesca ($aiQuestionsRemaining/5 hoy)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoonPhaseBadge(
    moonAgeDays: Double,
    score: Int,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0x28000000),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(modifier = Modifier.size(36.dp)) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = Color(0xFF1A3A4A),
                    radius = r,
                    center = center,
                )
                val phaseFraction = (moonAgeDays / 29.53058867).toFloat()
                val litShift = -cos(phaseFraction * 2f * Math.PI.toFloat())
                drawCircle(
                    color = Color(0xFFFFD88A),
                    radius = r * 0.88f,
                    center = center,
                )
                if (phaseFraction < 0.48f || phaseFraction > 0.52f) {
                    drawCircle(
                        color = Color(0xFF0A2A38),
                        radius = r * 0.86f,
                        center = Offset(
                            x = center.x + (litShift * r * 0.85f),
                            y = center.y,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$score/100",
                style = MaterialTheme.typography.titleMedium,
                color = BrassGold,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "ÍNDICE",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCFE8F0),
            )
        }
    }
}

@Composable
private fun SolunarActivityCurveCard(solunar: SolunarForecast) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Curva de actividad (24h)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Picos de alimentación según tránsito lunar, amanecer y ocaso real",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.NightsStay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val values = solunar.hourlyActivity
                if (values.size < 2) return@Canvas
                val w = size.width
                val h = size.height
                val topPad = 12.dp.toPx()
                val bottomPad = 12.dp.toPx()
                val usableH = h - topPad - bottomPad

                for (i in 0..2) {
                    val y = topPad + (usableH * i / 2f)
                    drawLine(
                        color = gridColor.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    )
                }

                val points = values.mapIndexed { idx, v ->
                    val x = (idx.toFloat() / (values.size - 1)) * w
                    val y = topPad + usableH * (1f - v)
                    Offset(x, y)
                }

                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val midX = (p0.x + p1.x) / 2f
                        cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.30f),
                            primaryColor.copy(alpha = 0.02f),
                        )
                    ),
                )
                drawPath(
                    path = strokePath,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )

                val nowX = ((solunar.currentHourDecimal / 23.0).toFloat().coerceIn(0f, 1f)) * w
                val nowIdx = solunar.currentHourDecimal.toInt().coerceIn(0, values.size - 1)
                val nowY = topPad + usableH * (1f - values[nowIdx])
                drawLine(
                    color = accentColor,
                    start = Offset(nowX, 0f),
                    end = Offset(nowX, h),
                    strokeWidth = 2.dp.toPx(),
                )
                drawCircle(
                    color = accentColor,
                    radius = 5.dp.toPx(),
                    center = Offset(nowX, nowY),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach { hourLabel ->
                    Text(
                        text = hourLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherBarometerSection(
    weatherState: UiState<MarineWeatherSummary>,
    spots: List<FishingSpot>,
    locationHint: String?,
    isRefreshingWeather: Boolean,
    isAutoRefreshEnabled: Boolean,
    refreshIntervalSeconds: Int,
    secondsUntilNextRefresh: Int,
    lastUpdatedEpochMs: Long?,
    deviceBarometerHpa: Float?,
    onUseDeviceGps: () -> Unit,
    onSelectSpotWeather: (FishingSpot) -> Unit,
    onRefreshNow: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onSelectRefreshInterval: (Int) -> Unit,
) {
    val spotsWithCoords = remember(spots) {
        spots.filter { it.latitude != null && it.longitude != null }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Telemetría y Condiciones Reales",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle = when (weatherState) {
                        is UiState.Success -> "${weatherState.data.locationLabel} · ${weatherState.data.conditionLabel}"
                        is UiState.Loading -> "Consultando parte real en línea..."
                        is UiState.Error -> "Sin datos en línea"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRefreshNow,
                    modifier = Modifier.testTag("refresh_weather_now_button"),
                ) {
                    if (isRefreshingWeather) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Actualizar datos ahora",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(
                    onClick = onUseDeviceGps,
                    modifier = Modifier.testTag("gps_weather_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Usar mi ubicación GPS",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            LiveTelemetryBar(
                isAutoRefreshEnabled = isAutoRefreshEnabled,
                isRefreshing = isRefreshingWeather,
                refreshIntervalSeconds = refreshIntervalSeconds,
                secondsUntilNextRefresh = secondsUntilNextRefresh,
                lastUpdatedEpochMs = lastUpdatedEpochMs,
                onToggleAutoRefresh = onToggleAutoRefresh,
                onSelectRefreshInterval = onSelectRefreshInterval,
            )

            if (spotsWithCoords.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    spotsWithCoords.forEach { spot ->
                        val isSelected = (weatherState as? UiState.Success)?.data?.locationLabel == spot.name
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectSpotWeather(spot) },
                            label = { Text(spot.name, maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Place,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }

            if (locationHint != null) {
                Text(
                    text = locationHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            when (weatherState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                is UiState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = weatherState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onRefreshNow) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reintentar")
                        }
                    }
                }
                is UiState.Success -> {
                    val w = weatherState.data
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        WeatherMetricTile(
                            icon = Icons.Rounded.Speed,
                            label = "Presión",
                            value = "${w.pressureHpa.toInt()} hPa",
                            extra = if (deviceBarometerHpa != null) {
                                "${w.pressureTrend} · Sensor ${deviceBarometerHpa.toInt()} hPa"
                            } else {
                                w.pressureTrend
                            },
                            trendIcon = when (w.pressureTrend) {
                                "Subiendo" -> Icons.AutoMirrored.Rounded.TrendingUp
                                "Bajando" -> Icons.AutoMirrored.Rounded.TrendingDown
                                else -> Icons.AutoMirrored.Rounded.TrendingFlat
                            },
                            modifier = Modifier.weight(1f),
                        )
                        WeatherMetricTile(
                            icon = Icons.Rounded.Air,
                            label = "Viento",
                            value = "${w.windSpeedKmh.toInt()} km/h ${w.windCardinal}",
                            extra = "Rachas ${w.windGustsKmh.toInt()} km/h",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        WeatherMetricTile(
                            icon = Icons.Rounded.Thermostat,
                            label = "Temperatura",
                            value = "${"%.1f".format(w.temperatureC)} °C",
                            extra = "Sensación ${w.apparentTempC.toInt()} °C",
                            modifier = Modifier.weight(1f),
                        )
                        WeatherMetricTile(
                            icon = Icons.Rounded.WaterDrop,
                            label = "Humedad / Lluvia",
                            value = "${w.humidityPct}% · ${w.cloudCoverPct}% nub.",
                            extra = if (w.precipitationMm > 0.0) {
                                "%.1f mm/h".format(w.precipitationMm)
                            } else {
                                "Sin precipitaciones"
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (w.sunriseLabel != null && w.sunsetLabel != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WbSunny,
                                        contentDescription = null,
                                        tint = BrassGold,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Amanecer ${w.sunriseLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WbTwilight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ocaso ${w.sunsetLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }

                    w.marine?.let { marine ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Waves,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Estado del mar en tiempo real",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    marine.waveHeightM?.let { waveM ->
                                        WeatherMetricTile(
                                            icon = Icons.Rounded.Waves,
                                            label = "Oleaje",
                                            value = "%.2f m".format(waveM),
                                            extra = buildString {
                                                marine.wavePeriodSec?.let { append("Período %.1f s".format(it)) }
                                                marine.waveDirectionCardinal?.let {
                                                    if (isNotEmpty()) append(" · ")
                                                    append(it)
                                                }
                                            }.ifBlank { "Altura combinada" },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    val rightValue = when {
                                        marine.seaSurfaceTempC != null -> "%.1f °C".format(marine.seaSurfaceTempC)
                                        marine.swellHeightM != null -> "%.2f m".format(marine.swellHeightM)
                                        else -> null
                                    }
                                    if (rightValue != null) {
                                        WeatherMetricTile(
                                            icon = Icons.Rounded.Water,
                                            label = if (marine.seaSurfaceTempC != null) "Agua del mar" else "Mar de fondo",
                                            value = rightValue,
                                            extra = if (marine.seaSurfaceTempC != null && marine.swellHeightM != null) {
                                                "Mar de fondo %.2f m".format(marine.swellHeightM)
                                            } else {
                                                "Sonda superficial"
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = w.fishingTip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTelemetryBar(
    isAutoRefreshEnabled: Boolean,
    isRefreshing: Boolean,
    refreshIntervalSeconds: Int,
    secondsUntilNextRefresh: Int,
    lastUpdatedEpochMs: Long?,
    onToggleAutoRefresh: () -> Unit,
    onSelectRefreshInterval: (Int) -> Unit,
) {
    val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val formattedTime = remember(lastUpdatedEpochMs) {
        lastUpdatedEpochMs?.let { ms ->
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
        } ?: "—"
    }

    val intervalOptions = remember {
        listOf(
            15 to "15 s",
            30 to "30 s",
            60 to "1 min",
            300 to "5 min",
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isAutoRefreshEnabled) {
            ReleaseGreen.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_telemetry_bar"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .alpha(if (isAutoRefreshEnabled) pulseAlpha else 0.4f)
                            .clip(CircleShape)
                            .background(
                                if (isAutoRefreshEnabled) ReleaseGreen else MaterialTheme.colorScheme.outline
                            )
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = when {
                            isRefreshing -> "Actualizando datos reales..."
                            isAutoRefreshEnabled -> "EN VIVO · $formattedTime · Próx. ${secondsUntilNextRefresh}s"
                            else -> "Pausado · Última: $formattedTime"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAutoRefreshEnabled) ReleaseGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onToggleAutoRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_auto_refresh_button"),
                ) {
                    Icon(
                        imageVector = if (isAutoRefreshEnabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isAutoRefreshEnabled) "Pausar actualización automática" else "Reanudar actualización automática",
                        tint = if (isAutoRefreshEnabled) ReleaseGreen else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Frecuencia:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                intervalOptions.forEach { (sec, label) ->
                    FilterChip(
                        selected = isAutoRefreshEnabled && refreshIntervalSeconds == sec,
                        onClick = { onSelectRefreshInterval(sec) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("interval_chip_${sec}s"),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    extra: String,
    modifier: Modifier = Modifier,
    trendIcon: ImageVector? = null,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trendIcon != null) {
                    Icon(
                        imageVector = trendIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = extra,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SeasonSummaryCard(
    catches: List<CatchLog>,
    spotsCount: Int,
    tackleCount: Int,
    onOpenBitacora: () -> Unit,
) {
    val bestWeight = catches.maxOfOrNull { it.weightKg } ?: 0.0
    val releasedPct = if (catches.isNotEmpty()) {
        (catches.count { it.released } * 100) / catches.size
    } else 0

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenBitacora),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Phishing,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tu temporada",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "Ver bitácora →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryStatPill("Capturas", "${catches.size}", Modifier.weight(1f))
                SummaryStatPill("Récord", if (bestWeight > 0) "%.1f kg".format(bestWeight) else "—", Modifier.weight(1f))
                SummaryStatPill("Suelta", "$releasedPct%", Modifier.weight(1f), highlightColor = ReleaseGreen)
                SummaryStatPill("Zonas/Caja", "$spotsCount / $tackleCount", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlightColor: Color? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
