<?php

namespace Tests\Feature;

use App\Mail\CodeMail;
use App\Models\EmailCode;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Mail;
use Laravel\Socialite\Facades\Socialite;
use Laravel\Socialite\Two\User as SocialiteUser;
use Mockery;
use Tests\TestCase;

/** Halaman web akun (tahap 31C): daftar, verifikasi, masuk, lupa dan reset kata sandi, halaman Akun, halaman publik. */
class AccountWebTest extends TestCase
{
    use RefreshDatabase;

    private const PASSWORD = 'kopi-susu-gula-aren';

    protected function setUp(): void
    {
        parent::setUp();
        Mail::fake();
    }

    private function registerData(array $overrides = []): array
    {
        return $overrides + [
            'name' => 'Budi',
            'email' => 'budi@example.com',
            'password' => self::PASSWORD,
            'password_confirmation' => self::PASSWORD,
            'privacy' => '1',
        ];
    }

    private function lastCode(string $purpose = EmailCode::PURPOSE_VERIFY): string
    {
        return Mail::queued(CodeMail::class, fn (CodeMail $m) => $m->purpose === $purpose)->last()->code;
    }

    private function verifiedUser(array $attrs = []): User
    {
        $user = new User(['email' => 'budi@example.com', 'name' => 'Budi', 'password' => self::PASSWORD] + $attrs);
        $user->forceFill(['email_verified_at' => now()])->save();

        return $user;
    }

    // --- Daftar dan verifikasi ---

    public function test_halaman_daftar_memuat_persetujuan_privasi_dengan_tautan(): void
    {
        $this->get('/register')->assertOk()
            ->assertSee('name="privacy"', false)
            ->assertSee('data kesehatan')
            ->assertSee(route('privacy'), false);
    }

    public function test_daftar_tanpa_persetujuan_ditolak(): void
    {
        $this->post('/register', $this->registerData(['privacy' => null]))
            ->assertSessionHasErrors('privacy');

        $this->assertSame(0, User::count());
        Mail::assertNothingQueued();
    }

    public function test_daftar_membuat_akun_belum_terverifikasi_tidak_masuk_dan_mengirim_kode(): void
    {
        $this->post('/register', $this->registerData())
            ->assertRedirect(route('verification.show'))
            ->assertSessionHas('verification_email', 'budi@example.com');

        $this->assertGuest();
        $user = User::first();
        $this->assertNull($user->email_verified_at);
        $this->assertNotNull($user->privacy_consented_at);
        $this->assertSame(config('habitflow.privacy_policy_version'), $user->privacy_policy_version);
        Mail::assertQueued(CodeMail::class, 1);
    }

    public function test_daftar_email_sudah_terverifikasi_ditolak(): void
    {
        $this->verifiedUser();

        $this->post('/register', $this->registerData())->assertSessionHasErrors('email');
        Mail::assertNothingQueued();
    }

    public function test_daftar_menolak_kata_sandi_umum(): void
    {
        $this->post('/register', $this->registerData(['password' => 'password123', 'password_confirmation' => 'password123']))
            ->assertSessionHasErrors('password');
        $this->assertSame(0, User::count());
    }

    public function test_halaman_verifikasi_menampilkan_email_dan_kolom_kode(): void
    {
        $this->withSession(['verification_email' => 'budi@example.com'])
            ->get('/verifikasi-email')->assertOk()
            ->assertSee('value="budi@example.com"', false)
            ->assertSee('name="code"', false);
    }

    public function test_kode_benar_memverifikasi_dan_langsung_masuk(): void
    {
        $this->post('/register', $this->registerData());
        $code = $this->lastCode();

        $this->post('/verifikasi-email', ['email' => 'budi@example.com', 'code' => $code])
            ->assertRedirect(route('dashboard'));

        $this->assertAuthenticatedAs(User::first());
        $this->assertNotNull(User::first()->email_verified_at);
    }

    public function test_kode_salah_tidak_masuk_dan_menampilkan_galat(): void
    {
        $this->post('/register', $this->registerData());
        $salah = $this->lastCode() === '000000' ? '111111' : '000000';

        $this->post('/verifikasi-email', ['email' => 'budi@example.com', 'code' => $salah])
            ->assertSessionHasErrors(['code' => 'Kode salah.']);

        $this->assertGuest();
        $this->assertNull(User::first()->email_verified_at);
    }

    public function test_kode_kedaluwarsa_dan_sekali_pakai(): void
    {
        $this->post('/register', $this->registerData());
        $code = $this->lastCode();

        $this->travel(11)->minutes();
        $this->post('/verifikasi-email', ['email' => 'budi@example.com', 'code' => $code])
            ->assertSessionHasErrors(['code' => 'Kode sudah kedaluwarsa. Minta kode baru.']);
        $this->assertGuest();
    }

    public function test_kode_tidak_bisa_dipakai_dua_kali(): void
    {
        $this->post('/register', $this->registerData());
        $code = $this->lastCode();

        $this->post('/verifikasi-email', ['email' => 'budi@example.com', 'code' => $code])->assertRedirect(route('dashboard'));
        auth()->logout();
        $this->post('/verifikasi-email', ['email' => 'budi@example.com', 'code' => $code])->assertSessionHasErrors('code');
        $this->assertGuest();
    }

    public function test_verifikasi_web_dibatasi_lajunya(): void
    {
        $this->post('/register', $this->registerData());

        foreach (range(1, 10) as $i) {
            $this->post('/verifikasi-email', ['email' => 'budi@example.com', 'code' => '000000']);
        }

        $this->post('/verifikasi-email', ['email' => 'budi@example.com', 'code' => '000000'])
            ->assertSessionHasErrors('code');
        $this->assertStringContainsString('Terlalu banyak', session('errors')->first('code'));
    }

    public function test_kirim_ulang_jawabannya_sama_untuk_email_terdaftar_dan_tidak(): void
    {
        $this->post('/register', $this->registerData());
        $this->travel(61)->seconds();

        $ada = $this->post('/verifikasi-email/kirim-ulang', ['email' => 'budi@example.com']);
        $tidakAda = $this->post('/verifikasi-email/kirim-ulang', ['email' => 'asing@example.com']);

        $this->assertSame(session('status'), 'Kalau email itu menunggu verifikasi, kode baru sudah dikirim. Tunggu semenit sebelum meminta lagi.');
        $ada->assertRedirect();
        $tidakAda->assertRedirect();
        $this->assertSame($ada->headers->get('Location'), $tidakAda->headers->get('Location'));
        Mail::assertQueued(CodeMail::class, 2); // daftar + kirim ulang untuk akun yang ada
    }

    // --- Masuk ---

    public function test_masuk_dengan_email_terverifikasi_berhasil(): void
    {
        $user = $this->verifiedUser();

        $this->post('/login', ['email' => 'budi@example.com', 'password' => self::PASSWORD])
            ->assertRedirect(route('dashboard'));

        $this->assertAuthenticatedAs($user);
    }

    public function test_masuk_email_belum_terverifikasi_tidak_masuk_dan_diarahkan_ke_verifikasi(): void
    {
        $this->post('/register', $this->registerData());
        $this->travel(61)->seconds();

        $this->post('/login', ['email' => 'budi@example.com', 'password' => self::PASSWORD])
            ->assertRedirect(route('verification.show'))
            ->assertSessionHas('verification_email', 'budi@example.com');

        $this->assertGuest();
        Mail::assertQueued(CodeMail::class, 2);
    }

    public function test_masuk_salah_pesannya_sama_untuk_email_terdaftar_dan_tidak(): void
    {
        $this->verifiedUser();

        $this->post('/login', ['email' => 'budi@example.com', 'password' => 'salah-banget-sandinya'])
            ->assertSessionHasErrors(['email' => 'Email atau kata sandi salah.']);
        $this->post('/login', ['email' => 'asing@example.com', 'password' => 'salah-banget-sandinya'])
            ->assertSessionHasErrors(['email' => 'Email atau kata sandi salah.']);
        $this->assertGuest();
    }

    public function test_masuk_gagal_beruntun_dikunci(): void
    {
        $this->verifiedUser();

        foreach (range(1, 5) as $i) {
            $this->post('/login', ['email' => 'budi@example.com', 'password' => 'salah-banget-sandinya']);
        }

        $this->post('/login', ['email' => 'budi@example.com', 'password' => self::PASSWORD])
            ->assertSessionHasErrors('email');
        $this->assertStringContainsString('Terlalu banyak', session('errors')->first('email'));
        $this->assertGuest();
    }

    public function test_halaman_masuk_punya_tautan_lupa_kata_sandi(): void
    {
        $this->get('/login')->assertOk()->assertSee(route('password.forgot'), false);
    }

    // --- Lupa dan atur ulang kata sandi ---

    public function test_lupa_kata_sandi_jawabannya_sama_dan_email_hanya_untuk_akun_terdaftar(): void
    {
        $this->verifiedUser();

        $ada = $this->post('/lupa-kata-sandi', ['email' => 'budi@example.com']);
        $status = session('status');
        $tidakAda = $this->post('/lupa-kata-sandi', ['email' => 'asing@example.com']);

        $ada->assertRedirect(route('password.reset'));
        $tidakAda->assertRedirect(route('password.reset'));
        $this->assertSame($status, session('status'));
        Mail::assertQueued(CodeMail::class, 1);
    }

    public function test_reset_kata_sandi_mengganti_sandi_dan_memutus_semua_sesi(): void
    {
        $user = $this->verifiedUser();
        [, $plain] = UserToken::issue($user, 'hp');
        $this->post('/lupa-kata-sandi', ['email' => 'budi@example.com']);
        $code = $this->lastCode(EmailCode::PURPOSE_RESET);

        $this->post('/reset-kata-sandi', [
            'email' => 'budi@example.com', 'code' => $code,
            'password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat',
        ])->assertRedirect(route('login'));

        $user->refresh();
        $this->assertTrue(Hash::check('sandi-baru-yang-kuat', $user->password));
        $this->assertSame(1, $user->session_epoch);
        $this->assertSame(0, UserToken::count());
        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer '.$plain])->assertStatus(401);
    }

    public function test_reset_kode_salah_dan_sandi_lemah_tidak_mengubah_apa_apa(): void
    {
        $user = $this->verifiedUser();
        $this->post('/lupa-kata-sandi', ['email' => 'budi@example.com']);
        $code = $this->lastCode(EmailCode::PURPOSE_RESET);
        $salah = $code === '000000' ? '111111' : '000000';

        $this->post('/reset-kata-sandi', [
            'email' => 'budi@example.com', 'code' => $salah,
            'password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat',
        ])->assertSessionHasErrors(['code' => 'Kode salah.']);

        // Sandi lemah ditolak sebelum kode diperiksa, jadi kode yang benar tetap utuh.
        $this->post('/reset-kata-sandi', [
            'email' => 'budi@example.com', 'code' => $code,
            'password' => 'password123', 'password_confirmation' => 'password123',
        ])->assertSessionHasErrors('password');
        $this->assertNull(EmailCode::where('purpose', EmailCode::PURPOSE_RESET)->first()->consumed_at);
        $this->assertTrue(Hash::check(self::PASSWORD, $user->fresh()->password));
    }

    public function test_halaman_lupa_dan_reset_terbuka_untuk_tamu_dan_tertutup_untuk_yang_sudah_masuk(): void
    {
        $this->get('/lupa-kata-sandi')->assertOk();
        $this->get('/reset-kata-sandi')->assertOk();

        $this->actingAs($this->verifiedUser());
        $this->get('/lupa-kata-sandi')->assertRedirect();
        $this->get('/reset-kata-sandi')->assertRedirect();
    }

    // --- Halaman Akun ---

    public function test_halaman_akun_butuh_login(): void
    {
        $this->get('/akun')->assertRedirect(route('login'));
        $this->put('/akun/kata-sandi', [])->assertRedirect(route('login'));
        $this->post('/akun/keluar-semua')->assertRedirect(route('login'));
    }

    public function test_halaman_akun_akun_email_menampilkan_ganti_kata_sandi(): void
    {
        $this->actingAs($this->verifiedUser())->get('/akun')->assertOk()
            ->assertSee('Ganti kata sandi')
            ->assertSee('name="current_password"', false)
            ->assertSee('Keluar dari semua perangkat');
    }

    public function test_halaman_akun_akun_google_menampilkan_buat_kata_sandi_tanpa_kolom_lama(): void
    {
        $user = User::forceCreate(['google_sub' => 'g-1', 'email' => 'budi@example.com', 'name' => 'Budi', 'email_verified_at' => now()]);

        $this->actingAs($user)->get('/akun')->assertOk()
            ->assertSee('Buat kata sandi')
            ->assertDontSee('name="current_password"', false);
    }

    public function test_ganti_kata_sandi_butuh_sandi_lama_yang_benar(): void
    {
        $user = $this->verifiedUser();
        $baru = ['password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat'];

        $this->actingAs($user)->put('/akun/kata-sandi', $baru)->assertSessionHasErrors('current_password');
        $this->actingAs($user)->put('/akun/kata-sandi', ['current_password' => 'bukan-itu-sandinya'] + $baru)
            ->assertSessionHasErrors(['current_password' => 'Kata sandi saat ini salah.']);
        $this->assertTrue(Hash::check(self::PASSWORD, $user->fresh()->password));
    }

    public function test_ganti_kata_sandi_mempertahankan_sesi_ini_dan_mengeluarkan_perangkat_lain(): void
    {
        $user = $this->verifiedUser();
        [, $plain] = UserToken::issue($user, 'hp');

        $this->actingAs($user)->withSession(['session_epoch' => 0])->put('/akun/kata-sandi', [
            'current_password' => self::PASSWORD,
            'password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat',
        ])->assertRedirect(route('account'));

        $user->refresh();
        $this->assertTrue(Hash::check('sandi-baru-yang-kuat', $user->password));
        $this->assertSame(1, $user->session_epoch);
        $this->assertSame(1, session('session_epoch')); // sesi ini dicap ulang, tidak ikut keluar
        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer '.$plain])->assertStatus(401);
    }

    public function test_akun_google_membuat_kata_sandi_tanpa_sandi_lama(): void
    {
        $user = User::forceCreate(['google_sub' => 'g-1', 'email' => 'budi@example.com', 'name' => 'Budi', 'email_verified_at' => now()]);

        $this->actingAs($user)->put('/akun/kata-sandi', [
            'password' => 'sandi-baru-yang-kuat', 'password_confirmation' => 'sandi-baru-yang-kuat',
        ])->assertRedirect(route('account'));

        $this->assertTrue(Hash::check('sandi-baru-yang-kuat', $user->fresh()->password));
    }

    public function test_keluar_dari_semua_perangkat_mencabut_token_dan_sesi_termasuk_ini(): void
    {
        $user = $this->verifiedUser();
        [, $plain] = UserToken::issue($user, 'hp');

        $this->actingAs($user)->post('/akun/keluar-semua')->assertRedirect(route('login'));

        $this->assertGuest();
        $this->assertSame(1, $user->fresh()->session_epoch);
        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer '.$plain])->assertStatus(401);
    }

    public function test_sesi_browser_lama_ditolak_setelah_pemutusan(): void
    {
        $user = $this->verifiedUser();
        $user->revokeWebSessions(); // misalnya dari HP: Keluar dari semua perangkat

        $this->actingAs($user)->withSession(['session_epoch' => 0])->get('/akun')
            ->assertRedirect(route('login'));
        $this->assertGuest();
    }

    public function test_sesi_tanpa_cap_dicap_dengan_angka_sekarang(): void
    {
        $user = $this->verifiedUser();
        $user->revokeWebSessions();

        $this->actingAs($user)->get('/akun')->assertOk();
        $this->assertSame(1, session('session_epoch'));
    }

    // --- Halaman publik ---

    public function test_kebijakan_privasi_terbuka_tanpa_login_dan_memuat_versi_penanggung_jawab_dan_kontak(): void
    {
        config(['habitflow.privacy_contact_email' => 'privasi@example.com', 'habitflow.privacy_policy_version' => '7']);

        $this->get('/kebijakan-privasi')->assertOk()
            ->assertSee('Versi 7')
            ->assertSee(config('habitflow.owner_name'))
            ->assertSee('privasi@example.com')
            ->assertSee('Data kesehatan', false)
            ->assertSee('bukan alat diagnosis', false)
            ->assertSee('3 x 24 jam');
    }

    public function test_halaman_hapus_akun_terbuka_tanpa_login_dan_menjelaskan_data_dan_waktunya(): void
    {
        $this->get('/hapus-akun')->assertOk()
            ->assertSee('langsung dan permanen', false)
            ->assertSee('Data kesehatan')
            ->assertSee('30 hari')
            ->assertSee(route('login'), false);
    }

    public function test_tautan_kebijakan_privasi_dan_hapus_akun_ada_di_halaman_setelah_masuk(): void
    {
        $this->actingAs($this->verifiedUser())->get('/akun')->assertOk()
            ->assertSee(route('privacy'), false)
            ->assertSee(route('delete-account'), false);
    }

    // --- Google di web ---

    private function fakeGoogle(array $overrides = []): void
    {
        $g = Mockery::mock(SocialiteUser::class);
        $g->shouldReceive('getId')->andReturn($overrides['id'] ?? 'google-sub-1');
        $g->shouldReceive('getEmail')->andReturn($overrides['email'] ?? 'budi@example.com');
        $g->shouldReceive('getName')->andReturn('Budi Google');
        $g->shouldReceive('getAvatar')->andReturn('https://example.com/a.png');
        $g->user = ['email_verified' => $overrides['email_verified'] ?? true];

        Socialite::shouldReceive('driver->user')->andReturn($g);
    }

    public function test_google_web_membuat_akun_terverifikasi_dan_masuk(): void
    {
        $this->fakeGoogle();

        $this->get('/auth/google/callback')->assertRedirect(route('dashboard'));

        $user = User::first();
        $this->assertAuthenticatedAs($user);
        $this->assertNotNull($user->email_verified_at);
    }

    public function test_google_web_mengambil_alih_akun_email_yang_belum_terverifikasi(): void
    {
        $this->post('/register', $this->registerData(['password' => 'sandi-penyerang-1', 'password_confirmation' => 'sandi-penyerang-1']));
        $penyerang = User::first();
        [, $tokenPenyerang] = UserToken::issue($penyerang, 'hp-penyerang');
        $this->fakeGoogle();

        $this->get('/auth/google/callback')->assertRedirect(route('dashboard'));

        $this->assertSame(1, User::count());
        $user = User::first();
        $this->assertNull($user->password);
        $this->assertNotNull($user->email_verified_at);
        $this->assertSame('google-sub-1', $user->google_sub);
        $this->assertSame(1, $user->session_epoch);
        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer '.$tokenPenyerang])->assertStatus(401);
    }

    public function test_google_web_email_belum_diverifikasi_google_ditolak(): void
    {
        $this->fakeGoogle(['email_verified' => false]);

        $this->get('/auth/google/callback')->assertRedirect(route('login'));

        $this->assertGuest();
        $this->assertSame(0, User::count());
    }

    public function test_hubungkan_google_ke_akun_yang_sudah_dipakai_akun_lain_ditolak(): void
    {
        User::forceCreate(['google_sub' => 'google-sub-1', 'email' => 'lain@example.com', 'name' => 'Lain', 'email_verified_at' => now()]);
        $user = $this->verifiedUser();
        $this->fakeGoogle();

        $this->actingAs($user)->get('/auth/google/callback')
            ->assertRedirect(route('account'))
            ->assertSessionHasErrors('google');

        $this->assertNull($user->fresh()->google_sub);
    }

    public function test_putuskan_google_ditolak_kalau_tidak_ada_kata_sandi(): void
    {
        $user = User::forceCreate(['google_sub' => 'g-1', 'email' => 'budi@example.com', 'name' => 'Budi', 'email_verified_at' => now()]);

        $this->actingAs($user)->delete('/auth/google')->assertSessionHasErrors('google');

        $this->assertSame('g-1', $user->fresh()->google_sub);
    }

    public function test_putuskan_google_boleh_kalau_ada_kata_sandi(): void
    {
        $user = $this->verifiedUser(['google_sub' => 'g-1']);

        $this->actingAs($user)->delete('/auth/google')->assertRedirect(route('dashboard'));

        $this->assertNull($user->fresh()->google_sub);
    }
}
