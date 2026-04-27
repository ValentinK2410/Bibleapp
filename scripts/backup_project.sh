#!/usr/bin/env bash
# Резервная копия проекта в отдельный каталог (без сборочного мусора).
# Восстановление: скопировать файлы из каталога назад в рабочую папку или
# открыть проект прямо из зеркала в Android Studio.
#
# Использование:
#   ./scripts/backup_project.sh
#   BACKUP_DEST=/path/to/mirror ./scripts/backup_project.sh
#   BACKUP_TIMESTAMP=1 ./scripts/backup_project.sh   # снимок Bible-safety-snapshots/ГГГГММДД-ЧЧММСС/Bible
#   DRY_RUN=1 ./scripts/backup_project.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PARENT="$(dirname "$ROOT")"
DEFAULT_DEST="${PARENT}/Bible-safety-mirror"

DEST="${BACKUP_DEST:-$DEFAULT_DEST}"
if [[ "${BACKUP_TIMESTAMP:-}" == "1" ]]; then
  SNAP="${PARENT}/Bible-safety-snapshots/$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$SNAP"
  DEST="${SNAP}/Bible"
fi

RSYNC=(rsync -a --human-readable --delete)
if [[ "${DRY_RUN:-}" == "1" ]]; then
  RSYNC+=(--dry-run)
fi

mkdir -p "$DEST"

# Исключаем то, что раздувает копию и всё равно пересобирается / кэшируется.
EXCLUDES=(
  --exclude '.gradle/'
  --exclude '**/build/'
  --exclude 'build/'
  --exclude '.idea/caches/'
  --exclude '.idea/libraries/'
  --exclude 'captures/'
  --exclude '.externalNativeBuild/'
  --exclude '.cxx/'
  --exclude '*.iml'
  --exclude '.DS_Store'
)

# Раскомментируйте, если не нужно тащить историю Git в зеркало:
# EXCLUDES+=(--exclude '.git/')

echo "Источник: $ROOT"
echo "  Куда: $DEST"
"${RSYNC[@]}" "${EXCLUDES[@]}" "$ROOT/" "$DEST/"
echo "Готово."
