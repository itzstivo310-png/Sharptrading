package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bot_config")
data class BotConfig(
    @PrimaryKey val id: Int = 1,
    val symbol: String = "BTCUSDT",
    val shortSma: Int = 5,
    val longSma: Int = 15,
    val balanceUSDT: Double = 10000.0,
    val balanceAsset: Double = 0.0,
    val isRunning: Boolean = false,
    val useAiSignal: Boolean = false,
    val useMlSignal: Boolean = false,
    val strategy: String = "SMA_CROSSOVER",
    val stopLossPct: Double = 2.0,
    val takeProfitPct: Double = 5.0,
    val lastBuyPrice: Double = 0.0,
    val apiKey: String = "",
    val apiSecret: String = "",
    val useSandbox: Boolean = true,
    
    // MT5 Link properties
    val mt5Enabled: Boolean = false,
    val mt5Server: String = "MetaQuotes-Demo",
    val mt5Login: String = "",
    val mt5Password: String = "",
    val mt5Connected: Boolean = false,

    // TradingView Link properties
    val tradingViewEnabled: Boolean = false,
    val tradingViewWebhookUrl: String = "http://localhost:8080/webhook",
    val tradingViewToken: String = "",
    val tradingViewConnected: Boolean = false
)
