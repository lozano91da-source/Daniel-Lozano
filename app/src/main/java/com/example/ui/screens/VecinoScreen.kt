package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.CondoUnit
import com.example.model.Condominium
import com.example.model.PaymentReminder
import com.example.model.UnitInvoice
import com.example.ui.CondoWorkspaceData
import com.example.ui.CurrencyDisplay
import com.example.ui.components.CondoMoneyText
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatBs
import com.example.ui.components.formatUsd
import com.example.ui.theme.SolventeGreen
import com.example.util.NotificationHelper
import com.example.util.QrUtils
import com.example.util.ReceiptPdfGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VecinoScreen(
  data: CondoWorkspaceData,
  currentUserId: String,
  currencyDisplay: CurrencyDisplay,
  isBusy: Boolean,
  onSelectUnit: (unit: CondoUnit, claimIfUnassigned: Boolean) -> Unit,
  onReportPayment: (
    invoice: UnitInvoice,
    method: String,
    bankName: String,
    referenceNumber: String,
    senderPhone: String,
    currency: String,
    amountReported: Double,
    paymentDate: String,
    receiptPreviewBase64: String,
  ) -> Unit,
  onMarkReminderRead: (String) -> Unit,
  onMessage: (String) -> Unit,
) {
  val condo = data.activeCondo ?: return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var activeInvoiceToPay by remember { mutableStateOf<UnitInvoice?>(null) }
  var hasNotificationPermission by remember {
    mutableStateOf(NotificationHelper.hasNotificationPermission(context))
  }

  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted
      if (granted) {
        onMessage("Notificaciones de cobro activadas.")
      } else {
        onMessage("Permiso de notificaciones denegado. Puedes activarlas luego en Ajustes.")
      }
    }

  val activeUnit = data.activeUnit
  val unitInvoices =
    remember(data.invoices, activeUnit) {
      if (activeUnit == null) emptyList()
      else data.invoices.filter { it.unitId == activeUnit.id }
    }
  val unitReminders =
    remember(data.reminders, activeUnit) {
      if (activeUnit == null) data.reminders
      else data.reminders.filter { it.unitId == activeUnit.id || it.targetUid == currentUserId }
    }

  val openDebtUsd =
    remember(unitInvoices) {
      unitInvoices
        .filter { it.status == "pending" || it.status == "overdue" }
        .sumOf { it.amountDueUsd }
    }
  val openDebtBs = openDebtUsd * condo.bcvRate

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Apartment / House Selector
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Filled.Apartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Tu Apartamento / Casa en ${condo.name}",
                style = MaterialTheme.typography.titleMedium,
              )
            }
            if (data.units.isEmpty()) {
              Text(
                text = "La Junta aún no ha registrado unidades en este condominio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                data.units.forEach { unit ->
                  val isSelected = activeUnit?.id == unit.id
                  FilterChip(
                    selected = isSelected,
                    onClick = { onSelectUnit(unit, unit.residentUid.isBlank()) },
                    label = { Text(unit.unitNumber) },
                  )
                }
              }
            }
          }
        }
      }

      // Notification Permission Opt-In Banner (Android 13+)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
              ),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Filled.Notifications,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Activa recordatorios de cuotas pendientes en tu dispositivo.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
              TextButton(
                onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
              ) {
                Text("Activar")
              }
            }
          }
        }
      }

      // Balance Hero Card
      if (activeUnit != null) {
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    text = "ESTADO DE CUENTA · ${activeUnit.unitNumber.uppercase(Locale.US)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                  )
                  if (openDebtUsd > 0.0) {
                    val mainText =
                      if (currencyDisplay == CurrencyDisplay.USD) formatUsd(openDebtUsd)
                      else formatBs(openDebtBs)
                    val secText =
                      if (currencyDisplay == CurrencyDisplay.USD) formatBs(openDebtBs)
                      else formatUsd(openDebtUsd)
                    Text(
                      text = mainText,
                      style = MaterialTheme.typography.displaySmall,
                      color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                      text = "Equivalente: $secText · Tasa BCV ${String.format(Locale.US, "%.2f", condo.bcvRate)}",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    )
                  } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = "Unidad Solvente",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                      )
                    }
                  }
                }
              }

              // Bank Details for Pago Movil / Transferencia
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = "Cuentas de la Junta para pagar",
                  style = MaterialTheme.typography.titleMedium,
                )
                if (condo.pagoMovilPhone.isNotBlank()) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "Pago Móvil: ${condo.pagoMovilBank} · ${condo.pagoMovilPhone} · ${condo.pagoMovilId}",
                      style = MaterialTheme.typography.bodyMedium,
                      modifier = Modifier.weight(1f),
                    )
                    TextButton(
                      onClick = {
                        val cb =
                          context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = "${condo.pagoMovilBank} ${condo.pagoMovilPhone} ${condo.pagoMovilId}"
                        cb.setPrimaryClip(ClipData.newPlainText("Pago Móvil", text))
                        onMessage("Datos de Pago Móvil copiados.")
                      }
                    ) {
                      Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar Pago Móvil", modifier = Modifier.size(16.dp))
                    }
                  }
                }
                if (condo.transferAccount.isNotBlank()) {
                  Text(
                    text = "Transferencia: ${condo.transferBank} · ${condo.transferAccount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }

      // Unread / Active Payment Reminders
      val unreadReminders = unitReminders.filter { !it.isRead }
      if (unreadReminders.isNotEmpty()) {
        item {
          Text(
            text = "Recordatorios de Pago (${unreadReminders.size})",
            style = MaterialTheme.typography.titleLarge,
          )
        }
        items(unreadReminders, key = { it.id }) { reminder ->
          ReminderAlertCard(
            reminder = reminder,
            onMarkRead = { onMarkReminderRead(reminder.id) },
          )
        }
      }

      // Monthly Invoices & Receipts
      item {
        Text(
          text = "Recibos y Cuotas Mensuales",
          style = MaterialTheme.typography.titleLarge,
        )
      }

      if (unitInvoices.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "Sin recibos emitidos aún",
            subtitle =
              "Cuando la Junta divida los gastos del mes, verás aquí tu cuota en USD y Bolívares para pagar y descargar tu recibo PDF.",
          )
        }
      } else {
        items(unitInvoices, key = { it.id }) { invoice ->
          val approvedPayment =
            data.residentPayments.firstOrNull {
              it.invoiceId == invoice.id && it.status == "approved"
            } ?: data.adminPayments.firstOrNull {
              it.invoiceId == invoice.id && it.status == "approved"
            }

          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(
                    text = "Período ${invoice.billingPeriod}",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text = "${invoice.receiptNumber} · Alícuota ${String.format(Locale.US, "%.2f", invoice.alicuotaPct)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                StatusBadge(status = invoice.status)
              }

              CondoMoneyText(
                amountUsd = invoice.amountDueUsd,
                amountBs = invoice.amountDueBs,
                currencyDisplay = currencyDisplay,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                if (invoice.status == "pending" || invoice.status == "overdue") {
                  Button(
                    onClick = { activeInvoiceToPay = invoice },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("report_payment_button"),
                  ) {
                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reportar Pago")
                  }
                }
                OutlinedButton(
                  onClick = {
                    val periodExpenses =
                      data.expenses.filter { it.billingPeriod == invoice.billingPeriod }
                    ReceiptPdfGenerator.generateInvoiceReceiptPdf(
                      context = context,
                      condo = condo,
                      invoice = invoice,
                      expenses = periodExpenses,
                      approvedPayment = approvedPayment,
                    )
                      .onSuccess { res ->
                        val viewIntent =
                          Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(res.shareUri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                          }
                        val chooser =
                          Intent.createChooser(viewIntent, "Abrir o compartir ${res.fileName}")
                        runCatching { context.startActivity(chooser) }
                        val savedNote =
                          if (res.savedToDownloads) "Guardado en Descargas/CondoZulia"
                          else "PDF generado"
                        onMessage("$savedNote: ${res.fileName}")
                      }
                      .onFailure {
                        onMessage(it.localizedMessage ?: "No se pudo generar el recibo PDF.")
                      }
                  },
                  modifier = Modifier.weight(1f).height(48.dp).testTag("download_receipt_button"),
                ) {
                  Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Recibo PDF")
                }
              }
            }
          }
        }
      }
    }
  }

  activeInvoiceToPay?.let { invoice ->
    ReportPaymentDialog(
      condo = condo,
      invoice = invoice,
      onDismiss = { activeInvoiceToPay = null },
      onSubmit = { method, bank, ref, phone, currency, amount, date, base64 ->
        activeInvoiceToPay = null
        onReportPayment(invoice, method, bank, ref, phone, currency, amount, date, base64)
      },
    )
  }
}

@Composable
private fun ReminderAlertCard(
  reminder: PaymentReminder,
  onMarkRead: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = reminder.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onMarkRead) { Text("Entendido") }
      }
      Text(
        text = reminder.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportPaymentDialog(
  condo: Condominium,
  invoice: UnitInvoice,
  onDismiss: () -> Unit,
  onSubmit: (
    method: String,
    bankName: String,
    referenceNumber: String,
    senderPhone: String,
    currency: String,
    amountReported: Double,
    paymentDate: String,
    receiptPreviewBase64: String,
  ) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var method by remember { mutableStateOf("pago_movil") }
  var bankName by remember { mutableStateOf("Banesco") }
  var reference by remember { mutableStateOf("") }
  var senderPhone by remember { mutableStateOf("0414") }
  var currency by remember { mutableStateOf("VES") }
  var amountText by remember {
    mutableStateOf(String.format(Locale.US, "%.2f", invoice.amountDueBs))
  }
  val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
  var paymentDate by remember { mutableStateOf(today) }
  var voucherBase64 by remember { mutableStateOf("") }
  var voucherAttached by remember { mutableStateOf(false) }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val encoded = withContext(Dispatchers.IO) { QrUtils.compressUriToBase64(context, uri) }
          if (encoded.isNotBlank()) {
            voucherBase64 = encoded
            voucherAttached = true
          }
        }
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Reportar Pago · ${invoice.unitNumber}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(
            "pago_movil" to "Pago Móvil",
            "transferencia" to "Transferencia",
            "efectivo_usd" to "Efectivo $",
            "zelle" to "Zelle",
          ).forEach { (key, label) ->
            FilterChip(
              selected = method == key,
              onClick = {
                method = key
                if (key == "efectivo_usd" || key == "zelle") {
                  currency = "USD"
                  amountText = String.format(Locale.US, "%.2f", invoice.amountDueUsd)
                } else {
                  currency = "VES"
                  amountText = String.format(Locale.US, "%.2f", invoice.amountDueBs)
                }
              },
              label = { Text(label) },
            )
          }
        }

        OutlinedTextField(
          value = bankName,
          onValueChange = { bankName = it },
          label = { Text("Banco Emisor / Plataforma") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = reference,
          onValueChange = { reference = it },
          label = { Text("Número de Referencia") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("payment_reference_input"),
        )
        if (method == "pago_movil") {
          OutlinedTextField(
            value = senderPhone,
            onValueChange = { senderPhone = it },
            label = { Text("Teléfono Emisor") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Monto ($currency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = paymentDate,
            onValueChange = { paymentDate = it },
            label = { Text("Fecha") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }

        OutlinedButton(
          onClick = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (voucherAttached) "Captura adjunta ✓" else "Adjuntar Captura (Opcional)")
        }

        if (voucherAttached) {
          Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
            Text(
              text = "Comprobante comprimido listo para enviar a la Junta (${condo.name}).",
              style = MaterialTheme.typography.bodyMedium,
              color = SolventeGreen,
              modifier = Modifier.padding(8.dp),
            )
          }
        }
      }
    },
    confirmButton = {
      val parsedAmount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
      Button(
        onClick = {
          onSubmit(
            method,
            bankName.ifBlank { "Banco" },
            reference,
            senderPhone,
            currency,
            parsedAmount,
            paymentDate,
            voucherBase64,
          )
        },
        enabled = reference.trim().length >= 3 && parsedAmount > 0.0 && bankName.trim().length >= 2,
        modifier = Modifier.testTag("submit_payment_button"),
      ) {
        Text("Enviar Reporte")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}
