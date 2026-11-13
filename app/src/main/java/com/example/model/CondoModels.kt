package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CondoRole(val key: String, val label: String) {
  JUNTA("junta", "Junta"),
  VECINO("vecino", "Mi Apto"),
  GARITA("garita", "Garita QR");

  companion object {
    fun fromKey(key: String?): CondoRole = entries.firstOrNull { it.key == key } ?: JUNTA
  }
}

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val activeCondoId: String = "",
  val activeUnitId: String = "",
  val activeRole: String = CondoRole.JUNTA.key,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Condominium(
  @DocumentId val id: String = "",
  val name: String = "",
  val address: String = "",
  val joinCode: String = "",
  val bcvRate: Double = 46.50,
  val pagoMovilBank: String = "",
  val pagoMovilPhone: String = "",
  val pagoMovilId: String = "",
  val transferBank: String = "",
  val transferAccount: String = "",
  val adminUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CondoUnit(
  @DocumentId val id: String = "",
  val condoId: String = "",
  val unitNumber: String = "",
  val ownerLabel: String = "",
  val alicuotaPct: Double = 0.0,
  val residentUid: String = "",
  val adminUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MonthlyExpense(
  @DocumentId val id: String = "",
  val condoId: String = "",
  val billingPeriod: String = "",
  val title: String = "",
  val category: String = "",
  val amountUsd: Double = 0.0,
  val amountBs: Double = 0.0,
  val adminUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UnitInvoice(
  @DocumentId val id: String = "",
  val condoId: String = "",
  val unitId: String = "",
  val unitNumber: String = "",
  val billingPeriod: String = "",
  val alicuotaPct: Double = 0.0,
  val totalCondoUsd: Double = 0.0,
  val amountDueUsd: Double = 0.0,
  val bcvRate: Double = 0.0,
  val amountDueBs: Double = 0.0,
  val status: String = "pending",
  val receiptNumber: String = "",
  val paidAtMs: Long = 0L,
  val adminUid: String = "",
  val residentUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PaymentReport(
  @DocumentId val id: String = "",
  val condoId: String = "",
  val invoiceId: String = "",
  val unitId: String = "",
  val unitNumber: String = "",
  val billingPeriod: String = "",
  val method: String = "pago_movil",
  val bankName: String = "",
  val referenceNumber: String = "",
  val senderPhone: String = "",
  val currency: String = "VES",
  val amountReported: Double = 0.0,
  val amountUsdEquivalent: Double = 0.0,
  val bcvRateUsed: Double = 0.0,
  val paymentDate: String = "",
  val receiptPreviewBase64: String = "",
  val status: String = "pending_review",
  val reviewNote: String = "",
  val adminUid: String = "",
  val reportedByUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class VisitorPass(
  @DocumentId val id: String = "",
  val condoId: String = "",
  val unitId: String = "",
  val unitNumber: String = "",
  val visitorName: String = "",
  val visitorCedula: String = "",
  val vehiclePlate: String = "",
  val visitType: String = "Visita",
  val qrToken: String = "",
  val status: String = "active",
  val checkedInAtMs: Long = 0L,
  val checkedOutAtMs: Long = 0L,
  val adminUid: String = "",
  val createdByUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PaymentReminder(
  @DocumentId val id: String = "",
  val condoId: String = "",
  val unitId: String = "",
  val unitNumber: String = "",
  val invoiceId: String = "",
  val billingPeriod: String = "",
  val title: String = "",
  val message: String = "",
  val amountDueUsd: Double = 0.0,
  val amountDueBs: Double = 0.0,
  val isRead: Boolean = false,
  val adminUid: String = "",
  val targetUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
