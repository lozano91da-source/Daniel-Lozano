package com.example.data

import android.content.Context
import com.example.R
import com.example.model.Condominio
import com.example.model.GastoMensual
import com.example.model.PagoReportado
import com.example.model.Vecino
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

/**
 * Repositorio principal para operar en Cloud Firestore sobre la estructura de colecciones:
 * - condominios/{condominioId}
 * - condominios/{condominioId}/vecinos/{vecinoId}
 * - condominios/{condominioId}/gastos_mensuales/{gastoId}
 * - condominios/{condominioId}/pagos_reportados/{pagoId}
 */
class DataRepository(
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

  // --- 1. Colección 'condominios' ---

  fun observeCondominios(): Flow<List<Condominio>> = flow {
    val uid = requireUserId()
    val path = COLLECTION_CONDOMINIOS
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(Condominio::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  suspend fun crearCondominio(
    nombre: String,
    direccion: String,
    tasaBcv: Double,
    bancoPagoMovil: String = "",
    telefonoPagoMovil: String = "",
    rifPagoMovil: String = "",
    bancoTransferencia: String = "",
    cuentaTransferencia: String = "",
    vecinosIniciales: List<Triple<String, String, Double>> = emptyList(),
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val condominioId = newDocId("cond")
    val codigoAcceso =
      nombre
        .uppercase(Locale.US)
        .replace(Regex("[^A-Z0-9]"), "")
        .take(4)
        .padEnd(4, 'Z') + UUID.randomUUID().toString().uppercase(Locale.US).take(4)

    val condominioRef = db.collection(COLLECTION_CONDOMINIOS).document(condominioId)
    val payload =
      mapOf(
        "nombre" to nombre.trim().take(100),
        "direccion" to direccion.trim().take(160),
        "codigoAcceso" to codigoAcceso,
        "tasaBcv" to tasaBcv.coerceIn(1.0, 999999.0),
        "bancoPagoMovil" to bancoPagoMovil.trim().take(60),
        "telefonoPagoMovil" to telefonoPagoMovil.trim().take(30),
        "rifPagoMovil" to rifPagoMovil.trim().take(30),
        "bancoTransferencia" to bancoTransferencia.trim().take(60),
        "cuentaTransferencia" to cuentaTransferencia.trim().take(40),
        "adminUid" to uid,
        "memberUids" to listOf(uid),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    val batch = db.batch()
    batch.set(condominioRef, payload)

    vecinosIniciales.forEachIndexed { idx, (unidad, nombreVecino, alicuota) ->
      val vecinoId = newDocId("vec")
      val vecinoRef = condominioRef.collection(SUBCOLLECTION_VECINOS).document(vecinoId)
      val vecinoCode = "CZ-" + UUID.randomUUID().toString().uppercase(Locale.US).replace("-", "").take(6)
      batch.set(
        vecinoRef,
        mapOf(
          "condominioId" to condominioId,
          "unidad" to unidad.trim().take(40),
          "nombreCompleto" to nombreVecino.trim().take(80),
          "telefono" to "",
          "alicuotaPct" to alicuota.coerceIn(0.01, 100.0),
          "saldoPendienteUsd" to 0.0,
          "saldoPendienteBs" to 0.0,
          "estadoSolvencia" to "solvente",
          "codigoAcceso" to vecinoCode,
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
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, condominioRef.path, auth)
      throw e
    }
    condominioId
  }

  suspend fun getCondominio(condominioId: String): Result<Condominio?> = runCatching {
    requireUserId()
    val docRef = db.collection(COLLECTION_CONDOMINIOS).document(condominioId)
    try {
      docRef
        .get()
        .await()
        .toObject(Condominio::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      throw e
    }
  }

  suspend fun actualizarTasaYCuentas(
    condominioId: String,
    tasaBcv: Double,
    bancoPagoMovil: String,
    telefonoPagoMovil: String,
    rifPagoMovil: String,
    bancoTransferencia: String,
    cuentaTransferencia: String,
  ): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection(COLLECTION_CONDOMINIOS).document(condominioId)
    try {
      docRef
        .update(
          mapOf(
            "tasaBcv" to tasaBcv.coerceIn(1.0, 999999.0),
            "bancoPagoMovil" to bancoPagoMovil.trim().take(60),
            "telefonoPagoMovil" to telefonoPagoMovil.trim().take(30),
            "rifPagoMovil" to rifPagoMovil.trim().take(30),
            "bancoTransferencia" to bancoTransferencia.trim().take(60),
            "cuentaTransferencia" to cuentaTransferencia.trim().take(40),
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

  suspend fun unirseACondominio(condominioId: String, codigoAcceso: String): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val cleanCode = codigoAcceso.trim().uppercase(Locale.US)
      require(cleanCode.length in 4..16) { "El código de acceso debe tener entre 4 y 16 caracteres." }
      val docRef = db.collection(COLLECTION_CONDOMINIOS).document(condominioId)
      try {
        docRef
          .update(
            mapOf(
              "codigoAcceso" to cleanCode,
              "memberUids" to FieldValue.arrayUnion(uid),
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

  suspend fun rotarCodigoAccesoCondominio(
    condominioId: String,
    nombre: String,
  ): Result<String> = runCatching {
    requireUserId()
    val nuevoCodigo =
      nombre
        .uppercase(Locale.US)
        .replace(Regex("[^A-Z0-9]"), "")
        .take(4)
        .padEnd(4, 'Z') + UUID.randomUUID().toString().uppercase(Locale.US).replace("-", "").take(4)
    val docRef = db.collection(COLLECTION_CONDOMINIOS).document(condominioId)
    try {
      docRef
        .update(
          mapOf(
            "codigoAcceso" to nuevoCodigo,
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
    nuevoCodigo
  }

  suspend fun sincronizarMiembrosCondominio(
    condominio: Condominio,
    vecinos: List<Vecino>,
    gastos: List<GastoMensual>,
  ): Result<Unit> = runCatching {
    requireUserId()
    val condominioRef = db.collection(COLLECTION_CONDOMINIOS).document(condominio.id)
    val members = condominio.memberUids.distinct().take(50)
    val batch = db.batch()
    var hasChanges = false
    for (vecino in vecinos) {
      if (vecino.memberUids.toSet() != members.toSet()) {
        batch.update(
          condominioRef.collection(SUBCOLLECTION_VECINOS).document(vecino.id),
          mapOf("memberUids" to members, "updatedAt" to FieldValue.serverTimestamp()),
        )
        hasChanges = true
      }
    }
    for (gasto in gastos) {
      if (gasto.memberUids.toSet() != members.toSet()) {
        batch.update(
          condominioRef.collection(SUBCOLLECTION_GASTOS).document(gasto.id),
          mapOf("memberUids" to members, "updatedAt" to FieldValue.serverTimestamp()),
        )
        hasChanges = true
      }
    }
    if (hasChanges) {
      try {
        batch.commit().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, condominioRef.path, auth)
        throw e
      }
    }
  }

  suspend fun eliminarCondominio(condominioId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection(COLLECTION_CONDOMINIOS).document(condominioId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- 2. Colección 'vecinos' ---

  fun observeVecinos(condominioId: String): Flow<List<Vecino>> = flow {
    val uid = requireUserId()
    val path = "$COLLECTION_CONDOMINIOS/$condominioId/$SUBCOLLECTION_VECINOS"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(Vecino::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.unidad }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  suspend fun registrarVecino(
    condominio: Condominio,
    unidad: String,
    nombreCompleto: String,
    telefono: String,
    alicuotaPct: Double,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val vecinoId = newDocId("vec")
    val docRef =
      db.collection(COLLECTION_CONDOMINIOS)
        .document(condominio.id)
        .collection(SUBCOLLECTION_VECINOS)
        .document(vecinoId)
    val members = (condominio.memberUids + uid).distinct().take(50)
    val vecinoCode = "CZ-" + UUID.randomUUID().toString().uppercase(Locale.US).replace("-", "").take(6)

    val payload =
      mapOf(
        "condominioId" to condominio.id,
        "unidad" to unidad.trim().take(40),
        "nombreCompleto" to nombreCompleto.trim().take(80),
        "telefono" to telefono.trim().take(30),
        "alicuotaPct" to alicuotaPct.coerceIn(0.01, 100.0),
        "saldoPendienteUsd" to 0.0,
        "saldoPendienteBs" to 0.0,
        "estadoSolvencia" to "solvente",
        "codigoAcceso" to vecinoCode,
        "residentUid" to "",
        "adminUid" to condominio.adminUid,
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
    vecinoId
  }

  suspend fun generarCodigoAccesoResidente(
    condominioId: String,
    vecinoId: String,
  ): Result<String> = runCatching {
    requireUserId()
    val uniqueCode = "CZ-" + UUID.randomUUID().toString().uppercase(Locale.US).replace("-", "").take(6)
    val docRef =
      db.collection(COLLECTION_CONDOMINIOS)
        .document(condominioId)
        .collection(SUBCOLLECTION_VECINOS)
        .document(vecinoId)
    try {
      docRef
        .update(
          mapOf(
            "codigoAcceso" to uniqueCode,
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
    uniqueCode
  }

  suspend fun getVecino(condominioId: String, vecinoId: String): Result<Vecino?> = runCatching {
    requireUserId()
    val docRef =
      db.collection(COLLECTION_CONDOMINIOS)
        .document(condominioId)
        .collection(SUBCOLLECTION_VECINOS)
        .document(vecinoId)
    try {
      docRef
        .get()
        .await()
        .toObject(Vecino::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      throw e
    }
  }

  suspend fun actualizarVecino(
    condominioId: String,
    vecinoId: String,
    unidad: String,
    nombreCompleto: String,
    telefono: String,
    alicuotaPct: Double,
  ): Result<Unit> = runCatching {
    requireUserId()
    val docRef =
      db.collection(COLLECTION_CONDOMINIOS)
        .document(condominioId)
        .collection(SUBCOLLECTION_VECINOS)
        .document(vecinoId)
    try {
      docRef
        .update(
          mapOf(
            "unidad" to unidad.trim().take(40),
            "nombreCompleto" to nombreCompleto.trim().take(80),
            "telefono" to telefono.trim().take(30),
            "alicuotaPct" to alicuotaPct.coerceIn(0.01, 100.0),
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

  suspend fun reclamarUnidadVecino(condominioId: String, vecinoId: String): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val docRef =
        db.collection(COLLECTION_CONDOMINIOS)
          .document(condominioId)
          .collection(SUBCOLLECTION_VECINOS)
          .document(vecinoId)
      try {
        docRef
          .update(mapOf("residentUid" to uid, "updatedAt" to FieldValue.serverTimestamp()))
          .await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
        throw e
      }
    }

  suspend fun eliminarVecino(condominioId: String, vecinoId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef =
      db.collection(COLLECTION_CONDOMINIOS)
        .document(condominioId)
        .collection(SUBCOLLECTION_VECINOS)
        .document(vecinoId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- 3. Colección 'gastos_mensuales' ---

  fun observeGastosMensuales(condominioId: String): Flow<List<GastoMensual>> = flow {
    val uid = requireUserId()
    val path = "$COLLECTION_CONDOMINIOS/$condominioId/$SUBCOLLECTION_GASTOS"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(GastoMensual::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  suspend fun registrarGastoMensual(
    condominio: Condominio,
    periodoMes: String,
    concepto: String,
    categoria: String,
    montoUsd: Double,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val gastoId = newDocId("gasto")
    val docRef =
      db.collection(COLLECTION_CONDOMINIOS)
        .document(condominio.id)
        .collection(SUBCOLLECTION_GASTOS)
        .document(gastoId)

    val cleanUsd = round2(montoUsd.coerceAtLeast(0.01))
    val montoBs = round2(cleanUsd * condominio.tasaBcv)
    val members = (condominio.memberUids + uid).distinct().take(50)

    val payload =
      mapOf(
        "condominioId" to condominio.id,
        "periodoMes" to periodoMes.trim().take(16),
        "concepto" to concepto.trim().take(100),
        "categoria" to categoria.trim().take(40),
        "montoUsd" to cleanUsd,
        "montoBs" to montoBs,
        "tasaBcvAplicada" to condominio.tasaBcv,
        "distribuido" to false,
        "adminUid" to condominio.adminUid,
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
    gastoId
  }

  suspend fun distribuirGastosPorAlicuota(
    condominio: Condominio,
    vecinos: List<Vecino>,
    gastosPendientes: List<GastoMensual>,
  ): Result<Int> = runCatching {
    requireUserId()
    require(vecinos.isNotEmpty()) { "Debe registrar al menos un vecino o unidad antes de prorratear." }
    val totalGastosUsd = round2(gastosPendientes.sumOf { it.montoUsd })
    require(totalGastosUsd > 0.0) { "No hay gastos pendientes por distribuir." }

    val totalAlicuota = vecinos.sumOf { it.alicuotaPct }.coerceAtLeast(0.01)
    val condominioRef = db.collection(COLLECTION_CONDOMINIOS).document(condominio.id)
    val batch = db.batch()
    var actualizados = 0

    for (vecino in vecinos) {
      val proporcion = vecino.alicuotaPct / totalAlicuota
      val cuotaUsd = round2(totalGastosUsd * proporcion)
      val nuevoSaldoUsd = round2(vecino.saldoPendienteUsd + cuotaUsd)
      val nuevoSaldoBs = round2(nuevoSaldoUsd * condominio.tasaBcv)

      val vecinoRef = condominioRef.collection(SUBCOLLECTION_VECINOS).document(vecino.id)
      batch.update(
        vecinoRef,
        mapOf(
          "saldoPendienteUsd" to nuevoSaldoUsd,
          "saldoPendienteBs" to nuevoSaldoBs,
          "estadoSolvencia" to if (nuevoSaldoUsd > 0.0) "pendiente" else "solvente",
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      actualizados++
    }

    for (gasto in gastosPendientes) {
      val gastoRef = condominioRef.collection(SUBCOLLECTION_GASTOS).document(gasto.id)
      batch.update(
        gastoRef,
        mapOf("distribuido" to true, "updatedAt" to FieldValue.serverTimestamp()),
      )
    }

    try {
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, condominioRef.path, auth)
      throw e
    }
    actualizados
  }

  suspend fun getGastoMensual(condominioId: String, gastoId: String): Result<GastoMensual?> =
    runCatching {
      requireUserId()
      val docRef =
        db.collection(COLLECTION_CONDOMINIOS)
          .document(condominioId)
          .collection(SUBCOLLECTION_GASTOS)
          .document(gastoId)
      try {
        docRef
          .get()
          .await()
          .toObject(GastoMensual::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, docRef.path, auth)
        throw e
      }
    }

  suspend fun actualizarGastoMensual(
    condominio: Condominio,
    gastoId: String,
    periodoMes: String,
    concepto: String,
    categoria: String,
    montoUsd: Double,
  ): Result<Unit> = runCatching {
    requireUserId()
    val docRef =
      db.collection(COLLECTION_CONDOMINIOS)
        .document(condominio.id)
        .collection(SUBCOLLECTION_GASTOS)
        .document(gastoId)
    val cleanUsd = round2(montoUsd.coerceAtLeast(0.01))
    val montoBs = round2(cleanUsd * condominio.tasaBcv)
    try {
      docRef
        .update(
          mapOf(
            "periodoMes" to periodoMes.trim().take(16),
            "concepto" to concepto.trim().take(100),
            "categoria" to categoria.trim().take(40),
            "montoUsd" to cleanUsd,
            "montoBs" to montoBs,
            "tasaBcvAplicada" to condominio.tasaBcv,
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

  suspend fun eliminarGastoMensual(condominioId: String, gastoId: String): Result<Unit> =
    runCatching {
      requireUserId()
      val docRef =
        db.collection(COLLECTION_CONDOMINIOS)
          .document(condominioId)
          .collection(SUBCOLLECTION_GASTOS)
          .document(gastoId)
      try {
        docRef.delete().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
        throw e
      }
    }

  // --- 4. Colección 'pagos_reportados' ---

  fun observePagosReportadosAdmin(condominioId: String): Flow<List<PagoReportado>> = flow {
    val uid = requireUserId()
    val path = "$COLLECTION_CONDOMINIOS/$condominioId/$SUBCOLLECTION_PAGOS"
    emitAll(
      db.collection(path)
        .whereEqualTo("adminUid", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(PagoReportado::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  fun observeMisPagosReportados(condominioId: String): Flow<List<PagoReportado>> = flow {
    val uid = requireUserId()
    val path = "$COLLECTION_CONDOMINIOS/$condominioId/$SUBCOLLECTION_PAGOS"
    emitAll(
      db.collection(path)
        .whereEqualTo("reportadoPorUid", uid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(PagoReportado::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .withFirestoreErrorLog(uid, OperationType.LIST, path)
    )
  }

  suspend fun reportarPago(
    condominio: Condominio,
    vecino: Vecino,
    periodoMes: String,
    metodoPago: String,
    bancoOrigen: String,
    numeroReferencia: String,
    telefonoEmisor: String,
    moneda: String,
    montoReportado: Double,
    fechaPago: String,
    comprobanteBase64: String = "",
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val pagoId = newDocId("pago")
    val condominioRef = db.collection(COLLECTION_CONDOMINIOS).document(condominio.id)
    val pagoRef = condominioRef.collection(SUBCOLLECTION_PAGOS).document(pagoId)
    val vecinoRef = condominioRef.collection(SUBCOLLECTION_VECINOS).document(vecino.id)

    val equivalenteUsd =
      if (moneda == "VES") round2(montoReportado / condominio.tasaBcv.coerceAtLeast(1.0))
      else round2(montoReportado)
    val members = (condominio.memberUids + uid).distinct().take(50)

    val payload =
      mutableMapOf<String, Any>(
        "condominioId" to condominio.id,
        "vecinoId" to vecino.id,
        "unidad" to vecino.unidad,
        "periodoMes" to periodoMes.trim().take(16),
        "metodoPago" to metodoPago,
        "bancoOrigen" to bancoOrigen.trim().take(60),
        "numeroReferencia" to numeroReferencia.trim().take(40),
        "telefonoEmisor" to telefonoEmisor.trim().take(24),
        "moneda" to moneda,
        "montoReportado" to round2(montoReportado),
        "montoEquivalenteUsd" to equivalenteUsd,
        "tasaBcvUsada" to condominio.tasaBcv,
        "fechaPago" to fechaPago.trim().take(20),
        "estado" to "pendiente_revision",
        "notaConciliacion" to "",
        "adminUid" to condominio.adminUid,
        "reportadoPorUid" to uid,
        "memberUids" to members,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (comprobanteBase64.isNotBlank()) {
      payload["comprobanteBase64"] = comprobanteBase64.take(79000)
    }

    val batch = db.batch()
    batch.set(pagoRef, payload)
    if (vecino.estadoSolvencia == "pendiente" || vecino.estadoSolvencia == "moroso") {
      batch.update(
        vecinoRef,
        mapOf("estadoSolvencia" to "en_revision", "updatedAt" to FieldValue.serverTimestamp()),
      )
    }

    try {
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, pagoRef.path, auth)
      throw e
    }
    pagoId
  }

  suspend fun getPagoReportado(condominioId: String, pagoId: String): Result<PagoReportado?> =
    runCatching {
      requireUserId()
      val docRef =
        db.collection(COLLECTION_CONDOMINIOS)
          .document(condominioId)
          .collection(SUBCOLLECTION_PAGOS)
          .document(pagoId)
      try {
        docRef
          .get()
          .await()
          .toObject(PagoReportado::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, docRef.path, auth)
        throw e
      }
    }

  suspend fun conciliarPago(
    condominio: Condominio,
    pago: PagoReportado,
    vecino: Vecino,
    aprobar: Boolean,
    notaConciliacion: String,
  ): Result<Unit> = runCatching {
    requireUserId()
    val condominioRef = db.collection(COLLECTION_CONDOMINIOS).document(condominio.id)
    val pagoRef = condominioRef.collection(SUBCOLLECTION_PAGOS).document(pago.id)
    val vecinoRef = condominioRef.collection(SUBCOLLECTION_VECINOS).document(vecino.id)

    val nuevoSaldoUsd =
      if (aprobar) round2((vecino.saldoPendienteUsd - pago.montoEquivalenteUsd).coerceAtLeast(0.0))
      else vecino.saldoPendienteUsd
    val nuevoSaldoBs = round2(nuevoSaldoUsd * condominio.tasaBcv)
    val nuevoEstadoVecino =
      when {
        aprobar && nuevoSaldoUsd <= 0.0 -> "solvente"
        else -> "pendiente"
      }

    val batch = db.batch()
    batch.update(
      pagoRef,
      mapOf(
        "estado" to if (aprobar) "aprobado" else "rechazado",
        "notaConciliacion" to notaConciliacion.trim().take(200),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    )
    batch.update(
      vecinoRef,
      mapOf(
        "saldoPendienteUsd" to nuevoSaldoUsd,
        "saldoPendienteBs" to nuevoSaldoBs,
        "estadoSolvencia" to nuevoEstadoVecino,
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    )

    try {
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, pagoRef.path, auth)
      throw e
    }
  }

  suspend fun eliminarPagoReportado(condominioId: String, pagoId: String): Result<Unit> =
    runCatching {
      requireUserId()
      val docRef =
        db.collection(COLLECTION_CONDOMINIOS)
          .document(condominioId)
          .collection(SUBCOLLECTION_PAGOS)
          .document(pagoId)
      try {
        docRef.delete().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
        throw e
      }
    }

  companion object {
    const val COLLECTION_CONDOMINIOS = "condominios"
    const val SUBCOLLECTION_VECINOS = "vecinos"
    const val SUBCOLLECTION_GASTOS = "gastos_mensuales"
    const val SUBCOLLECTION_PAGOS = "pagos_reportados"
  }
}
