#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/face_landmarker.task"
URL="https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
mkdir -p "$(dirname "$DEST")"
echo "Downloading MediaPipe Face Landmarker model..."
curl -fL --retry 3 -o "$DEST.tmp" "$URL"
mv "$DEST.tmp" "$DEST"
echo "OK: $DEST"
