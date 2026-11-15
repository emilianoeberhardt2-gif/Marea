package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.media.PhotoStorageHelper
import com.example.data.model.CatchLog
import com.example.data.model.FishingSpot
import com.example.data.model.TackleItem
import com.example.data.model.WaterType
import com.example.domain.FishingReferenceData
import com.example.ui.theme.BrassGold
import com.example.ui.theme.ReleaseGreen
import com.example.ui.viewmodel.UiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CatchFilter(val label: String) {
    ALL("Todas"),
    DULCE("Agua Dulce"),
    SALADA("Agua Salada"),
    ESTUARIO("Estuario"),
    RELEASED("Captura y suelta"),
}

@Composable
fun BitacoraScreen(
    catchesState: UiState<List<CatchLog>>,
    onOpenNewCatch: () -> Unit,
    onOpenSpeciesIdentifier: () -> Unit,
    onEditCatch: (CatchLog) -> Unit,
    onDeleteCatch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(CatchFilter.ALL) }
    var selectedCatchForDetail by remember { mutableStateOf<CatchLog?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (catchesState) {
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
                    Text(
                        text = catchesState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is UiState.Success -> {
                val allCatches = catchesState.data
                val filteredCatches = remember(allCatches, searchQuery, activeFilter) {
                    allCatches.filter { item ->
                        val matchesFilter = when (activeFilter) {
                            CatchFilter.ALL -> true
                            CatchFilter.DULCE -> item.waterType == WaterType.DULCE.key
                            CatchFilter.SALADA -> item.waterType == WaterType.SALADA.key
                            CatchFilter.ESTUARIO -> item.waterType == WaterType.ESTUARIO.key
                            CatchFilter.RELEASED -> item.released
                        }
                        val q = searchQuery.trim().lowercase()
                        val matchesQuery = q.isEmpty() ||
                            item.species.lowercase().contains(q) ||
                            item.spotName.lowercase().contains(q) ||
                            item.lureOrBait.lowercase().contains(q)
                        matchesFilter && matchesQuery
                    }
                }

                val personalBest = remember(allCatches) {
                    allCatches.maxByOrNull { it.weightKg }
                }

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        OutlinedButton(
                            onClick = onOpenSpeciesIdentifier,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("bitacora_identify_species_button"),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sacar foto y comparar especie")
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar especie, zona o señuelo...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Limpiar búsqueda")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_catches_input"),
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CatchFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = activeFilter == filter,
                                    onClick = { activeFilter = filter },
                                    label = { Text(filter.label) },
                                )
                            }
                        }
                    }

                    if (personalBest != null && personalBest.weightKg > 0.0) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.EmojiEvents,
                                        contentDescription = null,
                                        tint = BrassGold,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Récord Personal",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                        Text(
                                            text = "${personalBest.species} · %.2f kg (%.0f cm)".format(
                                                personalBest.weightKg,
                                                personalBest.lengthCm,
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filteredCatches.isEmpty()) {
                        item {
                            EmptyCatchesCard(
                                isFiltered = allCatches.isNotEmpty(),
                                onAddCatch = onOpenNewCatch,
                            )
                        }
                    } else {
                        items(filteredCatches, key = { it.id }) { catchLog ->
                            CatchLogCard(
                                catchLog = catchLog,
                                onClick = { selectedCatchForDetail = catchLog },
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

    selectedCatchForDetail?.let { detail ->
        CatchDetailDialog(
            catchLog = detail,
            onDismiss = { selectedCatchForDetail = null },
            onEdit = {
                selectedCatchForDetail = null
                onEditCatch(detail)
            },
            onDelete = {
                selectedCatchForDetail = null
                onDeleteCatch(detail.id)
            },
        )
    }
}

@Composable
private fun EmptyCatchesCard(
    isFiltered: Boolean,
    onAddCatch: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_catches),
                contentDescription = "Ilustración de equipo de pesca en la orilla",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = if (isFiltered) "Sin capturas con ese filtro" else "Tu bitácora está lista para el primer lance",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isFiltered) {
                    "Prueba cambiando la búsqueda o los filtros superiores."
                } else {
                    "Anota especie, peso, talla, señuelo y las condiciones del día para recordar qué funcionó mejor."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isFiltered) {
                Button(
                    onClick = onAddCatch,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("empty_add_catch_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Registrar primera captura")
                }
            }
        }
    }
}

@Composable
private fun CatchLogCard(
    catchLog: CatchLog,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val dateStr = remember(catchLog.caughtAtEpochMs) {
        val sdf = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es", "ES"))
        sdf.format(Date(catchLog.caughtAtEpochMs))
    }
    val waterLabel = WaterType.fromKey(catchLog.waterType).label
    val photoFile = remember(catchLog.photoPath) {
        PhotoStorageHelper.resolvePhotoFile(context, catchLog.photoPath)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("catch_card_${catchLog.id}"),
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
                if (photoFile != null) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = "Foto de ${catchLog.species}",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = catchLog.species,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (catchLog.released) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ReleaseGreen.copy(alpha = 0.14f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Eco,
                                contentDescription = "Captura y suelta",
                                tint = ReleaseGreen,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Suelta",
                                style = MaterialTheme.typography.labelSmall,
                                color = ReleaseGreen,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricChip(
                    icon = Icons.Rounded.Scale,
                    text = "%.2f kg".format(catchLog.weightKg),
                )
                MetricChip(
                    icon = Icons.Rounded.Straighten,
                    text = "%.0f cm".format(catchLog.lengthCm),
                )
                MetricChip(
                    icon = Icons.Rounded.Water,
                    text = waterLabel,
                )
            }

            if (catchLog.spotName.isNotBlank() || catchLog.lureOrBait.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (catchLog.spotName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Rounded.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = catchLog.spotName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    if (catchLog.lureOrBait.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Rounded.Phishing,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = catchLog.lureOrBait,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CatchDetailDialog(
    catchLog: CatchLog,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val photoFile = remember(catchLog.photoPath) {
        PhotoStorageHelper.resolvePhotoFile(context, catchLog.photoPath)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(catchLog.species) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (photoFile != null) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = "Foto de ${catchLog.species}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text("Peso: %.2f kg · Longitud: %.0f cm".format(catchLog.weightKg, catchLog.lengthCm))
                Text("Tipo de agua: ${WaterType.fromKey(catchLog.waterType).label}")
                if (catchLog.spotName.isNotBlank()) Text("Zona: ${catchLog.spotName}")
                if (catchLog.lureOrBait.isNotBlank()) Text("Señuelo / Carnada: ${catchLog.lureOrBait}")
                if (catchLog.weatherSummary.isNotBlank()) Text("Condiciones: ${catchLog.weatherSummary}")
                Text("Captura y suelta: ${if (catchLog.released) "Sí" else "No"}")
                if (catchLog.notes.isNotBlank()) {
                    Text(
                        text = catchLog.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Editar")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchEditorSheet(
    initialCatch: CatchLog?,
    spots: List<FishingSpot>,
    tackle: List<TackleItem>,
    currentWeatherSummary: String,
    onIdentifyWithPhoto: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (CatchLog) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var species by remember(initialCatch) { mutableStateOf(initialCatch?.species ?: "") }
    var weightText by remember(initialCatch) {
        mutableStateOf(if (initialCatch != null && initialCatch.weightKg > 0) initialCatch.weightKg.toString() else "")
    }
    var lengthText by remember(initialCatch) {
        mutableStateOf(if (initialCatch != null && initialCatch.lengthCm > 0) initialCatch.lengthCm.toString() else "")
    }
    var lureOrBait by remember(initialCatch) { mutableStateOf(initialCatch?.lureOrBait ?: "") }
    var spotName by remember(initialCatch) { mutableStateOf(initialCatch?.spotName ?: "") }
    var waterType by remember(initialCatch) { mutableStateOf(initialCatch?.waterType ?: WaterType.DULCE.key) }
    var weatherSummary by remember(initialCatch) {
        mutableStateOf(initialCatch?.weatherSummary?.ifBlank { currentWeatherSummary } ?: currentWeatherSummary)
    }
    var released by remember(initialCatch) { mutableStateOf(initialCatch?.released ?: true) }
    var notes by remember(initialCatch) { mutableStateOf(initialCatch?.notes ?: "") }
    var photoPath by remember(initialCatch) { mutableStateOf(initialCatch?.photoPath ?: "") }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null && file.exists()) {
            PhotoStorageHelper.persistFileToInternalPhoto(context, file)?.let { saved ->
                photoPath = saved
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            PhotoStorageHelper.persistUriToInternalPhoto(context, uri)?.let { saved ->
                photoPath = saved
            }
        }
    }

    val photoFile = remember(photoPath) {
        PhotoStorageHelper.resolvePhotoFile(context, photoPath)
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
                text = if (initialCatch?.id.isNullOrBlank()) "Nueva Captura" else "Editar Captura",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (photoFile != null) {
                AsyncImage(
                    model = photoFile,
                    contentDescription = "Foto de captura",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val (uri, file) = PhotoStorageHelper.createCameraCaptureUri(context)
                        pendingCameraFile = file
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Foto", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galería", maxLines = 1)
                }
                Button(
                    onClick = { onIdentifyWithPhoto(photoPath) },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(46.dp)
                        .testTag("catch_identify_species_button"),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Comparar", maxLines = 1)
                }
            }

            OutlinedTextField(
                value = species,
                onValueChange = {
                    species = it
                    errorText = null
                },
                label = { Text("Especie *") },
                placeholder = { Text("Ej. Lubina, Trucha, Dorado") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catch_species_input"),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FishingReferenceData.commonSpecies.forEach { preset ->
                    SuggestionChip(
                        onClick = {
                            species = preset
                            errorText = null
                        },
                        label = { Text(preset) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.replace(',', '.') },
                    label = { Text("Peso (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("catch_weight_input"),
                )
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it.replace(',', '.') },
                    label = { Text("Talla (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("catch_length_input"),
                )
            }

            Text("Tipo de agua", style = MaterialTheme.typography.labelLarge)
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
                value = spotName,
                onValueChange = { spotName = it },
                label = { Text("Zona / Lugar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (spots.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    spots.forEach { spot ->
                        SuggestionChip(
                            onClick = {
                                spotName = spot.name
                                waterType = spot.waterType
                            },
                            label = { Text(spot.name) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = lureOrBait,
                onValueChange = { lureOrBait = it },
                label = { Text("Señuelo o carnada") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (tackle.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tackle.forEach { item ->
                        SuggestionChip(
                            onClick = { lureOrBait = item.name },
                            label = { Text(item.name) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = weatherSummary,
                onValueChange = { weatherSummary = it },
                label = { Text("Clima / Presión / Marea") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Captura y suelta", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Pez devuelto vivo al agua",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = released,
                    onCheckedChange = { released = it },
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas de la jornada") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

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
                        if (species.isBlank()) {
                            errorText = "Ingresa el nombre de la especie."
                            return@Button
                        }
                        val w = weightText.toDoubleOrNull() ?: 0.0
                        val l = lengthText.toDoubleOrNull() ?: 0.0
                        onSave(
                            (initialCatch ?: CatchLog()).copy(
                                species = species.trim(),
                                weightKg = w.coerceIn(0.0, 500.0),
                                lengthCm = l.coerceIn(0.0, 500.0),
                                lureOrBait = lureOrBait.trim(),
                                spotName = spotName.trim(),
                                waterType = waterType,
                                weatherSummary = weatherSummary.trim(),
                                released = released,
                                notes = notes.trim(),
                                photoPath = photoPath,
                                caughtAtEpochMs = initialCatch?.caughtAtEpochMs?.takeIf { it > 0L }
                                    ?: System.currentTimeMillis(),
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_catch_button"),
                ) {
                    Text("Guardar")
                }
            }
        }
    }
}
