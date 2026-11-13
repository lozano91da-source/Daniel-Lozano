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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.example.model.Condominio
import com.example.model.PagoReportado
import com.example.model.Vecino
import com.example.ui.CondoDashboardUiData
import com.example.ui.CurrencyDisplay
import com.example.ui.components.AccessQrCameraScannerBox
import com.example.ui.components.Base64ReceiptThumbnail
import com.example.ui.components.CondoMoneyText
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatBs
import com.example.ui.components.formatUsd
import com.example.ui.theme.SolventeGreen
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
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
fun ResidentDashboard(
  data: CondoDashboardUiData,
  currencyDisplay: CurrencyDisplay,
  billingPeriod: String,
  isBusy: Boolean,
  onSelectVecino: (vecino: Vecino, claimIfUnassigned: Boolean) -> Unit,
  onJoinCondominio: (condominioId: String, codigoAcceso: String, vecinoId: String?) -> Unit,
  onOpenAdminSetup: () -> Unit,
  onReportPayment: (
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
  ) -> Unit,
  onMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showJoinDialog by remember { mutableStateOf(false) }
  var showReportDialogFor by remember { mutableStateOf<Vecino?>(null) }
  var hasNotificationPermission by remember {
    mutableStateOf(NotificationHelper.hasNotificationPermission(context))
  }

  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted
      onMessage(
        if (granted) "Notificaciones de cobro activadas."
        else "Puedes activar notificaciones más tarde desde Ajustes."
      )
    }

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
          icon = Icons.Filled.Key,
          title = "Aún no estás vinculado a un condominio",
          subtitle =
            "Únete usando el ID y código de acceso compartido por tu Junta, o crea un nuevo condominio desde el panel administrador.",
          actionLabel = "Unirse con Código",
          onAction = { showJoinDialog = true },
        )
        OutlinedButton(
          onClick = onOpenAdminSetup,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("go_to_admin_setup_button"),
        ) {
          Text("Crear nuevo condominio como Junta")
        }
      }
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

  val activeVecino = data.activeVecino
  val periodGastos =
    remember(data.gastos, billingPeriod) {
      val filtered = data.gastos.filter { it.periodoMes == billingPeriod }
      filtered.ifEmpty { data.gastos }
    }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  Icons.Filled.Apartment,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Tu Unidad en ${condo.nombre}",
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              TextButton(onClick = { showJoinDialog = true }) {
                Text("Otro código")
              }
            }

            if (data.vecinos.isEmpty()) {
              Text(
                text = "La Junta aún no ha registrado apartamentos o casas en este condominio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                data.vecinos.forEach { vecino ->
                  FilterChip(
                    selected = activeVecino?.id == vecino.id,
                    onClick = { onSelectVecino(vecino, vecino.residentUid.isBlank()) },
                    label = { Text(vecino.unidad) },
                    modifier = Modifier.testTag("unit_chip_${vecino.id}"),
                  )
                }
              }
            }
          }
        }
      }

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
                  text = "Activa notificaciones para alertas de vencimiento y conciliación.",
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
      if (activeVecino != null) {
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
                          MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
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
                        text = "ESTADO DE CUENTA · ${activeVecino.unidad.uppercase(Locale.US)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                      )
                      Text(
                        text = "${activeVecino.nombreCompleto} · Alícuota ${String.format(Locale.US, "%.2f", activeVecino.alicuotaPct)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                      )
                    }
                    StatusBadge(status = activeVecino.estadoSolvencia)
                  }

                  if (activeVecino.saldoPendienteUsd > 0.0) {
                    val primaryAmt =
                      if (currencyDisplay == CurrencyDisplay.USD) {
                        formatUsd(activeVecino.saldoPendienteUsd)
                      } else {
                        formatBs(activeVecino.saldoPendienteBs)
                      }
                    val secondaryAmt =
                      if (currencyDisplay == CurrencyDisplay.USD) {
                        formatBs(activeVecino.saldoPendienteBs)
                      } else {
                        formatUsd(activeVecino.saldoPendienteUsd)
                      }
                    Text(
                      text = primaryAmt,
                      style = MaterialTheme.typography.displaySmall,
                      color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                      text = "Equivalente: $secondaryAmt · Tasa BCV Bs. ${String.format(Locale.US, "%.2f", condo.tasaBcv)}",
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
                        text = "Unidad Solvente ($0.00 pendiente)",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                      )
                    }
                  }
                }
              }

              // Actions + Payment Account Details
              Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Button(
                    onClick = { showReportDialogFor = activeVecino },
                    enabled = !isBusy,
                    modifier =
                      Modifier.weight(1f)
                        .height(48.dp)
                        .testTag("resident_report_payment_button"),
                  ) {
                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reportar Pago")
                  }
                  OutlinedButton(
                    onClick = {
                      val latestApproved =
                        data.misPagos.firstOrNull {
                          it.vecinoId == activeVecino.id && it.estado == "aprobado"
                        } ?: data.misPagos.firstOrNull { it.vecinoId == activeVecino.id }
                      ReceiptPdfGenerator.generateVecinoReceiptPdf(
                        context = context,
                        condominio = condo,
                        vecino = activeVecino,
                        periodoMes = billingPeriod,
                        gastos = periodGastos,
                        pago = latestApproved,
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
                          onMessage(
                            if (res.savedToDownloads) "Recibo guardado en Descargas/CondoZulia"
                            else "Recibo PDF generado: ${res.fileName}"
                          )
                        }
                        .onFailure {
                          onMessage(it.localizedMessage ?: "No se pudo generar el recibo PDF.")
                        }
                    },
                    modifier =
                      Modifier.weight(1f)
                        .height(48.dp)
                        .testTag("resident_download_receipt_button"),
                  ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Recibo PDF")
                  }
                }

                if (condo.telefonoPagoMovil.isNotBlank() || condo.cuentaTransferencia.isNotBlank()) {
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(
                      modifier = Modifier.padding(12.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Text(
                        text = "Cuentas de la Junta para pagar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                      )
                      if (condo.telefonoPagoMovil.isNotBlank()) {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically,
                        ) {
                          Text(
                            text = "Pago Móvil: ${condo.bancoPagoMovil} · ${condo.telefonoPagoMovil} · ${condo.rifPagoMovil}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                          )
                          TextButton(
                            onClick = {
                              val cb =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                              val copyText =
                                "${condo.bancoPagoMovil} ${condo.telefonoPagoMovil} ${condo.rifPagoMovil}"
                              cb.setPrimaryClip(ClipData.newPlainText("Pago Móvil", copyText))
                              onMessage("Datos de Pago Móvil copiados.")
                            }
                          ) {
                            Icon(
                              Icons.Filled.ContentCopy,
                              contentDescription = "Copiar datos de Pago Móvil",
                              modifier = Modifier.size(16.dp),
                            )
                          }
                        }
                      }
                      if (condo.cuentaTransferencia.isNotBlank()) {
                        Text(
                          text = "Transferencia: ${condo.bancoTransferencia} · ${condo.cuentaTransferencia}",
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

      // Monthly Condo Expenses Breakdown
      item {
        Text(
          text = "Gastos del Condominio (${periodGastos.size})",
          style = MaterialTheme.typography.titleLarge,
        )
      }
      if (periodGastos.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "Sin gastos registrados aún",
            subtitle = "Cuando la Junta cargue los gastos del mes, aparecerá aquí el desglose transparente.",
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
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = gasto.concepto, style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "${gasto.categoria} · ${gasto.periodoMes}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              CondoMoneyText(
                amountUsd = gasto.montoUsd,
                amountBs = gasto.montoBs,
                currencyDisplay = currencyDisplay,
              )
            }
          }
        }
      }

      // Reported Payments History
      item {
        Text(
          text = "Mis Pagos Reportados (${data.misPagos.size})",
          style = MaterialTheme.typography.titleLarge,
        )
      }
      if (data.misPagos.isEmpty()) {
        item {
          EmptyStateCard(
            icon = Icons.Filled.Payments,
            title = "No has reportado pagos",
            subtitle = "Usa el botón 'Reportar Pago' para enviar tu referencia de Pago Móvil, transferencia o divisas.",
          )
        }
      } else {
        items(data.misPagos, key = { it.id }) { pago ->
          ResidentPaymentHistoryCard(pago = pago)
        }
      }
    }
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

  showReportDialogFor?.let { vecino ->
    ResidentReportPaymentDialog(
      condominio = condo,
      vecino = vecino,
      defaultPeriodo = billingPeriod,
      onDismiss = { showReportDialogFor = null },
      onSubmit = { periodo, metodo, banco, ref, tel, moneda, monto, fecha, base64 ->
        showReportDialogFor = null
        onReportPayment(vecino, periodo, metodo, banco, ref, tel, moneda, monto, fecha, base64)
      },
    )
  }
}

@Composable
private fun ResidentPaymentHistoryCard(pago: PagoReportado) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${pago.unidad} · ${pago.periodoMes}",
            style = MaterialTheme.typography.titleMedium,
          )
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
      Text(
        text = "Monto: $montoStr · Ref: ${pago.numeroReferencia}",
        style = MaterialTheme.typography.labelLarge,
      )
      Text(
        text = "Fecha: ${pago.fechaPago}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (pago.notaConciliacion.isNotBlank()) {
        Text(
          text = "Nota Junta: ${pago.notaConciliacion}",
          style = MaterialTheme.typography.bodyMedium,
          color = SolventeGreen,
        )
      }
      if (pago.comprobanteBase64.isNotBlank()) {
        Base64ReceiptThumbnail(base64 = pago.comprobanteBase64)
      }
    }
  }
}

@Composable
fun JoinCondominioDialog(
  onDismiss: () -> Unit,
  onConfirm: (condominioId: String, codigoAcceso: String, vecinoId: String?) -> Unit,
) {
  val context = LocalContext.current
  var condominioId by remember { mutableStateOf("") }
  var codigoAcceso by remember { mutableStateOf("") }
  var targetVecinoId by remember { mutableStateOf<String?>(null) }
  var qrStatusHint by remember { mutableStateOf<String?>(null) }
  var showScanner by remember { mutableStateOf(false) }
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasCameraPermission = granted
      if (granted) {
        showScanner = true
      } else {
        qrStatusHint = "Permiso de cámara denegado. Puedes pegar o escribir los códigos."
      }
    }

  fun applyRawQrText(raw: String): Boolean {
    val parsed = QrUtils.parseResidentAccessQrPayload(raw) ?: return false
    condominioId = parsed.condominioId
    codigoAcceso = parsed.codigoAcceso.uppercase(Locale.US)
    targetVecinoId = parsed.vecinoId.ifBlank { null }
    qrStatusHint =
      if (parsed.unidad.isNotBlank()) "QR leído para ${parsed.unidad} (${parsed.codigoVecino})."
      else "Código QR del condominio leído."
    return true
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Unirse a Condominio") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Escanea el QR generado por tu Junta o ingresa el ID y código de acceso.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              if (hasCameraPermission) {
                showScanner = !showScanner
              } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
              }
            },
            modifier = Modifier.weight(1f).height(48.dp).testTag("scan_access_qr_button"),
          ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (showScanner) "Cerrar" else "Escanear QR")
          }
          OutlinedButton(
            onClick = {
              val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val pasted = cb.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
              if (!applyRawQrText(pasted)) {
                qrStatusHint = "El portapapeles no contiene un código QR CondoZulia válido."
              }
            },
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Pegar QR")
          }
        }
        if (showScanner && hasCameraPermission) {
          AccessQrCameraScannerBox(
            onQrDecoded = { decoded ->
              if (applyRawQrText(decoded)) {
                showScanner = false
              }
            },
            onCameraError = { err ->
              showScanner = false
              qrStatusHint = err
            },
          )
        }
        if (!qrStatusHint.isNullOrBlank()) {
          Text(
            text = qrStatusHint.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        OutlinedTextField(
          value = condominioId,
          onValueChange = { condominioId = it },
          label = { Text("ID del Condominio (ej. cond_...)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("join_condominio_id_input"),
        )
        OutlinedTextField(
          value = codigoAcceso,
          onValueChange = { codigoAcceso = it.uppercase(Locale.US) },
          label = { Text("Código de Acceso") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("join_codigo_acceso_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(condominioId, codigoAcceso, targetVecinoId) },
        enabled = condominioId.trim().length >= 3 && codigoAcceso.trim().length in 4..16,
        modifier = Modifier.testTag("confirm_join_condo_button"),
      ) {
        Text("Unirse")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResidentReportPaymentDialog(
  condominio: Condominio,
  vecino: Vecino,
  defaultPeriodo: String,
  onDismiss: () -> Unit,
  onSubmit: (
    periodoMes: String,
    metodoPago: String,
    bancoOrigen: String,
    numeroReferencia: String,
    telefonoEmisor: String,
    moneda: String,
    montoReportado: Double,
    fechaPago: String,
    comprobanteBase64: String,
  ) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var metodo by remember { mutableStateOf("pago_movil") }
  var banco by remember { mutableStateOf("Banesco") }
  var referencia by remember { mutableStateOf("") }
  var telefono by remember { mutableStateOf(vecino.telefono.ifBlank { "0414" }) }
  var moneda by remember { mutableStateOf("VES") }
  val initialBs =
    if (vecino.saldoPendienteBs > 0.0) vecino.saldoPendienteBs
    else condominio.tasaBcv * 10.0
  var montoText by remember { mutableStateOf(String.format(Locale.US, "%.2f", initialBs)) }
  val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
  var fecha by remember { mutableStateOf(today) }
  var voucherBase64 by remember { mutableStateOf("") }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val encoded = withContext(Dispatchers.IO) { QrUtils.compressUriToBase64(context, uri) }
          if (encoded.isNotBlank()) voucherBase64 = encoded
        }
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Reportar Pago · ${vecino.unidad}") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(
            "pago_movil" to "Pago Móvil",
            "transferencia" to "Transferencia",
            "efectivo_usd" to "Efectivo $",
            "zelle" to "Zelle",
          ).forEach { (key, label) ->
            FilterChip(
              selected = metodo == key,
              onClick = {
                metodo = key
                if (key == "efectivo_usd" || key == "zelle") {
                  moneda = "USD"
                  val defaultUsd =
                    if (vecino.saldoPendienteUsd > 0.0) vecino.saldoPendienteUsd else 10.0
                  montoText = String.format(Locale.US, "%.2f", defaultUsd)
                } else {
                  moneda = "VES"
                  montoText = String.format(Locale.US, "%.2f", initialBs)
                }
              },
              label = { Text(label) },
            )
          }
        }

        OutlinedTextField(
          value = banco,
          onValueChange = { banco = it },
          label = { Text("Banco Emisor / Plataforma") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = referencia,
          onValueChange = { referencia = it },
          label = { Text("Número de Referencia") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("resident_payment_reference_input"),
        )
        if (metodo == "pago_movil") {
          OutlinedTextField(
            value = telefono,
            onValueChange = { telefono = it },
            label = { Text("Teléfono Emisor") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = montoText,
            onValueChange = { montoText = it },
            label = { Text("Monto ($moneda)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = fecha,
            onValueChange = { fecha = it },
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
          Text(if (voucherBase64.isNotBlank()) "Comprobante adjunto ✓" else "Adjuntar Captura (Opcional)")
        }
      }
    },
    confirmButton = {
      val parsedMonto = montoText.replace(",", ".").toDoubleOrNull() ?: 0.0
      Button(
        onClick = {
          onSubmit(
            defaultPeriodo,
            metodo,
            banco.ifBlank { "Banco" },
            referencia,
            telefono,
            moneda,
            parsedMonto,
            fecha,
            voucherBase64,
          )
        },
        enabled = referencia.trim().length >= 3 && parsedMonto > 0.0 && banco.trim().length >= 2,
        modifier = Modifier.testTag("confirm_resident_payment_button"),
      ) {
        Text("Enviar Reporte")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}
