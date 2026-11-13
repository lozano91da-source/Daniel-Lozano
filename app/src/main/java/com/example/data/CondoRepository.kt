package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CondoRole
import com.example.model.CondoUnit
import com.example.model.Condominium
import com.example.model.MonthlyExpense
import com.example.model.PaymentReminder
import com.example.model.PaymentReport
import com.example.model.UnitInvoice
import com.example.model.UserProfile
import com.example.model.VisitorPass
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CondoRepository(
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
      ?: throw IllegalStateException("Debe iniciar sesión con Google antes de acceder a Firestore.")
  }

  private fun newDocId(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"

  private fun round2(value: Double): Double = (value * 100.0).roundToLong() / 100.0

  private fun <T> Flow<T>.withFirestoreErrorLog(
    uid: String,
    operationType: OperationType,
    path: String,
  ): Flow<T> = catch { error ->
    val firestoreError =
      generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path, auth)
    }
    throw error
  }

  // --- User Profile ---

  fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
    val docRef = db.collection("users").document(uid)
    emitAll(
      docRef
        .snapshots()
        .map { snap ->
          snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .withFirestoreErrorLog(uid, OperationType.GET, docRef.path)
    )
  }

  suspend fun ensureUserProfile(displayName: String): Result<UserProfile> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val snapshot =
      try {
        docRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, docRef.path, auth)
        throw e
      }

    if (snapshot.exists()) {
      snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: UserProfile(id = uid, userId = uid, displayName = displayName)
    } else {
      val cleanName = displayName.trim().ifBlank { "Residente" }.take(80)
      val payload =
        mapOf(
          "userId" to uid,
          "displayName" to cleanName,
          "activeCondoId" to "",
          "activeUnitId" to "",
          "activeRole" to CondoRole.JUNTA.key,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      try {
        docRef.set(payload).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
        throw e
      }
      UserProfile(id = uid, userId = uid, displayName = cleanName, activeRole = CondoRole.JUNTA.key)
    }
  }

  suspend fun updateActiveSelection(
    condoId: String,
    unitId: String,
    role: CondoRole,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    try {
      docRef
        .update(
          mapOf(
            "activeCondoId" to condoId,
            "activeUnitId" to unitId,
            "activeRole" to role.key,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  // --- Condominiums ---

  fun observeCondominiums(): Flow<List<Condominium>> = flow {
    val uid = requireUserId()
    val path = "condominiums"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(Condominium::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  suspend fun createCondominium(
    name: String,
    address: String,
    bcvRate: Double,
    pagoMovilBank: String,
    pagoMovilPhone: String,
    pagoMovilId: String,
    transferBank: String,
    transferAccount: String,
    initialUnits: List<Triple<String, String, Double>> = emptyList(),
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val condoId = newDocId("condo")
    val cleanCode =
      name
        .uppercase(Locale.US)
        .replace(Regex("[^A-Z0-9]"), "")
        .take(4)
        .padEnd(4, 'Z') + UUID.randomUUID().toString().uppercase(Locale.US).take(4)

    val condoRef = db.collection("condominiums").document(condoId)
    val codeRef = db.collection("condoCodes").document(cleanCode)
    val userRef = db.collection("users").document(uid)

    val condoPayload =
      mapOf(
        "name" to name.trim().take(100),
        "address" to address.trim().take(160),
        "joinCode" to cleanCode,
        "bcvRate" to bcvRate.coerceIn(1.0, 999999.0),
        "pagoMovilBank" to pagoMovilBank.trim().take(60),
        "pagoMovilPhone" to pagoMovilPhone.trim().take(30),
        "pagoMovilId" to pagoMovilId.trim().take(30),
        "transferBank" to transferBank.trim().take(60),
        "transferAccount" to transferAccount.trim().take(40),
        "adminUid" to uid,
        "memberUids" to listOf(uid),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    val codePayload =
      mapOf(
        "condoId" to condoId,
        "condoName" to name.trim().take(100),
        "adminUid" to uid,
        "createdAt" to FieldValue.serverTimestamp(),
      )

    var firstUnitId = ""
    val batch = db.batch()
    batch.set(condoRef, condoPayload)
    batch.set(codeRef, codePayload)

    initialUnits.forEachIndexed { idx, (unitNum, ownerLabel, alicuota) ->
      val unitId = newDocId("unit")
      if (idx == 0) firstUnitId = unitId
      val unitRef = condoRef.collection("units").document(unitId)
      batch.set(
        unitRef,
        mapOf(
          "condoId" to condoId,
          "unitNumber" to unitNum.trim().take(40),
          "ownerLabel" to ownerLabel.trim().take(80),
          "alicuotaPct" to alicuota.coerceIn(0.01, 100.0),
          "residentUid" to if (idx == 0) uid else "",
          "adminUid" to uid,
          "memberUids" to listOf(uid),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
    }

    try {
      batch.commit().await()
      userRef
        .update(
          mapOf(
            "activeCondoId" to condoId,
            "activeUnitId" to firstUnitId,
            "activeRole" to CondoRole.JUNTA.key,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, condoRef.path, auth)
      throw e
    }
    condoId
  }

  suspend fun updateCondominiumSettings(
    condo: Condominium,
    bcvRate: Double,
    pagoMovilBank: String,
    pagoMovilPhone: String,
    pagoMovilId: String,
    transferBank: String,
    transferAccount: String,
  ): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("condominiums").document(condo.id)
    try {
      docRef
        .update(
          mapOf(
            "bcvRate" to bcvRate.coerceIn(1.0, 999999.0),
            "pagoMovilBank" to pagoMovilBank.trim().take(60),
            "pagoMovilPhone" to pagoMovilPhone.trim().take(30),
            "pagoMovilId" to pagoMovilId.trim().take(30),
            "transferBank" to transferBank.trim().take(60),
            "transferAccount" to transferAccount.trim().take(40),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun joinCondominiumByCode(rawCode: String): Result<String> = runCatching {
    val uid = requireUserId()
    val code = rawCode.trim().uppercase(Locale.US)
    require(code.length in 4..16) { "El código debe tener entre 4 y 16 caracteres." }

    val codeRef = db.collection("condoCodes").document(code)
    val codeSnap =
      try {
        codeRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, codeRef.path, auth)
        throw e
      }

    if (!codeSnap.exists()) {
      throw IllegalArgumentException("No existe un condominio con el código $code.")
    }
    val condoId = codeSnap.getString("condoId") ?: throw IllegalStateException("Código inválido.")
    val condoRef = db.collection("condominiums").document(condoId)
    try {
      condoRef
        .update(
          mapOf(
            "memberUids" to FieldValue.arrayUnion(uid),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      db.collection("users")
        .document(uid)
        .update(
          mapOf(
            "activeCondoId" to condoId,
            "activeRole" to CondoRole.VECINO.key,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, condoRef.path, auth)
      throw e
    }
    condoId
  }

  suspend fun syncCondoMembers(condo: Condominium): Result<Unit> = runCatching {
    val uid = requireUserId()
    if (condo.adminUid != uid) return@runCatching
    val targetMembers = condo.memberUids.distinct().take(50)
    val condoRef = db.collection("condominiums").document(condo.id)
    val collections = listOf("units", "expenses", "invoices", "visitorPasses", "reminders")
    val batch = db.batch()
    var writes = 0

    for (col in collections) {
      val docs =
        condoRef.collection(col).whereEqualTo("adminUid", uid).get().await().documents
      for (doc in docs) {
        @Suppress("UNCHECKED_CAST")
        val existingMembers = (doc.get("memberUids") as? List<String>).orEmpty()
        if (existingMembers.toSet() != targetMembers.toSet()) {
          batch.update(
            doc.reference,
            mapOf("memberUids" to targetMembers, "updatedAt" to FieldValue.serverTimestamp()),
          )
          writes++
        }
      }
    }
    if (writes > 0) {
      batch.commit().await()
    }
  }

  // --- Units ---

  fun observeUnits(condoId: String): Flow<List<CondoUnit>> = flow {
    val uid = requireUserId()
    val path = "condominiums/$condoId/units"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(CondoUnit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.unitNumber }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  suspend fun addUnit(
    condo: Condominium,
    unitNumber: String,
    ownerLabel: String,
    alicuotaPct: Double,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val unitId = newDocId("unit")
    val docRef = db.collection("condominiums").document(condo.id).collection("units").document(unitId)
    val members = (condo.memberUids + uid).distinct().take(50)
    val payload =
      mapOf(
        "condoId" to condo.id,
        "unitNumber" to unitNumber.trim().take(40),
        "ownerLabel" to ownerLabel.trim().take(80),
        "alicuotaPct" to alicuotaPct.coerceIn(0.01, 100.0),
        "residentUid" to "",
        "adminUid" to condo.adminUid,
        "memberUids" to members,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
    unitId
  }

  suspend fun claimUnit(condoId: String, unitId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val unitRef = db.collection("condominiums").document(condoId).collection("units").document(unitId)
    val userRef = db.collection("users").document(uid)
    try {
      unitRef
        .update(
          mapOf(
            "residentUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      userRef
        .update(
          mapOf(
            "activeCondoId" to condoId,
            "activeUnitId" to unitId,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, unitRef.path, auth)
      throw e
    }
  }

  // --- Expenses & Automatic Aliquot Billing ---

  fun observeExpenses(condoId: String): Flow<List<MonthlyExpense>> = flow {
    val uid = requireUserId()
    val path = "condominiums/$condoId/expenses"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(MonthlyExpense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  suspend fun addMonthlyExpense(
    condo: Condominium,
    billingPeriod: String,
    title: String,
    category: String,
    amountUsd: Double,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val expenseId = newDocId("exp")
    val docRef =
      db.collection("condominiums").document(condo.id).collection("expenses").document(expenseId)
    val cleanUsd = round2(amountUsd.coerceAtLeast(0.01))
    val amountBs = round2(cleanUsd * condo.bcvRate)
    val members = (condo.memberUids + uid).distinct().take(50)

    val payload =
      mapOf(
        "condoId" to condo.id,
        "billingPeriod" to billingPeriod.trim().take(16),
        "title" to title.trim().take(100),
        "category" to category.trim().take(40),
        "amountUsd" to cleanUsd,
        "amountBs" to amountBs,
        "adminUid" to condo.adminUid,
        "memberUids" to members,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
    expenseId
  }

  suspend fun deleteExpense(condoId: String, expenseId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef =
      db.collection("condominiums").document(condoId).collection("expenses").document(expenseId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  fun observeInvoices(condoId: String): Flow<List<UnitInvoice>> = flow {
    val uid = requireUserId()
    val path = "condominiums/$condoId/invoices"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(UnitInvoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(compareByDescending<UnitInvoice> { it.billingPeriod }.thenBy { it.unitNumber })
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  /**
   * Automatically splits the total monthly expenses across all condominium units
   * proportionally by each unit's alícuota percentage.
   */
  suspend fun generateMonthlyInvoices(
    condo: Condominium,
    billingPeriod: String,
    units: List<CondoUnit>,
    expensesForPeriod: List<MonthlyExpense>,
    existingInvoicesForPeriod: List<UnitInvoice>,
  ): Result<Int> = runCatching {
    val uid = requireUserId()
    require(units.isNotEmpty()) { "Registre al menos una unidad/apartamento primero." }
    val totalCondoUsd = round2(expensesForPeriod.sumOf { it.amountUsd })
    require(totalCondoUsd > 0.0) { "Cargue al menos un gasto en el mes $billingPeriod." }

    val existingByUnitId = existingInvoicesForPeriod.associateBy { it.unitId }
    val totalAliquota = units.sumOf { it.alicuotaPct }.coerceAtLeast(0.01)
    val members = (condo.memberUids + uid).distinct().take(50)

    val batch = db.batch()
    var count = 0

    for (unit in units) {
      val existing = existingByUnitId[unit.id]
      if (existing != null && existing.status == "paid") continue

      // Normalize proportion if sum of alicuotas != 100%, while storing the unit's nominal %
      val normalizedShare = unit.alicuotaPct / totalAliquota
      val amountDueUsd = round2(totalCondoUsd * normalizedShare)
      val amountDueBs = round2(amountDueUsd * condo.bcvRate)

      if (existing != null) {
        val invRef =
          db.collection("condominiums").document(condo.id).collection("invoices").document(existing.id)
        batch.update(
          invRef,
          mapOf(
            "alicuotaPct" to unit.alicuotaPct,
            "totalCondoUsd" to totalCondoUsd,
            "amountDueUsd" to amountDueUsd,
            "bcvRate" to condo.bcvRate,
            "amountDueBs" to amountDueBs,
            "residentUid" to unit.residentUid,
            "memberUids" to members,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        count++
      } else {
        val invoiceId = newDocId("inv")
        val invRef =
          db.collection("condominiums").document(condo.id).collection("invoices").document(invoiceId)
        val cleanUnitCode =
          unit.unitNumber.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "").take(8).ifBlank { "U" }
        val receiptNumber = "REC-${billingPeriod.replace("-", "")}-$cleanUnitCode"

        batch.set(
          invRef,
          mapOf(
            "condoId" to condo.id,
            "unitId" to unit.id,
            "unitNumber" to unit.unitNumber,
            "billingPeriod" to billingPeriod,
            "alicuotaPct" to unit.alicuotaPct,
            "totalCondoUsd" to totalCondoUsd,
            "amountDueUsd" to amountDueUsd,
            "bcvRate" to condo.bcvRate,
            "amountDueBs" to amountDueBs,
            "status" to "pending",
            "receiptNumber" to receiptNumber,
            "paidAtMs" to 0L,
            "adminUid" to condo.adminUid,
            "residentUid" to unit.residentUid,
            "memberUids" to members,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        count++
      }
    }

    try {
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "condominiums/${condo.id}/invoices", auth)
      throw e
    }
    count
  }

  suspend fun markInvoiceOverdue(condoId: String, invoiceId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef =
      db.collection("condominiums").document(condoId).collection("invoices").document(invoiceId)
    try {
      docRef
        .update(mapOf("status" to "overdue", "updatedAt" to FieldValue.serverTimestamp()))
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  // --- Payment Reports & Reconciliation ---

  fun observePaymentsForAdmin(condoId: String): Flow<List<PaymentReport>> = flow {
    val uid = requireUserId()
    val path = "condominiums/$condoId/payments"
    emitAll(
      db.collection(path)
        .whereEqualTo("adminUid", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(PaymentReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  fun observePaymentsForResident(condoId: String): Flow<List<PaymentReport>> = flow {
    val uid = requireUserId()
    val path = "condominiums/$condoId/payments"
    emitAll(
      db.collection(path)
        .whereEqualTo("reportedByUid", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(PaymentReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  suspend fun reportPayment(
    condo: Condominium,
    invoice: UnitInvoice,
    method: String,
    bankName: String,
    referenceNumber: String,
    senderPhone: String,
    currency: String,
    amountReported: Double,
    paymentDate: String,
    receiptPreviewBase64: String = "",
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val paymentId = newDocId("pay")
    val payRef =
      db.collection("condominiums").document(condo.id).collection("payments").document(paymentId)
    val invRef =
      db.collection("condominiums").document(condo.id).collection("invoices").document(invoice.id)
    val members = (condo.memberUids + uid).distinct().take(50)

    val amountUsdEq =
      if (currency == "VES") round2(amountReported / condo.bcvRate.coerceAtLeast(1.0))
      else round2(amountReported)

    val payload =
      mutableMapOf<String, Any>(
        "condoId" to condo.id,
        "invoiceId" to invoice.id,
        "unitId" to invoice.unitId,
        "unitNumber" to invoice.unitNumber,
        "billingPeriod" to invoice.billingPeriod,
        "method" to method,
        "bankName" to bankName.trim().take(60),
        "referenceNumber" to referenceNumber.trim().take(40),
        "senderPhone" to senderPhone.trim().take(24),
        "currency" to currency,
        "amountReported" to round2(amountReported),
        "amountUsdEquivalent" to amountUsdEq,
        "bcvRateUsed" to condo.bcvRate,
        "paymentDate" to paymentDate.trim().take(20),
        "status" to "pending_review",
        "reviewNote" to "",
        "adminUid" to condo.adminUid,
        "reportedByUid" to uid,
        "memberUids" to members,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (receiptPreviewBase64.isNotBlank()) {
      payload["receiptPreviewBase64"] = receiptPreviewBase64.take(79000)
    }

    val batch = db.batch()
    batch.set(payRef, payload)
    if (invoice.status == "pending" || invoice.status == "overdue") {
      batch.update(
        invRef,
        mapOf("status" to "in_review", "updatedAt" to FieldValue.serverTimestamp()),
      )
    }
    try {
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, payRef.path, auth)
      throw e
    }
    paymentId
  }

  suspend fun reconcilePayment(
    condoId: String,
    payment: PaymentReport,
    approve: Boolean,
    reviewNote: String,
  ): Result<Unit> = runCatching {
    requireUserId()
    val payRef =
      db.collection("condominiums").document(condoId).collection("payments").document(payment.id)
    val invRef =
      db.collection("condominiums").document(condoId).collection("invoices").document(payment.invoiceId)

    val batch = db.batch()
    batch.update(
      payRef,
      mapOf(
        "status" to if (approve) "approved" else "rejected",
        "reviewNote" to reviewNote.trim().take(200),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    )
    if (approve) {
      batch.update(
        invRef,
        mapOf(
          "status" to "paid",
          "paidAtMs" to System.currentTimeMillis(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
    } else {
      batch.update(
        invRef,
        mapOf(
          "status" to "pending",
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
    }
    try {
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, payRef.path, auth)
      throw e
    }
  }

  // --- Visitor QR Passes (Garita) ---

  fun observeVisitorPasses(condoId: String): Flow<List<VisitorPass>> = flow {
    val uid = requireUserId()
    val path = "condominiums/$condoId/visitorPasses"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(VisitorPass::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  suspend fun createVisitorPass(
    condo: Condominium,
    unit: CondoUnit,
    visitorName: String,
    visitorCedula: String,
    vehiclePlate: String,
    visitType: String,
  ): Result<VisitorPass> = runCatching {
    val uid = requireUserId()
    val passId = newDocId("pass")
    val token = "CZ-${UUID.randomUUID().toString().uppercase(Locale.US).take(8)}"
    val docRef =
      db.collection("condominiums").document(condo.id).collection("visitorPasses").document(passId)
    val members = (condo.memberUids + uid).distinct().take(50)

    val payload =
      mapOf(
        "condoId" to condo.id,
        "unitId" to unit.id,
        "unitNumber" to unit.unitNumber,
        "visitorName" to visitorName.trim().take(80),
        "visitorCedula" to visitorCedula.trim().take(24),
        "vehiclePlate" to vehiclePlate.trim().uppercase(Locale.US).take(16),
        "visitType" to visitType,
        "qrToken" to token,
        "status" to "active",
        "checkedInAtMs" to 0L,
        "checkedOutAtMs" to 0L,
        "adminUid" to condo.adminUid,
        "createdByUid" to uid,
        "memberUids" to members,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
    VisitorPass(
      id = passId,
      condoId = condo.id,
      unitId = unit.id,
      unitNumber = unit.unitNumber,
      visitorName = visitorName.trim(),
      visitorCedula = visitorCedula.trim(),
      vehiclePlate = vehiclePlate.trim().uppercase(Locale.US),
      visitType = visitType,
      qrToken = token,
      status = "active",
      adminUid = condo.adminUid,
      createdByUid = uid,
      memberUids = members,
    )
  }

  suspend fun updateVisitorPassStatus(
    condoId: String,
    passId: String,
    nextStatus: String,
  ): Result<Unit> = runCatching {
    requireUserId()
    val docRef =
      db.collection("condominiums").document(condoId).collection("visitorPasses").document(passId)
    val updates =
      mutableMapOf<String, Any>(
        "status" to nextStatus,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    val now = System.currentTimeMillis()
    if (nextStatus == "checked_in") updates["checkedInAtMs"] = now
    if (nextStatus == "checked_out") updates["checkedOutAtMs"] = now

    try {
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  // --- Payment Reminders ---

  fun observeReminders(condoId: String): Flow<List<PaymentReminder>> = flow {
    val uid = requireUserId()
    val path = "condominiums/$condoId/reminders"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              PaymentReminder::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  suspend fun sendPaymentReminder(condo: Condominium, invoice: UnitInvoice): Result<String> =
    runCatching {
      val uid = requireUserId()
      val remId = newDocId("rem")
      val docRef =
        db.collection("condominiums").document(condo.id).collection("reminders").document(remId)
      val members = (condo.memberUids + uid).distinct().take(50)

      val payload =
        mapOf(
          "condoId" to condo.id,
          "unitId" to invoice.unitId,
          "unitNumber" to invoice.unitNumber,
          "invoiceId" to invoice.id,
          "billingPeriod" to invoice.billingPeriod,
          "title" to "Recordatorio de pago · ${invoice.unitNumber}",
          "message" to
            "Cuota pendiente del período ${invoice.billingPeriod}: $${String.format(Locale.US, "%.2f", invoice.amountDueUsd)} (Bs. ${String.format(Locale.US, "%.2f", invoice.amountDueBs)}). Reporte su Pago Móvil o transferencia.",
          "amountDueUsd" to invoice.amountDueUsd,
          "amountDueBs" to invoice.amountDueBs,
          "isRead" to false,
          "adminUid" to condo.adminUid,
          "targetUid" to invoice.residentUid,
          "memberUids" to members,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      try {
        docRef.set(payload).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
        throw e
      }
      remId
    }

  suspend fun markReminderRead(condoId: String, reminderId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef =
      db.collection("condominiums").document(condoId).collection("reminders").document(reminderId)
    try {
      docRef.update(mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp())).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }
}
