package com.kbul.spicycrab.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kbul.spicycrab.ui.common.DateTimeField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSubstanceSheet(
    builtInKeys: List<String>,
    onSave: (key: String, amountInt: Int, timestampEpoch: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedKey by remember { mutableStateOf(builtInKeys.firstOrNull() ?: "") }
    var customKey by remember { mutableStateOf("") }
    var useCustom by remember { mutableStateOf(builtInKeys.isEmpty()) }
    var amountText by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val effectiveKey = (if (useCustom) customKey else selectedKey).trim().lowercase()
    val amount = amountText.toIntOrNull() ?: 0
    val canSave = effectiveKey.isNotBlank() && amount > 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Log substance", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Records a timed amount (alcohol g, caffeine mg, nicotine mg, or your own). " +
                    "Amounts are whole numbers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                builtInKeys.forEach { key ->
                    FilterChip(
                        selected = !useCustom && selectedKey == key,
                        onClick = { useCustom = false; selectedKey = key },
                        label = { Text(key) },
                    )
                }
                FilterChip(
                    selected = useCustom,
                    onClick = { useCustom = true },
                    label = { Text("custom") },
                )
            }

            if (useCustom) {
                OutlinedTextField(
                    value = customKey,
                    onValueChange = { customKey = it },
                    label = { Text("Substance name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { txt -> amountText = txt.filter { it.isDigit() } },
                label = { Text("Amount (whole number)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            DateTimeField(epochMillis = timestamp, onChange = { timestamp = it })

            Button(
                onClick = { onSave(effectiveKey, amount, timestamp) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Log") }
        }
    }
}
