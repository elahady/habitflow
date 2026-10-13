package com.roziqrizal.habitflow.data.account

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SignInResult {
    data class Success(val account: Account) : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val message: String) : SignInResult
    data object Offline : SignInResult

    /** Akun ini bukan pemilik data di HP ini: login ditahan sampai pengguna mengonfirmasi penghapusan (31H). */
    data object OwnerConflict : SignInResult
}

/**
 * Pintu tunggal ke API akun dan penyimpanan sesi (tahap 25, diperluas di 31F): daftar, verifikasi, masuk (email
 * dan Google), lupa dan reset kata sandi, keluar, keluar dari semua perangkat, ubah kata sandi, hapus akun, dan `me`.
 * Operasi berjalan satu per satu ([mutex]) supaya tombol dobel tidak saling menimpa.
 *
 * Semua hasil bertipe [AccountResult]. Sesi hanya disimpan setelah server menyatakan berhasil. Sesi yang berakhir
 * (401) ditangani [SessionGuard], bukan di sini.
 */
class AccountManager(
    private val settings: AccountSettings,
    private val googleSignIn: GoogleSignIn,
    private val reset: LocalDataReset,
    private val api: AccountApi = AccountClient(),
    /** Nama perangkat yang dikirim ke server dan tampil di daftar sesi. */
    private val deviceName: () -> String = { "android" },
) : AuthOperations, SessionEnding {
    private val mutex = Mutex()

    /**
     * Login yang sudah diterima server tapi ditahan karena akunnya bukan pemilik data di HP ini (31H). Tokennya hanya di memori:
     * kalau proses mati sebelum pengguna memutuskan, login hilang dan token itu kedaluwarsa sendiri di server.
     */
    private var held: Account? = null

    /** Boleh masuk kalau HP belum punya pemilik data, atau pemiliknya akun yang sama. */
    private fun admits(account: Account): Boolean = settings.ownerAccountId.let { it == null || it == account.id }

    /** Masuk dengan Google: minta ID token (tampil pemilih akun), lalu tukar ke token API. */
    suspend fun signIn(context: Context): SignInResult = when (val idToken = googleSignIn.requestIdToken(context)) {
        is GoogleIdTokenResult.Success -> signInWithGoogleToken(idToken.idToken)
        GoogleIdTokenResult.Cancelled -> SignInResult.Cancelled
        is GoogleIdTokenResult.Failed -> SignInResult.Failed(idToken.detail)
    }

    override suspend fun signInWithGoogleToken(idToken: String, privacyPolicyVersion: String?): SignInResult = mutex.withLock {
        when (val result = api.loginWithGoogle(idToken, deviceName(), privacyPolicyVersion)) {
            is AccountResult.Success -> if (!admits(result.value)) {
                held = result.value
                SignInResult.OwnerConflict
            } else {
                held = null
                settings.save(result.value)
                SignInResult.Success(result.value)
            }
            is AccountResult.Failed -> SignInResult.Failed(result.message)
            AccountResult.Unauthorized -> SignInResult.Failed("Google menolak token ini. Coba lagi.")
            AccountResult.Offline -> SignInResult.Offline
        }
    }

    /** Mendaftar. Berhasil berarti kode verifikasi dikirim ke email, belum ada sesi. */
    override suspend fun register(
        name: String,
        email: String,
        password: String,
        passwordConfirmation: String,
        privacyPolicyVersion: String,
    ): AccountResult<Unit> = mutex.withLock {
        api.register(name, email, password, passwordConfirmation, privacyPolicyVersion)
    }

    /** Memverifikasi kode email. Berhasil berarti langsung masuk. */
    override suspend fun verifyEmail(email: String, code: String): AccountResult<Account> = mutex.withLock {
        startSession(api.verifyEmail(email, code, deviceName()))
    }

    override suspend fun resendVerification(email: String): AccountResult<Unit> = mutex.withLock { api.resendVerification(email) }

    /** Masuk dengan email. Email belum terverifikasi menghasilkan [AccountResult.Failed] berkode [AccountErrors.EMAIL_NOT_VERIFIED]. */
    override suspend fun login(email: String, password: String): AccountResult<Account> = mutex.withLock {
        startSession(api.login(email, password, deviceName()))
    }

    override suspend fun forgotPassword(email: String): AccountResult<Unit> = mutex.withLock { api.forgotPassword(email) }

    /**
     * Menyetel kata sandi baru. Berhasil mencabut semua sesi akun di server, jadi sesi yang masih tercatat di HP ini
     * ditutup (sesi yang sudah berakhir dibiarkan supaya layar Masuk tetap tahu emailnya).
     */
    override suspend fun resetPassword(
        email: String,
        code: String,
        password: String,
        passwordConfirmation: String,
    ): AccountResult<Unit> = mutex.withLock {
        val result = api.resetPassword(email, code, password, passwordConfirmation)
        if (result is AccountResult.Success && signedInToken() != null) settings.clear()
        result
    }

    /**
     * Keluar: mencabut token di server lalu menutup sesi di HP. Sesi di HP **selalu** ditutup, juga saat server
     * tidak terjangkau; hasilnya [AccountResult.Offline] memberi tahu pemanggil bahwa token di server belum dicabut
     * (token itu tetap berlaku sampai dicabut dari perangkat lain). 401 dianggap berhasil: tokennya sudah mati.
     */
    suspend fun signOut(context: Context): AccountResult<Unit> {
        googleSignIn.clearState(context)
        return revokeAndClear()
    }

    override suspend fun revokeAndClear(): AccountResult<Unit> = mutex.withLock {
        val token = (settings.session.value as? SessionState.SignedIn)?.account?.token
        val result = if (token == null) AccountResult.Success(Unit) else api.logout(token)
        settings.clear()
        if (result is AccountResult.Unauthorized) AccountResult.Success(Unit) else result
    }

    /** Mencabut semua sesi akun, termasuk di HP ini. Hanya menutup sesi lokal kalau server benar-benar mencabut. */
    suspend fun signOutAllDevices(): AccountResult<Unit> = mutex.withLock {
        val token = signedInToken() ?: return@withLock AccountResult.Unauthorized
        val result = api.logoutAll(token)
        if (result is AccountResult.Success || result is AccountResult.Unauthorized) settings.clear()
        if (result is AccountResult.Unauthorized) AccountResult.Success(Unit) else result
    }

    /** Ubah kata sandi. [currentPassword] null untuk akun Google yang belum punya sandi. Sesi ini tetap hidup. */
    suspend fun changePassword(
        currentPassword: String?,
        password: String,
        passwordConfirmation: String,
    ): AccountResult<Unit> = mutex.withLock {
        val token = signedInToken() ?: return@withLock AccountResult.Unauthorized
        api.changePassword(token, currentPassword, password, passwordConfirmation)
    }

    /**
     * Menghapus akun dan semua datanya di server. Berhasil berarti sesi di HP ditutup. Data lokal dan
     * [AccountSettings.ownerAccountId] tidak disentuh di sini (penghapusan data di HP ada di alur Keluar, 31H).
     */
    override suspend fun deleteAccount(password: String?, googleIdToken: String?): AccountResult<Unit> = mutex.withLock {
        val token = signedInToken() ?: return@withLock AccountResult.Unauthorized
        val result = api.deleteAccount(token, password, googleIdToken)
        if (result is AccountResult.Success) settings.clear()
        result
    }

    override suspend fun me(): AccountResult<MeInfo> = mutex.withLock {
        val token = signedInToken() ?: return@withLock AccountResult.Unauthorized
        api.me(token)
    }

    private fun signedInToken(): String? = (settings.session.value as? SessionState.SignedIn)?.account?.token

    private fun startSession(result: AccountResult<Account>): AccountResult<Account> {
        if (result !is AccountResult.Success) return result
        if (!admits(result.value)) {
            held = result.value
            return AccountResult.Failed(409, AccountErrors.OWNER_MISMATCH, "HP ini masih menyimpan data akun lain.")
        }
        held = null
        settings.save(result.value)
        return result
    }

    /**
     * Pengguna setuju data akun lain di HP ini dihapus: menandai penghapusan beserta login yang ditahan, lalu memulai ulang
     * proses. Data baru terhapus di awal proses berikutnya, yang lalu memulihkan sesi akun baru ([LocalDataReset]).
     * Mengembalikan false kalau tidak ada login yang ditahan.
     */
    override suspend fun confirmReplaceLocalData(): Boolean = mutex.withLock {
        val account = held ?: return@withLock false
        held = null
        reset.scheduleWipe(account)
        reset.restartProcess()
        true
    }

    /** Pengguna membatalkan: login yang ditahan dicabut di server supaya tidak ada sesi yang menggantung. Data di HP tidak berubah. */
    override suspend fun cancelReplaceLocalData() {
        mutex.withLock {
            val account = held ?: return@withLock
            held = null
            api.logout(account.token)
        }
    }
}
