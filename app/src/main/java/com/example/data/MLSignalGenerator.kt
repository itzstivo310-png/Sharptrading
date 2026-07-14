package com.example.data

import kotlin.math.sqrt

object MLSignalGenerator {

    fun calculateRsi(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size <= period) return 50.0
        var gains = 0.0
        var losses = 0.0
        
        for (i in 1..period) {
            val difference = candles[candles.size - period - 1 + i].close - candles[candles.size - period - 2 + i].close
            if (difference > 0) {
                gains += difference
            } else {
                losses -= difference
            }
        }
        
        var avgGain = gains / period
        var avgLoss = losses / period
        
        for (i in (candles.size - period) until candles.size) {
            val difference = candles[i].close - candles[i - 1].close
            val gain = if (difference > 0) difference else 0.0
            val loss = if (difference < 0) -difference else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }
        
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun calculateRoc(candles: List<Candle>, period: Int = 5): Double {
        if (candles.size <= period) return 0.0
        val currentPrice = candles.last().close
        val oldPrice = candles[candles.size - 1 - period].close
        if (oldPrice == 0.0) return 0.0
        return ((currentPrice - oldPrice) / oldPrice) * 100.0
    }

    fun calculateVolatility(candles: List<Candle>, period: Int = 5): Double {
        if (candles.size <= period) return 0.0
        val recent = candles.takeLast(period)
        val mean = recent.map { it.close }.average()
        val variance = recent.map { (it.close - mean) * (it.close - mean) }.sum() / period
        val stdDev = sqrt(variance)
        if (mean == 0.0) return 0.0
        return (stdDev / mean) * 100.0
    }

    fun generateSignal(candles: List<Candle>, shortPeriod: Int = 5, longPeriod: Int = 15): String {
        if (candles.size < longPeriod + 5) return "HOLD"

        // Extract features
        val rsi = calculateRsi(candles, 14)
        val roc = calculateRoc(candles, 5)
        val vol = calculateVolatility(candles, 5)

        // Calculate SMAs
        val lastShortSma = candles.takeLast(shortPeriod).map { it.close }.average()
        val lastLongSma = candles.takeLast(longPeriod).map { it.close }.average()
        val smaSpread = if (lastLongSma == 0.0) 0.0 else ((lastShortSma - lastLongSma) / lastLongSma) * 100.0

        // Machine Learning scoring (Weighted Logistic/Softmax Model)
        val wBuyRsi = -0.06      // Lower RSI is oversold/bullish
        val wBuyRoc = 0.25       // Positive momentum is bullish
        val wBuySpread = 0.50    // Positive crossover is bullish
        val wBuyVol = -0.15      // Consolidation is healthy
        val biasBuy = 1.2

        val wSellRsi = 0.08      // Higher RSI is overbought/bearish
        val wSellRoc = -0.30     // Negative momentum is bearish
        val wSellSpread = -0.60  // Negative crossover is bearish
        val wSellVol = 0.20      // Panic volatility is bearish
        val biasSell = 1.0

        val buyScore = (rsi * wBuyRsi) + (roc * wBuyRoc) + (smaSpread * wBuySpread) + (vol * wBuyVol) + biasBuy
        val sellScore = (rsi * wSellRsi) + (roc * wSellRoc) + (smaSpread * wSellSpread) + (vol * wSellVol) + biasSell

        return when {
            buyScore > sellScore && buyScore > 1.5 -> "BUY"
            sellScore > buyScore && sellScore > 1.5 -> "SELL"
            else -> "HOLD"
        }
    }
}
