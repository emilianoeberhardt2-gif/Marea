package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AiDailyQuota
import com.example.data.model.AnglerProfile
import com.example.data.model.CatchLog
import com.example.data.model.Competition
import com.example.data.model.FishingSpot
import com.example.data.model.FriendLink
import com.example.data.model.SharedCatch
import com.example.data.model.TackleItem
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FishingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth,
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google antes de acceder a Firestore.")
    }

    // --- CATCHES ---

    fun observeCatches(userId: String = auth.currentUser?.uid ?: ""): Flow<List<CatchLog>> = flow {
        val uid = userId.ifBlank { requireUserId() }
        val path = "users/$uid/catches"
        emitAll(
            db.collection("users").document(uid).collection("catches")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CatchLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.caughtAtEpochMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getCatch(catchId: String): Result<CatchLog?> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("catches").document(catchId)
        return try {
            val snap = docRef.get().await()
            val item = snap.toObject(CatchLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addCatch(
        catchLog: CatchLog,
        anglerDisplayName: String = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Pescador",
    ): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("catches").document()
        val caughtMs = if (catchLog.caughtAtEpochMs > 0L) catchLog.caughtAtEpochMs else System.currentTimeMillis()
        val speciesClean = catchLog.species.trim().take(60)
        val weightClean = catchLog.weightKg.coerceIn(0.0, 500.0)
        val lengthClean = catchLog.lengthCm.coerceIn(0.0, 500.0)
        val lureClean = catchLog.lureOrBait.trim().take(80)
        val spotClean = catchLog.spotName.trim().take(80)
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("species", speciesClean)
            put("weightKg", weightClean)
            put("lengthCm", lengthClean)
            put("lureOrBait", lureClean)
            put("spotName", spotClean)
            put("waterType", catchLog.waterType)
            put("weatherSummary", catchLog.weatherSummary.trim().take(120))
            put("released", catchLog.released)
            put("notes", catchLog.notes.trim().take(500))
            if (catchLog.photoPath.isNotBlank()) {
                put("photoPath", catchLog.photoPath.trim().take(120))
            }
            put("caughtAtEpochMs", caughtMs)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        val sharedPayload = mapOf<String, Any>(
            "userId" to uid,
            "anglerName" to anglerDisplayName.trim().ifBlank { "Pescador" }.take(60),
            "species" to speciesClean,
            "weightKg" to weightClean,
            "lengthCm" to lengthClean,
            "lureOrBait" to lureClean,
            "spotName" to spotClean,
            "waterType" to catchLog.waterType,
            "released" to catchLog.released,
            "isShared" to true,
            "caughtAtEpochMs" to caughtMs,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            runCatching {
                db.collection("shared_catches").document(docRef.id).set(sharedPayload).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateCatch(
        catchLog: CatchLog,
        anglerDisplayName: String = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Pescador",
    ): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        require(catchLog.id.isNotBlank()) { "Catch ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("catches").document(catchLog.id)
        val caughtMs = if (catchLog.caughtAtEpochMs > 0L) catchLog.caughtAtEpochMs else System.currentTimeMillis()
        val speciesClean = catchLog.species.trim().take(60)
        val weightClean = catchLog.weightKg.coerceIn(0.0, 500.0)
        val lengthClean = catchLog.lengthCm.coerceIn(0.0, 500.0)
        val lureClean = catchLog.lureOrBait.trim().take(80)
        val spotClean = catchLog.spotName.trim().take(80)
        val updates = mapOf<String, Any>(
            "species" to speciesClean,
            "weightKg" to weightClean,
            "lengthCm" to lengthClean,
            "lureOrBait" to lureClean,
            "spotName" to spotClean,
            "waterType" to catchLog.waterType,
            "weatherSummary" to catchLog.weatherSummary.trim().take(120),
            "released" to catchLog.released,
            "notes" to catchLog.notes.trim().take(500),
            "photoPath" to catchLog.photoPath.trim().take(120),
            "caughtAtEpochMs" to caughtMs,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.update(updates).await()
            runCatching {
                val sharedRef = db.collection("shared_catches").document(catchLog.id)
                val snap = sharedRef.get().await()
                if (snap.exists()) {
                    sharedRef.update(
                        mapOf<String, Any>(
                            "anglerName" to anglerDisplayName.trim().ifBlank { "Pescador" }.take(60),
                            "species" to speciesClean,
                            "weightKg" to weightClean,
                            "lengthCm" to lengthClean,
                            "lureOrBait" to lureClean,
                            "spotName" to spotClean,
                            "waterType" to catchLog.waterType,
                            "released" to catchLog.released,
                            "isShared" to true,
                            "caughtAtEpochMs" to caughtMs,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                } else {
                    sharedRef.set(
                        mapOf<String, Any>(
                            "userId" to uid,
                            "anglerName" to anglerDisplayName.trim().ifBlank { "Pescador" }.take(60),
                            "species" to speciesClean,
                            "weightKg" to weightClean,
                            "lengthCm" to lengthClean,
                            "lureOrBait" to lureClean,
                            "spotName" to spotClean,
                            "waterType" to catchLog.waterType,
                            "released" to catchLog.released,
                            "isShared" to true,
                            "caughtAtEpochMs" to caughtMs,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteCatch(catchId: String): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("catches").document(catchId)
        return try {
            docRef.delete().await()
            runCatching {
                db.collection("shared_catches").document(catchId).delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- SPOTS ---

    fun observeSpots(userId: String = auth.currentUser?.uid ?: ""): Flow<List<FishingSpot>> = flow {
        val uid = userId.ifBlank { requireUserId() }
        val path = "users/$uid/spots"
        emitAll(
            db.collection("users").document(uid).collection("spots")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FishingSpot::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.rating }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addSpot(spot: FishingSpot): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("spots").document()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("name", spot.name.trim().take(80))
            put("waterType", spot.waterType)
            put("targetSpecies", spot.targetSpecies.trim().take(120))
            put("accessNotes", spot.accessNotes.trim().take(500))
            spot.latitude?.let { put("latitude", it.coerceIn(-90.0, 90.0)) }
            spot.longitude?.let { put("longitude", it.coerceIn(-180.0, 180.0)) }
            put("rating", spot.rating.coerceIn(1L, 5L))
            put("photoPaths", spot.photoPaths.map { it.trim().take(120) }.filter { it.isNotEmpty() }.take(5))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSpot(spot: FishingSpot): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        require(spot.id.isNotBlank()) { "Spot ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("spots").document(spot.id)
        val updates = buildMap<String, Any> {
            put("name", spot.name.trim().take(80))
            put("waterType", spot.waterType)
            put("targetSpecies", spot.targetSpecies.trim().take(120))
            put("accessNotes", spot.accessNotes.trim().take(500))
            put("latitude", spot.latitude?.coerceIn(-90.0, 90.0) ?: FieldValue.delete())
            put("longitude", spot.longitude?.coerceIn(-180.0, 180.0) ?: FieldValue.delete())
            put("rating", spot.rating.coerceIn(1L, 5L))
            put("photoPaths", spot.photoPaths.map { it.trim().take(120) }.filter { it.isNotEmpty() }.take(5))
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSpot(spotId: String): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("spots").document(spotId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- TACKLE ---

    fun observeTackle(userId: String = auth.currentUser?.uid ?: ""): Flow<List<TackleItem>> = flow {
        val uid = userId.ifBlank { requireUserId() }
        val path = "users/$uid/tackle"
        emitAll(
            db.collection("users").document(uid).collection("tackle")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TackleItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.isFavorite }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addTackle(item: TackleItem): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("tackle").document()
        val payload = mapOf<String, Any>(
            "userId" to uid,
            "name" to item.name.trim().take(80),
            "category" to item.category,
            "colorPattern" to item.colorPattern.trim().take(60),
            "weightGrams" to item.weightGrams.coerceIn(0.0, 2000.0),
            "notes" to item.notes.trim().take(300),
            "isFavorite" to item.isFavorite,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTackle(item: TackleItem): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        require(item.id.isNotBlank()) { "Tackle ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("tackle").document(item.id)
        val updates = mapOf<String, Any>(
            "name" to item.name.trim().take(80),
            "category" to item.category,
            "colorPattern" to item.colorPattern.trim().take(60),
            "weightGrams" to item.weightGrams.coerceIn(0.0, 2000.0),
            "notes" to item.notes.trim().take(300),
            "isFavorite" to item.isFavorite,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleTackleFavorite(tackleId: String, favorite: Boolean): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("tackle").document(tackleId)
        return try {
            docRef.update(
                mapOf(
                    "isFavorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTackle(tackleId: String): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("tackle").document(tackleId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- ANGLER PROFILES & FRIENDS ---

    suspend fun ensureAnglerProfile(displayName: String): Result<AnglerProfile> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("anglers").document(uid)
        val cleanName = displayName.trim().ifBlank { "Pescador" }.take(60)
        val code = buildFriendCode(uid)
        return try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "friendCode" to code,
                    "discoverable" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
                Result.success(
                    AnglerProfile(
                        id = uid,
                        userId = uid,
                        displayName = cleanName,
                        friendCode = code,
                        discoverable = true,
                    )
                )
            } else {
                val current = existingSnap.toObject(
                    AnglerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (current != null && current.displayName != cleanName) {
                    docRef.update(
                        mapOf<String, Any>(
                            "displayName" to cleanName,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
                Result.success(
                    current?.copy(displayName = cleanName) ?: AnglerProfile(
                        id = uid,
                        userId = uid,
                        displayName = cleanName,
                        friendCode = code,
                        discoverable = true,
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeDiscoverableAnglers(): Flow<List<AnglerProfile>> = flow {
        val uid = requireUserId()
        val path = "anglers"
        emitAll(
            db.collection("anglers")
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AnglerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.userId != uid }
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeFriends(userId: String = auth.currentUser?.uid ?: ""): Flow<List<FriendLink>> = flow {
        val uid = userId.ifBlank { requireUserId() }
        val path = "users/$uid/friends"
        emitAll(
            db.collection("users").document(uid).collection("friends")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FriendLink::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.friendName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addFriend(angler: AnglerProfile): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        if (angler.userId == uid) {
            return Result.failure(IllegalArgumentException("No puedes agregarte a ti mismo como amigo."))
        }
        val docRef = db.collection("users").document(uid).collection("friends").document(angler.userId)
        val payload = mapOf<String, Any>(
            "userId" to uid,
            "friendUid" to angler.userId,
            "friendName" to angler.displayName.trim().ifBlank { "Pescador" }.take(60),
            "friendCode" to angler.friendCode.trim().ifBlank { buildFriendCode(angler.userId) }.take(16),
            "addedAtEpochMs" to System.currentTimeMillis(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addFriendByCode(code: String): Result<AnglerProfile> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val normalized = code.trim().uppercase()
        if (normalized.length < 4) {
            return Result.failure(IllegalArgumentException("Introduce un código de amigo válido."))
        }
        return try {
            val snap = db.collection("anglers")
                .whereEqualTo("discoverable", true)
                .whereEqualTo("friendCode", normalized)
                .limit(1)
                .get()
                .await()
            val match = snap.toObjects(
                AnglerProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).firstOrNull()
                ?: return Result.failure(IllegalArgumentException("No se encontró ningún pescador con ese código."))
            if (match.userId == uid) {
                return Result.failure(IllegalArgumentException("Ese es tu propio código de pescador."))
            }
            addFriend(match).getOrThrow()
            Result.success(match)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "anglers")
            Result.failure(e)
        }
    }

    suspend fun removeFriend(friendUid: String): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("friends").document(friendUid)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- SHARED CATCHES ---

    fun observeSharedCatchesByUser(targetUid: String): Flow<List<SharedCatch>> = flow {
        requireUserId()
        val path = "shared_catches"
        emitAll(
            db.collection("shared_catches")
                .whereEqualTo("userId", targetUid)
                .whereEqualTo("isShared", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SharedCatch::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.caughtAtEpochMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllSharedCatches(): Flow<List<SharedCatch>> = flow {
        requireUserId()
        val path = "shared_catches"
        emitAll(
            db.collection("shared_catches")
                .whereEqualTo("isShared", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SharedCatch::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.caughtAtEpochMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- COMPETITIONS ---

    fun observeCompetitions(userId: String = auth.currentUser?.uid ?: ""): Flow<List<Competition>> = flow {
        val uid = userId.ifBlank { requireUserId() }
        val path = "competitions"
        emitAll(
            db.collection("competitions")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Competition::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.startsAtEpochMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createCompetition(
        title: String,
        creatorName: String,
        friends: List<FriendLink>,
        targetSpecies: String,
        scoringMode: String,
        waterType: String,
        startsAtEpochMs: Long,
        endsAtEpochMs: Long,
    ): Result<String> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val distinctFriends = friends.filter { it.friendUid != uid }.distinctBy { it.friendUid }.take(9)
        if (distinctFriends.isEmpty()) {
            return Result.failure(IllegalArgumentException("Selecciona al menos un amigo para competir."))
        }
        val creatorLabel = creatorName.trim().ifBlank { "Pescador" }.take(35)
        val participantUids = listOf(uid) + distinctFriends.map { it.friendUid }
        val participantLabels = listOf("$uid|$creatorLabel") + distinctFriends.map {
            "${it.friendUid}|${it.friendName.trim().ifBlank { "Amigo" }.take(35)}"
        }
        val docRef = db.collection("competitions").document()
        val payload = mapOf<String, Any>(
            "creatorUid" to uid,
            "creatorName" to creatorLabel.take(60),
            "title" to title.trim().ifBlank { "Reto de Pesca" }.take(80),
            "targetSpecies" to targetSpecies.trim().ifBlank { "Libre" }.take(60),
            "scoringMode" to scoringMode,
            "waterType" to waterType,
            "participantUids" to participantUids,
            "participantLabels" to participantLabels,
            "status" to "activa",
            "startsAtEpochMs" to startsAtEpochMs,
            "endsAtEpochMs" to endsAtEpochMs.coerceAtLeast(startsAtEpochMs),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun finalizeCompetition(competitionId: String, winnerName: String): Result<Unit> {
        runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("competitions").document(competitionId)
        val updates = mapOf<String, Any>(
            "status" to "finalizada",
            "winnerName" to winnerName.trim().ifBlank { "Sin capturas" }.take(80),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- AI ASSISTANT DAILY QUOTA (MAX 5 / DAY) ---

    fun observeDailyAiQuota(
        userId: String = auth.currentUser?.uid ?: "",
        dateKey: String,
    ): Flow<Int> = flow {
        val uid = userId.ifBlank { requireUserId() }
        val path = "users/$uid/ai_quota/$dateKey"
        emitAll(
            db.collection("users").document(uid).collection("ai_quota").document(dateKey)
                .snapshots()
                .map { snapshot ->
                    val quota = snapshot.toObject(
                        AiDailyQuota::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    (quota?.questionsUsed ?: 0).coerceIn(0, MAX_AI_QUESTIONS_PER_DAY)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun consumeAiQuestionQuota(dateKey: String): Result<Int> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("ai_quota").document(dateKey)
        return try {
            val updatedCount = db.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                if (!snap.exists()) {
                    transaction.set(
                        docRef,
                        mapOf<String, Any>(
                            "userId" to uid,
                            "dateKey" to dateKey,
                            "questionsUsed" to 1,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                    1
                } else {
                    val current = (snap.getLong("questionsUsed") ?: 0L).toInt()
                    if (current >= MAX_AI_QUESTIONS_PER_DAY) {
                        throw AiQuotaExceededException("Alcanzaste el límite de $MAX_AI_QUESTIONS_PER_DAY preguntas de hoy.")
                    }
                    val next = current + 1
                    transaction.update(
                        docRef,
                        mapOf<String, Any>(
                            "questionsUsed" to next,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                    next
                }
            }.await()
            Result.success(updatedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val MAX_AI_QUESTIONS_PER_DAY = 5

        fun buildFriendCode(uid: String): String {
            val clean = uid.filter { it.isLetterOrDigit() }.uppercase()
            return if (clean.length >= 6) clean.take(6) else clean.padEnd(6, 'X')
        }
    }
}

class AiQuotaExceededException(message: String) : IllegalStateException(message)
