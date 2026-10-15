#!/usr/bin/env bash
# Pemantauan Habitflow (tahap 31K), dijalankan dari cron tiap 10 menit di VM. Memeriksa container, kesehatan situs (lokal
# dan lewat Cloudflare Tunnel), disk, kesegaran backup dan latihan pemulihan, dan antrean email. Peringatan dikirim ke
# ponsel lewat ntfy saat masalah MUNCUL, diulang tiap ALERT_REPEAT_HOURS selama belum pulih, dan dikabari saat PULIH.
# Setiap putaran yang selesai mengirim ping ke HC_MONITOR_URL: kalau VM atau cron mati, layanan pemantau luar yang
# berbunyi (VM tidak bisa memberi tahu bahwa dirinya mati).
# Batas email harian (80%) dipantau di aplikasi sendiri (CountSentMail), bukan di sini.
set -uo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "$here/lib.sh"
load_env

: "${PUBLIC_URL:=}" "${LOCAL_URL:=http://127.0.0.1:8081/up}" "${DISK_MAX_PERCENT:=85}" "${BACKUP_MAX_AGE_HOURS:=26}"
: "${QUEUE_MAX_AGE_SECONDS:=900}" "${ALERT_REPEAT_HOURS:=6}"
state_dir="$BACKUP_DIR/.monitor-state"
mkdir -p "$state_dir"
now="$(date +%s)"
failures=0

# cek <kunci> <judul> <status 0/1> <pesan>: bandingkan dengan keadaan sebelumnya, lalu kirim peringatan seperlunya.
cek() {
  local key="$1" title="$2" bad="$3" msg="$4" f="$state_dir/$1" last
  if [ "$bad" -eq 0 ]; then
    if [ -f "$f" ]; then rm -f "$f"; alert "Habitflow pulih: $title" "$msg"; fi
    return
  fi
  failures=$((failures + 1))
  last=0
  [ -f "$f" ] && last="$(cat "$f")"
  if [ ! -f "$f" ] || [ $(( now - last )) -ge $(( ALERT_REPEAT_HOURS * 3600 )) ]; then
    echo "$now" > "$f"
    alert "Habitflow: $title" "$msg"
  else
    log "masih bermasalah ($title), peringatan sudah dikirim"
  fi
}

# 1. Container hidup.
for c in "$APP_CONTAINER" "$QUEUE_CONTAINER" "$MYSQL_CONTAINER"; do
  running="$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null || echo missing)"
  if [ "$running" = "true" ]; then cek "container-$c" "container $c mati" 0 "$c hidup lagi"; else cek "container-$c" "container $c mati" 1 "Container $c tidak berjalan (status: $running)."; fi
done

# 2. Situs menjawab: dari dalam VM, lalu lewat internet dan tunnel.
if curl -fsS -m 10 -o /dev/null "$LOCAL_URL" 2>/dev/null; then cek situs-lokal "situs tidak menjawab di VM" 0 "situs menjawab lagi"; else cek situs-lokal "situs tidak menjawab di VM" 1 "$LOCAL_URL tidak menjawab."; fi
if [ -n "$PUBLIC_URL" ]; then
  if curl -fsS -m 15 -o /dev/null "$PUBLIC_URL" 2>/dev/null; then cek situs-publik "situs tidak terjangkau dari internet" 0 "situs terjangkau lagi"; else cek situs-publik "situs tidak terjangkau dari internet" 1 "$PUBLIC_URL tidak menjawab (cek Cloudflare Tunnel)."; fi
fi

# 3. Disk.
used="$(df --output=pcent / 2>/dev/null | tail -n 1 | tr -dc '0-9')"
if [ -n "$used" ] && [ "$used" -ge "$DISK_MAX_PERCENT" ]; then cek disk "disk hampir penuh" 1 "Disk VM terpakai ${used}% (batas ${DISK_MAX_PERCENT}%). Lihat DOCKER-CLEANUP.md."; else cek disk "disk hampir penuh" 0 "disk lega lagi (${used:-?}%)"; fi

# 4. Backup terakhir cukup baru, dan latihan pemulihan tidak terlalu lama (bulanan, batas 40 hari).
age_check() { # <berkas> <batas-detik> -> 0 kalau cukup baru
  [ -f "$1" ] && [ $(( now - $(cat "$1") )) -le "$2" ]
}
if age_check "$BACKUP_DIR/last-success" $(( BACKUP_MAX_AGE_HOURS * 3600 )); then cek backup "backup tidak berjalan" 0 "backup berjalan lagi"; else cek backup "backup tidak berjalan" 1 "Backup sukses terakhir lebih dari ${BACKUP_MAX_AGE_HOURS} jam lalu (atau belum pernah)."; fi
if age_check "$BACKUP_DIR/last-drill" $(( 40 * 86400 )); then cek latihan "latihan pemulihan terlambat" 0 "latihan pemulihan sudah dijalankan"; else cek latihan "latihan pemulihan terlambat" 1 "Latihan pemulihan terakhir lebih dari 40 hari lalu (atau belum pernah). Jalankan restore-drill.sh."; fi

# 5. Antrean email: job macet, dan email yang gagal kirim.
db_name="$(docker exec "$APP_CONTAINER" printenv DB_DATABASE 2>/dev/null)"
db_user="$(docker exec "$APP_CONTAINER" printenv DB_USERNAME 2>/dev/null)"
db_pass="$(docker exec "$APP_CONTAINER" printenv DB_PASSWORD 2>/dev/null)"
if [ -n "$db_name" ] && [ -n "$db_user" ] && [ -n "$db_pass" ]; then
  q() { docker exec -e MYSQL_PWD="$db_pass" "$MYSQL_CONTAINER" mysql -N -B -u"$db_user" "$db_name" -e "$1" 2>/dev/null; }
  stuck="$(q "SELECT COUNT(*) FROM jobs WHERE created_at < UNIX_TIMESTAMP() - $QUEUE_MAX_AGE_SECONDS")"
  failed="$(q "SELECT COUNT(*) FROM failed_jobs")"
  if [[ "$stuck" =~ ^[0-9]+$ ]] && [ "$stuck" -gt 0 ]; then cek antrean "antrean email macet" 1 "$stuck job menunggu lebih dari $QUEUE_MAX_AGE_SECONDS detik. Cek container $QUEUE_CONTAINER."; else cek antrean "antrean email macet" 0 "antrean jalan lagi"; fi
  if [[ "$failed" =~ ^[0-9]+$ ]] && [ "$failed" -gt 0 ]; then cek email-gagal "ada email gagal kirim" 1 "$failed email di failed_jobs (kode verifikasi pengguna mungkin tidak sampai)."; else cek email-gagal "ada email gagal kirim" 0 "tidak ada email gagal"; fi
else
  cek database "pemantau tidak bisa membaca database" 1 "Kredensial database tidak terbaca dari container $APP_CONTAINER."
fi

if [ "$failures" -eq 0 ]; then log "semua pemeriksaan lulus"; else log "$failures pemeriksaan bermasalah"; fi
# Ping tetap dikirim walau ada masalah: yang dipantau layanan luar adalah "pemantau ini hidup", masalahnya sudah lewat ntfy.
ping_url "${HC_MONITOR_URL:-}" "" "$failures masalah"
exit 0
