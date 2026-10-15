#!/usr/bin/env bash
# Latihan pemulihan Habitflow (tahap 31K): ambil backup terbaru (dari tujuan di luar VM, atau --file), verifikasi checksum,
# dekripsi, pulihkan ke MySQL SEMENTARA (container baru, bukan shared-mysql), lalu bandingkan jumlah baris dengan manifest.
# Backup yang belum pernah dipulihkan dianggap belum ada. Jalankan sekali sebelum 31M, lalu sebulan sekali.
#   restore-drill.sh                 backup terbaru di BACKUP_REMOTE
#   restore-drill.sh --local         backup terbaru di BACKUP_DIR (VM)
#   restore-drill.sh --file <path>   berkas .tar.gpg tertentu (checksum dicari di sebelahnya)
set -Eeuo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "$here/lib.sh"
load_env
umask 077

mode=remote file=""
while [ $# -gt 0 ]; do
  case "$1" in
    --local) mode=local ;;
    --file) mode=file; file="${2:?--file butuh path}"; shift ;;
    *) echo "Argumen tidak dikenal: $1" >&2; exit 2 ;;
  esac
  shift
done

lock_or_exit drill
work=""
drill_container="habitflow-restore-drill"
stage="persiapan"
cleanup() {
  docker rm -f "$drill_container" >/dev/null 2>&1 || true
  [ -n "$work" ] && rm -rf "$work"
  unlock
}
on_error() {
  local code=$?
  trap - ERR
  log "LATIHAN GAGAL pada langkah: $stage (kode $code)"
  ping_url "${HC_DRILL_URL:-}" /fail "latihan pemulihan gagal pada langkah: $stage"
  alert "Habitflow: latihan pemulihan GAGAL" "Langkah: $stage. Backup belum terbukti bisa dipulihkan."
  exit "$code"
}
trap on_error ERR
trap cleanup EXIT
ping_url "${HC_DRILL_URL:-}" /start

[ -s "${BACKUP_PASSPHRASE_FILE:-/nonexistent}" ] || { echo "BACKUP_PASSPHRASE_FILE tidak ada atau kosong." >&2; false; }
mkdir -p "$BACKUP_DIR"
work="$(mktemp -d "$BACKUP_DIR/.drill-XXXXXX")"

stage="memilih backup"
case "$mode" in
  remote)
    [ -n "${BACKUP_REMOTE:-}" ] || { echo "BACKUP_REMOTE kosong." >&2; false; }
    name="$(rclone lsf "$BACKUP_REMOTE" --include 'habitflow-*.tar.gpg' | sort | tail -n 1 | tr -d '\r')"
    [ -n "$name" ] || { echo "Tidak ada backup di $BACKUP_REMOTE." >&2; false; }
    stage="mengunduh $name dari $BACKUP_REMOTE"
    rclone copyto "$BACKUP_REMOTE/$name" "$work/$name"
    rclone copyto "$BACKUP_REMOTE/$name.sha256" "$work/$name.sha256"
    ;;
  local)
    src="$(find "$BACKUP_DIR" -maxdepth 1 -name 'habitflow-*.tar.gpg' | sort | tail -n 1)"
    [ -n "$src" ] || { echo "Tidak ada backup di $BACKUP_DIR." >&2; false; }
    name="$(basename "$src")"
    cp "$src" "$work/$name"; cp "$src.sha256" "$work/$name.sha256"
    ;;
  file)
    [ -f "$file" ] || { echo "Berkas $file tidak ada." >&2; false; }
    name="$(basename "$file")"
    cp "$file" "$work/$name"; cp "$file.sha256" "$work/$name.sha256"
    ;;
esac
log "memulihkan $name"

stage="memeriksa checksum"
( cd "$work" && sha256sum -c "$name.sha256" >/dev/null ) || { echo "Checksum $name tidak cocok: berkas rusak atau diubah." >&2; false; }

stage="dekripsi (kata sandi salah atau berkas rusak)"
gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-file "$BACKUP_PASSPHRASE_FILE" \
  -o "$work/bundle.tar" --decrypt "$work/$name"
mkdir "$work/isi"
tar -C "$work/isi" -xf "$work/bundle.tar"
for f in db.sql.gz files.tar.gz manifest.tsv; do [ -s "$work/isi/$f" ] || { echo "Isi backup tidak lengkap: $f hilang." >&2; false; }; done

stage="memeriksa memori VM"
if [ -r /proc/meminfo ]; then
  avail_mb=$(( $(awk '/MemAvailable/{print $2}' /proc/meminfo) / 1024 ))
  [ "$avail_mb" -ge 500 ] || { echo "Memori tersedia ${avail_mb}MB (<500MB): latihan bisa mematikan container lain. Coba lagi nanti." >&2; false; }
fi

stage="menjalankan MySQL sementara"
root_pw="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
docker rm -f "$drill_container" >/dev/null 2>&1 || true
docker run -d --name "$drill_container" --memory=450m -e MYSQL_ROOT_PASSWORD="$root_pw" -e MYSQL_DATABASE=restore_test \
  mysql:8.4 --performance-schema=OFF --innodb-buffer-pool-size=64M --max-connections=10 >/dev/null
ready=0
for _ in $(seq 1 60); do
  if docker exec -e MYSQL_PWD="$root_pw" "$drill_container" mysqladmin ping -uroot --silent >/dev/null 2>&1; then ready=1; break; fi
  sleep 2
done
[ "$ready" = 1 ] || { echo "MySQL sementara tidak siap dalam 120 detik." >&2; false; }

stage="memulihkan dump"
zcat "$work/isi/db.sql.gz" | docker exec -i -e MYSQL_PWD="$root_pw" "$drill_container" mysql -uroot restore_test

stage="membandingkan jumlah baris dengan manifest"
problems=0 tables=0
while IFS=$'\t' read -r kind t before after; do
  [ "$kind" = "tabel" ] || continue
  tables=$((tables + 1))
  got="$(docker exec -e MYSQL_PWD="$root_pw" "$drill_container" mysql -N -B -uroot restore_test -e "SELECT COUNT(*) FROM \`$t\`" 2>&1)" || got="ERROR: $got"
  lo=$before hi=$after
  [ "$before" -le "$after" ] || { lo=$after; hi=$before; }
  if [[ "$got" =~ ^[0-9]+$ ]] && [ "$got" -ge "$lo" ] && [ "$got" -le "$hi" ]; then
    log "  ok    $t: $got baris (manifest $lo..$hi)"
  else
    log "  BEDA  $t: dipulihkan '$got', manifest $lo..$hi"
    problems=$((problems + 1))
  fi
done < "$work/isi/manifest.tsv"
[ "$tables" -gt 0 ] || { echo "Manifest tidak memuat satu pun tabel." >&2; false; }
[ "$problems" -eq 0 ] || { echo "$problems tabel tidak cocok dengan manifest." >&2; false; }

stage="memeriksa berkas snapshot"
want_files="$(awk -F'\t' '$1=="berkas-snapshot"{print $2}' "$work/isi/manifest.tsv")"
have_files="$(tar -tzf "$work/isi/files.tar.gz" | grep -c '\.enc$' || true)"
[ "$want_files" = "$have_files" ] || { echo "Berkas snapshot: manifest $want_files, arsip $have_files." >&2; false; }

date +%s > "$BACKUP_DIR/last-drill"
log "LATIHAN OK: $name dipulihkan, $tables tabel cocok, $have_files berkas snapshot"
ping_url "${HC_DRILL_URL:-}" "" "latihan ok: $name, $tables tabel"
