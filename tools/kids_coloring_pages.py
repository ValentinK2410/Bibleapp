#!/usr/bin/env python3
"""Генератор раскрасок раздела «Детям».

Каждая страница выпускается в трёх файлах формата `kids-coloring/v1`:
  kids_coloring_<id>_small.svg    — крупные области для 3–5 лет;
  kids_coloring_<id>_detailed.svg — подробный рисунок для школьников;
  kids_coloring_<id>.json         — то, что читает приложение (оба уровня).

Координаты — абсолютные, в квадрате 800×800. Регионы идут от фона к переднему
плану: приложение рисует их по порядку, а тап проверяет с конца списка.

Запуск:
    python3 tools/kids_coloring_pages.py            # записать файлы
    python3 tools/kids_coloring_pages.py --check    # только отчёт и валидация
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys

SIZE = 800
STROKE_COLOR = "#1F2937"
STROKE_WIDTH = 4
NOTE = (
    "Regions are painted in list order (first = background). Hit-test from last to first: "
    "the topmost region containing the tap wins. Path data is standard SVG path syntax "
    "(M L H V C Q A Z), absolute coordinates in the 800x800 viewBox; use "
    "androidx.core.graphics.PathParser.createPathFromPathData(d) and scale by canvasSize/800."
)

ASSETS_DIR = os.path.join("app", "src", "main", "assets", "kids", "coloring")

# Палитра приложения (KidsColoringPages.palette) — подсказки берём только из неё,
# чтобы ребёнок всегда мог повторить образец цветом из набора.
SKY_BLUE = "#7DD3FC"
DEEP_BLUE = "#1E3A8A"
MID_BLUE = "#2563EB"
INDIGO = "#312E81"
TEAL = "#14B8A6"
SEA = "#38BDF8"
YELLOW = "#FACC15"
GOLD = "#CA8A04"
AMBER = "#FBBF24"
CREAM = "#FDE68A"
ORANGE = "#F97316"
PEACH = "#FB923C"
RED = "#EF4444"
CRIMSON = "#B91C1C"
LIME = "#A3E635"
GREEN = "#22C55E"
FOREST = "#15803D"
VIOLET = "#8B5CF6"
LILAC = "#C084FC"
PINK = "#F472B6"
RASPBERRY = "#BE185D"
BROWN = "#92400E"
DARK_BROWN = "#78350F"
TAN = "#B45309"
BEIGE = "#F5DEB3"
GREY = "#9CA3AF"
SLATE = "#94A3B8"
LIGHT_SLATE = "#CBD5E1"
PAPER = "#FFFBF5"
INK = "#1F2937"


def n(value: float) -> str:
    """Компактная запись числа: целые без точки, иначе один знак."""
    rounded = round(float(value), 1)
    if rounded == 0:
        return "0"
    if rounded == int(rounded):
        return str(int(rounded))
    return f"{rounded:.1f}"


# ---------------------------------------------------------------------------
# Примитивы пути
# ---------------------------------------------------------------------------

def poly(*points: tuple[float, float]) -> str:
    parts = [f"M{n(points[0][0])},{n(points[0][1])}"]
    parts += [f"L{n(x)},{n(y)}" for x, y in points[1:]]
    parts.append("Z")
    return " ".join(parts)


def rect(x: float, y: float, w: float, h: float) -> str:
    return poly((x, y), (x + w, y), (x + w, y + h), (x, y + h))


def rrect(x: float, y: float, w: float, h: float, r: float) -> str:
    r = max(0.0, min(r, w / 2.0, h / 2.0))
    if r == 0:
        return rect(x, y, w, h)
    return (
        f"M{n(x + r)},{n(y)} H{n(x + w - r)} A{n(r)},{n(r)} 0 0 1 {n(x + w)},{n(y + r)} "
        f"V{n(y + h - r)} A{n(r)},{n(r)} 0 0 1 {n(x + w - r)},{n(y + h)} "
        f"H{n(x + r)} A{n(r)},{n(r)} 0 0 1 {n(x)},{n(y + h - r)} "
        f"V{n(y + r)} A{n(r)},{n(r)} 0 0 1 {n(x + r)},{n(y)} Z"
    )


def circle(cx: float, cy: float, r: float) -> str:
    return (
        f"M{n(cx - r)},{n(cy)} A{n(r)},{n(r)} 0 1 1 {n(cx + r)},{n(cy)} "
        f"A{n(r)},{n(r)} 0 1 1 {n(cx - r)},{n(cy)} Z"
    )


def ellipse(cx: float, cy: float, rx: float, ry: float, rot: float = 0.0) -> str:
    if rx <= 0 or ry <= 0:
        return ""
    rad = math.radians(rot)
    dx = rx * math.cos(rad)
    dy = rx * math.sin(rad)
    return (
        f"M{n(cx - dx)},{n(cy - dy)} A{n(rx)},{n(ry)} {n(rot)} 1 1 {n(cx + dx)},{n(cy + dy)} "
        f"A{n(rx)},{n(ry)} {n(rot)} 1 1 {n(cx - dx)},{n(cy - dy)} Z"
    )


def star(cx: float, cy: float, r_out: float, r_in: float, points: int = 5, rot: float = -90.0) -> str:
    verts = []
    for index in range(points * 2):
        radius = r_out if index % 2 == 0 else r_in
        angle = math.radians(rot + index * 180.0 / points)
        verts.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return poly(*verts)


def arc_band(cx: float, cy: float, r_out: float, r_in: float, a0: float, a1: float) -> str:
    """Кольцевой сектор между углами a0 и a1 (градусы, y вниз)."""
    def point(radius: float, angle: float) -> tuple[float, float]:
        rad = math.radians(angle)
        return cx + radius * math.cos(rad), cy + radius * math.sin(rad)

    p0o, p1o = point(r_out, a0), point(r_out, a1)
    p1i, p0i = point(r_in, a1), point(r_in, a0)
    large = 1 if abs(a1 - a0) > 180 else 0
    sweep = 1 if a1 > a0 else 0
    return (
        f"M{n(p0o[0])},{n(p0o[1])} A{n(r_out)},{n(r_out)} 0 {large} {sweep} {n(p1o[0])},{n(p1o[1])} "
        f"L{n(p1i[0])},{n(p1i[1])} A{n(r_in)},{n(r_in)} 0 {large} {1 - sweep} {n(p0i[0])},{n(p0i[1])} Z"
    )


def wave_band(y_top: float, amp: float, seg: float, y_bottom: float = SIZE) -> str:
    """Полоса с волнистым верхом: вода, песок, трава."""
    parts = [f"M0,{n(y_bottom)}", f"L0,{n(y_top)}"]
    x = 0.0
    crest = True
    while x < SIZE - 1e-6:
        nxt = min(x + seg, SIZE)
        ctrl_y = y_top - amp if crest else y_top + amp
        parts.append(f"Q{n((x + nxt) / 2)},{n(ctrl_y)} {n(nxt)},{n(y_top)}")
        x = nxt
        crest = not crest
    parts.append(f"L{SIZE},{n(y_bottom)}")
    parts.append("Z")
    return " ".join(parts)


def hill(cx: float, base_y: float, w: float, h: float) -> str:
    return f"M{n(cx - w / 2)},{n(base_y)} Q{n(cx)},{n(base_y - 2 * h)} {n(cx + w / 2)},{n(base_y)} Z"


def cloud(cx: float, cy: float, scale: float = 1.0) -> str:
    w, h = 150 * scale, 46 * scale
    left, bottom = cx - w / 2, cy + h / 2
    r1 = r3 = h * 0.40
    r2 = h * 0.56
    lift = h * 0.42
    return (
        f"M{n(left)},{n(bottom)} "
        f"A{n(r1)},{n(r1)} 0 0 1 {n(left + w * 0.32)},{n(bottom - lift)} "
        f"A{n(r2)},{n(r2)} 0 0 1 {n(left + w * 0.70)},{n(bottom - lift)} "
        f"A{n(r3)},{n(r3)} 0 0 1 {n(left + w)},{n(bottom)} Z"
    )


def sun_rays(prefix: str, cx: float, cy: float, r_in: float, r_out: float, count: int = 6) -> list[tuple[str, str, str]]:
    """Треугольные лучи вокруг солнца."""
    out = []
    for index in range(count):
        angle = math.radians(index * 360.0 / count - 90)
        tip = (cx + r_out * math.cos(angle), cy + r_out * math.sin(angle))
        half = math.radians(9)
        base_a = (cx + r_in * math.cos(angle + half), cy + r_in * math.sin(angle + half))
        base_b = (cx + r_in * math.cos(angle - half), cy + r_in * math.sin(angle - half))
        out.append((f"{prefix}{index + 1}", AMBER, poly(base_a, tip, base_b)))
    return out


# ---------------------------------------------------------------------------
# Составные элементы (возвращают списки регионов)
# ---------------------------------------------------------------------------

def star_scatter(prefix: str, spots: list[tuple[float, float, float]], colors: list[str]) -> list[tuple[str, str, str]]:
    out = []
    for index, (cx, cy, radius) in enumerate(spots):
        hint = colors[index % len(colors)]
        out.append((f"{prefix}{index + 1}", hint, star(cx, cy, radius, radius * 0.45)))
    return out


def figure(prefix: str, cx: float, ground_y: float, height: float, robe: str, skin: str,
           hair: str = INK, sash: str | None = None) -> list[tuple[str, str, str]]:
    """Фигурка человека: подол, пояс, две руки, голова, волосы."""
    head_r = height * 0.13
    head_cy = ground_y - height + head_r
    robe_top = head_cy + head_r * 1.25
    hem_w = height * 0.46
    shoulder_w = height * 0.30
    out = [
        (f"{prefix}robe", robe, poly(
            (cx - shoulder_w / 2, robe_top),
            (cx + shoulder_w / 2, robe_top),
            (cx + hem_w / 2, ground_y),
            (cx - hem_w / 2, ground_y),
        )),
    ]
    if sash:
        band_h = height * 0.07
        band_y = robe_top + (ground_y - robe_top) * 0.42
        width_at = shoulder_w + (hem_w - shoulder_w) * 0.42
        out.append((f"{prefix}sash", sash, rect(cx - width_at / 2, band_y, width_at, band_h)))
    out += [
        (f"{prefix}arm_l", robe, rrect(cx - shoulder_w / 2 - height * 0.09, robe_top + height * 0.04,
                                       height * 0.10, height * 0.44, height * 0.05)),
        (f"{prefix}arm_r", robe, rrect(cx + shoulder_w / 2 - height * 0.01, robe_top + height * 0.04,
                                       height * 0.10, height * 0.44, height * 0.05)),
        (f"{prefix}head", skin, circle(cx, head_cy, head_r)),
        (f"{prefix}hair", hair, arc_band(cx, head_cy, head_r * 1.06, head_r * 0.62, 180, 360)),
    ]
    return out


def sheep(prefix: str, cx: float, cy: float, scale: float = 1.0) -> list[tuple[str, str, str]]:
    body_rx, body_ry = 42 * scale, 28 * scale
    return [
        (f"{prefix}body", PAPER, ellipse(cx, cy, body_rx, body_ry)),
        (f"{prefix}fluff", LIGHT_SLATE, circle(cx + body_rx * 0.15, cy - body_ry * 0.9, 11 * scale)),
        (f"{prefix}head", GREY, circle(cx + body_rx * 0.85, cy - body_ry * 0.2, 15 * scale)),
        (f"{prefix}legs", INK, rrect(cx - body_rx * 0.4, cy + body_ry * 0.55,
                                     body_rx * 0.7, 26 * scale, 6 * scale)),
    ]


def palm(prefix: str, cx: float, ground_y: float, scale: float = 1.0) -> list[tuple[str, str, str]]:
    trunk_h = 150 * scale
    top_y = ground_y - trunk_h
    leaves = []
    for index, angle in enumerate((-150, -90, -30)):
        rad = math.radians(angle)
        tip = (cx + 95 * scale * math.cos(rad), top_y + 95 * scale * math.sin(rad))
        mid = (cx + 55 * scale * math.cos(rad - 0.34), top_y + 55 * scale * math.sin(rad - 0.34))
        mid2 = (cx + 55 * scale * math.cos(rad + 0.34), top_y + 55 * scale * math.sin(rad + 0.34))
        leaves.append((f"{prefix}leaf{index + 1}", FOREST, poly((cx, top_y), mid, tip, mid2)))
    return [
        (f"{prefix}trunk", TAN, poly(
            (cx - 11 * scale, ground_y), (cx + 11 * scale, ground_y),
            (cx + 7 * scale, top_y), (cx - 7 * scale, top_y),
        )),
        *leaves,
        (f"{prefix}nut", BROWN, circle(cx, top_y + 20 * scale, 10 * scale)),
    ]


def campfire(prefix: str, cx: float, ground_y: float, scale: float = 1.0) -> list[tuple[str, str, str]]:
    log_w = 78 * scale
    return [
        (f"{prefix}log1", DARK_BROWN, rrect(cx - log_w / 2, ground_y - 16 * scale, log_w, 14 * scale, 6 * scale)),
        (f"{prefix}log2", TAN, rrect(cx - log_w * 0.34, ground_y - 30 * scale, log_w * 0.72, 13 * scale, 6 * scale)),
        (f"{prefix}flame", ORANGE, poly(
            (cx, ground_y - 118 * scale),
            (cx + 30 * scale, ground_y - 58 * scale),
            (cx + 18 * scale, ground_y - 26 * scale),
            (cx - 18 * scale, ground_y - 26 * scale),
            (cx - 30 * scale, ground_y - 58 * scale),
        )),
        (f"{prefix}flame_in", YELLOW, poly(
            (cx, ground_y - 82 * scale),
            (cx + 15 * scale, ground_y - 46 * scale),
            (cx - 15 * scale, ground_y - 46 * scale),
        )),
    ]


def wheat(prefix: str, cx: float, ground_y: float, scale: float = 1.0, lean: float = 0.0) -> list[tuple[str, str, str]]:
    stalk_h = 120 * scale
    top_y = ground_y - stalk_h
    grains = []
    for index in range(3):
        offset = index * 24 * scale
        grains.append((f"{prefix}grain{index * 2 + 1}", GOLD,
                       ellipse(cx + lean * offset * 0.4 - 9 * scale, top_y + offset, 11 * scale, 7 * scale, -25 + lean)))
        grains.append((f"{prefix}grain{index * 2 + 2}", GOLD,
                       ellipse(cx + lean * offset * 0.4 + 9 * scale, top_y + offset + 9 * scale, 11 * scale, 7 * scale, 25 + lean)))
    return [
        (f"{prefix}stalk", GREEN, poly(
            (cx - 4 * scale, ground_y), (cx + 4 * scale, ground_y),
            (cx + 3 * scale + lean * stalk_h * 0.15, top_y), (cx - 3 * scale + lean * stalk_h * 0.15, top_y),
        )),
        *grains[:4],
    ]


def fish(prefix: str, cx: float, cy: float, scale: float = 1.0, body: str = ORANGE, flip: bool = False) -> list[tuple[str, str, str]]:
    length, thick = 62 * scale, 26 * scale
    direction = -1 if flip else 1
    nose = cx + direction * length / 2
    tail = cx - direction * length / 2
    return [
        (f"{prefix}body", body, ellipse(cx, cy, length / 2, thick / 2)),
        (f"{prefix}tail", body, poly(
            (tail, cy),
            (tail - direction * 22 * scale, cy - 18 * scale),
            (tail - direction * 22 * scale, cy + 18 * scale),
        )),
        (f"{prefix}fin", GOLD, poly(
            (cx - direction * 4 * scale, cy - thick / 2),
            (cx + direction * 12 * scale, cy - thick / 2 - 16 * scale),
            (cx + direction * 16 * scale, cy - thick / 2 + 2 * scale),
        )),
        (f"{prefix}eye", INK, circle(nose - direction * 16 * scale, cy - 5 * scale, 4 * scale)),
    ]


def basket(prefix: str, cx: float, base_y: float, scale: float = 1.0) -> list[tuple[str, str, str]]:
    top_w, bot_w, h = 150 * scale, 104 * scale, 96 * scale
    top_y = base_y - h
    bands = []
    for index in range(3):
        t0, t1 = index / 3.0, (index + 1) / 3.0
        def edge(t: float) -> tuple[float, float]:
            width = top_w + (bot_w - top_w) * t
            return cx - width / 2, cx + width / 2
        l0, r0 = edge(t0)
        l1, r1 = edge(t1)
        bands.append((f"{prefix}band{index + 1}", TAN if index % 2 == 0 else BROWN, poly(
            (l0, top_y + h * t0), (r0, top_y + h * t0), (r1, top_y + h * t1), (l1, top_y + h * t1),
        )))
    return [
        *bands,
        (f"{prefix}rim", DARK_BROWN, rrect(cx - top_w / 2 - 6 * scale, top_y - 12 * scale,
                                           top_w + 12 * scale, 18 * scale, 8 * scale)),
        (f"{prefix}handle", DARK_BROWN, arc_band(cx, top_y - 12 * scale, 58 * scale, 46 * scale, 180, 360)),
    ]


def building(prefix: str, x: float, base_y: float, w: float, h: float, wall: str, roof: str) -> list[tuple[str, str, str]]:
    top_y = base_y - h
    return [
        (f"{prefix}wall", wall, rect(x, top_y, w, h)),
        (f"{prefix}roof", roof, poly((x - 8, top_y), (x + w / 2, top_y - h * 0.34), (x + w + 8, top_y))),
        (f"{prefix}door", DARK_BROWN, rrect(x + w / 2 - 13, base_y - h * 0.46, 26, h * 0.46, 12)),
        (f"{prefix}window", SKY_BLUE, rrect(x + 12, top_y + h * 0.2, 20, 22, 5)),
    ]


def lion(prefix: str, cx: float, cy: float, scale: float = 1.0) -> list[tuple[str, str, str]]:
    body_rx, body_ry = 58 * scale, 32 * scale
    head_r = 26 * scale
    mane_r = 40 * scale
    hx = cx + body_rx * 0.82
    hy = cy - body_ry * 0.35
    return [
        (f"{prefix}tail", TAN, poly(
            (cx - body_rx, cy),
            (cx - body_rx - 40 * scale, cy - 34 * scale),
            (cx - body_rx - 28 * scale, cy - 40 * scale),
            (cx - body_rx + 6 * scale, cy - 8 * scale),
        )),
        (f"{prefix}body", TAN, ellipse(cx, cy, body_rx, body_ry)),
        (f"{prefix}legs", TAN, rrect(cx - body_rx * 0.5, cy + body_ry * 0.5,
                                     body_rx * 0.95, 34 * scale, 10 * scale)),
        (f"{prefix}mane", BROWN, circle(hx, hy, mane_r)),
        (f"{prefix}head", AMBER, circle(hx, hy, head_r)),
        (f"{prefix}muzzle", BEIGE, ellipse(hx, hy + head_r * 0.34, head_r * 0.56, head_r * 0.4)),
        (f"{prefix}nose", INK, poly(
            (hx - 6 * scale, hy + head_r * 0.18),
            (hx + 6 * scale, hy + head_r * 0.18),
            (hx, hy + head_r * 0.4),
        )),
        (f"{prefix}eye1", INK, circle(hx - head_r * 0.36, hy - head_r * 0.18, 4 * scale)),
        (f"{prefix}eye2", INK, circle(hx + head_r * 0.36, hy - head_r * 0.18, 4 * scale)),
    ]


def angel(prefix: str, cx: float, ground_y: float, scale: float = 1.0) -> list[tuple[str, str, str]]:
    height = 190 * scale
    base = figure(prefix, cx, ground_y, height, PAPER, BEIGE, hair=AMBER)
    head_cy = ground_y - height + height * 0.13
    wings = [
        (f"{prefix}wing_l", LIGHT_SLATE, poly(
            (cx - height * 0.10, ground_y - height * 0.62),
            (cx - height * 0.52, ground_y - height * 0.86),
            (cx - height * 0.44, ground_y - height * 0.40),
            (cx - height * 0.16, ground_y - height * 0.34),
        )),
        (f"{prefix}wing_r", LIGHT_SLATE, poly(
            (cx + height * 0.10, ground_y - height * 0.62),
            (cx + height * 0.52, ground_y - height * 0.86),
            (cx + height * 0.44, ground_y - height * 0.40),
            (cx + height * 0.16, ground_y - height * 0.34),
        )),
        (f"{prefix}halo", AMBER, arc_band(cx, head_cy - height * 0.1365, 26 * scale, 19 * scale, 180, 360)),
    ]
    return wings + base


def coat_stripes(prefix: str, cx: float, top_y: float, bot_y: float, top_w: float, bot_w: float,
                 colors: list[str]) -> list[tuple[str, str, str]]:
    """Разноцветная одежда Иосифа: вертикальные полосы внутри трапеции."""
    out = []
    count = len(colors)
    for index in range(count):
        t0, t1 = index / count, (index + 1) / count
        x0t = cx - top_w / 2 + top_w * t0
        x1t = cx - top_w / 2 + top_w * t1
        x0b = cx - bot_w / 2 + bot_w * t0
        x1b = cx - bot_w / 2 + bot_w * t1
        out.append((f"{prefix}{index + 1}", colors[index], poly((x0t, top_y), (x1t, top_y), (x1b, bot_y), (x0b, bot_y))))
    return out


# ---------------------------------------------------------------------------
# Сцены
# ---------------------------------------------------------------------------

def scene_abraham() -> tuple[list, list]:
    small = [
        ("sky", INDIGO, rect(0, 0, 800, 800)),
        ("moon", YELLOW, circle(140, 130, 54)),
        ("big_star", YELLOW, star(430, 165, 66, 28)),
        ("hill", DEEP_BLUE, hill(620, 620, 560, 130)),
        ("ground", DARK_BROWN, wave_band(620, 14, 100)),
        ("tent", TAN, poly((470, 620), (620, 420), (770, 620))),
        ("tent_door", DARK_BROWN, poly((585, 620), (620, 520), (655, 620))),
        ("abraham_robe", VIOLET, poly((250, 420), (350, 420), (380, 640), (220, 640))),
        ("abraham_head", BEIGE, circle(300, 385, 34)),
        ("sheep_body", PAPER, ellipse(150, 655, 48, 32)),
    ]
    detailed = [
        ("sky", INDIGO, rect(0, 0, 800, 800)),
        ("moon", YELLOW, circle(132, 126, 54)),
        ("moon_crater1", GOLD, circle(118, 112, 13)),
        ("moon_crater2", GOLD, circle(152, 142, 9)),
        *star_scatter("star", [
            (300, 90, 16), (500, 108, 18), (612, 66, 13), (700, 128, 16),
            (240, 190, 12), (90, 258, 14), (470, 258, 12), (596, 300, 14),
        ], [PAPER, YELLOW, CREAM]),
        ("big_star", YELLOW, star(430, 165, 68, 29)),
        ("big_star_core", CREAM, star(430, 165, 32, 14)),
        ("hill_far", DEEP_BLUE, hill(250, 620, 700, 150)),
        ("hill_near", MID_BLUE, hill(640, 620, 520, 120)),
        ("ground", DARK_BROWN, wave_band(618, 14, 100)),
        ("ripple1", TAN, ellipse(180, 742, 62, 7)),
        ("ripple2", TAN, ellipse(430, 764, 78, 7)),
        ("stone1", SLATE, ellipse(96, 700, 30, 17)),
        ("tent_main", TAN, poly((456, 622), (618, 404), (780, 622))),
        ("tent_side", BROWN, poly((618, 404), (780, 622), (700, 622))),
        ("tent_stripe", CREAM, poly((556, 486), (600, 428), (644, 486), (620, 622), (580, 622))),
        ("tent_door", DARK_BROWN, poly((580, 622), (618, 512), (656, 622))),
        ("tent_pole", DARK_BROWN, rrect(612, 372, 12, 44, 5)),
        ("tent_flag", RED, poly((624, 376), (686, 392), (624, 408))),
        *figure("abraham_", 296, 646, 250, VIOLET, BEIGE, hair=LIGHT_SLATE, sash=YELLOW),
        ("abraham_beard", LIGHT_SLATE, ellipse(296, 462, 20, 16)),
        ("abraham_staff", TAN, rrect(372, 420, 11, 232, 5)),
        ("sandal1", BROWN, ellipse(272, 652, 22, 9)),
        ("sandal2", BROWN, ellipse(322, 652, 22, 9)),
        *campfire("fire_", 152, 700, 0.9),
        *sheep("sheep1_", 560, 690, 0.95),
        *sheep("sheep2_", 68, 762, 0.8),
        *palm("palm_", 726, 622, 0.72),
    ]
    return small, detailed


def scene_joseph() -> tuple[list, list]:
    coat_colors = [RED, ORANGE, YELLOW, GREEN, SEA, VIOLET, PINK]
    small = [
        ("sky", SKY_BLUE, rect(0, 0, 800, 800)),
        ("sun", YELLOW, circle(660, 130, 62)),
        ("ground", LIME, wave_band(600, 16, 100)),
        ("coat", coat_colors[0], poly((330, 330), (470, 330), (520, 640), (280, 640))),
        ("coat_band", YELLOW, rect(312, 470, 176, 30)),
        ("joseph_head", BEIGE, circle(400, 288, 44)),
        ("joseph_hair", INK, arc_band(400, 288, 48, 28, 180, 360)),
        ("brother_robe", TEAL, poly((600, 420), (700, 420), (730, 660), (570, 660))),
        ("brother_head", BEIGE, circle(650, 382, 34)),
        ("wheat", GOLD, ellipse(150, 560, 40, 90)),
    ]
    detailed = [
        ("sky", SKY_BLUE, rect(0, 0, 800, 800)),
        ("sun", YELLOW, circle(664, 124, 60)),
        ("sun_core", CREAM, circle(664, 124, 34)),
        *sun_rays("ray_", 664, 124, 68, 104),
        ("cloud1", PAPER, cloud(180, 120, 1.0)),
        ("bird1", INK, poly((300, 190), (326, 168), (352, 190), (326, 178))),
        ("hill_far", GREEN, hill(180, 600, 620, 130)),
        ("ground", LIME, wave_band(598, 16, 100)),
        ("path", BEIGE, poly((352, 800), (452, 800), (420, 600), (386, 600))),
        *coat_stripes("coat_", 400, 336, 646, 140, 250, coat_colors),
        ("coat_collar", CREAM, poly((348, 330), (452, 330), (430, 366), (400, 348), (370, 366))),
        ("coat_sleeve_l", coat_colors[3], poly((330, 344), (286, 366), (262, 520), (306, 528))),
        ("coat_sleeve_r", coat_colors[5], poly((470, 344), (514, 366), (538, 520), (494, 528))),
        ("belt", BROWN, rrect(306, 468, 190, 26, 10)),
        ("belt_gem", AMBER, circle(400, 481, 15)),
        ("joseph_head", BEIGE, circle(400, 286, 44)),
        ("joseph_hair", INK, arc_band(400, 286, 48, 27, 180, 360)),
        ("turban", CREAM, arc_band(400, 268, 52, 30, 180, 360)),
        ("turban_gem", RED, circle(400, 246, 11)),
        ("eye_l", INK, circle(384, 288, 5)),
        ("eye_r", INK, circle(416, 288, 5)),
        ("smile", INK, arc_band(400, 300, 16, 11, 20, 160)),
        ("sandal1", BROWN, ellipse(364, 654, 24, 10)),
        ("sandal2", BROWN, ellipse(438, 654, 24, 10)),
        *figure("bro1_", 636, 664, 240, TEAL, BEIGE, sash=CRIMSON),
        *wheat("wheat1_", 748, 700, 1.0, lean=0.4),
        *wheat("wheat2_", 250, 700, 0.94, lean=-0.3),
        ("flower1", PINK, circle(330, 742, 13)),
        ("flower2", VIOLET, circle(540, 748, 13)),
        *sheep("sheep_", 96, 712, 0.78),
    ]
    return small, detailed


def scene_moses() -> tuple[list, list]:
    small = [
        ("sky", SKY_BLUE, rect(0, 0, 800, 800)),
        ("pillar", LIGHT_SLATE, cloud(400, 130, 1.1)),
        ("sea_left", MID_BLUE, poly((0, 240), (250, 300), (250, 800), (0, 800))),
        ("sea_right", MID_BLUE, poly((800, 240), (550, 300), (550, 800), (800, 800))),
        ("dry_path", BEIGE, poly((300, 300), (500, 300), (560, 800), (240, 800))),
        ("foam_left", PAPER, ellipse(252, 470, 40, 18, -14)),
        ("foam_right", PAPER, ellipse(548, 470, 40, 18, 14)),
        ("moses_robe", CRIMSON, poly((350, 400), (450, 400), (480, 620), (320, 620))),
        ("moses_head", BEIGE, circle(400, 360, 36)),
        ("staff", TAN, rrect(492, 330, 12, 300, 5)),
    ]
    detailed = [
        ("sky", SKY_BLUE, rect(0, 0, 800, 800)),
        ("sun", YELLOW, circle(690, 96, 46)),
        ("cloud1", PAPER, cloud(150, 110, 0.8)),
        ("pillar1", LIGHT_SLATE, cloud(400, 150, 1.05)),
        ("pillar2", PAPER, cloud(400, 214, 0.9)),
        ("pillar3", LIGHT_SLATE, cloud(400, 274, 0.78)),
        ("sea_wall_left1", MID_BLUE, poly((0, 210), (60, 190), (150, 250), (215, 320), (255, 460), (255, 800), (0, 800))),
        ("sea_wall_left2", DEEP_BLUE, poly((0, 330), (110, 360), (190, 470), (220, 620), (220, 800), (0, 800))),
        ("sea_wall_left3", SEA, poly((0, 470), (90, 520), (150, 640), (165, 800), (0, 800))),
        ("sea_wall_right1", MID_BLUE, poly((800, 210), (740, 190), (650, 250), (585, 320), (545, 460), (545, 800), (800, 800))),
        ("sea_wall_right2", DEEP_BLUE, poly((800, 330), (690, 360), (610, 470), (580, 620), (580, 800), (800, 800))),
        ("sea_wall_right3", SEA, poly((800, 470), (710, 520), (650, 640), (635, 800), (800, 800))),
        ("foam_l1", PAPER, ellipse(196, 300, 44, 16, -28)),
        ("foam_l2", PAPER, ellipse(232, 420, 40, 15, -18)),
        ("foam_r1", PAPER, ellipse(604, 300, 44, 16, 28)),
        ("foam_r2", PAPER, ellipse(568, 420, 40, 15, 18)),
        ("fish_l", ORANGE, ellipse(96, 400, 34, 18)),
        ("fish_l_tail", ORANGE, poly((62, 400), (34, 380), (34, 420))),
        ("fish_r", YELLOW, ellipse(706, 424, 34, 18)),
        ("fish_r_tail", YELLOW, poly((740, 424), (768, 404), (768, 444))),
        ("seaweed1", FOREST, poly((120, 800), (136, 800), (150, 690), (126, 682))),
        ("dry_path", BEIGE, poly((280, 462), (520, 462), (548, 800), (252, 800))),
        ("dry_path_shade", TAN, poly((280, 462), (320, 462), (292, 800), (252, 800))),
        ("stone1", SLATE, ellipse(330, 726, 30, 16)),
        ("stone2", SLATE, ellipse(470, 762, 26, 14)),
        ("shell1", PINK, circle(438, 700, 12)),
        *figure("moses_", 400, 632, 250, CRIMSON, BEIGE, hair=LIGHT_SLATE, sash=GOLD),
        ("moses_beard", LIGHT_SLATE, ellipse(400, 452, 20, 16)),
        ("moses_arm", CRIMSON, poly((446, 452), (512, 392), (524, 410), (458, 476))),
        ("staff", TAN, rrect(506, 316, 13, 316, 6)),
        ("staff_top", GOLD, circle(512, 316, 14)),
        ("sandal1", BROWN, ellipse(376, 638, 22, 9)),
        ("sandal2", BROWN, ellipse(424, 638, 22, 9)),
        *figure("person1_", 320, 786, 210, VIOLET, BEIGE, hair=BROWN),
        *figure("person2_", 494, 790, 190, TEAL, BEIGE, hair=INK),
        ("jar", TAN, ellipse(296, 776, 26, 34)),
        ("jar_neck", TAN, rrect(286, 736, 20, 18, 5)),
    ]
    return small, detailed


def scene_daniel() -> tuple[list, list]:
    small = [
        ("night", INDIGO, rect(0, 0, 800, 800)),
        ("window", YELLOW, arc_band(400, 300, 150, 0.001, 180, 360)),
        ("window_base", YELLOW, rect(250, 300, 300, 130)),
        ("floor", BROWN, rect(0, 620, 800, 180)),
        ("daniel_robe", TEAL, poly((350, 400), (450, 400), (480, 640), (320, 640))),
        ("daniel_head", BEIGE, circle(400, 360, 36)),
        ("lion_body", TAN, ellipse(620, 660, 76, 44)),
        ("lion_head", AMBER, circle(690, 626, 40)),
        ("angel_robe", PAPER, poly((110, 380), (200, 380), (230, 640), (80, 640))),
        ("angel_wing", LIGHT_SLATE, poly((200, 430), (280, 360), (260, 500))),
    ]
    detailed = [
        ("night", INDIGO, rect(0, 0, 800, 800)),
        ("moon", CREAM, circle(96, 96, 42)),
        *star_scatter("star", [
            (200, 70, 13), (300, 118, 10), (520, 66, 14), (628, 110, 11), (716, 62, 13),
        ], [PAPER, YELLOW, CREAM]),
        ("wall_top", SLATE, rect(0, 250, 800, 90)),
        ("brick1", GREY, rrect(20, 262, 92, 34, 6)),
        ("brick2", GREY, rrect(128, 262, 92, 34, 6)),
        ("brick3", GREY, rrect(688, 262, 92, 34, 6)),
        ("window_arch", YELLOW, arc_band(400, 250, 132, 0.001, 180, 360)),
        ("window_frame", TAN, rrect(268, 250, 264, 92, 10)),
        ("window_pane", YELLOW, rect(284, 262, 232, 68)),
        ("bar1", BROWN, rrect(330, 250, 14, 92, 4)),
        ("bar2", BROWN, rrect(456, 250, 14, 92, 4)),
        ("ray1", CREAM, poly((330, 342), (392, 342), (300, 620), (180, 620))),
        ("ray2", CREAM, poly((408, 342), (470, 342), (620, 620), (500, 620))),
        ("floor", BROWN, rect(0, 620, 800, 180)),
        ("floor_band", TAN, rect(0, 620, 800, 26)),
        ("floor_tile1", DARK_BROWN, rrect(40, 676, 130, 22, 6)),
        ("straw1", GOLD, poly((120, 762), (230, 748), (232, 760), (122, 774))),
        ("stone1", SLATE, ellipse(716, 640, 40, 24)),
        ("stone2", SLATE, ellipse(66, 636, 34, 21)),
        *figure("daniel_", 400, 664, 260, TEAL, BEIGE, hair=INK, sash=GOLD),
        ("daniel_hands", BEIGE, poly((368, 470), (432, 470), (424, 502), (376, 502))),
        ("daniel_eye1", INK, circle(386, 434, 5)),
        ("daniel_eye2", INK, circle(414, 434, 5)),
        *angel("angel_", 150, 640, 1.0),
        *lion("lion1_", 620, 640, 1.0),
        *lion("lion2_", 250, 700, 0.7),
        ("bone1", PAPER, rrect(330, 770, 74, 12, 6)),
    ]
    return small, detailed


def scene_jonah() -> tuple[list, list]:
    small = [
        ("sky", SKY_BLUE, rect(0, 0, 800, 800)),
        ("sun", YELLOW, circle(120, 120, 56)),
        ("sea", MID_BLUE, wave_band(430, 18, 90)),
        ("boat", TAN, poly((90, 370), (280, 370), (240, 440), (130, 440))),
        ("sail", CREAM, poly((185, 370), (185, 220), (280, 370))),
        ("fish_body", TEAL, ellipse(450, 600, 205, 122)),
        ("fish_tail", TEAL, poly((655, 600), (765, 500), (775, 700))),
        ("fish_fin", TEAL, poly((420, 478), (480, 398), (520, 488))),
        ("fish_belly", PAPER, ellipse(410, 650, 140, 60)),
        ("fish_eye", PAPER, circle(300, 560, 24)),
    ]
    detailed = [
        ("sky", SKY_BLUE, rect(0, 0, 800, 800)),
        ("sun", YELLOW, circle(116, 116, 52)),
        ("sun_core", CREAM, circle(116, 116, 30)),
        *sun_rays("ray_", 116, 116, 60, 92),
        ("cloud1", PAPER, cloud(400, 96, 0.9)),
        ("bird1", INK, poly((500, 200), (524, 182), (548, 200), (524, 190))),
        ("hill_city", GREEN, hill(660, 400, 560, 90)),
        ("city_wall", BEIGE, rect(540, 280, 260, 120)),
        *building("tower_", 576, 280, 60, 92, TAN, CRIMSON),
        ("dome", GOLD, arc_band(700, 280, 52, 0.001, 180, 360)),
        ("dome_base", TAN, rect(648, 280, 104, 26)),
        ("city_gate", DARK_BROWN, rrect(644, 346, 52, 64, 24)),
        ("sea_far", SEA, wave_band(428, 16, 90)),
        ("sea_mid", MID_BLUE, wave_band(520, 18, 100)),
        ("sea_near", DEEP_BLUE, wave_band(676, 16, 110)),
        ("foam1", PAPER, ellipse(120, 470, 44, 12)),
        ("foam2", PAPER, ellipse(700, 500, 40, 11)),
        ("boat_hull", TAN, poly((70, 392), (286, 392), (244, 470), (112, 470))),
        ("boat_mast", DARK_BROWN, rrect(178, 232, 12, 168, 4)),
        ("sail", CREAM, poly((192, 244), (292, 392), (192, 392))),
        ("sail_stripe", RED, poly((192, 300), (254, 392), (192, 392))),
        ("flag", RED, poly((190, 232), (240, 244), (190, 258))),
        ("sailor_body", TEAL, poly((120, 330), (166, 330), (176, 392), (110, 392))),
        ("sailor_head", BEIGE, circle(143, 312, 20)),
        ("fish_body", TEAL, ellipse(470, 600, 200, 118)),
        ("fish_belly", PAPER, ellipse(430, 648, 140, 58)),
        ("fish_tail_top", TEAL, poly((668, 592), (786, 478), (774, 592))),
        ("fish_tail_bottom", TEAL, poly((668, 608), (786, 718), (774, 608))),
        ("fish_fin_top", TEAL, poly((434, 478), (492, 382), (538, 484))),
        ("fish_eye", PAPER, circle(304, 554, 26)),
        ("fish_pupil", INK, circle(310, 554, 12)),
        ("fish_mouth", DARK_BROWN, ellipse(262, 620, 24, 10, -15)),
        ("fish_scale1", SEA, circle(438, 572, 18)),
        ("fish_scale2", SEA, circle(500, 592, 18)),
        ("fish_spout", SKY_BLUE, poly((318, 478), (348, 406), (378, 478))),
        ("beach", BEIGE, poly((0, 800), (280, 800), (240, 716), (0, 690))),
        ("jonah_robe", VIOLET, poly((108, 660), (172, 660), (192, 790), (88, 790))),
        ("jonah_head", BEIGE, circle(140, 632, 28)),
        ("jonah_hair", INK, arc_band(140, 632, 31, 17, 180, 360)),
        ("jonah_arm", VIOLET, poly((172, 686), (212, 730), (196, 744), (156, 700))),
        *fish("small_fish_", 640, 740, 0.8, body=ORANGE),
        ("seaweed", FOREST, poly((740, 800), (756, 800), (768, 706), (746, 698))),
    ]
    return small, detailed


def scene_five_loaves() -> tuple[list, list]:
    small = [
        ("sky", SKY_BLUE, rect(0, 0, 800, 800)),
        ("sun", YELLOW, circle(660, 120, 58)),
        ("grass", LIME, wave_band(560, 18, 100)),
        ("basket", TAN, poly((300, 560), (500, 560), (460, 700), (340, 700))),
        ("loaf1", TAN, ellipse(360, 540, 44, 28)),
        ("loaf2", TAN, ellipse(440, 540, 44, 28)),
        ("fish1", SEA, ellipse(200, 640, 62, 26)),
        ("boy_robe", TEAL, poly((120, 460), (210, 460), (234, 660), (96, 660))),
        ("boy_head", BEIGE, circle(165, 424, 34)),
    ]
    detailed = [
        ("sky", SKY_BLUE, rect(0, 0, 800, 800)),
        ("sun", YELLOW, circle(664, 118, 56)),
        ("sun_core", CREAM, circle(664, 118, 32)),
        *sun_rays("ray_", 664, 118, 64, 98),
        ("cloud1", PAPER, cloud(190, 110, 0.95)),
        ("bird1", INK, poly((300, 176), (324, 158), (348, 176), (324, 166))),
        ("hill_far", GREEN, hill(200, 560, 640, 130)),
        ("tree_crown", FOREST, circle(60, 430, 56)),
        ("tree_trunk", BROWN, rrect(50, 460, 20, 116, 8)),
        ("apple1", RED, circle(46, 418, 11)),
        ("grass", LIME, wave_band(558, 18, 100)),
        ("blade1", FOREST, poly((250, 664), (258, 620), (266, 664))),
        ("blade2", FOREST, poly((480, 660), (488, 612), (496, 660))),
        ("blanket", RED, poly((150, 690), (690, 690), (730, 800), (110, 800))),
        ("blanket_stripe", CREAM, poly((148, 726), (702, 726), (708, 746), (146, 746))),
        *basket("basket_", 420, 706, 1.0),
        ("loaf1", TAN, ellipse(366, 592, 40, 26)),
        ("loaf2", TAN, ellipse(446, 586, 38, 25)),
        ("loaf3", TAN, ellipse(406, 566, 34, 22)),
        ("loaf4", BEIGE, ellipse(340, 616, 30, 19)),
        ("loaf5", BEIGE, ellipse(474, 612, 30, 19)),
        *fish("fish1_", 232, 772, 0.86, body=SEA),
        *fish("fish2_", 600, 778, 0.78, body=SKY_BLUE, flip=True),
        *figure("boy_", 148, 700, 240, TEAL, BEIGE, hair=BROWN, sash=YELLOW),
        ("boy_hand", BEIGE, circle(196, 566, 17)),
        *figure("man_", 700, 686, 250, VIOLET, BEIGE, hair=INK, sash=CRIMSON),
        ("jar", TAN, ellipse(316, 762, 28, 38)),
        ("jar_neck", TAN, rrect(304, 716, 24, 20, 6)),
        ("flower1", PINK, circle(96, 762, 13)),
        ("flower2", YELLOW, circle(764, 776, 13)),
    ]
    return small, detailed


SCENES = [
    {
        "id": "abraham_1",
        "storyId": "abraham",
        "title": "Звёзды Авраама",
        "scene": scene_abraham,
    },
    {
        "id": "joseph_1",
        "storyId": "joseph",
        "title": "Разноцветная одежда Иосифа",
        "scene": scene_joseph,
    },
    {
        "id": "moses_sea_1",
        "storyId": "moses-sea",
        "title": "Море расступилось",
        "scene": scene_moses,
    },
    {
        "id": "daniel_1",
        "storyId": "daniel",
        "title": "Даниил и львы",
        "scene": scene_daniel,
    },
    {
        "id": "jonah_1",
        "storyId": "jonah",
        "title": "Иона и большая рыба",
        "scene": scene_jonah,
    },
    {
        "id": "five_loaves_1",
        "storyId": "five-loaves",
        "title": "Пять хлебов и две рыбки",
        "scene": scene_five_loaves,
    },
]


# ---------------------------------------------------------------------------
# Запись файлов
# ---------------------------------------------------------------------------

def render_svg(page_id: str, title: str, level: str, regions: list[tuple[str, str, str]]) -> str:
    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {SIZE} {SIZE}" width="{SIZE}" '
        f'height="{SIZE}" data-page="{page_id}" data-level="{level}">',
        f"  <title>{title}</title>",
        f'  <g fill="#FFFFFF" stroke="{STROKE_COLOR}" stroke-width="{STROKE_WIDTH}" '
        f'stroke-linejoin="round" stroke-linecap="round">',
    ]
    for region_id, hint, d in regions:
        lines.append(f'    <path id="{region_id}" data-hint="{hint}" d="{d}"/>')
    lines.append("  </g>")
    lines.append("</svg>")
    return "\n".join(lines) + "\n"


def render_json(page_id: str, story_id: str, title: str, small: list, detailed: list) -> str:
    payload = {
        "format": "kids-coloring/v1",
        "id": page_id,
        "asset": f"kids_coloring_{page_id}.json",
        "storyId": story_id,
        "title": title,
        "viewBox": [0, 0, SIZE, SIZE],
        "strokeColor": STROKE_COLOR,
        "strokeWidth": STROKE_WIDTH,
        "note": NOTE,
        "levels": {
            "small": {"regions": [{"id": i, "d": d, "hint": h} for i, h, d in small]},
            "detailed": {"regions": [{"id": i, "d": d, "hint": h} for i, h, d in detailed]},
        },
    }
    return json.dumps(payload, indent=1, ensure_ascii=False) + "\n"


PATH_COMMANDS = re.compile(r"^[MLHVCSQTAZmlhvcsqtaz0-9.,\-\s]+$")


def validate(page_id: str, level: str, regions: list[tuple[str, str, str]]) -> list[str]:
    problems: list[str] = []
    seen: set[str] = set()
    for region_id, hint, d in regions:
        if region_id in seen:
            problems.append(f"{page_id}/{level}: повтор id «{region_id}»")
        seen.add(region_id)
        if not d.strip():
            problems.append(f"{page_id}/{level}: пустой путь у «{region_id}»")
        elif not PATH_COMMANDS.match(d):
            problems.append(f"{page_id}/{level}: недопустимые символы в пути «{region_id}»")
        if not re.fullmatch(r"#[0-9A-Fa-f]{6}", hint):
            problems.append(f"{page_id}/{level}: подсказка «{hint}» у «{region_id}» не #RRGGBB")
    if not regions:
        problems.append(f"{page_id}/{level}: нет регионов")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="не записывать файлы, только отчёт")
    parser.add_argument("--out", default=ASSETS_DIR, help="каталог assets/kids/coloring")
    args = parser.parse_args()

    problems: list[str] = []
    report: list[str] = []
    for scene in SCENES:
        page_id = scene["id"]
        small, detailed = scene["scene"]()
        problems += validate(page_id, "small", small)
        problems += validate(page_id, "detailed", detailed)
        report.append(
            f"{page_id:<16} story={scene['storyId']:<12} small={len(small):>3} detailed={len(detailed):>3}"
        )
        if args.check:
            continue
        files = {
            f"kids_coloring_{page_id}_small.svg": render_svg(page_id, scene["title"], "small", small),
            f"kids_coloring_{page_id}_detailed.svg": render_svg(page_id, scene["title"], "detailed", detailed),
            f"kids_coloring_{page_id}.json": render_json(
                page_id, scene["storyId"], scene["title"], small, detailed,
            ),
        }
        for name, text in files.items():
            with open(os.path.join(args.out, name), "w", encoding="utf-8") as handle:
                handle.write(text)

    print("\n".join(report))
    if problems:
        print("\nОшибки:", file=sys.stderr)
        for item in problems:
            print(f"  - {item}", file=sys.stderr)
        return 1
    print("OK" if args.check else f"OK — записано {len(SCENES) * 3} файлов в {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
