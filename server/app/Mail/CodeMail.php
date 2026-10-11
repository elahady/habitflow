<?php

namespace App\Mail;

use App\Models\EmailCode;
use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Mail\Mailable;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;
use Illuminate\Queue\SerializesModels;

/**
 * Email kode 6 angka (verifikasi email dan lupa kata sandi, tahap 31B). Lewat antrean supaya
 * pendaftaran tidak macet kalau layanan pengirim lambat. Kode asli ikut tersimpan di tabel jobs
 * sampai email terkirim - itu satu-satunya tempat kode asli (bukan hash) berada.
 */
class CodeMail extends Mailable implements ShouldQueue
{
    use Queueable, SerializesModels;

    public int $tries = 3;

    public function __construct(
        public string $purpose,
        public string $code,
        public string $name,
        public int $minutes,
    ) {
        $this->afterCommit();
    }

    public function envelope(): Envelope
    {
        return new Envelope(subject: $this->purpose === EmailCode::PURPOSE_RESET
            ? 'Kode atur ulang kata sandi Habitflow'
            : 'Kode verifikasi email Habitflow');
    }

    public function content(): Content
    {
        return new Content(text: 'mail.code');
    }
}
