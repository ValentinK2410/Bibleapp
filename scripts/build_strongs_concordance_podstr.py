#!/usr/bin/env python3
"""
Строит strongs_concordance_podstr.json: номер Стронга (нормализованный) → список стихов
по всем JSON подстрочника Винокурова (bible/PODSTR/*.json).

Формат элемента стиха: {"b": book_id, "c": chapter, "v": verse}
На один номер не более MAX_PER_STRONG вхождений (по порядку книг/глав/стихов).
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PODSTR = ROOT / "app" / "src" / "main" / "assets" / "bible" / "PODSTR"
OUT = ROOT / "app" / "src" / "main" / "assets" / "strongs_concordance_podstr.json"

MAX_PER_STRONG = 800


def normalize_strong(code: str) -> str | None:
    t = code.strip().upper()
    if not t:
        return None
    m = re.match(r"^([GH])(\d{1,5})$", t)
    if not m:
        return None
    prefix, num = m.group(1), m.group(2)
    n = int(num)
    return f"{prefix}{n:04d}"


def main() -> None:
    index: dict[str, list[dict]] = {}
    files = sorted(PODSTR.glob("*.json"))
    if not files:
        raise SystemExit(f"No JSON in {PODSTR}")

    for path in files:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        book = data["book"]
        book_id = book["id"]
        for ch in book["chapters"]:
            cnum = ch["number"]
            for verse in ch["verses"]:
                vnum = verse["number"]
                for w in verse.get("words") or []:
                    raw = w.get("s")
                    if not raw:
                        continue
                    key = normalize_strong(str(raw))
                    if not key:
                        continue
                    lst = index.setdefault(key, [])
                    if len(lst) >= MAX_PER_STRONG:
                        continue
                    lst.append({"b": book_id, "c": cnum, "v": vnum})

    # Сортировка по канону уже обеспечена порядком обхода файлов и стихов
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, separators=(",", ":"))

    total_keys = len(index)
    total_refs = sum(len(v) for v in index.values())
    print(f"Wrote {OUT}")
    print(f"Strong numbers: {total_keys}, total verse refs: {total_refs}")


if __name__ == "__main__":
    main()
