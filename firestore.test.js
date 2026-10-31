const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read purchases", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("purchases").get());
});

test("Unauthenticated user: cannot write purchase", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("purchases").doc("p1").set({
      userId: ALICE_UID,
      name: "Panadol",
      qty: 10,
      purchase: 2.5,
      sale: 3.0,
      date: "2026-10-01",
      total: 25.0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user: can create their own valid purchase", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("purchases").doc("p1").set({
      userId: ALICE_UID,
      name: "Panadol 500mg",
      company: "GSK",
      qty: 10,
      purchase: 2.5,
      sale: 3.0,
      date: "2026-10-01",
      total: 25.0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user: cannot read another user's purchase", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("purchases").doc("bob_p").set({
      userId: BOB_UID,
      name: "Amoxil",
      qty: 5,
      purchase: 5.0,
      sale: 7.0,
      date: "2026-10-01",
      total: 25.0,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("purchases").doc("bob_p").get());
});

test("Authenticated user: can query their own daily sales with filter", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("daily_sales").doc("s1").set({
      userId: ALICE_UID,
      date: "2026-10-01",
      amount: 1500.0,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("daily_sales").where("userId", "==", ALICE_UID).get()
  );
});

test("Authenticated user: fails daily sales query without userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("daily_sales").get());
});

test("Schema validation: rejects purchase with negative qty", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("purchases").doc("bad_p").set({
      userId: ALICE_UID,
      name: "Panadol",
      qty: -5,
      purchase: 2.0,
      sale: 3.0,
      date: "2026-10-01",
      total: -10.0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});
