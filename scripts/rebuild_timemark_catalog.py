#!/usr/bin/env python3
"""Rebuild docs/timemarks/catalog.json from project JSON files."""

from __future__ import annotations

import json
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIR = ROOT / "docs" / "timemarks"
PROJECTS = DIR / "projects"
CATALOG = DIR / "catalog.json"


def main() -> None:
    items = []
    for path in sorted(PROJECTS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        book_id = str(data.get("bookId", "")).strip()
        translation = str(data.get("translationCode", "")).strip()
        chapter = int(data.get("chapter") or 0)
        cues = data.get("cues") or []
        if not book_id or not translation or chapter <= 0 or not cues:
            continue
        item_id = str(data.get("id") or "").strip() or f"gh_{translation.lower()}_{book_id.lower()}_{chapter}"
        items.append(
            {
                "id": item_id,
                "translationCode": translation,
                "bookId": book_id,
                "chapter": chapter,
                "title": str(data.get("title") or "").strip(),
                "cueCount": len(cues),
                "narratorId": str(data.get("narratorId") or "").strip(),
                "file": f"projects/{path.name}",
            }
        )
        if not items[-1]["narratorId"]:
            items[-1].pop("narratorId")
        if not items[-1]["title"]:
            items[-1].pop("title")

    catalog = {
        "format": "bible_timemark_catalog",
        "version": 1,
        "updatedAt": date.today().isoformat(),
        "items": items,
    }
    CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {CATALOG} ({len(items)} items)")


if __name__ == "__main__":
    main()
