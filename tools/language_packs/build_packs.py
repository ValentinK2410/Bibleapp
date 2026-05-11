#!/usr/bin/env python3
"""Сборка zip-пакетов словарей для приложения BibleSqlite «Изучение языков».

Требование: pip install -r requirements.txt (wordfreq).
"""
from __future__ import annotations

import json
import pathlib
import re
import zipfile

from wordfreq import top_n_list

ROOT = pathlib.Path(__file__).resolve().parent
DIST = ROOT / "dist"
DATA = ROOT / "data"

TARGET = 1000


def load_en_ru() -> dict[str, str]:
    p = DATA / "en_ru_core.json"
    if not p.is_file():
        return {}
    return json.loads(p.read_text(encoding="utf-8"))


def freq_lemmas(lang: str) -> list[str]:
    raw = top_n_list(lang, 25000)
    out: list[str] = []
    for w in raw:
        if not w:
            continue
        w = w.strip()
        if len(w) < 2:
            continue
        if lang == "en":
            if not re.match(r"^[a-z][a-z\-']*$", w, re.I):
                continue
        out.append(w)
        if len(out) >= TARGET + 120:
            break
    return out[:TARGET]


def pack_rows_english(words: list[str], enru: dict[str, str]) -> list[dict]:
    rows = []
    for i, lemma in enumerate(words[:TARGET], start=1):
        low = lemma.lower()
        gloss = enru.get(low) or enru.get(lemma)
        if not gloss:
            gloss = f"англ. «{lemma}» — уточните значение по контексту (топ-{i})."
        rows.append(
            {
                "id": f"{i:04d}",
                "lemma": lemma,
                "display": lemma,
                "glossRu": gloss,
                "pos": "",
                "frequencyRank": i,
                "mnemonicHint": f"Частотный порядок (EN #{i}); связывайте с примером и образом.",
                "morphologyNotes": "",
            },
        )
    return rows


def pack_rows_foreign(
    *,
    lang_app: str,
    script_label: str,
    words: list[str],
) -> list[dict]:
    rows = []
    hint_lang = {"irit": "иврите", "greek": "греческом", "arabic": "арабском"}[lang_app]
    for i, w in enumerate(words[:TARGET], start=1):
        gloss = (
            f"Слово на {hint_lang} (форма в учебной базе: «{w}»). №{i} по частоте списков."
        )
        row = {
            "id": f"{i:04d}",
            "lemma": w,
            "display": w,
            "glossRu": gloss,
            "pos": "",
            "frequencyRank": i,
            "mnemonicHint": f"Выучите связь символов {script_label} с звучанием; образ для «{w[:18]}». ",
            "morphologyNotes": "Корень/паттерн см. языковые справочники; поле можно дополнить вручную.",
        }
        rows.append(row)
    return rows


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
    DATA.mkdir(parents=True, exist_ok=True)
    enru = load_en_ru()
    packs = [
        ("english", pack_rows_english(freq_lemmas("en"), enru)),
        ("greek", pack_rows_foreign(lang_app="greek", script_label="Ελληνικά", words=freq_lemmas("el"))),
        ("arabic", pack_rows_foreign(lang_app="arabic", script_label="العربية", words=freq_lemmas("ar"))),
        ("irit", pack_rows_foreign(lang_app="irit", script_label="עברית", words=freq_lemmas("he"))),
    ]
    for lang_app, rows in packs:
        if len(rows) < TARGET:
            raise SystemExit(f"Недостаточно лемм для {lang_app}: {len(rows)}")
        zp = write_zip(lang_app, "1.0.0", rows)
        print("OK", zp, "words", len(rows))


if __name__ == "__main__":
    main()
