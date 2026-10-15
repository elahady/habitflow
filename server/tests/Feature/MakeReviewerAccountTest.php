<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

/** Perintah `habitflow:make-reviewer-account` (31L): akun terverifikasi dengan data contoh untuk peninjau Play. */
class MakeReviewerAccountTest extends TestCase
{
    use RefreshDatabase;

    private function login(string $email, string $password)
    {
        return $this->postJson('/api/v1/auth/login', ['email' => $email, 'password' => $password, 'device_name' => 'uji']);
    }

    public function test_membuat_akun_terverifikasi_yang_bisa_masuk_dan_berisi_data_contoh(): void
    {
        $this->artisan('habitflow:make-reviewer-account', ['email' => 'Peninjau@Contoh.com', '--password' => 'sandi-peninjau-1'])
            ->assertSuccessful();

        $user = User::where('email', 'peninjau@contoh.com')->firstOrFail();
        $this->assertTrue($user->hasVerifiedEmail());
        $this->assertNotNull($user->privacy_consented_at);
        $this->assertSame(config('habitflow.privacy_policy_version'), $user->privacy_policy_version);
        $this->assertTrue(Hash::check('sandi-peninjau-1', $user->password));
        $this->assertSame(3, DB::table('habits')->where('user_id', $user->id)->count());
        $this->assertGreaterThan(0, DB::table('habit_entries')->count());
        $this->assertSame(3, DB::table('todos')->where('user_id', $user->id)->count());
        $this->assertSame(2, DB::table('follow_ups')->where('user_id', $user->id)->count());

        $this->login('peninjau@contoh.com', 'sandi-peninjau-1')
            ->assertOk()->assertJsonStructure(['token', 'account' => ['id', 'email']]);
    }

    public function test_tanpa_password_dibuat_acak_dan_dicetak_sekali(): void
    {
        $this->artisan('habitflow:make-reviewer-account', ['email' => 'p@contoh.com'])
            ->expectsOutputToContain('Kata sandi (hanya tampil sekali')
            ->assertSuccessful();

        $this->assertNotNull(User::where('email', 'p@contoh.com')->first()?->password);
    }

    public function test_menolak_akun_yang_sudah_ada_kecuali_reset_password(): void
    {
        $this->artisan('habitflow:make-reviewer-account', ['email' => 'p@contoh.com', '--password' => 'sandi-lama-123'])->assertSuccessful();
        $tokenLama = $this->login('p@contoh.com', 'sandi-lama-123')->assertOk()['token'];

        $this->artisan('habitflow:make-reviewer-account', ['email' => 'p@contoh.com', '--password' => 'sandi-baru-456'])->assertFailed();
        $this->login('p@contoh.com', 'sandi-lama-123')->assertOk();

        $this->artisan('habitflow:make-reviewer-account', ['email' => 'p@contoh.com', '--password' => 'sandi-baru-456', '--reset-password' => true])
            ->assertSuccessful();
        $this->login('p@contoh.com', 'sandi-baru-456')->assertOk();
        $this->login('p@contoh.com', 'sandi-lama-123')->assertStatus(422);
        $this->getJson('/api/v1/me', ['Authorization' => 'Bearer '.$tokenLama])->assertStatus(401);
        $this->assertSame(3, DB::table('habits')->count(), 'Data contoh tidak dibuat dua kali.');
    }

    public function test_menolak_email_dan_kata_sandi_yang_tidak_sah(): void
    {
        $this->artisan('habitflow:make-reviewer-account', ['email' => 'bukan-email'])->assertFailed();
        $this->artisan('habitflow:make-reviewer-account', ['email' => 'p@contoh.com', '--password' => 'pendek'])->assertFailed();
        $this->assertSame(0, User::count());
    }
}
