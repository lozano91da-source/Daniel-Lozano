const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const ts = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_junta_123";
const BOB_UID = "bob_vecino_456";
const CHARLIE_UID = "charlie_outsider_789";

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

test("Unauthenticated user: cannot read or write condominiums or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("condominiums").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc("anon").set({
      userId: "anon",
      displayName: "Anon",
      activeRole: "vecino",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

test("User profile: owner can create/read/update, outsider cannot read or shadow-update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Maria Junta",
      activeCondoId: "",
      activeUnitId: "",
      activeRole: "junta",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Bob cannot read Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isSuperAdmin: true,
      updatedAt: ts(),
    })
  );
});

test("Condominium + Units + Invoices + Payments + QR Pass + Reminder lifecycle & isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // 1. Alice creates condominium & code lookup
  await assertSucceeds(
    aliceDb.collection("condominiums").doc("condo_1").set({
      name: "Residencias Lago Mar",
      address: "Av. El Milagro, Maracaibo",
      joinCode: "LAGOMAR1",
      bcvRate: 46.5,
      pagoMovilBank: "Banesco",
      pagoMovilPhone: "04146001122",
      pagoMovilId: "J-30123456-7",
      transferBank: "Mercantil",
      transferAccount: "0105-0011-22-1234567890",
      adminUid: ALICE_UID,
      memberUids: [ALICE_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("condoCodes").doc("LAGOMAR1").set({
      condoId: "condo_1",
      condoName: "Residencias Lago Mar",
      adminUid: ALICE_UID,
      createdAt: ts(),
    })
  );

  // Unfiltered list on condoCodes is forbidden
  await assertFails(bobDb.collection("condoCodes").get());
  // Direct get by code succeeds
  await assertSucceeds(bobDb.collection("condoCodes").doc("LAGOMAR1").get());

  // Unfiltered list on condominiums fails; memberUids query succeeds for Alice
  await assertFails(aliceDb.collection("condominiums").get());
  await assertSucceeds(
    aliceDb.collection("condominiums").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Charlie (outsider) cannot read condo_1
  await assertFails(charlieDb.collection("condominiums").doc("condo_1").get());

  // Bob joins condo_1 with matching joinCode
  await assertSucceeds(
    bobDb.collection("condominiums").doc("condo_1").update({
      joinCode: "LAGOMAR1",
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: ts(),
    })
  );

  // 2. Alice creates Unit 4B
  await assertSucceeds(
    aliceDb.collection("condominiums").doc("condo_1").collection("units").doc("unit_4b").set({
      condoId: "condo_1",
      unitNumber: "Torre A - 4B",
      ownerLabel: "Familia Fuenmayor",
      alicuotaPct: 12.5,
      residentUid: BOB_UID,
      adminUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // 3. Alice creates Expense
  await assertSucceeds(
    aliceDb.collection("condominiums").doc("condo_1").collection("expenses").doc("exp_1").set({
      condoId: "condo_1",
      billingPeriod: "2026-11",
      title: "Vigilancia Privada 24h",
      category: "Vigilancia",
      amountUsd: 800.0,
      amountBs: 37200.0,
      adminUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Bob cannot create expenses (only Junta/adminUid)
  await assertFails(
    bobDb.collection("condominiums").doc("condo_1").collection("expenses").doc("exp_bob").set({
      condoId: "condo_1",
      billingPeriod: "2026-11",
      title: "Gasto Falso",
      category: "Otro",
      amountUsd: 100.0,
      amountBs: 4650.0,
      adminUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // 4. Alice generates Invoice for Unit 4B
  await assertSucceeds(
    aliceDb.collection("condominiums").doc("condo_1").collection("invoices").doc("inv_4b").set({
      condoId: "condo_1",
      unitId: "unit_4b",
      unitNumber: "Torre A - 4B",
      billingPeriod: "2026-11",
      alicuotaPct: 12.5,
      totalCondoUsd: 800.0,
      amountDueUsd: 100.0,
      bcvRate: 46.5,
      amountDueBs: 4650.0,
      status: "pending",
      receiptNumber: "REC-202611-4B",
      paidAtMs: 0,
      adminUid: ALICE_UID,
      residentUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // 5. Bob reports Pago Movil payment & transitions invoice to in_review
  await assertSucceeds(
    bobDb.collection("condominiums").doc("condo_1").collection("payments").doc("pay_1").set({
      condoId: "condo_1",
      invoiceId: "inv_4b",
      unitId: "unit_4b",
      unitNumber: "Torre A - 4B",
      billingPeriod: "2026-11",
      method: "pago_movil",
      bankName: "Banesco",
      referenceNumber: "00482910",
      senderPhone: "04141234567",
      currency: "VES",
      amountReported: 4650.0,
      amountUsdEquivalent: 100.0,
      bcvRateUsed: 46.5,
      paymentDate: "2026-11-12",
      status: "pending_review",
      adminUid: ALICE_UID,
      reportedByUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Bob cannot self-approve payment!
  await assertFails(
    bobDb.collection("condominiums").doc("condo_1").collection("payments").doc("pay_1").update({
      status: "approved",
      updatedAt: ts(),
    })
  );

  // Charlie cannot read Bob's payment
  await assertFails(
    charlieDb.collection("condominiums").doc("condo_1").collection("payments").doc("pay_1").get()
  );

  // Alice approves payment & marks invoice paid
  await assertSucceeds(
    aliceDb.collection("condominiums").doc("condo_1").collection("payments").doc("pay_1").update({
      status: "approved",
      reviewNote: "Conciliado Banesco",
      updatedAt: ts(),
    })
  );

  // 6. Bob creates Visitor Pass QR and Garita checks in & checks out
  await assertSucceeds(
    bobDb.collection("condominiums").doc("condo_1").collection("visitorPasses").doc("pass_1").set({
      condoId: "condo_1",
      unitId: "unit_4b",
      unitNumber: "Torre A - 4B",
      visitorName: "Carlos Perez",
      visitorCedula: "V-18234567",
      vehiclePlate: "AB123CD",
      visitType: "Visita",
      qrToken: "CZ-LAGO-8841",
      status: "active",
      checkedInAtMs: 0,
      checkedOutAtMs: 0,
      adminUid: ALICE_UID,
      createdByUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("condominiums").doc("condo_1").collection("visitorPasses").doc("pass_1").update({
      status: "checked_in",
      checkedInAtMs: 1763000000000,
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("condominiums").doc("condo_1").collection("visitorPasses").doc("pass_1").update({
      status: "checked_out",
      checkedOutAtMs: 1763003600000,
      updatedAt: ts(),
    })
  );

  // Terminal state lock: checked_out pass cannot be reopened
  await assertFails(
    aliceDb.collection("condominiums").doc("condo_1").collection("visitorPasses").doc("pass_1").update({
      status: "checked_in",
      updatedAt: ts(),
    })
  );

  // 7. Alice sends reminder, Bob marks as read
  await assertSucceeds(
    aliceDb.collection("condominiums").doc("condo_1").collection("reminders").doc("rem_1").set({
      condoId: "condo_1",
      unitId: "unit_4b",
      unitNumber: "Torre A - 4B",
      invoiceId: "inv_4b",
      billingPeriod: "2026-11",
      title: "Recordatorio de Condominio",
      message: "Tiene una cuota pendiente para Noviembre 2026.",
      amountDueUsd: 100.0,
      amountDueBs: 4650.0,
      isRead: false,
      adminUid: ALICE_UID,
      targetUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    bobDb.collection("condominiums").doc("condo_1").collection("reminders").doc("rem_1").update({
      isRead: true,
      updatedAt: ts(),
    })
  );
});

test("Atomic batch creation of condominium + condoCode + initial unit succeeds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = aliceDb.batch();
  const condoRef = aliceDb.collection("condominiums").doc("condo_batch_1");
  const codeRef = aliceDb.collection("condoCodes").doc("BATCH001");
  const unitRef = condoRef.collection("units").doc("unit_batch_1");

  batch.set(condoRef, {
    name: "Res Tierra Bella",
    address: "Av. Fuerzas Armadas",
    joinCode: "BATCH001",
    bcvRate: 50.0,
    pagoMovilBank: "Banesco",
    pagoMovilPhone: "04146001122",
    pagoMovilId: "J-30123456-7",
    transferBank: "Mercantil",
    transferAccount: "0105-0011-22-1234567890",
    adminUid: ALICE_UID,
    memberUids: [ALICE_UID],
    createdAt: ts(),
    updatedAt: ts(),
  });
  batch.set(codeRef, {
    condoId: "condo_batch_1",
    condoName: "Res Tierra Bella",
    adminUid: ALICE_UID,
    createdAt: ts(),
  });
  batch.set(unitRef, {
    condoId: "condo_batch_1",
    unitNumber: "Apto 1A",
    ownerLabel: "Familia Diaz",
    alicuotaPct: 100.0,
    residentUid: ALICE_UID,
    adminUid: ALICE_UID,
    memberUids: [ALICE_UID],
    createdAt: ts(),
    updatedAt: ts(),
  });

  await assertSucceeds(batch.commit());
});

test("DataRepository collections (condominios, vecinos, gastos_mensuales, pagos_reportados) security & lifecycle", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Unauthenticated cannot list condominios
  await assertFails(unauthDb.collection("condominios").get());

  // 1. Alice creates condominio + initial vecino atomically
  const batch = aliceDb.batch();
  const condRef = aliceDb.collection("condominios").doc("cond_maracaibo");
  const vecRef = condRef.collection("vecinos").doc("vec_1a");

  batch.set(condRef, {
    nombre: "Res. El Milagro",
    direccion: "Av. El Milagro, Maracaibo",
    codigoAcceso: "MILAGRO1",
    tasaBcv: 50.0,
    bancoPagoMovil: "Banesco",
    telefonoPagoMovil: "04146001122",
    rifPagoMovil: "J-30123456-7",
    bancoTransferencia: "Mercantil",
    cuentaTransferencia: "0105-0011-22-1234567890",
    adminUid: ALICE_UID,
    memberUids: [ALICE_UID],
    createdAt: ts(),
    updatedAt: ts(),
  });
  batch.set(vecRef, {
    condominioId: "cond_maracaibo",
    unidad: "Apto 1A",
    nombreCompleto: "Familia Quintero",
    telefono: "04141112233",
    alicuotaPct: 50.0,
    saldoPendienteUsd: 0.0,
    saldoPendienteBs: 0.0,
    estadoSolvencia: "solvente",
    residentUid: "",
    adminUid: ALICE_UID,
    memberUids: [ALICE_UID],
    createdAt: ts(),
    updatedAt: ts(),
  });
  await assertSucceeds(batch.commit());

  // Charlie (outsider) cannot read condominio or create gasto
  await assertFails(charlieDb.collection("condominios").doc("cond_maracaibo").get());
  await assertFails(
    charlieDb.collection("condominios").doc("cond_maracaibo").collection("gastos_mensuales").doc("g_bad").set({
      condominioId: "cond_maracaibo",
      periodoMes: "2026-11",
      concepto: "Intruso",
      categoria: "Otro",
      montoUsd: 100.0,
      montoBs: 5000.0,
      tasaBcvAplicada: 50.0,
      distribuido: false,
      adminUid: ALICE_UID,
      memberUids: [ALICE_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Bob joins condominio and Alice syncs Bob into vecino
  await assertSucceeds(
    bobDb.collection("condominios").doc("cond_maracaibo").update({
      codigoAcceso: "MILAGRO1",
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("condominios").doc("cond_maracaibo").collection("vecinos").doc("vec_1a").update({
      memberUids: [ALICE_UID, BOB_UID],
      saldoPendienteUsd: 250.0,
      saldoPendienteBs: 12500.0,
      estadoSolvencia: "pendiente",
      updatedAt: ts(),
    })
  );

  // Alice adds gasto_mensual; Bob cannot add gasto_mensual
  await assertSucceeds(
    aliceDb.collection("condominios").doc("cond_maracaibo").collection("gastos_mensuales").doc("gasto_1").set({
      condominioId: "cond_maracaibo",
      periodoMes: "2026-11",
      concepto: "Vigilancia 24h",
      categoria: "Vigilancia",
      montoUsd: 500.0,
      montoBs: 25000.0,
      tasaBcvAplicada: 50.0,
      distribuido: true,
      adminUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertFails(
    bobDb.collection("condominios").doc("cond_maracaibo").collection("gastos_mensuales").doc("gasto_bob").set({
      condominioId: "cond_maracaibo",
      periodoMes: "2026-11",
      concepto: "Gasto no autorizado",
      categoria: "Otro",
      montoUsd: 10.0,
      montoBs: 500.0,
      tasaBcvAplicada: 50.0,
      distribuido: false,
      adminUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Bob reports pago_reportado; cannot self-approve; Alice approves
  await assertSucceeds(
    bobDb.collection("condominios").doc("cond_maracaibo").collection("pagos_reportados").doc("pago_1").set({
      condominioId: "cond_maracaibo",
      vecinoId: "vec_1a",
      unidad: "Apto 1A",
      periodoMes: "2026-11",
      metodoPago: "pago_movil",
      bancoOrigen: "Banesco",
      numeroReferencia: "88776655",
      telefonoEmisor: "04141112233",
      moneda: "VES",
      montoReportado: 12500.0,
      montoEquivalenteUsd: 250.0,
      tasaBcvUsada: 50.0,
      fechaPago: "2026-11-12",
      estado: "pendiente_revision",
      notaConciliacion: "",
      adminUid: ALICE_UID,
      reportadoPorUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertFails(
    bobDb.collection("condominios").doc("cond_maracaibo").collection("pagos_reportados").doc("pago_1").update({
      estado: "aprobado",
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("condominios").doc("cond_maracaibo").collection("pagos_reportados").doc("pago_1").update({
      estado: "aprobado",
      notaConciliacion: "Conciliado Banesco",
      updatedAt: ts(),
    })
  );
});
