#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Аудит русских глоссов PODSTR (Винокуров): ВЗ → НЗ.
- Применяет те же замены, что VinokurovInterlinearFixes.kt
- Фиксирует латиницу в поле r
- Опционально: pyspellchecker (ru) для токенов без тега Name/Geox в pymorphy3
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

# Порядок как в BibleCanon.kt: ВЗ (39), затем НЗ (27)
OLD_TESTAMENT = [
    "genesis", "exodus", "leviticus", "numbers", "deuteronomy",
    "joshua", "judges", "ruth", "1_samuel", "2_samuel", "1_kings", "2_kings",
    "1_chronicles", "2_chronicles", "ezra", "nehemiah", "esther",
    "job", "psalms", "proverbs", "ecclesiastes", "song_of_solomon",
    "isaiah", "jeremiah", "lamentations", "ezekiel", "daniel",
    "hosea", "joel", "amos", "obadiah", "jonah", "micah", "nahum",
    "habakkuk", "zephaniah", "haggai", "zechariah", "malachi",
]

NEW_TESTAMENT = [
    "matthew", "mark", "luke", "john", "acts",
    "james", "1_peter", "2_peter", "1_john", "2_john", "3_john", "jude",
    "romans", "1_corinthians", "2_corinthians", "galatians", "ephesians",
    "philippians", "colossians", "1_thessalonians", "2_thessalonians",
    "1_timothy", "2_timothy", "titus", "philemon", "hebrews", "revelation",
]

PHRASES = [
    ("оно имеет был написанное", "написано"),
    ("они были открыл", "открылись"),
    ("и, оно был", "и было"),
    ("В много части", "Многочастно"),
    ("в много пути", "многообразно"),
    ("имея сказанное", "говоривший"),
    ("Инасмуч как", "Поскольку, как"),
    ("аккомплишед", "уверенно познавших"),
    ("нарратион", "повествование"),
    ("ундертук", "предприняли"),
    ("к тянуть вверх", "составить"),
    ("оно пришёл", "было"),
    ("оно пришло", "было"),
    ("Фессалонианс", "фессалоникийцам"),
    ("генеалогй", "генеалогия"),
    ("провербс", "притчи"),
    ("в Господь", "у Господа"),
    ("Силванус", "Силуан"),
    ("Морасфите", "Морасфитянин"),
    ("Джофам", "Иоафам"),
    ("Яхве", "Господь"),
    ("безвидность", "пустота"),
    ("был в, среди", "был среди"),
    ("на, день пять", "пятый день"),
    ("в, четвёртый месяц", "в четвёртом месяце"),
    ("в, тридцать", "в тридцатый"),
    ("к, учиться", "узнать"),
    ("к, учить", "понять"),
    ("к, говоря", "говоря"),
    ("к, его", "к нему"),
    ("от, шатёр", "из шатра"),
    ("был Иов", "Иов"),
    ("долгий назад", "давно"),
]
PHRASES = sorted(dict(PHRASES).items(), key=lambda x: -len(x[0]))

TOKENS = [
    ("иникуитй", "нечестие"),
    ("ванкуиш", "одолевать"),
    ("трансгрессион", "преступление"),
    ("куадрупле", "четверной"),
    ("фоурфолд", "четверной"),
    ("супервенинг", "наступающий"),
    ("енсуинг", "следующий"),
    ("сентенке", "высказывание"),
    ("мессаге", "возвещение"),
    ("бусинесс", "дело"),
    ("дутй", "долг"),
    ("оркл", "оракул"),
    ("чрониклес", "летописи"),
    ("коунсел", "совет"),
    ("инфериор", "ниже"),
    ("фавоуред", "благосклонный"),
    ("пертаининг", "относящийся"),
    ("плеасе", "угождать"),
    ("реквест", "просьба"),
    ("вхеревиф", "подлинно"),
    ("коммуне(-икатион)", "общение"),
    ("коммуне", "общение"),
]
TOKENS = sorted(dict(TOKENS).items(), key=lambda x: -len(x[0]))

LATIN_RE = re.compile(r"[a-zA-Z]{2,}")

# Оставшиеся после таблицы VinokurovInterlinearFixes (подстроки в глоссе **после** fix_r).
# Уже исправляемые в коде слова (генеалогй, нарратион, …) сюда не включать — они не встретятся.
REMAINING_SUSPICIOUS_SUBSTRINGS = sorted(
    {
        "дисплеасинг",
        "рестраинед",
        "десирабле",
        "компосед",
        "екуаллй",
        "обтаинед",
        "гритинг",
        "дисперсион",
        "аррангед",
        "трублед",
        "рекординг",
    },
    key=len,
    reverse=True,
)


def fix_russian_gloss(raw: str) -> str:
    if not raw:
        return raw
    s = raw
    for a, b in PHRASES:
        s = s.replace(a, b)
    for a, b in TOKENS:
        s = s.replace(a, b)
    return s


def tokenize_cyrillic(s: str) -> list[str]:
    return re.findall(r"[а-яА-ЯёЁ]+", s)


def main() -> int:
    import argparse

    ap = argparse.ArgumentParser(description="Аудит PODSTR глоссов Винокурова")
    ap.add_argument(
        "--spell",
        action="store_true",
        help="Медленно: pyspellchecker+pymorphy3 (много ложных срабатываний)",
    )
    args = ap.parse_args()

    root = Path(__file__).resolve().parents[1]
    podstr = root / "app" / "src" / "main" / "assets" / "bible" / "PODSTR"
    out_md = root / "tools" / "podstr_vinokurov_audit_report.md"

    have_extra = False
    spell_ru = None
    morph = None
    if args.spell:
        try:
            from spellchecker import SpellChecker
            from pymorphy3 import MorphAnalyzer

            spell_ru = SpellChecker(language="ru")
            morph = MorphAnalyzer()
            have_extra = True
        except ImportError:
            pass

    fix_rows: list[dict] = []
    latin_rows: list[dict] = []
    spell_rows: list[dict] = []
    suspicious_rows: list[dict] = []

    def process_book(book_id: str, testament: str) -> None:
        path = podstr / f"{book_id}.json"
        if not path.exists():
            return
        data = json.loads(path.read_text(encoding="utf-8"))
        book_name = data.get("book", {}).get("name", book_id)
        for ch in data.get("book", {}).get("chapters", []):
            cnum = ch.get("number")
            for verse in ch.get("verses", []):
                vnum = verse.get("number")
                for wi, w in enumerate(verse.get("words", [])):
                    r = w.get("r") or ""
                    o = w.get("o") or ""
                    fixed = fix_russian_gloss(r)
                    if fixed != r:
                        fix_rows.append({
                            "testament": testament,
                            "book_id": book_id,
                            "book_name": book_name,
                            "chapter": cnum,
                            "verse": vnum,
                            "word_index": wi,
                            "o": o[:80] + ("…" if len(o) > 80 else ""),
                            "r_before": r,
                            "r_after": fixed,
                        })
                    lat = LATIN_RE.findall(fixed)
                    if lat:
                        latin_rows.append({
                            "testament": testament,
                            "book_id": book_id,
                            "book_name": book_name,
                            "chapter": cnum,
                            "verse": vnum,
                            "word_index": wi,
                            "r": fixed,
                            "latin_fragments": ", ".join(sorted(set(lat))),
                        })
                    low = fixed.lower()
                    for sub in REMAINING_SUSPICIOUS_SUBSTRINGS:
                        if sub.lower() in low:
                            suspicious_rows.append({
                                "testament": testament,
                                "book_id": book_id,
                                "book_name": book_name,
                                "chapter": cnum,
                                "verse": vnum,
                                "word_index": wi,
                                "substring": sub,
                                "r_after_fix": fixed[:200],
                            })
                    if have_extra and morph and spell_ru and fixed:
                        for tok in tokenize_cyrillic(fixed):
                            tl = tok.lower()
                            if len(tl) < 4:
                                continue
                            p = morph.parse(tl)
                            if not p:
                                continue
                            tag = str(p[0].tag)
                            if "Name" in tag or "Geox" in tag or "Patr" in tag or "Surn" in tag:
                                continue
                            if tl in spell_ru:
                                continue
                            unk = spell_ru.unknown([tl])
                            if tl in unk:
                                spell_rows.append({
                                    "testament": testament,
                                    "book_id": book_id,
                                    "chapter": cnum,
                                    "verse": vnum,
                                    "token": tok,
                                    "r_excerpt": fixed[:120],
                                })

    for bid in OLD_TESTAMENT:
        process_book(bid, "ВЗ")
    for bid in NEW_TESTAMENT:
        process_book(bid, "НЗ")

    # Сводка замен: какая пара сколько раз
    from collections import Counter

    def diff_reason(before: str, after: str) -> str:
        """Грубое описание: какие подстроки из таблицы затронуты."""
        reasons = []
        for a, b in PHRASES + TOKENS:
            if a in before and a not in after:
                reasons.append(f"{a!r}→{b!r}")
        if not reasons:
            reasons.append("(составная замена или пересечение)")
        return "; ".join(reasons[:5])

    fix_counter = Counter()
    for row in fix_rows:
        key = (row["r_before"], row["r_after"])
        fix_counter[key] += 1

    lines: list[str] = []
    lines.append("# Отчёт: аудит подстрочника Винокурова (PODSTR, поле `r`)\n")
    lines.append("Сгенерировано скриптом `tools/audit_podstr_vinokurov.py`.\n")
    lines.append("## Метод\n")
    lines.append("1. **Замены из кода** — те же пары, что в `VinokurovInterlinearFixes.kt` (PHRASES + TOKENS), в порядке «длинная фраза раньше».\n")
    lines.append(
        "2. **Латиница в поле `r`** — после замен ищутся фрагменты `[a-zA-Z]{2,}`. В PODSTR русский глосс обычно **только кириллица**; латиница в основном в поле `t` (транслитерация), поэтому здесь часто **0** вхождений.\n"
    )
    lines.append(
        "3. **Подозрительные подстроки (кириллические кальки)** — ручной список `REMAINING_SUSPICIOUS_SUBSTRINGS` в скрипте: что ещё может остаться в тексте после автозамен (для дальнейшего переноса в `VinokurovInterlinearFixes`).\n"
    )
    if have_extra:
        lines.append(
            "4. **Опционально `--spell`** — `pyspellchecker` + pymorphy3 (очень много ложных срабатываний на редких словах; не рекомендуется для полного отчёта).\n"
        )

    lines.append("\n## Сводка\n")
    lines.append(f"- **Всего глоссов с изменением после таблицы замен:** {len(fix_rows)}\n")
    lines.append(f"- **Уникальных пар (до → после):** {len(fix_counter)}\n")
    lines.append(f"- **Глоссов с латиницей после замен:** {len(latin_rows)}\n")
    lines.append(f"- **Глоссов с оставшимися подозрительными подстроками (список в скрипте):** {len(suspicious_rows)}\n")
    if have_extra:
        lines.append(f"- **Записей «подозрительный токен» (spellchecker, опционально):** {len(spell_rows)}\n")

    lines.append("\n## 1. Статистика замен (ВинокуровInterlinearFixes)\n")
    lines.append("| Счётчик | Было | Стало |\n|---|---|---|\n")
    for (rb, ra), cnt in fix_counter.most_common(80):
        lines.append(f"| {cnt} | {rb!r} | {ra!r} |\n")
    if len(fix_counter) > 80:
        lines.append(f"\n*… и ещё {len(fix_counter) - 80} уникальных пар.*\n")

    lines.append("\n## 2. Ветхий завет: все вхождения с заменой (фрагмент)\n")
    ot_fix = [x for x in fix_rows if x["testament"] == "ВЗ"]
    lines.append(f"Всего строк: {len(ot_fix)}. Ниже — первые 200; полный список см. CSV при необходимости.\n\n")
    lines.append("| Книга | Гл | Ст | r (до) | r (после) |\n|---|---|---|---|---|\n")
    for row in ot_fix[:200]:
        lines.append(
            f"| {row['book_name']} | {row['chapter']} | {row['verse']} | {row['r_before'][:60]!r} | {row['r_after'][:60]!r} |\n"
        )
    if len(ot_fix) > 200:
        lines.append(f"\n*… скрыто {len(ot_fix) - 200} строк.*\n")

    lines.append("\n## 3. Новый завет: все вхождения с заменой (фрагмент)\n")
    nt_fix = [x for x in fix_rows if x["testament"] == "НЗ"]
    lines.append(f"Всего строк: {len(nt_fix)}. Первые 200.\n\n")
    lines.append("| Книга | Гл | Ст | r (до) | r (после) |\n|---|---|---|---|---|\n")
    for row in nt_fix[:200]:
        lines.append(
            f"| {row['book_name']} | {row['chapter']} | {row['verse']} | {row['r_before'][:60]!r} | {row['r_after'][:60]!r} |\n"
        )
    if len(nt_fix) > 200:
        lines.append(f"\n*… скрыто {len(nt_fix) - 200} строк.*\n")

    lines.append("\n## 4. Латиница в глоссах (после замен)\n")
    if not latin_rows:
        lines.append("*Латинских фрагментов в поле `r` не найдено (ожидаемо для этого перевода).*\n")
    else:
        lines.append("| Завет | Книга | Гл | Ст | Латинские фрагменты | r (фрагмент) |\n|---|---|---|---|---|---|\n")
        for row in latin_rows[:300]:
            lines.append(
                f"| {row['testament']} | {row['book_name']} | {row['chapter']} | {row['verse']} | {row['latin_fragments'][:40]} | {row['r'][:70]!r} |\n"
            )
        if len(latin_rows) > 300:
            lines.append(f"\n*… скрыто {len(latin_rows) - 300} строк.*\n")

    lines.append("\n## 5. Оставшиеся подозрительные подстроки (после таблицы замен)\n")
    lines.append("| Завет | Книга | Гл | Ст | Подстрока | r (после fix) |\n|---|---|---|---|---|---|\n")
    for row in suspicious_rows[:400]:
        lines.append(
            f"| {row['testament']} | {row['book_name']} | {row['chapter']} | {row['verse']} | {row['substring']!r} | {row['r_after_fix'][:90]!r} |\n"
        )
    if len(suspicious_rows) > 400:
        lines.append(f"\n*… скрыто {len(suspicious_rows) - 400} строк.*\n")

    if have_extra and spell_rows:
        lines.append("\n## 6. Подозрительные кириллические токены (spellchecker, фрагмент)\n")
        lines.append("| Завет | Книга | Гл | Ст | Токен | Глосс |\n|---|---|---|---|---|---|\n")
        for row in spell_rows[:250]:
            lines.append(
                f"| {row['testament']} | {row['book_id']} | {row['chapter']} | {row['verse']} | {row['token']!r} | {row['r_excerpt'][:80]!r} |\n"
            )
        if len(spell_rows) > 250:
            lines.append(f"\n*… скрыто {len(spell_rows) - 250} строк.*\n")

    out_md.write_text("".join(lines), encoding="utf-8")

    # CSV полный для замен
    csv_path = root / "tools" / "podstr_fix_occurrences.csv"
    import csv
    with csv_path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(
            f,
            fieldnames=["testament", "book_id", "book_name", "chapter", "verse", "word_index", "o", "r_before", "r_after"],
        )
        w.writeheader()
        for row in fix_rows:
            w.writerow(row)

    print(f"Report: {out_md}")
    print(f"CSV (all fixes): {csv_path}")
    print(
        f"fix_rows={len(fix_rows)} latin_rows={len(latin_rows)} "
        f"suspicious={len(suspicious_rows)} spell_rows={len(spell_rows)}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
