package com.example.data

import kotlinx.coroutines.flow.Flow

class TradeRepository(private val tradeDao: TradeDao) {
    val allTrades: Flow<List<Trade>> = tradeDao.getAllTrades()
    val botConfig: Flow<BotConfig?> = tradeDao.getBotConfig()

    suspend fun insertTrade(trade: Trade) {
        tradeDao.insertTrade(trade)
    }

    suspend fun clearTrades() {
        tradeDao.clearTrades()
    }

    suspend fun getBotConfigDirect(): BotConfig? {
        return tradeDao.getBotConfigDirect()
    }

    suspend fun saveBotConfig(config: BotConfig) {
        tradeDao.insertBotConfig(config)
    }
}
