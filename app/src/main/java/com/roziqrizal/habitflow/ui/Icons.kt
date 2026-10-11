package com.roziqrizal.habitflow.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * Ikon garis (gaya Feather, 24x24, tebal 2) untuk tab bawah dan kartu Hari ini, sama dengan mockup di
 * docs/design/README.md. Digambar dari path SVG supaya tidak butuh pustaka ikon. Warna ikon dari tint
 * pemanggil, jadi garisnya hitam polos di sini.
 */

private fun feather(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()

object AppIcons {
    val Heart = feather(
        "heart",
        "M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z",
    )
    val Gauge = feather("gauge", "m12 14 4-4", "M3.34 19a10 10 0 1 1 17.32 0")
    val Droplet = feather("droplet", "M12 2.69l5.66 5.66a8 8 0 1 1-11.31 0z")
    val Activity = feather("activity", "M22 12h-4l-3 9L9 3l-3 9H2")
    val Home = feather("home", "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12h6v10")
    val Briefcase = feather(
        "briefcase",
        "M4 7h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z",
        "M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16",
    )
    val Users = feather(
        "users",
        "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2",
        "M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8z",
        "M23 21v-2a4 4 0 0 0-3-3.87",
        "M16 3.13a4 4 0 0 1 0 7.75",
    )
    val BarChart = feather("bar-chart", "M18 20V10", "M12 20V4", "M6 20v-6")
    val CheckSquare = feather("check-square", "M9 11l3 3L22 4", "M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11")
    val Info = feather("info", "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M12 16v-4", "M12 8h.01")
}
