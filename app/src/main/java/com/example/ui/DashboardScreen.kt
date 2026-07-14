package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Trade
import com.example.data.BacktestResult
import com.example.data.BacktestStrategy
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TradingChart
import com.example.ui.components.RealTimePriceTracker
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: BotViewModel,
    modifier: Modifier = Modifier
) {
    val botConfig by viewModel.botConfig.collectAsStateWithLifecycle()
    val candles by viewModel.candles.collectAsStateWithLifecycle()
    val smaShort by viewModel.smaShort.collectAsStateWithLifecycle()
    val smaLong by viewModel.smaLong.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val isFetching by viewModel.isFetching.collectAsStateWithLifecycle()
    val aiAnalysis by viewModel.aiAnalysis.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val tradesHistory by viewModel.tradesHistory.collectAsStateWithLifecycle()

    val backtestResult by viewModel.backtestResult.collectAsStateWithLifecycle()
    val isBacktesting by viewModel.isBacktesting.collectAsStateWithLifecycle()
    val backtestError by viewModel.backtestError.collectAsStateWithLifecycle()

    val trackerSymbol by viewModel.trackerSymbol.collectAsStateWithLifecycle()
    val trackerCandles by viewModel.trackerCandles.collectAsStateWithLifecycle()
    val trackerIsLoading by viewModel.trackerIsLoading.collectAsStateWithLifecycle()
    val trackerBids by viewModel.trackerBids.collectAsStateWithLifecycle()
    val trackerAsks by viewModel.trackerAsks.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Live Scanner, 1 = Live Tracker, 2 = Backtester, 3 = Platforms
    var backtestSymbol by remember { mutableStateOf("BTCUSDT") }
    var backtestTimeframe by remember { mutableStateOf("1h") } // "1m", "5m", "15m", "1h", "1d"
    var backtestStrategy by remember { mutableStateOf(BacktestStrategy.MACHINE_LEARNING) }
    var backtestStopLoss by remember { mutableStateOf("2.0") }
    var backtestTakeProfit by remember { mutableStateOf("5.0") }
    var backtestShortSma by remember { mutableStateOf("5") }
    var backtestLongSma by remember { mutableStateOf("15") }
    var symbolExpanded by remember { mutableStateOf(false) }
    var timeframeExpanded by remember { mutableStateOf(false) }
    var strategyExpanded by remember { mutableStateOf(false) }

    var showSettings by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var mt5Server by remember(botConfig) { mutableStateOf(botConfig.mt5Server) }
    var mt5Login by remember(botConfig) { mutableStateOf(botConfig.mt5Login) }
    var mt5Password by remember(botConfig) { mutableStateOf(botConfig.mt5Password) }
    var tvWebhookUrl by remember(botConfig) { mutableStateOf(botConfig.tradingViewWebhookUrl) }
    var tvToken by remember(botConfig) { mutableStateOf(botConfig.tradingViewToken) }
    var tvEnabled by remember(botConfig) { mutableStateOf(botConfig.tradingViewEnabled) }
    var mt5PasswordVisible by remember { mutableStateOf(false) }

    // Calculated portfolio values
    val latestPrice = candles.lastOrNull()?.close ?: 0.0
    val portfolioWorth = botConfig.balanceUSDT + (botConfig.balanceAsset * latestPrice)

    // Pulse animation for Active Status indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Bot Logo",
                            tint = Color(0xFF0061A4),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Crypto AlgoBot",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color(0xFF1A1C1E)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure Bot",
                            tint = Color(0xFF64748B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF7F9FC)
                )
            )
        },
        containerColor = Color(0xFFF7F9FC)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. App Header + Status Banner
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Active Pair: ${botConfig.symbol.uppercase()}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1A1C1E)
                            )
                            Text(
                                text = "SMA Windows: (${botConfig.shortSma}, ${botConfig.longSma})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFF64748B)
                            )
                            val strategyLabel = when (botConfig.strategy) {
                                "SMA_CROSSOVER" -> "SMA Crossover"
                                "RSI_MEAN_REVERSION" -> "RSI Mean Reversion"
                                "MACHINE_LEARNING" -> "Local Machine Learning"
                                else -> "SMA Crossover"
                            }
                            Text(
                                text = "Strategy: $strategyLabel",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (botConfig.isRunning) Color(0xFFDCFCE7)
                                    else Color(0xFFF1F3F5)
                                )
                                .border(
                                    1.dp,
                                    if (botConfig.isRunning) Color(0xFF16A34A).copy(alpha = alphaPulse)
                                    else Color(0xFFE2E8F0),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (botConfig.isRunning) Color(0xFF16A34A)
                                            else Color(0xFF64748B)
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (botConfig.isRunning) "ACTIVE" else "INACTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (botConfig.isRunning) Color(0xFF16A34A) else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE1E5EB))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 0) Color.White else Color.Transparent)
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = "Scanner",
                                    tint = if (selectedTab == 0) Color(0xFF0061A4) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Scanner",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedTab == 0) Color(0xFF1A1C1E) else Color(0xFF64748B)
                                    )
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 1) Color.White else Color.Transparent)
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Tracker",
                                    tint = if (selectedTab == 1) Color(0xFF0061A4) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Tracker",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedTab == 1) Color(0xFF1A1C1E) else Color(0xFF64748B)
                                    )
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 2) Color.White else Color.Transparent)
                                .clickable { selectedTab = 2 }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Science,
                                    contentDescription = "Backtester",
                                    tint = if (selectedTab == 2) Color(0xFF0061A4) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Backtest",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedTab == 2) Color(0xFF1A1C1E) else Color(0xFF64748B)
                                    )
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 3) Color.White else Color.Transparent)
                                .clickable { selectedTab = 3 }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "Platform Links",
                                    tint = if (selectedTab == 3) Color(0xFF0061A4) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Platforms",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedTab == 3) Color(0xFF1A1C1E) else Color(0xFF64748B)
                                    )
                                )
                            }
                        }
                    }
                }

                if (selectedTab == 0) {
                    // 2. Simulated Wallet Card (Clean Minimal Card Style)
                    item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE1E2E5), RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "SIMULATED PORTFOLIO VALUATION",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Wallet Balance",
                                    tint = Color(0xFF0061A4),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "${String.format(Locale.US, "%,.2f", portfolioWorth)} USDT",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1C1E)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Simulated cash", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                    Text(
                                        "${String.format(Locale.US, "%,.2f", botConfig.balanceUSDT)} USDT",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1C1E)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Simulated holdings", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                    Text(
                                        "${String.format(Locale.US, "%.5f", botConfig.balanceAsset)} ${botConfig.symbol.replace("USDT", "")}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Main Chart Canvas Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE1E2E5))
                    ) {
                        TradingChart(
                            candles = candles,
                            smaShort = smaShort,
                            smaLong = smaLong,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 4. Interactive Execution Control Board
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Play/Pause Core Bot execution
                        Button(
                            onClick = { viewModel.toggleBot() },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(50.dp)
                                .testTag("run_bot_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (botConfig.isRunning) Color(0xFFFEE2E2) else Color(0xFFD1E4FF),
                                contentColor = if (botConfig.isRunning) Color(0xFF991B1B) else Color(0xFF001D36)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = if (botConfig.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (botConfig.isRunning) "Stop" else "Start"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (botConfig.isRunning) "STOP BOT" else "START BOT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // Instant Refresh tick
                        FilledIconButton(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.tickBot()
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("refresh_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFF1F3F5),
                                contentColor = Color(0xFF1A1C1E)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF1A1C1E)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync ticker",
                                    tint = Color(0xFF1A1C1E)
                                )
                            }
                        }

                        // Reset simulation data
                        FilledIconButton(
                            onClick = { viewModel.resetSimulation() },
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("reset_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFFEE2E2),
                                contentColor = Color(0xFF991B1B)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Reset simulation data",
                                tint = Color(0xFF991B1B)
                            )
                        }
                    }
                }

                // Manual paper transactions panel
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.manualTrade("BUY") },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("manual_buy_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF16A34A)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF16A34A))
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = "Buy icon", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manual Buy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.manualTrade("SELL") },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("manual_sell_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFDC2626)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Default.TrendingDown, contentDescription = "Sell icon", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manual Sell", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 5. Gemini AI Technical sentiment analyzer
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F0FF)),
                        border = BorderStroke(1.dp, Color(0xFFD1E4FF))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome, // AI icon
                                        contentDescription = "Gemini AI",
                                        tint = Color(0xFF0061A4),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Gemini AI Advisor",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF001D36)
                                    )
                                }

                                if (!isAiLoading) {
                                    TextButton(
                                        onClick = { viewModel.runGeminiAnalysis() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF0061A4))
                                    ) {
                                        Text("Analyze", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (isAiLoading) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF0061A4)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("AI is parsing indicators...", color = Color(0xFF0061A4), fontSize = 12.sp)
                                }
                            } else {
                                Text(
                                    text = aiAnalysis ?: "Tap Analyze to let Gemini analyze visible price structures and output market signals directly.",
                                    color = if (aiAnalysis != null) Color(0xFF001D36) else Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // 6. Real-time Log Feed / Terminal Component
                item {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Logs",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "SYSTEM EXECUTION TERMINAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 1.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF2F3F7))
                                .border(1.dp, Color(0xFFE1E2E5), RoundedCornerShape(20.dp))
                                .padding(12.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                              ) {
                                items(logs) { log ->
                                    Text(
                                        text = log,
                                        color = when {
                                            log.contains("BUY") -> Color(0xFF16A34A)
                                            log.contains("SELL") -> Color(0xFFDC2626)
                                            log.contains("Error") -> Color(0xFFB91C1C)
                                            else -> Color(0xFF475569)
                                        },
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 7. Completed Trade History Panel
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Trade history",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "COMPLETED TRANSACTION LOGS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.sp
                        )
                    }
                }

                if (tradesHistory.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE1E2E5))
                        ) {
                            Text(
                                text = "No completed trades on record. Run the bot or trigger a manual trade above.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                textAlign = TextAlign.Center,
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                } // End if (selectedTab == 0)

                if (selectedTab == 1) {
                    item {
                        RealTimePriceTracker(
                            symbol = trackerSymbol,
                            candles = trackerCandles,
                            isLoading = trackerIsLoading,
                            bids = trackerBids,
                            asks = trackerAsks,
                            onSymbolSelected = { newSym ->
                                viewModel.setTrackerSymbol(newSym)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (selectedTab == 2) {
                    // Backtester Configuration Form Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE1E2E5))
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "BACKTEST CONFIGURATION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0061A4),
                                    letterSpacing = 1.sp
                                )

                                // Symbol and Timeframe inputs
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    // Symbol dropdown
                                    Box(modifier = Modifier.weight(1.2f)) {
                                        OutlinedTextField(
                                            value = backtestSymbol,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Symbol") },
                                            trailingIcon = { IconButton(onClick = { symbolExpanded = true }) { Icon(Icons.Default.ArrowDropDown, "Open dropdown") } },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DropdownMenu(
                                            expanded = symbolExpanded,
                                            onDismissRequest = { symbolExpanded = false }
                                        ) {
                                            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "ADAUSDT", "DOGEUSDT").forEach { sym ->
                                                DropdownMenuItem(
                                                    text = { Text(sym) },
                                                    onClick = {
                                                        backtestSymbol = sym
                                                        symbolExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Timeframe dropdown
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = backtestTimeframe,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Timeframe") },
                                            trailingIcon = { IconButton(onClick = { timeframeExpanded = true }) { Icon(Icons.Default.ArrowDropDown, "Open dropdown") } },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DropdownMenu(
                                            expanded = timeframeExpanded,
                                            onDismissRequest = { timeframeExpanded = false }
                                        ) {
                                            listOf("1m", "5m", "15m", "1h", "1d").forEach { tf ->
                                                DropdownMenuItem(
                                                    text = { Text(tf) },
                                                    onClick = {
                                                        backtestTimeframe = tf
                                                        timeframeExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Strategy Dropdown
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = when (backtestStrategy) {
                                            BacktestStrategy.SMA_CROSSOVER -> "SMA Crossover"
                                            BacktestStrategy.MACHINE_LEARNING -> "Local Machine Learning"
                                            BacktestStrategy.RSI_MEAN_REVERSION -> "RSI Mean Reversion"
                                        },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Trading Strategy") },
                                        trailingIcon = { IconButton(onClick = { strategyExpanded = true }) { Icon(Icons.Default.ArrowDropDown, "Open dropdown") } },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DropdownMenu(
                                        expanded = strategyExpanded,
                                        onDismissRequest = { strategyExpanded = false }
                                    ) {
                                        listOf(
                                            BacktestStrategy.SMA_CROSSOVER to "SMA Crossover",
                                            BacktestStrategy.MACHINE_LEARNING to "Local Machine Learning",
                                            BacktestStrategy.RSI_MEAN_REVERSION to "RSI Mean Reversion"
                                        ).forEach { (strat, name) ->
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    backtestStrategy = strat
                                                    strategyExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // SMA Inputs (for SMA and ML strategies)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = backtestShortSma,
                                        onValueChange = { if (it.all { c -> c.isDigit() }) backtestShortSma = it },
                                        label = { Text("Short SMA") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = backtestLongSma,
                                        onValueChange = { if (it.all { c -> c.isDigit() }) backtestLongSma = it },
                                        label = { Text("Long SMA") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Stop Loss % & Take Profit %
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = backtestStopLoss,
                                        onValueChange = { backtestStopLoss = it },
                                        label = { Text("Stop Loss (X %)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = backtestTakeProfit,
                                        onValueChange = { backtestTakeProfit = it },
                                        label = { Text("Take Profit (Y %)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Run Backtest Button
                                Button(
                                    onClick = {
                                        viewModel.runBacktest(
                                            symbol = backtestSymbol,
                                            timeframe = backtestTimeframe,
                                            strategy = backtestStrategy,
                                            shortSma = backtestShortSma.toIntOrNull() ?: 5,
                                            longSma = backtestLongSma.toIntOrNull() ?: 15,
                                            stopLossPct = backtestStopLoss.toDoubleOrNull() ?: 2.0,
                                            takeProfitPct = backtestTakeProfit.toDoubleOrNull() ?: 5.0
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_backtest_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4), contentColor = Color.White),
                                    shape = RoundedCornerShape(14.dp),
                                    enabled = !isBacktesting
                                ) {
                                    if (isBacktesting) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Running Simulation...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.Science, contentDescription = "Run")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("RUN BACKTEST SIMULATION", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Backtest Error Alert
                    if (backtestError != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, "Error", tint = Color.Red)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(backtestError ?: "Error running backtest.", color = Color.Red, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Backtest Results Card
                    backtestResult?.let { result ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE1E2E5))
                            ) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "BACKTEST SIMULATION METRICS",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B),
                                            letterSpacing = 1.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(if (result.totalReturnPct >= 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (result.totalReturnPct >= 0) "+${String.format(Locale.US, "%.2f", result.totalReturnPct)}%" else "${String.format(Locale.US, "%.2f", result.totalReturnPct)}%",
                                                color = if (result.totalReturnPct >= 0) Color(0xFF16A34A) else Color(0xFFDC2626),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    HorizontalDivider()

                                    // Balances row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Initial Capital", fontSize = 11.sp, color = Color(0xFF64748B))
                                            Text("${String.format(Locale.US, "%,.2f", result.initialBalance)} USDT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Final Capital", fontSize = 11.sp, color = Color(0xFF64748B))
                                            Text("${String.format(Locale.US, "%,.2f", result.finalBalance)} USDT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    HorizontalDivider()

                                    // Grid of stats
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Max Drawdown", fontSize = 11.sp, color = Color(0xFF64748B))
                                            Text("${String.format(Locale.US, "%.2f", result.maxDrawdownPct)}%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Win Rate", fontSize = 11.sp, color = Color(0xFF64748B))
                                            Text("${String.format(Locale.US, "%.1f", result.winRatePct)}%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Sharpe Ratio", fontSize = 11.sp, color = Color(0xFF64748B))
                                            Text(String.format(Locale.US, "%.2f", result.sharpeRatio), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            Text("Total Trades", fontSize = 11.sp, color = Color(0xFF64748B))
                                            Text("${result.totalTrades}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Simulated Trade History Header
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Simulated trades",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "SIMULATED HISTORICAL ORDERS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        if (result.trades.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFE1E2E5))
                                ) {
                                    Text(
                                        text = "No orders executed. Try relaxing the SMA periods or selecting a different timeframe.",
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        textAlign = TextAlign.Center,
                                        color = Color(0xFF64748B),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            items(result.trades.reversed()) { trade ->
                                TradeItem(trade = trade)
                            }
                        }
                    }
                }

                if (selectedTab == 3) {
                    // Platforms Tab Title Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = "Integrations",
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "External Platforms & API Gateway",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1E3A8A)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Replicate trades to your MetaTrader 5 (MT5) terminal or link with external signal providers like TradingView to trigger automated execution via secure webhook APIs.",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFF1E40AF)
                                )
                            }
                        }
                    }

                    // MT5 Integration Section Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE1E2E5))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Build,
                                            contentDescription = "MetaTrader 5",
                                            tint = Color(0xFF475569),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "MetaTrader 5 (MT5) Link",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF1A1C1E)
                                        )
                                    }

                                    // Status Badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (botConfig.mt5Connected) Color(0xFFDCFCE7)
                                                else Color(0xFFF1F3F5)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (botConfig.mt5Connected) "LINKED" else "DISCONNECTED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (botConfig.mt5Connected) Color(0xFF16A34A) else Color(0xFF64748B)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = mt5Server,
                                    onValueChange = { mt5Server = it },
                                    label = { Text("MT5 Server Address") },
                                    placeholder = { Text("e.g. MetaQuotes-Demo") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = mt5Login,
                                    onValueChange = { mt5Login = it },
                                    label = { Text("MT5 Account Login ID") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = mt5Password,
                                    onValueChange = { mt5Password = it },
                                    label = { Text("MT5 Password") },
                                    singleLine = true,
                                    visualTransformation = if (mt5PasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { mt5PasswordVisible = !mt5PasswordVisible }) {
                                            Icon(
                                                imageVector = if (mt5PasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.connectMT5(mt5Server, mt5Login, mt5Password) },
                                        modifier = Modifier.weight(1.5f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Connect Account", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (botConfig.mt5Connected) {
                                        OutlinedButton(
                                            onClick = { viewModel.disconnectMT5() },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xFFDC2626)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                                        ) {
                                            Text("Unlink", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TradingView Webhook Configuration Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE1E2E5))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = "TradingView",
                                            tint = Color(0xFF475569),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "TradingView Webhook API",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF1A1C1E)
                                        )
                                    }

                                    Switch(
                                        checked = tvEnabled,
                                        onCheckedChange = {
                                            tvEnabled = it
                                            viewModel.updateTradingViewConfig(it, tvWebhookUrl, tvToken)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Start a background listener to execute trades directly from incoming TradingView alerts, or send notifications when the bot executes a trade.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = tvWebhookUrl,
                                    onValueChange = { tvWebhookUrl = it },
                                    label = { Text("Webhook Callback Endpoint URL") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = tvToken,
                                    onValueChange = { tvToken = it },
                                    label = { Text("Authentication Token (Optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { viewModel.updateTradingViewConfig(tvEnabled, tvWebhookUrl, tvToken) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Apply & Restart Webhook Receiver", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Interactive Simulator Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Science,
                                        contentDescription = "Simulator",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Webhook Connection Simulator",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF334155)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Simulate an incoming TradingView signal over the local webhook channel. The bot will automatically intercept and execute the signal on your account.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.simulateTradingViewAlert("BUY") },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Simulate BUY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { viewModel.simulateTradingViewAlert("SELL") },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.TrendingDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Simulate SELL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // System Execution Terminal / Logs Component
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "Logs",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "LINKED PLATFORM TELEMETRY LOGS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 1.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFFF2F3F7))
                                    .border(1.dp, Color(0xFFE1E2E5), RoundedCornerShape(20.dp))
                                    .padding(12.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(logs) { log ->
                                        Text(
                                            text = log,
                                            color = when {
                                                log.contains("BUY") -> Color(0xFF16A34A)
                                                log.contains("SELL") -> Color(0xFFDC2626)
                                                log.contains("MT5 SUCCESS") || log.contains("MT5 REPLICATION") -> Color(0xFF0061A4)
                                                log.contains("MT5 LINK") || log.contains("MT5 CONNECTING") -> Color(0xFF1E3A8A)
                                                log.contains("TRADINGVIEW") -> Color(0xFF475569)
                                                log.contains("Error") || log.contains("ERROR") -> Color(0xFFB91C1C)
                                                else -> Color(0xFF475569)
                                            },
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Render Settings Dialog overlay
            if (showSettings) {
                SettingsDialog(
                    currentConfig = botConfig,
                    onDismiss = { showSettings = false },
                    onSave = { symbol, short, long, sandbox, key, secret, ai, strategy, sl, tp ->
                        viewModel.updateSettings(symbol, short, long, sandbox, key, secret, ai, strategy, sl, tp)
                        showSettings = false
                    }
                )
            }
        }
    }
}

@Composable
fun TradeItem(trade: Trade) {
    val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    val dateStr = sdf.format(Date(trade.timestamp))

    val isBuy = trade.type == "BUY"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE1E2E5))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isBuy) Color(0xFFDCFCE7)
                                else Color(0xFFFEE2E2)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = trade.type,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBuy) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = trade.symbol.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1A1C1E)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateStr,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format(Locale.US, "%,.2f", trade.price)} USDT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1A1C1E)
                )
                Text(
                    text = "Amount: ${String.format(Locale.US, "%,.5f", trade.amount)}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )

                if (!isBuy && trade.profitLoss != null) {
                    Text(
                        text = "P&L: ${if (trade.profitLoss >= 0) "+" else ""}${String.format(Locale.US, "%.2f", trade.profitLoss)}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (trade.profitLoss >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                    )
                }
            }
        }
    }
}
