package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CondoRepository
import com.example.model.CondoRole
import com.example.model.CondoUnit
import com.example.model.Condominium
import com.example.model.MonthlyExpense
import com.example.model.PaymentReminder
import com.example.model.PaymentReport
import com.example.model.UiState
import com.example.model.UnitInvoice
import com.example.model.UserProfile
import com.example.model.VisitorPass
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CurrencyDisplay(val label: String) {
  USD("USD $"),
  VES("Bolívares Bs."),
}

data class CondoWorkspaceData(
  val profile: UserProfile?,
  val condominiums: List<Condominium>,
  val activeCondo: Condominium?,
  val units: List<CondoUnit>,
  val activeUnit: CondoUnit?,
  val expenses: List<MonthlyExpense>,
  val invoices: List<UnitInvoice>,
  val adminPayments: List<PaymentReport>,
  val residentPayments: List<PaymentReport>,
  val visitorPasses: List<VisitorPass>,
  val reminders: List<PaymentReminder>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CondoViewModel(
  private val repository: CondoRepository,
  private val currentUserId: String,
  private val initialDisplayName: String,
) : ViewModel() {

  private val _selectedRole = MutableStateFlow(CondoRole.JUNTA)
  val selectedRole: StateFlow<CondoRole> = _selectedRole.asStateFlow()

  private val _currencyDisplay = MutableStateFlow(CurrencyDisplay.USD)
  val currencyDisplay: StateFlow<CurrencyDisplay> = _currencyDisplay.asStateFlow()

  private val _billingPeriod =
    MutableStateFlow(SimpleDateFormat("yyyy-MM", Locale.US).format(Date()))
  val billingPeriod: StateFlow<String> = _billingPeriod.asStateFlow()

  private val _selectedCondoIdOverride = MutableStateFlow<String?>(null)
  private val _selectedUnitIdOverride = MutableStateFlow<String?>(null)

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  init {
    viewModelScope.launch {
      repository.ensureUserProfile(initialDisplayName).onSuccess { profile ->
        _selectedRole.value = CondoRole.fromKey(profile.activeRole)
      }
    }
  }

  private fun <T> Flow<T>.unwrapFirestoreCatch(tag: String, fallback: T): Flow<T> = catch { error ->
    val firestoreError =
      generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    Log.w(tag, "Firestore stream warning: ${firestoreError?.message ?: error.message}")
    emit(fallback)
  }

  private val profileFlow: Flow<UserProfile?> =
    repository.observeUserProfile(currentUserId).unwrapFirestoreCatch("CondoVM", null)

  private val condosFlow: Flow<List<Condominium>> =
    repository.observeCondominiums().unwrapFirestoreCatch("CondoVM", emptyList())

  val uiState: StateFlow<UiState<CondoWorkspaceData>> =
    combine(profileFlow, condosFlow, _selectedCondoIdOverride, _selectedUnitIdOverride) {
        profile,
        condos,
        condoOverride,
        unitOverride ->
        val chosenCondoId =
          condoOverride?.takeIf { id -> condos.any { it.id == id } }
            ?: profile?.activeCondoId?.takeIf { id -> condos.any { it.id == id } }
            ?: condos.firstOrNull()?.id
            ?: ""
        val activeCondo = condos.firstOrNull { it.id == chosenCondoId }
        Quadruple(profile, condos, activeCondo, unitOverride)
      }
      .flatMapLatest { (profile, condos, activeCondo, unitOverride) ->
        if (activeCondo == null) {
          flowOf<UiState<CondoWorkspaceData>>(
            UiState.Success(
              CondoWorkspaceData(
                profile = profile,
                condominiums = condos,
                activeCondo = null,
                units = emptyList(),
                activeUnit = null,
                expenses = emptyList(),
                invoices = emptyList(),
                adminPayments = emptyList(),
                residentPayments = emptyList(),
                visitorPasses = emptyList(),
                reminders = emptyList(),
              )
            )
          )
        } else {
          val condoId = activeCondo.id
          val isAdmin = activeCondo.adminUid == currentUserId
          val adminPaymentsFlow =
            if (isAdmin) {
              repository.observePaymentsForAdmin(condoId).unwrapFirestoreCatch("CondoVM", emptyList())
            } else {
              flowOf(emptyList())
            }

          combine(
            repository.observeUnits(condoId).unwrapFirestoreCatch("CondoVM", emptyList()),
            repository.observeExpenses(condoId).unwrapFirestoreCatch("CondoVM", emptyList()),
            repository.observeInvoices(condoId).unwrapFirestoreCatch("CondoVM", emptyList()),
            adminPaymentsFlow,
            repository.observePaymentsForResident(condoId).unwrapFirestoreCatch("CondoVM", emptyList()),
            repository.observeVisitorPasses(condoId).unwrapFirestoreCatch("CondoVM", emptyList()),
            repository.observeReminders(condoId).unwrapFirestoreCatch("CondoVM", emptyList()),
          ) { array ->
            @Suppress("UNCHECKED_CAST")
            val units = array[0] as List<CondoUnit>
            @Suppress("UNCHECKED_CAST")
            val expenses = array[1] as List<MonthlyExpense>
            @Suppress("UNCHECKED_CAST")
            val invoices = array[2] as List<UnitInvoice>
            @Suppress("UNCHECKED_CAST")
            val adminPayments = array[3] as List<PaymentReport>
            @Suppress("UNCHECKED_CAST")
            val residentPayments = array[4] as List<PaymentReport>
            @Suppress("UNCHECKED_CAST")
            val visitorPasses = array[5] as List<VisitorPass>
            @Suppress("UNCHECKED_CAST")
            val reminders = array[6] as List<PaymentReminder>

            val chosenUnit =
              unitOverride?.let { uid -> units.firstOrNull { it.id == uid } }
                ?: profile?.activeUnitId?.let { uid -> units.firstOrNull { it.id == uid } }
                ?: units.firstOrNull { it.residentUid == currentUserId }
                ?: units.firstOrNull()

            if (activeCondo.adminUid == currentUserId &&
              units.any { it.memberUids.toSet() != activeCondo.memberUids.toSet() }
            ) {
              viewModelScope.launch { repository.syncCondoMembers(activeCondo) }
            }

            UiState.Success(
              CondoWorkspaceData(
                profile = profile,
                condominiums = condos,
                activeCondo = activeCondo,
                units = units,
                activeUnit = chosenUnit,
                expenses = expenses,
                invoices = invoices,
                adminPayments = adminPayments,
                residentPayments = residentPayments,
                visitorPasses = visitorPasses,
                reminders = reminders,
              )
            )
          }
        }
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        emit(UiState.Error((firestoreError ?: error).message ?: "Error al cargar condominio"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  fun setRole(role: CondoRole, condoId: String?, unitId: String?) {
    _selectedRole.value = role
    if (!condoId.isNullOrBlank()) {
      viewModelScope.launch {
        repository.updateActiveSelection(condoId, unitId.orEmpty(), role)
      }
    }
  }

  fun setCurrency(currency: CurrencyDisplay) {
    _currencyDisplay.value = currency
  }

  fun setBillingPeriod(period: String) {
    _billingPeriod.value = period
  }

  fun selectCondo(condoId: String) {
    _selectedCondoIdOverride.value = condoId
    _selectedUnitIdOverride.value = null
    viewModelScope.launch {
      repository.updateActiveSelection(condoId, "", _selectedRole.value)
    }
  }

  fun selectUnit(condoId: String, unitId: String, claimIfUnassigned: Boolean = false) {
    _selectedUnitIdOverride.value = unitId
    viewModelScope.launch {
      if (claimIfUnassigned) {
        repository.claimUnit(condoId, unitId)
      } else {
        repository.updateActiveSelection(condoId, unitId, _selectedRole.value)
      }
    }
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun showMessage(message: String) {
    _statusBanner.value = message
  }

  fun createCondominium(
    name: String,
    address: String,
    bcvRate: Double,
    pagoMovilBank: String,
    pagoMovilPhone: String,
    pagoMovilId: String,
    transferBank: String,
    transferAccount: String,
    includeMaracaiboUnitsTemplate: Boolean,
  ) {
    val unitsTemplate =
      if (includeMaracaiboUnitsTemplate) {
        listOf(
          Triple("Torre A - 1A", "Propietario 1A", 16.5),
          Triple("Torre A - 1B", "Propietario 1B", 16.5),
          Triple("Torre A - 2A", "Propietario 2A", 17.0),
          Triple("Torre A - 2B", "Propietario 2B", 17.0),
          Triple("Torre B - 3A", "Propietario 3A", 16.5),
          Triple("Penthouse PH", "Propietario PH", 16.5),
        )
      } else {
        emptyList()
      }

    viewModelScope.launch {
      _isBusy.value = true
      repository
        .createCondominium(
          name = name,
          address = address,
          bcvRate = bcvRate,
          pagoMovilBank = pagoMovilBank,
          pagoMovilPhone = pagoMovilPhone,
          pagoMovilId = pagoMovilId,
          transferBank = transferBank,
          transferAccount = transferAccount,
          initialUnits = unitsTemplate,
        )
        .onSuccess { condoId ->
          _selectedCondoIdOverride.value = condoId
          _selectedRole.value = CondoRole.JUNTA
          _statusBanner.value = "Condominio creado correctamente."
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "No se pudo crear el condominio." }
      _isBusy.value = false
    }
  }

  fun joinCondominium(code: String) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .joinCondominiumByCode(code)
        .onSuccess { condoId ->
          _selectedCondoIdOverride.value = condoId
          _selectedRole.value = CondoRole.VECINO
          _statusBanner.value = "Vinculado al condominio exitosamente."
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Código inválido." }
      _isBusy.value = false
    }
  }

  fun updateCondoSettings(
    condo: Condominium,
    bcvRate: Double,
    pagoMovilBank: String,
    pagoMovilPhone: String,
    pagoMovilId: String,
    transferBank: String,
    transferAccount: String,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .updateCondominiumSettings(
          condo = condo,
          bcvRate = bcvRate,
          pagoMovilBank = pagoMovilBank,
          pagoMovilPhone = pagoMovilPhone,
          pagoMovilId = pagoMovilId,
          transferBank = transferBank,
          transferAccount = transferAccount,
        )
        .onSuccess { _statusBanner.value = "Tasa BCV y cuentas actualizadas." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al actualizar ajustes." }
      _isBusy.value = false
    }
  }

  fun addUnit(condo: Condominium, unitNumber: String, ownerLabel: String, alicuotaPct: Double) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .addUnit(condo, unitNumber, ownerLabel, alicuotaPct)
        .onSuccess { _statusBanner.value = "Unidad $unitNumber agregada." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al agregar unidad." }
      _isBusy.value = false
    }
  }

  fun addExpense(condo: Condominium, title: String, category: String, amountUsd: Double) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .addMonthlyExpense(condo, _billingPeriod.value, title, category, amountUsd)
        .onSuccess { _statusBanner.value = "Gasto '$title' registrado." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al cargar gasto." }
      _isBusy.value = false
    }
  }

  fun deleteExpense(condoId: String, expenseId: String) {
    viewModelScope.launch {
      repository
        .deleteExpense(condoId, expenseId)
        .onFailure { _statusBanner.value = it.localizedMessage ?: "No se pudo eliminar el gasto." }
    }
  }

  fun generateMonthlyInvoices(
    condo: Condominium,
    units: List<CondoUnit>,
    expensesForPeriod: List<MonthlyExpense>,
    existingInvoicesForPeriod: List<UnitInvoice>,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .generateMonthlyInvoices(
          condo = condo,
          billingPeriod = _billingPeriod.value,
          units = units,
          expensesForPeriod = expensesForPeriod,
          existingInvoicesForPeriod = existingInvoicesForPeriod,
        )
        .onSuccess { count ->
          _statusBanner.value = "Alícuotas divididas automáticamente ($count recibos emitidos)."
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al generar recibos." }
      _isBusy.value = false
    }
  }

  fun reportPayment(
    condo: Condominium,
    invoice: UnitInvoice,
    method: String,
    bankName: String,
    referenceNumber: String,
    senderPhone: String,
    currency: String,
    amountReported: Double,
    paymentDate: String,
    receiptPreviewBase64: String,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .reportPayment(
          condo = condo,
          invoice = invoice,
          method = method,
          bankName = bankName,
          referenceNumber = referenceNumber,
          senderPhone = senderPhone,
          currency = currency,
          amountReported = amountReported,
          paymentDate = paymentDate,
          receiptPreviewBase64 = receiptPreviewBase64,
        )
        .onSuccess {
          _statusBanner.value = "Pago reportado. En espera de conciliación por la Junta."
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al reportar pago." }
      _isBusy.value = false
    }
  }

  fun reconcilePayment(condoId: String, payment: PaymentReport, approve: Boolean, note: String) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .reconcilePayment(condoId, payment, approve, note)
        .onSuccess {
          _statusBanner.value =
            if (approve) "Pago ref. ${payment.referenceNumber} conciliado y recibo solvente."
            else "Pago rechazado. Se notificó estado pendiente."
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al conciliar." }
      _isBusy.value = false
    }
  }

  fun markOverdue(condoId: String, invoiceId: String) {
    viewModelScope.launch {
      repository.markInvoiceOverdue(condoId, invoiceId).onSuccess {
        _statusBanner.value = "Unidad marcada en mora."
      }
    }
  }

  fun createVisitorPass(
    condo: Condominium,
    unit: CondoUnit,
    visitorName: String,
    visitorCedula: String,
    vehiclePlate: String,
    visitType: String,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .createVisitorPass(condo, unit, visitorName, visitorCedula, vehiclePlate, visitType)
        .onSuccess { pass -> _statusBanner.value = "Pase QR ${pass.qrToken} generado para ${pass.visitorName}." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al generar pase QR." }
      _isBusy.value = false
    }
  }

  fun updatePassStatus(condoId: String, passId: String, nextStatus: String) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .updateVisitorPassStatus(condoId, passId, nextStatus)
        .onSuccess {
          val msg =
            when (nextStatus) {
              "checked_in" -> "Entrada autorizada en garita."
              "checked_out" -> "Salida registrada en garita."
              else -> "Pase actualizado."
            }
          _statusBanner.value = msg
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error en garita." }
      _isBusy.value = false
    }
  }

  fun sendPaymentReminder(condo: Condominium, invoice: UnitInvoice, onLocalNotify: (String, String) -> Unit) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .sendPaymentReminder(condo, invoice)
        .onSuccess {
          val title = "Recordatorio · ${invoice.unitNumber}"
          val body =
            "Saldo pendiente ${invoice.billingPeriod}: $${String.format(Locale.US, "%.2f", invoice.amountDueUsd)} / Bs. ${String.format(Locale.US, "%.2f", invoice.amountDueBs)}"
          onLocalNotify(title, body)
          _statusBanner.value = "Recordatorio enviado a ${invoice.unitNumber}."
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al enviar recordatorio." }
      _isBusy.value = false
    }
  }

  fun markReminderRead(condoId: String, reminderId: String) {
    viewModelScope.launch { repository.markReminderRead(condoId, reminderId) }
  }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
