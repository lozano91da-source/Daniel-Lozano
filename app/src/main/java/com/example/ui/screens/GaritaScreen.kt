package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.CondoUnit
import com.example.model.VisitorPass
import com.example.ui.CondoWorkspaceData
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.QrBitmapView
import com.example.ui.components.StatusBadge
import com.example.ui.theme.SolventeGreen
import com.example.util.QrUtils
import java.util.concurrent.Executors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GaritaScreen(
  data: CondoWorkspaceData,
  isBusy: Boolean,
  onCreatePass: (unit: CondoUnit, visitorName: String, visitorCedula: String, vehiclePlate: String, visitType: String) -> Unit,
  onUpdatePassStatus: (passId: String, nextStatus: String) -> Unit,
  onMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  val condo = data.activeCondo ?: return
  var selectedTab by remember { mutableIntStateOf(0) }
  var showCreateDialog by remember { mutableStateOf(false) }

  // Garita Scanner / Verification state
  var tokenQuery by remember { mutableStateOf("") }
  var showCameraScanner by remember { mutableStateOf(false) }
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
        showCameraScanner = true
      } else {
        onMessage("Permiso de cámara denegado. Puedes validar ingresando el código del pase.")
      }
    }

  val matchedPass =
    remember(data.visitorPasses, tokenQuery) {
      val clean = tokenQuery.trim().uppercase()
      if (clean.isBlank()) null
      else data.visitorPasses.firstOrNull { it.qrToken.equals(clean, ignoreCase = true) }
    }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 820.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surface,
          modifier = Modifier.clip(RoundedCornerShape(14.dp)),
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Pases QR (${data.visitorPasses.size})") },
            icon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
            modifier = Modifier.testTag("garita_tab_passes"),
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("Control Garita") },
            icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
            modifier = Modifier.testTag("garita_tab_scanner"),
          )
        }
      }

      if (selectedTab == 0) {
        item {
          Button(
            onClick = { showCreateDialog = true },
            enabled = !isBusy && data.units.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("new_qr_pass_button"),
          ) {
            Icon(Icons.Filled.QrCode2, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generar Pase QR para Visitante")
          }
        }

        if (data.visitorPasses.isEmpty()) {
          item {
            EmptyStateCard(
              icon = Icons.Filled.QrCode2,
              title = "Sin pases QR generados",
              subtitle =
                "Autoriza visitas, deliveries o técnicos generando un código QR único para validar en la garita de ${condo.name}.",
              actionLabel = if (data.units.isNotEmpty()) "Crear primer pase QR" else null,
              onAction = if (data.units.isNotEmpty()) ({ showCreateDialog = true }) else null,
            )
          }
        } else {
          items(data.visitorPasses, key = { it.id }) { pass ->
            VisitorPassCard(
              pass = pass,
              isBusy = isBusy,
              onShareQr = { bmp ->
                val uri = QrUtils.saveQrBitmapToShareUri(context, bmp, pass.qrToken)
                if (uri != null) {
                  val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                      type = "image/png"
                      putExtra(Intent.EXTRA_STREAM, uri)
                      putExtra(
                        Intent.EXTRA_TEXT,
                        "Pase QR ${condo.name} · Unidad ${pass.unitNumber} · Visitante: ${pass.visitorName} · Código: ${pass.qrToken}",
                      )
                      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                  runCatching {
                    context.startActivity(Intent.createChooser(shareIntent, "Compartir pase QR"))
                  }
                }
              },
              onCancel = { onUpdatePassStatus(pass.id, "cancelled") },
            )
          }
        }
      } else {
        // Garita Control Scanner & Code Verification
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = "Verificación de Acceso en Garita",
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = "Escanea el QR del visitante con la cámara o escribe el código CZ-XXXXXXXX.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Button(
                  onClick = {
                    if (hasCameraPermission) {
                      showCameraScanner = !showCameraScanner
                    } else {
                      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                  },
                  modifier = Modifier.weight(1f).height(48.dp).testTag("toggle_camera_scanner_button"),
                ) {
                  Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (showCameraScanner) "Cerrar Cámara" else "Escanear QR")
                }
              }

              if (showCameraScanner && hasCameraPermission) {
                CameraQrScannerPreview(
                  onQrDetected = { detectedToken ->
                    tokenQuery = detectedToken
                    showCameraScanner = false
                    onMessage("QR leído: $detectedToken")
                  },
                  onCameraError = { err ->
                    showCameraScanner = false
                    onMessage(err)
                  },
                )
              }

              OutlinedTextField(
                value = tokenQuery,
                onValueChange = { tokenQuery = it.uppercase() },
                label = { Text("Código del Pase QR (ej. CZ-A1B2C3D4)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("garita_token_input"),
              )

              if (tokenQuery.isNotBlank()) {
                if (matchedPass == null) {
                  Text(
                    text = "No se encontró ningún pase con el código '$tokenQuery'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                  )
                } else {
                  GaritaVerificationResultCard(
                    pass = matchedPass,
                    isBusy = isBusy,
                    onCheckIn = { onUpdatePassStatus(matchedPass.id, "checked_in") },
                    onCheckOut = { onUpdatePassStatus(matchedPass.id, "checked_out") },
                  )
                }
              }
            }
          }
        }

        // Active / Checked-in queue for quick guard action
        val gatePasses =
          data.visitorPasses.filter { it.status == "active" || it.status == "checked_in" }
        if (gatePasses.isNotEmpty()) {
          item {
            Text(
              text = "Visitas Activas y En Sitio (${gatePasses.size})",
              style = MaterialTheme.typography.titleMedium,
            )
          }
          items(gatePasses, key = { "gate_${it.id}" }) { pass ->
            GaritaVerificationResultCard(
              pass = pass,
              isBusy = isBusy,
              onCheckIn = { onUpdatePassStatus(pass.id, "checked_in") },
              onCheckOut = { onUpdatePassStatus(pass.id, "checked_out") },
            )
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateVisitorPassDialog(
      units = data.units,
      defaultUnit = data.activeUnit ?: data.units.firstOrNull(),
      onDismiss = { showCreateDialog = false },
      onCreate = { unit, name, cedula, plate, type ->
        showCreateDialog = false
        onCreatePass(unit, name, cedula, plate, type)
      },
    )
  }
}

@Composable
private fun VisitorPassCard(
  pass: VisitorPass,
  isBusy: Boolean,
  onShareQr: (Bitmap) -> Unit,
  onCancel: () -> Unit,
) {
  var generatedBmp by remember { mutableStateOf<Bitmap?>(null) }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${pass.visitorName} · ${pass.visitType}",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Destino: ${pass.unitNumber} · CI: ${pass.visitorCedula}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (pass.vehiclePlate.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Filled.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Placa: ${pass.vehiclePlate}",
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }
        }
        StatusBadge(status = pass.status)
      }

      QrBitmapView(
        qrToken = pass.qrToken,
        sizeDp = 164,
        onBitmapReady = { generatedBmp = it },
      )

      Text(
        text = pass.qrToken,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = { generatedBmp?.let(onShareQr) },
          enabled = generatedBmp != null,
          modifier = Modifier.weight(1f).height(48.dp),
        ) {
          Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Compartir QR")
        }
        if (pass.status == "active") {
          OutlinedButton(
            onClick = onCancel,
            enabled = !isBusy,
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Text("Anular")
          }
        }
      }
    }
  }
}

@Composable
private fun GaritaVerificationResultCard(
  pass: VisitorPass,
  isBusy: Boolean,
  onCheckIn: () -> Unit,
  onCheckOut: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            Icons.Filled.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(text = pass.visitorName, style = MaterialTheme.typography.titleMedium)
            Text(
              text = "${pass.unitNumber} · CI ${pass.visitorCedula} · ${pass.qrToken}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusBadge(status = pass.status)
      }

      if (pass.status == "active") {
        Button(
          onClick = onCheckIn,
          enabled = !isBusy,
          colors = ButtonDefaults.buttonColors(containerColor = SolventeGreen),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("garita_checkin_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Autorizar Entrada")
        }
      } else if (pass.status == "checked_in") {
        Button(
          onClick = onCheckOut,
          enabled = !isBusy,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("garita_checkout_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Registrar Salida")
        }
      }
    }
  }
}

@Composable
private fun CameraQrScannerPreview(
  onQrDetected: (String) -> Unit,
  onCameraError: (String) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
  val previewView = remember { PreviewView(context) }

  DisposableEffect(Unit) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val mainExecutor = ContextCompat.getMainExecutor(context)

    cameraProviderFuture.addListener(
      {
        runCatching {
          val cameraProvider = cameraProviderFuture.get()
          val preview =
            Preview.Builder().build().also {
              it.surfaceProvider = previewView.surfaceProvider
            }

          val imageAnalysis =
            ImageAnalysis.Builder()
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()

          imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            try {
              val plane = imageProxy.planes.firstOrNull()
              if (plane != null) {
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val decoded = QrUtils.decodeQrFromYuvBytes(bytes, imageProxy.width, imageProxy.height)
                if (!decoded.isNullOrBlank()) {
                  mainExecutor.execute { onQrDetected(decoded) }
                }
              }
            } finally {
              imageProxy.close()
            }
          }

          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis,
          )
        }.onFailure {
          onCameraError("Cámara no disponible en este dispositivo. Usa el código manual.")
        }
      },
      mainExecutor,
    )

    onDispose {
      runCatching { cameraProviderFuture.get().unbindAll() }
      analysisExecutor.shutdown()
    }
  }

  AndroidView(
    factory = { previewView },
    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)),
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateVisitorPassDialog(
  units: List<CondoUnit>,
  defaultUnit: CondoUnit?,
  onDismiss: () -> Unit,
  onCreate: (CondoUnit, String, String, String, String) -> Unit,
) {
  var selectedUnit by remember { mutableStateOf(defaultUnit ?: units.first()) }
  var visitorName by remember { mutableStateOf("") }
  var visitorCedula by remember { mutableStateOf("") }
  var vehiclePlate by remember { mutableStateOf("") }
  var visitType by remember { mutableStateOf("Visita") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nuevo Pase QR") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          units.take(6).forEach { unit ->
            FilterChip(
              selected = selectedUnit.id == unit.id,
              onClick = { selectedUnit = unit },
              label = { Text(unit.unitNumber) },
            )
          }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("Visita", "Delivery", "Servicio").forEach { type ->
            FilterChip(
              selected = visitType == type,
              onClick = { visitType = type },
              label = { Text(type) },
            )
          }
        }
        OutlinedTextField(
          value = visitorName,
          onValueChange = { visitorName = it },
          label = { Text("Nombre del Visitante") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("visitor_name_input"),
        )
        OutlinedTextField(
          value = visitorCedula,
          onValueChange = { visitorCedula = it },
          label = { Text("Cédula / Documento (ej. V-18234567)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("visitor_cedula_input"),
        )
        OutlinedTextField(
          value = vehiclePlate,
          onValueChange = { vehiclePlate = it },
          label = { Text("Placa de Vehículo (Opcional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onCreate(selectedUnit, visitorName, visitorCedula, vehiclePlate, visitType) },
        enabled = visitorName.trim().length >= 2 && visitorCedula.trim().length >= 4,
        modifier = Modifier.testTag("confirm_qr_pass_button"),
      ) {
        Text("Generar QR")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
  )
}
