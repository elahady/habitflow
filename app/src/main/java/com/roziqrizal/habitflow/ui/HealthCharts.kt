package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Satu titik grafik: tanggal dan nilainya. */
data class ChartPoint(val date: LocalDate, val value: Double)

/** Satu garis grafik. Titik sudah urut menurut waktu. [strokeDp] membedakan garis selain lewat warna. */
data class ChartSeries(val points: List<ChartPoint>, val color: Color, val strokeDp: Float = 2f)

private val AXIS_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("id", "ID"))

/**
 * Grafik garis sederhana (tahap 21), digambar dengan Canvas seperti heatmap. Sumbu x dari [from] sampai [to] dengan tiga
 * label tanggal, sumbu y dengan label nilai terkecil dan terbesar. [targetLine] digambar putus-putus. Kategori dan angka
 * penting ada di teks kartu; grafik hanya memperlihatkan arah.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    from: LocalDate,
    to: LocalDate,
    description: String,
    modifier: Modifier = Modifier,
    targetLine: Double? = null,
    formatValue: (Double) -> String = { formatDecimal1(it) },
) {
    val measurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val targetColor = MaterialTheme.colorScheme.outline

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description },
    ) {
        val values = series.flatMap { s -> s.points.map { it.value } } + listOfNotNull(targetLine)
        if (values.isEmpty()) return@Canvas
        var low = values.min()
        var high = values.max()
        if (high - low < 1.0) {
            low -= 0.5
            high += 0.5
        }
        val pad = (high - low) * 0.1
        low -= pad
        high += pad

        val leftGutter = 40.dp.toPx()
        val bottomGutter = 22.dp.toPx()
        val topGutter = 8.dp.toPx()
        val rightGutter = 8.dp.toPx()
        val plotLeft = leftGutter
        val plotRight = size.width - rightGutter
        val plotTop = topGutter
        val plotBottom = size.height - bottomGutter

        val spanDays = ChronoUnit.DAYS.between(from, to).coerceAtLeast(1).toFloat()
        fun x(date: LocalDate): Float {
            val offset = ChronoUnit.DAYS.between(from, date).toFloat().coerceIn(0f, spanDays)
            return plotLeft + (plotRight - plotLeft) * offset / spanDays
        }
        fun y(value: Double): Float =
            (plotBottom - (plotBottom - plotTop) * ((value - low) / (high - low)).toFloat())

        // Garis dasar dan atas sebagai pegangan mata.
        drawLine(gridColor, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), strokeWidth = 1.dp.toPx())
        drawLine(gridColor, Offset(plotLeft, plotTop), Offset(plotRight, plotTop), strokeWidth = 1.dp.toPx())

        targetLine?.let {
            drawLine(
                color = targetColor,
                start = Offset(plotLeft, y(it)),
                end = Offset(plotRight, y(it)),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
            )
        }

        series.forEach { s ->
            val pts = s.points.map { Offset(x(it.date), y(it.value)) }
            for (i in 0 until pts.size - 1) {
                drawLine(s.color, pts[i], pts[i + 1], strokeWidth = s.strokeDp.dp.toPx(), cap = StrokeCap.Round)
            }
            pts.forEach { drawCircle(s.color, radius = 3.5.dp.toPx(), center = it) }
        }

        // Label sumbu y: nilai terbesar dan terkecil yang tampil.
        fun label(text: String, at: Offset) {
            drawText(measurer, text, topLeft = at, style = labelStyle)
        }
        label(formatValue(high - pad), Offset(0f, plotTop - 2.dp.toPx()))
        label(formatValue(low + pad), Offset(0f, plotBottom - 14.dp.toPx()))

        // Tiga label tanggal: awal, tengah, akhir.
        val mid = from.plusDays(ChronoUnit.DAYS.between(from, to) / 2)
        listOf(from to Alignment.START, mid to Alignment.CENTER, to to Alignment.END).forEach { (date, align) ->
            val text = date.format(AXIS_DATE)
            val width = measurer.measure(text, labelStyle).size.width
            val px = when (align) {
                Alignment.START -> plotLeft
                Alignment.CENTER -> (plotLeft + plotRight) / 2 - width / 2
                Alignment.END -> plotRight - width
            }
            label(text, Offset(px, plotBottom + 4.dp.toPx()))
        }
    }
}

private enum class Alignment { START, CENTER, END }
