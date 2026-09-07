#!/usr/bin/env bash
# Снимки экранов для README на GitHub. Нужен подключённый телефон и установленный debug APK.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/screenshots"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PKG="com.example.bible.sqlite"
ACTIVITY="com.example.bible.sqlite/com.example.bible.MainActivity"

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "Устройство не найдено. Подключите телефон с USB-отладкой." >&2
  exit 1
fi

mkdir -p "$OUT"

capture() {
  local route="$1"
  local file="$2"
  echo "→ $file ($route)"
  "$ADB" shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 1
  "$ADB" shell am start -n "$ACTIVITY" -e start_route "$route" >/dev/null
  sleep 3
  "$ADB" exec-out screencap -p > "$OUT/$file"
}

echo "Снимаю экраны приложения…"

capture "books" "01-books.jpg"
capture "chapters/genesis" "02-chapters.jpg"
capture "read/genesis/1/0" "03-reader.jpg"
capture "media" "04-media.jpg"
capture "media_videos" "05-videos.jpg"
capture "media_video_playlists" "06-playlists.jpg"
capture "media_audios" "07-audios.jpg"
capture "songs" "08-songs.jpg"
capture "kids" "09-kids.jpg"
capture "main_settings" "10-settings.jpg"
capture "about_app" "11-about.jpg"
capture "ai" "12-ai-deepseek.jpg"
capture "gigachat" "13-gigachat.jpg"
capture "notes" "14-notes.jpg"
capture "bookmarks" "15-bookmarks.jpg"
capture "search" "16-search.jpg"
capture "reading_plan" "17-reading-plan.jpg"
capture "genealogy" "18-genealogy.jpg"
capture "maps" "19-maps.jpg"
capture "other_books" "20-other-books.jpg"
capture "quran/1" "21-quran.jpg"

echo "Готово: $OUT"
