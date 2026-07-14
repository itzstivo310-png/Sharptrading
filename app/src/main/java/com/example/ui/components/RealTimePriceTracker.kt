package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Candle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RealTimePriceTracker(
    symbol: String,
    candles: List<Candle>,
    isLoading: Boolean,
    bids: List<Pair<Double, Double>>,
    asks: List<Pair<Double, Double>>,
    onSymbolSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val supportedSymbols = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "ADAUSDT", "DOGEUSDT")
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Selector Tab Strip
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "SELECT CRYPTOCURRENCY FEED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    supportedSymbols.forEach { sym ->
                        val isSelected = sym == symbol
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF1F5F9))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF3B82F6) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSymbolSelected(sym) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when (sym) {
                                    "BTCUSDT" -> Icons.Default.CurrencyBitcoin
                                    "ETHUSDT" -> Icons.Default.CurrencyExchange
                                    "SOLUSDT" -> Icons.Default.Cyclone
                                    "ADAUSDT" -> Icons.Default.Token
                                    else -> Icons.Default.Savings
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = sym,
                                    tint = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = sym.replace("USDT", ""),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Real-time Price Info Header Card
        val latestPrice = candles.lastOrNull()?.close ?: 0.0
        val previousPrice = if (candles.size >= 2) candles[candles.size - 2].close else latestPrice
        val priceChange = latestPrice - previousPrice
        val percentChange = if (previousPrice > 0.0) (priceChange / previousPrice) * 100 else 0.0

        val maxPrice = if (candles.isNotEmpty()) candles.maxOf { it.high } else 0.0
        val minPrice = if (candles.isNotEmpty()) candles.minOf { it.low } else 0.0
        val totalVolume = if (candles.isNotEmpty()) candles.sumOf { it.volume } else 0.0

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pulsing Green Live Badge
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${symbol.uppercase()} REAL-TIME STREAM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = Color(0xFF3B82F6)
                        )
                    } else {
                        Text(
                            text = "LIVE FEED ACTIVE",
                            fontSize = 9.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = if (latestPrice > 0.0) String.format(Locale.US, "%,.2f USDT", latestPrice) else "Loading...",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }

                    // Percent capsule
                    val sign = if (percentChange >= 0) "+" else ""
                    val capsuleColor = if (percentChange >= 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                    val textColor = if (percentChange >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(capsuleColor)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$sign${String.format(Locale.US, "%.3f%%", percentChange)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(14.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("24h High", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text(
                            text = if (maxPrice > 0.0) String.format(Locale.US, "%,.1f", maxPrice) else "--",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }
                    Column {
                        Text("24h Low", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text(
                            text = if (minPrice > 0.0) String.format(Locale.US, "%,.1f", minPrice) else "--",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Cumulative Vol", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text(
                            text = if (totalVolume > 0.0) String.format(Locale.US, "%,.2f", totalVolume) else "--",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }

        // 3. Interactive Web-style Recharts-equivalent Canvas Chart
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            if (candles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                }
            } else {
                var touchOffset by remember { mutableStateOf<Offset?>(null) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 12.dp, top = 16.dp)
                ) {
                    val finalMin = candles.minOf { it.low } * 0.9995
                    val finalMax = candles.maxOf { it.high } * 1.0005
                    val finalRange = (finalMax - finalMin).coerceAtLeast(0.001)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 52.dp, start = 8.dp)
                            .pointerInput(candles) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        touchOffset = offset
                                        tryAwaitRelease()
                                        touchOffset = null
                                    }
                                )
                            }
                            .pointerInput(candles) {
                                detectDragGestures(
                                    onDragStart = { offset -> touchOffset = offset },
                                    onDrag = { change, _ ->
                                        touchOffset = change.position
                                    },
                                    onDragEnd = { touchOffset = null },
                                    onDragCancel = { touchOffset = null }
                                )
                            }
                    ) {
                        val width = size.width
                        val height = size.height

                        // Draw Grid Horizontal Lines
                        val gridCount = 4
                        for (i in 0..gridCount) {
                            val y = height * i / gridCount
                            val priceValue = finalMax - (finalRange * i / gridCount)

                            drawLine(
                                color = Color(0xFFF1F5F9),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1f
                            )

                            // Price label on right border
                            val priceText = String.format(Locale.US, "%,.2f", priceValue)
                            drawText(
                                textMeasurer = textMeasurer,
                                text = priceText,
                                style = TextStyle(color = Color(0xFF64748B), fontSize = 9.sp),
                                topLeft = Offset(width + 6.dp.toPx(), y - 12.sp.toPx() / 2f)
                            )
                        }

                        val stepX = width / (candles.size - 1).coerceAtLeast(1)

                        fun getCanvasY(price: Double): Float {
                            return (height - ((price - finalMin) / finalRange * height)).toFloat()
                        }

                        // Generate Price Line Path
                        val linePath = Path()
                        val areaPath = Path()

                        candles.forEachIndexed { index, candle ->
                            val x = index * stepX
                            val y = getCanvasY(candle.close)

                            if (index == 0) {
                                linePath.moveTo(x, y)
                                areaPath.moveTo(x, height)
                                areaPath.lineTo(x, y)
                            } else {
                                linePath.lineTo(x, y)
                                areaPath.lineTo(x, y)
                            }

                            if (index == candles.size - 1) {
                                areaPath.lineTo(x, height)
                                areaPath.close()
                            }
                        }

                        // Draw Gradient Area Fill under the curve
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6).copy(alpha = 0.16f),
                                    Color(0xFF3B82F6).copy(alpha = 0.0f)
                                ),
                                startY = 0f,
                                endY = height
                            )
                        )

                        // Draw the glowing primary Line
                        drawPath(
                            path = linePath,
                            color = Color(0xFF2563EB),
                            style = Stroke(width = 4f)
                        )

                        // If touch occurs, compute nearest point (Crosshair & Tooltip overlay)
                        touchOffset?.let { offset ->
                            val touchX = offset.x.coerceIn(0f, width)
                            val nearestIdx = (touchX / stepX + 0.5f).toInt().coerceIn(0, candles.size - 1)
                            val nearestCandle = candles[nearestIdx]

                            val crossX = nearestIdx * stepX
                            val crossY = getCanvasY(nearestCandle.close)

                            // Draw vertical dashed line
                            drawLine(
                                color = Color(0xFF3B82F6),
                                start = Offset(crossX, 0f),
                                end = Offset(crossX, height),
                                strokeWidth = 1.5f
                            )

                            // Draw intersection point
                            drawCircle(
                                color = Color(0xFF2563EB),
                                radius = 7f,
                                center = Offset(crossX, crossY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.5f,
                                center = Offset(crossX, crossY)
                            )

                            // Format index details to render on bottom labels
                            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            val timeStr = sdf.format(Date(nearestCandle.openTime))
                            val toolTipText = "${String.format(Locale.US, "%,.2f", nearestCandle.close)} USDT @ $timeStr"

                            val rectWidth = 145.dp.toPx()
                            val rectHeight = 22.dp.toPx()
                            val toolTipX = (crossX - rectWidth / 2).coerceIn(0f, width - rectWidth)
                            val toolTipY = (crossY - rectHeight - 10f).coerceAtLeast(4f)

                            // Tooltip box background
                            drawRoundRect(
                                color = Color(0xFF1E293B),
                                topLeft = Offset(toolTipX, toolTipY),
                                size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                            )

                            // Tooltip text centered
                            drawText(
                                textMeasurer = textMeasurer,
                                text = toolTipText,
                                style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                topLeft = Offset(toolTipX + 6.dp.toPx(), toolTipY + 4.dp.toPx())
                            )
                        }
                    }
                }
            }
        }

        // 4. Live Order Book Panel (Asks & Bids)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ListAlt,
                            contentDescription = "Order Book",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Order Book & Market Depth",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SPREAD: ~0.03%",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Headers
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Price (USDT)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = "Amount (${symbol.replace("USDT", "")})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Depth Volume",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val maxVolume = (asks.map { it.second } + bids.map { it.second }).maxOrNull() ?: 1.0

                // Render Asks (SELLS) - Sorted high to low to stack correctly
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    asks.take(4).reversed().forEach { (price, size) ->
                        val ratio = (size / maxVolume).coerceIn(0.01, 1.0)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        ) {
                            // Volume bar fill
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(ratio.toFloat())
                                    .align(Alignment.CenterEnd)
                                    .background(Color(0xFFFEE2E2).copy(alpha = 0.5f))
                            )

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%,.2f", price),
                                    fontSize = 11.sp,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.2f)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.4f", size),
                                    fontSize = 11.sp,
                                    color = Color(0xFF334155),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = String.format(Locale.US, "%,.2f", price * size),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Current Spread Separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(Color(0xFFF8FAFC))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SPREAD LIMIT GATEWAY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569),
                        letterSpacing = 1.sp
                    )
                }

                // Render Bids (BUYS)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    bids.take(4).forEach { (price, size) ->
                        val ratio = (size / maxVolume).coerceIn(0.01, 1.0)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        ) {
                            // Volume bar fill
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(ratio.toFloat())
                                    .align(Alignment.CenterEnd)
                                    .background(Color(0xFFD1FAE5).copy(alpha = 0.5f))
                            )

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%,.2f", price),
                                    fontSize = 11.sp,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.2f)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.4f", size),
                                    fontSize = 11.sp,
                                    color = Color(0xFF334155),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = String.format(Locale.US, "%,.2f", price * size),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
