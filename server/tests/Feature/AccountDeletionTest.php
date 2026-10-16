<?php

namespace Tests\Feature;

use App\Mail\CodeMail;
use App\Models\EmailCode;
use App\Models\Snapshot;
use App\Models\User;
use App\Models\UserToken;
use App\Services\AccountDeletionService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Mail;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\Storage;
use Tests\Support\DataInventory;
use Tests\TestCase;

/** Hapus akun menyeluruh (tahap 31D): layanan, aturan tim, API, dan web. */
class AccountDeletionTest extends TestCase
{
    use RefreshDatabase;

    private const PASSWORD = 'kopi-susu-gula-aren';

    protected function setUp(): void
    {
        parent::setUp();
        Mail::fake();
        Storage::fake('local');
        config(['services.google.android_client_id' => 'client-uji']);
    }

    private function makeUser(string $email = 'budi@example.com', array $attrs = []): User
    {
        $user = new User(['email' => $email, 'name' => 'Pengguna '.$email, 'password' => self::PASSWORD] + $attrs);
        $user->forceFill(['email_verified_at' => now()])->save();

        return $user;
    }

    /** Isi satu baris di SETIAP tabel milik pengguna, termasuk berkas snapshot. */
    private function populate(User $user): void
    {
        UserToken::issue($user, 'hp');
        DB::table('email_codes')->insert([
            'user_id' => $user->id, 'purpose' => 'verify', 'code_hash' => str_repeat('a', 64),
            'expires_at' => now()->addMinutes(10), 'created_at' => now(), 'updated_at' => now(),
        ]);
        $habitId = DB::table('habits')->insertGetId(['user_id' => $user->id, 'name' => 'Sholat', 'created_at' => now(), 'updated_at' => now()]);
        DB::table('habit_entries')->insert(['habit_id' => $habitId, 'date' => '2026-10-12', 'created_at' => now(), 'updated_at' => now()]);
        $blockId = DB::table('schedule_blocks')->insertGetId([
            'user_id' => $user->id, 'name' => 'Pagi', 'start_value' => 300, 'created_at' => now(), 'updated_at' => now(),
        ]);
        DB::table('schedule_block_habits')->insert(['schedule_block_id' => $blockId, 'habit_id' => $habitId]);
        DB::table('todos')->insert(['user_id' => $user->id, 'title' => 'Beli susu', 'date' => '2026-10-12', 'created_at' => now(), 'updated_at' => now()]);
        DB::table('follow_ups')->insert(['user_id' => $user->id, 'title' => 'Telepon Pak Andi', 'created_at' => now(), 'updated_at' => now()]);
        DB::table('prayer_days')->insert(['user_id' => $user->id, 'date' => '2026-10-12', 'timezone' => 'Asia/Jakarta', 'created_at' => now(), 'updated_at' => now()]);
        DB::table('daily_briefs')->insert(['user_id' => $user->id, 'date' => '2026-10-12', 'email_digest' => '3 email belum dibaca', 'created_at' => now(), 'updated_at' => now()]);
        DB::table('work_days')->insert(['user_id' => $user->id, 'date' => '2026-10-12', 'eod_note' => 'catatan EOD', 'created_at' => now(), 'updated_at' => now()]);
        DB::table('weight_entries')->insert(['user_id' => $user->id, 'time_millis' => 1792000000000, 'kg' => 70.4, 'created_at' => now(), 'updated_at' => now()]);
        DB::table('blood_pressure_entries')->insert(['user_id' => $user->id, 'time_millis' => 1792000000000, 'systolic' => 120, 'diastolic' => 80, 'created_at' => now(), 'updated_at' => now()]);
        DB::table('step_days')->insert(['user_id' => $user->id, 'date' => '2026-10-12', 'steps' => 5400, 'created_at' => now(), 'updated_at' => now()]);
        DB::table('health_focus')->insert(['user_id' => $user->id, 'timezone' => 'Asia/Jakarta', 'rule' => 'ON_TRACK', 'title' => 'Hari ini sudah di jalur', 'created_at' => now(), 'updated_at' => now()]);

        $path = 'snapshots/'.$user->id.'/uji.enc';
        Storage::disk('local')->put($path, 'isi-terenkripsi');
        Snapshot::create([
            'user_id' => $user->id, 'schema_version' => 1,
            'bytes' => 15, 'sha256' => str_repeat('b', 64), 'path' => $path,
        ]);
    }

    private function rowCount(string $table, ?User $user = null): int
    {
        return DB::table($table)->count();
    }

    private function newTeam(User $owner, string $name = 'Rumah'): int
    {
        $id = DB::table('teams')->insertGetId(['name' => $name, 'created_by' => $owner->id, 'created_at' => now(), 'updated_at' => now()]);
        $this->join($id, $owner, now()->subDays(10));

        return $id;
    }

    private function join(int $teamId, User $user, $at): void
    {
        DB::table('team_members')->insert(['team_id' => $teamId, 'user_id' => $user->id, 'created_at' => $at, 'updated_at' => $at]);
    }

    private function teamTodo(int $teamId, User $creator, string $title = 'Cuci piring'): int
    {
        return DB::table('team_todos')->insertGetId([
            'team_id' => $teamId, 'title' => $title, 'created_by' => $creator->id, 'created_at' => now(), 'updated_at' => now(),
        ]);
    }

    private function invite(int $teamId, User $creator, string $token): void
    {
        DB::table('team_invites')->insert([
            'team_id' => $teamId, 'token' => $token, 'created_by' => $creator->id,
            'expires_at' => now()->addDay(), 'created_at' => now(), 'updated_at' => now(),
        ]);
    }

    // --- Layanan: menyeluruh ---

    public function test_hapus_akun_membuat_nol_baris_di_semua_tabel_data_pengguna_dan_nol_berkas(): void
    {
        $user = $this->makeUser();
        $this->populate($user);
        $teamId = $this->newTeam($user);
        $this->teamTodo($teamId, $user);
        $this->invite($teamId, $user, 'tok-1');
        $this->assertCount(1, Storage::disk('local')->allFiles());

        app(AccountDeletionService::class)->delete($user, AccountDeletionService::SOURCE_APP);

        foreach (DataInventory::DELETED_WITH_ACCOUNT as $table) {
            $this->assertSame(0, $this->rowCount($table), "Tabel $table masih berisi baris setelah hapus akun.");
        }
        $this->assertSame([], Storage::disk('local')->allFiles(), 'Berkas snapshot tersisa.');
    }

    public function test_setiap_tabel_data_pengguna_benar_benar_terisi_di_tes_ini(): void
    {
        // Menjaga tes di atas: kalau tabel baru ditambah ke daftar tapi tidak diisi di populate(), tes menyeluruh
        // lulus karena tabelnya memang kosong dari awal. Tes ini gagal untuk kasus itu.
        $user = $this->makeUser();
        $this->populate($user);
        $teamId = $this->newTeam($user);
        $this->teamTodo($teamId, $user);
        $this->invite($teamId, $user, 'tok-1');

        foreach (DataInventory::DELETED_WITH_ACCOUNT as $table) {
            $this->assertGreaterThan(0, $this->rowCount($table), "populate() belum mengisi tabel $table.");
        }
    }

    public function test_hapus_akun_tidak_menyentuh_data_pengguna_lain(): void
    {
        $budi = $this->makeUser('budi@example.com');
        $sari = $this->makeUser('sari@example.com');
        $this->populate($budi);
        $this->populate($sari);
        $teamId = $this->newTeam($sari);
        $this->teamTodo($teamId, $sari);

        app(AccountDeletionService::class)->delete($budi, AccountDeletionService::SOURCE_APP);

        $this->assertSame(1, User::count());
        $this->assertSame($sari->id, User::first()->id);
        foreach (['user_tokens', 'email_codes', 'habits', 'schedule_blocks', 'todos', 'follow_ups', 'prayer_days', 'daily_briefs', 'work_days', 'weight_entries', 'blood_pressure_entries', 'step_days', 'health_focus', 'snapshots'] as $table) {
            $this->assertSame(1, DB::table($table)->where('user_id', $sari->id)->count(), "Baris $table milik pengguna lain ikut terhapus.");
        }
        $this->assertSame(1, DB::table('habit_entries')->count());
        $this->assertSame(1, DB::table('schedule_block_habits')->count());
        $this->assertSame(1, DB::table('teams')->count());
        $this->assertSame(1, DB::table('team_todos')->count());
        $this->assertCount(1, Storage::disk('local')->allFiles());
    }

    public function test_catatan_audit_hanya_waktu_dan_sumber_tanpa_data_pribadi(): void
    {
        $user = $this->makeUser('rahasia@example.com');

        app(AccountDeletionService::class)->delete($user, AccountDeletionService::SOURCE_WEB);

        $this->assertEqualsCanonicalizing(['id', 'source', 'deleted_at'], Schema::getColumnListing('account_deletions'));
        $row = DB::table('account_deletions')->first();
        $this->assertSame('web', $row->source);
        $this->assertStringNotContainsString('rahasia', json_encode($row));
        $this->assertSame(1, DB::table('account_deletions')->count());
    }

    public function test_email_yang_menunggu_di_antrean_ikut_dihapus_dan_milik_orang_lain_tetap(): void
    {
        $budi = $this->makeUser('budi@example.com');
        $sari = $this->makeUser('sari@example.com');
        $job = fn (string $email) => ['queue' => 'default', 'payload' => '{"to":"'.$email.'","code":"123456"}', 'attempts' => 0, 'available_at' => time(), 'created_at' => time()];
        $failed = fn (string $email, string $uuid) => ['uuid' => $uuid, 'connection' => 'database', 'queue' => 'default', 'payload' => '{"to":"'.$email.'"}', 'exception' => 'x'];
        DB::table('jobs')->insert([$job('budi@example.com'), $job('sari@example.com')]);
        DB::table('failed_jobs')->insert([$failed('budi@example.com', 'u1'), $failed('sari@example.com', 'u2')]);
        app(AccountDeletionService::class)->delete($budi, AccountDeletionService::SOURCE_APP);

        foreach (['jobs', 'failed_jobs'] as $table) {
            $this->assertSame(1, DB::table($table)->count());
            $this->assertStringContainsString('sari@', DB::table($table)->value('payload'));
        }
    }

    public function test_gagal_di_tengah_transaksi_tidak_menghapus_apa_pun_termasuk_berkas(): void
    {
        $user = $this->makeUser();
        $this->populate($user);
        // Memaksa galat di langkah terakhir transaksi: insert catatan audit gagal setelah semua baris dihapus.
        DB::listen(function ($query) {
            if (str_contains($query->sql, 'account_deletions')) {
                throw new \RuntimeException('galat paksaan');
            }
        });

        try {
            app(AccountDeletionService::class)->delete($user, AccountDeletionService::SOURCE_APP);
            $this->fail('Seharusnya melempar galat.');
        } catch (\Throwable) {
            // diharapkan
        }

        $this->assertSame(1, User::count());
        $this->assertSame(1, DB::table('habits')->count());
        $this->assertSame(1, DB::table('snapshots')->count());
        $this->assertCount(1, Storage::disk('local')->allFiles(), 'Berkas hilang walau transaksinya gagal.');
    }

    // --- Aturan tim ---

    public function test_pemilik_dengan_anggota_lain_memindahkan_kepemilikan_ke_anggota_terlama(): void
    {
        $pemilik = $this->makeUser('pemilik@example.com');
        $baru = $this->makeUser('baru@example.com');
        $lama = $this->makeUser('lama@example.com');
        $teamId = $this->newTeam($pemilik);
        $this->join($teamId, $baru, now()->subDay());
        $this->join($teamId, $lama, now()->subDays(5)); // bergabung lebih dulu dari $baru
        $todoId = $this->teamTodo($teamId, $pemilik);
        DB::table('team_todos')->where('id', $todoId)->update(['assigned_to' => $pemilik->id, 'done_by' => $pemilik->id]);

        app(AccountDeletionService::class)->delete($pemilik, AccountDeletionService::SOURCE_APP);

        $this->assertSame($lama->id, DB::table('teams')->where('id', $teamId)->value('created_by'));
        $this->assertSame(2, DB::table('team_members')->where('team_id', $teamId)->count());
        $todo = DB::table('team_todos')->where('id', $todoId)->first();
        $this->assertNotNull($todo, 'To-do tim buatan pemilik harus tetap ada.');
        $this->assertNull($todo->created_by);
        $this->assertNull($todo->assigned_to);
        $this->assertNull($todo->done_by);
    }

    public function test_pemilik_tanpa_anggota_lain_menghapus_tim_dan_isinya(): void
    {
        $pemilik = $this->makeUser();
        $teamId = $this->newTeam($pemilik);
        $this->teamTodo($teamId, $pemilik);
        $this->invite($teamId, $pemilik, 'tok-1');

        app(AccountDeletionService::class)->delete($pemilik, AccountDeletionService::SOURCE_APP);

        foreach (['teams', 'team_members', 'team_todos', 'team_invites'] as $table) {
            $this->assertSame(0, DB::table($table)->count(), $table);
        }
    }

    public function test_anggota_biasa_hanya_keluar_dari_tim(): void
    {
        $pemilik = $this->makeUser('pemilik@example.com');
        $anggota = $this->makeUser('anggota@example.com');
        $teamId = $this->newTeam($pemilik);
        $this->join($teamId, $anggota, now()->subDay());
        $todoPemilik = $this->teamTodo($teamId, $pemilik, 'Milik pemilik');
        $todoAnggota = $this->teamTodo($teamId, $anggota, 'Milik anggota');
        $this->invite($teamId, $anggota, 'tok-anggota');
        $this->invite($teamId, $pemilik, 'tok-pemilik');

        app(AccountDeletionService::class)->delete($anggota, AccountDeletionService::SOURCE_APP);

        $this->assertSame($pemilik->id, DB::table('teams')->where('id', $teamId)->value('created_by'));
        $this->assertSame([$pemilik->id], DB::table('team_members')->where('team_id', $teamId)->pluck('user_id')->all());
        $this->assertSame($pemilik->id, (int) DB::table('team_todos')->where('id', $todoPemilik)->value('created_by'));
        $this->assertNotNull(DB::table('team_todos')->where('id', $todoAnggota)->first(), 'To-do buatan anggota tetap ada.');
        $this->assertNull(DB::table('team_todos')->where('id', $todoAnggota)->value('created_by'));
        $this->assertSame(['tok-pemilik'], DB::table('team_invites')->pluck('token')->all(), 'Undangan buatan anggota harus hangus, buatan pemilik tetap.');
    }

    public function test_undangan_yang_dipakai_pengguna_dihapus_tetap_ada_tanpa_penggunanya(): void
    {
        $pemilik = $this->makeUser('pemilik@example.com');
        $anggota = $this->makeUser('anggota@example.com');
        $teamId = $this->newTeam($pemilik);
        $this->invite($teamId, $pemilik, 'tok-dipakai');
        DB::table('team_invites')->update(['used_by' => $anggota->id, 'used_at' => now()]);

        app(AccountDeletionService::class)->delete($anggota, AccountDeletionService::SOURCE_APP);

        $this->assertNull(DB::table('team_invites')->value('used_by'));
    }

    // --- API ---

    private function bearer(User $user): array
    {
        [, $plain] = UserToken::issue($user, 'hp-uji');

        return ['Authorization' => 'Bearer '.$plain];
    }

    public function test_api_hapus_akun_dengan_kata_sandi_benar_menghapus_semuanya_dan_mencabut_token(): void
    {
        $user = $this->makeUser();
        $this->populate($user);
        $h = $this->bearer($user);

        $this->deleteJson('/api/v1/account', ['password' => self::PASSWORD], $h)->assertNoContent();

        $this->assertSame(0, User::count());
        $this->assertSame(0, UserToken::count());
        $this->getJson('/api/v1/me', $h)->assertStatus(401);
        $this->assertSame('app', DB::table('account_deletions')->value('source'));
        $this->assertSame([], Storage::disk('local')->allFiles());
    }

    public function test_api_hapus_akun_kata_sandi_salah_atau_kosong_tidak_menghapus(): void
    {
        $user = $this->makeUser();
        $h = $this->bearer($user);

        $this->deleteJson('/api/v1/account', [], $h)->assertStatus(422)->assertJsonValidationErrors('password');
        $this->deleteJson('/api/v1/account', ['password' => 'bukan-sandinya'], $h)
            ->assertStatus(422)->assertJson(['code' => 'invalid_credentials']);

        $this->assertSame(1, User::count());
    }

    public function test_api_hapus_akun_salah_berulang_dikunci(): void
    {
        $user = $this->makeUser();
        $h = $this->bearer($user);

        foreach (range(1, 5) as $i) {
            $this->deleteJson('/api/v1/account', ['password' => 'bukan-sandinya'], $h)->assertStatus(422);
        }

        $this->deleteJson('/api/v1/account', ['password' => self::PASSWORD], $h)
            ->assertStatus(429)->assertJson(['code' => 'too_many_attempts']);
        $this->assertSame(1, User::count());
    }

    public function test_api_hapus_akun_tanpa_token_ditolak(): void
    {
        $this->makeUser();

        $this->deleteJson('/api/v1/account', ['password' => self::PASSWORD])->assertStatus(401);
        $this->assertSame(1, User::count());
    }

    public function test_api_akun_google_tanpa_sandi_konfirmasi_dengan_id_token(): void
    {
        $user = User::forceCreate(['google_sub' => 'google-sub-1', 'email' => 'budi@example.com', 'name' => 'Budi', 'email_verified_at' => now()]);
        $h = $this->bearer($user);
        $claims = ['aud' => 'client-uji', 'sub' => 'google-sub-1', 'email' => 'budi@example.com', 'email_verified' => 'true'];

        $this->deleteJson('/api/v1/account', [], $h)->assertStatus(422)->assertJsonValidationErrors('id_token');

        Http::fake(['oauth2.googleapis.com/*' => Http::sequence()->push(['sub' => 'google-lain'] + $claims)->push($claims)]);
        $this->deleteJson('/api/v1/account', ['id_token' => 'x'], $h)->assertStatus(422)->assertJson(['code' => 'invalid_credentials']);
        $this->assertSame(1, User::count());

        $this->deleteJson('/api/v1/account', ['id_token' => 'x'], $h)->assertNoContent();
        $this->assertSame(0, User::count());
    }

    public function test_api_akun_google_token_untuk_aplikasi_lain_ditolak(): void
    {
        $user = User::forceCreate(['google_sub' => 'google-sub-1', 'email' => 'budi@example.com', 'name' => 'Budi', 'email_verified_at' => now()]);
        Http::fake(['oauth2.googleapis.com/*' => Http::response(['aud' => 'client-lain', 'sub' => 'google-sub-1', 'email_verified' => 'true'])]);

        $this->deleteJson('/api/v1/account', ['id_token' => 'x'], $this->bearer($user))->assertStatus(422);
        $this->assertSame(1, User::count());
    }

    // --- Web: kata sandi ---

    public function test_web_hapus_akun_dengan_kata_sandi_menghapus_dan_keluar(): void
    {
        $user = $this->makeUser();
        $this->populate($user);

        $this->actingAs($user)->delete('/akun', ['delete_password' => self::PASSWORD])
            ->assertRedirect(route('login'))
            ->assertSessionHas('status', 'Akun dan semua datamu sudah dihapus.');

        $this->assertGuest();
        $this->assertSame(0, User::count());
        $this->assertSame('web', DB::table('account_deletions')->value('source'));
        $this->assertSame([], Storage::disk('local')->allFiles());
    }

    public function test_web_hapus_akun_kata_sandi_salah_tidak_menghapus(): void
    {
        $user = $this->makeUser();

        $this->actingAs($user)->delete('/akun', ['delete_password' => 'bukan-sandinya'])
            ->assertSessionHasErrors(['delete_password' => 'Kata sandi salah.']);
        $this->actingAs($user)->delete('/akun', [])->assertSessionHasErrors('delete_password');

        $this->assertSame(1, User::count());
    }

    public function test_web_hapus_akun_butuh_login(): void
    {
        $this->makeUser();

        $this->delete('/akun', ['delete_password' => self::PASSWORD])->assertRedirect(route('login'));
        $this->assertSame(1, User::count());
    }

    public function test_halaman_akun_menampilkan_cara_konfirmasi_sesuai_punya_sandi_atau_tidak(): void
    {
        $this->actingAs($this->makeUser())->get('/akun')->assertOk()
            ->assertSee('name="delete_password"', false)
            ->assertDontSee('Kirim kode hapus akun');

        $google = User::forceCreate(['google_sub' => 'g-1', 'email' => 'g@example.com', 'name' => 'G', 'email_verified_at' => now()]);
        $this->actingAs($google)->get('/akun')->assertOk()
            ->assertSee('Kirim kode hapus akun')
            ->assertDontSee('name="delete_password"', false);
    }

    // --- Web: kode email (publik, tanpa login) ---

    private function deletionCode(): string
    {
        return Mail::queued(CodeMail::class, fn (CodeMail $m) => $m->purpose === EmailCode::PURPOSE_DELETE)->last()->code;
    }

    public function test_halaman_hapus_akun_publik_punya_form_minta_kode(): void
    {
        $this->get('/hapus-akun')->assertOk()
            ->assertSee(route('delete-account.code'), false)
            ->assertSee('name="email"', false);
        $this->get('/hapus-akun/konfirmasi')->assertOk()->assertSee('name="code"', false);
    }

    public function test_kode_email_menghapus_akun_tanpa_login_walau_punya_kata_sandi(): void
    {
        $user = $this->makeUser();
        $this->populate($user);

        $this->post('/hapus-akun/kode', ['email' => 'budi@example.com'])
            ->assertRedirect(route('delete-account.confirm'))
            ->assertSessionHas('deletion_email', 'budi@example.com');
        $code = $this->deletionCode();

        $this->post('/hapus-akun/konfirmasi', ['email' => 'budi@example.com', 'code' => $code])
            ->assertRedirect(route('login'))
            ->assertSessionHas('status', 'Akun dan semua datamu sudah dihapus.');

        $this->assertSame(0, User::count());
        $this->assertSame('web-code', DB::table('account_deletions')->value('source'));
        $this->assertSame([], Storage::disk('local')->allFiles());
    }

    public function test_kode_hapus_akun_jawabannya_sama_untuk_email_terdaftar_dan_tidak_dan_email_hanya_untuk_yang_terdaftar(): void
    {
        $this->makeUser();

        $ada = $this->post('/hapus-akun/kode', ['email' => 'budi@example.com']);
        $status = session('status');
        $tidak = $this->post('/hapus-akun/kode', ['email' => 'asing@example.com']);

        $this->assertSame($ada->headers->get('Location'), $tidak->headers->get('Location'));
        $this->assertSame($status, session('status'));
        Mail::assertQueued(CodeMail::class, 1);
    }

    public function test_akun_belum_terverifikasi_tidak_dikirimi_kode_hapus(): void
    {
        $user = new User(['email' => 'baru@example.com', 'name' => 'Baru', 'password' => self::PASSWORD]);
        $user->save();

        $this->post('/hapus-akun/kode', ['email' => 'baru@example.com'])->assertRedirect();

        Mail::assertNothingQueued();
    }

    public function test_kode_hapus_salah_kedaluwarsa_atau_dari_tujuan_lain_tidak_menghapus(): void
    {
        $user = $this->makeUser();
        $this->post('/hapus-akun/kode', ['email' => 'budi@example.com']);
        $code = $this->deletionCode();
        $salah = $code === '000000' ? '111111' : '000000';

        $this->post('/hapus-akun/konfirmasi', ['email' => 'budi@example.com', 'code' => $salah])
            ->assertSessionHasErrors(['code' => 'Kode salah.']);
        $this->assertSame(1, User::count());

        // Kode lupa kata sandi bukan kode hapus akun.
        $this->post('/lupa-kata-sandi', ['email' => 'budi@example.com']);
        $resetCode = Mail::queued(CodeMail::class, fn (CodeMail $m) => $m->purpose === EmailCode::PURPOSE_RESET)->last()->code;
        if ($resetCode !== $code) {
            $this->post('/hapus-akun/konfirmasi', ['email' => 'budi@example.com', 'code' => $resetCode])->assertSessionHasErrors('code');
            $this->assertSame(1, User::count());
        }

        $this->travel(11)->minutes();
        $this->post('/hapus-akun/konfirmasi', ['email' => 'budi@example.com', 'code' => $code])
            ->assertSessionHasErrors(['code' => 'Kode sudah kedaluwarsa. Minta kode baru.']);
        $this->assertSame(1, User::count());
    }

    public function test_kode_hapus_sekali_pakai_dan_dibatasi_percobaannya(): void
    {
        $this->makeUser();
        $this->post('/hapus-akun/kode', ['email' => 'budi@example.com']);
        $code = $this->deletionCode();
        $salah = $code === '000000' ? '111111' : '000000';

        foreach (range(1, 5) as $i) {
            $this->post('/hapus-akun/konfirmasi', ['email' => 'budi@example.com', 'code' => $salah]);
        }
        // Lima kali salah menghanguskan kode, jadi yang benar pun tidak lagi berlaku.
        $this->post('/hapus-akun/konfirmasi', ['email' => 'budi@example.com', 'code' => $code])->assertSessionHasErrors('code');
        $this->assertSame(1, User::count());
    }

    public function test_pengguna_yang_masuk_menghapus_akun_google_lewat_kode_dan_sesinya_berakhir(): void
    {
        $user = User::forceCreate(['google_sub' => 'g-1', 'email' => 'g@example.com', 'name' => 'G', 'email_verified_at' => now()]);

        $this->actingAs($user)->post('/hapus-akun/kode', ['email' => 'g@example.com'])->assertRedirect(route('delete-account.confirm'));
        $this->actingAs($user)->post('/hapus-akun/konfirmasi', ['email' => 'g@example.com', 'code' => $this->deletionCode()])
            ->assertRedirect(route('login'));

        $this->assertGuest();
        $this->assertSame(0, User::count());
    }
}
