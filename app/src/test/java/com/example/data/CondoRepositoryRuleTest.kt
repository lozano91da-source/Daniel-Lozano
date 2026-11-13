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

class CondoRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun fullCondoBillingPaymentAndQrFlow_succeedsWithAliquotCalculation() = runBlocking {
    val juntaUid = signInTestUser("junta_${UUID.randomUUID().toString().take(6)}@condozulia.test")
    val repo = CondoRepository(firestore, auth)

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Junta Lago Mar").getOrThrow() }

    val condoId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createCondominium(
            name = "Res Lago Mar",
            address = "Av. El Milagro",
            bcvRate = 50.0,
            pagoMovilBank = "Banesco",
            pagoMovilPhone = "04146001122",
            pagoMovilId = "J-30123456-7",
            transferBank = "Mercantil",
            transferAccount = "0105-0011-22-1234567890",
            initialUnits =
              listOf(
                Triple("Apto 1A", "Familia Quintero", 40.0),
                Triple("Apto 1B", "Familia Urdaneta", 60.0),
              ),
          )
          .getOrThrow()
      }

    val condos =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCondominiums().first { list -> list.any { it.id == condoId } }
      }
    val condo = condos.first { it.id == condoId }
    assertEquals(juntaUid, condo.adminUid)

    val units =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeUnits(condoId).first { it.size == 2 } }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo
        .addMonthlyExpense(
          condo = condo,
          billingPeriod = "2026-11",
          title = "Vigilancia 24h",
          category = "Vigilancia",
          amountUsd = 1000.0,
        )
        .getOrThrow()
    }

    val expenses =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeExpenses(condoId).first { it.isNotEmpty() } }

    val generatedCount =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .generateMonthlyInvoices(
            condo = condo,
            billingPeriod = "2026-11",
            units = units,
            expensesForPeriod = expenses,
            existingInvoicesForPeriod = emptyList(),
          )
          .getOrThrow()
      }
    assertEquals(2, generatedCount)

    val invoices =
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeInvoices(condoId).first { it.size == 2 } }
    val inv1A = invoices.first { it.unitNumber == "Apto 1A" }
    val inv1B = invoices.first { it.unitNumber == "Apto 1B" }
    assertEquals(400.0, inv1A.amountDueUsd, 0.01)
    assertEquals(20000.0, inv1A.amountDueBs, 0.01)
    assertEquals(600.0, inv1B.amountDueUsd, 0.01)

    val paymentId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .reportPayment(
            condo = condo,
            invoice = inv1A,
            method = "pago_movil",
            bankName = "Banesco",
            referenceNumber = "00998877",
            senderPhone = "04141112233",
            currency = "VES",
            amountReported = 20000.0,
            paymentDate = "2026-11-12",
          )
          .getOrThrow()
      }

    val payments =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePaymentsForAdmin(condoId).first { list -> list.any { it.id == paymentId } }
      }
    val payment = payments.first { it.id == paymentId }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.reconcilePayment(condoId, payment, approve = true, reviewNote = "OK").getOrThrow()
    }

    val updatedInv1A =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInvoices(condoId).first { list ->
          list.first { it.id == inv1A.id }.status == "paid"
        }
      }
    assertEquals("paid", updatedInv1A.first { it.id == inv1A.id }.status)

    val pass =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createVisitorPass(
            condo = condo,
            unit = units.first(),
            visitorName = "Pedro Rincón",
            visitorCedula = "V-15222333",
            vehiclePlate = "AA111BB",
            visitType = "Visita",
          )
          .getOrThrow()
      }
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateVisitorPassStatus(condoId, pass.id, "checked_in").getOrThrow()
    }
    val checkedInPass =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeVisitorPasses(condoId).first { list ->
          list.first { it.id == pass.id }.status == "checked_in"
        }
      }
    assertEquals("checked_in", checkedInPass.first { it.id == pass.id }.status)
  }

  @Test
  fun crossUserAccess_outsiderCannotReadCondoOrAddExpense() = runBlocking {
    signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@condozulia.test")
    val aliceRepo = CondoRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.ensureUserProfile("Alice").getOrThrow() }
    val condoId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .createCondominium(
            name = "Torre Vereda",
            address = "Calle 72",
            bcvRate = 48.0,
            pagoMovilBank = "Banesco",
            pagoMovilPhone = "04140000000",
            pagoMovilId = "J-11111111-1",
            transferBank = "Mercantil",
            transferAccount = "0105-0000",
          )
          .getOrThrow()
      }
    val condo =
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeCondominiums().first { list -> list.any { it.id == condoId } }
      }.first { it.id == condoId }

    // Switch to Bob (outsider)
    signInTestUser("bob_${UUID.randomUUID().toString().take(6)}@condozulia.test")
    val bobRepo = CondoRepository(firestore, auth)

    val expenseResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        bobRepo.addMonthlyExpense(
          condo = condo,
          billingPeriod = "2026-11",
          title = "Gasto no autorizado",
          category = "Otro",
          amountUsd = 50.0,
        )
      }
    assertTrue(expenseResult.isFailure)
    val firestoreErr = expenseResult.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr?.code)
  }

  @Test
  fun unauthenticatedQuery_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    var caught: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        firestore.collection("condominiums").whereArrayContains("memberUids", "nobody").snapshots().first()
      }
    } catch (e: Exception) {
      caught = e
    }
    val firestoreErr = checkNotNull(caught?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $caught"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr.code)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3500L
  }
}
