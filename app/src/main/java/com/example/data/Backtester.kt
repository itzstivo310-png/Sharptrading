package com.example.data

import kotlin.math.sqrt

enum class BacktestStrategy {
    SMA_CROSSOVER,
    MACHINE_LEARNING,
    RSI_MEAN_REVERSION
}

data class BacktestResult(
    val initialBalance: Double,
    val finalBalance: Double,
    val totalReturnPct: Double,
    val maxDrawdownPct: Double,
    val winRatePct: Double,
    val totalTrades: Int,
    val sharpeRatio: Double,
    val trades: List<Trade>
)

object Backtester {
    fun runBacktest(
        candles: List<Candle>,
        strategy: BacktestStrategy,
        shortPeriod: Int = 5,
        longPeriod: Int = 15,
        stopLossPct: Double = 2.0,
        takeProfitPct: Double = 5.0
    ): BacktestResult {
        if (candles.size < longPeriod + 5) {
            return BacktestResult(10000.0, 10000.0, 0.0, 0.0, 0.0, 0, 0.0, emptyList())
        }

        var balanceUSDT = 10000.0
        var balanceAsset = 0.0
        var lastBuyPrice = 0.0
        var peakBalance = 10000.0
        var maxDrawdown = 0.0
        val virtualTrades = mutableListOf<Trade>()
        
        val portfolioValues = mutableListOf<Double>()
        portfolioValues.add(balanceUSDT)

        var totalBuyTrades = 0
        var wonTrades = 0

        for (i in longPeriod until candles.size) {
            val subCandles = candles.subList(0, i + 1)
            val currentPrice = candles[i].close
            val currentHigh = candles[i].high
            val currentLow = candles[i].low

            val currentPortfolioValue = balanceUSDT + (balanceAsset * currentPrice)
            portfolioValues.add(currentPortfolioValue)
            if (currentPortfolioValue > peakBalance) {
                peakBalance = currentPortfolioValue
            }
            val drawdown = ((peakBalance - currentPortfolioValue) / peakBalance) * 100.0
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown
            }

            // 1. Check Stop Loss and Take Profit
            if (balanceAsset > 0.0 && lastBuyPrice > 0.0) {
                val slPrice = lastBuyPrice * (1.0 - stopLossPct / 100.0)
                val tpPrice = lastBuyPrice * (1.0 + takeProfitPct / 100.0)

                if (currentLow <= slPrice) {
                    val sellPrice = slPrice
                    val amountToSell = balanceAsset
                    val totalValue = amountToSell * sellPrice
                    val fee = totalValue * 0.001
                    balanceUSDT += totalValue - fee
                    balanceAsset = 0.0

                    val pnl = ((sellPrice - lastBuyPrice) / lastBuyPrice) * 100.0
                    virtualTrades.add(
                        Trade(
                            timestamp = candles[i].openTime,
                            symbol = "BACKTEST",
                            type = "SELL (SL)",
                            price = sellPrice,
                            amount = amountToSell,
                            totalCost = totalValue,
                            fee = fee,
                            profitLoss = pnl
                        )
                    )
                    if (pnl > 0.0) wonTrades++
                    lastBuyPrice = 0.0
                    continue
                } else if (currentHigh >= tpPrice) {
                    val sellPrice = tpPrice
                    val amountToSell = balanceAsset
                    val totalValue = amountToSell * sellPrice
                    val fee = totalValue * 0.001
                    balanceUSDT += totalValue - fee
                    balanceAsset = 0.0

                    val pnl = ((sellPrice - lastBuyPrice) / lastBuyPrice) * 100.0
                    virtualTrades.add(
                        Trade(
                            timestamp = candles[i].openTime,
                            symbol = "BACKTEST",
                            type = "SELL (TP)",
                            price = sellPrice,
                            amount = amountToSell,
                            totalCost = totalValue,
                            fee = fee,
                            profitLoss = pnl
                        )
                    )
                    if (pnl > 0.0) wonTrades++
                    lastBuyPrice = 0.0
                    continue
                }
            }

            // 2. Strategy evaluation
            val signal = when (strategy) {
                BacktestStrategy.SMA_CROSSOVER -> {
                    val shortSma = subCandles.takeLast(shortPeriod).map { it.close }.average()
                    val longSma = subCandles.takeLast(longPeriod).map { it.close }.average()
                    
                    val prevSub = subCandles.dropLast(1)
                    val prevShortSma = prevSub.takeLast(shortPeriod).map { it.close }.average()
                    val prevLongSma = prevSub.takeLast(longPeriod).map { it.close }.average()

                    if (prevShortSma <= prevLongSma && shortSma > longSma) "BUY"
                    else if (prevShortSma >= prevLongSma && shortSma < longSma) "SELL"
                    else "HOLD"
                }
                BacktestStrategy.MACHINE_LEARNING -> {
                    MLSignalGenerator.generateSignal(subCandles, shortPeriod, longPeriod)
                }
                BacktestStrategy.RSI_MEAN_REVERSION -> {
                    val rsi = MLSignalGenerator.calculateRsi(subCandles, 14)
                    if (rsi <= 30) "BUY"
                    else if (rsi >= 70) "SELL"
                    else "HOLD"
                }
            }

            // 3. Trade Execution
            if (signal == "BUY" && balanceUSDT >= 10.0) {
                val totalToSpend = balanceUSDT * 0.99
                val amountToBuy = totalToSpend / currentPrice
                val fee = totalToSpend * 0.001
                balanceUSDT -= totalToSpend + fee
                balanceAsset += amountToBuy
                lastBuyPrice = currentPrice
                totalBuyTrades++

                virtualTrades.add(
                    Trade(
                        timestamp = candles[i].openTime,
                        symbol = "BACKTEST",
                        type = "BUY",
                        price = currentPrice,
                        amount = amountToBuy,
                        totalCost = totalToSpend,
                        fee = fee
                    )
                )
            } else if (signal == "SELL" && balanceAsset > 0.00001) {
                val amountToSell = balanceAsset
                val totalValue = amountToSell * currentPrice
                val fee = totalValue * 0.001
                balanceUSDT += totalValue - fee
                balanceAsset = 0.0

                val pnl = ((currentPrice - lastBuyPrice) / lastBuyPrice) * 100.0
                virtualTrades.add(
                    Trade(
                        timestamp = candles[i].openTime,
                        symbol = "BACKTEST",
                        type = "SELL",
                        price = currentPrice,
                        amount = amountToSell,
                        totalCost = totalValue,
                        fee = fee,
                        profitLoss = pnl
                    )
                )
                if (pnl > 0.0) wonTrades++
                lastBuyPrice = 0.0
            }
        }

        // Close active spot position at final price
        val finalClose = candles.last().close
        if (balanceAsset > 0.0) {
            val amountToSell = balanceAsset
            val totalValue = amountToSell * finalClose
            val fee = totalValue * 0.001
            balanceUSDT += totalValue - fee
            balanceAsset = 0.0
            val pnl = ((finalClose - lastBuyPrice) / lastBuyPrice) * 100.0
            virtualTrades.add(
                Trade(
                    timestamp = candles.last().openTime,
                    symbol = "BACKTEST",
                    type = "SELL (SETTLE)",
                    price = finalClose,
                    amount = amountToSell,
                    totalCost = totalValue,
                    fee = fee,
                    profitLoss = pnl
                )
            )
            if (pnl > 0.0) wonTrades++
        }

        val finalPortfolioWorth = balanceUSDT
        val totalReturnPct = ((finalPortfolioWorth - 10000.0) / 10000.0) * 100.0
        val winRatePct = if (totalBuyTrades > 0) (wonTrades.toDouble() / totalBuyTrades.toDouble()) * 100.0 else 0.0

        val returns = mutableListOf<Double>()
        for (i in 1 until portfolioValues.size) {
            val prev = portfolioValues[i - 1]
            if (prev > 0.0) {
                returns.add((portfolioValues[i] - prev) / prev)
            }
        }
        val averageReturn = if (returns.isNotEmpty()) returns.average() else 0.0
        val stdDevReturn = if (returns.size > 1) {
            val sumSq = returns.map { (it - averageReturn) * (it - averageReturn) }.sum()
            sqrt(sumSq / (returns.size - 1))
        } else {
            0.0
        }
        val sharpe = if (stdDevReturn > 0.0) {
            (averageReturn / stdDevReturn) * sqrt(252.0)
        } else {
            0.0
        }

        return BacktestResult(
            initialBalance = 10000.0,
            finalBalance = finalPortfolioWorth,
            totalReturnPct = totalReturnPct,
            maxDrawdownPct = maxDrawdown,
            winRatePct = winRatePct,
            totalTrades = virtualTrades.size,
            sharpeRatio = sharpe,
            trades = virtualTrades
        )
    }
}
