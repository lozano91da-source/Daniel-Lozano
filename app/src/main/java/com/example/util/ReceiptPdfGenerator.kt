package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.Condominium
import com.example.model.MonthlyExpense
import com.example.model.PaymentReport
import com.example.model.UnitInvoice
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GeneratedReceiptResult(
  val shareUri: Uri,
  val savedToDownloads: Boolean,
  val fileName: String,
)

object ReceiptPdfGenerator {

  fun generateInvoiceReceiptPdf(
    context: Context,
    condo: Condominium,
    invoice: UnitInvoice,
    expenses: List<MonthlyExpense>,
    approvedPayment: PaymentReport?,
  ): Result<GeneratedReceiptResult> = runCatching {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    val headerPaint = Paint().apply {
      color = Color.rgb(9, 76, 102)
      style = Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, 595f, 118f, headerPaint)

    val titlePaint = Paint().apply {
      color = Color.WHITE
      textSize = 22f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subHeaderPaint = Paint().apply {
      color = Color.rgb(220, 240, 248)
      textSize = 12f
    }
    canvas.drawText("CONDOZULIA · RECIBO DE CONDOMINIO", 36f, 48f, titlePaint)
    canvas.drawText(condo.name, 36f, 74f, subHeaderPaint)
    if (condo.address.isNotBlank()) {
      canvas.drawText(condo.address, 36f, 94f, subHeaderPaint)
    }

    val labelPaint = Paint().apply {
      color = Color.rgb(82, 99, 115)
      textSize = 11f
    }
    val valuePaint = Paint().apply {
      color = Color.rgb(19, 32, 43)
      textSize = 13f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bodyPaint = Paint().apply {
      color = Color.rgb(30, 41, 59)
      textSize = 11.5f
    }
    val linePaint = Paint().apply {
      color = Color.rgb(210, 218, 226)
      strokeWidth = 1.2f
    }

    var y = 152f
    canvas.drawText("NRO. RECIBO", 36f, y, labelPaint)
    canvas.drawText("UNIDAD / APTO", 230f, y, labelPaint)
    canvas.drawText("PERÍODO", 420f, y, labelPaint)
    y += 18f
    canvas.drawText(invoice.receiptNumber, 36f, y, valuePaint)
    canvas.drawText(invoice.unitNumber, 230f, y, valuePaint)
    canvas.drawText(invoice.billingPeriod, 420f, y, valuePaint)

    y += 28f
    canvas.drawText("ALÍCUOTA", 36f, y, labelPaint)
    canvas.drawText("TASA BCV", 230f, y, labelPaint)
    canvas.drawText("ESTADO", 420f, y, labelPaint)
    y += 18f
    canvas.drawText("${String.format(Locale.US, "%.2f", invoice.alicuotaPct)}%", 36f, y, valuePaint)
    canvas.drawText("Bs. ${String.format(Locale.US, "%.2f", invoice.bcvRate)} / USD", 230f, y, valuePaint)

    val statusText =
      when (invoice.status) {
        "paid" -> "SOLVENTE / PAGADO"
        "in_review" -> "EN CONCILIACIÓN"
        "overdue" -> "MOROSO / VENCIDO"
        else -> "PENDIENTE POR PAGAR"
      }
    val statusPaint = Paint(valuePaint).apply {
      color =
        when (invoice.status) {
          "paid" -> Color.rgb(15, 157, 88)
          "overdue" -> Color.rgb(220, 38, 38)
          "in_review" -> Color.rgb(2, 132, 199)
          else -> Color.rgb(217, 119, 6)
        }
    }
    canvas.drawText(statusText, 420f, y, statusPaint)

    y += 22f
    canvas.drawLine(36f, y, 559f, y, linePaint)
    y += 26f

    // Summary Box
    val boxPaint = Paint().apply {
      color = Color.rgb(244, 248, 250)
      style = Paint.Style.FILL
    }
    canvas.drawRoundRect(36f, y, 559f, y + 76f, 10f, 10f, boxPaint)
    canvas.drawText("TOTAL GASTOS DEL MES", 52f, y + 24f, labelPaint)
    canvas.drawText("CUOTA EN DIVISAS (USD)", 230f, y + 24f, labelPaint)
    canvas.drawText("EQUIVALENTE EN BOLÍVARES", 400f, y + 24f, labelPaint)

    val bigAmountPaint = Paint(valuePaint).apply {
      textSize = 16f
      color = Color.rgb(9, 76, 102)
    }
    canvas.drawText("$${String.format(Locale.US, "%.2f", invoice.totalCondoUsd)}", 52f, y + 52f, bigAmountPaint)
    canvas.drawText("$${String.format(Locale.US, "%.2f", invoice.amountDueUsd)}", 230f, y + 52f, bigAmountPaint)
    canvas.drawText("Bs. ${String.format(Locale.US, "%.2f", invoice.amountDueBs)}", 400f, y + 52f, bigAmountPaint)

    y += 106f
    canvas.drawText("RELACIÓN DE GASTOS DEL CONDOMINIO (${invoice.billingPeriod})", 36f, y, valuePaint)
    y += 14f
    canvas.drawLine(36f, y, 559f, y, linePaint)
    y += 22f

    if (expenses.isEmpty()) {
      canvas.drawText("Sin detalle individual registrado para este período.", 36f, y, bodyPaint)
      y += 22f
    } else {
      expenses.take(12).forEach { exp ->
        canvas.drawText("${exp.title} (${exp.category})", 36f, y, bodyPaint)
        canvas.drawText("$${String.format(Locale.US, "%.2f", exp.amountUsd)}", 475f, y, bodyPaint)
        y += 20f
      }
      if (expenses.size > 12) {
        canvas.drawText("... y ${expenses.size - 12} conceptos adicionales.", 36f, y, labelPaint)
        y += 20f
      }
    }

    y += 12f
    canvas.drawLine(36f, y, 559f, y, linePaint)
    y += 26f

    if (approvedPayment != null) {
      canvas.drawText("DETALLE DEL PAGO CONCILIADO", 36f, y, valuePaint)
      y += 20f
      canvas.drawText("Método: ${approvedPayment.method.uppercase(Locale.US)} · Banco: ${approvedPayment.bankName}", 36f, y, bodyPaint)
      y += 18f
      canvas.drawText("Referencia: ${approvedPayment.referenceNumber} · Fecha: ${approvedPayment.paymentDate}", 36f, y, bodyPaint)
      y += 18f
      val currSym = if (approvedPayment.currency == "VES") "Bs." else "$"
      canvas.drawText("Monto Reportado: $currSym ${String.format(Locale.US, "%.2f", approvedPayment.amountReported)}", 36f, y, bodyPaint)
      y += 26f
    }

    val generatedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    canvas.drawText("Documento generado digitalmente por CondoZulia el $generatedAt.", 36f, 800f, labelPaint)

    document.finishPage(page)

    val safeName = "Recibo_${invoice.receiptNumber}.pdf".replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
    val cacheDir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val cacheFile = File(cacheDir, safeName)
    FileOutputStream(cacheFile).use { out -> document.writeTo(out) }

    var savedDownloads = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      runCatching {
        val values =
          ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CondoZulia")
          }
        val resolver = context.contentResolver
        val dlUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (dlUri != null) {
          resolver.openOutputStream(dlUri)?.use { dlOut ->
            cacheFile.inputStream().use { it.copyTo(dlOut) }
          }
          savedDownloads = true
        }
      }
    }

    document.close()

    val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
    GeneratedReceiptResult(shareUri = shareUri, savedToDownloads = savedDownloads, fileName = safeName)
  }

  fun generateVecinoReceiptPdf(
    context: Context,
    condominio: com.example.model.Condominio,
    vecino: com.example.model.Vecino,
    periodoMes: String,
    gastos: List<com.example.model.GastoMensual>,
    pago: com.example.model.PagoReportado?,
  ): Result<GeneratedReceiptResult> {
    val totalMesUsd = gastos.sumOf { it.montoUsd }
    val syntheticInvoice =
      UnitInvoice(
        id = pago?.id ?: vecino.id,
        condoId = condominio.id,
        unitId = vecino.id,
        unitNumber = vecino.unidad,
        billingPeriod = periodoMes,
        alicuotaPct = vecino.alicuotaPct,
        totalCondoUsd = totalMesUsd,
        amountDueUsd = pago?.montoEquivalenteUsd ?: vecino.saldoPendienteUsd,
        bcvRate = condominio.tasaBcv,
        amountDueBs =
          if (pago != null && pago.moneda == "VES") pago.montoReportado
          else vecino.saldoPendienteBs,
        status =
          when (pago?.estado ?: vecino.estadoSolvencia) {
            "solvente", "aprobado" -> "paid"
            "en_revision", "pendiente_revision" -> "in_review"
            "moroso" -> "overdue"
            else -> "pending"
          },
        receiptNumber =
          pago?.numeroReferencia?.let { "REF-$it" } ?: "CUOTA-${vecino.unidad.take(8)}-$periodoMes",
      )
    val mappedCondo =
      Condominium(
        id = condominio.id,
        name = condominio.nombre,
        address = condominio.direccion,
        joinCode = condominio.codigoAcceso,
        bcvRate = condominio.tasaBcv,
      )
    val mappedExpenses =
      gastos.map {
        MonthlyExpense(
          id = it.id,
          condoId = it.condominioId,
          billingPeriod = it.periodoMes,
          title = it.concepto,
          category = it.categoria,
          amountUsd = it.montoUsd,
          amountBs = it.montoBs,
        )
      }
    val mappedPayment =
      pago?.let {
        PaymentReport(
          id = it.id,
          condoId = it.condominioId,
          unitId = it.vecinoId,
          unitNumber = it.unidad,
          billingPeriod = it.periodoMes,
          method = it.metodoPago,
          bankName = it.bancoOrigen,
          referenceNumber = it.numeroReferencia,
          senderPhone = it.telefonoEmisor,
          currency = it.moneda,
          amountReported = it.montoReportado,
          amountUsdEquivalent = it.montoEquivalenteUsd,
          bcvRateUsed = it.tasaBcvUsada,
          paymentDate = it.fechaPago,
          status =
            when (it.estado) {
              "aprobado" -> "approved"
              "rechazado" -> "rejected"
              else -> "pending_review"
            },
        )
      }
    return generateInvoiceReceiptPdf(
      context = context,
      condo = mappedCondo,
      invoice = syntheticInvoice,
      expenses = mappedExpenses,
      approvedPayment = mappedPayment,
    )
  }
}
