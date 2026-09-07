#!/usr/bin/env bash
# Публикация Bible.apk на GitHub Releases (тег latest). Нужен: gh auth login
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
DIST="$ROOT/dist"
NOTES="$DIST/release-notes.md"

if ! command -v gh >/dev/null; then
  echo "Установите GitHub CLI: brew install gh && gh auth login" >&2
  exit 1
fi

if [ ! -f "$APK" ]; then
  echo "Сначала соберите release: ./gradlew :app:assembleRelease" >&2
  exit 1
fi

mkdir -p "$DIST"
cp "$APK" "$DIST/Bible.apk"
ls -lh "$DIST/Bible.apk"

{
  echo "Готовая сборка приложения для Android."
  echo
  echo "1. Скачайте **Bible.apk** в разделе Assets."
  echo "2. На телефоне разрешите установку из неизвестного источника."
  echo "3. Откройте файл и установите."
  echo
  awk '/^## /{n++} n==1' "$ROOT/CHANGELOG.md"
  echo
  echo "Полная история: [CHANGELOG.md](https://github.com/ValentinK2410/Bibleapp/blob/master/CHANGELOG.md)"
} > "$NOTES"

gh release upload latest "$DIST/Bible.apk" --clobber
gh release edit latest --notes-file "$NOTES" --title "Скачать приложение"
echo "Готово: https://github.com/ValentinK2410/Bibleapp/releases/latest"
