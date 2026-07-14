package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BotConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentConfig: BotConfig,
    onDismiss: () -> Unit,
    onSave: (
        symbol: String,
        shortSma: Int,
        longSma: Int,
        useSandbox: Boolean,
        apiKey: String,
        apiSecret: String,
        useAiSignal: Boolean,
        strategy: String,
        stopLossPct: Double,
        takeProfitPct: Double
    ) -> Unit
) {
    var selectedSymbol by remember { mutableStateOf(currentConfig.symbol) }
    var shortSmaStr by remember { mutableStateOf(currentConfig.shortSma.toString()) }
    var longSmaStr by remember { mutableStateOf(currentConfig.longSma.toString()) }
    var useSandbox by remember { mutableStateOf(currentConfig.useSandbox) }
    var useAiSignal by remember { mutableStateOf(currentConfig.useAiSignal) }
    var selectedStrategy by remember { mutableStateOf(currentConfig.strategy.ifEmpty { "SMA_CROSSOVER" }) }
    var stopLossStr by remember { mutableStateOf(currentConfig.stopLossPct.toString()) }
    var takeProfitStr by remember { mutableStateOf(currentConfig.takeProfitPct.toString()) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var apiSecret by remember { mutableStateOf(currentConfig.apiSecret) }

    var symbolExpanded by remember { mutableStateOf(false) }
    var strategyExpanded by remember { mutableStateOf(false) }
    var showApiKeys by remember { mutableStateOf(false) }

    val symbols = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "ADAUSDT", "DOGEUSDT")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trading Bot Configurations") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Symbol Selection
                Column {
                    Text("Trading Symbol / Pair", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = symbolExpanded,
                        onExpandedChange = { symbolExpanded = !symbolExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSymbol,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = symbolExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = symbolExpanded,
                            onDismissRequest = { symbolExpanded = false }
                        ) {
                            symbols.forEach { symbol ->
                                DropdownMenuItem(
                                    text = { Text(symbol) },
                                    onClick = {
                                        selectedSymbol = symbol
                                        symbolExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // SMA Periods
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = shortSmaStr,
                        onValueChange = { if (it.all { char -> char.isDigit() }) shortSmaStr = it },
                        label = { Text("Short SMA Window") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = longSmaStr,
                        onValueChange = { if (it.all { char -> char.isDigit() }) longSmaStr = it },
                        label = { Text("Long SMA Window") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Sandbox & AI Filters
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Paper Trading / Sandbox Mode", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Simulate execution with zero risk.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useSandbox,
                            onCheckedChange = { useSandbox = it }
                        )
                    }

                    HorizontalDivider()

                    Column {
                        Text("Execution Strategy", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = strategyExpanded,
                            onExpandedChange = { strategyExpanded = !strategyExpanded }
                        ) {
                            OutlinedTextField(
                                value = when (selectedStrategy) {
                                    "SMA_CROSSOVER" -> "SMA Crossover"
                                    "RSI_MEAN_REVERSION" -> "RSI Mean Reversion"
                                    "MACHINE_LEARNING" -> "Local Machine Learning"
                                    else -> "SMA Crossover"
                                },
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = strategyExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = strategyExpanded,
                                onDismissRequest = { strategyExpanded = false }
                            ) {
                                listOf(
                                    "SMA_CROSSOVER" to "SMA Crossover (Moving Average)",
                                    "RSI_MEAN_REVERSION" to "RSI Mean Reversion",
                                    "MACHINE_LEARNING" to "Local Machine Learning"
                                ).forEach { (stratKey, stratName) ->
                                    DropdownMenuItem(
                                        text = { Text(stratName) },
                                        onClick = {
                                            selectedStrategy = stratKey
                                            strategyExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = stopLossStr,
                            onValueChange = { stopLossStr = it },
                            label = { Text("Stop Loss (X %)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = takeProfitStr,
                            onValueChange = { takeProfitStr = it },
                            label = { Text("Take Profit (Y %)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Real API Keys Section (Visible only when not in sandbox, or as optional field)
                if (!useSandbox) {
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Security Alert",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Security Warning: Keys are stored locally on device.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Binance API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = apiSecret,
                            onValueChange = { apiSecret = it },
                            label = { Text("Binance Secret Key") },
                            singleLine = true,
                            visualTransformation = if (showApiKeys) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showApiKeys = !showApiKeys }) {
                                    Icon(
                                        imageVector = if (showApiKeys) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Keys Visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sSma = shortSmaStr.toIntOrNull() ?: 5
                    val lSma = longSmaStr.toIntOrNull() ?: 15
                    val slVal = stopLossStr.toDoubleOrNull() ?: 2.0
                    val tpVal = takeProfitStr.toDoubleOrNull() ?: 5.0
                    onSave(
                        selectedSymbol,
                        sSma,
                        lSma,
                        useSandbox,
                        apiKey,
                        apiSecret,
                        useAiSignal,
                        selectedStrategy,
                        slVal,
                        tpVal
                    )
                }
            ) {
                Text("Apply Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
