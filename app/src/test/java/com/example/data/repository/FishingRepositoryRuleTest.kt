package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CatchLog
import com.example.data.model.FishingSpot
import com.example.data.model.TackleCategory
import com.example.data.model.TackleItem
import com.example.data.model.WaterType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FishingRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun catchCrud_authenticatedOwner_succeedsAndEmitsRealtimeUpdates() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = FishingRepository(firestore, auth)

        val catchId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addCatch(
                CatchLog(
                    species = "Lubina",
                    weightKg = 2.6,
                    lengthCm = 58.0,
                    lureOrBait = "Paseante 110mm",
                    spotName = "Espigón Norte",
                    waterType = WaterType.SALADA.key,
                    weatherSummary = "1016 hPa",
                    released = true,
                    notes = "Amanecer con marejada",
                    photoPath = "catch_lubina.jpg",
                    caughtAtEpochMs = System.currentTimeMillis(),
                )
            ).getOrThrow()
        }
        assertTrue(catchId.isNotEmpty())

        val catches = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCatches().first { list -> list.any { it.id == catchId } }
        }
        val created = catches.first { it.id == catchId }
        assertEquals("Lubina", created.species)

        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateCatch(created.copy(weightKg = 2.8, notes = "Editado"))
        }
        assertTrue(updateRes.isSuccess)

        val deleteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteCatch(catchId)
        }
        assertTrue(deleteRes.isSuccess)
    }

    @Test
    fun spotAndTackleCrud_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = FishingRepository(firestore, auth)

        val spotId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addSpot(
                FishingSpot(
                    name = "Desembocadura Sur",
                    waterType = WaterType.ESTUARIO.key,
                    targetSpecies = "Corvina, Lenguado",
                    accessNotes = "Mejor con marea subiendo",
                    latitude = -36.5,
                    longitude = -56.7,
                    rating = 5L,
                    photoPaths = listOf("spot_desembocadura.jpg"),
                )
            ).getOrThrow()
        }
        val spots = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSpots().first { list -> list.any { it.id == spotId } }
        }
        assertEquals("Desembocadura Sur", spots.first { it.id == spotId }.name)

        val tackleId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addTackle(
                TackleItem(
                    name = "Jig Bucktail 28g",
                    category = TackleCategory.FONDO.key,
                    colorPattern = "Blanco / Rojo",
                    weightGrams = 28.0,
                    notes = "Canal profundo",
                    isFavorite = true,
                )
            ).getOrThrow()
        }
        val tackle = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTackle().first { list -> list.any { it.id == tackleId } }
        }
        assertEquals("Jig Bucktail 28g", tackle.first { it.id == tackleId }.name)
    }

    @Test
    fun crossUserIsolation_bobCannotReadAlicesCatch() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FishingRepository(firestore, auth)
        val catchId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addCatch(
                CatchLog(
                    species = "Dorado",
                    weightKg = 5.2,
                    lengthCm = 72.0,
                    waterType = WaterType.DULCE.key,
                    caughtAtEpochMs = System.currentTimeMillis(),
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FishingRepository(firestore, auth)
        val docResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(aliceUid)
                .collection("catches").document(catchId)
                .run {
                    runCatching {
                        com.google.android.gms.tasks.Tasks.await(get())
                    }
                }
        }
        // Bob calling his own repo works, reading Alice's path fails with PERMISSION_DENIED
        val exception = docResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(exception)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception?.code)
        assertNotNull(bobRepo)
    }

    @Test
    fun observeCatches_unauthenticatedUser_failsFastOrPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = FishingRepository(firestore, auth)
        var failed = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeCatches("some_uid_123").first()
            }
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull(firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun friendsAndSharedCatches_bobAddsAliceAndViewsHerCatches() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FishingRepository(firestore, auth)
        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureAnglerProfile("Alicia Pescadora").getOrThrow()
        }
        assertTrue(aliceProfile.friendCode.length >= 4)

        val catchId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addCatch(
                catchLog = CatchLog(
                    species = "Corvina",
                    weightKg = 4.1,
                    lengthCm = 68.0,
                    lureOrBait = "Sardina fresca",
                    spotName = "Canal Sur",
                    waterType = WaterType.SALADA.key,
                    released = false,
                    caughtAtEpochMs = System.currentTimeMillis(),
                ),
                anglerDisplayName = "Alicia Pescadora",
            ).getOrThrow()
        }
        assertTrue(catchId.isNotEmpty())

        signInTestUser(BOB_EMAIL)
        val bobRepo = FishingRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureAnglerProfile("Bob").getOrThrow()
            bobRepo.addFriendByCode(aliceProfile.friendCode).getOrThrow()
        }

        val friends = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeFriends().first { it.any { f -> f.friendUid == aliceUid } }
        }
        assertEquals("Alicia Pescadora", friends.first { it.friendUid == aliceUid }.friendName)

        val aliceCatches = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeSharedCatchesByUser(aliceUid).first { it.any { c -> c.id == catchId } }
        }
        assertEquals("Corvina", aliceCatches.first { it.id == catchId }.species)
    }

    @Test
    fun competitionLifecycle_terminalStateLockedAfterFinalize() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FishingRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureAnglerProfile("Alicia").getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = FishingRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureAnglerProfile("Bob").getOrThrow()
        }

        signInTestUser(ALICE_EMAIL)
        val aliceAsFriend = com.example.data.model.FriendLink(
            userId = aliceUid,
            friendUid = bobUid,
            friendName = "Bob",
            friendCode = FishingRepository.buildFriendCode(bobUid),
            addedAtEpochMs = System.currentTimeMillis(),
        )
        val now = System.currentTimeMillis()
        val compId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createCompetition(
                title = "Copa Río",
                creatorName = "Alicia",
                friends = listOf(aliceAsFriend),
                targetSpecies = "Dorado",
                scoringMode = "peso_mayor",
                waterType = "dulce",
                startsAtEpochMs = now,
                endsAtEpochMs = now + 86_400_000L,
            ).getOrThrow()
        }

        val comps = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeCompetitions(aliceUid).first { it.any { c -> c.id == compId } }
        }
        assertEquals("activa", comps.first { c -> c.id == compId }.status)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.finalizeCompetition(compId, "Alicia").getOrThrow()
        }

        val secondUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.finalizeCompetition(compId, "Bob")
        }
        assertTrue(secondUpdate.isFailure)
        val firestoreEx = secondUpdate.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun aiDailyQuota_enforcesMax5PerDayAndBlocksDelete() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FishingRepository(firestore, auth)
        val dateKey = "2026-11-14"

        for (expected in 1..5) {
            val count = withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.consumeAiQuestionQuota(dateKey).getOrThrow()
            }
            assertEquals(expected, count)
        }

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeDailyAiQuota(aliceUid, dateKey).first { it == 5 }
        }
        assertEquals(5, observed)

        val sixth = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.consumeAiQuestionQuota(dateKey)
        }
        assertTrue(sixth.isFailure)
        assertTrue(sixth.exceptionOrNull() is AiQuotaExceededException)

        val bypassWrite = runCatching {
            com.google.android.gms.tasks.Tasks.await(
                firestore.collection("users").document(aliceUid)
                    .collection("ai_quota").document(dateKey)
                    .update("questionsUsed", 6)
            )
        }
        assertTrue(bypassWrite.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            bypassWrite.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    private companion object {
        const val ALICE_EMAIL = "alice_pesca@test.com"
        const val BOB_EMAIL = "bob_pesca@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
