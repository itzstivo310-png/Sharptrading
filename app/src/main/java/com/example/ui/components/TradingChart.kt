package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Candle
import java.util.*

@Composable
fun TradingChart(
    candles: List<Candle>,
    smaShort: List<Double>,
    smaLong: List<Double>,
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) {
        Box(
            modifier = modifier
                .background(Color(0xFFF7F9FC))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No market data available yet",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    // Limit visible data to the last 30 points to keep the chart clean and spacious
    val maxPoints = 30
    val visibleCandles = candles.takeLast(maxPoints)
    val visibleSmaShort = smaShort.takeLast(maxPoints)
    val visibleSmaLong = smaLong.takeLast(maxPoints)

    // Calculate dynamic scaling ranges
    val priceMin = visibleCandles.minOf { it.low }.coerceAtMost(
        visibleSmaShort.filter { it > 0.0 }.minInList(visibleCandles.minOf { it.low })
    ).coerceAtMost(
        visibleSmaLong.filter { it > 0.0 }.minInList(visibleCandles.minOf { it.low })
    )

    val priceMax = visibleCandles.maxOf { it.high }.coerceAtLeast(
        visibleSmaShort.filter { it > 0.0 }.maxInList(visibleCandles.maxOf { it.high })
    ).coerceAtLeast(
        visibleSmaLong.filter { it > 0.0 }.maxInList(visibleCandles.maxOf { it.high })
    )

    val priceRange = (priceMax - priceMin).coerceAtLeast(0.01)
    // Add 10% safety padding top & bottom
    val paddedMin = priceMin - (priceRange * 0.1)
    val paddedMax = priceMax + (priceRange * 0.1)
    val finalRange = paddedMax - paddedMin

    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier.background(Color.White)) {
        // Legend indicator row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF16A34A))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Price", color = Color(0xFF1A1C1E), fontSize = 11.sp)

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF0061A4))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Short SMA", color = Color(0xFF0061A4), fontSize = 11.sp)

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFFF59E0B))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Long SMA", color = Color(0xFFF59E0B), fontSize = 11.sp)
            }
            Text(
                text = "Latest: ${String.format(Locale.US, "%,.2f", visibleCandles.last().close)}",
                color = Color(0xFF1A1C1E),
                fontSize = 11.sp,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(end = 55.dp, top = 8.dp, bottom = 12.dp)
        ) {
            val width = size.width
            val height = size.height

            // 1. Draw horizontal grid lines and prices
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = height * i / gridLines
                val priceAtLine = paddedMax - (finalRange * i / gridLines)
                
                // Grid line
                drawLine(
                    color = Color(0xFFE1E2E5),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )

                // Price label on the right margin
                val label = String.format(Locale.US, "%,.0f", priceAtLine)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = TextStyle(color = Color(0xFF64748B), fontSize = 10.sp),
                    topLeft = Offset(width + 6.dp.toPx(), y - 14.sp.toPx() / 2f)
                )
            }

            val stepX = width / (visibleCandles.size - 1).coerceAtLeast(1)

            // Helper to translate price to canvas Y coordinate
            fun getCanvasY(price: Double): Float {
                return (height - ((price - paddedMin) / finalRange * height)).toFloat()
            }

            // 2. Draw Price Area Gradient Fill & Line
            val pricePath = Path()
            val fillPath = Path()

            visibleCandles.forEachIndexed { index, candle ->
                val x = index * stepX
                val y = getCanvasY(candle.close)

                if (index == 0) {
                    pricePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    pricePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }

                if (index == visibleCandles.size - 1) {
                    fillPath.lineTo(x, height)
                    fillPath.close()
                }
            }

            // Draw area gradient
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF16A34A).copy(alpha = 0.15f),
                        Color(0xFF16A34A).copy(alpha = 0.0f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw solid price line
            drawPath(
                path = pricePath,
                color = Color(0xFF16A34A),
                style = Stroke(width = 4f)
            )

            // 3. Draw Short SMA line
            val shortPath = Path()
            var shortStarted = false
            visibleSmaShort.forEachIndexed { index, valSma ->
                if (valSma > 0.0) {
                    val x = index * stepX
                    val y = getCanvasY(valSma)
                    if (!shortStarted) {
                        shortPath.moveTo(x, y)
                        shortStarted = true
                    } else {
                        shortPath.lineTo(x, y)
                    }
                }
            }
            if (shortStarted) {
                drawPath(
                    path = shortPath,
                    color = Color(0xFF0061A4),
                    style = Stroke(width = 3f)
                )
            }

            // 4. Draw Long SMA line
            val longPath = Path()
            var longStarted = false
            visibleSmaLong.forEachIndexed { index, valSma ->
                if (valSma > 0.0) {
                    val x = index * stepX
                    val y = getCanvasY(valSma)
                    if (!longStarted) {
                        longPath.moveTo(x, y)
                        longStarted = true
                    } else {
                        longPath.lineTo(x, y)
                    }
                }
            }
            if (longStarted) {
                drawPath(
                    path = longPath,
                    color = Color(0xFFF59E0B),
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}

// Helpers for list collections
private fun List<Double>.minInList(default: Double): Double {
    return if (this.isEmpty()) default else this.min()
}

private fun List<Double>.maxInList(default: Double): Double {
    return if (this.isEmpty()) default else this.max()
}
