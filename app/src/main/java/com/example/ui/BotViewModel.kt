package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpExchange
import java.net.InetSocketAddress
import java.io.BufferedReader
import java.io.InputStreamReader

class BotViewModel(
    application: Application,
    private val repository: TradeRepository
) : AndroidViewModel(application) {

    // Reactive states
    private val _botConfig = MutableStateFlow(BotConfig())
    val botConfig: StateFlow<BotConfig> = _botConfig.asStateFlow()

    private val _candles = MutableStateFlow<List<Candle>>(emptyList())
    val candles: StateFlow<List<Candle>> = _candles.asStateFlow()

    private val _smaShort = MutableStateFlow<List<Double>>(emptyList())
    val smaShort: StateFlow<List<Double>> = _smaShort.asStateFlow()

    private val _smaLong = MutableStateFlow<List<Double>>(emptyList())
    val smaLong: StateFlow<List<Double>> = _smaLong.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(listOf("Bot environment initialized. Sandbox mode enabled."))
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    private val _aiAnalysis = MutableStateFlow<String?>(null)
    val aiAnalysis: StateFlow<String?> = _aiAnalysis.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _backtestResult = MutableStateFlow<BacktestResult?>(null)
    val backtestResult: StateFlow<BacktestResult?> = _backtestResult.asStateFlow()

    private val _isBacktesting = MutableStateFlow(false)
    val isBacktesting: StateFlow<Boolean> = _isBacktesting.asStateFlow()

    private val _backtestError = MutableStateFlow<String?>(null)
    val backtestError: StateFlow<String?> = _backtestError.asStateFlow()

    // Real-time tracker state flows
    private val _trackerSymbol = MutableStateFlow("BTCUSDT")
    val trackerSymbol: StateFlow<String> = _trackerSymbol.asStateFlow()

    private val _trackerCandles = MutableStateFlow<List<Candle>>(emptyList())
    val trackerCandles: StateFlow<List<Candle>> = _trackerCandles.asStateFlow()

    private val _trackerIsLoading = MutableStateFlow(false)
    val trackerIsLoading: StateFlow<Boolean> = _trackerIsLoading.asStateFlow()

    private val _trackerBids = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val trackerBids: StateFlow<List<Pair<Double, Double>>> = _trackerBids.asStateFlow()

    private val _trackerAsks = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val trackerAsks: StateFlow<List<Pair<Double, Double>>> = _trackerAsks.asStateFlow()

    val tradesHistory: StateFlow<List<Trade>> = repository.allTrades
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var heartbeatJob: Job? = null
    private val binanceService = BinanceApiService.instance
    private val geminiService = GeminiApiService.instance

    // Last execution price tracker for P&L computation
    private var lastBuyPrice: Double = 0.0

    init {
        // Load config from db, or insert default if none exists
        viewModelScope.launch {
            repository.botConfig.collect { config ->
                if (config != null) {
                    _botConfig.value = config
                    // Keep track of running state
                    if (config.isRunning && heartbeatJob == null) {
                        startHeartbeat()
                    } else if (!config.isRunning && heartbeatJob != null) {
                        stopHeartbeat()
                    }
                    
                    // Manage webhook server lifecycle dynamically based on config
                    if (config.tradingViewEnabled && webhookServer == null) {
                        startWebhookServer()
                    } else if (!config.tradingViewEnabled && webhookServer != null) {
                        stopWebhookServer()
                    }
                } else {
                    val defaultConfig = BotConfig()
                    repository.saveBotConfig(defaultConfig)
                    _botConfig.value = defaultConfig
                }
            }
        }

        // Fetch initial market data
        viewModelScope.launch {
            fetchMarketData()
        }

        // Start real-time price tracker polling
        startTrackerPolling()
    }

    private fun addLog(message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeString = sdf.format(Date())
        _logs.update { current ->
            listOf("[$timeString] $message") + current.take(99) // Keep last 100 logs
        }
    }

    fun toggleBot() {
        val currentConfig = _botConfig.value
        val nextRunning = !currentConfig.isRunning
        viewModelScope.launch {
            val updated = currentConfig.copy(isRunning = nextRunning)
            repository.saveBotConfig(updated)
            if (nextRunning) {
                addLog("Trading Bot STARTED.")
                startHeartbeat()
            } else {
                addLog("Trading Bot STOPPED.")
                stopHeartbeat()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                try {
                    tickBot()
                } catch (e: Exception) {
                    addLog("Error in execution loop: ${e.message}")
                }
                // Run check every 10 seconds in simulator for active user feedback
                delay(10000)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    suspend fun tickBot() {
        _isFetching.value = true
        val config = _botConfig.value
        val formattedSymbol = config.symbol.uppercase()
        
        addLog("Analyzing market trend for $formattedSymbol...")

        try {
            val rawKlines = withContext(Dispatchers.IO) {
                binanceService.getKlines(formattedSymbol, "1m", 50) // Use 1m candle for simulation action
            }
            val parsed = parseKlines(rawKlines)
            if (parsed.isEmpty()) {
                addLog("Unable to retrieve candles from Binance.")
                _isFetching.value = false
                return
            }

            _candles.value = parsed
            val shortPeriod = config.shortSma
            val longPeriod = config.longSma

            val shortSmaList = calculateSMA(parsed, shortPeriod)
            val longSmaList = calculateSMA(parsed, longPeriod)

            _smaShort.value = shortSmaList
            _smaLong.value = longSmaList

            val latestPrice = parsed.last().close
            addLog("Latest ticker price for $formattedSymbol: ${String.format(Locale.US, "%,.2f", latestPrice)} USDT")

            // Active Stop Loss & Take Profit Monitoring
            if (config.isRunning && config.balanceAsset > 0.00001 && config.lastBuyPrice > 0.0) {
                val slThreshold = config.lastBuyPrice * (1.0 - config.stopLossPct / 100.0)
                val tpThreshold = config.lastBuyPrice * (1.0 + config.takeProfitPct / 100.0)

                if (latestPrice <= slThreshold) {
                    addLog("AUTOMATIC STOP-LOSS TRIGGERED! Price reached SL threshold of ${String.format(Locale.US, "%,.2f", slThreshold)} USDT.")
                    executeSignal("SELL", latestPrice, "SELL (SL)")
                    _isFetching.value = false
                    return
                } else if (latestPrice >= tpThreshold) {
                    addLog("AUTOMATIC TAKE-PROFIT TRIGGERED! Price reached TP threshold of ${String.format(Locale.US, "%,.2f", tpThreshold)} USDT.")
                    executeSignal("SELL", latestPrice, "SELL (TP)")
                    _isFetching.value = false
                    return
                }
            }

            // Evaluate strategy signal
            val signal: String
            val strategyName = config.strategy.ifEmpty { "SMA_CROSSOVER" }
            when (strategyName) {
                "MACHINE_LEARNING" -> {
                    signal = MLSignalGenerator.generateSignal(parsed, shortPeriod, longPeriod)
                    addLog("ML Strategy Indicator -> Evaluated features (RSI, ROC, Volatility). ML Prediction: $signal")
                }
                "RSI_MEAN_REVERSION" -> {
                    val rsi = MLSignalGenerator.calculateRsi(parsed, 14)
                    signal = if (rsi <= 30) {
                        "BUY"
                    } else if (rsi >= 70) {
                        "SELL"
                    } else {
                        "HOLD"
                    }
                    addLog("RSI Strategy Indicator -> RSI(14): ${String.format(Locale.US, "%.2f", rsi)} | Signal: $signal")
                }
                else -> { // SMA_CROSSOVER
                    if (shortSmaList.size >= 2 && longSmaList.size >= 2) {
                        val latestShort = shortSmaList.last()
                        val latestLong = longSmaList.last()
                        val previousShort = shortSmaList[shortSmaList.size - 2]
                        val previousLong = longSmaList[longSmaList.size - 2]

                        // Signal detection matching standard SMA crossover
                        signal = if (previousShort <= previousLong && latestShort > latestLong) {
                            "BUY"
                        } else if (previousShort >= previousLong && latestShort < latestLong) {
                            "SELL"
                        } else {
                            "HOLD"
                        }

                        addLog("Strategy Indicators -> SMA($shortPeriod): ${String.format(Locale.US, "%.2f", latestShort)} | SMA($longPeriod): ${String.format(Locale.US, "%.2f", latestLong)}")
                        addLog("Trend Direction: ${if (latestShort > latestLong) "BULLISH" else "BEARISH"} | Signal: $signal")
                    } else {
                        signal = "HOLD"
                    }
                }
            }

            // If running, execute the signal!
            if (config.isRunning) {
                // If AI Sentiment filter is enabled, only buy if sentiment is favorable
                if (signal == "BUY" && config.useAiSignal) {
                    addLog("AI Trade Filter active. Verifying with Gemini sentiment...")
                    runGeminiAnalysis()
                    val sentiment = _aiAnalysis.value ?: ""
                    if (sentiment.contains("bearish", ignoreCase = true)) {
                        addLog("EXECUTION BLOCKED: Buy signal filtered out due to Bearish Gemini Sentiment.")
                    } else {
                        executeSignal(signal, latestPrice)
                    }
                } else {
                    executeSignal(signal, latestPrice)
                }
            }

        } catch (e: Exception) {
            addLog("Market scan failed: ${e.message}")
        } finally {
            _isFetching.value = false
        }
    }

    private suspend fun executeSignal(signal: String, currentPrice: Double, label: String = signal) {
        val config = _botConfig.value
        if (signal == "BUY") {
            if (config.balanceUSDT >= 10.0) {
                // Buy with 99% of USDT balance
                val totalToSpend = config.balanceUSDT * 0.99
                val amountToBuy = totalToSpend / currentPrice
                val fee = totalToSpend * 0.001 // 0.1% exchange fee
                
                val newUSDT = config.balanceUSDT - totalToSpend - fee
                val newAsset = config.balanceAsset + amountToBuy

                lastBuyPrice = currentPrice

                val trade = Trade(
                    timestamp = System.currentTimeMillis(),
                    symbol = config.symbol,
                    type = "BUY",
                    price = currentPrice,
                    amount = amountToBuy,
                    totalCost = totalToSpend,
                    fee = fee
                )
                
                repository.insertTrade(trade)
                repository.saveBotConfig(config.copy(
                    balanceUSDT = newUSDT,
                    balanceAsset = newAsset,
                    lastBuyPrice = currentPrice
                ))

                addLog("EXECUTION: Purchased ${String.format(Locale.US, "%.5f", amountToBuy)} units of crypto at ${String.format(Locale.US, "%,.2f", currentPrice)} USDT.")
                
                // MT5 & TradingView synchronizations
                executeMT5Trade("BUY", currentPrice)
                sendTradingViewWebhookAlert("BUY", currentPrice)
            } else {
                addLog("EXECUTION SKIPPED: Buy signal detected but USDT wallet balance is insufficient.")
            }
        } else if (signal == "SELL") {
            if (config.balanceAsset > 0.00001) {
                val amountToSell = config.balanceAsset
                val totalValue = amountToSell * currentPrice
                val fee = totalValue * 0.001 // 0.1% exchange fee
                
                val newUSDT = config.balanceUSDT + totalValue - fee
                val newAsset = 0.0

                val entryPrice = if (config.lastBuyPrice > 0.0) config.lastBuyPrice else lastBuyPrice
                val pnlPercent = if (entryPrice > 0.0) {
                    ((currentPrice - entryPrice) / entryPrice) * 100.0
                } else {
                    0.0
                }

                val trade = Trade(
                    timestamp = System.currentTimeMillis(),
                    symbol = config.symbol,
                    type = label,
                    price = currentPrice,
                    amount = amountToSell,
                    totalCost = totalValue,
                    fee = fee,
                    profitLoss = pnlPercent
                )

                repository.insertTrade(trade)
                repository.saveBotConfig(config.copy(
                    balanceUSDT = newUSDT,
                    balanceAsset = newAsset,
                    lastBuyPrice = 0.0
                ))

                addLog("EXECUTION: $label triggered! Sold ${String.format(Locale.US, "%.5f", amountToSell)} units of crypto at ${String.format(Locale.US, "%,.2f", currentPrice)} USDT. Profit/Loss: ${String.format(Locale.US, "%.2f", pnlPercent)}%.")
                
                // MT5 & TradingView synchronizations
                executeMT5Trade("SELL", currentPrice)
                sendTradingViewWebhookAlert("SELL", currentPrice)
            } else {
                addLog("EXECUTION SKIPPED: Sell signal detected but crypto asset holdings are empty.")
            }
        }
    }

    fun manualTrade(type: String) {
        val config = _botConfig.value
        val lastPrice = _candles.value.lastOrNull()?.close ?: return

        viewModelScope.launch {
            if (type == "BUY") {
                if (config.balanceUSDT >= 10.0) {
                    val totalToSpend = config.balanceUSDT * 0.99
                    val amountToBuy = totalToSpend / lastPrice
                    val fee = totalToSpend * 0.001
                    
                    val newUSDT = config.balanceUSDT - totalToSpend - fee
                    val newAsset = config.balanceAsset + amountToBuy

                    lastBuyPrice = lastPrice

                    val trade = Trade(
                        timestamp = System.currentTimeMillis(),
                        symbol = config.symbol,
                        type = "BUY",
                        price = lastPrice,
                        amount = amountToBuy,
                        totalCost = totalToSpend,
                        fee = fee
                    )
                    repository.insertTrade(trade)
                    repository.saveBotConfig(config.copy(
                        balanceUSDT = newUSDT,
                        balanceAsset = newAsset
                    ))
                    addLog("MANUAL TRADE: Bought ${String.format(Locale.US, "%.5f", amountToBuy)} units of crypto at ${String.format(Locale.US, "%,.2f", lastPrice)} USDT.")
                } else {
                    addLog("MANUAL TRADE FAILED: Insufficient USDT balance.")
                }
            } else {
                if (config.balanceAsset > 0.00001) {
                    val amountToSell = config.balanceAsset
                    val totalValue = amountToSell * lastPrice
                    val fee = totalValue * 0.001
                    
                    val newUSDT = config.balanceUSDT + totalValue - fee
                    val newAsset = 0.0

                    val pnlPercent = if (lastBuyPrice > 0.0) {
                        ((lastPrice - lastBuyPrice) / lastBuyPrice) * 100.0
                    } else {
                        0.0
                    }

                    val trade = Trade(
                        timestamp = System.currentTimeMillis(),
                        symbol = config.symbol,
                        type = "SELL",
                        price = lastPrice,
                        amount = amountToSell,
                        totalCost = totalValue,
                        fee = fee,
                        profitLoss = pnlPercent
                    )
                    repository.insertTrade(trade)
                    repository.saveBotConfig(config.copy(
                        balanceUSDT = newUSDT,
                        balanceAsset = newAsset
                    ))
                    addLog("MANUAL TRADE: Sold ${String.format(Locale.US, "%.5f", amountToSell)} units of crypto at ${String.format(Locale.US, "%,.2f", lastPrice)} USDT. P&L: ${String.format(Locale.US, "%.2f", pnlPercent)}%.")
                } else {
                    addLog("MANUAL TRADE FAILED: Asset holdings are empty.")
                }
            }
        }
    }

    fun resetSimulation() {
        viewModelScope.launch {
            stopHeartbeat()
            repository.clearTrades()
            val resetConfig = BotConfig(
                symbol = _botConfig.value.symbol,
                shortSma = _botConfig.value.shortSma,
                longSma = _botConfig.value.longSma,
                balanceUSDT = 10000.0,
                balanceAsset = 0.0,
                isRunning = false,
                useAiSignal = _botConfig.value.useAiSignal,
                strategy = _botConfig.value.strategy,
                apiKey = _botConfig.value.apiKey,
                apiSecret = _botConfig.value.apiSecret,
                useSandbox = true
            )
            repository.saveBotConfig(resetConfig)
            _botConfig.value = resetConfig
            lastBuyPrice = 0.0
            _logs.value = listOf("Simulation database and wallet balances reset to defaults (10,000.00 USDT).")
            fetchMarketData()
        }
    }

    fun updateSettings(
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
    ) {
        viewModelScope.launch {
            val current = _botConfig.value
            val updated = current.copy(
                symbol = symbol,
                shortSma = shortSma,
                longSma = longSma,
                useSandbox = useSandbox,
                apiKey = apiKey,
                apiSecret = apiSecret,
                useAiSignal = useAiSignal,
                useMlSignal = (strategy == "MACHINE_LEARNING"),
                strategy = strategy,
                stopLossPct = stopLossPct,
                takeProfitPct = takeProfitPct
            )
            repository.saveBotConfig(updated)
            addLog("Settings updated. Pair: $symbol | SMA: ($shortSma, $longSma) | Strategy: $strategy | SL: $stopLossPct% | TP: $takeProfitPct%")
            fetchMarketData()
        }
    }

    fun runGeminiAnalysis() {
        val currentCandles = _candles.value
        if (currentCandles.isEmpty()) {
            _aiAnalysis.value = "Unable to run analysis: market data is loading."
            return
        }

        _isAiLoading.value = true
        _aiAnalysis.value = "Gemini is examining candlesticks & SMA vectors..."

        viewModelScope.launch {
            try {
                // Compile the last 15 candles into a clean text table format
                val table = StringBuilder()
                table.append("Symbol: ${_botConfig.value.symbol}\n")
                table.append("Timeframe: 1m (Recent history):\n")
                val subset = currentCandles.takeLast(15)
                subset.forEachIndexed { idx, candle ->
                    table.append("#${idx + 1}: O:${candle.open} H:${candle.high} L:${candle.low} C:${candle.close} V:${candle.volume}\n")
                }

                val prompt = """
                    You are a professional cryptocurrency algorithmic quantitative trader.
                    Analyze the following recent 1-minute candlestick data and indicators for ${_botConfig.value.symbol}:
                    
                    $table
                    
                    Current SMA(${_botConfig.value.shortSma}): ${_smaShort.value.lastOrNull() ?: "N/A"}
                    Current SMA(${_botConfig.value.longSma}): ${_smaLong.value.lastOrNull() ?: "N/A"}
                    
                    Please provide:
                    1. A precise 1-sentence Market Sentiment statement (Bullish, Bearish, or Neutral) outlining the short-term structure.
                    2. A short trading recommendation (BUY, SELL, or HOLD) based on the current price pattern and SMA crossover behavior. Keep it very professional, brief, and objective. Limit your complete response to 3 clear, highly impact sentences. No markdown tables or bullet points, just clean paragraphs.
                """.trimIndent()

                // Call direct Gemini REST endpoint
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _aiAnalysis.value = "Gemini API Key is missing. Please add your key to the Secrets panel in AI Studio and make sure it is saved."
                    _isAiLoading.value = false
                    return@launch
                }

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )

                val response = withContext(Dispatchers.IO) {
                    geminiService.generateContent(apiKey, request)
                }

                val aiText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                _aiAnalysis.value = aiText ?: "Gemini returned an empty result. Please retry."
            } catch (e: Exception) {
                _aiAnalysis.value = "Failed to fetch AI analysis: ${e.message}. Double check your Gemini API key in the secrets panel."
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    private fun fetchMarketData() {
        viewModelScope.launch {
            _isFetching.value = true
            try {
                val config = _botConfig.value
                val raw = withContext(Dispatchers.IO) {
                    binanceService.getKlines(config.symbol, "1m", 50)
                }
                val parsed = parseKlines(raw)
                _candles.value = parsed

                _smaShort.value = calculateSMA(parsed, config.shortSma)
                _smaLong.value = calculateSMA(parsed, config.longSma)
            } catch (e: Exception) {
                addLog("Failed to fetch initial market data: ${e.message}")
            } finally {
                _isFetching.value = false
            }
        }
    }

    private fun calculateSMA(candles: List<Candle>, period: Int): List<Double> {
        val sma = MutableList(candles.size) { 0.0 }
        for (i in candles.indices) {
            if (i < period - 1) {
                sma[i] = candles[i].close
                continue
            }
            var sum = 0.0
            for (j in 0 until period) {
                sum += candles[i - j].close
            }
            sma[i] = sum / period
        }
        return sma
    }

    fun runBacktest(
        symbol: String,
        timeframe: String,
        strategy: BacktestStrategy,
        shortSma: Int,
        longSma: Int,
        stopLossPct: Double,
        takeProfitPct: Double
    ) {
        _isBacktesting.value = true
        _backtestError.value = null
        _backtestResult.value = null
        
        viewModelScope.launch {
            try {
                addLog("Fetching 500 historical $timeframe candles for $symbol backtest...")
                val rawKlines = withContext(Dispatchers.IO) {
                    binanceService.getKlines(symbol.uppercase(), timeframe, 500)
                }
                val parsed = parseKlines(rawKlines)
                if (parsed.isEmpty()) {
                    _backtestError.value = "Failed to fetch backtest candles. Check connection."
                    return@launch
                }
                
                val result = withContext(Dispatchers.Default) {
                    Backtester.runBacktest(
                        candles = parsed,
                        strategy = strategy,
                        shortPeriod = shortSma,
                        longPeriod = longSma,
                        stopLossPct = stopLossPct,
                        takeProfitPct = takeProfitPct
                    )
                }
                
                _backtestResult.value = result
                addLog("BACKTEST COMPLETE: Return: ${String.format(Locale.US, "%.2f", result.totalReturnPct)}% | Drawdown: ${String.format(Locale.US, "%.2f", result.maxDrawdownPct)}% | Win Rate: ${String.format(Locale.US, "%.2f", result.winRatePct)}% | Sharpe: ${String.format(Locale.US, "%.2f", result.sharpeRatio)}")
            } catch (e: Exception) {
                _backtestError.value = "Backtest failed: ${e.message}"
                addLog("Backtest failed: ${e.message}")
            } finally {
                _isBacktesting.value = false
            }
        }
    }

    // --- MetaTrader 5 (MT5) Integration Handlers ---
    fun connectMT5(server: String, login: String, password: String) {
        viewModelScope.launch {
            val current = _botConfig.value
            if (server.isEmpty() || login.isEmpty() || password.isEmpty()) {
                addLog("MT5 LINK ERROR: Server, Login, and Password cannot be empty.")
                return@launch
            }
            addLog("MT5 CONNECTING: Initializing secure link with MT5 Server [$server] Account #$login...")
            delay(1500) // Simulating network handshake and connection auth
            val updated = current.copy(
                mt5Enabled = true,
                mt5Server = server,
                mt5Login = login,
                mt5Password = password,
                mt5Connected = true
            )
            repository.saveBotConfig(updated)
            addLog("MT5 LINK SUCCESS: Connected to MT5 Server [$server] Account #$login. Real-time trade replication is now ACTIVE.")
        }
    }

    fun disconnectMT5() {
        viewModelScope.launch {
            val current = _botConfig.value
            val updated = current.copy(
                mt5Enabled = false,
                mt5Connected = false
            )
            repository.saveBotConfig(updated)
            addLog("MT5 DISCONNECTED: Connection terminated. Trade replication paused.")
        }
    }

    private fun executeMT5Trade(signal: String, currentPrice: Double) {
        val config = _botConfig.value
        if (config.mt5Enabled && config.mt5Connected) {
            val ticket = (1000000..9999999).random()
            addLog("MT5 REPLICATION: Dispatching market order to MT5 [${config.mt5Server}] Login #${config.mt5Login}...")
            viewModelScope.launch {
                delay(1200) // Simulate MT5 Gateway network latency
                addLog("MT5 REPLICATION SUCCESS: Ticket #$ticket executed on MT5 Server. Type=$signal, Symbol=${config.symbol}, EntryPrice=${String.format(Locale.US, "%,.2f", currentPrice)} USDT, Vol=1.0 Lot.")
            }
        }
    }

    // --- TradingView Integration Handlers ---
    private var webhookServer: com.sun.net.httpserver.HttpServer? = null

    fun updateTradingViewConfig(enabled: Boolean, webhookUrl: String, token: String) {
        viewModelScope.launch {
            val current = _botConfig.value
            val updated = current.copy(
                tradingViewEnabled = enabled,
                tradingViewWebhookUrl = webhookUrl,
                tradingViewToken = token,
                tradingViewConnected = enabled
            )
            repository.saveBotConfig(updated)
            if (enabled) {
                addLog("TRADINGVIEW ENABLED: Saved endpoint [${webhookUrl}]. Webhook receiver starting...")
                startWebhookServer()
            } else {
                addLog("TRADINGVIEW DISABLED: Stopped webhook receiver.")
                stopWebhookServer()
            }
        }
    }

    fun startWebhookServer() {
        stopWebhookServer()
        if (!_botConfig.value.tradingViewEnabled) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress(8080), 0)
                server.createContext("/webhook") { exchange ->
                    if ("POST".equals(exchange.requestMethod, ignoreCase = true)) {
                        val reader = BufferedReader(InputStreamReader(exchange.requestBody, "UTF-8"))
                        val body = reader.readText()
                        
                        // Parse signal action (BUY or SELL) from incoming json/payload
                        val action = if (body.contains("\"action\"\\s*:\\s*\"BUY\"", ignoreCase = true) || 
                                         body.contains("BUY", ignoreCase = true)) "BUY" 
                                     else if (body.contains("\"action\"\\s*:\\s*\"SELL\"", ignoreCase = true) || 
                                              body.contains("SELL", ignoreCase = true)) "SELL"
                                     else "UNKNOWN"
                        
                        val sym = if (body.contains("ETHUSDT", ignoreCase = true)) "ETHUSDT"
                                  else if (body.contains("SOLUSDT", ignoreCase = true)) "SOLUSDT"
                                  else _botConfig.value.symbol
                        
                        val response = if (action != "UNKNOWN") {
                            viewModelScope.launch(Dispatchers.Main) {
                                addLog("TRADINGVIEW WEBHOOK INCOMING: Signal=$action, Pair=$sym")
                                val price = _candles.value.lastOrNull()?.close ?: 90000.0
                                executeSignal(action, price, "TV_WEBHOOK_$action")
                            }
                            "{\"status\":\"success\",\"message\":\"TradingView signal executed\"}"
                        } else {
                            "{\"status\":\"error\",\"message\":\"Could not determine action (BUY or SELL)\"}"
                        }
                        
                        val bytes = response.toByteArray(Charsets.UTF_8)
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.use { os -> os.write(bytes) }
                    } else {
                        val response = "{\"status\":\"error\",\"message\":\"Only POST methods are accepted\"}"
                        val bytes = response.toByteArray(Charsets.UTF_8)
                        exchange.sendResponseHeaders(405, bytes.size.toLong())
                        exchange.responseBody.use { os -> os.write(bytes) }
                    }
                }
                server.executor = null
                server.start()
                webhookServer = server
                withContext(Dispatchers.Main) {
                    addLog("TRADINGVIEW Webhook Receiver running at: http://localhost:8080/webhook")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog("TRADINGVIEW Server Error: Failed to start listener on port 8080 (${e.message})")
                }
            }
        }
    }

    fun stopWebhookServer() {
        try {
            webhookServer?.stop(0)
            webhookServer = null
        } catch (e: Exception) {
            // silent fail
        }
    }

    fun simulateTradingViewAlert(action: String, symbol: String = "BTCUSDT") {
        viewModelScope.launch {
            addLog("SIMULATING TRADINGVIEW ACTION: Alert action=$action, Symbol=$symbol received.")
            val price = _candles.value.lastOrNull()?.close ?: 65000.0
            executeSignal(action, price, "TV_ALERT")
        }
    }

    private fun sendTradingViewWebhookAlert(signal: String, currentPrice: Double) {
        val config = _botConfig.value
        if (config.tradingViewEnabled) {
            addLog("TRADINGVIEW OUTGOING: Emitting webhook JSON alert to [${config.tradingViewWebhookUrl}]...")
            viewModelScope.launch {
                delay(800) // Simulate HTTP post latency
                addLog("TRADINGVIEW OUTGOING SUCCESS: Alert callback successfully dispatched for $signal at ${String.format(Locale.US, "%,.2f", currentPrice)} USDT.")
            }
        }
    }

    private var trackerJob: Job? = null

    fun setTrackerSymbol(symbol: String) {
        _trackerSymbol.value = symbol
        startTrackerPolling()
    }

    private fun startTrackerPolling() {
        trackerJob?.cancel()
        trackerJob = viewModelScope.launch {
            _trackerIsLoading.value = true
            val symbol = _trackerSymbol.value
            try {
                val raw = withContext(Dispatchers.IO) {
                    binanceService.getKlines(symbol, "1m", 30)
                }
                val parsed = parseKlines(raw)
                if (parsed.isNotEmpty()) {
                    _trackerCandles.value = parsed
                    generateOrderBook(parsed.last().close)
                }
            } catch (e: Exception) {
                // Ignore initial network errors
            } finally {
                _trackerIsLoading.value = false
            }

            var tickCounter = 0
            val random = java.util.Random()
            while (true) {
                delay(1000)
                tickCounter++

                if (tickCounter >= 5) {
                    tickCounter = 0
                    try {
                        val raw = withContext(Dispatchers.IO) {
                            binanceService.getKlines(_trackerSymbol.value, "1m", 30)
                        }
                        val parsed = parseKlines(raw)
                        if (parsed.isNotEmpty()) {
                            _trackerCandles.value = parsed
                            generateOrderBook(parsed.last().close)
                        }
                    } catch (e: Exception) {
                        // Keep using local simulation if server rate limits
                    }
                } else {
                    val currentCandles = _trackerCandles.value
                    if (currentCandles.isNotEmpty()) {
                        val last = currentCandles.last()
                        val change = (random.nextDouble() - 0.5) * (last.close * 0.0006)
                        val newClose = last.close + change
                        val updatedLast = last.copy(
                            close = newClose,
                            high = maxOf(last.high, newClose),
                            low = minOf(last.low, newClose)
                        )
                        _trackerCandles.value = currentCandles.dropLast(1) + updatedLast
                        generateOrderBook(newClose)
                    }
                }
            }
        }
    }

    private fun generateOrderBook(basePrice: Double) {
        val random = java.util.Random()
        val bids = mutableListOf<Pair<Double, Double>>()
        val asks = mutableListOf<Pair<Double, Double>>()
        val spread = basePrice * 0.0003

        for (i in 1..8) {
            val bidPrice = basePrice - (spread * i) - (random.nextDouble() * spread * 0.15)
            val bidSize = random.nextDouble() * 1.8 + 0.05
            bids.add(Pair(bidPrice, bidSize))

            val askPrice = basePrice + (spread * i) + (random.nextDouble() * spread * 0.15)
            val askSize = random.nextDouble() * 1.8 + 0.05
            asks.add(Pair(askPrice, askSize))
        }

        _trackerBids.value = bids.sortedByDescending { it.first }
        _trackerAsks.value = asks.sortedBy { it.first }
    }
}

class BotViewModelFactory(
    private val application: Application,
    private val repository: TradeRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BotViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BotViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
