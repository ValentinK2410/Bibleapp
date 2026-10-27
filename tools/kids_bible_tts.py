#!/usr/bin/env python3
"""Голос пилота «Детской Библии»: Piper (русский), на выходе m4a и тайминги абзацев.

Нужны piper, модель .onnx и ffmpeg.
  PIPER_BIN=/path/piper PIPER_MODEL=/path/ru.onnx python3 tools/kids_bible_tts.py
"""
import json
import os
import subprocess
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STORIES = ROOT / "app/src/main/assets/kids_bible/stories.json"
AUDIO = ROOT / "app/src/main/assets/kids_bible/audio"
PIPER = os.environ.get("PIPER_BIN", "piper")
MODEL = os.environ.get("PIPER_MODEL", "")


def duration_ms(path: Path) -> int:
    with wave.open(str(path), "rb") as handle:
        return int(handle.getnframes() / handle.getframerate() * 1000)


def main() -> None:
    if not MODEL:
        raise SystemExit("Задайте PIPER_MODEL")
    stories = json.loads(STORIES.read_text(encoding="utf-8"))["stories"]
    work = Path("/tmp/kids_bible_wav")
    work.mkdir(parents=True, exist_ok=True)
    silence = work / "silence.wav"
    subprocess.check_call(
        ["ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=22050:cl=mono", "-t", "0.35", str(silence)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    gap = duration_ms(silence)
    AUDIO.mkdir(parents=True, exist_ok=True)
    for story in stories:
        parts = [(f"p{i}", text) for i, text in enumerate(story["paragraphs"])]
        parts += [
            ("verse", "Ключевой стих. " + story["keyVerse"]),
            ("meaning", "Что это значит? " + story["meaning"]),
            ("christ", "Как это связано с Иисусом. " + story["christLink"]),
            ("prayer", "Молитва. " + story["prayer"]),
        ]
        folder = work / story["id"]
        folder.mkdir(parents=True, exist_ok=True)
        cues = []
        cursor = 0
        lines = []
        for index, (part, text) in enumerate(parts):
            wav = folder / f"{index:02d}.wav"
            subprocess.run(
                [PIPER, "-m", MODEL, "-f", str(wav), "--length_scale", "1.08", "--sentence_silence", "0.28"],
                input=text.encode("utf-8"),
                check=True,
            )
            length = duration_ms(wav)
            cues.append({"part": part, "startMs": cursor, "endMs": cursor + length})
            cursor += length
            lines.append(f"file '{wav}'")
            if index != len(parts) - 1:
                lines.append(f"file '{silence}'")
                cursor += gap
        listing = folder / "list.txt"
        listing.write_text("\n".join(lines) + "\n", encoding="utf-8")
        m4a = AUDIO / f"{story['id']}.m4a"
        subprocess.check_call(
            ["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", str(listing), "-c:a", "aac", "-b:a", "48k", "-ac", "1", str(m4a)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        (AUDIO / f"{story['id']}.timings.json").write_text(
            json.dumps({"cues": cues}, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(story["id"], m4a.stat().st_size)


if __name__ == "__main__":
    main()
