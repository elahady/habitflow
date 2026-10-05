package com.roziqrizal.habitflow.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.roziqrizal.habitflow.domain.CELL_NOT_DRAWN
import com.roziqrizal.habitflow.domain.HEATMAP_MAX_WEEKS
import com.roziqrizal.habitflow.domain.HEATMAP_WEEKS
import com.roziqrizal.habitflow.domain.HeatmapCell
import com.roziqrizal.habitflow.domain.heatmapCellAt
import com.roziqrizal.habitflow.domain.heatmapDate
import com.roziqrizal.habitflow.domain.heatmapStart
import com.roziqrizal.habitflow.domain.heatmapWeeksFor
import com.roziqrizal.habitflow.ui.theme.LocalHeatColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val CELL = 9.dp
private val GAP = 3.dp
private val CORNER = 2.dp
private val CELL_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))
private val STEP = CELL + GAP
private val GRID_HEIGHT = STEP * 7 - GAP
private val TOOLTIP_DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale("id", "ID"))
private val SCRUB_OUTLINE = 1.5.dp
// Jarak tooltip dari atas kotak. Cukup tinggi supaya tidak tertutup ujung jari.
private val TOOLTIP_LIFT = 32.dp
private const val SCRUB_HOLD_MILLIS = 300L
private const val TAP_AFTER_SCRUB_MILLIS = 300L

/**
 * Heatmap gaya GitHub yang mengisi lebar yang tersedia: minimal [HEATMAP_WEEKS] minggu, ditambah
 * minggu sebanyak yang muat sampai [HEATMAP_MAX_WEEKS], lalu diletakkan di tengah supaya sisa
 * ruang kiri dan kanan sama. Kolom terakhir adalah minggu ini, dan hari setelah [today] tidak
 * digambar. [levelFor] memberi level 0 sampai 4 untuk satu tanggal, atau [CELL_NOT_DRAWN] untuk
 * sel yang dilewati. Tap memanggil [onDayClick] dengan tanggal kotak terdekat, termasuk tap di
 * celah. Tahan lalu geser menampilkan tooltip tanggal yang mengikuti jari, dan melepas jari di
 * kotak juga memanggil [onDayClick]. [label] menjadi awal deskripsi aksesibilitas, diikuti
 * jumlah minggu yang tampil. [describeLevel] mengubah level jadi teks untuk tooltip dan
 * deskripsi kotak.
 */
@Composable
fun HeatmapGrid(
    today: LocalDate,
    levelFor: (LocalDate) -> Int,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Heatmap",
    showLegend: Boolean = true,
    describeLevel: (Int) -> String = { "Level $it" },
) {
    val heat = LocalHeatColors.current
    val outlineColor = MaterialTheme.colorScheme.onSurface
    val haptic = LocalHapticFeedback.current
    val currentOnDayClick by rememberUpdatedState(onDayClick)
    val currentLevelFor by rememberUpdatedState(levelFor)

    // Tahan lalu geser: kotak di bawah jari selama mode geser aktif, atau null.
    var scrubCell by remember { mutableStateOf<HeatmapCell?>(null) }
    // Tap biasa juga terbaca saat jari diangkat setelah geser, jadi diabaikan sesaat setelahnya.
    var lastScrubEnd by remember { mutableLongStateOf(0L) }

    // Mode geser aktif setelah ditahan 300 ms, lebih cepat dari tekan lama bawaan sistem.
    val baseConfig = LocalViewConfiguration.current
    val scrubConfig = remember(baseConfig) {
        object : ViewConfiguration by baseConfig {
            override val longPressTimeoutMillis: Long = SCRUB_HOLD_MILLIS
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val weeks = heatmapWeeksFor(maxWidth.value, STEP.value, GAP.value)
        val gridWidth = STEP * weeks - GAP
        val start = heatmapStart(today, weeks)

        /** Tanggal sel kalau selnya digambar, atau null untuk hari depan dan sel yang dilewati. */
        fun drawnDate(cell: HeatmapCell): LocalDate? {
            val date = heatmapDate(start, cell.col, cell.row)
            return date.takeIf { !it.isAfter(today) && currentLevelFor(it) != CELL_NOT_DRAWN }
        }

        Column(modifier = Modifier.align(Alignment.TopCenter).width(gridWidth)) {
            Box(
                modifier = Modifier
                    .size(width = gridWidth, height = GRID_HEIGHT)
                    .semantics { contentDescription = "$label $weeks minggu terakhir" },
            ) {
                CompositionLocalProvider(LocalViewConfiguration provides scrubConfig) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(today, weeks) {
                                detectTapGestures { offset ->
                                    if (SystemClock.uptimeMillis() - lastScrubEnd < TAP_AFTER_SCRUB_MILLIS) {
                                        return@detectTapGestures
                                    }
                                    heatmapCellAt(offset.x, offset.y, STEP.toPx(), GAP.toPx(), weeks)
                                        ?.let(::drawnDate)
                                        ?.let(currentOnDayClick)
                                }
                            }
                            .pointerInput(today, weeks) {
                                fun cellAt(offset: Offset) =
                                    heatmapCellAt(offset.x, offset.y, STEP.toPx(), GAP.toPx(), weeks)

                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        scrubCell = cellAt(offset)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val cell = cellAt(change.position)
                                        if (cell != scrubCell) {
                                            scrubCell = cell
                                            if (cell != null && drawnDate(cell) != null) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val date = scrubCell?.let(::drawnDate)
                                        scrubCell = null
                                        lastScrubEnd = SystemClock.uptimeMillis()
                                        // Lepas di luar grid atau di sel yang tidak digambar berarti batal.
                                        date?.let(currentOnDayClick)
                                    },
                                    onDragCancel = {
                                        scrubCell = null
                                        lastScrubEnd = SystemClock.uptimeMillis()
                                    },
                                )
                            },
                    ) {
                        drawGrid(start, weeks, today, levelFor, heat::forLevel)
                        scrubCell?.takeIf { drawnDate(it) != null }?.let { drawScrubOutline(it, outlineColor) }
                    }
                }

                scrubCell?.let { cell ->
                    drawnDate(cell)?.let { date ->
                        ScrubTooltip(
                            cell = cell,
                            text = "${date.format(TOOLTIP_DATE_FORMAT)} · ${describeLevel(currentLevelFor(date))}",
                        )
                    }
                }

                // Canvas tidak punya node aksesibilitas per kotak, jadi setiap kotak yang digambar
                // diberi node transparan sendiri supaya TalkBack bisa membaca dan membuka harinya.
                for (col in 0 until weeks) {
                    for (row in 0..6) {
                        val date = heatmapDate(start, col, row)
                        val level = levelFor(date)
                        if (date.isAfter(today) || level == CELL_NOT_DRAWN) continue
                        Box(
                            modifier = Modifier
                                .offset(x = STEP * col, y = STEP * row)
                                .size(CELL)
                                .semantics {
                                    contentDescription = "${date.format(CELL_DATE_FORMAT)}, ${describeLevel(level)}"
                                    onClick(label = "Lihat detail hari") {
                                        currentOnDayClick(date)
                                        true
                                    }
                                },
                        )
                    }
                }
            }

            if (showLegend) {
                Spacer(Modifier.size(8.dp))
                HeatmapLegend(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun DrawScope.drawGrid(
    start: LocalDate,
    weeks: Int,
    today: LocalDate,
    levelFor: (LocalDate) -> Int,
    colorFor: (Int) -> Color,
) {
    val stepPx = STEP.toPx()
    val cellPx = CELL.toPx()
    val radius = CornerRadius(CORNER.toPx())

    for (col in 0 until weeks) {
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

/** Bingkai di luar kotak yang sedang disentuh saat mode geser. */
private fun DrawScope.drawScrubOutline(cell: HeatmapCell, color: Color) {
    val stepPx = STEP.toPx()
    val strokePx = SCRUB_OUTLINE.toPx()
    // Garis berpusat di tepi persegi, jadi persegi diperbesar setengah tebal garis
    // supaya seluruh garis jatuh di luar kotak.
    val grow = strokePx / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(cell.col * stepPx - grow, cell.row * stepPx - grow),
        size = Size(CELL.toPx() + strokePx, CELL.toPx() + strokePx),
        cornerRadius = CornerRadius(CORNER.toPx() + grow),
        style = Stroke(width = strokePx),
    )
}

/**
 * Tooltip tanggal di atas kotak yang sedang disentuh. Memakai [Popup] supaya tidak terpotong
 * oleh kartu, dan digeser supaya tidak keluar dari tepi layar.
 */
@Composable
private fun ScrubTooltip(cell: HeatmapCell, text: String) {
    val density = LocalDensity.current
    val position = remember(cell, density) {
        with(density) {
            TooltipPosition(
                cellX = (cell.col * STEP.toPx()).roundToInt(),
                cellY = (cell.row * STEP.toPx()).roundToInt(),
                cellSize = CELL.roundToPx(),
                lift = TOOLTIP_LIFT.roundToPx(),
            )
        }
    }
    Popup(popupPositionProvider = position) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.inverseSurface, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private class TooltipPosition(
    private val cellX: Int,
    private val cellY: Int,
    private val cellSize: Int,
    private val lift: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centerX = anchorBounds.left + cellX + cellSize / 2
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (centerX - popupContentSize.width / 2).coerceIn(0, maxX)
        val y = (anchorBounds.top + cellY - lift - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
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
