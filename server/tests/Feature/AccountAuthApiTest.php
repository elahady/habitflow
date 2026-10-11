<?php

namespace Tests\Feature;

use App\Mail\CodeMail;
use App\Models\EmailCode;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Mail\MailManager;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

/** Akun email dan kata sandi di API (tahap 31B): daftar, verifikasi kode, masuk, reset, sesi, penautan Google. */
class AccountAuthApiTest extends TestCase
{
    use RefreshDatabase;

    private const PASSWORD = 'kopi-susu-gula-aren';

    protected function setUp(): void
    {
        parent::setUp();
        Mail::fake();
        config(['services.google.android_client_id' => 'client-uji']);
    }

    private function register(array $overrides = [])
    {
        return $this->postJson('/api/v1/auth/register', $overrides + [
            'name' => 'Budi',
            'email' => 'budi@example.com',
            'password' => self::PASSWORD,
            'password_confirmation' => self::PASSWORD,
            'privacy_policy_version' => config('habitflow.privacy_policy_version'),
        ]);
    }

    /** Ambil kode asli dari email yang dikirim (kode asli hanya ada di email, tidak di database). */
    private function lastCode(string $purpose = EmailCode::PURPOSE_VERIFY): string
    {
        $sent = Mail::queued(CodeMail::class, fn (CodeMail $m) => $m->purpose === $purpose);

        return $sent->last()->code;
    }

    private function verifiedUser(array $attrs = []): User
    {
        $user = new User(['email' => 'budi@example.com', 'name' => 'Budi', 'password' => self::PASSWORD] + $attrs);
        $user->forceFill(['email_verified_at' => now()])->save();

        return $user;
    }

    private function tokenFor(User $user, string $name = 'hp'): array
    {
        [$model, $plain] = UserToken::issue($user, $name);

        return [$model, ['Authorization' => 'Bearer '.$plain]];
    }

    private function loginCall(string $password = self::PASSWORD, string $email = 'budi@example.com')
    {
        return $this->postJson('/api/v1/auth/login', ['email' => $email, 'password' => $password, 'device_name' => 'hp']);
    }

    // --- Daftar dan verifikasi ---

    public function test_daftar_membuat_akun_belum_terverifikasi_dan_mengirim_kode(): void
    {
        $this->register()->assertCreated()->assertJson(['status' => 'verification_required', 'email' => 'budi@example.com']);

        $user = User::where('email', 'budi@example.com')->first();
        $this->assertNull($user->email_verified_at);
        $this->assertNotNull($user->privacy_consented_at);
        $this->assertSame('1', $user->privacy_policy_version);
        Mail::assertQueued(CodeMail::class, 1);
        $this->assertMatchesRegularExpression('/^\d{6}$/', $this->lastCode());
    }

    public function test_daftar_tidak_memberi_token_sebelum_verifikasi(): void
    {
        $this->register()->assertCreated()->assertJsonMissingPath('token');
        $this->assertSame(0, UserToken::count());
    }

    public function test_daftar_menolak_email_yang_sudah_terverifikasi(): void
    {
        $this->verifiedUser();

        $this->register()->assertStatus(422)->assertJson(['code' => 'email_taken']);
        Mail::assertNothingQueued();
    }

    public function test_daftar_email_belum_terverifikasi_mengirim_ulang_dan_pendaftar_terbaru_menang(): void
    {
        $this->register(['name' => 'Penyerang', 'password' => 'sandi-penyerang-1', 'password_confirmation' => 'sandi-penyerang-1'])->assertCreated();
        $this->travel(2)->minutes();
        $this->register(['name' => 'Budi Asli'])->assertCreated();

        $this->assertSame(1, User::count());
        $user = User::first();
        $this->assertSame('Budi Asli', $user->name);
        $this->assertTrue(\Hash::check(self::PASSWORD, $user->password));
        $this->assertFalse(\Hash::check('sandi-penyerang-1', $user->password));
    }

    public function test_daftar_mencatat_versi_persetujuan_dan_menolak_versi_salah(): void
    {
        $this->register(['privacy_policy_version' => '999'])->assertStatus(422)->assertJsonValidationErrors('privacy_policy_version');
        $this->register(['privacy_policy_version' => null])->assertStatus(422);
        $this->assertSame(0, User::count());
    }

    public function test_aturan_kata_sandi(): void
    {
        foreach (['pendek1', 'password', '12345678', 'aaaaaaaa', 'Password123'] as $buruk) {
            $this->register(['password' => $buruk, 'password_confirmation' => $buruk])
                ->assertStatus(422)->assertJsonValidationErrors('password');
        }
        $this->register(['password' => self::PASSWORD, 'password_confirmation' => 'beda'])->assertStatus(422);
        $this->assertSame(0, User::count());
    }

    public function test_email_dinormalkan_huruf_kecil(): void
    {
        $this->register(['email' => '  Budi@Example.COM '])->assertCreated()->assertJson(['email' => 'budi@example.com']);
        $this->assertSame('budi@example.com', User::first()->email);
    }

    public function test_verifikasi_dengan_kode_benar_memberi_token_dan_menandai_terverifikasi(): void
    {
        $this->register();
        $code = $this->lastCode();

        $response = $this->postJson('/api/v1/auth/verify-email', ['email' => 'budi@example.com', 'code' => $code, 'device_name' => 'hp-budi'])
            ->assertOk()
            ->assertJsonStructure(['token', 'account' => ['id', 'name', 'email', 'avatar']]);

        $this->assertNotNull(User::first()->email_verified_at);
        $this->assertSame('hp-budi', UserToken::first()->name);

        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer '.$response['token']])->assertOk();
    }

    public function test_kode_disimpan_sebagai_hash_bukan_aslinya(): void
    {
        $this->register();
        $code = $this->lastCode();

        $row = EmailCode::first();
        $this->assertNotSame($code, $row->code_hash);
        $this->assertSame(64, strlen($row->code_hash));
        $this->assertStringNotContainsString($code, json_encode($row->toArray()));
    }

    public function test_kode_sekali_pakai(): void
    {
        $this->register();
        $code = $this->lastCode();
        $body = ['email' => 'budi@example.com', 'code' => $code, 'device_name' => 'hp'];

        $this->postJson('/api/v1/auth/verify-email', $body)->assertOk();
        $this->postJson('/api/v1/auth/verify-email', $body)->assertStatus(422)->assertJson(['code' => 'code_invalid']);
        $this->assertSame(1, UserToken::count());
    }

    public function test_kode_kedaluwarsa_setelah_10_menit(): void
    {
        $this->register();
        $code = $this->lastCode();

        $this->travel(11)->minutes();

        $this->postJson('/api/v1/auth/verify-email', ['email' => 'budi@example.com', 'code' => $code, 'device_name' => 'hp'])
            ->assertStatus(422)->assertJson(['code' => 'code_expired']);
        $this->assertNull(User::first()->email_verified_at);
    }

    public function test_kode_masih_berlaku_di_menit_ke_9(): void
    {
        $this->register();
        $code = $this->lastCode();

        $this->travel(9)->minutes();

        $this->postJson('/api/v1/auth/verify-email', ['email' => 'budi@example.com', 'code' => $code, 'device_name' => 'hp'])->assertOk();
    }

    public function test_lima_kali_salah_menghanguskan_kode_walau_lalu_dicoba_yang_benar(): void
    {
        $this->register();
        $code = $this->lastCode();
        $salah = $code === '000000' ? '111111' : '000000';

        for ($i = 0; $i < 5; $i++) {
            $this->postJson('/api/v1/auth/verify-email', ['email' => 'budi@example.com', 'code' => $salah, 'device_name' => 'hp'])
                ->assertStatus(422)->assertJson(['code' => 'code_invalid']);
        }

        $this->postJson('/api/v1/auth/verify-email', ['email' => 'budi@example.com', 'code' => $code, 'device_name' => 'hp'])
            ->assertStatus(422)->assertJson(['code' => 'code_invalid']);
        $this->assertNull(User::first()->email_verified_at);
    }

    public function test_kode_baru_menghanguskan_kode_lama(): void
    {
        $this->register();
        $lama = $this->lastCode();
        $this->travel(61)->seconds();
        $this->postJson('/api/v1/auth/resend-verification', ['email' => 'budi@example.com'])->assertStatus(202);
        $baru = $this->lastCode();

        $this->assertNotSame($lama, $baru);
        if ($lama !== $baru) {
            $this->postJson('/api/v1/auth/verify-email', ['email' => 'budi@example.com', 'code' => $lama, 'device_name' => 'hp'])
                ->assertStatus(422);
            $this->postJson('/api/v1/auth/verify-email', ['email' => 'budi@example.com', 'code' => $baru, 'device_name' => 'hp'])
                ->assertOk();
        }
    }

    public function test_kirim_ulang_ditahan_60_detik_dan_5_per_jam_tetap_202(): void
    {
        $this->register();
        Mail::assertQueued(CodeMail::class, 1);

        $this->postJson('/api/v1/auth/resend-verification', ['email' => 'budi@example.com'])->assertStatus(202);
        Mail::assertQueued(CodeMail::class, 1); // masih dalam jeda 60 detik sejak daftar

        foreach (range(1, 6) as $i) {
            $this->travel(61)->seconds();
            $this->postJson('/api/v1/auth/resend-verification', ['email' => 'budi@example.com'])->assertStatus(202);
        }
        Mail::assertQueued(CodeMail::class, 5); // batas 5 per jam, daftar menghitung satu
    }

    public function test_kirim_ulang_untuk_email_tak_dikenal_atau_sudah_terverifikasi_sama_202_tanpa_email(): void
    {
        $this->verifiedUser();

        $this->postJson('/api/v1/auth/resend-verification', ['email' => 'budi@example.com'])->assertStatus(202)->assertExactJson(['status' => 'accepted']);
        $this->postJson('/api/v1/auth/resend-verification', ['email' => 'tak-ada@example.com'])->assertStatus(202)->assertExactJson(['status' => 'accepted']);
        Mail::assertNothingQueued();
    }

    public function test_verifikasi_dibatasi_lajunya(): void
    {
        $this->register();

        for ($i = 0; $i < 10; $i++) {
            $this->postJson('/api/v1/auth/verify-email', ['email' => 'budi@example.com', 'code' => '000000', 'device_name' => 'hp']);
        }

        $this->postJson('/api/v1/auth/verify-email', ['email' => 'budi@example.com', 'code' => '000000', 'device_name' => 'hp'])
            ->assertStatus(429)->assertJson(['code' => 'too_many_attempts'])->assertHeader('Retry-After');
    }

    // --- Masuk ---

    public function test_login_berhasil_memberi_token_dan_account(): void
    {
        $this->verifiedUser();

        $this->loginCall()->assertOk()->assertJsonStructure(['token', 'account' => ['id', 'name', 'email', 'avatar']]);
        $this->assertSame(1, UserToken::count());
    }

    public function test_login_belum_terverifikasi_403_hanya_kalau_kata_sandi_benar(): void
    {
        $this->register();

        $this->loginCall()->assertStatus(403)->assertJson(['code' => 'email_not_verified']);
        $this->loginCall('sandi-lain-lagi')->assertStatus(422)->assertJson(['code' => 'invalid_credentials']);
        $this->assertSame(0, UserToken::count());
    }

    public function test_login_salah_jawabannya_identik_untuk_email_terdaftar_dan_tidak(): void
    {
        $this->verifiedUser();

        $sandiSalah = $this->loginCall('sandi-salah-banget');
        $emailAsing = $this->loginCall(self::PASSWORD, 'orang-asing@example.com');

        $sandiSalah->assertStatus(422);
        $emailAsing->assertStatus(422);
        $this->assertSame($sandiSalah->json(), $emailAsing->json());
    }

    public function test_akun_hanya_google_tidak_bisa_masuk_dengan_kata_sandi(): void
    {
        User::forceCreate(['google_sub' => 'g-1', 'email' => 'budi@example.com', 'name' => 'Budi', 'email_verified_at' => now()]);

        $this->loginCall('apa-saja-1234')->assertStatus(422)->assertJson(['code' => 'invalid_credentials']);
    }

    public function test_login_gagal_dibatasi_5_per_menit_per_ip(): void
    {
        $this->verifiedUser();

        foreach (range(1, 5) as $i) {
            $this->loginCall('salah-salah-salah')->assertStatus(422);
        }

        $this->loginCall('salah-salah-salah')->assertStatus(429)->assertJson(['code' => 'too_many_attempts']);
        $this->loginCall()->assertStatus(429); // yang benar pun ditahan selama masih terkunci
    }

    public function test_login_gagal_dibatasi_10_per_15_menit_per_email_dari_ip_berbeda(): void
    {
        $this->verifiedUser();

        foreach (range(1, 10) as $i) {
            $this->withServerVariables(['REMOTE_ADDR' => "10.0.0.$i"])->loginCall('salah-salah-salah')->assertStatus(422);
        }

        $this->withServerVariables(['REMOTE_ADDR' => '10.0.1.1'])->loginCall()->assertStatus(429);
    }

    // --- Lupa dan atur ulang kata sandi ---

    public function test_lupa_kata_sandi_jawabannya_identik_dan_email_hanya_untuk_akun_terdaftar(): void
    {
        $this->verifiedUser();

        $ada = $this->postJson('/api/v1/auth/forgot-password', ['email' => 'budi@example.com']);
        $tidakAda = $this->postJson('/api/v1/auth/forgot-password', ['email' => 'tak-ada@example.com']);

        $ada->assertStatus(202);
        $tidakAda->assertStatus(202);
        $this->assertSame($ada->getContent(), $tidakAda->getContent());
        Mail::assertQueued(CodeMail::class, 1);
    }

    public function test_lupa_kata_sandi_maksimal_3_per_jam_per_email(): void
    {
        $this->verifiedUser();

        foreach (range(1, 5) as $i) {
            $this->postJson('/api/v1/auth/forgot-password', ['email' => 'budi@example.com'])->assertStatus(202);
        }

        Mail::assertQueued(CodeMail::class, 3);
    }

    public function test_lupa_kata_sandi_akun_belum_terverifikasi_tidak_dikirimi_kode(): void
    {
        $this->register();
        Mail::assertQueued(CodeMail::class, 1); // hanya kode verifikasi dari daftar

        $this->postJson('/api/v1/auth/forgot-password', ['email' => 'budi@example.com'])->assertStatus(202);

        Mail::assertQueued(CodeMail::class, 1);
    }

    public function test_atur_ulang_kata_sandi_mengganti_sandi_dan_mencabut_semua_token(): void
    {
        $user = $this->verifiedUser();
        [, $h1] = $this->tokenFor($user, 'hp-1');
        [, $h2] = $this->tokenFor($user, 'hp-2');
        $this->postJson('/api/v1/auth/forgot-password', ['email' => 'budi@example.com']);
        $code = $this->lastCode(EmailCode::PURPOSE_RESET);

        $this->postJson('/api/v1/auth/reset-password', [
            'email' => 'budi@example.com', 'code' => $code,
            'password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat',
        ])->assertNoContent();

        $this->getJson('/api/v1/me', $h1)->assertStatus(401);
        $this->getJson('/api/v1/me', $h2)->assertStatus(401);
        $this->loginCall(self::PASSWORD)->assertStatus(422);
        $this->loginCall('sandi-baru-yang-kuat')->assertOk();
    }

    public function test_atur_ulang_kode_sekali_pakai_dan_kode_verifikasi_tidak_berlaku_di_reset(): void
    {
        $this->register();
        $kodeVerifikasi = $this->lastCode();
        $user = User::first();
        $user->forceFill(['email_verified_at' => now()])->save();
        $this->travel(61)->seconds();
        $this->postJson('/api/v1/auth/forgot-password', ['email' => 'budi@example.com']);
        $kodeReset = $this->lastCode(EmailCode::PURPOSE_RESET);
        $body = fn (string $code) => [
            'email' => 'budi@example.com', 'code' => $code,
            'password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat',
        ];

        // Kode untuk tujuan lain tidak bisa dipakai di sini (kecuali kebetulan sama, tidak mungkin dihitung).
        if ($kodeVerifikasi !== $kodeReset) {
            $this->postJson('/api/v1/auth/reset-password', $body($kodeVerifikasi))->assertStatus(422)->assertJson(['code' => 'code_invalid']);
        }
        $this->postJson('/api/v1/auth/reset-password', $body($kodeReset))->assertNoContent();
        $this->postJson('/api/v1/auth/reset-password', $body($kodeReset))->assertStatus(422)->assertJson(['code' => 'code_invalid']);
    }

    public function test_atur_ulang_kode_kedaluwarsa_dan_sandi_lemah_tidak_menghanguskan_kode(): void
    {
        $this->verifiedUser();
        $this->postJson('/api/v1/auth/forgot-password', ['email' => 'budi@example.com']);
        $code = $this->lastCode(EmailCode::PURPOSE_RESET);

        // Sandi lemah ditolak sebelum kode diperiksa, jadi kode yang benar tetap utuh.
        $this->postJson('/api/v1/auth/reset-password', [
            'email' => 'budi@example.com', 'code' => $code, 'password' => '12345678', 'password_confirmation' => '12345678',
        ])->assertStatus(422)->assertJsonValidationErrors('password');
        $this->assertNull(EmailCode::first()->consumed_at);

        $this->travel(11)->minutes();
        $this->postJson('/api/v1/auth/reset-password', [
            'email' => 'budi@example.com', 'code' => $code,
            'password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat',
        ])->assertStatus(422)->assertJson(['code' => 'code_expired']);
    }

    public function test_atur_ulang_email_tak_dikenal_dijawab_sama_dengan_kode_salah(): void
    {
        $this->verifiedUser();
        $body = ['code' => '123456', 'password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat'];

        $asing = $this->postJson('/api/v1/auth/reset-password', ['email' => 'asing@example.com'] + $body);
        $terdaftar = $this->postJson('/api/v1/auth/reset-password', ['email' => 'budi@example.com'] + $body);

        $this->assertSame($asing->getContent(), $terdaftar->getContent());
    }

    // --- Pencabutan sesi ---

    public function test_logout_hanya_mencabut_token_ini(): void
    {
        $user = $this->verifiedUser();
        [, $ini] = $this->tokenFor($user, 'ini');
        [, $lain] = $this->tokenFor($user, 'lain');

        $this->postJson('/api/v1/auth/logout', [], $ini)->assertNoContent();

        $this->getJson('/api/v1/me', $ini)->assertStatus(401);
        $this->getJson('/api/v1/me', $lain)->assertOk();
    }

    public function test_logout_all_mencabut_semua_token_termasuk_ini_dan_memutus_sesi_web(): void
    {
        $user = $this->verifiedUser();
        $user->forceFill(['remember_token' => 'cookie-lama'])->save();
        [, $ini] = $this->tokenFor($user, 'ini');
        [, $lain] = $this->tokenFor($user, 'lain');

        $this->postJson('/api/v1/auth/logout-all', [], $ini)->assertNoContent();

        $this->getJson('/api/v1/me', $ini)->assertStatus(401);
        $this->getJson('/api/v1/me', $lain)->assertStatus(401);
        $this->assertNotSame('cookie-lama', $user->fresh()->remember_token);
    }

    public function test_ganti_kata_sandi_mencabut_token_lain_tapi_mempertahankan_yang_dipakai(): void
    {
        $user = $this->verifiedUser();
        [, $ini] = $this->tokenFor($user, 'ini');
        [, $lain] = $this->tokenFor($user, 'lain');

        $this->putJson('/api/v1/account/password', [
            'current_password' => self::PASSWORD,
            'password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat',
        ], $ini)->assertNoContent();

        $this->getJson('/api/v1/me', $ini)->assertOk();
        $this->getJson('/api/v1/me', $lain)->assertStatus(401);
        $this->loginCall('sandi-baru-yang-kuat')->assertOk();
    }

    public function test_ganti_kata_sandi_butuh_kata_sandi_lama_yang_benar(): void
    {
        $user = $this->verifiedUser();
        [, $h] = $this->tokenFor($user);
        $baru = ['password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat'];

        $this->putJson('/api/v1/account/password', $baru, $h)->assertStatus(422)->assertJsonValidationErrors('current_password');
        $this->putJson('/api/v1/account/password', ['current_password' => 'bukan-sandi-ini'] + $baru, $h)
            ->assertStatus(422)->assertJson(['code' => 'invalid_credentials']);
        $this->assertTrue(\Hash::check(self::PASSWORD, $user->fresh()->password));
    }

    public function test_ganti_kata_sandi_salah_berulang_dibatasi(): void
    {
        $user = $this->verifiedUser();
        [, $h] = $this->tokenFor($user);
        $baru = ['password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat'];

        foreach (range(1, 5) as $i) {
            $this->putJson('/api/v1/account/password', ['current_password' => 'salah-salah-salah'] + $baru, $h)->assertStatus(422);
        }

        $this->putJson('/api/v1/account/password', ['current_password' => self::PASSWORD] + $baru, $h)->assertStatus(429);
    }

    public function test_akun_google_tanpa_sandi_boleh_membuat_kata_sandi_tanpa_yang_lama(): void
    {
        $user = User::forceCreate(['google_sub' => 'g-1', 'email' => 'budi@example.com', 'name' => 'Budi', 'email_verified_at' => now()]);
        [, $h] = $this->tokenFor($user);

        $this->getJson('/api/v1/me', $h)->assertJson(['has_password' => false, 'google_linked' => true, 'email_verified' => true]);

        $this->putJson('/api/v1/account/password', [
            'password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat',
        ], $h)->assertNoContent();

        $this->getJson('/api/v1/me', $h)->assertJson(['has_password' => true]);
        $this->loginCall('sandi-baru-yang-kuat')->assertOk();
    }

    public function test_endpoint_sesi_menolak_tanpa_token(): void
    {
        $this->postJson('/api/v1/auth/logout')->assertStatus(401);
        $this->postJson('/api/v1/auth/logout-all')->assertStatus(401);
        $this->getJson('/api/v1/me')->assertStatus(401);
        $this->putJson('/api/v1/account/password', [])->assertStatus(401);
    }

    // --- Penautan Google ---

    private function googleClaims(array $overrides = []): array
    {
        return $overrides + [
            'aud' => 'client-uji', 'sub' => 'google-sub-1', 'email' => 'budi@example.com',
            'email_verified' => 'true', 'name' => 'Budi Google', 'picture' => 'https://example.com/a.png',
        ];
    }

    private function googleLogin(array $claims = [])
    {
        Http::fake(['oauth2.googleapis.com/*' => Http::response($this->googleClaims($claims))]);

        return $this->postJson('/api/v1/auth/google', ['id_token' => 'x', 'device_name' => 'hp-google']);
    }

    public function test_google_membuat_akun_baru_terverifikasi(): void
    {
        $this->googleLogin()->assertOk()
            ->assertJsonStructure(['token', 'account' => ['id', 'email'], 'user' => ['id', 'email']]);

        $user = User::first();
        $this->assertNotNull($user->email_verified_at);
        $this->assertSame('google-sub-1', $user->google_sub);
        $this->assertNull($user->password);
    }

    public function test_google_ditautkan_ke_akun_email_yang_sudah_terverifikasi_dan_sandi_tetap(): void
    {
        $user = $this->verifiedUser();

        $this->googleLogin()->assertOk();

        $this->assertSame(1, User::count());
        $user->refresh();
        $this->assertSame('google-sub-1', $user->google_sub);
        $this->assertTrue(\Hash::check(self::PASSWORD, $user->password));
    }

    public function test_pengambilalihan_akun_google_mengambil_alih_email_yang_belum_terverifikasi(): void
    {
        // Penyerang mendaftar duluan dengan email korban dan memegang kata sandinya.
        $this->register(['password' => 'sandi-penyerang-1', 'password_confirmation' => 'sandi-penyerang-1'])->assertCreated();
        $penyerang = User::first();
        $penyerang->forceFill(['remember_token' => 'cookie-penyerang'])->save();
        $penyerangToken = UserToken::issue($penyerang, 'hp-penyerang')[1];

        // Korban masuk dengan Google yang membuktikan dia pemilik email itu.
        $this->googleLogin()->assertOk();

        $this->assertSame(1, User::count());
        $user = User::first();
        $this->assertNull($user->password);
        $this->assertNotNull($user->email_verified_at);
        $this->assertSame('google-sub-1', $user->google_sub);
        $this->assertNotSame('cookie-penyerang', $user->remember_token);
        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer '.$penyerangToken])->assertStatus(401);
        $this->loginCall('sandi-penyerang-1')->assertStatus(422);
    }

    public function test_google_dengan_email_belum_diverifikasi_oleh_google_ditolak_dan_tidak_menyentuh_akun(): void
    {
        $this->register();

        $this->googleLogin(['email_verified' => 'false'])->assertStatus(422);

        $user = User::first();
        $this->assertNull($user->google_sub);
        $this->assertNotNull($user->password);
    }

    public function test_google_untuk_aplikasi_lain_ditolak(): void
    {
        $this->googleLogin(['aud' => 'client-lain'])->assertStatus(422);
        $this->assertSame(0, User::count());
    }

    public function test_google_kedua_kali_memakai_akun_yang_sama(): void
    {
        $this->googleLogin()->assertOk();
        $this->googleLogin(['email' => 'budi-ganti@example.com'])->assertOk();

        $this->assertSame(1, User::count());
        $this->assertSame(2, UserToken::count());
    }

    // --- Akun lama dan antrean ---

    public function test_migrasi_menandai_akun_yang_sudah_ada_sebagai_terverifikasi(): void
    {
        $this->artisan('migrate:rollback', ['--step' => 2]);
        \DB::table('users')->insert(['google_sub' => 'lama', 'email' => 'lama@example.com', 'name' => 'Lama', 'created_at' => now(), 'updated_at' => now()]);
        $this->artisan('migrate');

        $this->assertNotNull(User::where('email', 'lama@example.com')->first()->email_verified_at);
    }

    public function test_kode_dikirim_lewat_antrean(): void
    {
        $this->register();

        Mail::assertQueued(CodeMail::class, fn (CodeMail $m) => $m->hasTo('budi@example.com') && $m->minutes === 10);
        Mail::assertNotSent(CodeMail::class);
    }

    public function test_isi_email_menyebut_kode_masa_berlaku_dan_abaikan(): void
    {
        $mail = new CodeMail(EmailCode::PURPOSE_VERIFY, '123456', 'Budi', 10);

        $mail->assertSeeInText('123456');
        $mail->assertSeeInText('10 menit');
        $mail->assertSeeInText('abaikan');
    }

    public function test_pemberitahuan_pemilik_saat_email_mencapai_80_persen_batas_sekali_sehari(): void
    {
        Mail::swap(new MailManager($this->app));
        config(['habitflow.mail_daily_limit' => 10, 'habitflow.owner_email' => 'pemilik@example.com', 'mail.default' => 'array']);
        Cache::flush();

        $kirim = fn () => Mail::raw('x', fn ($m) => $m->to('a@example.com')->subject('s'));
        foreach (range(1, 7) as $i) {
            $kirim();
        }
        $diPemilik = fn () => collect(Mail::mailer('array')->getSymfonyTransport()->messages())
            ->filter(fn ($m) => str_contains($m->getOriginalMessage()->getSubject(), 'batas email harian'))->count();
        $this->assertSame(0, $diPemilik());

        $kirim(); // ke-8 = 80%
        $kirim();
        $kirim();

        $this->assertSame(1, $diPemilik());
    }
}
