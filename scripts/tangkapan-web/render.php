<?php
/**
 * Render halaman web Habitflow dari DATA CONTOH ke HTML statis, untuk difoto (lihat ../ambil-tangkapan-web.ps1).
 *
 * Memakai database SQLite sementara di folder keluaran, bukan database server/ yang asli: skrip menolak jalan
 * kalau path database-nya ada di dalam server/. Tanggal dibekukan ke Jumat 16 Oktober 2026 10.20 WIB dan
 * angka acak diberi benih tetap, jadi keluarannya sama setiap dijalankan. Pengguna bernama "Demo".
 * Pemakaian: php render.php <folder-keluaran>
 */
$out = rtrim(str_replace(chr(92), '/', $argv[1] ?? ''), '/');
if ($out === '') {
    fwrite(STDERR, "Pemakaian: php render.php <folder-keluaran>\n");
    exit(2);
}
@mkdir($out, 0777, true);
$out = str_replace(chr(92), '/', realpath($out));
$server = str_replace(chr(92), '/', realpath(__DIR__.'/../../server'));
$db = "$out/tangkapan.sqlite";
if (str_starts_with($db, "$server/")) {
    fwrite(STDERR, "Folder keluaran tidak boleh di dalam server/ ($db)\n");
    exit(2);
}
@unlink($db);
touch($db);
$publicUrl = 'file:///'.ltrim("$server/public", '/');
foreach ([
    'DB_CONNECTION' => 'sqlite', 'DB_DATABASE' => $db, 'SESSION_DRIVER' => 'array', 'CACHE_STORE' => 'array',
    'APP_URL' => $publicUrl, 'APP_ENV' => 'local', 'APP_DEBUG' => 'true', 'QUEUE_CONNECTION' => 'sync',
] as $k => $v) {
    putenv("$k=$v");
    $_ENV[$k] = $v;
    $_SERVER[$k] = $v;
}
require "$server/vendor/autoload.php";
$app = require "$server/bootstrap/app.php";
$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();
$kernel = $app->make(Illuminate\Contracts\Http\Kernel::class);
Illuminate\Support\Facades\Artisan::call('migrate', ['--force' => true]);

use App\Models\Team;
use App\Models\User;
use Illuminate\Support\Carbon;

Carbon::setTestNow(Carbon::parse('2026-10-16 10:20', 'Asia/Jakarta'));
mt_srand(42);
$u = User::create(['google_sub' => 's1', 'email' => 'roziq@example.com', 'name' => 'Demo']);
$p = User::create(['google_sub' => 's2', 'email' => 'istri@example.com', 'name' => 'Aisyah']);
$today = '2026-10-16';

// Habit + riwayat 200 hari
$names = [['Sholat Subuh berjamaah', 1], ['Tilawah 1 halaman', 1], ['Olahraga 20 menit', 1], ['Minum 2 liter air', 0], ['Baca buku', 0], ['Dzikir pagi', 1], ['Jurnal malam', 0], ['Tidur sebelum 22.00', 0], ['Sedekah harian', 0]];
foreach ($names as $i => [$n, $m]) {
    $h = $u->habits()->create(['name' => $n, 'sort_order' => $i, 'is_mandatory' => (bool) $m]);
    for ($d = 370; $d >= 0; $d--) {
        $date = Carbon::parse($today)->subDays($d)->toDateString();
        $rate = $d < 14 ? 0.9 : 0.62 + 0.25 * sin($d / 17 + $i);
        if ($d == 0) {
            $on = in_array($i, [0, 1, 5]);
        } elseif ($d <= 23) {
            $on = true;
        } else {
            $on = mt_rand(0, 1000) / 1000 < $rate;
        }
        if ($on) {
            $h->entries()->create(['date' => $date]);
        }
    }
}
foreach ([['Kirim laporan mingguan', 1], ['Beli token listrik', 0], ['Servis motor', 0]] as [$t, $dn]) {
    $u->todos()->create(['title' => $t, 'date' => $today, 'done' => (bool) $dn]);
}

// Jadwal + sholat
$B = fn ($n, $type, $val, $dur, $extra = []) => $u->scheduleBlocks()->create($extra + ['name' => $n, 'start_type' => $type, 'start_value' => $val, 'duration_minutes' => $dur, 'active_days' => 127, 'level' => 'REMINDER']);
$B('Sholat Subuh', 1, 0, 30, ['prayer' => 'SUBUH', 'level' => 'ALARM', 'sort_order' => 1]);
$B('Daily scrum', 0, 9 * 60 + 30, 20, ['active_days' => 31, 'sort_order' => 2, 'work_action' => 'SCRUM']);
$B('Deep work: proposal Q4', 0, 10 * 60, 120, ['active_days' => 31, 'sort_order' => 3]);
$B('Sholat Dzuhur', 1, 0, 30, ['prayer' => 'DZUHUR', 'sort_order' => 4]);
$B('Rapat tim produk', 0, 14 * 60, 60, ['active_days' => 31, 'sort_order' => 5]);
$B('EOD dan rencana besok', 0, 16 * 60 + 30, 20, ['active_days' => 31, 'sort_order' => 6, 'work_action' => 'EOD']);
$u->prayerDays()->create(['date' => $today, 'timezone' => 'Asia/Jakarta', 'subuh' => '04:03', 'dzuhur' => '11:38', 'ashar' => '14:52', 'maghrib' => '17:46', 'isya' => '18:57']);

// Follow-up + rekap
$f = fn ($t, $s, $x = []) => $u->followUps()->create(['title' => $t, 'status' => $s] + $x);
$f('Balas email vendor cloud', 'DONE', ['done_at' => Carbon::parse("$today 09:10", 'Asia/Jakarta')]);
$f('Review PR modul laporan', 'DONE', ['done_at' => Carbon::parse("$today 09:50", 'Asia/Jakarta')]);
$f('Susun draf proposal Q4', 'ACTIVE', ['date' => $today]);
$f('Konfirmasi jadwal demo ke klien', 'ACTIVE', ['date' => $today, 'person' => 'Pak Dimas']);
$f('Perpanjang lisensi monitoring', 'ACTIVE', ['date' => '2026-10-14']);
$f('Menunggu akses staging', 'WAITING', ['person' => 'Tim infra']);
$f('Ide: dasbor biaya cloud', 'INBOX');
$f('Tanya HR soal cuti akhir tahun', 'INBOX');
$f('Siapkan bahan presentasi Senin', 'ACTIVE', ['picked_date' => '2026-10-17']);
$u->workDays()->create(['date' => $today, 'eod_note' => '', 'scrum_done_at' => Carbon::parse("$today 09:45", 'Asia/Jakarta')]);
$u->dailyBriefs()->create(['date' => $today, 'email_digest' => "Ada 6 email belum dibaca.\n- Vendor cloud mengirim penawaran perpanjangan, jatuh tempo 20 Oktober.\n- Klien menanyakan jadwal demo minggu depan.\n- 2 undangan rapat baru: Rabu 14.00 dan Kamis 10.00.\n- Sisanya buletin dan notifikasi, bisa diabaikan.", 'meeting_note' => 'Rapat produk: prioritas sprint depan adalah laporan bulanan.']);

// Kesehatan
$ms = fn ($w) => Carbon::parse($w, 'Asia/Jakarta')->getTimestampMs();
foreach ([-35 => 74.2, -28 => 73.6, -21 => 73.1, -14 => 72.4, -7 => 71.9, -2 => 71.2, 0 => 70.8] as $off => $kg) {
    $u->weightEntries()->create(['time_millis' => $ms(Carbon::parse($today)->addDays($off)->toDateString().' 07:00'), 'kg' => $kg]);
}
foreach ([[-5, 126, 82, 72], [-2, 122, 80, 70], [0, 124, 81, 74]] as [$off, $s, $d, $pl]) {
    $u->bloodPressureEntries()->create(['time_millis' => $ms(Carbon::parse($today)->addDays($off)->toDateString().' 07:30'), 'systolic' => $s, 'diastolic' => $d, 'pulse' => $pl]);
}
foreach ([-6 => 6400, -5 => 8200, -4 => 5100, -3 => 9300, -2 => 7600, -1 => 8800, 0 => 3900] as $off => $st) {
    $u->stepDays()->create(['date' => Carbon::parse($today)->addDays($off)->toDateString(), 'steps' => $st]);
}
$u->healthFocus()->create([
    'timezone' => 'Asia/Jakarta', 'height_cm' => 170, 'target_kg' => 68, 'steps_target' => 8000, 'rule' => 'LOW_STEPS', 'walk_minutes' => 30,
    'title' => 'Jalan santai 30 menit sore ini', 'sentence' => 'Langkah baru 3.900 dari 8.000.', 'note' => 'Dipilih karena langkah di bawah separuh target.',
    'reasons' => [['label' => 'Berat', 'value' => '2,8 kg di atas target'], ['label' => 'Tensi', 'value' => '124/81 · Normal']],
    'computed_at' => Carbon::parse("$today 10:15", 'Asia/Jakarta')->utc(),
]);

// Tim
$t = Team::create(['name' => 'Rumah Roziq', 'created_by' => $u->id]);
$t->members()->attach([$u->id, $p->id]);
foreach ([['Belanja bulanan', $p->id, 0], ['Bayar listrik dan air', $u->id, 1], ['Antar anak ke dokter gigi', $u->id, 0], ['Bersihkan kebun belakang', null, 0], ['Servis AC', $p->id, 1]] as [$ti, $as, $dn]) {
    $t->todos()->create(['title' => $ti, 'assigned_to' => $as, 'done' => (bool) $dn, 'created_by' => $u->id, 'done_by' => $dn ? $u->id : null, 'done_at' => $dn ? now() : null]);
}

$app['auth']->guard('web')->setUser($u);
$pages = ['beranda' => '/', 'habit' => '/habit', 'kerja' => '/follow-up', 'tugas-rumah' => '/tugas-rumah'];
foreach ($pages as $name => $uri) {
    $res = $kernel->handle(Illuminate\Http\Request::create($uri, 'GET'));
    echo "$name {$res->getStatusCode()}\n";
    file_put_contents("$out/$name.html", preg_replace("/<html /", "<html data-theme=\"light\" ", str_replace("http://localhost/", "$publicUrl/", $res->getContent()), 1));
}
