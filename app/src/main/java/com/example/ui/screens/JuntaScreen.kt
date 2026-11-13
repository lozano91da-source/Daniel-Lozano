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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
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
import com.example.model.CondoUnit
import com.example.model.Condominium
import com.example.model.MonthlyExpense
import com.example.model.PaymentReport
import com.example.model.UnitInvoice
import com.example.ui.CondoWorkspaceData
import com.example.ui.CurrencyDisplay
import com.example.ui.components.Base64ReceiptThumbnail
import com.example.ui.components.CondoMoneyText
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatBs
import com.example.ui.components.formatUsd
import com.example.ui.theme.MoraRed
import com.example.ui.theme.SolventeGreen
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JuntaScreen(
  data: CondoWorkspaceData,
  currentUserId: String,
  currencyDisplay: CurrencyDisplay,
  billingPeriod: String,
  isBusy: Boolean,
  onChangePeriod: (String) -> Unit,
  onAddExpense: (title: String, category: String, amountUsd: Double) -> Unit,
  onDeleteExpense: (expenseId: String) -> Unit,
  onGenerateInvoices: (List<CondoUnit>, List<MonthlyExpense>, List<UnitInvoice>) -> Unit,
  onAddUnit: (unitNumber: String, ownerLabel: String, alicuotaPct: Double) -> Unit,
  onReconcilePayment: (PaymentReport, Boolean, String) -> Unit,
  onMarkOverdue: (String) -> Unit,
  onSendReminder: (UnitInvoice) -> Unit,
  onUpdateSettings: (Double, String, String, String, String, String) -> Unit,
  onMessage: (String) -> Unit,
) {
  val condo = data.activeCondo ?: return
  val isAdmin = condo.adminUid == currentUserId
  val context = LocalContext.current

  var subTab by remember { mutableIntStateOf(0) }
  var showAddExpenseDialog by remember { mutableStateOf(false) }
  var showAddUnitDialog by remember { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }

  val periodExpenses =
    remember(data.expenses, billingPeriod) {
      data.expenses.filter { it.billingPeriod == billingPeriod }
    }
  val periodInvoices =
    remember(data.invoices, billingPeriod) {
      data.invoices.filter { it.billingPeriod == billingPeriod }
    }
  val totalExpensesUsd = remember(periodExpenses) { periodExpenses.sumOf { it.amountUsd } }
  val totalExpensesBs = totalExpensesUsd * condo.bcvRate
  val collectedUsd =
    remember(periodInvoices) { periodInvoices.filter { it.status == "paid" }.sumOf { it.amountDueUsd } }
  val pendingPaymentsCount =
    remember(data.adminPayments) { data.adminPayments.count { it.status == "pending_review" } }
  val pendingInvoices =
    remember(periodInvoices) {
      periodInvoices.filter { it.status == "pending" || it.status == "overdue" }
    }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 820.dp)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Hero Financial Dashboard Card
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
                        text = condo.name,
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
                          text = "BCV ${String.format(Locale.US, "%.2f", condo.bcvRate)}",
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.onSecondary,
                        )
                        if (isAdmin) {
                          Spacer(modifier = Modifier.width(4.dp))
                          IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.size(20.dp).testTag("edit_bcv_button"),
                          ) {
                            Icon(
                              Icons.Filled.Edit,
                              contentDescription = "Editar tasa BCV y cuentas",
                              tint = MaterialTheme.colorScheme.onSecondary,
                              modifier = Modifier.size(14.dp),
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
                      text = "Código Vecinos: ${condo.joinCode}",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    )
                    TextButton(
                      onClick = {
                        val cb =
                          context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Código", condo.joinCode))
                        onMessage("Código ${condo.joinCode} copiado para compartir con vecinos.")
                      },
                      colors =
                        ButtonDefaults.textButtonColors(
                          contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                    ) {
                      Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar código", modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Copiar")
                    }
                  }
                }
              }

              // Metrics Row
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
                    amountUsd = totalExpensesUsd,
                    amountBs = totalExpensesBs,
                    currencyDisplay = currencyDisplay,
                    primaryColor = MaterialTheme.colorScheme.primary,
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Recaudado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  CondoMoneyText(
                    amountUsd = collectedUsd,
                    amountBs = collectedUsd * condo.bcvRate,
                    currencyDisplay = currencyDisplay,
                    primaryColor = SolventeGreen,
                  )
                }
              }

              // Period chips
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
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
              shape = RoundedCornerShape(16.dp),
            ) {
              Text(
                text =
                  "Modo lectura: Solo el administrador que creó el condominio puede modificar gastos o conciliar pagos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(14.dp),
              )
            }
          }
        }

        // Sub-tabs
        item {
          TabRow(
            selectedTabIndex = subTab,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)),
          ) {
            Tab(
              selected = subTab == 0,
              onClick = { subTab = 0 },
              text = { Text("Gastos (${periodExpenses.size})") },
              modifier = Modifier.testTag("junta_tab_gastos"),
            )
            Tab(
              selected = subTab == 1,
              onClick = { subTab = 1 },
              text = {
                Text(
                  if (pendingPaymentsCount > 0) "Conciliar ($pendingPaymentsCount)" else "Conciliar"
                )
              },
              modifier = Modifier.testTag("junta_tab_conciliar"),
            )
            Tab(
              selected = subTab == 2,
              onClick = { subTab = 2 },
              text = { Text("Morosos (${pendingInvoices.size})") },
              modifier = Modifier.testTag("junta_tab_morosos"),
            )
          }
        }

        when (subTab) {
          0 -> {
            // Actions row
            if (isAdmin) {
              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  OutlinedButton(
                    onClick = { showAddExpenseDialog = true },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("add_expense_button"),
                  ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cargar Gasto")
                  }
                  Button(
                    onClick = {
                      onGenerateInvoices(data.units, periodExpenses, periodInvoices)
                    },
                    enabled = !isBusy && periodExpenses.isNotEmpty() && data.units.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("split_aliquots_button"),
                  ) {
                    Icon(Icons.Filled.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dividir Alícuotas")
                  }
                }
              }
            }

            if (periodExpenses.isEmpty()) {
              item {
                EmptyStateCard(
                  icon = Icons.Filled.Receipt,
                  title = "Sin gastos cargados en $billingPeriod",
                  subtitle =
                    "Agrega vigilancia, hidroneumático, electricidad o mantenimiento y divide automáticamente entre los vecinos.",
                  actionLabel = if (isAdmin) "Cargar primer gasto" else null,
                  onAction = if (isAdmin) ({ showAddExpenseDialog = true }) else null,
                )
              }
            } else {
              items(periodExpenses, key = { it.id }) { expense ->
                Card(
                  shape = RoundedCornerShape(16.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp),
                      ) {
                        Text(
                          text = expense.category,
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                      }
                      Spacer(modifier = Modifier.height(6.dp))
                      Text(text = expense.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      CondoMoneyText(
                        amountUsd = expense.amountUsd,
                        amountBs = expense.amountUsd * condo.bcvRate,
                        currencyDisplay = currencyDisplay,
                      )
                      if (isAdmin) {
                        IconButton(onClick = { onDeleteExpense(expense.id) }) {
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
            val payments = data.adminPayments
            if (payments.isEmpty()) {
              item {
                EmptyStateCard(
                  icon = Icons.Filled.AccountBalance,
                  title = "Sin pagos por conciliar",
                  subtitle =
                    "Cuando los vecinos reporten su Pago Móvil o transferencia, aparecerán aquí para verificación y aprobación.",
                )
              }
            } else {
              items(payments, key = { it.id }) { payment ->
                PaymentReconcileCard(
                  payment = payment,
                  isAdmin = isAdmin,
                  isBusy = isBusy,
                  onApprove = { onReconcilePayment(payment, true, "Conciliado por la Junta") },
                  onReject = { onReconcilePayment(payment, false, "Referencia no encontrada") },
                )
              }
            }
          }

          2 -> {
            if (isAdmin) {
              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  OutlinedButton(
                    onClick = { showAddUnitDialog = true },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("add_unit_button"),
                  ) {
                    Icon(Icons.Filled.Apartment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nueva Unidad")
                  }
                  Button(
                    onClick = {
                      pendingInvoices.forEach { inv -> onSendReminder(inv) }
                    },
                    enabled = !isBusy && pendingInvoices.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("remind_all_button"),
                  ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Recordar Todos")
                  }
                }
              }
            }

            if (data.units.isEmpty()) {
              item {
                EmptyStateCard(
                  icon = Icons.Filled.Apartment,
                  title = "No hay apartamentos o casas",
                  subtitle = "Registra las unidades del condominio con su porcentaje de alícuota.",
                  actionLabel = if (isAdmin) "Agregar Unidad" else null,
                  onAction = if (isAdmin) ({ showAddUnitDialog = true }) else null,
                )
              }
            } else {
              items(data.units, key = { it.id }) { unit ->
                val invoice = periodInvoices.firstOrNull { it.unitId == unit.id }
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
                      Column {
                        Text(text = unit.unitNumber, style = MaterialTheme.typography.titleMedium)
                        Text(
                          text = "${unit.ownerLabel} · Alícuota ${String.format(Locale.US, "%.2f", unit.alicuotaPct)}%",
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      StatusBadge(status = invoice?.status ?: "pending")
                    }

                    if (invoice != null) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        CondoMoneyText(
                          amountUsd = invoice.amountDueUsd,
                          amountBs = invoice.amountDueBs,
                          currencyDisplay = currencyDisplay,
                        )
                        if (isAdmin && invoice.status != "paid") {
                          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (invoice.status != "overdue") {
                              IconButton(onClick = { onMarkOverdue(invoice.id) }) {
                                Icon(
                                  Icons.Filled.WarningAmber,
                                  contentDescription = "Marcar en mora",
                                  tint = MoraRed,
                                )
                              }
                            }
                            OutlinedButton(
                              onClick = { onSendReminder(invoice) },
                              modifier = Modifier.height(40.dp),
                            ) {
                              Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                              Spacer(modifier = Modifier.width(4.dp))
                              Text("Recordar")
                            }
                          }
                        }
                      }
                    } else {
                      Text(
                        text = "Sin recibo generado para $billingPeriod",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

  if (showAddExpenseDialog) {
    AddExpenseDialog(
      bcvRate = condo.bcvRate,
      onDismiss = { showAddExpenseDialog = false },
      onConfirm = { title, category, amountUsd ->
        showAddExpenseDialog = false
        onAddExpense(title, category, amountUsd)
      },
    )
  }

  if (showAddUnitDialog) {
    AddUnitDialog(
      onDismiss = { showAddUnitDialog = false },
      onConfirm = { num, owner, alicuota ->
        showAddUnitDialog = false
        onAddUnit(num, owner, alicuota)
      },
    )
  }

  if (showSettingsDialog) {
    CondoSettingsDialog(
      condo = condo,
      onDismiss = { showSettingsDialog = false },
      onSave = { rate, pmB, pmP, pmI, trB, trA ->
        showSettingsDialog = false
        onUpdateSettings(rate, pmB, pmP, pmI, trB, trA)
      },
    )
  }
}

@Composable
private fun PaymentReconcileCard(
  payment: PaymentReport,
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
        Column {
          Text(
            text = "${payment.unitNumber} · ${payment.billingPeriod}",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "${payment.method.replace('_', ' ').uppercase(Locale.US)} · ${payment.bankName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        StatusBadge(status = payment.status)
      }

      val amountLabel =
        if (payment.currency == "VES") {
          "${formatBs(payment.amountReported)} (≈ ${formatUsd(payment.amountUsdEquivalent)})"
        } else {
          formatUsd(payment.amountReported)
        }
      Text(
        text = "Monto: $amountLabel",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Ref: ${payment.referenceNumber} · Tel: ${payment.senderPhone.ifBlank { "N/A" }} · Fecha: ${payment.paymentDate}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (payment.receiptPreviewBase64.isNotBlank()) {
        Base64ReceiptThumbnail(base64 = payment.receiptPreviewBase64)
      }

      if (isAdmin && payment.status == "pending_review") {
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
            modifier = Modifier.weight(1f).height(48.dp).testTag("approve_payment_button"),
          ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Conciliar")
          }
        }
      } else if (payment.status == "approved") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Filled.Verified,
            contentDescription = null,
            tint = SolventeGreen,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = payment.reviewNote.ifBlank { "Pago conciliado" },
            style = MaterialTheme.typography.bodyMedium,
            color = SolventeGreen,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseDialog(
  bcvRate: Double,
  onDismiss: () -> Unit,
  onConfirm: (String, String, Double) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Vigilancia") }
  var amountText by remember { mutableStateOf("") }
  var inputInBs by remember { mutableStateOf(false) }

  val categories =
    listOf("Vigilancia", "Hidroneumático", "Electricidad", "Mantenimiento", "Ascensores", "Aseo", "Fondo Reserva")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Cargar Gasto del Mes") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Concepto (ej. Vigilancia privada 24h)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("expense_title_input"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          categories.forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat) },
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = !inputInBs, onClick = { inputInBs = false }, label = { Text("En USD $") })
          FilterChip(selected = inputInBs, onClick = { inputInBs = true }, label = { Text("En Bolívares") })
        }
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text(if (inputInBs) "Monto en Bolívares (Bs.)" else "Monto en Divisas ($)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("expense_amount_input"),
        )
        val parsed = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (parsed > 0) {
          val eqUsd = if (inputInBs) parsed / bcvRate.coerceAtLeast(1.0) else parsed
          Text(
            text = "Equivalente: ${formatUsd(eqUsd)} · ${formatBs(eqUsd * bcvRate)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    },
    confirmButton = {
      val parsed = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
      val finalUsd = if (inputInBs) parsed / bcvRate.coerceAtLeast(1.0) else parsed
      Button(
        onClick = { onConfirm(title, category, finalUsd) },
        enabled = title.trim().length >= 2 && finalUsd > 0.0,
        modifier = Modifier.testTag("confirm_expense_button"),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}

@Composable
private fun AddUnitDialog(
  onDismiss: () -> Unit,
  onConfirm: (String, String, Double) -> Unit,
) {
  var unitNumber by remember { mutableStateOf("") }
  var ownerLabel by remember { mutableStateOf("") }
  var alicuotaText by remember { mutableStateOf("10.0") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Agregar Apartamento / Casa") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = unitNumber,
          onValueChange = { unitNumber = it },
          label = { Text("Nro. Apartamento / Casa (ej. Torre A - 4B)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = ownerLabel,
          onValueChange = { ownerLabel = it },
          label = { Text("Propietario / Familia") },
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
      val pct = alicuotaText.replace(",", ".").toDoubleOrNull() ?: 0.0
      Button(
        onClick = { onConfirm(unitNumber, ownerLabel.ifBlank { "Propietario" }, pct) },
        enabled = unitNumber.isNotBlank() && pct > 0.0 && pct <= 100.0,
      ) {
        Text("Agregar")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}

@Composable
private fun CondoSettingsDialog(
  condo: Condominium,
  onDismiss: () -> Unit,
  onSave: (Double, String, String, String, String, String) -> Unit,
) {
  var rateText by remember { mutableStateOf(condo.bcvRate.toString()) }
  var pmBank by remember { mutableStateOf(condo.pagoMovilBank) }
  var pmPhone by remember { mutableStateOf(condo.pagoMovilPhone) }
  var pmId by remember { mutableStateOf(condo.pagoMovilId) }
  var trBank by remember { mutableStateOf(condo.transferBank) }
  var trAcc by remember { mutableStateOf(condo.transferAccount) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Tasa BCV y Cuentas Bancarias") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = rateText,
          onValueChange = { rateText = it },
          label = { Text("Tasa BCV (Bs./USD)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = pmBank,
          onValueChange = { pmBank = it },
          label = { Text("Banco Pago Móvil") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = pmPhone,
          onValueChange = { pmPhone = it },
          label = { Text("Teléfono Pago Móvil") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = pmId,
          onValueChange = { pmId = it },
          label = { Text("RIF / Cédula") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = trBank,
          onValueChange = { trBank = it },
          label = { Text("Banco Transferencia") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = trAcc,
          onValueChange = { trAcc = it },
          label = { Text("Nro. Cuenta Bancaria") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      val rate = rateText.replace(",", ".").toDoubleOrNull() ?: condo.bcvRate
      Button(
        onClick = { onSave(rate, pmBank, pmPhone, pmId, trBank, trAcc) },
        enabled = rate > 0.0,
      ) {
        Text("Actualizar")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}
