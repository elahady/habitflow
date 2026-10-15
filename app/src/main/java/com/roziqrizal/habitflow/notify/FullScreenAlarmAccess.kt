package com.roziqrizal.habitflow.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Izin layar alarm penuh di atas layar kunci (31L). `USE_FULL_SCREEN_INTENT` dibatasi Google untuk app alarm dan panggilan,
 * dan di Android 14 ke atas pengguna bisa mencabutnya per app. Tanpa izin ini alarm **tetap berbunyi penuh** (suara,
 * getar, dan layanan foreground tidak bergantung padanya); yang berubah hanya tampilannya: notifikasi heads-up dan layar
 * kunci dengan tombol Matikan dan Tunda, dan mengetuknya membuka layar alarm. Jadi penolakan Google tidak mematikan fitur.
 */
object FullScreenAlarmAccess {

    /** Benar kalau layar alarm penuh boleh ditampilkan di atas layar kunci. */
    fun isGranted(context: Context): Boolean = evaluate(
        sdk = Build.VERSION.SDK_INT,
        canUseOnApi34 = { context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() },
        permissionGranted = {
            ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FULL_SCREEN_INTENT) == PackageManager.PERMISSION_GRANTED
        },
    )

    /**
     * Layar sistem tempat pengguna memberikan izin, atau null kalau tidak ada yang bisa diberikan pengguna: Android 13 ke
     * bawah, atau izinnya tidak dideklarasikan di manifest (build uji dan rilis pertama tidak mendeklarasikannya, jadi
     * layar sistem itu tidak bisa memberikan apa-apa dan tawarannya tidak boleh muncul).
     */
    fun requestIntent(context: Context): Intent? =
        if (!canOfferSettings(Build.VERSION.SDK_INT, declaresPermission(context))) null
        else Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))

    @Suppress("DEPRECATION")
    private fun declaresPermission(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().contains(Manifest.permission.USE_FULL_SCREEN_INTENT)
    }.getOrDefault(false)

    internal fun canOfferSettings(sdk: Int, permissionDeclared: Boolean): Boolean =
        sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && permissionDeclared

    /**
     * Android 14 ke atas: `canUseFullScreenIntent()` yang menentukan (izin bisa dicabut pengguna atau ditolak Google).
     * Di bawahnya izin hanya berlaku kalau tercantum di manifest, jadi yang dicek keberadaan izinnya.
     */
    internal fun evaluate(sdk: Int, canUseOnApi34: () -> Boolean, permissionGranted: () -> Boolean): Boolean =
        if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) canUseOnApi34() else permissionGranted()
}
