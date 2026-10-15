#!/usr/bin/env bash
# Backup harian Habitflow (tahap 31K): dump MySQL + berkas snapshot, dikompres, dienkripsi (gpg AES256), dikirim ke
# luar VM lewat rclone, lalu rotasi. Dijalankan dari cron di VM (lihat docs/operasional-server.md).
# Kode keluar 0 hanya kalau SEMUA langkah berhasil, termasuk unggahan terverifikasi. Selain itu pemantau diberi sinyal gagal.
set -Eeuo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "$here/lib.sh"
load_env
umask 077

lock_or_exit backup
work=""
stage="persiapan"
cleanup() { [ -n "$work" ] && rm -rf "$work"; unlock; }
on_error() {
  local code=$?
  trap - ERR
  log "GAGAL pada langkah: $stage (kode $code)"
  ping_url "${HC_BACKUP_URL:-}" /fail "backup gagal pada langkah: $stage"
  alert "Habitflow: backup GAGAL" "Langkah: $stage. Cek log backup di VM."
  exit "$code"
}
trap on_error ERR
trap cleanup EXIT

ping_url "${HC_BACKUP_URL:-}" /start

stage="pemeriksaan pengaturan"
[ -n "${BACKUP_REMOTE:-}" ] || { echo "BACKUP_REMOTE kosong: backup tanpa salinan di luar VM tidak diterima." >&2; false; }
[ -s "${BACKUP_PASSPHRASE_FILE:-/nonexistent}" ] || { echo "BACKUP_PASSPHRASE_FILE tidak ada atau kosong." >&2; false; }

stamp="$(date +%Y%m%d-%H%M%S)"
name="habitflow-$stamp"
mkdir -p "$BACKUP_DIR"
work="$(mktemp -d "$BACKUP_DIR/.work-XXXXXX")"

stage="membaca kredensial database dari container app"
db_name="$(docker exec "$APP_CONTAINER" printenv DB_DATABASE)"
db_user="$(docker exec "$APP_CONTAINER" printenv DB_USERNAME)"
db_pass="$(docker exec "$APP_CONTAINER" printenv DB_PASSWORD)"
[ -n "$db_name" ] && [ -n "$db_user" ] && [ -n "$db_pass" ]

mysql_q() { docker exec -e MYSQL_PWD="$db_pass" "$MYSQL_CONTAINER" mysql -N -B -u"$db_user" "$db_name" -e "$1"; }

count_rows() { # tulis "tabel<TAB>jumlah" untuk setiap tabel
  local t c
  while IFS= read -r t; do
    [ -n "$t" ] || continue
    c="$(mysql_q "SELECT COUNT(*) FROM \`$t\`")"
    [[ "$c" =~ ^[0-9]+$ ]] || { echo "Jumlah baris tabel $t tidak terbaca: '$c'" >&2; return 1; }
    printf '%s\t%s\n' "$t" "$c"
  done < <(mysql_q "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name")
}

stage="menghitung baris sebelum dump"
count_rows > "$work/counts-before.tsv"
[ -s "$work/counts-before.tsv" ] || { echo "Tidak ada tabel di database $db_name." >&2; false; }

stage="dump MySQL"
docker exec -e MYSQL_PWD="$db_pass" "$MYSQL_CONTAINER" mysqldump \
  --single-transaction --routines --triggers --no-tablespaces --default-character-set=utf8mb4 \
  -u"$db_user" "$db_name" | gzip -9 > "$work/db.sql.gz"

stage="memeriksa dump utuh"
gzip -t "$work/db.sql.gz"
zcat "$work/db.sql.gz" | tail -n 3 | grep "Dump completed" >/dev/null || { echo "Dump terpotong: tidak ada penutup 'Dump completed'." >&2; false; }

stage="menghitung baris sesudah dump"
count_rows > "$work/counts-after.tsv"

stage="mengarsipkan berkas snapshot"
docker exec "$APP_CONTAINER" sh -c 'cd /var/www/html/storage/app/private 2>/dev/null && [ -d snapshots ] && tar -cf - snapshots || tar -cf - --files-from /dev/null' | gzip -9 > "$work/files.tar.gz"
gzip -t "$work/files.tar.gz"
files_count="$(tar -tzf "$work/files.tar.gz" | grep -c '\.enc$' || true)"

stage="menyusun manifest"
{
  echo "versi	1"
  echo "dibuat	$stamp"
  echo "database	$db_name"
  echo "berkas-snapshot	$files_count"
  while IFS=$'\t' read -r t before; do
    after="$(awk -F'\t' -v t="$t" '$1==t{print $2}' "$work/counts-after.tsv")"
    printf 'tabel\t%s\t%s\t%s\n' "$t" "$before" "${after:-$before}"
  done < "$work/counts-before.tsv"
} > "$work/manifest.tsv"
rm -f "$work/counts-before.tsv" "$work/counts-after.tsv"

stage="enkripsi"
tar -C "$work" -cf "$work/$name.tar" db.sql.gz files.tar.gz manifest.tsv
gpg --batch --yes --quiet --pinentry-mode loopback --symmetric --cipher-algo AES256 \
  --passphrase-file "$BACKUP_PASSPHRASE_FILE" -o "$work/$name.tar.gpg" "$work/$name.tar"
rm -f "$work/$name.tar" "$work/db.sql.gz" "$work/files.tar.gz"
( cd "$work" && sha256sum "$name.tar.gpg" > "$name.tar.gpg.sha256" )
size="$(wc -c < "$work/$name.tar.gpg" | tr -d ' ')"
[ "$size" -gt 200 ] || { echo "Hasil enkripsi terlalu kecil ($size byte)." >&2; false; }

stage="menyimpan salinan lokal"
mv "$work/$name.tar.gpg" "$work/$name.tar.gpg.sha256" "$BACKUP_DIR/"

stage="unggah ke $BACKUP_REMOTE"
rclone copyto "$BACKUP_DIR/$name.tar.gpg" "$BACKUP_REMOTE/$name.tar.gpg"
rclone copyto "$BACKUP_DIR/$name.tar.gpg.sha256" "$BACKUP_REMOTE/$name.tar.gpg.sha256"

stage="memverifikasi unggahan"
remote_size="$(rclone lsf --format s "$BACKUP_REMOTE/$name.tar.gpg" | tr -d ' \r\n')"
[ "$remote_size" = "$size" ] || { echo "Ukuran di tujuan ($remote_size) berbeda dari lokal ($size)." >&2; false; }

# Sampai sini backup SAH. Rotasi dijalankan sesudahnya dan hanya menghapus berkas yang namanya cocok.
date +%s > "$BACKUP_DIR/last-success"

stage="rotasi lokal"
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'habitflow-*.tar.gpg*' -mtime "+$BACKUP_LOCAL_KEEP_DAYS" -delete

stage="rotasi di tujuan"
rclone delete "$BACKUP_REMOTE" --min-age "${BACKUP_KEEP_DAYS}d" --include 'habitflow-*.tar.gpg*'

log "OK $name ($size byte, $files_count berkas snapshot)"
ping_url "${HC_BACKUP_URL:-}" "" "backup $name ok, $size byte"
