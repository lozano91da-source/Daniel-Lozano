package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.Condominio
import com.example.model.GastoMensual
import com.example.model.PagoReportado
import com.example.model.Vecino
import com.example.ui.CondoDashboardUiData
import com.example.ui.CurrencyDisplay
import com.example.ui.components.Base64ReceiptThumbnail
import com.example.ui.components.CondoAccessQrDialog
import com.example.ui.components.CondoMoneyText
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ResidentAccessQrDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatBs
import com.example.ui.components.formatUsd
import com.example.ui.theme.MoraRed
import com.example.ui.theme.SolventeGreen
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminDashboard(
  data: CondoDashboardUiData,
  currencyDisplay: CurrencyDisplay,
  billingPeriod: String,
  isBusy: Boolean,
  onChangePeriod: (String) -> Unit,
  onCreateCondominio: (
    nombre: String,
    direccion: String,
    tasaBcv: Double,
    bancoPagoMovil: String,
    telefonoPagoMovil: String,
    rifPagoMovil: String,
    bancoTransferencia: String,
    cuentaTransferencia: String,
    incluirPlantillaMaracaibo: Boolean,
  ) -> Unit,
  onJoinCondominio: (condominioId: String, codigoAcceso: String, vecinoId: String?) -> Unit,
  onUpdateConfig: (
    condominioId: String,
    tasaBcv: Double,
    bancoPagoMovil: String,
    telefonoPagoMovil: String,
    rifPagoMovil: String,
    bancoTransferencia: String,
    cuentaTransferencia: String,
  ) -> Unit,
  onRotateCondoCode: () -> Unit,
  onGenerateVecinoAccessCode: (Vecino) -> Unit,
  onAddGasto: (concepto: String, categoria: String, montoUsd: Double) -> Unit,
  onUpdateGasto: (gastoId: String, periodoMes: String, concepto: String, categoria: String, montoUsd: Double) -> Unit,
  onDeleteGasto: (gastoId: String) -> Unit,
  onDistribuirAlicuotas: (List<Vecino>, List<GastoMensual>) -> Unit,
  onConciliarPago: (PagoReportado, Vecino, Boolean, String) -> Unit,
  onAddVecino: (unidad: String, nombreCompleto: String, telefono: String, alicuotaPct: Double) -> Unit,
  onUpdateVecino: (vecinoId: String, unidad: String, nombreCompleto: String, telefono: String, alicuotaPct: Double) -> Unit,
  onDeleteVecino: (vecinoId: String) -> Unit,
  onMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showCreateCondoDialog by remember { mutableStateOf(false) }
  var showJoinDialog by remember { mutableStateOf(false) }

  val condo = data.activeCondo
  if (condo == null) {
    Box(
      modifier = modifier.fillMaxSize().padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        EmptyStateCard(
          icon = Icons.Filled.AccountBalance,
          title = "Configura tu Junta de Condominio",
          subtitle =
            "Crea un conjunto residencial en Maracaibo para cargar gastos mensuales, prorratear alícuotas en USD/Bs. y conciliar pagos.",
          actionLabel = "Crear Condominio",
          onAction = { showCreateCondoDialog = true },
        )
        OutlinedButton(
          onClick = { showJoinDialog = true },
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_join_condo_button"),
        ) {
          Text("Unirse a condominio existente")
        }
      }
    }

    if (showCreateCondoDialog) {
      CrearCondominioDialog(
        onDismiss = { showCreateCondoDialog = false },
        onConfirm = { nombre, dir, tasa, pmB, pmT, pmR, trB, trC, template ->
          showCreateCondoDialog = false
          onCreateCondominio(nombre, dir, tasa, pmB, pmT, pmR, trB, trC, template)
        },
      )
    }
    if (showJoinDialog) {
      JoinCondominioDialog(
        onDismiss = { showJoinDialog = false },
        onConfirm = { condId, code, vecId ->
          showJoinDialog = false
          onJoinCondominio(condId, code, vecId)
        },
      )
    }
    return
  }

  val isAdmin = data.isAdmin
  var subTab by remember { mutableIntStateOf(0) }
  var showConfigDialog by remember { mutableStateOf(false) }
  var showCondoQrDialog by remember { mutableStateOf(false) }
  var qrVecinoTargetId by remember { mutableStateOf<String?>(null) }
  var showAddGastoDialog by remember { mutableStateOf(false) }
  var editingGasto by remember { mutableStateOf<GastoMensual?>(null) }
  var showAddVecinoDialog by remember { mutableStateOf(false) }
  var editingVecino by remember { mutableStateOf<Vecino?>(null) }

  val qrVecinoTarget =
    remember(qrVecinoTargetId, data.vecinos) {
      qrVecinoTargetId?.let { id -> data.vecinos.firstOrNull { it.id == id } }
    }

  val periodGastos =
    remember(data.gastos, billingPeriod) {
      data.gastos.filter { it.periodoMes == billingPeriod }
    }
  val gastosPendientes =
    remember(periodGastos) { periodGastos.filter { !it.distribuido } }
  val totalGastosUsd = remember(periodGastos) { periodGastos.sumOf { it.montoUsd } }
  val totalPorCobrarUsd = remember(data.vecinos) { data.vecinos.sumOf { it.saldoPendienteUsd } }
  val pendingPagosCount =
    remember(data.pagosAdmin) { data.pagosAdmin.count { it.estado == "pendiente_revision" } }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Admin Hero Card
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .background(
                    Brush.horizontalGradient(
                      listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                      )
                    )
                  )
                  .padding(18.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "JUNTA DE CONDOMINIO",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
                    Text(
                      text = condo.nombre,
                      style = MaterialTheme.typography.headlineSmall,
                      color = MaterialTheme.colorScheme.onPrimary,
                    )
                  }
                  Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(10.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                      Text(
                        text = "BCV ${String.format(Locale.US, "%.2f", condo.tasaBcv)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondary,
                      )
                      if (isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                          onClick = { showConfigDialog = true },
                          modifier = Modifier.size(24.dp).testTag("admin_edit_bcv_button"),
                        ) {
                          Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar tasa BCV y cuentas",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(16.dp),
                          )
                        }
                      }
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "ID: ${condo.id} · Código: ${condo.codigoAcceso}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                    modifier = Modifier.weight(1f),
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                      onClick = { showCondoQrDialog = true },
                      colors =
                        ButtonDefaults.textButtonColors(
                          contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                      modifier = Modifier.testTag("admin_condo_qr_button"),
                    ) {
                      Icon(
                        Icons.Filled.QrCode2,
                        contentDescription = "Ver código QR del condominio",
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("QR")
                    }
                    TextButton(
                      onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val invite = "ID: ${condo.id} | Código: ${condo.codigoAcceso}"
                        cb.setPrimaryClip(ClipData.newPlainText("Invitación CondoZulia", invite))
                        onMessage("ID y código de acceso copiados para vecinos.")
                      },
                      colors =
                        ButtonDefaults.textButtonColors(
                          contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                    ) {
                      Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copiar invitación",
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Copiar")
                    }
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Gastos ($billingPeriod)",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CondoMoneyText(
                  amountUsd = totalGastosUsd,
                  amountBs = totalGastosUsd * condo.tasaBcv,
                  currencyDisplay = currencyDisplay,
                  primaryColor = MaterialTheme.colorScheme.primary,
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Saldo por cobrar",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CondoMoneyText(
                  amountUsd = totalPorCobrarUsd,
                  amountBs = totalPorCobrarUsd * condo.tasaBcv,
                  currencyDisplay = currencyDisplay,
                  primaryColor = if (totalPorCobrarUsd > 0.0) MoraRed else SolventeGreen,
                )
              }
            }

            FlowRow(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf("2026-10", "2026-11", "2026-12").forEach { period ->
                FilterChip(
                  selected = billingPeriod == period,
                  onClick = { onChangePeriod(period) },
                  label = { Text(period) },
                )
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
          }
        }
      }

      if (!isAdmin) {
        item {
          Card(
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(16.dp),
          ) {
            Text(
              text =
                "Vista de solo lectura: únicamente el administrador de la Junta puede cargar gastos, editar vecinos o conciliar pagos.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      }

      item {
        TabRow(
          selectedTabIndex = subTab,
          containerColor = MaterialTheme.colorScheme.surface,
          modifier = Modifier.clip(RoundedCornerShape(14.dp)),
        ) {
          Tab(
            selected = subTab == 0,
            onClick = { subTab = 0 },
            text = { Text("Gastos (${periodGastos.size})") },
            modifier = Modifier.testTag("admin_tab_gastos"),
          )
          Tab(
            selected = subTab == 1,
            onClick = { subTab = 1 },
            text = {
              Text(if (pendingPagosCount > 0) "Pagos ($pendingPagosCount)" else "Pagos")
            },
            modifier = Modifier.testTag("admin_tab_pagos"),
          )
          Tab(
            selected = subTab == 2,
            onClick = { subTab = 2 },
            text = { Text("Vecinos (${data.vecinos.size})") },
            modifier = Modifier.testTag("admin_tab_vecinos"),
          )
        }
      }

      when (subTab) {
        0 -> {
          if (isAdmin) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedButton(
                  onClick = { showAddGastoDialog = true },
                  modifier =
                    Modifier.weight(1f).height(48.dp).testTag("admin_add_expense_button"),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Cargar Gasto")
                }
                Button(
                  onClick = { onDistribuirAlicuotas(data.vecinos, gastosPendientes) },
                  enabled = !isBusy && gastosPendientes.isNotEmpty() && data.vecinos.isNotEmpty(),
                  modifier =
                    Modifier.weight(1f).height(48.dp).testTag("admin_distribute_aliquots_button"),
                ) {
                  Icon(Icons.Filled.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Dividir Alícuotas")
                }
              }
            }
          }

          if (periodGastos.isEmpty()) {
            item {
              EmptyStateCard(
                icon = Icons.Filled.Receipt,
                title = "Sin gastos cargados en $billingPeriod",
                subtitle =
                  "Registra gastos de vigilancia, agua, ascensores o electricidad para dividirlos automáticamente según la alícuota de cada vecino.",
                actionLabel = if (isAdmin) "Cargar primer gasto" else null,
                onAction = if (isAdmin) ({ showAddGastoDialog = true }) else null,
              )
            }
          } else {
            items(periodGastos, key = { it.id }) { gasto ->
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp),
                      ) {
                        Text(
                          text = gasto.categoria,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                      }
                      if (gasto.distribuido) {
                        Surface(
                          color = SolventeGreen.copy(alpha = 0.15f),
                          shape = RoundedCornerShape(6.dp),
                        ) {
                          Text(
                            text = "Distribuido",
                            style = MaterialTheme.typography.labelSmall,
                            color = SolventeGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                          )
                        }
                      }
                    }
                    Text(text = gasto.concepto, style = MaterialTheme.typography.titleMedium)
                  }
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    CondoMoneyText(
                      amountUsd = gasto.montoUsd,
                      amountBs = gasto.montoBs,
                      currencyDisplay = currencyDisplay,
                    )
                    if (isAdmin) {
                      IconButton(onClick = { editingGasto = gasto }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar gasto")
                      }
                      IconButton(onClick = { onDeleteGasto(gasto.id) }) {
                        Icon(
                          Icons.Filled.DeleteOutline,
                          contentDescription = "Eliminar gasto",
                          tint = MaterialTheme.colorScheme.error,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        1 -> {
          if (data.pagosAdmin.isEmpty()) {
            item {
              EmptyStateCard(
                icon = Icons.Filled.AccountBalance,
                title = "Sin pagos reportados",
                subtitle = "Los reportes de Pago Móvil y transferencias enviados por vecinos aparecerán aquí.",
              )
            }
          } else {
            items(data.pagosAdmin, key = { it.id }) { pago ->
              val vecino = data.vecinos.firstOrNull { it.id == pago.vecinoId }
              AdminPagoCard(
                pago = pago,
                isAdmin = isAdmin && vecino != null,
                isBusy = isBusy,
                onApprove = {
                  if (vecino != null) onConciliarPago(pago, vecino, true, "Conciliado por Junta")
                },
                onReject = {
                  if (vecino != null) onConciliarPago(pago, vecino, false, "Referencia no encontrada")
                },
              )
            }
          }
        }

        2 -> {
          if (isAdmin) {
            item {
              OutlinedButton(
                onClick = { showAddVecinoDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_add_vecino_button"),
              ) {
                Icon(Icons.Filled.Apartment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Registrar Apartamento / Casa")
              }
            }
          }

          if (data.vecinos.isEmpty()) {
            item {
              EmptyStateCard(
                icon = Icons.Filled.Apartment,
                title = "No hay vecinos registrados",
                subtitle = "Agrega los apartamentos o casas del condominio con su porcentaje de alícuota.",
                actionLabel = if (isAdmin) "Registrar Unidad" else null,
                onAction = if (isAdmin) ({ showAddVecinoDialog = true }) else null,
              )
            }
          } else {
            items(data.vecinos, key = { it.id }) { vecino ->
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(text = vecino.unidad, style = MaterialTheme.typography.titleMedium)
                      Text(
                        text = "${vecino.nombreCompleto} · Alícuota ${String.format(Locale.US, "%.2f", vecino.alicuotaPct)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      if (vecino.codigoAcceso.isNotBlank()) {
                        Text(
                          text = "Código QR: ${vecino.codigoAcceso}",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.primary,
                        )
                      }
                    }
                    StatusBadge(status = vecino.estadoSolvencia)
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    CondoMoneyText(
                      amountUsd = vecino.saldoPendienteUsd,
                      amountBs = vecino.saldoPendienteBs,
                      currencyDisplay = currencyDisplay,
                    )
                    if (isAdmin) {
                      Row {
                        IconButton(
                          onClick = { qrVecinoTargetId = vecino.id },
                          modifier = Modifier.testTag("admin_vecino_qr_${vecino.id}"),
                        ) {
                          Icon(
                            Icons.Filled.QrCode2,
                            contentDescription = "Generar código QR para ${vecino.unidad}",
                            tint = MaterialTheme.colorScheme.primary,
                          )
                        }
                        IconButton(onClick = { editingVecino = vecino }) {
                          Icon(Icons.Filled.Edit, contentDescription = "Editar vecino")
                        }
                        IconButton(onClick = { onDeleteVecino(vecino.id) }) {
                          Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Eliminar vecino",
                            tint = MaterialTheme.colorScheme.error,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showConfigDialog) {
    CondoConfigDialog(
      condominio = condo,
      onDismiss = { showConfigDialog = false },
      onSave = { tasa, pmB, pmT, pmR, trB, trC ->
        showConfigDialog = false
        onUpdateConfig(condo.id, tasa, pmB, pmT, pmR, trB, trC)
      },
    )
  }

  if (showCondoQrDialog) {
    CondoAccessQrDialog(
      condominio = condo,
      isAdmin = isAdmin,
      isBusy = isBusy,
      onRotateCondoCode = onRotateCondoCode,
      onMessage = onMessage,
      onDismiss = { showCondoQrDialog = false },
    )
  }

  qrVecinoTarget?.let { vecino ->
    ResidentAccessQrDialog(
      condominio = condo,
      vecino = vecino,
      isBusy = isBusy,
      onGenerateNewCode = { onGenerateVecinoAccessCode(vecino) },
      onMessage = onMessage,
      onDismiss = { qrVecinoTargetId = null },
    )
  }

  if (showAddGastoDialog || editingGasto != null) {
    GastoFormDialog(
      tasaBcv = condo.tasaBcv,
      initialGasto = editingGasto,
      onDismiss = {
        showAddGastoDialog = false
        editingGasto = null
      },
      onConfirm = { concepto, categoria, montoUsd ->
        val currentEdit = editingGasto
        showAddGastoDialog = false
        editingGasto = null
        if (currentEdit == null) {
          onAddGasto(concepto, categoria, montoUsd)
        } else {
          onUpdateGasto(currentEdit.id, currentEdit.periodoMes, concepto, categoria, montoUsd)
        }
      },
    )
  }

  if (showAddVecinoDialog || editingVecino != null) {
    VecinoFormDialog(
      initialVecino = editingVecino,
      onDismiss = {
        showAddVecinoDialog = false
        editingVecino = null
      },
      onConfirm = { unidad, nombre, tel, alicuota ->
        val currentEdit = editingVecino
        showAddVecinoDialog = false
        editingVecino = null
        if (currentEdit == null) {
          onAddVecino(unidad, nombre, tel, alicuota)
        } else {
          onUpdateVecino(currentEdit.id, unidad, nombre, tel, alicuota)
        }
      },
    )
  }
}

@Composable
private fun AdminPagoCard(
  pago: PagoReportado,
  isAdmin: Boolean,
  isBusy: Boolean,
  onApprove: () -> Unit,
  onReject: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "${pago.unidad} · ${pago.periodoMes}", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "${pago.metodoPago.replace('_', ' ').uppercase(Locale.US)} · ${pago.bancoOrigen}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        StatusBadge(status = pago.estado)
      }
      val montoStr =
        if (pago.moneda == "VES") "${formatBs(pago.montoReportado)} (≈ ${formatUsd(pago.montoEquivalenteUsd)})"
        else formatUsd(pago.montoReportado)
      Text(text = "Monto: $montoStr", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
      Text(
        text = "Ref: ${pago.numeroReferencia} · Tel: ${pago.telefonoEmisor.ifBlank { "N/A" }} · ${pago.fechaPago}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (pago.comprobanteBase64.isNotBlank()) {
        Base64ReceiptThumbnail(base64 = pago.comprobanteBase64)
      }
      if (isAdmin && pago.estado == "pendiente_revision") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onReject,
            enabled = !isBusy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MoraRed),
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Rechazar")
          }
          Button(
            onClick = onApprove,
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = SolventeGreen),
            modifier = Modifier.weight(1f).height(48.dp).testTag("admin_approve_payment_button"),
          ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Conciliar")
          }
        }
      } else if (pago.estado == "aprobado") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Verified, contentDescription = null, tint = SolventeGreen, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = pago.notaConciliacion.ifBlank { "Conciliado" },
            style = MaterialTheme.typography.bodyMedium,
            color = SolventeGreen,
          )
        }
      }
    }
  }
}

@Composable
fun CrearCondominioDialog(
  onDismiss: () -> Unit,
  onConfirm: (String, String, Double, String, String, String, String, String, Boolean) -> Unit,
) {
  var nombre by remember { mutableStateOf("") }
  var direccion by remember { mutableStateOf("Av. El Milagro, Maracaibo") }
  var tasaText by remember { mutableStateOf("46.50") }
  var pmBanco by remember { mutableStateOf("Banesco") }
  var pmTelefono by remember { mutableStateOf("0414-6000000") }
  var pmRif by remember { mutableStateOf("J-30123456-7") }
  var trBanco by remember { mutableStateOf("Mercantil") }
  var trCuenta by remember { mutableStateOf("0105-0011-22-0000000000") }
  var incluirPlantilla by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nuevo Condominio") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = nombre,
          onValueChange = { nombre = it },
          label = { Text("Nombre (ej. Res. Lago Sol)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_condo_nombre_input"),
        )
        OutlinedTextField(
          value = direccion,
          onValueChange = { direccion = it },
          label = { Text("Dirección / Sector") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = tasaText,
          onValueChange = { tasaText = it },
          label = { Text("Tasa BCV (Bs./USD)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = pmBanco,
          onValueChange = { pmBanco = it },
          label = { Text("Banco Pago Móvil") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = pmTelefono,
          onValueChange = { pmTelefono = it },
          label = { Text("Teléfono Pago Móvil") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = pmRif,
          onValueChange = { pmRif = it },
          label = { Text("RIF / Cédula") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Precargar 6 apartamentos de ejemplo",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
          Switch(checked = incluirPlantilla, onCheckedChange = { incluirPlantilla = it })
        }
      }
    },
    confirmButton = {
      val tasa = tasaText.replace(",", ".").toDoubleOrNull() ?: 0.0
      Button(
        onClick = {
          onConfirm(nombre, direccion, tasa, pmBanco, pmTelefono, pmRif, trBanco, trCuenta, incluirPlantilla)
        },
        enabled = nombre.trim().length >= 2 && tasa > 0.0,
        modifier = Modifier.testTag("confirm_create_condo_button"),
      ) {
        Text("Crear")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}

@Composable
private fun CondoConfigDialog(
  condominio: Condominio,
  onDismiss: () -> Unit,
  onSave: (Double, String, String, String, String, String) -> Unit,
) {
  var tasaText by remember { mutableStateOf(condominio.tasaBcv.toString()) }
  var pmBanco by remember { mutableStateOf(condominio.bancoPagoMovil) }
  var pmTel by remember { mutableStateOf(condominio.telefonoPagoMovil) }
  var pmRif by remember { mutableStateOf(condominio.rifPagoMovil) }
  var trBanco by remember { mutableStateOf(condominio.bancoTransferencia) }
  var trCuenta by remember { mutableStateOf(condominio.cuentaTransferencia) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Tasa BCV y Cuentas Bancarias") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = tasaText,
          onValueChange = { tasaText = it },
          label = { Text("Tasa BCV (Bs./USD)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = pmBanco,
          onValueChange = { pmBanco = it },
          label = { Text("Banco Pago Móvil") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = pmTel,
          onValueChange = { pmTel = it },
          label = { Text("Teléfono Pago Móvil") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = pmRif,
          onValueChange = { pmRif = it },
          label = { Text("RIF / Cédula") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = trBanco,
          onValueChange = { trBanco = it },
          label = { Text("Banco Transferencia") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = trCuenta,
          onValueChange = { trCuenta = it },
          label = { Text("Cuenta Bancaria") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      val tasa = tasaText.replace(",", ".").toDoubleOrNull() ?: condominio.tasaBcv
      Button(
        onClick = { onSave(tasa, pmBanco, pmTel, pmRif, trBanco, trCuenta) },
        enabled = tasa > 0.0,
      ) {
        Text("Guardar")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GastoFormDialog(
  tasaBcv: Double,
  initialGasto: GastoMensual?,
  onDismiss: () -> Unit,
  onConfirm: (concepto: String, categoria: String, montoUsd: Double) -> Unit,
) {
  var concepto by remember { mutableStateOf(initialGasto?.concepto.orEmpty()) }
  var categoria by remember { mutableStateOf(initialGasto?.categoria?.ifBlank { "Vigilancia" } ?: "Vigilancia") }
  var montoText by remember {
    mutableStateOf(
      if (initialGasto != null) String.format(Locale.US, "%.2f", initialGasto.montoUsd) else ""
    )
  }
  var enBs by remember { mutableStateOf(false) }

  val categorias = listOf("Vigilancia", "Hidroneumático", "Electricidad", "Mantenimiento", "Ascensores", "Aseo")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initialGasto == null) "Cargar Gasto Mensual" else "Editar Gasto") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = concepto,
          onValueChange = { concepto = it },
          label = { Text("Concepto") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("admin_expense_concept_input"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          categorias.forEach { cat ->
            FilterChip(selected = categoria == cat, onClick = { categoria = cat }, label = { Text(cat) })
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = !enBs, onClick = { enBs = false }, label = { Text("USD $") })
          FilterChip(selected = enBs, onClick = { enBs = true }, label = { Text("Bolívares Bs.") })
        }
        OutlinedTextField(
          value = montoText,
          onValueChange = { montoText = it },
          label = { Text(if (enBs) "Monto (Bs.)" else "Monto (USD)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("admin_expense_amount_input"),
        )
        val parsed = montoText.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (parsed > 0.0) {
          val usd = if (enBs) parsed / tasaBcv.coerceAtLeast(1.0) else parsed
          Text(
            text = "Equivalente: ${formatUsd(usd)} · ${formatBs(usd * tasaBcv)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    },
    confirmButton = {
      val parsed = montoText.replace(",", ".").toDoubleOrNull() ?: 0.0
      val finalUsd = if (enBs) parsed / tasaBcv.coerceAtLeast(1.0) else parsed
      Button(
        onClick = { onConfirm(concepto, categoria, finalUsd) },
        enabled = concepto.trim().length >= 2 && finalUsd > 0.0,
        modifier = Modifier.testTag("confirm_admin_expense_button"),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}

@Composable
private fun VecinoFormDialog(
  initialVecino: Vecino?,
  onDismiss: () -> Unit,
  onConfirm: (unidad: String, nombreCompleto: String, telefono: String, alicuotaPct: Double) -> Unit,
) {
  var unidad by remember { mutableStateOf(initialVecino?.unidad.orEmpty()) }
  var nombre by remember { mutableStateOf(initialVecino?.nombreCompleto.orEmpty()) }
  var telefono by remember { mutableStateOf(initialVecino?.telefono.orEmpty()) }
  var alicuotaText by remember {
    mutableStateOf(
      if (initialVecino != null) String.format(Locale.US, "%.2f", initialVecino.alicuotaPct)
      else "10.0"
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initialVecino == null) "Registrar Apartamento / Casa" else "Editar Unidad") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = unidad,
          onValueChange = { unidad = it },
          label = { Text("Unidad (ej. Torre A - 4B)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("admin_vecino_unidad_input"),
        )
        OutlinedTextField(
          value = nombre,
          onValueChange = { nombre = it },
          label = { Text("Propietario / Familia") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = telefono,
          onValueChange = { telefono = it },
          label = { Text("Teléfono (Opcional)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = alicuotaText,
          onValueChange = { alicuotaText = it },
          label = { Text("Alícuota % (ej. 12.5)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      val alicuota = alicuotaText.replace(",", ".").toDoubleOrNull() ?: 0.0
      Button(
        onClick = { onConfirm(unidad, nombre.ifBlank { "Propietario" }, telefono, alicuota) },
        enabled = unidad.isNotBlank() && alicuota > 0.0 && alicuota <= 100.0,
        modifier = Modifier.testTag("confirm_admin_vecino_button"),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}
