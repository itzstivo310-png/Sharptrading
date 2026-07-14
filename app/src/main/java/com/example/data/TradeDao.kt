package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY timestamp DESC")
    fun getAllTrades(): Flow<List<Trade>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: Trade)

    @Query("DELETE FROM trades")
    suspend fun clearTrades()

    @Query("SELECT * FROM bot_config WHERE id = 1 LIMIT 1")
    fun getBotConfig(): Flow<BotConfig?>

    @Query("SELECT * FROM bot_config WHERE id = 1 LIMIT 1")
    suspend fun getBotConfigDirect(): BotConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBotConfig(config: BotConfig)
}
