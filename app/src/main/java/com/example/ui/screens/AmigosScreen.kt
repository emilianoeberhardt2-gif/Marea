package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AnglerProfile
import com.example.data.model.Competition
import com.example.data.model.CompetitionScoringMode
import com.example.data.model.CompetitionWaterFilter
import com.example.data.model.FriendLink
import com.example.data.model.SharedCatch
import com.example.data.model.WaterType
import com.example.data.repository.FishingRepository
import com.example.ui.theme.BrassGold
import com.example.ui.theme.ReleaseGreen
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CompetitionStanding(
    val uid: String,
    val name: String,
    val catchCount: Int,
    val bestWeightKg: Double,
    val bestLengthCm: Double,
    val scoreDisplay: String,
    val sortValue: Double,
)

@Composable
fun AmigosScreen(
    currentUserId: String,
    userDisplayName: String,
    friendsState: UiState<List<FriendLink>>,
    discoverableAnglersState: UiState<List<AnglerProfile>>,
    competitionsState: UiState<List<Competition>>,
    allSharedCatchesState: UiState<List<SharedCatch>>,
    inspectedFriend: FriendLink?,
    inspectedFriendCatchesState: UiState<List<SharedCatch>>,
    onAddFriend: (AnglerProfile) -> Unit,
    onAddFriendByCode: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onInspectFriendCatches: (FriendLink?) -> Unit,
    onCreateCompetition: (
        title: String,
        selectedFriends: List<FriendLink>,
        targetSpecies: String,
        scoringMode: String,
        waterType: String,
        durationDays: Int,
    ) -> Unit,
    onFinalizeCompetition: (Competition, String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val myFriendCode = remember(currentUserId) { FishingRepository.buildFriendCode(currentUserId) }
    var codeInput by rememberSaveable { mutableStateOf("") }
    val selectedFriendUids = remember { mutableStateListOf<String>() }
    var showCreateCompetitionSheet by remember { mutableStateOf(false) }

    val friends = (friendsState as? UiState.Success)?.data.orEmpty()
    val friendUidSet = remember(friends) { friends.map { it.friendUid }.toSet() }
    val discoverable = (discoverableAnglersState as? UiState.Success)?.data.orEmpty()
        .filter { it.userId !in friendUidSet && it.userId != currentUserId }
    val competitions = (competitionsState as? UiState.Success)?.data.orEmpty()
    val sharedCatches = (allSharedCatchesState as? UiState.Success)?.data.orEmpty()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                MyAnglerCodeCard(
                    userDisplayName = userDisplayName,
                    friendCode = myFriendCode,
                    codeInput = codeInput,
                    onCodeInputChange = { codeInput = it.uppercase().take(16) },
                    onCopyCode = {
                        clipboard.setText(AnnotatedString(myFriendCode))
                        onShowMessage("Código $myFriendCode copiado")
                    },
                    onAddByCode = {
                        val trimmed = codeInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddFriendByCode(trimmed)
                            codeInput = ""
                        }
                    },
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Mis Amigos (${friends.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (friends.isNotEmpty()) {
                        Button(
                            onClick = { showCreateCompetitionSheet = true },
                            modifier = Modifier.testTag("create_competition_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (selectedFriendUids.isEmpty()) {
                                    "Nueva competencia"
                                } else {
                                    "Competir (${selectedFriendUids.size})"
                                }
                            )
                        }
                    }
                }
            }

            when (friendsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp))
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = friendsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                is UiState.Success -> {
                    if (friends.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "Aún no tienes amigos agregados",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Agrega pescadores por código o desde la lista inferior para ver sus capturas y armar competencias.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(friends, key = { it.friendUid }) { friend ->
                            val isSelected = friend.friendUid in selectedFriendUids
                            FriendCard(
                                friend = friend,
                                isSelectedForComp = isSelected,
                                onToggleSelect = {
                                    if (isSelected) {
                                        selectedFriendUids.remove(friend.friendUid)
                                    } else {
                                        selectedFriendUids.add(friend.friendUid)
                                    }
                                },
                                onViewCatches = { onInspectFriendCatches(friend) },
                                onRemove = {
                                    selectedFriendUids.remove(friend.friendUid)
                                    onRemoveFriend(friend.friendUid)
                                },
                            )
                        }
                    }
                }
            }

            if (discoverable.isNotEmpty()) {
                item {
                    Text(
                        text = "Pescadores en Marea",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(discoverable, key = { it.userId }) { angler ->
                    DiscoverableAnglerCard(
                        angler = angler,
                        onAdd = { onAddFriend(angler) },
                    )
                }
            }

            item {
                Text(
                    text = "Competencias (${competitions.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            when (competitionsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = competitionsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                is UiState.Success -> {
                    if (competitions.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.EmojiEvents,
                                        contentDescription = null,
                                        tint = BrassGold,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Selecciona uno o más amigos arriba y toca Competir para iniciar un torneo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(competitions, key = { it.id }) { comp ->
                            val standings = remember(comp, sharedCatches) {
                                computeStandings(comp, sharedCatches)
                            }
                            CompetitionCard(
                                competition = comp,
                                standings = standings,
                                onFinalize = {
                                    val leader = standings.firstOrNull { it.catchCount > 0 }?.name ?: "Sin capturas"
                                    onFinalizeCompetition(comp, leader)
                                },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showCreateCompetitionSheet) {
        val initialSelection = friends.filter { it.friendUid in selectedFriendUids }.ifEmpty {
            friends.take(1)
        }
        CreateCompetitionSheet(
            friends = friends,
            initialSelectedUids = initialSelection.map { it.friendUid }.toSet(),
            onDismiss = { showCreateCompetitionSheet = false },
            onCreate = { title, chosenFriends, species, mode, water, days ->
                onCreateCompetition(title, chosenFriends, species, mode, water, days)
                selectedFriendUids.clear()
                showCreateCompetitionSheet = false
            },
        )
    }

    if (inspectedFriend != null) {
        FriendCatchesSheet(
            friend = inspectedFriend,
            catchesState = inspectedFriendCatchesState,
            onDismiss = { onInspectFriendCatches(null) },
        )
    }
}

@Composable
private fun MyAnglerCodeCard(
    userDisplayName: String,
    friendCode: String,
    codeInput: String,
    onCodeInputChange: (String) -> Unit,
    onCopyCode: () -> Unit,
    onAddByCode: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = userDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Tu código de amigo: $friendCode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onCopyCode,
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = onCodeInputChange,
                    label = { Text("Código de amigo") },
                    placeholder = { Text("Ej. A1B2C3") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("friend_code_input"),
                )
                Button(
                    onClick = onAddByCode,
                    enabled = codeInput.trim().length >= 4,
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("add_friend_by_code_button"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar")
                }
            }
        }
    }
}

@Composable
private fun FriendCard(
    friend: FriendLink,
    isSelectedForComp: Boolean,
    onToggleSelect: () -> Unit,
    onViewCatches: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelectedForComp) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelectedForComp) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("friend_card_${friend.friendUid}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isSelectedForComp,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.testTag("select_friend_${friend.friendUid}"),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.friendName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Código: ${friend.friendCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onViewCatches,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("view_friend_catches_${friend.friendUid}"),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Capturas")
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Quitar amigo",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DiscoverableAnglerCard(
    angler: AnglerProfile,
    onAdd: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = angler.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Código: ${angler.friendCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Agregar")
            }
        }
    }
}

@Composable
private fun CompetitionCard(
    competition: Competition,
    standings: List<CompetitionStanding>,
    onFinalize: () -> Unit,
) {
    val isActive = competition.status == "activa"
    val modeLabel = CompetitionScoringMode.fromKey(competition.scoringMode).label
    val waterLabel = CompetitionWaterFilter.fromKey(competition.waterType).label
    val dateFmt = remember { SimpleDateFormat("dd MMM", Locale("es", "ES")) }
    val dateRange = "${dateFmt.format(Date(competition.startsAtEpochMs))} – ${dateFmt.format(Date(competition.endsAtEpochMs))}"

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("competition_card_${competition.id}"),
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
                    Text(
                        text = competition.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$modeLabel · ${competition.targetSpecies} · $waterLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Fechas: $dateRange",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isActive) {
                        ReleaseGreen.copy(alpha = 0.14f)
                    } else {
                        BrassGold.copy(alpha = 0.16f)
                    },
                ) {
                    Text(
                        text = if (isActive) "En curso" else "Finalizada",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) ReleaseGreen else BrassGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                standings.forEachIndexed { index, row ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (index == 0 && row.catchCount > 0) {
                            BrassGold.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${index + 1}°",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (index == 0) BrassGold else MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = row.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            Text(
                                text = row.scoreDisplay,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            if (isActive) {
                OutlinedButton(
                    onClick = onFinalize,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("finalize_competition_${competition.id}"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Finalizar y proclamar ganador")
                }
            } else if (!competition.winnerName.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrassGold.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = BrassGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ganador: ${competition.winnerName}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendCatchesSheet(
    friend: FriendLink,
    catchesState: UiState<List<SharedCatch>>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es", "ES")) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Phishing,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Capturas de ${friend.friendName}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Código ${friend.friendCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (catchesState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = catchesState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is UiState.Success -> {
                    val items = catchesState.data
                    if (items.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "${friend.friendName} todavía no tiene capturas registradas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        val maxWeight = items.maxOfOrNull { it.weightKg } ?: 0.0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("${items.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Capturas", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BrassGold.copy(alpha = 0.16f),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("%.2f kg".format(maxWeight), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Mejor peso", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(items, key = { it.id }) { catchItem ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = catchItem.species,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = "%.2f kg · %.0f cm".format(catchItem.weightKg, catchItem.lengthCm),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        Text(
                                            text = "${WaterType.fromKey(catchItem.waterType).label}" +
                                                if (catchItem.spotName.isNotBlank()) " · ${catchItem.spotName}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (catchItem.lureOrBait.isNotBlank()) {
                                            Text(
                                                text = "Señuelo/carnada: ${catchItem.lureOrBait}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = dateFmt.format(Date(catchItem.caughtAtEpochMs)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (catchItem.released) {
                                                Text(
                                                    text = "Captura y suelta",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ReleaseGreen,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCompetitionSheet(
    friends: List<FriendLink>,
    initialSelectedUids: Set<String>,
    onDismiss: () -> Unit,
    onCreate: (
        title: String,
        selectedFriends: List<FriendLink>,
        targetSpecies: String,
        scoringMode: String,
        waterType: String,
        durationDays: Int,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable { mutableStateOf("Reto de Pesca") }
    var targetSpecies by rememberSaveable { mutableStateOf("Libre") }
    var scoringMode by rememberSaveable { mutableStateOf(CompetitionScoringMode.PESO_MAYOR.key) }
    var waterType by rememberSaveable { mutableStateOf(CompetitionWaterFilter.TODAS.key) }
    var durationDays by rememberSaveable { mutableIntStateOf(3) }
    val selectedUids = remember { mutableStateListOf<String>().apply { addAll(initialSelectedUids) } }

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
                text = "Nueva Competencia entre Amigos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                label = { Text("Nombre del torneo") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("competition_title_input"),
            )

            OutlinedTextField(
                value = targetSpecies,
                onValueChange = { targetSpecies = it.take(60) },
                label = { Text("Especie objetivo (o 'Libre')") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Regla de puntuación",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompetitionScoringMode.entries.forEach { mode ->
                    FilterChip(
                        selected = scoringMode == mode.key,
                        onClick = { scoringMode = mode.key },
                        label = { Text(mode.label) },
                    )
                }
            }

            Text(
                text = "Tipo de agua",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompetitionWaterFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = waterType == filter.key,
                        onClick = { waterType = filter.key },
                        label = { Text(filter.label) },
                    )
                }
            }

            Text(
                text = "Duración",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "24 horas", 3 to "3 días", 7 to "7 días").forEach { (days, label) ->
                    FilterChip(
                        selected = durationDays == days,
                        onClick = { durationDays = days },
                        label = { Text(label) },
                    )
                }
            }

            Text(
                text = "Participantes (${selectedUids.size} amigos seleccionados)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                friends.forEach { friend ->
                    val checked = friend.friendUid in selectedUids
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (friend.friendUid !in selectedUids) selectedUids.add(friend.friendUid)
                                    } else {
                                        selectedUids.remove(friend.friendUid)
                                    }
                                },
                            )
                            Text(
                                text = "${friend.friendName} (${friend.friendCode})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val chosen = friends.filter { it.friendUid in selectedUids }
                    if (chosen.isNotEmpty() && title.isNotBlank()) {
                        onCreate(title, chosen, targetSpecies.ifBlank { "Libre" }, scoringMode, waterType, durationDays)
                    }
                },
                enabled = selectedUids.isNotEmpty() && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_create_competition_button"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Iniciar Competencia")
            }
        }
    }
}

private fun computeStandings(
    competition: Competition,
    allSharedCatches: List<SharedCatch>,
): List<CompetitionStanding> {
    val participants = competition.resolvedParticipants()
    val relevantCatches = allSharedCatches.filter { c ->
        c.userId in competition.participantUids &&
            c.caughtAtEpochMs in competition.startsAtEpochMs..competition.endsAtEpochMs &&
            (competition.waterType == CompetitionWaterFilter.TODAS.key || c.waterType == competition.waterType) &&
            (competition.targetSpecies.equals("Libre", ignoreCase = true) ||
                c.species.contains(competition.targetSpecies, ignoreCase = true))
    }

    return participants.map { (uid, name) ->
        val userCatches = relevantCatches.filter { it.userId == uid }
        val bestWeight = userCatches.maxOfOrNull { it.weightKg } ?: 0.0
        val bestLength = userCatches.maxOfOrNull { it.lengthCm } ?: 0.0
        val count = userCatches.size
        val (sortVal, scoreLabel) = when (competition.scoringMode) {
            CompetitionScoringMode.TALLA_MAYOR.key -> bestLength to if (count > 0) "%.0f cm (%d)".format(bestLength, count) else "0 cm"
            CompetitionScoringMode.CANTIDAD.key -> count.toDouble() to "$count capturas"
            else -> bestWeight to if (count > 0) "%.2f kg (%d)".format(bestWeight, count) else "0.00 kg"
        }
        CompetitionStanding(
            uid = uid,
            name = name,
            catchCount = count,
            bestWeightKg = bestWeight,
            bestLengthCm = bestLength,
            scoreDisplay = scoreLabel,
            sortValue = sortVal,
        )
    }.sortedByDescending { it.sortValue }
}
