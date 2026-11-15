package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiChatMessage
import com.example.data.model.AnglerProfile
import com.example.data.model.CatchLog
import com.example.data.model.Competition
import com.example.data.model.FishingSpot
import com.example.data.model.FriendLink
import com.example.data.model.SharedCatch
import com.example.data.model.TackleItem
import com.example.data.remote.MarineWeatherSummary
import com.example.data.remote.WeatherRepository
import com.example.data.repository.AiQuotaExceededException
import com.example.data.repository.FishingRepository
import com.example.domain.FishIdentifierService
import com.example.domain.FishingAssistantService
import com.example.domain.SolunarCalculator
import com.example.domain.SolunarForecast
import com.example.domain.SpeciesIdentificationResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface SpeciesAnalysisState {
    data object Idle : SpeciesAnalysisState
    data object Analyzing : SpeciesAnalysisState
    data class Success(val result: SpeciesIdentificationResult) : SpeciesAnalysisState
    data class Error(val message: String) : SpeciesAnalysisState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MareaViewModel(
    private val repository: FishingRepository,
    private val weatherRepository: WeatherRepository,
    private val fishIdentifierService: FishIdentifierService,
    private val currentUserId: String,
    private val fishingAssistantService: FishingAssistantService = FishingAssistantService(),
    private val currentUserDisplayName: String = "Pescador",
) : ViewModel() {

    val catchesState: StateFlow<UiState<List<CatchLog>>> = repository.observeCatches(currentUserId)
        .map<List<CatchLog>, UiState<List<CatchLog>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing catches", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "No se pudo cargar la bitácora"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val spotsState: StateFlow<UiState<List<FishingSpot>>> = repository.observeSpots(currentUserId)
        .map<List<FishingSpot>, UiState<List<FishingSpot>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing spots", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "No se pudieron cargar las zonas"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val tackleState: StateFlow<UiState<List<TackleItem>>> = repository.observeTackle(currentUserId)
        .map<List<TackleItem>, UiState<List<TackleItem>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing tackle", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "No se pudo cargar la caja de equipo"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val friendsState: StateFlow<UiState<List<FriendLink>>> = repository.observeFriends(currentUserId)
        .map<List<FriendLink>, UiState<List<FriendLink>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing friends", error)
            emit(UiState.Error(error.localizedMessage ?: "No se pudo cargar la lista de amigos"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val discoverableAnglersState: StateFlow<UiState<List<AnglerProfile>>> = repository.observeDiscoverableAnglers()
        .map<List<AnglerProfile>, UiState<List<AnglerProfile>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing anglers", error)
            emit(UiState.Error(error.localizedMessage ?: "No se pudieron cargar otros pescadores"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val competitionsState: StateFlow<UiState<List<Competition>>> = repository.observeCompetitions(currentUserId)
        .map<List<Competition>, UiState<List<Competition>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing competitions", error)
            emit(UiState.Error(error.localizedMessage ?: "No se pudieron cargar las competencias"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val allSharedCatchesState: StateFlow<UiState<List<SharedCatch>>> = repository.observeAllSharedCatches()
        .map<List<SharedCatch>, UiState<List<SharedCatch>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing shared catches", error)
            emit(UiState.Error(error.localizedMessage ?: "No se pudieron cargar capturas compartidas"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _inspectedFriend = MutableStateFlow<FriendLink?>(null)
    val inspectedFriend: StateFlow<FriendLink?> = _inspectedFriend.asStateFlow()

    val inspectedFriendCatchesState: StateFlow<UiState<List<SharedCatch>>> = _inspectedFriend
        .flatMapLatest { friend ->
            if (friend == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeSharedCatchesByUser(friend.friendUid)
                    .map<List<SharedCatch>, UiState<List<SharedCatch>>> { UiState.Success(it) }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Error observing friend catches", error)
                        emit(UiState.Error(error.localizedMessage ?: "No se pudieron cargar sus capturas"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val dailyAiQuestionsUsed: StateFlow<Int> = repository.observeDailyAiQuota(
        userId = currentUserId,
        dateKey = todayDateKey(),
    )
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing daily AI quota", error)
            emit(0)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0,
        )

    private var activeLatitude: Double = DEFAULT_LAT
    private var activeLongitude: Double = DEFAULT_LON
    private var activeLocationLabel: String = DEFAULT_LABEL
    private var lastSunriseHour: Double = 6.5
    private var lastSunsetHour: Double = 19.0
    private var isForegroundActive: Boolean = true

    private val _weatherState = MutableStateFlow<UiState<MarineWeatherSummary>>(UiState.Loading)
    val weatherState: StateFlow<UiState<MarineWeatherSummary>> = _weatherState.asStateFlow()

    private val _solunarForecast = MutableStateFlow(SolunarCalculator.calculate())
    val solunarForecast: StateFlow<SolunarForecast> = _solunarForecast.asStateFlow()

    private val _isAutoRefreshEnabled = MutableStateFlow(true)
    val isAutoRefreshEnabled: StateFlow<Boolean> = _isAutoRefreshEnabled.asStateFlow()

    private val _refreshIntervalSeconds = MutableStateFlow(DEFAULT_REFRESH_INTERVAL_SEC)
    val refreshIntervalSeconds: StateFlow<Int> = _refreshIntervalSeconds.asStateFlow()

    private val _secondsUntilNextRefresh = MutableStateFlow(DEFAULT_REFRESH_INTERVAL_SEC)
    val secondsUntilNextRefresh: StateFlow<Int> = _secondsUntilNextRefresh.asStateFlow()

    private val _isRefreshingWeather = MutableStateFlow(false)
    val isRefreshingWeather: StateFlow<Boolean> = _isRefreshingWeather.asStateFlow()

    private val _lastUpdatedEpochMs = MutableStateFlow<Long?>(null)
    val lastUpdatedEpochMs: StateFlow<Long?> = _lastUpdatedEpochMs.asStateFlow()

    private val _speciesAnalysisState = MutableStateFlow<SpeciesAnalysisState>(SpeciesAnalysisState.Idle)
    val speciesAnalysisState: StateFlow<SpeciesAnalysisState> = _speciesAnalysisState.asStateFlow()

    private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()

    private val _isAiReplying = MutableStateFlow(false)
    val isAiReplying: StateFlow<Boolean> = _isAiReplying.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var fetchJob: Job? = null
    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureAnglerProfile(currentUserDisplayName)
        }
        refreshConditions(DEFAULT_LAT, DEFAULT_LON, DEFAULT_LABEL)
        restartAutoRefreshLoop()
    }

    fun setForegroundActive(active: Boolean) {
        if (isForegroundActive == active) return
        isForegroundActive = active
        if (active) {
            recalculateSolunar()
            val lastMs = _lastUpdatedEpochMs.value ?: 0L
            val elapsedSec = (System.currentTimeMillis() - lastMs) / 1000L
            if (_isAutoRefreshEnabled.value && elapsedSec >= _refreshIntervalSeconds.value) {
                refreshNow()
            }
            restartAutoRefreshLoop()
        } else {
            autoRefreshJob?.cancel()
            autoRefreshJob = null
        }
    }

    fun toggleAutoRefresh() {
        val next = !_isAutoRefreshEnabled.value
        _isAutoRefreshEnabled.value = next
        if (next) {
            _secondsUntilNextRefresh.value = _refreshIntervalSeconds.value
            restartAutoRefreshLoop()
        } else {
            autoRefreshJob?.cancel()
            autoRefreshJob = null
        }
    }

    fun setRefreshIntervalSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(15, 300)
        _refreshIntervalSeconds.value = clamped
        _secondsUntilNextRefresh.value = clamped
        if (!_isAutoRefreshEnabled.value) {
            _isAutoRefreshEnabled.value = true
        }
        restartAutoRefreshLoop()
    }

    fun refreshNow() {
        refreshConditions(
            latitude = activeLatitude,
            longitude = activeLongitude,
            locationLabel = activeLocationLabel,
            showBlockingLoading = _weatherState.value !is UiState.Success,
        )
    }

    fun refreshConditions(
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        showBlockingLoading: Boolean = true,
    ) {
        val locationChanged = activeLatitude != latitude || activeLongitude != longitude || activeLocationLabel != locationLabel
        activeLatitude = latitude
        activeLongitude = longitude
        activeLocationLabel = locationLabel
        _secondsUntilNextRefresh.value = _refreshIntervalSeconds.value

        recalculateSolunar()

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (showBlockingLoading && (locationChanged || _weatherState.value !is UiState.Success)) {
                _weatherState.value = UiState.Loading
            }
            _isRefreshingWeather.value = true
            try {
                weatherRepository.fetchConditions(latitude, longitude, locationLabel)
                    .onSuccess { summary ->
                        summary.sunriseHourDecimal?.let { lastSunriseHour = it }
                        summary.sunsetHourDecimal?.let { lastSunsetHour = it }
                        recalculateSolunar()
                        _weatherState.value = UiState.Success(summary)
                        _lastUpdatedEpochMs.value = summary.fetchedAtEpochMs
                    }
                    .onFailure { err ->
                        Log.w(TAG, "Weather fetch failed", err)
                        if (_weatherState.value !is UiState.Success) {
                            _weatherState.value = UiState.Error("Sin conexión al parte meteorológico")
                        } else {
                            _events.tryEmit("No se pudo actualizar en línea; mostrando último parte real")
                        }
                    }
            } finally {
                _isRefreshingWeather.value = false
                _secondsUntilNextRefresh.value = _refreshIntervalSeconds.value
            }
        }
    }

    private fun recalculateSolunar() {
        _solunarForecast.value = SolunarCalculator.calculate(
            nowMs = System.currentTimeMillis(),
            longitudeDeg = activeLongitude,
            sunriseHour = lastSunriseHour,
            sunsetHour = lastSunsetHour,
        )
    }

    private fun restartAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        if (!isForegroundActive || !_isAutoRefreshEnabled.value) return
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                if (!isForegroundActive || !_isAutoRefreshEnabled.value) break
                val currentSec = _secondsUntilNextRefresh.value
                if (currentSec <= 1) {
                    if (!_isRefreshingWeather.value) {
                        refreshConditions(
                            latitude = activeLatitude,
                            longitude = activeLongitude,
                            locationLabel = activeLocationLabel,
                            showBlockingLoading = false,
                        )
                    } else {
                        _secondsUntilNextRefresh.value = _refreshIntervalSeconds.value
                    }
                } else {
                    _secondsUntilNextRefresh.value = currentSec - 1
                    if ((currentSec - 1) % 30 == 0) {
                        recalculateSolunar()
                    }
                }
            }
        }
    }

    fun identifyFishSpecies(bitmap: Bitmap) {
        viewModelScope.launch {
            _speciesAnalysisState.value = SpeciesAnalysisState.Analyzing
            try {
                val result = fishIdentifierService.identifyAndCompare(bitmap)
                _speciesAnalysisState.value = SpeciesAnalysisState.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fish identification error", e)
                _speciesAnalysisState.value = SpeciesAnalysisState.Error(
                    e.localizedMessage ?: "No se pudo identificar la especie. Intenta con otra foto."
                )
            }
        }
    }

    fun resetSpeciesAnalysis() {
        _speciesAnalysisState.value = SpeciesAnalysisState.Idle
    }

    fun saveCatch(catchLog: CatchLog, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val result = if (catchLog.id.isBlank()) {
                repository.addCatch(catchLog, currentUserDisplayName).map { }
            } else {
                repository.updateCatch(catchLog, currentUserDisplayName)
            }
            result
                .onSuccess {
                    _events.tryEmit(if (catchLog.id.isBlank()) "Captura registrada en bitácora" else "Captura actualizada")
                    onDone()
                }
                .onFailure { err ->
                    _events.tryEmit("Error al guardar: ${err.localizedMessage ?: "intenta de nuevo"}")
                }
        }
    }

    fun deleteCatch(catchId: String) {
        viewModelScope.launch {
            repository.deleteCatch(catchId)
                .onSuccess { _events.tryEmit("Captura eliminada") }
                .onFailure { _events.tryEmit("No se pudo eliminar la captura") }
        }
    }

    fun saveSpot(spot: FishingSpot, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val result = if (spot.id.isBlank()) {
                repository.addSpot(spot).map { }
            } else {
                repository.updateSpot(spot)
            }
            result
                .onSuccess {
                    _events.tryEmit(if (spot.id.isBlank()) "Punto guardado en el mapa" else "Punto actualizado")
                    onDone()
                }
                .onFailure { err ->
                    _events.tryEmit("Error al guardar zona: ${err.localizedMessage ?: "intenta de nuevo"}")
                }
        }
    }

    fun deleteSpot(spotId: String) {
        viewModelScope.launch {
            repository.deleteSpot(spotId)
                .onSuccess { _events.tryEmit("Punto eliminado") }
                .onFailure { _events.tryEmit("No se pudo eliminar el punto") }
        }
    }

    fun saveTackle(item: TackleItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val result = if (item.id.isBlank()) {
                repository.addTackle(item).map { }
            } else {
                repository.updateTackle(item)
            }
            result
                .onSuccess {
                    _events.tryEmit(if (item.id.isBlank()) "Añadido a tu caja" else "Señuelo actualizado")
                    onDone()
                }
                .onFailure { err ->
                    _events.tryEmit("Error al guardar señuelo: ${err.localizedMessage ?: "intenta de nuevo"}")
                }
        }
    }

    fun toggleTackleFavorite(item: TackleItem) {
        viewModelScope.launch {
            repository.toggleTackleFavorite(item.id, !item.isFavorite)
                .onFailure { _events.tryEmit("No se pudo actualizar favorito") }
        }
    }

    fun deleteTackle(tackleId: String) {
        viewModelScope.launch {
            repository.deleteTackle(tackleId)
                .onSuccess { _events.tryEmit("Eliminado de tu caja") }
                .onFailure { _events.tryEmit("No se pudo eliminar") }
        }
    }

    // --- FRIENDS & COMPETITIONS ---

    fun addFriend(angler: AnglerProfile) {
        viewModelScope.launch {
            repository.addFriend(angler)
                .onSuccess { _events.tryEmit("${angler.displayName} añadido a tus amigos") }
                .onFailure { err -> _events.tryEmit(err.localizedMessage ?: "No se pudo agregar al amigo") }
        }
    }

    fun addFriendByCode(code: String) {
        viewModelScope.launch {
            repository.addFriendByCode(code)
                .onSuccess { angler -> _events.tryEmit("${angler.displayName} añadido a tus amigos") }
                .onFailure { err -> _events.tryEmit(err.localizedMessage ?: "Código no encontrado") }
        }
    }

    fun removeFriend(friendUid: String) {
        viewModelScope.launch {
            repository.removeFriend(friendUid)
                .onSuccess { _events.tryEmit("Amigo eliminado") }
                .onFailure { _events.tryEmit("No se pudo eliminar") }
        }
    }

    fun inspectFriendCatches(friend: FriendLink?) {
        _inspectedFriend.value = friend
    }

    fun createCompetition(
        title: String,
        selectedFriends: List<FriendLink>,
        targetSpecies: String,
        scoringMode: String,
        waterType: String,
        durationDays: Int,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val ends = now + durationDays.coerceIn(1, 30) * 86_400_000L
            repository.createCompetition(
                title = title,
                creatorName = currentUserDisplayName,
                friends = selectedFriends,
                targetSpecies = targetSpecies,
                scoringMode = scoringMode,
                waterType = waterType,
                startsAtEpochMs = now,
                endsAtEpochMs = ends,
            )
                .onSuccess { _events.tryEmit("Competencia iniciada con ${selectedFriends.size} amigo(s)") }
                .onFailure { err -> _events.tryEmit(err.localizedMessage ?: "No se pudo crear la competencia") }
        }
    }

    fun finalizeCompetition(competition: Competition, winnerName: String) {
        viewModelScope.launch {
            repository.finalizeCompetition(competition.id, winnerName)
                .onSuccess { _events.tryEmit("Competencia finalizada · Ganador: $winnerName") }
                .onFailure { err -> _events.tryEmit(err.localizedMessage ?: "No se pudo finalizar") }
        }
    }

    // --- AI FISHING ASSISTANT (5 QUESTIONS / DAY) ---

    fun askFishingAssistant(question: String) {
        val clean = question.trim()
        if (clean.isBlank() || _isAiReplying.value) return
        if (dailyAiQuestionsUsed.value >= FishingRepository.MAX_AI_QUESTIONS_PER_DAY) {
            _aiError.value = "Alcanzaste el límite de ${FishingRepository.MAX_AI_QUESTIONS_PER_DAY} preguntas por día. Vuelve mañana."
            return
        }

        val priorHistory = _aiMessages.value
        val userMsg = AiChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = clean,
        )
        _aiMessages.value = priorHistory + userMsg
        _aiError.value = null
        _isAiReplying.value = true

        viewModelScope.launch {
            try {
                val weatherSummary = (_weatherState.value as? UiState.Success)?.data?.let { w ->
                    "${w.locationLabel}, ${w.conditionLabel}, ${w.temperatureC}°C, viento ${w.windSpeedKmh} km/h ${w.windCardinal}, " +
                        "presión ${w.pressureHpa.toInt()} hPa (${w.pressureTrend})"
                }.orEmpty()
                val sol = _solunarForecast.value
                val contextInfo = buildString {
                    append("Fase lunar: ${sol.moonPhaseName} (${sol.illuminationPercent}%), actividad solunar ${sol.activityScore}/100 (${sol.activityLabel}). ")
                    if (weatherSummary.isNotBlank()) append("Clima: $weatherSummary.")
                }

                val answer = fishingAssistantService.askQuestion(
                    question = clean,
                    conditionsContext = contextInfo,
                    history = priorHistory,
                )

                repository.consumeAiQuestionQuota(todayDateKey()).getOrThrow()

                val aiMsg = AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = answer,
                )
                _aiMessages.value = _aiMessages.value + aiMsg
            } catch (e: AiQuotaExceededException) {
                _aiMessages.value = priorHistory
                _aiError.value = e.message ?: "Límite diario de 5 preguntas alcanzado."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Assistant error", e)
                _aiMessages.value = priorHistory
                _aiError.value = e.localizedMessage ?: "No se pudo obtener respuesta. Intenta de nuevo."
            } finally {
                _isAiReplying.value = false
            }
        }
    }

    fun emitMessage(message: String) {
        _events.tryEmit(message)
    }

    private fun todayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private companion object {
        const val TAG = "MareaVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val DEFAULT_LAT = -38.0055
        const val DEFAULT_LON = -57.5426
        const val DEFAULT_LABEL = "Costa Atlántica (Referencia)"
        const val DEFAULT_REFRESH_INTERVAL_SEC = 30
    }
}
