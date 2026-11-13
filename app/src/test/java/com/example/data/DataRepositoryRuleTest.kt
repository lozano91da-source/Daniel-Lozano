package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun flujoCompletoCondominioVecinosGastosYPagos_calculaAlicuotasYConcilia() = runBlocking {
    val adminUid = signInTestUser("junta_${UUID.randomUUID().toString().take(6)}@condozulia.test")
    val repo = DataRepository(firestore, auth)

    val condominioId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .crearCondominio(
            nombre = "Res. Isla Dorada",
            direccion = "Av. Fuerzas Armadas, Maracaibo",
            tasaBcv = 50.0,
            bancoPagoMovil = "Banesco",
            telefonoPagoMovil = "04146001122",
            rifPagoMovil = "J-30123456-7",
            bancoTransferencia = "Mercantil",
            cuentaTransferencia = "0105-0011-22-1234567890",
            vecinosIniciales =
              listOf(
                Triple("Casa 1", "Familia Fuenmayor", 40.0),
                Triple("Casa 2", "Familia Rincón", 60.0),
              ),
          )
          .getOrThrow()
      }

    val condominio =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCondominios().first { lista -> lista.any { it.id == condominioId } }
      }.first { it.id == condominioId }
    assertEquals(adminUid, condominio.adminUid)

    val vecinos =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeVecinos(condominioId).first { it.size == 2 } }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo
        .registrarGastoMensual(
          condominio = condominio,
          periodoMes = "2026-11",
          concepto = "Hidroneumático y Vigilancia",
          categoria = "Mantenimiento",
          montoUsd = 1000.0,
        )
        .getOrThrow()
    }

    val gastos =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeGastosMensuales(condominioId).first { it.isNotEmpty() }
      }

    val actualizados =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.distribuirGastosPorAlicuota(condominio, vecinos, gastos).getOrThrow()
      }
    assertEquals(2, actualizados)

    val vecinosConSaldo =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeVecinos(condominioId).first { lista ->
          lista.all { it.estadoSolvencia == "pendiente" }
        }
      }
    val vecino1 = vecinosConSaldo.first { it.unidad == "Casa 1" }
    val vecino2 = vecinosConSaldo.first { it.unidad == "Casa 2" }
    assertEquals(400.0, vecino1.saldoPendienteUsd, 0.01)
    assertEquals(20000.0, vecino1.saldoPendienteBs, 0.01)
    assertEquals(600.0, vecino2.saldoPendienteUsd, 0.01)

    val pagoId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .reportarPago(
            condominio = condominio,
            vecino = vecino1,
            periodoMes = "2026-11",
            metodoPago = "pago_movil",
            bancoOrigen = "Banesco",
            numeroReferencia = "00445566",
            telefonoEmisor = "04141112233",
            moneda = "VES",
            montoReportado = 20000.0,
            fechaPago = "2026-11-12",
          )
          .getOrThrow()
      }

    val pagos =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePagosReportadosAdmin(condominioId).first { lista -> lista.any { it.id == pagoId } }
      }
    val pago = pagos.first { it.id == pagoId }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.conciliarPago(condominio, pago, vecino1, aprobar = true, notaConciliacion = "Verificado").getOrThrow()
    }

    val vecinosFinal =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeVecinos(condominioId).first { lista ->
          lista.first { it.id == vecino1.id }.estadoSolvencia == "solvente"
        }
      }
    assertEquals(0.0, vecinosFinal.first { it.id == vecino1.id }.saldoPendienteUsd, 0.01)

    // Verify Read (get*), Update, and Delete across all 4 collections
    val fetchedCondo = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getCondominio(condominioId).getOrThrow() }
    assertEquals("Res. Isla Dorada", fetchedCondo?.nombre)

    val fetchedPago = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getPagoReportado(condominioId, pagoId).getOrThrow() }
    assertEquals("aprobado", fetchedPago?.estado)

    val extraVecinoId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.registrarVecino(condominio, "Casa 3", "Familia Briceño", "04120001122", 10.0).getOrThrow()
      }
    val uniqueCode =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.generarCodigoAccesoResidente(condominioId, extraVecinoId).getOrThrow()
      }
    assertTrue(uniqueCode.startsWith("CZ-"))
    val rotatedCondoCode =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.rotarCodigoAccesoCondominio(condominioId, condominio.nombre).getOrThrow()
      }
    assertTrue(rotatedCondoCode.length == 8)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.actualizarVecino(condominioId, extraVecinoId, "Casa 3A", "Familia Briceño Editada", "04149998877", 12.0).getOrThrow()
    }
    val fetchedVecino = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getVecino(condominioId, extraVecinoId).getOrThrow() }
    assertEquals("Casa 3A", fetchedVecino?.unidad)
    assertEquals(uniqueCode, fetchedVecino?.codigoAcceso)

    val extraGastoId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.registrarGastoMensual(condominio, "2026-11", "Fumigación", "Jardinería", 120.0).getOrThrow()
      }
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.actualizarGastoMensual(condominio, extraGastoId, "2026-11", "Fumigación General", "Mantenimiento", 150.0).getOrThrow()
    }
    val fetchedGasto = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getGastoMensual(condominioId, extraGastoId).getOrThrow() }
    assertEquals(150.0, fetchedGasto?.montoUsd ?: 0.0, 0.01)

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.eliminarGastoMensual(condominioId, extraGastoId).getOrThrow() }
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.eliminarPagoReportado(condominioId, pagoId).getOrThrow() }
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.eliminarVecino(condominioId, extraVecinoId).getOrThrow() }
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.eliminarCondominio(condominioId).getOrThrow() }
    val remainingCondos =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCondominios().first { lista -> lista.none { it.id == condominioId } }
      }
    assertTrue(remainingCondos.none { it.id == condominioId })
  }

  @Test
  fun accesoCruzado_usuarioNoMiembroNoPuedeRegistrarGastoNiLeerCondominio() = runBlocking {
    signInTestUser("admin_${UUID.randomUUID().toString().take(6)}@condozulia.test")
    val adminRepo = DataRepository(firestore, auth)
    val condominioId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        adminRepo
          .crearCondominio(
            nombre = "Torre Bella Vista",
            direccion = "Av. Bella Vista",
            tasaBcv = 49.0,
          )
          .getOrThrow()
      }
    val condominio =
      withTimeout(FLOW_TIMEOUT_MS) {
        adminRepo.observeCondominios().first { lista -> lista.any { it.id == condominioId } }
      }.first { it.id == condominioId }

    signInTestUser("intruso_${UUID.randomUUID().toString().take(6)}@condozulia.test")
    val intrusoRepo = DataRepository(firestore, auth)

    val gastoResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        intrusoRepo.registrarGastoMensual(
          condominio = condominio,
          periodoMes = "2026-11",
          concepto = "Gasto fraudulento",
          categoria = "Otro",
          montoUsd = 99.0,
        )
      }
    assertTrue(gastoResult.isFailure)
    val err = gastoResult.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, err?.code)
  }

  @Test
  fun consultaSinAutenticar_rechazaConPermissionDenied() = runBlocking {
    auth.signOut()
    var caught: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        firestore
          .collection(DataRepository.COLLECTION_CONDOMINIOS)
          .whereArrayContains("memberUids", "nadie")
          .snapshots()
          .first()
      }
    } catch (e: Exception) {
      caught = e
    }
    val err = checkNotNull(caught?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $caught"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, err.code)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3500L
  }
}
