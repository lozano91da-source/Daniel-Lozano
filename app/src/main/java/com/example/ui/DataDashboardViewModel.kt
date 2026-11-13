package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DataRepository
import com.example.model.Condominio
import com.example.model.GastoMensual
import com.example.model.PagoReportado
import com.example.model.UiState
import com.example.model.Vecino
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DashboardSection(val key: String, val label: String) {
  RESIDENT("vecino", "Vecino"),
  ADMIN("junta", "Junta Admin"),
}

data class CondoDashboardUiData(
  val condominios: List<Condominio>,
  val activeCondo: Condominio?,
  val vecinos: List<Vecino>,
  val activeVecino: Vecino?,
  val gastos: List<GastoMensual>,
  val pagosAdmin: List<PagoReportado>,
  val misPagos: List<PagoReportado>,
  val isAdmin: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DataDashboardViewModel(
  private val repository: DataRepository,
  val currentUserId: String,
  val displayName: String,
) : ViewModel() {

  private val _selectedSection = MutableStateFlow(DashboardSection.RESIDENT)
  val selectedSection: StateFlow<DashboardSection> = _selectedSection.asStateFlow()

  private val _currencyDisplay = MutableStateFlow(CurrencyDisplay.USD)
  val currencyDisplay: StateFlow<CurrencyDisplay> = _currencyDisplay.asStateFlow()

  private val _billingPeriod =
    MutableStateFlow(SimpleDateFormat("yyyy-MM", Locale.US).format(Date()))
  val billingPeriod: StateFlow<String> = _billingPeriod.asStateFlow()

  private val _selectedCondoId = MutableStateFlow<String?>(null)
  private val _selectedVecinoId = MutableStateFlow<String?>(null)

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private fun <T> Flow<T>.unwrapFirestoreCatch(tag: String, fallback: T): Flow<T> = catch { error ->
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    Log.w(tag, "Firestore stream warning: ${firestoreError?.message ?: error.message}")
    emit(fallback)
  }

  private val condominiosFlow: Flow<List<Condominio>> =
    repository.observeCondominios().unwrapFirestoreCatch("DataDashVM", emptyList())

  val uiState: StateFlow<UiState<CondoDashboardUiData>> =
    combine(condominiosFlow, _selectedCondoId, _selectedVecinoId) { condominios, condoId, vecinoId ->
      val activeCondo =
        condoId?.let { id -> condominios.firstOrNull { it.id == id } } ?: condominios.firstOrNull()
      Triple(condominios, activeCondo, vecinoId)
    }
      .flatMapLatest { (condominios, activeCondo, vecinoId) ->
        if (activeCondo == null) {
          flowOf<UiState<CondoDashboardUiData>>(
            UiState.Success(
              CondoDashboardUiData(
                condominios = condominios,
                activeCondo = null,
                vecinos = emptyList(),
                activeVecino = null,
                gastos = emptyList(),
                pagosAdmin = emptyList(),
                misPagos = emptyList(),
                isAdmin = false,
              )
            )
          )
        } else {
          val condominioId = activeCondo.id
          val isAdmin = activeCondo.adminUid == currentUserId
          val adminPagosFlow =
            if (isAdmin) {
              repository.observePagosReportadosAdmin(condominioId).unwrapFirestoreCatch("DataDashVM", emptyList())
            } else {
              flowOf(emptyList())
            }

          combine(
            repository.observeVecinos(condominioId).unwrapFirestoreCatch("DataDashVM", emptyList()),
            repository.observeGastosMensuales(condominioId).unwrapFirestoreCatch("DataDashVM", emptyList()),
            adminPagosFlow,
            repository.observeMisPagosReportados(condominioId).unwrapFirestoreCatch("DataDashVM", emptyList()),
          ) { vecinos, gastos, pagosAdmin, misPagos ->
            val activeVecino =
              vecinoId?.let { id -> vecinos.firstOrNull { it.id == id } }
                ?: vecinos.firstOrNull { it.residentUid == currentUserId }
                ?: vecinos.firstOrNull()

            if (isAdmin &&
              (vecinos.any { it.memberUids.toSet() != activeCondo.memberUids.toSet() } ||
                gastos.any { it.memberUids.toSet() != activeCondo.memberUids.toSet() })
            ) {
              viewModelScope.launch {
                repository.sincronizarMiembrosCondominio(activeCondo, vecinos, gastos)
              }
            }

            UiState.Success(
              CondoDashboardUiData(
                condominios = condominios,
                activeCondo = activeCondo,
                vecinos = vecinos,
                activeVecino = activeVecino,
                gastos = gastos,
                pagosAdmin = pagosAdmin,
                misPagos = misPagos,
                isAdmin = isAdmin,
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
        emit(UiState.Error((firestoreError ?: error).localizedMessage ?: "Error cargando datos del condominio."))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  fun setSection(section: DashboardSection) {
    _selectedSection.value = section
  }

  fun setCurrency(currency: CurrencyDisplay) {
    _currencyDisplay.value = currency
  }

  fun setBillingPeriod(period: String) {
    _billingPeriod.value = period
  }

  fun selectCondominio(condominioId: String) {
    _selectedCondoId.value = condominioId
    _selectedVecinoId.value = null
  }

  fun selectVecino(vecino: Vecino, claimIfUnassigned: Boolean) {
    _selectedVecinoId.value = vecino.id
    if (claimIfUnassigned && vecino.residentUid.isBlank()) {
      viewModelScope.launch {
        repository
          .reclamarUnidadVecino(vecino.condominioId, vecino.id)
          .onSuccess { _statusBanner.value = "Unidad ${vecino.unidad} vinculada a tu cuenta." }
          .onFailure { _statusBanner.value = it.localizedMessage ?: "No se pudo vincular la unidad." }
      }
    }
  }

  fun showMessage(message: String) {
    _statusBanner.value = message
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun crearCondominio(
    nombre: String,
    direccion: String,
    tasaBcv: Double,
    bancoPagoMovil: String,
    telefonoPagoMovil: String,
    rifPagoMovil: String,
    bancoTransferencia: String,
    cuentaTransferencia: String,
    incluirPlantillaMaracaibo: Boolean,
  ) {
    val plantilla =
      if (incluirPlantillaMaracaibo) {
        listOf(
          Triple("Torre A - 1A", "Familia Quintero", 16.5),
          Triple("Torre A - 1B", "Familia Urdaneta", 16.5),
          Triple("Torre A - 2A", "Familia Fuenmayor", 17.0),
          Triple("Torre A - 2B", "Familia Rincón", 17.0),
          Triple("Torre B - 3A", "Familia Briceño", 16.5),
          Triple("Penthouse PH", "Familia Romero", 16.5),
        )
      } else {
        emptyList()
      }

    viewModelScope.launch {
      _isBusy.value = true
      repository
        .crearCondominio(
          nombre = nombre,
          direccion = direccion,
          tasaBcv = tasaBcv,
          bancoPagoMovil = bancoPagoMovil,
          telefonoPagoMovil = telefonoPagoMovil,
          rifPagoMovil = rifPagoMovil,
          bancoTransferencia = bancoTransferencia,
          cuentaTransferencia = cuentaTransferencia,
          vecinosIniciales = plantilla,
        )
        .onSuccess { condominioId ->
          _selectedCondoId.value = condominioId
          _selectedSection.value = DashboardSection.ADMIN
          _statusBanner.value = "Condominio '$nombre' creado correctamente."
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "No se pudo crear el condominio." }
      _isBusy.value = false
    }
  }

  fun unirseACondominio(
    condominioId: String,
    codigoAcceso: String,
    vecinoId: String? = null,
  ) {
    val cleanCondoId = condominioId.trim()
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .unirseACondominio(cleanCondoId, codigoAcceso)
        .onSuccess {
          _selectedCondoId.value = cleanCondoId
          _selectedSection.value = DashboardSection.RESIDENT
          if (!vecinoId.isNullOrBlank()) {
            _selectedVecinoId.value = vecinoId
            repository.reclamarUnidadVecino(cleanCondoId, vecinoId)
          }
          _statusBanner.value = "Te uniste al condominio exitosamente."
        }
        .onFailure {
          _statusBanner.value = it.localizedMessage ?: "No se pudo unir. Verifica el ID y el código."
        }
      _isBusy.value = false
    }
  }

  fun generarCodigoAccesoResidente(condominioId: String, vecino: Vecino) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .generarCodigoAccesoResidente(condominioId, vecino.id)
        .onSuccess { nuevoCodigo ->
          _statusBanner.value = "Código único $nuevoCodigo generado para ${vecino.unidad}."
        }
        .onFailure {
          _statusBanner.value = it.localizedMessage ?: "No se pudo generar el código QR."
        }
      _isBusy.value = false
    }
  }

  fun rotarCodigoAccesoCondominio(condominio: Condominio) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .rotarCodigoAccesoCondominio(condominio.id, condominio.nombre)
        .onSuccess { nuevoCodigo ->
          _statusBanner.value = "Nuevo código de condominio generado: $nuevoCodigo"
        }
        .onFailure {
          _statusBanner.value = it.localizedMessage ?: "No se pudo rotar el código de acceso."
        }
      _isBusy.value = false
    }
  }

  fun actualizarTasaYCuentas(
    condominioId: String,
    tasaBcv: Double,
    bancoPagoMovil: String,
    telefonoPagoMovil: String,
    rifPagoMovil: String,
    bancoTransferencia: String,
    cuentaTransferencia: String,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .actualizarTasaYCuentas(
          condominioId = condominioId,
          tasaBcv = tasaBcv,
          bancoPagoMovil = bancoPagoMovil,
          telefonoPagoMovil = telefonoPagoMovil,
          rifPagoMovil = rifPagoMovil,
          bancoTransferencia = bancoTransferencia,
          cuentaTransferencia = cuentaTransferencia,
        )
        .onSuccess { _statusBanner.value = "Tasa BCV y cuentas bancarias actualizadas." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al actualizar configuración." }
      _isBusy.value = false
    }
  }

  fun registrarVecino(
    condominio: Condominio,
    unidad: String,
    nombreCompleto: String,
    telefono: String,
    alicuotaPct: Double,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .registrarVecino(condominio, unidad, nombreCompleto, telefono, alicuotaPct)
        .onSuccess { _statusBanner.value = "Unidad $unidad registrada." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al registrar unidad." }
      _isBusy.value = false
    }
  }

  fun actualizarVecino(
    condominioId: String,
    vecinoId: String,
    unidad: String,
    nombreCompleto: String,
    telefono: String,
    alicuotaPct: Double,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .actualizarVecino(condominioId, vecinoId, unidad, nombreCompleto, telefono, alicuotaPct)
        .onSuccess { _statusBanner.value = "Unidad $unidad actualizada." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al actualizar unidad." }
      _isBusy.value = false
    }
  }

  fun eliminarVecino(condominioId: String, vecinoId: String) {
    viewModelScope.launch {
      repository
        .eliminarVecino(condominioId, vecinoId)
        .onSuccess { _statusBanner.value = "Unidad eliminada." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "No se pudo eliminar la unidad." }
    }
  }

  fun registrarGastoMensual(
    condominio: Condominio,
    concepto: String,
    categoria: String,
    montoUsd: Double,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .registrarGastoMensual(condominio, _billingPeriod.value, concepto, categoria, montoUsd)
        .onSuccess { _statusBanner.value = "Gasto '$concepto' registrado." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al registrar gasto." }
      _isBusy.value = false
    }
  }

  fun actualizarGastoMensual(
    condominio: Condominio,
    gastoId: String,
    periodoMes: String,
    concepto: String,
    categoria: String,
    montoUsd: Double,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .actualizarGastoMensual(condominio, gastoId, periodoMes, concepto, categoria, montoUsd)
        .onSuccess { _statusBanner.value = "Gasto actualizado." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "No se pudo actualizar el gasto." }
      _isBusy.value = false
    }
  }

  fun eliminarGastoMensual(condominioId: String, gastoId: String) {
    viewModelScope.launch {
      repository
        .eliminarGastoMensual(condominioId, gastoId)
        .onSuccess { _statusBanner.value = "Gasto eliminado." }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "No se pudo eliminar el gasto." }
    }
  }

  fun distribuirGastosPorAlicuota(
    condominio: Condominio,
    vecinos: List<Vecino>,
    gastosPendientes: List<GastoMensual>,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .distribuirGastosPorAlicuota(condominio, vecinos, gastosPendientes)
        .onSuccess { count ->
          _statusBanner.value = "Alícuotas prorrateadas entre $count unidades."
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "No se pudo distribuir los gastos." }
      _isBusy.value = false
    }
  }

  fun reportarPago(
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
    comprobanteBase64: String,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .reportarPago(
          condominio = condominio,
          vecino = vecino,
          periodoMes = periodoMes,
          metodoPago = metodoPago,
          bancoOrigen = bancoOrigen,
          numeroReferencia = numeroReferencia,
          telefonoEmisor = telefonoEmisor,
          moneda = moneda,
          montoReportado = montoReportado,
          fechaPago = fechaPago,
          comprobanteBase64 = comprobanteBase64,
        )
        .onSuccess {
          _statusBanner.value = "Pago reportado correctamente. En revisión por la Junta."
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al reportar pago." }
      _isBusy.value = false
    }
  }

  fun conciliarPago(
    condominio: Condominio,
    pago: PagoReportado,
    vecino: Vecino,
    aprobar: Boolean,
    notaConciliacion: String,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .conciliarPago(condominio, pago, vecino, aprobar, notaConciliacion)
        .onSuccess {
          _statusBanner.value =
            if (aprobar) "Pago ref. ${pago.numeroReferencia} conciliado."
            else "Pago ref. ${pago.numeroReferencia} rechazado."
        }
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Error al conciliar el pago." }
      _isBusy.value = false
    }
  }
}
