package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.Condominio
import com.example.model.Vecino
import com.example.util.QrUtils
import java.util.concurrent.Executors

@Composable
fun ResidentAccessQrDialog(
  condominio: Condominio,
  vecino: Vecino,
  isBusy: Boolean,
  onGenerateNewCode: () -> Unit,
  onMessage: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

  val effectiveResidentCode = vecino.codigoAcceso.ifBlank { condominio.codigoAcceso }
  val qrPayload =
    remember(condominio.id, condominio.codigoAcceso, vecino.id, vecino.unidad, effectiveResidentCode) {
      QrUtils.buildResidentAccessQrPayload(
        condominioId = condominio.id,
        codigoAcceso = condominio.codigoAcceso,
        vecinoId = vecino.id,
        unidad = vecino.unidad,
        codigoVecino = effectiveResidentCode,
      )
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Filled.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Acceso QR · ${vecino.unidad}")
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "${vecino.nombreCompleto} · ${condominio.nombre}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        QrBitmapView(
          qrToken = qrPayload,
          sizeDp = 210,
          onBitmapReady = { qrBitmap = it },
        )

        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "CÓDIGO ÚNICO DEL RESIDENTE",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              text = effectiveResidentCode,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.testTag("unique_resident_access_code_text"),
            )
            Text(
              text = "Condominio ID: ${condominio.id} · Clave Junta: ${condominio.codigoAcceso}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              textAlign = TextAlign.Center,
            )
          }
        }

        Button(
          onClick = onGenerateNewCode,
          enabled = !isBusy,
          modifier =
            Modifier.fillMaxWidth()
              .height(48.dp)
              .testTag("generate_unique_qr_code_button"),
        ) {
          Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            if (vecino.codigoAcceso.isBlank()) "Generar Código Único"
            else "Regenerar Código Único"
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              cb.setPrimaryClip(ClipData.newPlainText("Acceso QR CondoZulia", qrPayload))
              onMessage("Invitación QR copiada al portapapeles.")
            },
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copiar")
          }
          OutlinedButton(
            onClick = {
              val bmp = qrBitmap ?: return@OutlinedButton
              val uri =
                QrUtils.saveQrBitmapToShareUri(
                  context = context,
                  bitmap = bmp,
                  fileName = "Acceso_${vecino.unidad.replace(" ", "_")}_$effectiveResidentCode",
                )
              if (uri != null) {
                val shareIntent =
                  Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                      Intent.EXTRA_TEXT,
                      "Acceso CondoZulia · ${condominio.nombre}\nUnidad: ${vecino.unidad}\nCódigo Único: $effectiveResidentCode\nID Condominio: ${condominio.id}\nCódigo Junta: ${condominio.codigoAcceso}",
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                runCatching {
                  context.startActivity(Intent.createChooser(shareIntent, "Compartir QR de acceso"))
                }
              }
            },
            enabled = qrBitmap != null,
            modifier = Modifier.weight(1f).height(48.dp).testTag("share_resident_qr_button"),
          ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Compartir")
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}

@Composable
fun CondoAccessQrDialog(
  condominio: Condominio,
  isAdmin: Boolean,
  isBusy: Boolean,
  onRotateCondoCode: () -> Unit,
  onMessage: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

  val qrPayload =
    remember(condominio.id, condominio.codigoAcceso) {
      QrUtils.buildResidentAccessQrPayload(
        condominioId = condominio.id,
        codigoAcceso = condominio.codigoAcceso,
      )
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Filled.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("QR General · ${condominio.nombre}")
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Comparte este código QR con los vecinos para que se unan directamente a ${condominio.nombre}.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        QrBitmapView(
          qrToken = qrPayload,
          sizeDp = 210,
          onBitmapReady = { qrBitmap = it },
        )

        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "CÓDIGO DE ACCESO DEL CONDOMINIO",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
              text = condominio.codigoAcceso,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
              text = "ID: ${condominio.id}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }

        if (isAdmin) {
          Button(
            onClick = onRotateCondoCode,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("rotate_condo_qr_code_button"),
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Generar Nuevo Código")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              cb.setPrimaryClip(ClipData.newPlainText("QR CondoZulia", qrPayload))
              onMessage("Payload QR copiado al portapapeles.")
            },
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copiar")
          }
          OutlinedButton(
            onClick = {
              val bmp = qrBitmap ?: return@OutlinedButton
              val uri =
                QrUtils.saveQrBitmapToShareUri(
                  context = context,
                  bitmap = bmp,
                  fileName = "Condominio_${condominio.codigoAcceso}",
                )
              if (uri != null) {
                val shareIntent =
                  Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                      Intent.EXTRA_TEXT,
                      "Únete a ${condominio.nombre} en CondoZulia\nID: ${condominio.id}\nCódigo: ${condominio.codigoAcceso}",
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                runCatching {
                  context.startActivity(Intent.createChooser(shareIntent, "Compartir QR de condominio"))
                }
              }
            },
            enabled = qrBitmap != null,
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Compartir")
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}

@Composable
fun AccessQrCameraScannerBox(
  onQrDecoded: (String) -> Unit,
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
                  mainExecutor.execute { onQrDecoded(decoded) }
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
          onCameraError("Cámara no disponible. Pega o escribe el código manualmente.")
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
    modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)),
  )
}
