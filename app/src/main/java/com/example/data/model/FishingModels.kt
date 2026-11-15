package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class WaterType(val key: String, val label: String) {
    DULCE("dulce", "Agua Dulce"),
    SALADA("salada", "Agua Salada"),
    ESTUARIO("estuario", "Estuario");

    companion object {
        fun fromKey(key: String): WaterType = entries.firstOrNull { it.key == key } ?: DULCE
    }
}

enum class TackleCategory(val key: String, val label: String) {
    SUPERFICIE("superficie", "Superficie"),
    MEDIA_AGUA("media_agua", "Media Agua"),
    FONDO("fondo", "Fondo / Jig"),
    MOSCA("mosca", "Mosca"),
    CARNADA("carnada", "Carnada");

    companion object {
        fun fromKey(key: String): TackleCategory = entries.firstOrNull { it.key == key } ?: MEDIA_AGUA
    }
}

@IgnoreExtraProperties
data class CatchLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val species: String = "",
    val weightKg: Double = 0.0,
    val lengthCm: Double = 0.0,
    val lureOrBait: String = "",
    val spotName: String = "",
    val waterType: String = WaterType.DULCE.key,
    val weatherSummary: String = "",
    val released: Boolean = false,
    val notes: String = "",
    val photoPath: String = "",
    val caughtAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FishingSpot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val waterType: String = WaterType.DULCE.key,
    val targetSpecies: String = "",
    val accessNotes: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rating: Long = 5L,
    val photoPaths: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class TackleItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = TackleCategory.MEDIA_AGUA.key,
    val colorPattern: String = "",
    val weightGrams: Double = 0.0,
    val notes: String = "",
    val isFavorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

enum class CompetitionScoringMode(val key: String, val label: String) {
    PESO_MAYOR("peso_mayor", "Mayor peso (kg)"),
    TALLA_MAYOR("talla_mayor", "Mayor talla (cm)"),
    CANTIDAD("cantidad", "Más capturas");

    companion object {
        fun fromKey(key: String): CompetitionScoringMode =
            entries.firstOrNull { it.key == key } ?: PESO_MAYOR
    }
}

enum class CompetitionWaterFilter(val key: String, val label: String) {
    TODAS("todas", "Cualquier agua"),
    DULCE("dulce", "Agua Dulce"),
    SALADA("salada", "Agua Salada"),
    ESTUARIO("estuario", "Estuario");

    companion object {
        fun fromKey(key: String): CompetitionWaterFilter =
            entries.firstOrNull { it.key == key } ?: TODAS
    }
}

@IgnoreExtraProperties
data class AnglerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val friendCode: String = "",
    val discoverable: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FriendLink(
    @DocumentId val id: String = "",
    val userId: String = "",
    val friendUid: String = "",
    val friendName: String = "",
    val friendCode: String = "",
    val addedAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SharedCatch(
    @DocumentId val id: String = "",
    val userId: String = "",
    val anglerName: String = "",
    val species: String = "",
    val weightKg: Double = 0.0,
    val lengthCm: Double = 0.0,
    val lureOrBait: String = "",
    val spotName: String = "",
    val waterType: String = WaterType.DULCE.key,
    val released: Boolean = false,
    val isShared: Boolean = true,
    val caughtAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Competition(
    @DocumentId val id: String = "",
    val creatorUid: String = "",
    val creatorName: String = "",
    val title: String = "",
    val targetSpecies: String = "Libre",
    val scoringMode: String = CompetitionScoringMode.PESO_MAYOR.key,
    val waterType: String = CompetitionWaterFilter.TODAS.key,
    val participantUids: List<String> = emptyList(),
    val participantLabels: List<String> = emptyList(),
    val status: String = "activa",
    val winnerName: String? = null,
    val startsAtEpochMs: Long = 0L,
    val endsAtEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedParticipants(): List<Pair<String, String>> {
        if (participantLabels.size == participantUids.size && participantLabels.isNotEmpty()) {
            return participantLabels.map { raw ->
                val parts = raw.split("|", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else raw to "Pescador"
            }
        }
        return participantUids.map { uid -> uid to "Pescador" }
    }
}

@IgnoreExtraProperties
data class AiDailyQuota(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val questionsUsed: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
)
