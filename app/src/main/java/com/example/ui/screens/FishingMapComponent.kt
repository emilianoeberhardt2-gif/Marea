package com.example.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.FishingSpot
import com.example.data.model.WaterType
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

@Composable
fun FishingMapComponent(
    spots: List<FishingSpot>,
    selectedSpotId: String?,
    centerLatLon: Pair<Double, Double>?,
    onMarkerClicked: (FishingSpot) -> Unit,
    onMapTapped: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnMarkerClick by rememberUpdatedState(onMarkerClicked)
    val currentOnMapTap by rememberUpdatedState(onMapTapped)

    remember(context) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles").apply { mkdirs() }
        }
        true
    }

    val mapView = remember(context) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(11.5)
            controller.setCenter(GeoPoint(-38.0055, -57.5426))
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            if (centerLatLon != null) {
                map.controller.animateTo(GeoPoint(centerLatLon.first, centerLatLon.second))
            }

            map.overlays.clear()

            val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    if (p != null) {
                        currentOnMapTap(p.latitude, p.longitude)
                    }
                    return true
                }

                override fun longPressHelper(p: GeoPoint?): Boolean {
                    if (p != null) {
                        currentOnMapTap(p.latitude, p.longitude)
                    }
                    return true
                }
            })
            map.overlays.add(eventsOverlay)

            spots.forEach { spot ->
                val lat = spot.latitude
                val lon = spot.longitude
                if (lat != null && lon != null) {
                    val isSelected = spot.id == selectedSpotId
                    val marker = Marker(map).apply {
                        position = GeoPoint(lat, lon)
                        title = spot.name
                        snippet = WaterType.fromKey(spot.waterType).label
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createCategoryMarkerDrawable(
                            context = context,
                            waterType = spot.waterType,
                            selected = isSelected,
                        )
                        setOnMarkerClickListener { _, _ ->
                            currentOnMarkerClick(spot)
                            true
                        }
                    }
                    map.overlays.add(marker)
                }
            }

            map.invalidate()
        },
    )
}

private fun createCategoryMarkerDrawable(
    context: Context,
    waterType: String,
    selected: Boolean,
): Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = ((if (selected) 44f else 36f) * density).toInt()
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)

    val fillColor = when (waterType) {
        WaterType.DULCE.key -> 0xFF2B7A78.toInt()
        WaterType.SALADA.key -> 0xFF0B4F6C.toInt()
        WaterType.ESTUARIO.key -> 0xFFD95D39.toInt()
        else -> 0xFF0B4F6C.toInt()
    }

    val radius = sizePx * 0.36f
    val cx = sizePx / 2f
    val cy = sizePx * 0.40f

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (selected) 0xFFFFD88A.toInt() else 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }

    // Outer white/gold pin head + pointer triangle
    val path = android.graphics.Path().apply {
        moveTo(cx - radius * 0.55f, cy + radius * 0.55f)
        lineTo(cx, sizePx * 0.96f)
        lineTo(cx + radius * 0.55f, cy + radius * 0.55f)
        close()
    }
    canvas.drawPath(path, borderPaint)
    canvas.drawCircle(cx, cy, radius + (2.5f * density), borderPaint)
    canvas.drawCircle(cx, cy, radius, pinPaint)

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, radius * 0.34f, dotPaint)

    return BitmapDrawable(context.resources, bitmap)
}
