package com.example.data

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

interface BinanceApiService {
    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1h",
        @Query("limit") limit: Int = 50
    ): List<List<Any>>

    companion object {
        private const val BASE_URL = "https://api.binance.com/"

        val instance: BinanceApiService by lazy {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(BinanceApiService::class.java)
        }
    }
}

// Helper to convert raw list of any to candle objects safely
fun parseKlines(raw: List<List<Any>>): List<Candle> {
    return raw.mapNotNull { item ->
        try {
            if (item.size >= 6) {
                val openTime = when (val time = item[0]) {
                    is Double -> time.toLong()
                    is String -> time.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val open = (item[1] as? String)?.toDoubleOrNull() ?: 0.0
                val high = (item[2] as? String)?.toDoubleOrNull() ?: 0.0
                val low = (item[3] as? String)?.toDoubleOrNull() ?: 0.0
                val close = (item[4] as? String)?.toDoubleOrNull() ?: 0.0
                val volume = (item[5] as? String)?.toDoubleOrNull() ?: 0.0

                Candle(
                    openTime = openTime,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
