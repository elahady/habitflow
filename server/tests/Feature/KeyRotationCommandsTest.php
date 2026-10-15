<?php

namespace Tests\Feature;

use App\Models\Snapshot;
use App\Models\User;
use App\Models\UserToken;
use Illuminate\Encryption\Encrypter;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

/** Perintah 31K: memutar APP_KEY tanpa kehilangan snapshot, dan mencabut semua sesi saat ada insiden. */
class KeyRotationCommandsTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Storage::fake('local');
    }

    private function user(string $email = 'a@contoh.com'): User
    {
        return User::create(['google_sub' => 'sub-'.$email, 'email' => $email, 'name' => $email]);
    }

    private function snapshot(User $user, string $path, string $cipher): Snapshot
    {
        Storage::disk('local')->put($path, $cipher);

        return Snapshot::create([
            'user_id' => $user->id, 'schema_version' => 1, 'bytes' => 1,
            'sha256' => hash('sha256', $path), 'path' => $path,
        ]);
    }

    /** Pasang kunci baru sebagai APP_KEY dan kunci saat ini sebelumnya sebagai kunci lama. Mengembalikan enkripsi kunci lama. */
    private function putarKunci(): Encrypter
    {
        $lama = Encrypter::generateKey(config('app.cipher'));
        $baru = Encrypter::generateKey(config('app.cipher'));
        config([
            'app.key' => 'base64:'.base64_encode($baru),
            'app.previous_keys' => [$lama],
        ]);
        app()->forgetInstance('encrypter');
        Crypt::clearResolvedInstance('encrypter');

        return new Encrypter($lama, config('app.cipher'));
    }

    public function test_snapshot_lama_ditulis_ulang_dan_terbaca_dengan_kunci_baru_saja(): void
    {
        $user = $this->user();
        $lama = $this->putarKunci();
        $s = $this->snapshot($user, 'snapshots/u1/a.enc', $lama->encryptString('{"data":"rahasia"}'));

        $this->artisan('habitflow:reencrypt-snapshots')->expectsOutputToContain('1 snapshot ditulis ulang')->assertSuccessful();

        // Setelah kunci lama dibuang dari APP_PREVIOUS_KEYS, snapshot tetap terbaca.
        config(['app.previous_keys' => []]);
        app()->forgetInstance('encrypter');
        Crypt::clearResolvedInstance('encrypter');
        $this->assertSame('{"data":"rahasia"}', Crypt::decryptString(Storage::disk('local')->get($s->path)));
        $this->assertSame([], array_values(array_filter(Storage::disk('local')->allFiles('snapshots'), fn ($f) => str_ends_with($f, '.tmp'))), 'tidak ada berkas sementara');
        $this->assertCount(1, Storage::disk('local')->allFiles('snapshots'));
    }

    public function test_dry_run_tidak_menulis_apa_pun(): void
    {
        $user = $this->user();
        $lama = $this->putarKunci();
        $cipher = $lama->encryptString('isi');
        $s = $this->snapshot($user, 'snapshots/u1/a.enc', $cipher);

        $this->artisan('habitflow:reencrypt-snapshots', ['--dry-run' => true])
            ->expectsOutputToContain('1 snapshot akan ditulis ulang')->assertSuccessful();

        $this->assertSame($cipher, Storage::disk('local')->get($s->path));
    }

    public function test_aman_diulang_dan_yang_sudah_memakai_kunci_baru_dilewati(): void
    {
        $user = $this->user();
        $lama = $this->putarKunci();
        $this->snapshot($user, 'snapshots/u1/a.enc', $lama->encryptString('satu'));
        $this->snapshot($user, 'snapshots/u1/b.enc', Crypt::encryptString('dua'));

        $this->artisan('habitflow:reencrypt-snapshots')->expectsOutputToContain('1 snapshot ditulis ulang, 1 sudah memakai kunci saat ini')->assertSuccessful();
        $this->artisan('habitflow:reencrypt-snapshots')->expectsOutputToContain('0 snapshot ditulis ulang, 2 sudah memakai kunci saat ini')->assertSuccessful();
    }

    public function test_snapshot_yang_tidak_terbaca_atau_hilang_dilaporkan_dan_perintah_gagal(): void
    {
        $user = $this->user();
        $this->putarKunci();
        $asing = new Encrypter(Encrypter::generateKey(config('app.cipher')), config('app.cipher'));
        $rusak = $this->snapshot($user, 'snapshots/u1/rusak.enc', $asing->encryptString('bukan milik kita'));
        $hilang = $this->snapshot($user, 'snapshots/u1/hilang.enc', 'x');
        Storage::disk('local')->delete($hilang->path);
        $cipherAsli = Storage::disk('local')->get($rusak->path);

        $this->artisan('habitflow:reencrypt-snapshots')
            ->expectsOutputToContain('tidak terbaca dengan APP_KEY maupun APP_PREVIOUS_KEYS')
            ->expectsOutputToContain('berkas tidak ada')
            ->assertFailed();

        $this->assertSame($cipherAsli, Storage::disk('local')->get($rusak->path), 'snapshot yang gagal tidak boleh diubah');
    }

    public function test_cabut_semua_sesi_menghapus_token_dan_memutus_sesi_web_semua_akun(): void
    {
        $a = $this->user('a@contoh.com');
        $b = $this->user('b@contoh.com');
        $a->forceFill(['remember_token' => 'ingat-a'])->save();
        [, $plainA] = UserToken::issue($a, 'hp');
        UserToken::issue($b, 'hp');
        $epochA = $a->session_epoch;

        $this->artisan('habitflow:revoke-all-sessions', ['--force' => true])
            ->expectsOutputToContain('2 token app dicabut, sesi web 2 akun diputus')->assertSuccessful();

        $this->assertSame(0, UserToken::count());
        $this->assertSame($epochA + 1, $a->fresh()->session_epoch);
        $this->assertNull($a->fresh()->remember_token);
        $this->getJson('/api/v1/ping', ['Authorization' => 'Bearer '.$plainA])->assertStatus(401);
        $this->assertSame(2, User::count(), 'akun dan datanya tidak disentuh');
    }

    public function test_cabut_semua_sesi_dibatalkan_kalau_tidak_dikonfirmasi(): void
    {
        $a = $this->user();
        UserToken::issue($a, 'hp');

        $this->artisan('habitflow:revoke-all-sessions')->expectsConfirmation('Ini mencabut 1 token app dan memutus sesi web 1 akun. Lanjut?', 'no')->assertFailed();

        $this->assertSame(1, UserToken::count());
        $this->assertSame(0, DB::table('users')->where('session_epoch', '>', 0)->count());
    }
}
