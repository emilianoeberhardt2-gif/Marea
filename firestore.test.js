const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_angler_123";
const BOB_UID = "bob_angler_456";
const CHARLIE_UID = "charlie_angler_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validCatchPayload(uid, overrides = {}) {
  return {
    userId: uid,
    species: "Lubina",
    weightKg: 2.4,
    lengthCm: 56.0,
    lureOrBait: "Paseante 110mm",
    spotName: "Espigón Norte",
    waterType: "salada",
    weatherSummary: "1016 hPa · Viento 12 km/h",
    released: true,
    notes: "Picada al amanecer",
    photoPath: "catch_1.jpg",
    caughtAtEpochMs: 1731600000000,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function validSpotPayload(uid, overrides = {}) {
  return {
    userId: uid,
    name: "Laguna del Sauce",
    waterType: "dulce",
    targetSpecies: "Tararira, Pejerrey",
    accessNotes: "Orilla oeste con juncos",
    latitude: -34.87,
    longitude: -55.05,
    rating: 5,
    photoPaths: ["spot_1.jpg"],
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function validTacklePayload(uid, overrides = {}) {
  return {
    userId: uid,
    name: "Rapala X-Rap 10",
    category: "media_agua",
    colorPattern: "Sardina",
    weightGrams: 13.0,
    notes: "Ideal con agua clara",
    isFavorite: true,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function validAnglerPayload(uid, overrides = {}) {
  return {
    userId: uid,
    displayName: "Alicia Pescadora",
    friendCode: "ALICIA1",
    discoverable: true,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function validFriendPayload(uid, friendUid, overrides = {}) {
  return {
    userId: uid,
    friendUid,
    friendName: "Bob",
    friendCode: "BOB123",
    addedAtEpochMs: 1731600000000,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function validSharedCatchPayload(uid, overrides = {}) {
  return {
    userId: uid,
    anglerName: "Alicia Pescadora",
    species: "Lubina",
    weightKg: 2.4,
    lengthCm: 56.0,
    lureOrBait: "Paseante 110mm",
    spotName: "Espigón Norte",
    waterType: "salada",
    released: true,
    isShared: true,
    caughtAtEpochMs: 1731600000000,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function validCompetitionPayload(creatorUid, overrides = {}) {
  return {
    creatorUid,
    creatorName: "Alicia Pescadora",
    title: "Reto Fin de Semana",
    targetSpecies: "Lubina",
    scoringMode: "peso_mayor",
    waterType: "salada",
    participantUids: [creatorUid, BOB_UID],
    participantLabels: [`${creatorUid}|Alicia`, `${BOB_UID}|Bob`],
    status: "activa",
    startsAtEpochMs: 1731600000000,
    endsAtEpochMs: 1731700000000,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied read/write on catches, spots, tackle, friends, competitions, ai_quota", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/catches`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/spots`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/tackle`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/friends`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/ai_quota`).get());
  await assertFails(unauthDb.collection("anglers").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("shared_catches").where("isShared", "==", true).get());
  await assertFails(
    unauthDb
      .collection("competitions")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/catches`)
      .doc("catch_1")
      .set(validCatchPayload(ALICE_UID))
  );
});

test("Cross-user isolation: Bob cannot access Alice's private collections, friends, ai_quota, or non-participating competitions", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection(`users/${ALICE_UID}/catches`).doc("alice_catch_1").set(validCatchPayload(ALICE_UID));
    await adminDb.collection(`users/${ALICE_UID}/friends`).doc(CHARLIE_UID).set(validFriendPayload(ALICE_UID, CHARLIE_UID));
    await adminDb.collection(`users/${ALICE_UID}/ai_quota`).doc("2026-11-14").set({
      userId: ALICE_UID,
      dateKey: "2026-11-14",
      questionsUsed: 2,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    await adminDb.collection("shared_catches").doc("alice_catch_1").set(validSharedCatchPayload(ALICE_UID));
    await adminDb.collection("competitions").doc("comp_ac").set(
      validCompetitionPayload(ALICE_UID, {
        participantUids: [ALICE_UID, CHARLIE_UID],
        participantLabels: [`${ALICE_UID}|Alicia`, `${CHARLIE_UID}|Charlie`],
      })
    );
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/catches`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/friends`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/ai_quota`).doc("2026-11-14").get());
  await assertFails(bobDb.collection("anglers").doc(ALICE_UID).set(validAnglerPayload(ALICE_UID)));
  await assertFails(bobDb.collection("shared_catches").doc("alice_catch_1").update({ species: "Hacked" }));
  await assertFails(bobDb.collection("shared_catches").doc("alice_catch_1").delete());
  await assertFails(bobDb.collection("competitions").doc("comp_ac").get());
  await assertFails(bobDb.collection("competitions").where("participantUids", "array-contains", ALICE_UID).get());
});

test("Friends, Shared Catches, Competitions, and AI Quota (max 5/day) work cleanly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Angler profiles & friend links
  await assertSucceeds(aliceDb.collection("anglers").doc(ALICE_UID).set(validAnglerPayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("anglers").doc(BOB_UID).set(validAnglerPayload(BOB_UID, { displayName: "Bob", friendCode: "BOB123" })));
  await assertSucceeds(bobDb.collection("anglers").where("discoverable", "==", true).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/friends`).doc(BOB_UID).set(validFriendPayload(ALICE_UID, BOB_UID)));
  await assertFails(aliceDb.collection(`users/${ALICE_UID}/friends`).doc(ALICE_UID).set(validFriendPayload(ALICE_UID, ALICE_UID)));

  // Shared catch readable by Bob, editable only by Alice
  await assertSucceeds(aliceDb.collection("shared_catches").doc("c1").set(validSharedCatchPayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("shared_catches").where("userId", "==", ALICE_UID).where("isShared", "==", true).get());

  // Competition lifecycle + terminal lock
  await assertSucceeds(aliceDb.collection("competitions").doc("comp_1").set(validCompetitionPayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("competitions").where("participantUids", "array-contains", BOB_UID).get());
  await assertSucceeds(
    aliceDb.collection("competitions").doc("comp_1").update({
      status: "finalizada",
      winnerName: "Alicia Pescadora",
      updatedAt: new Date(),
    })
  );
  // Once finalized, cannot update again (terminal state lock)
  await assertFails(
    aliceDb.collection("competitions").doc("comp_1").update({
      winnerName: "Otro",
      updatedAt: new Date(),
    })
  );

  // AI Quota enforces max 5/day
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/ai_quota`).doc("2026-11-14").set({
      userId: ALICE_UID,
      dateKey: "2026-11-14",
      questionsUsed: 5,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/ai_quota`).doc("2026-11-14").update({
      questionsUsed: 6,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/ai_quota`).doc("2026-11-14").delete()
  );
});

test("Schema validation: rejects ghost fields, invalid enums, out-of-range numbers, and immutable field tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/catches`).doc("bad_ghost").set(validCatchPayload(ALICE_UID, { isVerified: true }))
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/spots`).doc("bad_enum").set(validSpotPayload(ALICE_UID, { waterType: "piscina" }))
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/catches`).doc("bad_weight").set(validCatchPayload(ALICE_UID, { weightKg: 9999 }))
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/tackle`).doc("bad_uid").set(validTacklePayload(ALICE_UID, { userId: BOB_UID }))
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/catches`).doc("catch_imm").set(validCatchPayload(ALICE_UID))
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/catches`).doc("catch_imm").update({ createdAt: new Date(0), updatedAt: new Date() })
  );
});
