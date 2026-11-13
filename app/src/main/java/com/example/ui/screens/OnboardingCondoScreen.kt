package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingCondoScreen(
  isBusy: Boolean,
  onCreateCondo: (
    name: String,
    address: String,
    bcvRate: Double,
    pagoMovilBank: String,
    pagoMovilPhone: String,
    pagoMovilId: String,
    transferBank: String,
    transferAccount: String,
    includeTemplate: Boolean,
  ) -> Unit,
  onJoinCondo: (code: String) -> Unit,
  onCancel: (() -> Unit)? = null,
) {
  var selectedTab by remember { mutableIntStateOf(0) }

  // Create Condo state
  var condoName by remember { mutableStateOf("Residencias Lago Mar") }
  var address by remember { mutableStateOf("Av. El Milagro, Maracaibo") }
  var bcvRateText by remember { mutableStateOf("46.50") }
  var pmBank by remember { mutableStateOf("Banesco") }
  var pmPhone by remember { mutableStateOf("0414-6001122") }
  var pmId by remember { mutableStateOf("J-30123456-7") }
  var trBank by remember { mutableStateOf("Mercantil") }
  var trAccount by remember { mutableStateOf("0105-0011-22-1234567890") }
  var includeTemplate by remember { mutableStateOf(true) }

  // Join Condo state
  var joinCode by remember { mutableStateOf("") }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().widthIn(max = 580.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "Configurar Condominio",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text = "Crea tu conjunto residencial como Junta de Condominio o únete con el código si eres vecino o vigilante.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      TabRow(selectedTabIndex = selectedTab) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Crear (Junta)") },
          icon = { Icon(Icons.Filled.AddBusiness, contentDescription = null) },
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Unirse con Código") },
          icon = { Icon(Icons.Filled.Key, contentDescription = null) },
        )
      }

      if (selectedTab == 0) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OutlinedTextField(
              value = condoName,
              onValueChange = { condoName = it },
              label = { Text("Nombre del Conjunto / Edificio") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("condo_name_input"),
            )
            OutlinedTextField(
              value = address,
              onValueChange = { address = it },
              label = { Text("Dirección (Sector / Avenida)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = bcvRateText,
              onValueChange = { bcvRateText = it },
              label = { Text("Tasa BCV (Bs. por 1 USD)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("condo_bcv_input"),
            )
            Text(
              text = "Datos de Recepción (Pago Móvil y Transferencia)",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = pmBank,
                onValueChange = { pmBank = it },
                label = { Text("Banco Pago Móvil") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = pmPhone,
                onValueChange = { pmPhone = it },
                label = { Text("Teléfono") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
            OutlinedTextField(
              value = pmId,
              onValueChange = { pmId = it },
              label = { Text("RIF / Cédula Pago Móvil") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = trBank,
                onValueChange = { trBank = it },
                label = { Text("Banco Transferencia") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = trAccount,
                onValueChange = { trAccount = it },
                label = { Text("Cuenta Bancaria") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = "Incluir 6 apartamentos base",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Pre-carga Torre A/B con alícuotas sumando 100% para dividir recibos de inmediato.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = includeTemplate,
                onCheckedChange = { includeTemplate = it },
              )
            }

            Button(
              onClick = {
                val rate = bcvRateText.replace(",", ".").toDoubleOrNull() ?: 46.50
                onCreateCondo(
                  condoName,
                  address,
                  rate,
                  pmBank,
                  pmPhone,
                  pmId,
                  trBank,
                  trAccount,
                  includeTemplate,
                )
              },
              enabled = !isBusy && condoName.trim().length >= 2,
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_condo_button"),
            ) {
              Text(if (isBusy) "Creando..." else "Crear Condominio")
            }
          }
        }
      } else {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = "Ingresa el código de invitación compartido por la Junta de Condominio:",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = joinCode,
              onValueChange = { joinCode = it.uppercase() },
              label = { Text("Código de Condominio (ej. RESI1234)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("join_code_input"),
            )
            Button(
              onClick = { onJoinCondo(joinCode) },
              enabled = !isBusy && joinCode.trim().length >= 4,
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("join_condo_button"),
            ) {
              Text(if (isBusy) "Conectando..." else "Unirme al Condominio")
            }
          }
        }
      }

      if (onCancel != null) {
        OutlinedButton(
          onClick = onCancel,
          modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
          Text("Volver")
        }
      }
    }
  }
}
