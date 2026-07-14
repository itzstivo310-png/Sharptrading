package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trades")
data class Trade(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val symbol: String,
    val type: String, // "BUY" or "SELL"
    val price: Double,
    val amount: Double,
    val totalCost: Double,
    val fee: Double,
    val profitLoss: Double? = null // Profit/loss percentage if it's a SELL order
)
