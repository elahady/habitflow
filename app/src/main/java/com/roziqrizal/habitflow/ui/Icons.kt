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
    val Eye = feather("eye", "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z", "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z")
    val EyeOff = feather(
        "eye-off",
        "M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24",
        "M1 1l22 22",
    )
    val ArrowLeft = feather("arrow-left", "M19 12H5", "M12 19l-7-7 7-7")
    val ChevronRight = feather("chevron-right", "M9 18l6-6-6-6")
    val LogOut = feather("log-out", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5-5-5", "M21 12H9")
    val Settings = feather(
        "settings",
        "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z",
    )

    /** Huruf "G" Google dalam empat warna resmi (bukan ikon garis): pakai dengan `tint = Color.Unspecified`. */
    val Google: ImageVector = ImageVector.Builder(
        name = "google", defaultWidth = 18.dp, defaultHeight = 18.dp, viewportWidth = 18f, viewportHeight = 18f,
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M17.64,9.2c0,-0.637 -0.057,-1.251 -0.164,-1.84H9v3.481h4.844c-0.209,1.125 -0.843,2.078 -1.796,2.717v2.258h2.908c1.702,-1.567 2.684,-3.875 2.684,-6.615z",
            ),
            fill = SolidColor(Color(0xFF4285F4)),
        )
        addPath(
            pathData = addPathNodes(
                "M9,18c2.43,0 4.467,-0.806 5.956,-2.18l-2.908,-2.259c-0.806,0.54 -1.837,0.86 -3.048,0.86 -2.344,0 -4.328,-1.584 -5.036,-3.711H0.957v2.332C2.438,15.983 5.482,18 9,18z",
            ),
            fill = SolidColor(Color(0xFF34A853)),
        )
        addPath(
            pathData = addPathNodes(
                "M3.964,10.71c-0.18,-0.54 -0.282,-1.117 -0.282,-1.71s0.102,-1.17 0.282,-1.71V4.958H0.957C0.347,6.173 0,7.548 0,9s0.348,2.827 0.957,4.042l3.007,-2.332z",
            ),
            fill = SolidColor(Color(0xFFFBBC05)),
        )
        addPath(
            pathData = addPathNodes(
                "M9,3.58c1.321,0 2.508,0.454 3.44,1.345l2.582,-2.58C13.463,0.891 11.426,0 9,0 5.482,0 2.438,2.017 0.957,4.958L3.964,7.29C4.672,5.163 6.656,3.58 9,3.58z",
            ),
            fill = SolidColor(Color(0xFFEA4335)),
        )
    }.build()
}
