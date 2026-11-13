package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QrAccessTest {

  @Test
  fun generateQrBitmap_andParseResidentPayload_roundTripsCleanly() {
    val payload =
      QrUtils.buildResidentAccessQrPayload(
        condominioId = "cond_zulia123",
        codigoAcceso = "LAGO4A1B",
        vecinoId = "vec_apt2b",
        unidad = "Torre A - 2B",
        codigoVecino = "CZ-91AF02",
      )

    val bitmap = QrUtils.generateQrBitmap(payload, sizePx = 256)
    assertNotNull(bitmap)
    assertEquals(256, bitmap!!.width)
    assertEquals(256, bitmap.height)

    val parsed = QrUtils.parseResidentAccessQrPayload(payload)
    assertNotNull(parsed)
    assertEquals("cond_zulia123", parsed!!.condominioId)
    assertEquals("LAGO4A1B", parsed.codigoAcceso)
    assertEquals("vec_apt2b", parsed.vecinoId)
    assertEquals("Torre A - 2B", parsed.unidad)
    assertEquals("CZ-91AF02", parsed.codigoVecino)
  }

  @Test
  fun parseResidentAccessQrPayload_supportsPipeFormat() {
    val parsed = QrUtils.parseResidentAccessQrPayload("cond_123|CODE7788|vec_999")
    assertNotNull(parsed)
    assertEquals("cond_123", parsed!!.condominioId)
    assertEquals("CODE7788", parsed.codigoAcceso)
    assertEquals("vec_999", parsed.vecinoId)
    assertTrue(parsed.codigoVecino.isEmpty())
  }
}
