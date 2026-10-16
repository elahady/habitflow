---
name: habitflow-morning-routine
description: Use every morning, or when the user says "habitflow morning routine", "rekap pagi habitflow", "kirim ringkasan email ke habitflow", to summarize the user's unread Gmail and write the digest to their Habitflow account (POST /api/v1/daily-briefs), where it shows up as the read-only "Ringkasan dari Claude" card in the Kerja tab (Android) and Kerja page (web). Unlike the older Notion morning-routine, it does NOT create journal pages and does NOT migrate tasks (Habitflow follow-ups keep their status until marked Selesai, so nothing needs copying) and it never touches Notion. Safe to run repeatedly on the same day (upsert per date). Needs Gmail connected and a Habitflow session token; if either is missing it stops with an explicit note instead of writing anything. Designed to be paired with /schedule for unattended runs.
---

# Habitflow Morning Routine (Ringkasan Email → Habitflow)

## Tujuan

Satu hal saja: cek email yang belum dibaca, ringkas jadi teks pendek, lalu simpan ke akun Habitflow user untuk **hari ini** supaya tampil di kartu "Ringkasan dari Claude".

Sengaja **tidak** meniru `morning-routine` yang lama (Notion):

- Tidak membuat halaman jurnal harian.
- Tidak memigrasi task. Follow-up Habitflow punya status (Inbox/Aktif/Menunggu/Selesai) yang menempel di item yang sama sampai ditandai Selesai, jadi otomatis terbawa ke hari berikutnya. **Jangan menyalin atau membuat ulang follow-up.**
- Tidak memakai Notion sama sekali.
- Tidak menulis `meeting_note` (isi bagian "Meeting & briefing" belum diputuskan). Kolom itu tidak dikirim, jadi apa pun yang sudah ada di sana tidak tersentuh.

Dokumen rancangan: `docs/concept.md`, bagian "Tahap 32".

## Prasyarat

| Kebutuhan | Cara |
|---|---|
| Alamat server | Variabel lingkungan `HABITFLOW_URL`. Kalau kosong, pakai `https://habitflow.roziqrizal.com` |
| Token sesi | Variabel lingkungan `HABITFLOW_TOKEN`. Kalau kosong, baca berkas `~/.habitflow/claude-token` (satu baris, di luar repo). Kalau dua-duanya tidak ada, **berhenti** (lihat "Kalau token tidak ada") |
| Gmail | Gmail MCP terhubung |

### Membuat token (sekali saja, oleh user, bukan oleh skill)

Pakai **token terbatas** (cakupan `daily-briefs`): hanya boleh `GET /api/v1/ping` dan `POST /api/v1/daily-briefs`, jadi kalau bocor ia tidak bisa membaca atau mengubah data lain. Token ini diterbitkan di server oleh pemilik, tanpa kata sandi, jadi cocok untuk akun yang daftar lewat Google. Skill ini **tidak pernah meminta, menerima, atau menyimpan kata sandi** user.

Di VM (host Hyper-V; detail akses di `server/DEPLOY.md`), alihkan keluarannya langsung ke berkas supaya token tidak pernah tampil di layar:

```bash
ssh elahady@<ip-vm> "docker exec habitflow php artisan habitflow:issue-token <email> --replace" > ~/.habitflow/claude-token
```

Hanya token yang masuk stdout (pesan lain ke stderr). `--replace` mencabut token lama bernama `claude-morning-routine` dan membuat yang baru; tanpa `--replace`, perintah menolak kalau token itu sudah ada. Simpan isi berkas ke rahasia/variabel lingkungan routine `/schedule` kalau dijadwalkan di awan.

**Jangan** menampilkan token di chat, laporan, log, atau berkas di repo. Token terbatas yang dipakai untuk rute lain dibalas `403` (bukan `401`): itu berarti skill salah dipakai, bukan token mati. Token berhenti berlaku (`401`) kalau user menekan "Keluar dari semua perangkat" atau menjalankan `habitflow:revoke-all-sessions`; terbitkan ulang dengan perintah di atas. Token biasa dari `POST /api/v1/auth/login` (akses penuh) masih diterima skill, tapi **jangan** dipakai kalau ada pilihan token terbatas.

## Alur kerja

### A. Tentukan tanggal hari ini

1. Ambil tanggal sekarang yang akurat di zona **Asia/Jakarta** (`TZ=Asia/Jakarta date +%F`), jangan pakai tanggal dari ingatan atau context lama. Hasilnya `YYYY-MM-DD`, dipakai sebagai `date`.

### B. Cek sambungan ke Habitflow

2. Baca token (lihat Prasyarat) tanpa mencetaknya. Lalu:
   ```bash
   curl -s -o /dev/null -w "%{http_code}" "$HABITFLOW_URL/api/v1/ping" -H "Authorization: Bearer $HABITFLOW_TOKEN"
   ```
   - `200`: lanjut.
   - `401`: token dicabut atau salah. **Berhenti**, minta user membuat token baru (lihat di atas). Jangan mencoba login sendiri.
   - Lainnya atau gagal tersambung: **berhenti** dan laporkan kode atau pesan galatnya. Jangan menulis apa pun.

### C. Ambil email belum dibaca

3. Pastikan Gmail MCP terhubung. Kalau tidak, **berhenti dan jangan menulis apa pun ke Habitflow**: menulis "tidak ada email" saat sebenarnya Gmail tidak terbaca akan menimpa ringkasan yang benar dari proses sebelumnya hari ini.
4. `search_threads` dengan query `is:unread in:inbox`. Ambil pengirim, subjek, dan cuplikan singkat tiap thread. Cukup metadata; jangan membuka isi penuh kecuali user meminta.
5. **Jangan** memanggil aksi yang mengubah state (`label_message`, `mark_message_spam`, `trash_*`, `reply`, `send_message`, dan sejenisnya). Skill ini hanya membaca.

### D. Susun ringkasan

6. Teks polos (kartu di app hanya menampilkan teks, tanpa markdown). Format:
   ```
   <N> email belum dibaca (dicek <jam.menit> WIB)
   - <pengirim> — <subjek> — <cuplikan 1 kalimat pendek>
   - ...
   ```
   Contoh baris: `- Bu Rina (Finance) — Revisi invoice Oktober — minta angka PPN diperbaiki sebelum Jumat`.
7. Aturan isi:
   - Urutkan dari yang paling mendesak: yang meminta jawaban, tenggat, atau dari atasan dan klien dulu, buletin dan notifikasi otomatis paling bawah. Boleh menggabung notifikasi sejenis jadi satu baris (`- 6 notifikasi GitHub — ...`).
   - Maksimal 30 baris. Kalau lebih, tulis `... dan <M> email lainnya` di akhir. Total teks jauh di bawah batas server (20.000 karakter).
   - **Jangan menyalin** kode verifikasi (OTP), kata sandi, tautan reset atau masuk, token, atau nomor kartu dan rekening, walau muncul di cuplikan. Tulis seperlunya, misalnya `kode verifikasi dari <layanan>`. Ringkasan ini disimpan di server dan dibaca di web.
   - Tidak ada klaim yang tidak ada di email. Kalau cuplikan tidak cukup untuk meringkas, tulis subjeknya saja.
8. Kalau tidak ada email belum dibaca, tetap tulis ringkasan: `0 email belum dibaca (dicek <jam.menit> WIB)` dan `Tidak ada email belum dibaca.`, supaya kartu hari ini jelas "sudah dicek, kosong", bukan "belum dicek".

### E. Kirim ke Habitflow

9. Tulis payload JSON ke berkas sementara (folder scratchpad, bukan repo), lalu kirim dari berkas itu. Jangan menempel teks email langsung ke baris perintah: tanda kutip dan baris baru di dalamnya merusak JSON dan bisa tereksekusi shell.
   ```json
   {"date": "2026-10-15", "email_digest": "3 email belum dibaca (dicek 07.05 WIB)\n- ..."}
   ```
   Hanya dua kunci itu. **Jangan menyertakan `meeting_note`**, supaya tidak menimpanya.
   ```bash
   curl -s -w "\n%{http_code}" -X POST "$HABITFLOW_URL/api/v1/daily-briefs" \
     -H "Authorization: Bearer $HABITFLOW_TOKEN" -H "Content-Type: application/json" \
     --data @<berkas payload>
   ```
10. Hapus berkas payload setelah selesai (isinya ringkasan email pribadi).
11. Baca balasan:
    - `201`: baris baru dibuat untuk hari ini.
    - `200`: ringkasan hari ini ditimpa (skill dijalankan ulang). Normal, tidak ada baris dobel.
    - `401`: token dicabut di tengah jalan; sama seperti langkah 2.
    - `403`: token terbatas dengan cakupan yang tidak mengizinkan permintaan ini. Jangan dicoba ulang; laporkan.
    - `422`: validasi gagal. Tampilkan pesan dari server. Kalau sebabnya kelebihan panjang, perpendek ke 15 baris pertama, kirim ulang sekali, dan beri tahu user. Selain itu jangan mengubah isi diam-diam.
    - `429`: terlalu banyak permintaan. Tunggu sesuai header `Retry-After` lalu coba satu kali lagi.
    - `5xx` atau gagal tersambung: jangan mengulang terus-menerus. Laporkan, dan tampilkan ringkasan di chat sebagai cadangan.

### F. Laporan ke user

12. Tampilkan ringkas: jumlah email belum dibaca, 3 sampai 5 baris terpenting, dan hasil kirim (dibuat atau ditimpa, beserta tanggal). Kalau ada langkah yang dilewati atau gagal, sebutkan eksplisit di sini. **Jangan** menampilkan token, dan jangan mengutip kode atau tautan sensitif dari email.

## Kalau token tidak ada

Berhenti sebelum menyentuh Gmail. Jelaskan singkat: butuh token sesi Habitflow, cara membuatnya (bagian "Membuat token"), dan tempat menyimpannya. Jangan meminta user menempelkan kata sandi atau token ke chat; kalau user sudah terlanjur menempelkannya, sarankan menekan "Keluar dari semua perangkat" di Profil dan membuat token baru.

## Kalau Gmail tidak terhubung

Jangan menulis apa pun ke Habitflow (lihat langkah 3). Laporkan bahwa email tidak bisa dibaca dan ringkasan hari ini belum dikirim.

## Menjadwalkan otomatis tiap pagi

Skill ini baru jalan kalau dipanggil. Untuk jalan tiap pagi tanpa diminta, pasangkan dengan **`/schedule`** (routine berbasis cron) yang memanggil skill ini, misalnya hari kerja pukul 07.00 WIB. Routine awan butuh `HABITFLOW_TOKEN` sebagai variabel lingkungan atau rahasia di konfigurasi routine (bukan di prompt), dan Gmail MCP terhubung di akun yang menjalankannya. Skill ini tidak membuat jadwal sendiri.

## Memasang skill

Berkas ini ada di repo Habitflow (`skills/habitflow-morning-routine/SKILL.md`). Supaya dikenali Claude Code dari folder mana pun, salin folder `habitflow-morning-routine` ke `~/.claude/skills/`. Setelah mengubah berkas di repo, salin ulang.
