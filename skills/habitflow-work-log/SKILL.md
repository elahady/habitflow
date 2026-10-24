---
name: habitflow-work-log
description: Use when the user describes an activity they did/are doing today, OR a plan for tomorrow, and invokes this skill to log it to Habitflow — e.g. "hari ini saya melakukan [task] /habitflow-work-log", "catat ke habitflow: [task]", "besok saya mau [task] /habitflow-work-log". Writes it as a follow-up via the Habitflow MCP server (tools list-follow-ups/create-follow-up/update-follow-up, NOT the REST token used by [[habitflow-morning-routine]], NOT Notion): work already done becomes a `DONE` follow-up (`done_on` = today unless stated), a plan for tomorrow becomes an `ACTIVE` follow-up with `picked_date` = tomorrow (same meaning as "Lanjut besok" on the Kerja page). This is the Habitflow-MCP sibling of [[write-work-journal]] (which targets the Notion "Ruang Kerja : I am a Leader" page instead) — use this one when the user means Habitflow, use write-work-journal when they mean the Notion jurnal kerja. Needs the `habitflow` MCP connector added and logged in (`claude mcp add --transport http habitflow https://habitflow.roziqrizal.com/mcp` lalu `/mcp`); stops with an explicit note instead of guessing if the tools aren't reachable. First real end-to-end run not yet confirmed as of 2026-10-24 (tahap 35 langkah 2) — report back anything that doesn't match this doc so it can be corrected.
---

# Habitflow Work Log (Catat Aktivitas & Rencana Besok ke Habitflow)

## Tujuan

Cara cepat mencatat apa yang sedang/sudah dikerjakan user hari ini, atau rencana besok, ke
Habitflow — tanpa user harus buka web/app manual. User cukup ketik aktivitasnya lalu panggil
skill ini di pesan yang sama, mis.:

> hari ini saya melakukan perbaikan bug login /habitflow-work-log

Ditulis sebagai **follow-up** (bukan jenis item baru — wadah yang sudah ada di Habitflow, tabel
`follow_ups`), lewat **tiga tool MCP** di server `Habitflow` (lihat "Prasyarat"): `list-follow-ups`,
`create-follow-up`, `update-follow-up`. Rancangan lengkap: `habitflow/docs/concept.md`, bagian
"Tahap 35".

**Pemetaan ke follow-up** (sama dengan yang sudah diuji di `McpFollowUpToolsTest`):

| Yang dikatakan user | Status | Tanggal |
|---|---|---|
| Sudah/sedang dikerjakan hari ini (atau tanggal lampau yang disebut) | `DONE` | `done_on` = tanggal itu (bawaan hari ini) |
| Rencana besok / "lanjut besok" | `ACTIVE` | `picked_date` = besok |
| Rencana/mau coba hari ini, belum dikerjakan | `ACTIVE` | tidak ada (follow-up aktif tanpa tanggal, tetap tampil di halaman Kerja) |

**Bukan** untuk ringkasan email ("Ringkasan dari Claude") — itu [[habitflow-morning-routine]], kartu
yang berbeda sama sekali dan masih memakai token REST, bukan MCP. **Bukan** untuk jurnal kerja
Notion — itu [[write-work-journal]], halaman yang berbeda sama sekali.

## Prasyarat

| Kebutuhan | Cara cek |
|---|---|
| Connector `habitflow` terpasang & login OAuth | Tool `list-follow-ups`/`create-follow-up`/`update-follow-up` (nama lengkap biasanya `mcp__habitflow__list-follow-ups` dst.) harus bisa dipanggil. Kalau belum pernah dipakai di sesi ini, coba `ToolSearch` dengan query `select:mcp__habitflow__list-follow-ups,mcp__habitflow__create-follow-up,mcp__habitflow__update-follow-up` dulu — tool MCP sering deferred. |

### Memasang connector (sekali saja, biasanya oleh user di sesi interaktif)

```
claude mcp add --transport http habitflow https://habitflow.roziqrizal.com/mcp
/mcp
```

`/mcp` membuka alur login OAuth (akun Google yang sama dengan Habitflow). Skill ini **tidak bisa
melakukan OAuth sendiri** — kalau tool di atas tidak tersedia atau memanggilnya gagal dengan error
butuh otorisasi, **berhenti** dan jelaskan ke user bahwa connector `habitflow` perlu dipasang/login
dulu (lihat perintah di atas), jangan mencoba token REST sebagai pengganti.

## Alur kerja

### A. Ambil aktivitas dari pesan user

1. Aktivitas yang dicatat adalah bagian pesan user **selain** pemanggilan skill itu sendiri — mis.
   dari `"hari ini saya melakukan perbaikan bug login /habitflow-work-log"`, judulnya `"Perbaikan
   bug login"`. Buang basa-basi ("hari ini saya", "sudah", "lagi"), ambil inti pekerjaannya saja
   dalam kalimat singkat (ini jadi `title`, maksimal 255 karakter di server).
2. Kalau user menyebut **beberapa aktivitas sekaligus** (dipisah koma, "dan", atau baris terpisah),
   pecah jadi beberapa follow-up terpisah — jangan digabung jadi satu judul panjang.
3. Kalau pesan user kosong/tidak jelas aktivitasnya (cuma ketik skill-nya saja tanpa konteks),
   tanya balik aktivitas apa yang mau dicatat — jangan mengarang dari context lama.
4. Kalau user menyebut detail tambahan yang bukan bagian judul (mis. nama orang terkait, catatan
   panjang), itu masuk ke `person`/`note`, bukan ditempel ke `title`.

### B. Tentukan status & tanggal tujuan (per aktivitas)

5. **Besok eksplisit** (mis. "besok saya mau...", "rencana besok...", "lanjut besok...", "for
   tomorrow"): tujuannya `ACTIVE` + `picked_date` = besok. Jangan kirim `status` di `create-follow-up`
   untuk kasus ini kalau tidak perlu — bawaan tool sudah `ACTIVE`.
6. **Sudah/sedang dikerjakan hari ini** (default kalau tidak ada penanda lain — "saya melakukan",
   "sudah", "baru selesai"): tujuannya `DONE`, `done_on` = hari ini kecuali user menyebut tanggal
   lain secara eksplisit.
7. **Rencana/mau coba hari ini, jelas belum dikerjakan** (mis. "nanti saya akan...", "rencana hari
   ini...", "mau coba..." — tanpa penanda besok): tujuannya `ACTIVE` tanpa `picked_date` tanpa
   `done_on` (follow-up aktif biasa, bukan "Rencana besok").
8. Ambigu antara langkah 6 dan 7: default ke langkah 6 (`DONE` hari ini) — pola pemakaian utama
   skill ini adalah mencatat yang **sudah** dikerjakan, sama seperti [[write-work-journal]].
9. Tentukan tanggal "hari ini"/"besok" dari jam sekarang yang akurat, jangan dari ingatan/context
   lama:
   - Linux, macOS, routine awan: `TZ=Asia/Jakarta date '+%F'`.
   - **Windows, Git Bash: jangan pakai `TZ=... date`** (tidak dihormati, hasilnya UTC — tanggal bisa
     salah antara 00.00–07.00 WIB). Pakai PowerShell:
     `[TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow,'SE Asia Standard Time').ToString('yyyy-MM-dd')`.
   - Besok = hari ini + 1 hari kalender (bukan ditambah 24 jam dekat tengah malam).

### C. Cek follow-up yang sudah ada sebelum menulis

10. Panggil `list-follow-ups` **sekali** (tanpa filter — bawaannya sudah "semua yang belum
    selesai": `INBOX`/`ACTIVE`/`WAITING`) di awal kalau ada satu atau lebih aktivitas yang mau
    dicatat. Satu panggilan ini dipakai untuk mencocokkan semua aktivitas dalam pesan, tidak perlu
    dipanggil ulang per item.
11. Untuk tiap aktivitas, cari di hasil `list-follow-ups` follow-up dengan `title` yang sama
    **tanpa membedakan huruf besar-kecil** (sama seperti pencocokan di server). Ini menangkap kasus
    aktivitas yang sebelumnya sudah tercatat `ACTIVE`/`INBOX`/`WAITING` (mis. "Rencana besok"
    kemarin yang sekarang mau ditandai selesai) — **jangan** dilewatkan ke `create-follow-up` saja,
    karena tool itu baru mendeteksi dobel kalau status tujuannya sama persis dengan yang sudah ada
    (lihat catatan di langkah 13), bukan "item ACTIVE lama + sekarang mau ditandai DONE".

### D. Tulis atau ubah follow-up-nya

12. **Ketemu yang cocok (langkah 11):** panggil `update-follow-up` dengan `id` follow-up itu,
    **hanya** field yang berubah sesuai tujuan di langkah B:
    - Tujuan `DONE`: `{ id, status: "DONE", done_on: "<tanggal>" }`.
    - Tujuan "Rencana besok": `{ id, picked_date: "<besok>" }` — **jangan** ikut mengubah `status`
      kecuali memang perlu, "Rencana besok" ditentukan dari `picked_date`-nya, bukan status.
    - Tujuan "aktif hari ini tanpa tanggal": kalau item yang ketemu sudah `ACTIVE`/`INBOX` tanpa
      `picked_date`, tidak perlu memanggil apa-apa — sudah sesuai, cukup konfirmasi ke user bahwa
      itu sudah tercatat.
13. **Tidak ketemu:** panggil `create-follow-up` langsung dengan `title` dan field sesuai tujuan
    (`status`, `done_on`, atau `picked_date`). Tool ini sendiri sudah idempotent kalau skill ini
    dijalankan ulang untuk aktivitas yang sama hari yang sama (dicocokkan server: judul sama +
    status `DONE` di `done_on` yang sama, atau judul sama + status bukan `DONE`) — baca `created`
    di hasilnya: `false` berarti sudah ada sebelumnya (dikembalikan data yang ada, tidak dibuat
    baru), `true` berarti baru dibuat.
14. Baca hasilnya:
    - `isError` / respons galat: follow-up dengan `id` itu bukan milik user (tidak seharusnya
      terjadi dari hasil `list-follow-ups` sendiri, tapi tetap tangani) atau validasi gagal (judul
      kosong, format tanggal salah bukan `YYYY-MM-DD`, status bukan salah satu `INBOX`/`ACTIVE`/
      `WAITING`/`DONE`). Tampilkan pesan galatnya ke user, **jangan** menebak-nebak perbaikan atau
      mencoba ulang dengan data yang diubah sendiri.
    - Sukses: catat `id`, `status`, `done_at`/`picked_date` dari respons untuk laporan akhir.
15. **Tidak ada tool hapus** (sengaja, lihat `UpdateFollowUp.php`) — kalau user minta membatalkan
    pencatatan yang salah, **jangan** mencoba menyiasatinya lewat `update-follow-up` untuk
    mengosongkan semua field. Jelaskan ke user bahwa penghapusan belum didukung lewat MCP, dan
    arahkan ke halaman web `https://habitflow.roziqrizal.com/follow-up` untuk menghapus manual
    kalau memang perlu (konfirmasi dulu, ini aksi di luar MCP).

### E. Laporan akhir ke user

16. Konfirmasi singkat per aktivitas: judul, dibuat baru atau diubah dari yang sudah ada (sebut
    `id`-nya), status dan tanggal akhirnya. Tidak perlu menampilkan ulang seluruh daftar follow-up.
    Sertakan link `https://habitflow.roziqrizal.com/follow-up` kalau user ingin melihat/mengubah
    manual.

## Kalau connector `habitflow` tidak tersambung atau tool-nya gagal

Jangan diam-diam gagal dan jangan mencoba jalur lain (token REST, Notion). Tampilkan ke user
aktivitas yang seharusnya dicatat, sebutkan eksplisit bahwa penulisan ke Habitflow di-skip karena
connector MCP belum terpasang/login, dan tunjukkan perintah pemasangannya (lihat "Prasyarat").

## Memasang skill

Berkas ini disinkronkan dari repo `masuya-context` (`.claude/skills/habitflow-work-log/SKILL.md`)
ke `~/.claude/skills/`, sama seperti `habitflow-morning-routine`. Salinan sumber lain ada di repo
Habitflow (`habitflow/skills/habitflow-work-log/SKILL.md`) — kalau mengubah salah satu, selaraskan
juga yang lain.
