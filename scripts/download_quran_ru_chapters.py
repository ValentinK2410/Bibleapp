#!/usr/bin/env python3
"""Скачивает индекс и все суры (рус. перевод + арабский + транслит.) в assets/quran.

Источник: npm-пакет quran-json (Uthmani — QuranEnc, транслит. — Tanzil, переводы из дистрибутива).
Запуск из корня проекта: python3 scripts/download_quran_ru_chapters.py
"""
from __future__ import annotations

import json
import os
import urllib.request

BASE = "https://cdn.jsdelivr.net/npm/quran-json@3.1.2/dist/chapters/ru"
INDEX_URL = f"{BASE}/index.json"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ASSET_QURAN = os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets", "quran")
CHAPTERS_DIR = os.path.join(ASSET_QURAN, "chapters")


def main() -> None:
    os.makedirs(CHAPTERS_DIR, exist_ok=True)
    print("Загрузка index.json …")
    idx_path = os.path.join(ASSET_QURAN, "index.json")
    urllib.request.urlretrieve(INDEX_URL, idx_path)
    with open(idx_path, encoding="utf-8") as f:
        index = json.load(f)
    if not isinstance(index, list) or len(index) != 114:
        raise SystemExit(f"Неожиданный index: ожидалось 114 сур, получено {len(index) if isinstance(index, list) else type(index)}")
    for surah in index:
        n = int(surah["id"])
        url = f"{BASE}/{n}.json"
        out = os.path.join(CHAPTERS_DIR, f"{n}.json")
        print(f"  сура {n}/114 …")
        urllib.request.urlretrieve(url, out)
    print(f"Готово: {ASSET_QURAN}")


if __name__ == "__main__":
    main()
