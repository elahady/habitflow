#!/usr/bin/env bash
# Uji skrip operasional (tahap 31K) tanpa Docker, MySQL, atau rclone sungguhan: ketiganya diganti tiruan di uji/.
# Seperti scripts/uji-cek-rilis.ps1, tiap pemeriksaan DILANGGAR dengan sengaja dan skrip harus gagal dengan cara yang
# benar. Skrip yang tidak pernah terbukti gagal tidak bisa dipercaya lulus. Jalankan: bash server/ops/uji-ops.sh
set -u
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(mktemp -d)"
trap 'rm -rf "$root"' EXIT
pass=0 fail=0

ok() { # <deskripsi> <perintah...>
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then pass=$((pass + 1)); echo "  lulus  $desc"; else fail=$((fail + 1)); echo "  GAGAL  $desc"; fi
}
no() { # kebalikan ok: perintah harus gagal
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then fail=$((fail + 1)); echo "  GAGAL  $desc"; else pass=$((pass + 1)); echo "  lulus  $desc"; fi
}

# Lingkungan baru untuk tiap kasus.
fresh() {
  rm -rf "$root/case"; mkdir -p "$root/case"/{state,remote,home,storage/snapshots/u1}
  export FAKE_STATE="$root/case/state" FAKE_REMOTE="$root/case/remote" FAKE_STORAGE="$root/case/storage"
  export FAKE_TABLES="$root/case/tables"
  printf 'users 3\nhabits 12\nsnapshots 2\n' > "$FAKE_TABLES"
  echo isi-a > "$FAKE_STORAGE/snapshots/u1/a.enc"; echo isi-b > "$FAKE_STORAGE/snapshots/u1/b.enc"
  : > "$FAKE_STATE/curl.log"
  unset FAKE_DUMP_FAIL FAKE_DUMP_TRUNCATE FAKE_RCLONE_FAIL FAKE_LSF_WRONG FAKE_RESTORE_DROP FAKE_RESTORE_ALTER FAKE_DOWN FAKE_DISK FAKE_STUCK FAKE_FAILED FAKE_CURL_FAIL
  export HOME="$root/case/home" GNUPGHOME="$root/case/home/gnupg"; mkdir -p "$GNUPGHOME"; chmod 700 "$GNUPGHOME"
  echo "kata-sandi-uji-backup" > "$HOME/pass"
  cat > "$root/case/ops.env" <<ENV
BACKUP_DIR="$root/case/backups"
BACKUP_PASSPHRASE_FILE="$HOME/pass"
BACKUP_REMOTE="fake:cadangan"
HC_BACKUP_URL="https://hc.contoh/backup"
HC_DRILL_URL="https://hc.contoh/drill"
HC_MONITOR_URL="https://hc.contoh/monitor"
ALERT_URL="https://ntfy.contoh/topik"
PUBLIC_URL="https://situs.contoh/up"
LOCAL_URL="http://127.0.0.1:8081/up"
ENV
  export HABITFLOW_OPS_ENV="$root/case/ops.env"
  case ":$PATH:" in *":$here/uji:"*) ;; *) export PATH="$here/uji:$PATH" ;; esac
  B="$root/case/backups"; R="$root/case/remote/cadangan"
}
backup() { bash "$here/backup.sh" >"$root/case/out.log" 2>&1; }
drill() { bash "$here/restore-drill.sh" "$@" >"$root/case/out.log" 2>&1; }
monitor() { bash "$here/monitor.sh" >"$root/case/out.log" 2>&1; }
has() { grep -q -- "$1" "$FAKE_STATE/curl.log"; }
ada_berkas() { [ -n "$(find "$1" -type f 2>/dev/null)" ]; }
sisa_sementara() { [ -n "$(find "$B" -maxdepth 1 \( -name '.work-*' -o -name '.drill-*' -o -name '*.tar' -o -name '*.sql*' \) 2>/dev/null)" ]; }
backup_ok_terkirim() { has 'hc.contoh/backup/start' && grep -q 'hc.contoh/backup$' "$FAKE_STATE/curl.log"; }
drill_ok_terkirim() { test -s "$B/last-drill" && grep -q 'hc.contoh/drill$' "$FAKE_STATE/curl.log"; }
gagal_terkirim() { has "$1/fail" && has 'ntfy.contoh'; }
dua_ada() { test -e "$1" && test -e "$2"; }
semua_ada_di_tujuan() { ls "$R"/habitflow-*.tar.gpg >/dev/null && ls "$R"/habitflow-*.tar.gpg.sha256 >/dev/null; }
gpg_buka() { gpg --batch --yes --quiet --pinentry-mode loopback --passphrase "$1" -o "$root/case/dekripsi" --decrypt "$(ls "$R"/habitflow-*.tar.gpg)"; }
terenkripsi() { gpg_buka kata-sandi-uji-backup && ! gpg_buka salah; }
dump_konsisten() { grep mysqldump "$FAKE_STATE/docker.log" | grep -q -- --single-transaction; }

echo "== backup.sh =="
fresh
ok "backup berhasil" backup
ok "salinan terenkripsi dan checksum ada di tujuan luar VM" semua_ada_di_tujuan
ok "backup tidak bisa dibuka dengan kata sandi lain (terenkripsi)" terenkripsi
ok "dump memakai --single-transaction (konsisten tanpa mengunci tabel)" dump_konsisten
no "tidak ada sisa berkas sementara atau dump polos" sisa_sementara
ok "tanda sukses terakhir ditulis" test -s "$B/last-success"
ok "ping mulai dan ping sukses terkirim" backup_ok_terkirim
no "tidak ada ping gagal" has "backup/fail"

fresh; export FAKE_DUMP_FAIL=1
no "dump gagal membuat backup gagal" backup
no "dump gagal: tidak ada yang diunggah" ada_berkas "$R"
ok "dump gagal: ping gagal dan notifikasi ponsel terkirim" gagal_terkirim hc.contoh/backup
no "dump gagal: tanda sukses tidak ditulis" test -e "$B/last-success"
no "dump gagal: tidak ada sisa berkas sementara" sisa_sementara

fresh; export FAKE_DUMP_TRUNCATE=1
no "dump terpotong (tanpa 'Dump completed') ditolak" backup
no "dump terpotong: tidak diunggah" ada_berkas "$R"

fresh
mkdir -p "$R"; touch -d "40 days ago" "$R/habitflow-lama.tar.gpg"; export FAKE_RCLONE_FAIL=1
no "unggahan gagal membuat backup gagal" backup
ok "unggahan gagal: rotasi tidak jalan, backup lama tetap ada" test -e "$R/habitflow-lama.tar.gpg"
no "unggahan gagal: tanda sukses tidak ditulis" test -e "$B/last-success"

fresh; export FAKE_LSF_WRONG=1
no "ukuran di tujuan beda dari lokal ditolak" backup
no "ukuran beda: tanda sukses tidak ditulis" test -e "$B/last-success"

fresh; sed -i 's/^BACKUP_REMOTE=.*/BACKUP_REMOTE=""/' "$root/case/ops.env"
no "tanpa BACKUP_REMOTE ditolak (tidak ada backup hanya di VM)" backup
no "tanpa BACKUP_REMOTE: dump tidak pernah dijalankan" grep -q mysqldump "$FAKE_STATE/docker.log"

fresh; rm "$HOME/pass"
no "tanpa berkas kata sandi enkripsi ditolak" backup

fresh
mkdir -p "$R" "$B"
touch -d "40 days ago" "$R/habitflow-tua.tar.gpg" "$R/habitflow-tua.tar.gpg.sha256"; touch -d "5 days ago" "$R/habitflow-muda.tar.gpg"
touch -d "40 days ago" "$R/catatan-pemilik.txt"
touch -d "10 days ago" "$B/habitflow-lokal-tua.tar.gpg"; touch -d "2 days ago" "$B/habitflow-lokal-muda.tar.gpg"; touch -d "10 days ago" "$B/catatan.txt"
ok "backup berhasil bersama rotasi" backup
no "rotasi: backup di tujuan lebih dari 30 hari dihapus" test -e "$R/habitflow-tua.tar.gpg"
no "rotasi: checksum pasangannya ikut dihapus" test -e "$R/habitflow-tua.tar.gpg.sha256"
ok "rotasi: backup 5 hari dipertahankan" test -e "$R/habitflow-muda.tar.gpg"
ok "rotasi: berkas lain di tujuan tidak disentuh" test -e "$R/catatan-pemilik.txt"
no "rotasi lokal: backup lebih dari 7 hari dihapus" test -e "$B/habitflow-lokal-tua.tar.gpg"
ok "rotasi lokal: backup 2 hari dan berkas lain dipertahankan" dua_ada "$B/habitflow-lokal-muda.tar.gpg" "$B/catatan.txt"

echo "== restore-drill.sh =="
fresh; backup
ok "latihan pemulihan dari tujuan luar VM berhasil" drill
ok "latihan: tanda dan ping sukses" drill_ok_terkirim
ok "latihan: container sementara dibersihkan" grep -q "docker rm -f habitflow-restore-drill" "$FAKE_STATE/docker.log"
no "latihan tidak menjalankan restore di shared-mysql" grep -qE "docker exec .*shared-mysql mysql .*restore_test" "$FAKE_STATE/docker.log"
ok "latihan --local berhasil" drill --local

fresh; backup; echo "salah" > "$HOME/pass"
no "kata sandi salah: latihan gagal" drill
ok "kata sandi salah: ping gagal dan notifikasi terkirim" gagal_terkirim hc.contoh/drill
no "kata sandi salah: tanda latihan tidak ditulis" test -e "$B/last-drill"

fresh; backup; f="$(ls "$R"/habitflow-*.tar.gpg)"; printf 'X' | dd of="$f" bs=1 seek=100 conv=notrunc 2>/dev/null
no "berkas rusak (checksum tidak cocok): latihan gagal" drill
ok "berkas rusak: berhenti di pemeriksaan checksum" grep -q "Checksum" "$root/case/out.log"
no "berkas rusak: database sementara tidak sempat dijalankan" grep -q "docker run" "$FAKE_STATE/docker.log"

fresh; backup; export FAKE_RESTORE_ALTER="users:999"
no "jumlah baris hasil pulihan beda dari manifest: latihan gagal" drill
ok "laporan menyebut tabel yang beda" grep -q "BEDA  users" "$root/case/out.log"

fresh; backup; export FAKE_RESTORE_DROP="habits"
no "tabel hilang dari hasil pulihan: latihan gagal" drill
ok "laporan menyebut tabel yang hilang" grep -q "BEDA  habits" "$root/case/out.log"

fresh
no "tanpa satu pun backup: latihan gagal" drill

fresh; backup; rm "$R"/habitflow-*.sha256
no "tanpa berkas checksum: latihan gagal" drill

echo "== monitor.sh =="
fresh; backup; drill
ok "semua sehat: monitor lulus" monitor
no "semua sehat: tidak ada notifikasi ponsel" has "ntfy.contoh"
ok "semua sehat: ping hidup ke layanan pemantau luar" has "hc.contoh/monitor"

fresh; backup; drill; export FAKE_DOWN="habitflow-queue"
ok "container mati: monitor tetap selesai" monitor
ok "container mati: notifikasi ponsel terkirim" has "ntfy.contoh"
: > "$FAKE_STATE/curl.log"; monitor
no "masalah yang sama tidak diperingatkan ulang tiap putaran" has "ntfy.contoh"
unset FAKE_DOWN; monitor
ok "pulih: notifikasi 'pulih' terkirim" grep -q "pulih" "$FAKE_STATE/curl.log"

fresh; backup; drill; export FAKE_DISK=91
monitor; ok "disk 91%: peringatan disk" has "ntfy.contoh"

fresh; backup; drill; rm -f "$B/last-success"; : > "$FAKE_STATE/curl.log"
monitor; ok "backup tidak pernah sukses: peringatan backup" has "ntfy.contoh"
fresh; backup; drill; echo "$(( $(date +%s) - 30*3600 ))" > "$B/last-success"; : > "$FAKE_STATE/curl.log"
monitor; ok "backup sukses terakhir 30 jam lalu: peringatan" has "ntfy.contoh"
fresh; backup; drill; echo "$(( $(date +%s) - 50*86400 ))" > "$B/last-drill"; : > "$FAKE_STATE/curl.log"
monitor; ok "latihan pemulihan 50 hari lalu: peringatan" has "ntfy.contoh"
fresh; backup; drill; : > "$FAKE_STATE/curl.log"; export FAKE_STUCK=3
monitor; ok "job email macet: peringatan antrean" has "ntfy.contoh"
fresh; backup; drill; : > "$FAKE_STATE/curl.log"; export FAKE_FAILED=2
monitor; ok "email gagal kirim: peringatan" has "ntfy.contoh"
fresh; backup; drill; : > "$FAKE_STATE/curl.log"; export FAKE_CURL_FAIL="situs.contoh"
monitor; ok "situs tidak terjangkau dari internet: peringatan" has "ntfy.contoh"
fresh; backup; drill; : > "$FAKE_STATE/curl.log"; export FAKE_CURL_FAIL="127.0.0.1"
monitor; ok "situs tidak menjawab di VM: peringatan" has "ntfy.contoh"

echo
echo "Hasil: $pass lulus, $fail gagal."
[ "$fail" -eq 0 ]
