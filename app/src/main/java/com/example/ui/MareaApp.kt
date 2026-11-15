package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.location.LocationHelper
import com.example.data.media.PhotoStorageHelper
import com.example.data.model.CatchLog
import com.example.data.model.FishingSpot
import com.example.data.model.TackleItem
import com.example.data.remote.WeatherRepository
import com.example.data.repository.FishingRepository
import com.example.data.sensors.BarometerMonitor
import com.example.domain.FishIdentifierService
import com.example.domain.FishingAssistantService
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AmigosScreen
import com.example.ui.screens.AsistenteSheet
import com.example.ui.screens.BitacoraScreen
import com.example.ui.screens.CatchEditorSheet
import com.example.ui.screens.EquipoScreen
import com.example.ui.screens.JornadaScreen
import com.example.ui.screens.SpeciesIdentifierSheet
import com.example.ui.screens.SpotEditorSheet
import com.example.ui.screens.TackleEditorSheet
import com.example.ui.screens.ZonasScreen
import com.example.ui.viewmodel.MareaViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class MareaTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOY("Hoy", Icons.Filled.Waves, Icons.Outlined.Waves),
    BITACORA("Bitácora", Icons.Filled.Book, Icons.Outlined.Book),
    ZONAS("Mapa", Icons.Filled.Map, Icons.Outlined.Map),
    AMIGOS("Amigos", Icons.Filled.Group, Icons.Outlined.Group),
    EQUIPO("Equipo", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            onAuthSuccess = { /* AuthStateListener emits updated currentUser */ }
        )
    } else {
        key(user.uid) {
            MareaMainShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "Pescador",
                userEmail = user.email ?: "",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MareaMainShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: MareaViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MareaViewModel(
                    repository = FishingRepository(db, Firebase.auth),
                    weatherRepository = WeatherRepository(),
                    fishIdentifierService = FishIdentifierService(),
                    currentUserId = currentUserId,
                    fishingAssistantService = FishingAssistantService(),
                    currentUserDisplayName = userDisplayName,
                )
            }
        }
    )

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setForegroundActive(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setForegroundActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentUserId) {
        val coords = LocationHelper.getFreshOrLastCoordinates(context)
        if (coords != null) {
            viewModel.refreshConditions(coords.first, coords.second, "Tu ubicación GPS")
        }
    }

    val catchesState by viewModel.catchesState.collectAsStateWithLifecycle()
    val spotsState by viewModel.spotsState.collectAsStateWithLifecycle()
    val tackleState by viewModel.tackleState.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val solunar by viewModel.solunarForecast.collectAsStateWithLifecycle()
    val isRefreshingWeather by viewModel.isRefreshingWeather.collectAsStateWithLifecycle()
    val isAutoRefreshEnabled by viewModel.isAutoRefreshEnabled.collectAsStateWithLifecycle()
    val refreshIntervalSeconds by viewModel.refreshIntervalSeconds.collectAsStateWithLifecycle()
    val secondsUntilNextRefresh by viewModel.secondsUntilNextRefresh.collectAsStateWithLifecycle()
    val lastUpdatedEpochMs by viewModel.lastUpdatedEpochMs.collectAsStateWithLifecycle()
    val deviceBarometerHpa by remember(context) {
        BarometerMonitor.observeDevicePressureHpa(context)
    }.collectAsStateWithLifecycle(initialValue = null)
    val speciesAnalysisState by viewModel.speciesAnalysisState.collectAsStateWithLifecycle()
    val friendsState by viewModel.friendsState.collectAsStateWithLifecycle()
    val discoverableAnglersState by viewModel.discoverableAnglersState.collectAsStateWithLifecycle()
    val competitionsState by viewModel.competitionsState.collectAsStateWithLifecycle()
    val allSharedCatchesState by viewModel.allSharedCatchesState.collectAsStateWithLifecycle()
    val inspectedFriend by viewModel.inspectedFriend.collectAsStateWithLifecycle()
    val inspectedFriendCatchesState by viewModel.inspectedFriendCatchesState.collectAsStateWithLifecycle()
    val dailyAiQuestionsUsed by viewModel.dailyAiQuestionsUsed.collectAsStateWithLifecycle()
    val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val isAiReplying by viewModel.isAiReplying.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(MareaTab.HOY) }
    var editingCatch by remember { mutableStateOf<CatchLog?>(null) }
    var showCatchEditor by remember { mutableStateOf(false) }
    var showSpeciesIdentifier by remember { mutableStateOf(false) }
    var identifierInitialPhotoPath by remember { mutableStateOf("") }
    var editingSpot by remember { mutableStateOf<FishingSpot?>(null) }
    var showSpotEditor by remember { mutableStateOf(false) }
    var editingTackle by remember { mutableStateOf<TackleItem?>(null) }
    var showTackleEditor by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showAssistantSheet by remember { mutableStateOf(false) }

    val aiQuestionsRemaining = (FishingRepository.MAX_AI_QUESTIONS_PER_DAY - dailyAiQuestionsUsed).coerceIn(0, 5)

    BackHandler(enabled = currentTab != MareaTab.HOY) {
        currentTab = MareaTab.HOY
    }

    val spotsList = (spotsState as? UiState.Success)?.data.orEmpty()
    val tackleList = (tackleState as? UiState.Success)?.data.orEmpty()
    val currentWeatherSummary = (weatherState as? UiState.Success)?.data?.compactSummary().orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Phishing,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Marea",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { showAssistantSheet = true },
                            modifier = Modifier.testTag("topbar_ai_assistant_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Asistente IA de Pesca",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "IA $aiQuestionsRemaining/5",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        IconButton(
                            onClick = { showAccountDialog = true },
                            modifier = Modifier.testTag("account_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = "Cuenta de pescador",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingCatch = null
                        showCatchEditor = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag("fab_add_catch"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Registrar captura",
                    )
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        MareaTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        MareaTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}"),
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        MareaTab.HOY -> JornadaScreen(
                            solunar = solunar,
                            weatherState = weatherState,
                            catchesState = catchesState,
                            spotsState = spotsState,
                            tackleState = tackleState,
                            isRefreshingWeather = isRefreshingWeather,
                            isAutoRefreshEnabled = isAutoRefreshEnabled,
                            refreshIntervalSeconds = refreshIntervalSeconds,
                            secondsUntilNextRefresh = secondsUntilNextRefresh,
                            lastUpdatedEpochMs = lastUpdatedEpochMs,
                            deviceBarometerHpa = deviceBarometerHpa,
                            onRefreshLocation = { lat, lon, label ->
                                viewModel.refreshConditions(lat, lon, label)
                            },
                            onRefreshNow = { viewModel.refreshNow() },
                            onToggleAutoRefresh = { viewModel.toggleAutoRefresh() },
                            onSelectRefreshInterval = { sec -> viewModel.setRefreshIntervalSeconds(sec) },
                            onQuickAddCatch = {
                                editingCatch = null
                                showCatchEditor = true
                            },
                            onOpenSpeciesIdentifier = {
                                identifierInitialPhotoPath = ""
                                viewModel.resetSpeciesAnalysis()
                                showSpeciesIdentifier = true
                            },
                            onOpenAssistant = { showAssistantSheet = true },
                            aiQuestionsRemaining = aiQuestionsRemaining,
                            onNavigateToBitacora = { currentTab = MareaTab.BITACORA },
                        )
                        MareaTab.BITACORA -> BitacoraScreen(
                            catchesState = catchesState,
                            onOpenNewCatch = {
                                editingCatch = null
                                showCatchEditor = true
                            },
                            onOpenSpeciesIdentifier = {
                                identifierInitialPhotoPath = ""
                                viewModel.resetSpeciesAnalysis()
                                showSpeciesIdentifier = true
                            },
                            onEditCatch = { c ->
                                editingCatch = c
                                showCatchEditor = true
                            },
                            onDeleteCatch = { id -> viewModel.deleteCatch(id) },
                        )
                        MareaTab.ZONAS -> ZonasScreen(
                            spotsState = spotsState,
                            onOpenNewSpot = { lat, lon ->
                                editingSpot = if (lat != null && lon != null) {
                                    FishingSpot(latitude = lat, longitude = lon)
                                } else {
                                    null
                                }
                                showSpotEditor = true
                            },
                            onEditSpot = { spot ->
                                editingSpot = spot
                                showSpotEditor = true
                            },
                            onDeleteSpot = { id -> viewModel.deleteSpot(id) },
                            onCheckWeatherAtSpot = { spot ->
                                val lat = spot.latitude
                                val lon = spot.longitude
                                if (lat != null && lon != null) {
                                    viewModel.refreshConditions(lat, lon, spot.name)
                                    currentTab = MareaTab.HOY
                                }
                            },
                            onLogCatchAtSpot = { spot ->
                                editingCatch = CatchLog(
                                    spotName = spot.name,
                                    waterType = spot.waterType,
                                )
                                showCatchEditor = true
                            },
                        )
                        MareaTab.AMIGOS -> AmigosScreen(
                            currentUserId = currentUserId,
                            userDisplayName = userDisplayName,
                            friendsState = friendsState,
                            discoverableAnglersState = discoverableAnglersState,
                            competitionsState = competitionsState,
                            allSharedCatchesState = allSharedCatchesState,
                            inspectedFriend = inspectedFriend,
                            inspectedFriendCatchesState = inspectedFriendCatchesState,
                            onAddFriend = { angler -> viewModel.addFriend(angler) },
                            onAddFriendByCode = { code -> viewModel.addFriendByCode(code) },
                            onRemoveFriend = { uid -> viewModel.removeFriend(uid) },
                            onInspectFriendCatches = { friend -> viewModel.inspectFriendCatches(friend) },
                            onCreateCompetition = { title, selected, species, mode, water, days ->
                                viewModel.createCompetition(title, selected, species, mode, water, days)
                            },
                            onFinalizeCompetition = { comp, winner ->
                                viewModel.finalizeCompetition(comp, winner)
                            },
                            onShowMessage = { msg -> viewModel.emitMessage(msg) },
                        )
                        MareaTab.EQUIPO -> EquipoScreen(
                            tackleState = tackleState,
                            onOpenNewTackle = {
                                editingTackle = null
                                showTackleEditor = true
                            },
                            onEditTackle = { item ->
                                editingTackle = item
                                showTackleEditor = true
                            },
                            onToggleFavorite = { item -> viewModel.toggleTackleFavorite(item) },
                            onDeleteTackle = { id -> viewModel.deleteTackle(id) },
                        )
                    }
                }
            }
        }
    }

    if (showCatchEditor) {
        CatchEditorSheet(
            initialCatch = editingCatch,
            spots = spotsList,
            tackle = tackleList,
            currentWeatherSummary = currentWeatherSummary,
            onIdentifyWithPhoto = { currentPhoto ->
                identifierInitialPhotoPath = currentPhoto
                viewModel.resetSpeciesAnalysis()
                showCatchEditor = false
                showSpeciesIdentifier = true
            },
            onDismiss = { showCatchEditor = false },
            onSave = { log ->
                viewModel.saveCatch(log) {
                    showCatchEditor = false
                }
            },
        )
    }

    if (showSpeciesIdentifier) {
        SpeciesIdentifierSheet(
            analysisState = speciesAnalysisState,
            initialPhotoPath = identifierInitialPhotoPath,
            onAnalyzePhoto = { photoFileName ->
                val bmp = PhotoStorageHelper.loadBitmapForAnalysis(context, photoFileName)
                if (bmp != null) {
                    viewModel.identifyFishSpecies(bmp)
                }
            },
            onApplyResultToCatch = { result, photoPath ->
                val base = editingCatch ?: CatchLog()
                editingCatch = base.copy(
                    species = result.primarySpecies,
                    waterType = result.waterType,
                    photoPath = photoPath,
                    notes = base.notes.ifBlank { result.careAndReleaseNote },
                )
                showSpeciesIdentifier = false
                viewModel.resetSpeciesAnalysis()
                showCatchEditor = true
            },
            onDismiss = {
                showSpeciesIdentifier = false
                viewModel.resetSpeciesAnalysis()
            },
        )
    }

    if (showSpotEditor) {
        SpotEditorSheet(
            initialSpot = editingSpot,
            onDismiss = { showSpotEditor = false },
            onSave = { spot ->
                viewModel.saveSpot(spot) {
                    showSpotEditor = false
                }
            },
        )
    }

    if (showTackleEditor) {
        TackleEditorSheet(
            initialItem = editingTackle,
            onDismiss = { showTackleEditor = false },
            onSave = { item ->
                viewModel.saveTackle(item) {
                    showTackleEditor = false
                }
            },
        )
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("Cuenta de Pescador") },
            text = {
                Column {
                    Text(
                        text = userDisplayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (userEmail.isNotBlank()) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "\nTu bitácora, zonas y señuelos se sincronizan automáticamente en tu cuenta de Google.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccountDialog = false
                        signOutUser(credentialManager, scope)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.testTag("sign_out_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cerrar sesión")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Cerrar")
                }
            },
        )
    }

    if (showAssistantSheet) {
        AsistenteSheet(
            messages = aiMessages,
            questionsUsedToday = dailyAiQuestionsUsed,
            isReplying = isAiReplying,
            errorMessage = aiError,
            onSendQuestion = { question -> viewModel.askFishingAssistant(question) },
            onDismiss = { showAssistantSheet = false },
        )
    }
}
