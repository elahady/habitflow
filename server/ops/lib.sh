# Fungsi bersama skrip operasional Habitflow (tahap 31K). Di-source, bukan dijalankan.
# shellcheck shell=bash

OPS_ENV="${HABITFLOW_OPS_ENV:-$HOME/.habitflow-ops.env}"

load_env() {
  if [ ! -r "$OPS_ENV" ]; then
    echo "Berkas pengaturan $OPS_ENV tidak ada. Salin dari server/ops/ops.env.example." >&2
    exit 2
  fi
  # shellcheck disable=SC1090
  . "$OPS_ENV"
  : "${BACKUP_DIR:?BACKUP_DIR kosong}"
  : "${APP_CONTAINER:=habitflow}"
  : "${QUEUE_CONTAINER:=habitflow-queue}"
  : "${MYSQL_CONTAINER:=shared-mysql}"
  : "${BACKUP_KEEP_DAYS:=30}"
  : "${BACKUP_LOCAL_KEEP_DAYS:=7}"
}

log() { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

# ping <url> [/start|/fail] [pesan]: kirim sinyal ke layanan pemantau. Gagal mengirim tidak boleh menggagalkan pekerjaan.
ping_url() {
  local url="${1:-}" suffix="${2:-}" msg="${3:-}"
  [ -n "$url" ] || return 0
  curl -fsS -m 15 --retry 2 --data-raw "$msg" "${url%/}$suffix" >/dev/null 2>&1 || log "peringatan: ping $suffix gagal terkirim"
  return 0
}

# alert <judul> <pesan>: dorong ke ponsel lewat ntfy.
alert() {
  local title="$1" msg="$2"
  log "PERINGATAN: $title - $msg"
  [ -n "${ALERT_URL:-}" ] || return 0
  curl -fsS -m 15 --retry 2 -H "Title: $title" -H "Priority: high" -d "$msg" "$ALERT_URL" >/dev/null 2>&1 || log "peringatan: notifikasi ntfy gagal terkirim"
  return 0
}

# Kunci sederhana (mkdir bersifat atomik) supaya dua pekerjaan sejenis tidak berjalan bersamaan.
lock_or_exit() {
  LOCK_DIR="${BACKUP_DIR%/}/.lock-$1"
  mkdir -p "$BACKUP_DIR"
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    # Kunci yang lebih tua dari 6 jam dianggap sisa proses yang mati.
    if [ -n "$(find "$LOCK_DIR" -maxdepth 0 -mmin +360 2>/dev/null)" ]; then
      rmdir "$LOCK_DIR" 2>/dev/null && mkdir "$LOCK_DIR" 2>/dev/null || { log "kunci $1 masih dipegang"; exit 3; }
    else
      log "pekerjaan $1 sedang berjalan, dilewati"
      exit 3
    fi
  fi
}
unlock() { [ -n "${LOCK_DIR:-}" ] && rmdir "$LOCK_DIR" 2>/dev/null || true; }
