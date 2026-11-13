package com.example.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.DataRepository
import com.example.model.UiState
import com.example.ui.auth.signOutUser
import com.example.ui.components.CurrencyTogglePill
import com.example.ui.components.EmptyStateCard
import com.example.ui.screens.AdminDashboard
import com.example.ui.screens.CrearCondominioDialog
import com.example.ui.screens.JoinCondominioDialog
import com.example.ui.screens.ResidentDashboard
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CondoMainScreen(
  currentUserId: String,
  displayName: String,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val viewModel: DataDashboardViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY]) as Application
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            DataDashboardViewModel(
              repository = DataRepository(db),
              currentUserId = currentUserId,
              displayName = displayName,
            )
          }
        },
    )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
  val currencyDisplay by viewModel.currencyDisplay.collectAsStateWithLifecycle()
  val billingPeriod by viewModel.billingPeriod.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  var showCondoMenu by remember { mutableStateOf(false) }
  var showCreateDialog by remember { mutableStateOf(false) }
  var showJoinDialog by remember { mutableStateOf(false) }

  LaunchedEffect(statusBanner) {
    statusBanner?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearBanner()
    }
  }

  val dashboardData = (uiState as? UiState.Success)?.data
  val pendingAdminPaymentsCount =
    dashboardData?.pagosAdmin?.count { it.estado == "pendiente_revision" } ?: 0

  if (selectedSection != DashboardSection.RESIDENT) {
    BackHandler { viewModel.setSection(DashboardSection.RESIDENT) }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        TopAppBar(
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
              titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
          title = {
            Column {
              Text(
                text = dashboardData?.activeCondo?.nombre ?: "CondoZulia",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = selectedSection.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          },
          actions = {
            CurrencyTogglePill(
              selected = currencyDisplay,
              onSelect = viewModel::setCurrency,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box {
              IconButton(
                onClick = { showCondoMenu = true },
                modifier = Modifier.testTag("condo_menu_button"),
              ) {
                Icon(
                  Icons.Filled.AddBusiness,
                  contentDescription = "Menú de condominios y cuenta",
                )
              }
              DropdownMenu(
                expanded = showCondoMenu,
                onDismissRequest = { showCondoMenu = false },
              ) {
                dashboardData?.condominios?.forEach { cond ->
                  DropdownMenuItem(
                    text = { Text(cond.nombre) },
                    onClick = {
                      showCondoMenu = false
                      viewModel.selectCondominio(cond.id)
                    },
                  )
                }
                DropdownMenuItem(
                  text = { Text("+ Crear nuevo condominio") },
                  onClick = {
                    showCondoMenu = false
                    showCreateDialog = true
                  },
                )
                DropdownMenuItem(
                  text = { Text("Unirse con código") },
                  onClick = {
                    showCondoMenu = false
                    showJoinDialog = true
                  },
                )
                DropdownMenuItem(
                  text = { Text("Cerrar sesión") },
                  leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                  },
                  onClick = {
                    showCondoMenu = false
                    signOutUser(credentialManager, scope)
                  },
                )
              }
            }
          },
        )
      },
      bottomBar = {
        if (!useNavigationRail) {
          NavigationBar {
            DashboardSection.entries.forEach { section ->
              val selected = selectedSection == section
              NavigationBarItem(
                selected = selected,
                onClick = { viewModel.setSection(section) },
                icon = {
                  SectionNavIcon(
                    section = section,
                    selected = selected,
                    badgeCount =
                      if (section == DashboardSection.ADMIN) pendingAdminPaymentsCount else 0,
                  )
                },
                label = { Text(section.label) },
                modifier = Modifier.testTag("nav_${section.key}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavigationRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            Spacer(modifier = Modifier.weight(0.2f))
            DashboardSection.entries.forEach { section ->
              val selected = selectedSection == section
              NavigationRailItem(
                selected = selected,
                onClick = { viewModel.setSection(section) },
                icon = {
                  SectionNavIcon(
                    section = section,
                    selected = selected,
                    badgeCount =
                      if (section == DashboardSection.ADMIN) pendingAdminPaymentsCount else 0,
                  )
                },
                label = { Text(section.label) },
                modifier = Modifier.testTag("rail_${section.key}"),
              )
            }
            Spacer(modifier = Modifier.weight(1f))
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          when (val state = uiState) {
            UiState.Loading -> {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
              }
            }

            is UiState.Error -> {
              Box(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                contentAlignment = Alignment.Center,
              ) {
                EmptyStateCard(
                  icon = Icons.Filled.AccountBalance,
                  title = "Error al cargar datos",
                  subtitle = state.message,
                )
              }
            }

            is UiState.Success -> {
              val data = state.data
              val condo = data.activeCondo
              when (selectedSection) {
                DashboardSection.RESIDENT ->
                  ResidentDashboard(
                    data = data,
                    currencyDisplay = currencyDisplay,
                    billingPeriod = billingPeriod,
                    isBusy = isBusy,
                    onSelectVecino = viewModel::selectVecino,
                    onJoinCondominio = viewModel::unirseACondominio,
                    onOpenAdminSetup = { viewModel.setSection(DashboardSection.ADMIN) },
                    onReportPayment = {
                      vecino,
                      periodo,
                      metodo,
                      banco,
                      ref,
                      tel,
                      moneda,
                      monto,
                      fecha,
                      base64 ->
                      if (condo != null) {
                        viewModel.reportarPago(
                          condominio = condo,
                          vecino = vecino,
                          periodoMes = periodo,
                          metodoPago = metodo,
                          bancoOrigen = banco,
                          numeroReferencia = ref,
                          telefonoEmisor = tel,
                          moneda = moneda,
                          montoReportado = monto,
                          fechaPago = fecha,
                          comprobanteBase64 = base64,
                        )
                      }
                    },
                    onMessage = viewModel::showMessage,
                  )

                DashboardSection.ADMIN ->
                  AdminDashboard(
                    data = data,
                    currencyDisplay = currencyDisplay,
                    billingPeriod = billingPeriod,
                    isBusy = isBusy,
                    onChangePeriod = viewModel::setBillingPeriod,
                    onCreateCondominio = viewModel::crearCondominio,
                    onJoinCondominio = viewModel::unirseACondominio,
                    onUpdateConfig = viewModel::actualizarTasaYCuentas,
                    onRotateCondoCode = {
                      if (condo != null) viewModel.rotarCodigoAccesoCondominio(condo)
                    },
                    onGenerateVecinoAccessCode = { vecino ->
                      if (condo != null) viewModel.generarCodigoAccesoResidente(condo.id, vecino)
                    },
                    onAddGasto = { concepto, categoria, montoUsd ->
                      if (condo != null) {
                        viewModel.registrarGastoMensual(condo, concepto, categoria, montoUsd)
                      }
                    },
                    onUpdateGasto = { gastoId, periodo, concepto, categoria, montoUsd ->
                      if (condo != null) {
                        viewModel.actualizarGastoMensual(
                          condo,
                          gastoId,
                          periodo,
                          concepto,
                          categoria,
                          montoUsd,
                        )
                      }
                    },
                    onDeleteGasto = { gastoId ->
                      if (condo != null) viewModel.eliminarGastoMensual(condo.id, gastoId)
                    },
                    onDistribuirAlicuotas = { vecinos, gastosPendientes ->
                      if (condo != null) {
                        viewModel.distribuirGastosPorAlicuota(condo, vecinos, gastosPendientes)
                      }
                    },
                    onConciliarPago = { pago, vecino, aprobar, nota ->
                      if (condo != null) {
                        viewModel.conciliarPago(condo, pago, vecino, aprobar, nota)
                      }
                    },
                    onAddVecino = { unidad, nombre, tel, alicuota ->
                      if (condo != null) {
                        viewModel.registrarVecino(condo, unidad, nombre, tel, alicuota)
                      }
                    },
                    onUpdateVecino = { vecinoId, unidad, nombre, tel, alicuota ->
                      if (condo != null) {
                        viewModel.actualizarVecino(condo.id, vecinoId, unidad, nombre, tel, alicuota)
                      }
                    },
                    onDeleteVecino = { vecinoId ->
                      if (condo != null) viewModel.eliminarVecino(condo.id, vecinoId)
                    },
                    onMessage = viewModel::showMessage,
                  )
              }
            }
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CrearCondominioDialog(
      onDismiss = { showCreateDialog = false },
      onConfirm = { nombre, dir, tasa, pmB, pmT, pmR, trB, trC, plantilla ->
        showCreateDialog = false
        viewModel.crearCondominio(nombre, dir, tasa, pmB, pmT, pmR, trB, trC, plantilla)
      },
    )
  }

  if (showJoinDialog) {
    JoinCondominioDialog(
      onDismiss = { showJoinDialog = false },
      onConfirm = { condId, code, vecId ->
        showJoinDialog = false
        viewModel.unirseACondominio(condId, code, vecId)
      },
    )
  }
}

@Composable
private fun SectionNavIcon(section: DashboardSection, selected: Boolean, badgeCount: Int) {
  val icon =
    when (section) {
      DashboardSection.RESIDENT -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
      DashboardSection.ADMIN ->
        if (selected) Icons.Filled.AccountBalance else Icons.Outlined.AccountBalance
    }
  if (badgeCount > 0) {
    BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) {
      Icon(icon, contentDescription = section.label)
    }
  } else {
    Icon(icon, contentDescription = section.label)
  }
}
