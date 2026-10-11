<?php

namespace App\Listeners;

use Illuminate\Mail\Events\MessageSent;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Mail;

/**
 * Menghitung email terkirim per hari (tahap 31B). Layanan pengirim gratis punya batas harian;
 * kalau terlewati, pendaftar baru tidak bisa verifikasi. Saat terkirim mencapai 80% batas,
 * pemilik diberi tahu satu kali per hari lewat email biasa.
 */
class CountSentMail
{
    public function handle(MessageSent $event): void
    {
        $limit = config('habitflow.mail_daily_limit');
        $owner = config('habitflow.owner_email');
        if ($limit <= 0) {
            return;
        }

        $day = now()->format('Y-m-d');
        $key = "mail-sent:$day";
        Cache::add($key, 0, now()->addDay());
        $count = Cache::increment($key);

        if ($count >= (int) ceil($limit * 0.8) && Cache::add("mail-sent-warned:$day", true, now()->addDay()) && $owner) {
            Mail::raw(
                "Email terkirim hari ini sudah $count dari batas $limit. Setelah batas terlewati, kode verifikasi pendaftar baru tidak akan terkirim sampai besok.",
                fn ($message) => $message->to($owner)->subject('Habitflow: batas email harian hampir habis'),
            );
        }
    }
}
