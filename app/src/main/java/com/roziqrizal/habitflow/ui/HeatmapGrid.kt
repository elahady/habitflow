package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.CELL_NOT_DRAWN
import com.roziqrizal.habitflow.domain.HEATMAP_WEEKS
import com.roziqrizal.habitflow.domain.heatmapDate
import com.roziqrizal.habitflow.domain.heatmapStart
import com.roziqrizal.habitflow.ui.theme.LocalHeatColors
import java.time.LocalDate

private val CELL = 9.dp
private val GAP = 3.dp
private val CORNER = 2.dp
private val STEP = CELL + GAP
private val GRID_WIDTH = STEP * HEATMAP_WEEKS - GAP
private val GRID_HEIGHT = STEP * 7 - GAP

/**
 * Heatmap 26 minggu gaya GitHub. Kolom terakhir adalah minggu ini, dan hari setelah
 * [today] tidak digambar. [levelFor] memberi level 0 sampai 4 untuk satu tanggal, atau
 * [CELL_NOT_DRAWN] untuk sel yang dilewati. Tap pada kotak memanggil [onDayClick] dengan
 * tanggal kotak itu.
 */
@Composable
fun HeatmapGrid(
    today: LocalDate,
    levelFor: (LocalDate) -> Int,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    description: String = "Heatmap 26 minggu terakhir",
    showLegend: Boolean = true,
) {
    val heat = LocalHeatColors.current
    val start = heatmapStart(today)
    val currentOnDayClick by rememberUpdatedState(onDayClick)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .size(width = GRID_WIDTH, height = GRID_HEIGHT)
                .semantics { contentDescription = description }
                .pointerInput(today) {
                    detectTapGestures { offset ->
                        val stepPx = STEP.toPx()
                        val cellPx = CELL.toPx()
                        val col = (offset.x / stepPx).toInt()
                        val row = (offset.y / stepPx).toInt()
                        val onCell = offset.x - col * stepPx < cellPx && offset.y - row * stepPx < cellPx
                        if (onCell && col in 0 until HEATMAP_WEEKS && row in 0..6) {
                            val date = heatmapDate(start, col, row)
                            if (!date.isAfter(today)) currentOnDayClick(date)
                        }
                    }
                },
        ) {
            drawGrid(start, today, levelFor, heat::forLevel)
        }

        if (showLegend) {
            Spacer(Modifier.size(8.dp))
            HeatmapLegend()
        }
    }
}

private fun DrawScope.drawGrid(
    start: LocalDate,
    today: LocalDate,
    levelFor: (LocalDate) -> Int,
    colorFor: (Int) -> Color,
) {
    val stepPx = STEP.toPx()
    val cellPx = CELL.toPx()
    val radius = CornerRadius(CORNER.toPx())

    for (col in 0 until HEATMAP_WEEKS) {
        for (row in 0..6) {
            val date = heatmapDate(start, col, row)
            val level = levelFor(date)
            if (date.isAfter(today) || level == CELL_NOT_DRAWN) continue
            drawRoundRect(
                color = colorFor(level),
                topLeft = Offset(col * stepPx, row * stepPx),
                size = Size(cellPx, cellPx),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    val heat = LocalHeatColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier,
    ) {
        Text(
            text = "Less",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        for (level in 0..4) {
            LegendCell(color = heat.forLevel(level))
            Spacer(Modifier.width(GAP))
        }
        Spacer(Modifier.width(3.dp))
        Text(
            text = "More",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendCell(color: Color) {
    Box(
        modifier = Modifier
            .size(CELL)
            .background(color, shape = RoundedCornerShape(CORNER)),
    )
}
