package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.CategorySpendSummary
import com.example.domain.model.DailySpendPoint
import com.example.domain.model.MoneyFormatter
import com.example.ui.theme.LocalSpendWiseColors
import com.example.ui.theme.SpendWiseTextStyles
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Interactive Category Donut Chart built with Compose Canvas.
 * Supports animated sweep reveal and tap-to-inspect slices.
 */
@Composable
fun CategoryDonutChart(
    items: List<CategorySpendSummary>,
    currencyCode: String,
    hideBalances: Boolean,
    chartSize: Dp = 210.dp,
    strokeWidth: Dp = 28.dp,
    selectedIndex: Int = 0,
    onSelectSlice: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sweepProgress = remember { Animatable(0f) }
    LaunchedEffect(items) {
        sweepProgress.snapTo(0f)
        sweepProgress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }

    val safeIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val activeItem = items.getOrNull(safeIndex)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(chartSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(items) {
                    detectTapGestures { offset ->
                        if (items.isEmpty()) return@detectTapGestures
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = hypot(dx, dy)
                        val outerR = minOf(cx, cy)
                        if (dist < outerR * 0.35f || dist > outerR) return@detectTapGestures

                        var angle = (atan2(dy, dx) * 180.0 / PI).toFloat() + 90f
                        if (angle < 0f) angle += 360f

                        var cumulative = 0f
                        items.forEachIndexed { idx, summary ->
                            val sliceAngle = (summary.percentage / 100f) * 360f
                            if (angle in cumulative..(cumulative + sliceAngle)) {
                                onSelectSlice(idx)
                                return@detectTapGestures
                            }
                            cumulative += sliceAngle
                        }
                    }
                }
        ) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            if (items.isEmpty()) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx)
                )
                return@Canvas
            }

            var startAngle = -90f
            val gapDegrees = if (items.size > 1) 2.5f else 0f

            items.forEachIndexed { idx, summary ->
                val rawSweep = (summary.percentage / 100f) * 360f * sweepProgress.value
                val sweep = (rawSweep - gapDegrees).coerceAtLeast(1.5f)
                val isSelected = idx == safeIndex
                val activeStroke = if (isSelected) strokePx + 5.dp.toPx() else strokePx

                drawArc(
                    color = Color(summary.category.colorHex),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = activeStroke, cap = StrokeCap.Butt)
                )
                startAngle += rawSweep
            }
        }

        // Center callout matching the SpendWise visual identity
        if (activeItem != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = CategoryIcons.forKey(activeItem.category.iconKey),
                    contentDescription = activeItem.category.categoryName,
                    tint = Color(activeItem.category.colorHex),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = activeItem.category.categoryName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${activeItem.percentage.toInt()}%",
                    style = SpendWiseTextStyles.CardAmount,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = MoneyFormatter.formatMinorUnits(
                        activeItem.amountMinorUnits,
                        currencyCode,
                        isHidden = hideBalances
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalSpendWiseColors.current.mutedText,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = "No expenses",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Interactive Daily / Monthly Spending Trend Area & Line Chart built with Compose Canvas.
 */
@Composable
fun SpendingTrendLineChart(
    points: List<DailySpendPoint>,
    currencyCode: String,
    hideBalances: Boolean,
    chartHeight: Dp = 155.dp,
    modifier: Modifier = Modifier
) {
    val swColors = LocalSpendWiseColors.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = swColors.expense
    val fillStart = lineColor.copy(alpha = 0.28f)
    val fillEnd = lineColor.copy(alpha = 0.02f)

    var selectedIdx by remember(points) { mutableIntStateOf((points.size - 1).coerceAtLeast(0)) }
    val selectedPoint = points.getOrNull(selectedIdx)

    Column(modifier = modifier.fillMaxWidth()) {
        if (selectedPoint != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedPoint.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = MoneyFormatter.formatMinorUnits(
                        selectedPoint.expenseMinorUnits,
                        currencyCode,
                        isHidden = hideBalances
                    ),
                    style = SpendWiseTextStyles.RowAmount,
                    color = lineColor
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.size <= 1) return@detectTapGestures
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        selectedIdx = (fraction * (points.size - 1)).toInt().coerceIn(0, points.lastIndex)
                    }
                }
        ) {
            val padTop = 12.dp.toPx()
            val padBottom = 14.dp.toPx()
            val padH = 10.dp.toPx()
            val usableW = (size.width - padH * 2).coerceAtLeast(1f)
            val usableH = (size.height - padTop - padBottom).coerceAtLeast(1f)

            // Horizontal subtle guide lines
            for (i in 0..3) {
                val y = padTop + (usableH / 3f) * i
                drawLine(
                    color = gridColor,
                    start = Offset(padH, y),
                    end = Offset(size.width - padH, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (points.isEmpty()) return@Canvas

            val maxVal = points.maxOf { it.expenseMinorUnits }.coerceAtLeast(1L)
            val coords = points.mapIndexed { idx, pt ->
                val x = if (points.size == 1) {
                    size.width / 2f
                } else {
                    padH + (idx.toFloat() / (points.size - 1).toFloat()) * usableW
                }
                val normY = pt.expenseMinorUnits.toFloat() / maxVal.toFloat()
                val y = padTop + usableH * (1f - normY)
                Offset(x, y)
            }

            if (coords.size >= 2) {
                val linePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) {
                        val prev = coords[i - 1]
                        val curr = coords[i]
                        val midX = (prev.x + curr.x) / 2f
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(coords.last().x, padTop + usableH)
                    lineTo(coords.first().x, padTop + usableH)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(listOf(fillStart, fillEnd), startY = padTop, endY = padTop + usableH)
                )
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            coords.forEachIndexed { idx, pt ->
                val isSelected = idx == selectedIdx
                drawCircle(
                    color = if (isSelected) lineColor else lineColor.copy(alpha = 0.7f),
                    radius = if (isSelected) 6.dp.toPx() else 3.5.dp.toPx(),
                    center = pt
                )
                if (isSelected) {
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = pt
                    )
                }
            }
        }

        if (points.size >= 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = points.first().label,
                    style = MaterialTheme.typography.labelSmall,
                    color = swColors.mutedText
                )
                if (points.size >= 3) {
                    Text(
                        text = points[points.size / 2].label,
                        style = MaterialTheme.typography.labelSmall,
                        color = swColors.mutedText
                    )
                }
                Text(
                    text = points.last().label,
                    style = MaterialTheme.typography.labelSmall,
                    color = swColors.mutedText
                )
            }
        }
    }
}

/**
 * Clean dual bar chart comparing Income (Emerald) vs Expense (Coral Orange).
 */
@Composable
fun IncomeExpenseComparisonChart(
    incomeMinorUnits: Long,
    expenseMinorUnits: Long,
    currencyCode: String,
    hideBalances: Boolean,
    modifier: Modifier = Modifier
) {
    val swColors = LocalSpendWiseColors.current
    val maxVal = maxOf(incomeMinorUnits, expenseMinorUnits, 1L)
    val incomeRatio = (incomeMinorUnits.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
    val expenseRatio = (expenseMinorUnits.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ComparisonBarRow(
            label = "Income",
            amount = MoneyFormatter.formatMinorUnits(incomeMinorUnits, currencyCode, isHidden = hideBalances),
            fraction = incomeRatio,
            barColor = swColors.income
        )
        ComparisonBarRow(
            label = "Expenses",
            amount = MoneyFormatter.formatMinorUnits(expenseMinorUnits, currencyCode, isHidden = hideBalances),
            fraction = expenseRatio,
            barColor = swColors.expense
        )
    }
}

@Composable
private fun ComparisonBarRow(
    label: String,
    amount: String,
    fraction: Float,
    barColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = amount,
                style = SpendWiseTextStyles.RowAmount.copy(fontWeight = FontWeight.Bold),
                color = barColor
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.03f))
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}
