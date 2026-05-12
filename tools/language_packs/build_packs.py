#!/usr/bin/env python3
"""Сборка zip-пакетов словарей для приложения BibleSqlite «Изучение языков».

Требование: pip install -r requirements.txt (wordfreq, eng-to-ipa).

Переводы подтягиваются через MyMemory API (нужен интернет). Без сети используйте готовые dist/*.zip.
"""
from __future__ import annotations

import json
import pathlib
import zipfile

from pack_content_generator import (
    TARGET,
    build_english_rows,
    build_foreign_rows,
    freq_lemmas,
    load_en_ru,
)

ROOT = pathlib.Path(__file__).resolve().parent
DIST = ROOT / "dist"


def write_zip(lang_app: str, version: str, rows: list[dict]) -> pathlib.Path:
    DIST.mkdir(parents=True, exist_ok=True)
    path = DIST / f"{lang_app}_v1.zip"
    manifest = {"lang": lang_app, "version": version, "schema": 1}
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("pack.json", json.dumps(manifest, ensure_ascii=False))
        zf.writestr(
            "words.jsonl",
            "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows).encode("utf-8"),
        )
    return path


def main() -> None:
    enru = load_en_ru()
    packs = [
        ("english", build_english_rows(freq_lemmas("en"), enru)),
        ("greek", build_foreign_rows(lang_app="greek", words=freq_lemmas("el"), translator_src="el")),
        ("arabic", build_foreign_rows(lang_app="arabic", words=freq_lemmas("ar"), translator_src="ar")),
        ("irit", build_foreign_rows(lang_app="irit", words=freq_lemmas("he"), translator_src="iw")),
    ]
    for lang_app, rows in packs:
        print(f"Pack {lang_app}: {len(rows)} rows", flush=True)
        if len(rows) < TARGET:
            raise SystemExit(f"Недостаточно лемм для {lang_app}: {len(rows)}")
        zp = write_zip(lang_app, "1.0.0", rows)
        print("OK", zp, "words", len(rows), flush=True)


if __name__ == "__main__":
    main()
