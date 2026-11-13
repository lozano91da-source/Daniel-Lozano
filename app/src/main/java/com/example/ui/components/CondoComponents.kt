package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.CurrencyDisplay
import com.example.ui.theme.MoraBg
import com.example.ui.theme.MoraRed
import com.example.ui.theme.PendienteAmber
import com.example.ui.theme.PendienteBg
import com.example.ui.theme.RevisionBg
import com.example.ui.theme.RevisionBlue
import com.example.ui.theme.SolventeBg
import com.example.ui.theme.SolventeGreen
import com.example.util.QrUtils
import java.util.Locale

fun formatUsd(amount: Double): String = "$${String.format(Locale.US, "%,.2f", amount)}"

fun formatBs(amount: Double): String = "Bs. ${String.format(Locale.US, "%,.2f", amount)}"

@Composable
fun CurrencyTogglePill(
  selected: CurrencyDisplay,
  onSelect: (CurrencyDisplay) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    CurrencyDisplay.entries.forEach { option ->
      val isSelected = option == selected
      Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        modifier =
          Modifier.clip(RoundedCornerShape(9.dp))
            .clickable { onSelect(option) }
            .testTag("currency_toggle_${option.name.lowercase()}"),
      ) {
        Text(
          text = if (option == CurrencyDisplay.USD) "USD $" else "Bs.",
          style = MaterialTheme.typography.labelLarge,
          color =
            if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }
  }
}

@Composable
fun CondoMoneyText(
  amountUsd: Double,
  amountBs: Double,
  currencyDisplay: CurrencyDisplay,
  primaryColor: Color = MaterialTheme.colorScheme.onSurface,
  large: Boolean = false,
) {
  val primaryText =
    if (currencyDisplay == CurrencyDisplay.USD) formatUsd(amountUsd) else formatBs(amountBs)
  val secondaryText =
    if (currencyDisplay == CurrencyDisplay.USD) formatBs(amountBs) else formatUsd(amountUsd)

  Column {
    Text(
      text = primaryText,
      style =
        if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = primaryColor,
    )
    Text(
      text = "≈ $secondaryText",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
  val (label, bg, fg) =
    when (status) {
      "paid", "approved", "checked_in", "solvente", "aprobado" ->
        Triple(
          when (status) {
            "paid", "solvente" -> "Solvente"
            "approved", "aprobado" -> "Conciliado"
            else -> "En sitio"
          },
          SolventeBg,
          SolventeGreen,
        )
      "in_review", "pending_review", "en_revision", "pendiente_revision" ->
        Triple("En revisión", RevisionBg, RevisionBlue)
      "overdue", "rejected", "cancelled", "moroso", "rechazado" ->
        Triple(
          when (status) {
            "overdue", "moroso" -> "En mora"
            "rejected", "rechazado" -> "Rechazado"
            else -> "Anulado"
          },
          MoraBg,
          MoraRed,
        )
      "checked_out" -> Triple("Salió", RevisionBg, RevisionBlue)
      "active" -> Triple("Pase activo", PendienteBg, PendienteAmber)
      else -> Triple("Pendiente", PendienteBg, PendienteAmber)
    }

  Surface(
    color = bg,
    shape = RoundedCornerShape(8.dp),
    modifier = modifier,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = fg,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun EmptyStateCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier =
          Modifier.size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = onAction) { Text(actionLabel) }
      }
    }
  }
}

@Composable
fun QrBitmapView(
  qrToken: String,
  modifier: Modifier = Modifier,
  sizeDp: Int = 180,
  onBitmapReady: ((Bitmap) -> Unit)? = null,
) {
  val bitmap =
    remember(qrToken) {
      QrUtils.generateQrBitmap(qrToken, 512)?.also { onBitmapReady?.invoke(it) }
    }
  if (bitmap != null) {
    Box(
      modifier =
        modifier
          .size(sizeDp.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color.White)
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
          .padding(10.dp),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Código QR $qrToken",
        modifier = Modifier.fillMaxWidth(),
        contentScale = ContentScale.Fit,
      )
    }
  }
}

@Composable
fun Base64ReceiptThumbnail(base64: String, modifier: Modifier = Modifier) {
  val bitmap =
    remember(base64) {
      if (base64.isBlank()) null
      else
        runCatching {
          val bytes = Base64.decode(base64, Base64.DEFAULT)
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
  if (bitmap != null) {
    Image(
      bitmap = bitmap.asImageBitmap(),
      contentDescription = "Comprobante de pago adjunto",
      modifier =
        modifier
          .fillMaxWidth()
          .height(140.dp)
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
      contentScale = ContentScale.Crop,
    )
  }
}
