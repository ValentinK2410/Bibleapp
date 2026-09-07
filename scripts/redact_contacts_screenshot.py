#!/usr/bin/env python3
"""Замазывает личные данные на скриншоте экрана «Контакты» для README."""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageFilter


def redact_contacts_screenshot(path: Path) -> None:
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    # Область списка карточек: ниже поясняющего текста, выше FAB.
    y0 = int(h * 0.187)
    y1 = int(h * 0.96)
    x0, x1 = int(w * 0.022), int(w * 0.978)
    region = im.crop((x0, y0, x1, y1))
    rw, rh = region.size
    blocks = 12
    small = region.resize((max(1, rw // blocks), max(1, rh // blocks)), Image.Resampling.NEAREST)
    pixelated = small.resize((rw, rh), Image.Resampling.NEAREST)
    blurred = pixelated.filter(ImageFilter.GaussianBlur(radius=18))
    im.paste(blurred, (x0, y0))
    im.save(path, "PNG")


if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("docs/screenshots/29-contacts.png")
    redact_contacts_screenshot(target.resolve())
    print(f"Замазано: {target}")
