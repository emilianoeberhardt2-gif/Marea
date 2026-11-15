package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.location.LocationHelper
import com.example.data.media.PhotoStorageHelper
import com.example.data.model.FishingSpot
import com.example.data.model.WaterType
import com.example.ui.theme.BrassGold
import com.example.ui.viewmodel.UiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonasScreen(
    spotsState: UiState<List<FishingSpot>>,
    onOpenNewSpot: (Double?, Double?) -> Unit,
    onEditSpot: (FishingSpot) -> Unit,
    onDeleteSpot: (String) -> Unit,
    onCheckWeatherAtSpot: (FishingSpot) -> Unit,
    onLogCatchAtSpot: (FishingSpot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewMode by remember { mutableIntStateOf(0) } // 0 = Mapa, 1 = Lista
    var selectedWaterFilter by remember { mutableStateOf<WaterType?>(null) }
    var selectedSpotOnMap by remember { mutableStateOf<FishingSpot?>(null) }
    var mapCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val gpsCenterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                LocationHelper.getFreshOrLastCoordinates(context)?.let { coords ->
                    mapCenter = coords
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (spotsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(spotsState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val allSpots = spotsState.data
                val filteredSpots = remember(allSpots, selectedWaterFilter) {
                    allSpots.filter { spot ->
                        selectedWaterFilter == null || spot.waterType == selectedWaterFilter?.key
                    }
                }
                val activeSelectedSpot = remember(filteredSpots, selectedSpotOnMap) {
                    selectedSpotOnMap?.let { sel -> filteredSpots.firstOrNull { it.id == sel.id } }
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 900.dp)
                        .fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                SegmentedButton(
                                    selected = viewMode == 0,
                                    onClick = { viewMode = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    icon = { Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.testTag("zonas_map_tab"),
                                ) {
                                    Text("Mapa")
                                }
                                SegmentedButton(
                                    selected = viewMode == 1,
                                    onClick = { viewMode = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    icon = { Icon(Icons.Rounded.List, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.testTag("zonas_list_tab"),
                                ) {
                                    Text("Lista (${filteredSpots.size})")
                                }
                            }

                            Button(
                                onClick = { onOpenNewSpot(null, null) },
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("add_spot_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Añadir")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedWaterFilter == null,
                                onClick = { selectedWaterFilter = null },
                                label = { Text("Todos") },
                            )
                            WaterType.entries.forEach { wt ->
                                FilterChip(
                                    selected = selectedWaterFilter == wt,
                                    onClick = { selectedWaterFilter = wt },
                                    label = { Text(wt.label) },
                                )
                            }
                        }
                    }

                    if (viewMode == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            FishingMapComponent(
                                spots = filteredSpots,
                                selectedSpotId = activeSelectedSpot?.id,
                                centerLatLon = mapCenter,
                                onMarkerClicked = { spot ->
                                    selectedSpotOnMap = spot
                                    if (spot.latitude != null && spot.longitude != null) {
                                        mapCenter = spot.latitude to spot.longitude
                                    }
                                },
                                onMapTapped = { lat, lon ->
                                    selectedSpotOnMap = null
                                    onOpenNewSpot(lat, lon)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("interactive_fishing_map"),
                            )

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 10.dp, start = 16.dp, end = 16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TouchApp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Toca el mapa para marcar un punto de pesca",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            SmallFloatingActionButton(
                                onClick = {
                                    if (LocationHelper.hasLocationPermission(context)) {
                                        scope.launch {
                                            LocationHelper.getFreshOrLastCoordinates(context)?.let { coords ->
                                                mapCenter = coords
                                            }
                                        }
                                    } else {
                                        gpsCenterLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 54.dp, end = 12.dp),
                            ) {
                                Icon(Icons.Rounded.MyLocation, contentDescription = "Centrar mapa en mi GPS")
                            }

                            if (activeSelectedSpot != null) {
                                MapSpotPreviewCard(
                                    spot = activeSelectedSpot,
                                    onDismiss = { selectedSpotOnMap = null },
                                    onEdit = { onEditSpot(activeSelectedSpot) },
                                    onCheckWeather = { onCheckWeatherAtSpot(activeSelectedSpot) },
                                    onLogCatch = { onLogCatchAtSpot(activeSelectedSpot) },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(12.dp)
                                        .padding(bottom = 64.dp),
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (filteredSpots.isEmpty()) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Explore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(44.dp),
                                            )
                                            Text(
                                                text = "Sin puntos en esta categoría",
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                            Text(
                                                text = "Cambia al mapa y toca cualquier costa, río o embalse para crear un marcador con fotos.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Button(onClick = { viewMode = 0 }, modifier = Modifier.height(48.dp)) {
                                                Text("Ir al mapa interactivo")
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(filteredSpots, key = { it.id }) { spot ->
                                    SpotCard(
                                        spot = spot,
                                        onEdit = { onEditSpot(spot) },
                                        onDelete = { onDeleteSpot(spot.id) },
                                        onCheckWeather = { onCheckWeatherAtSpot(spot) },
                                        onLogCatch = { onLogCatchAtSpot(spot) },
                                        onShowOnMap = {
                                            selectedSpotOnMap = spot
                                            if (spot.latitude != null && spot.longitude != null) {
                                                mapCenter = spot.latitude to spot.longitude
                                            }
                                            viewMode = 0
                                        },
                                    )
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(76.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapSpotPreviewCard(
    spot: FishingSpot,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onCheckWeather: () -> Unit,
    onLogCatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spot.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${WaterType.fromKey(spot.waterType).label} · ${spot.rating}/5 ★",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Editar punto")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar ficha")
                }
            }

            if (spot.accessNotes.isNotBlank()) {
                Text(
                    text = spot.accessNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            if (spot.photoPaths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(spot.photoPaths) { fileName ->
                        val file = PhotoStorageHelper.resolvePhotoFile(context, fileName)
                        if (file != null) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Foto del punto ${spot.name}",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCheckWeather,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clima", maxLines = 1)
                }
                Button(
                    onClick = onLogCatch,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Icon(Icons.Rounded.Phishing, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Anotar captura", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SpotCard(
    spot: FishingSpot,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCheckWeather: () -> Unit,
    onLogCatch: () -> Unit,
    onShowOnMap: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("spot_card_${spot.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = spot.name,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = WaterType.fromKey(spot.waterType).label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        repeat(5) { idx ->
                            Icon(
                                imageVector = if (idx < spot.rating) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = null,
                                tint = BrassGold,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Row {
                    if (spot.latitude != null && spot.longitude != null) {
                        IconButton(onClick = onShowOnMap) {
                            Icon(Icons.Rounded.Map, contentDescription = "Ver en el mapa")
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Editar zona")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Eliminar zona",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (spot.targetSpecies.isNotBlank()) {
                Text(
                    text = "Especies: ${spot.targetSpecies}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (spot.latitude != null && spot.longitude != null) {
                Text(
                    text = "GPS: %.4f, %.4f".format(spot.latitude, spot.longitude),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (spot.accessNotes.isNotBlank()) {
                Text(
                    text = spot.accessNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (spot.photoPaths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(spot.photoPaths) { fileName ->
                        val file = PhotoStorageHelper.resolvePhotoFile(context, fileName)
                        if (file != null) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Foto de ${spot.name}",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (spot.latitude != null && spot.longitude != null) {
                    OutlinedButton(
                        onClick = onCheckWeather,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver parte", maxLines = 1)
                    }
                }
                Button(
                    onClick = onLogCatch,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(Icons.Rounded.Phishing, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Anotar captura", maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotEditorSheet(
    initialSpot: FishingSpot?,
    onDismiss: () -> Unit,
    onSave: (FishingSpot) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(initialSpot) { mutableStateOf(initialSpot?.name ?: "") }
    var waterType by remember(initialSpot) { mutableStateOf(initialSpot?.waterType ?: WaterType.DULCE.key) }
    var targetSpecies by remember(initialSpot) { mutableStateOf(initialSpot?.targetSpecies ?: "") }
    var accessNotes by remember(initialSpot) { mutableStateOf(initialSpot?.accessNotes ?: "") }
    var latText by remember(initialSpot) { mutableStateOf(initialSpot?.latitude?.toString() ?: "") }
    var lonText by remember(initialSpot) { mutableStateOf(initialSpot?.longitude?.toString() ?: "") }
    var rating by remember(initialSpot) { mutableLongStateOf(initialSpot?.rating ?: 5L) }
    var photoPaths by remember(initialSpot) { mutableStateOf(initialSpot?.photoPaths ?: emptyList()) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val gpsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                val coords = LocationHelper.getFreshOrLastCoordinates(context)
                if (coords != null) {
                    latText = "%.5f".format(coords.first).replace(',', '.')
                    lonText = "%.5f".format(coords.second).replace(',', '.')
                    errorText = null
                } else {
                    errorText = "Sin caché GPS reciente. Puedes ingresar latitud y longitud manualmente."
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null && file.exists()) {
            PhotoStorageHelper.persistFileToInternalPhoto(context, file)?.let { saved ->
                if (photoPaths.size < 5) {
                    photoPaths = photoPaths + saved
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        val added = uris.mapNotNull { PhotoStorageHelper.persistUriToInternalPhoto(context, it) }
        photoPaths = (photoPaths + added).take(5)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (initialSpot?.id.isNullOrBlank()) "Nuevo Punto de Pesca" else "Editar Punto de Pesca",
                style = MaterialTheme.typography.headlineSmall,
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorText = null
                },
                label = { Text("Nombre del marcador *") },
                placeholder = { Text("Ej. Espigón Norte, Laguna El Sauce") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("spot_name_input"),
            )

            Text("Categoría del punto", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaterType.entries.forEach { wt ->
                    FilterChip(
                        selected = waterType == wt.key,
                        onClick = { waterType = wt.key },
                        label = { Text(wt.label) },
                    )
                }
            }

            OutlinedTextField(
                value = targetSpecies,
                onValueChange = { targetSpecies = it },
                label = { Text("Especies habituales") },
                placeholder = { Text("Ej. Lubina, Sargo, Trucha") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = accessNotes,
                onValueChange = { accessNotes = it },
                label = { Text("Breve descripción, acceso o marea ideal") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Fotos del punto (${photoPaths.size}/5)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        enabled = photoPaths.size < 5,
                        onClick = {
                            val (uri, file) = PhotoStorageHelper.createCameraCaptureUri(context)
                            pendingCameraFile = file
                            cameraLauncher.launch(uri)
                        },
                    ) {
                        Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cámara")
                    }
                    TextButton(
                        enabled = photoPaths.size < 5,
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galería")
                    }
                }
            }

            if (photoPaths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(photoPaths) { fileName ->
                        val file = PhotoStorageHelper.resolvePhotoFile(context, fileName)
                        Box(modifier = Modifier.size(84.dp)) {
                            if (file != null) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Foto del punto",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xAA000000),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clickable { photoPaths = photoPaths - fileName },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Quitar foto",
                                    tint = Color.White,
                                    modifier = Modifier.padding(3.dp),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Coordenadas GPS", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = {
                    if (LocationHelper.hasLocationPermission(context)) {
                        scope.launch {
                            val coords = LocationHelper.getFreshOrLastCoordinates(context)
                            if (coords != null) {
                                latText = "%.5f".format(coords.first).replace(',', '.')
                                lonText = "%.5f".format(coords.second).replace(',', '.')
                            } else {
                                errorText = "Sin última ubicación GPS en caché."
                            }
                        }
                    } else {
                        gpsLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Usar mi GPS")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it.replace(',', '.') },
                    label = { Text("Latitud") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it.replace(',', '.') },
                    label = { Text("Longitud") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Valoración del punto", style = MaterialTheme.typography.labelLarge)
                Row {
                    for (star in 1L..5L) {
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = "$star estrellas",
                                tint = BrassGold,
                            )
                        }
                    }
                }
            }

            if (errorText != null) {
                Text(
                    text = errorText ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            errorText = "Ingresa un nombre para el marcador."
                            return@Button
                        }
                        val lat = latText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                        val lon = lonText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                        if ((latText.isNotBlank() && lat == null) || (lonText.isNotBlank() && lon == null)) {
                            errorText = "Coordenadas GPS inválidas."
                            return@Button
                        }
                        onSave(
                            (initialSpot ?: FishingSpot()).copy(
                                name = name.trim(),
                                waterType = waterType,
                                targetSpecies = targetSpecies.trim(),
                                accessNotes = accessNotes.trim(),
                                latitude = lat,
                                longitude = lon,
                                rating = rating,
                                photoPaths = photoPaths,
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_spot_button"),
                ) {
                    Text("Guardar")
                }
            }
        }
    }
}
