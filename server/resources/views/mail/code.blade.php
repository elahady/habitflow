Halo {{ $name }},

@if ($purpose === \App\Models\EmailCode::PURPOSE_RESET)
Kode untuk mengatur ulang kata sandi Habitflow kamu:
@elseif ($purpose === \App\Models\EmailCode::PURPOSE_DELETE)
Kode untuk MENGHAPUS akun Habitflow kamu secara permanen. Semua datamu akan hilang dan tidak bisa dikembalikan:
@else
Kode untuk memverifikasi email akun Habitflow kamu:
@endif

{{ $code }}

Kode berlaku {{ $minutes }} menit dan hanya bisa dipakai sekali. Jangan bagikan ke siapa pun.

Kalau bukan kamu yang meminta, abaikan email ini. Tidak ada yang berubah di akunmu.
@if ($purpose === \App\Models\EmailCode::PURPOSE_DELETE)
Segera ganti kata sandimu kalau kamu curiga ada orang lain yang meminta kode ini.
@endif

Habitflow
