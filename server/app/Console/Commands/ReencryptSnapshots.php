<?php

namespace App\Console\Commands;

use App\Models\Snapshot;
use Illuminate\Console\Command;
use Illuminate\Contracts\Encryption\DecryptException;
use Illuminate\Encryption\Encrypter;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\Storage;

/**
 * Tahap 31K: memutar APP_KEY tanpa kehilangan snapshot. Snapshot dienkripsi dengan APP_KEY, jadi kunci yang diganti
 * begitu saja membuat semuanya tidak terbaca. Urutan memutar kunci ada di docs/operasional-server.md: kunci lama dipindah
 * ke APP_PREVIOUS_KEYS (Laravel membaca dengan kunci lama sebagai cadangan), kunci baru dipasang di APP_KEY, lalu perintah
 * ini menulis ulang setiap snapshot dengan kunci baru. Aman diulang: yang sudah memakai kunci baru dilewati.
 */
class ReencryptSnapshots extends Command
{
    protected $signature = 'habitflow:reencrypt-snapshots
        {--dry-run : Hanya hitung, tanpa menulis apa pun}';

    protected $description = 'Tulis ulang semua snapshot dengan APP_KEY saat ini (tahap 31K, memutar kunci)';

    public function handle(): int
    {
        $current = $this->currentKeyEncrypter();
        $dry = (bool) $this->option('dry-run');
        $disk = Storage::disk('local');
        $rewritten = $skipped = 0;
        $failed = [];

        Snapshot::query()->orderBy('id')->each(function (Snapshot $snapshot) use ($current, $dry, $disk, &$rewritten, &$skipped, &$failed) {
            if ($snapshot->path === '' || ! $disk->exists($snapshot->path)) {
                $failed[] = "#{$snapshot->id}: berkas tidak ada ({$snapshot->path})";

                return;
            }

            $cipher = $disk->get($snapshot->path);
            try {
                $current->decryptString($cipher);
                $skipped++;

                return;
            } catch (DecryptException) {
                // Bukan kunci saat ini; coba kunci lama lewat APP_PREVIOUS_KEYS.
            }

            try {
                $plain = Crypt::decryptString($cipher);
            } catch (DecryptException) {
                $failed[] = "#{$snapshot->id}: tidak terbaca dengan APP_KEY maupun APP_PREVIOUS_KEYS";

                return;
            }

            if (! $dry) {
                // Tulis ke berkas sementara dulu, supaya gagal di tengah jalan tidak merusak snapshot yang ada.
                $temp = $snapshot->path.'.tmp';
                $disk->put($temp, $current->encryptString($plain));
                $disk->move($temp, $snapshot->path);
            }
            $rewritten++;
        });

        $verb = $dry ? 'akan ditulis ulang' : 'ditulis ulang';
        $this->line("$rewritten snapshot $verb, $skipped sudah memakai kunci saat ini, ".count($failed).' gagal.');
        foreach ($failed as $line) {
            $this->error($line);
        }

        return $failed === [] ? self::SUCCESS : self::FAILURE;
    }

    /** Enkripsi yang hanya mengenal APP_KEY saat ini, tanpa kunci cadangan. */
    private function currentKeyEncrypter(): Encrypter
    {
        $key = (string) config('app.key');
        if (str_starts_with($key, 'base64:')) {
            $key = base64_decode(substr($key, 7));
        }

        return new Encrypter($key, config('app.cipher'));
    }
}
