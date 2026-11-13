package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import org.json.JSONObject

data class ResidentAccessQrPayload(
  val condominioId: String,
  val codigoAcceso: String,
  val vecinoId: String = "",
  val unidad: String = "",
  val codigoVecino: String = "",
)

object QrUtils {

  fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
    if (content.isBlank()) return null
    return runCatching {
      val hints =
        mapOf(
          EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
          EncodeHintType.CHARACTER_SET to "UTF-8",
          EncodeHintType.MARGIN to 1,
        )
      val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
      val pixels = IntArray(sizePx * sizePx)
      for (y in 0 until sizePx) {
        val offset = y * sizePx
        for (x in 0 until sizePx) {
          pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
      }
      bmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
      bmp
    }.getOrNull()
  }

  fun buildResidentAccessQrPayload(
    condominioId: String,
    codigoAcceso: String,
    vecinoId: String = "",
    unidad: String = "",
    codigoVecino: String = "",
  ): String {
    return JSONObject()
      .apply {
        put("app", "CondoZulia")
        put("condominioId", condominioId)
        put("codigoAcceso", codigoAcceso)
        if (vecinoId.isNotBlank()) put("vecinoId", vecinoId)
        if (unidad.isNotBlank()) put("unidad", unidad)
        if (codigoVecino.isNotBlank()) put("codigoVecino", codigoVecino)
      }
      .toString()
  }

  fun parseResidentAccessQrPayload(raw: String): ResidentAccessQrPayload? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    return runCatching {
      if (trimmed.startsWith("{")) {
        val json = JSONObject(trimmed)
        val condoId = json.optString("condominioId").trim()
        val code = json.optString("codigoAcceso").trim()
        if (condoId.isNotBlank() && code.isNotBlank()) {
          ResidentAccessQrPayload(
            condominioId = condoId,
            codigoAcceso = code,
            vecinoId = json.optString("vecinoId").trim(),
            unidad = json.optString("unidad").trim(),
            codigoVecino = json.optString("codigoVecino").trim(),
          )
        } else null
      } else if (trimmed.contains("|")) {
        val parts = trimmed.split("|")
        if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
          ResidentAccessQrPayload(
            condominioId = parts[0].trim(),
            codigoAcceso = parts[1].trim(),
            vecinoId = parts.getOrNull(2)?.trim().orEmpty(),
          )
        } else null
      } else {
        null
      }
    }.getOrNull()
  }

  fun saveQrBitmapToShareUri(context: Context, bitmap: Bitmap, fileName: String): Uri? {
    return runCatching {
      val dir = File(context.cacheDir, "qr").apply { mkdirs() }
      val file = File(dir, "$fileName.png")
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      }
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
  }

  fun decodeQrFromYuvBytes(yuvData: ByteArray, width: Int, height: Int): String? {
    return runCatching {
      val source =
        PlanarYUVLuminanceSource(
          yuvData,
          width,
          height,
          0,
          0,
          width,
          height,
          false,
        )
      val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
      val reader =
        MultiFormatReader().apply {
          setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
      reader.decode(binaryBitmap).text
    }.getOrNull()
  }

  fun compressUriToBase64(context: Context, uri: Uri, maxDim: Int = 420): String {
    return runCatching {
      val input = context.contentResolver.openInputStream(uri) ?: return ""
      val original = input.use { BitmapFactory.decodeStream(it) } ?: return ""
      val scale = max(original.width, original.height).toFloat() / maxDim.toFloat()
      val target =
        if (scale > 1f) {
          Bitmap.createScaledBitmap(
            original,
            (original.width / scale).toInt().coerceAtLeast(1),
            (original.height / scale).toInt().coerceAtLeast(1),
            true,
          )
        } else {
          original
        }
      val out = ByteArrayOutputStream()
      target.compress(Bitmap.CompressFormat.JPEG, 55, out)
      val bytes = out.toByteArray()
      if (bytes.size > 55_000) {
        out.reset()
        target.compress(Bitmap.CompressFormat.JPEG, 35, out)
      }
      Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrDefault("")
  }
}
