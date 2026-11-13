package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Condominio(
  @DocumentId val id: String = "",
  val nombre: String = "",
  val direccion: String = "",
  val codigoAcceso: String = "",
  val tasaBcv: Double = 46.50,
  val bancoPagoMovil: String = "",
  val telefonoPagoMovil: String = "",
  val rifPagoMovil: String = "",
  val bancoTransferencia: String = "",
  val cuentaTransferencia: String = "",
  val adminUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Vecino(
  @DocumentId val id: String = "",
  val condominioId: String = "",
  val unidad: String = "",
  val nombreCompleto: String = "",
  val telefono: String = "",
  val alicuotaPct: Double = 0.0,
  val saldoPendienteUsd: Double = 0.0,
  val saldoPendienteBs: Double = 0.0,
  val estadoSolvencia: String = "solvente",
  val codigoAcceso: String = "",
  val residentUid: String = "",
  val adminUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class GastoMensual(
  @DocumentId val id: String = "",
  val condominioId: String = "",
  val periodoMes: String = "",
  val concepto: String = "",
  val categoria: String = "",
  val montoUsd: Double = 0.0,
  val montoBs: Double = 0.0,
  val tasaBcvAplicada: Double = 0.0,
  val distribuido: Boolean = false,
  val adminUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PagoReportado(
  @DocumentId val id: String = "",
  val condominioId: String = "",
  val vecinoId: String = "",
  val unidad: String = "",
  val periodoMes: String = "",
  val metodoPago: String = "pago_movil",
  val bancoOrigen: String = "",
  val numeroReferencia: String = "",
  val telefonoEmisor: String = "",
  val moneda: String = "VES",
  val montoReportado: Double = 0.0,
  val montoEquivalenteUsd: Double = 0.0,
  val tasaBcvUsada: Double = 0.0,
  val fechaPago: String = "",
  val comprobanteBase64: String = "",
  val estado: String = "pendiente_revision",
  val notaConciliacion: String = "",
  val adminUid: String = "",
  val reportadoPorUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
