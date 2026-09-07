#!/usr/bin/env bash
# Снимки экранов для README на GitHub. Нужен подключённый телефон и установленный debug APK.
# Важно: запускать только последовательно (не параллельно — adb и force-stop конфликтуют).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/screenshots"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PKG="com.example.bible.sqlite"
ACTIVITY="com.example.bible.sqlite/com.example.bible.MainActivity"
LOG_TAG="BibleScreenshot"
WAIT_READY_SEC="${WAIT_READY_SEC:-120}"
WAIT_ROUTE_SEC="${WAIT_ROUTE_SEC:-8}"

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "Устройство не найдено. Подключите телефон с USB-отладкой." >&2
  exit 1
fi

mkdir -p "$OUT"

wait_for_log() {
  local pattern="$1"
  local timeout="$2"
  local start
  start=$(date +%s)
  while true; do
    if "$ADB" logcat -d -s "${LOG_TAG}:I" 2>/dev/null | grep -q "$pattern"; then
      return 0
    fi
    if (( $(date +%s) - start >= timeout )); then
      echo "Таймаут ожидания: $pattern" >&2
      return 1
    fi
    sleep 1
  done
}

capture() {
  local route="$1"
  local file="$2"
  echo "→ $file ($route)"
  "$ADB" logcat -c >/dev/null 2>&1 || true
  "$ADB" shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  "$ADB" shell am start -n "$ACTIVITY" -e start_route "$route" >/dev/null
  wait_for_log "bible_ready" "$WAIT_READY_SEC"
  if [[ "$route" != "books" ]]; then
    wait_for_log "route_navigated:$route" "$WAIT_ROUTE_SEC" || sleep "$WAIT_ROUTE_SEC"
  fi
  sleep 2
  "$ADB" exec-out screencap -p > "$OUT/$file"
}

echo "Снимаю экраны приложения…"

capture "books" "01-books.png"
capture "chapters/genesis" "02-chapters.png"
capture "read/genesis/1/0" "03-reader.png"
capture "media" "04-media.png"
capture "media_videos" "05-videos.png"
capture "media_video_playlists" "06-playlists.png"
capture "media_audios" "07-audios.png"
capture "songs" "08-songs.png"
capture "kids" "09-kids.png"
capture "main_settings" "10-settings.png"
capture "about_app" "11-about.png"
capture "ai" "12-ai-deepseek.png"
capture "gigachat" "13-gigachat.png"
capture "notes" "14-notes.png"
capture "bookmarks" "15-bookmarks.png"
capture "search" "16-search.png"
capture "reading_plan" "17-reading-plan.png"
capture "genealogy" "18-genealogy.png"
capture "maps" "19-maps.png"
capture "other_books" "20-other-books.png"
capture "quran/1" "21-quran.png"
capture "dual?bookId=genesis&chapter=1" "22-dual-compare.png"
capture "history" "23-history.png"
capture "language_study" "24-language-study.png"
capture "strongs" "25-strongs.png"
capture "media_microblog" "26-microblog.png"
capture "media_musician" "27-musician.png"
capture "my_travels" "28-travels.png"
capture "app_contacts" "29-contacts.png"
python3 "$ROOT/scripts/redact_contacts_screenshot.py" "$OUT/29-contacts.png"
capture "backup" "30-backup.png"
capture "kids_games" "31-kids-games.png"
capture "timemark_editor?bookId=genesis&chapter=1&translationCode=SYN&narratorId=bondarenko" "32-timemark.png"
capture "offline_download" "33-offline-study.png"
capture "interlinear_hebrew_sandbox/genesis/1/1/0" "34-interlinear-sandbox.png"

echo "Готово: $OUT"
