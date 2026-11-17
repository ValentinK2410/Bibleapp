#!/usr/bin/env python3
"""Находит в подстрочнике (PODSTR) английские слова, записанные кириллицей
(«дебт», «фулфилл», «унфаиффуллй»), и заменяет их русскими.

Как ищем: слово глоссы не знает словарь русского языка (pymorphy3/OpenCorpora),
но оно получается из английского слова той же транслитерацией, что была
в tools/rebuild_strongs_ru.py (th→ф, y→й, c→к, w→в, h→х …).
Подряд идущие такие слова переводятся вместе как английская фраза по ручному
словарю tools/podstr_anglicism_overrides.json (библейские значения); машинный
перевод (Marian opus-mt-en-ru) — только запасной вариант. Нестандартно
транслитерированные слова («унфаиффуллй») заменяются по
tools/podstr_cyrillic_overrides.json, а глоссы-предложения целиком —
по tools/podstr_gloss_overrides.json. Имена собственные (с заглавной),
старославянские и редкие русские слова не трогаются. Меняется только поле "r"
(глосса), формат JSON-файла сохраняется.

Запуск:
  python3 tools/fix_podstr_anglicisms.py --scan      # таблица кандидатов /tmp/podstr/candidates.tsv
  python3 tools/fix_podstr_anglicisms.py --apply     # записать исправления и CSV-отчёт
Нужно: pymorphy3, pymorphy3-dicts-ru, wordfreq; transformers — только если
в ручных словарях нет какой-то фразы. Отчёт: --report PATH.
"""

import csv
import html
import glob
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PODSTR = ROOT / "app" / "src" / "main" / "assets" / "bible" / "PODSTR"
WORK = Path("/tmp/podstr")
REPORT = Path(sys.argv[sys.argv.index("--report") + 1]) if "--report" in sys.argv else WORK / "podstr-fixes.csv"

TRANSLIT = {
    "a": "а", "b": "б", "c": "к", "d": "д", "e": "е", "f": "ф", "g": "г", "h": "х",
    "i": "и", "j": "дж", "k": "к", "l": "л", "m": "м", "n": "н", "o": "о", "p": "п",
    "q": "к", "r": "р", "s": "с", "t": "т", "u": "у", "v": "в", "w": "в", "x": "кс",
    "y": "й", "z": "з",
}
DIGRAPHS = {"sh": "ш", "ch": "ч", "th": "ф", "ph": "ф", "zh": "ж", "kh": "х", "ts": "ц", "oo": "у"}
# Встречаются оба варианта транслитерации: с ou→у / ee→и и без них.
EXTRA = [{}, {"ou": "у"}, {"ee": "и"}, {"ou": "у", "ee": "и"}]

# Настоящие русские (церковные, библейские) слова и части слов, которых нет в словаре.
RUSSIAN_OK = {
    "ефа", "ефы", "ефу", "ефою", "сикль", "сиклей", "сикля", "сиклю", "сиклями", "сиклях",
    "ядущий", "ядущего", "ядущему", "ядущие", "ядущих", "надцать", "лехем", "терафим",
    "гомор", "гомер", "кор", "бат", "гин", "каб", "хомер", "мина", "талант", "лепта",
    "кодрант", "ассарий", "динарий", "драхма", "статир",
}

FUNCTION = {
    "the", "of", "a", "an", "to", "it", "as", "you", "will", "be", "in", "on", "up",
    "me", "let", "is", "are", "was", "were", "and", "or", "for", "with", "by", "at",
    "from", "out", "his", "her", "him", "they", "them", "we", "us", "our", "your",
    "my", "he", "she", "this", "that", "not", "no", "have", "has", "had", "do", "did",
    "shall", "who", "which", "all", "one", "so", "into", "off", "down", "over", "i",
}
# Ручные переводы (английская фраза → русская глосса); "" = убрать слово.
OVERRIDES = {"the": "", "of": "", "a": "", "an": ""}
OVERRIDES_FILE = Path(__file__).with_name("podstr_anglicism_overrides.json")
if OVERRIDES_FILE.exists():
    OVERRIDES.update(json.loads(OVERRIDES_FILE.read_text(encoding="utf-8")))

# Составные английские слова целиком (до разбора по словам).
PRE_REPLACE = {
    "фафер-ин-лав": "тесть", "мофер-ин-лав": "свекровь", "даугхтерс-ин-лав": "невестки",
    "даугхтер-ин-лав": "невестка", "сонс-ин-лав": "зятья", "сон-ин-лав": "зять",
    "систер-ин-лав": "невестка", "ваве-офферингс": "приношения потрясания",
    "ваве-офферинг": "приношение потрясания", "фанк-офферингс": "благодарственные жертвы",
    "фанк-офферинг": "благодарственная жертва", "дринк-офферинг": "возлияние",
    "лйинг-ин-ваит": "засада", "мофер-оф-пеарл": "перламутр",
}

TOKEN = re.compile(r"[А-Яа-яЁё]+")
RUN = re.compile(r"[А-Яа-яЁё]+(?:[ \-][А-Яа-яЁё]+)*")
LATIN = re.compile(r"[A-Za-z]")


def translit(word: str, extra: dict) -> str:
    out, i = [], 0
    while i < len(word):
        pair = word[i:i + 2]
        if pair in extra:
            out.append(extra[pair]); i += 2
        elif pair in DIGRAPHS:
            out.append(DIGRAPHS[pair]); i += 2
        else:
            out.append(TRANSLIT.get(word[i], word[i])); i += 1
    return "".join(out)


def english_index():
    from wordfreq import iter_wordlist, zipf_frequency

    index = defaultdict(list)
    for n, word in enumerate(iter_wordlist("en", "best")):
        if n > 300000:
            break
        if not word.isalpha() or not word.isascii():
            continue
        seen = set()
        for extra in EXTRA:
            cyr = translit(word, extra)
            if cyr not in seen:
                seen.add(cyr)
                index[cyr].append(word)  # по убыванию частоты
    return index, zipf_frequency


def strongs_english():
    out, names = {}, {}
    for name in ("strongs-greek-dictionary.js", "strongs-hebrew-dictionary.js"):
        path = WORK / name
        if not path.exists():
            continue
        raw = path.read_text(encoding="utf-8")
        data = json.loads(raw[raw.index("{"):raw.rindex("}") + 1])
        for key, entry in data.items():
            text = " ".join(str(entry.get(k, "")) for k in ("strongs_def", "kjv_def", "derivation"))
            code = key[0] + str(int(key[1:]))
            out[code] = set(re.findall(r"[a-z]+", text.lower()))
            names[code] = {w.lower() for w in re.findall(r"\b[A-Z][a-z]+", str(entry.get("kjv_def", "")))}
    return out, names


def norm_strong(code: str) -> str:
    m = re.match(r"([GH])0*(\d+)", code or "")
    return f"{m.group(1)}{m.group(2)}" if m else ""


def iter_words():
    for path in sorted(PODSTR.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for chapter in data["book"]["chapters"]:
            for verse in chapter["verses"]:
                for pos, word in enumerate(verse.get("words") or []):
                    yield path, data, chapter["number"], verse, pos, word


class Detector:
    def __init__(self):
        import pymorphy3

        self.morph = pymorphy3.MorphAnalyzer()
        self.eng, self.zipf = english_index()
        self.strongs, self.strong_names = strongs_english()
        self.known_cache = {}
        self.names = set()

    def known(self, low: str) -> bool:
        low = low.lower().replace("ё", "е")
        if low not in self.known_cache:
            self.known_cache[low] = self.morph.word_is_known(low) or low in RUSSIAN_OK
        return self.known_cache[low]

    def collect_names(self):
        lower, upper = Counter(), Counter()
        for *_rest, word in iter_words():
            for token in TOKEN.findall(word.get("r", "")):
                (upper if token[0].isupper() else lower)[token.lower()] += 1
        self.names = {w for w in upper if lower[w] == 0}

    def english(self, token: str, strong: str):
        """Английское слово для кириллической записи; при нескольких — то, что есть в статье Стронга."""
        options = self.eng.get(token.lower())
        if not options:
            return None, False
        hints = self.strongs.get(strong, set())
        for option in options:
            if option in hints:
                return option, True
        return options[0], False

    def flagged(self, token: str, strong: str) -> bool:
        low = token.lower()
        if keep_name(token) or self.known(low) or low in self.names:
            return False
        eng, in_strongs = self.english(token, strong)
        if eng is None or eng in FUNCTION:
            return False
        if eng in self.strong_names.get(strong, set()):
            return False  # имя собственное (Zur, Baal, Shua …), а не английское слово
        return in_strongs or self.zipf(eng, "en") >= 2.0

    def runs(self, gloss: str, strong: str):
        """Куски глоссы из английских слов: [(start, end, english_phrase)]."""
        out = []
        for match in RUN.finditer(gloss):
            current = []
            for m in TOKEN.finditer(match.group(0)):
                start, end, token = m.start() + match.start(), m.end() + match.start(), m.group(0)
                if self.flagged(token, strong):
                    current.append((start, end, token, True))
                    continue
                eng, _ = self.english(token, strong)
                if keep_name(token):
                    out.extend(self.close(current, gloss, strong))
                    current = []
                elif eng in FUNCTION and not self.known(token):
                    current.append((start, end, token, False))  # «фе», «оф», «йу»
                elif eng in FUNCTION and current:
                    current.append((start, end, token, False))  # «ит», «ас» внутри фразы
                else:
                    out.extend(self.close(current, gloss, strong))
                    current = []
            out.extend(self.close(current, gloss, strong))
        return out

    def close(self, current, gloss, strong):
        while current and not current[-1][3] and self.known(current[-1][2]):
            current = current[:-1]  # хвост из русских «то», «ас», «он» не трогаем
        while current and not current[0][3] and self.known(current[0][2]):
            current = current[1:]
        if not current:
            return []
        start, end = current[0][0], current[-1][1]
        text = gloss[start:end]
        words = []
        for piece in re.split(r"([ \-])", text):
            if piece in (" ", "-"):
                words.append(piece)
            else:
                words.append(self.english(piece, strong)[0] or piece)
        phrase = "".join(words).lower()
        if TOKEN.search(phrase):
            return []  # внутри осталось русское слово — не наш случай
        return [(start, end, phrase)]


# Английские слова, которые транслитерация записала нестандартно (ff вместо th,
# суффиксы -ness/-ed/-ing и т.п.) и которые детектор не смог сопоставить
# с английским словом: заменяются целиком по готовому списку.
CYRILLIC_FILE = Path(__file__).with_name("podstr_cyrillic_overrides.json")
CYRILLIC = json.loads(CYRILLIC_FILE.read_text(encoding="utf-8")) if CYRILLIC_FILE.exists() else {}
CYRILLIC_RE = re.compile(r"(?<![А-Яа-яЁё])(" + "|".join(sorted(map(re.escape, CYRILLIC), key=len, reverse=True)) + r")(?![А-Яа-яЁё])",
                         re.IGNORECASE) if CYRILLIC else None


# Глоссы-предложения, где английский остался между переведёнными словами
# («и, хе вилл кут горло»): заменяются целиком.
GLOSS_FILE = Path(__file__).with_name("podstr_gloss_overrides.json")
GLOSS = json.loads(GLOSS_FILE.read_text(encoding="utf-8")) if GLOSS_FILE.exists() else {}

# Точечные исправления, где транслитерация неоднозначна (sawed/saved → «савед»).
EXACT = {
    ("1_chronicles", "20:3", "и, он савед"): "и, он распилил",
    ("hebrews", "11:37", "они были савед в два"): "они были распилены надвое",
}


# Имена собственные, которые совпадают с английскими словами (Mark, Kedar ~ cedar,
# Ur ~ our, Lo-debar, Me-zahab, Saraph, Lair, Kitron ~ citron, Shear-jashub, Hen):
# с заглавной буквы их не трогаем.
NAMES_KEEP = {"марк", "кедар", "ур", "ло", "сараф", "лаир", "китрон", "шеар", "хен"}

# Слова, перевод которых зависит от номера Стронга.
STRONG_FIX = {
    "G4978": {"череда": "разделение", "череды": "разделения", "Череда": "Разделение"},  # σχίσμα — раскол, не «череда»
    "G518": {"Родственный": "Рассказали"},  # ἀπαγγέλλω (Лк 8:36) — related = рассказали
    "G2304": {"Гадать": "Божество"},  # θεῖος (Деян 17:29) — Divine being
}


def keep_name(word: str) -> bool:
    return word[:1].isupper() and word.lower() in NAMES_KEEP


def _cyrillic(m):
    word = m.group(0)
    if keep_name(word):
        return word
    ru = CYRILLIC[word.lower()]
    return ru[:1].upper() + ru[1:] if word[:1].isupper() else ru


def pre_replace(gloss: str) -> str:
    for src, dst in PRE_REPLACE.items():
        if src in gloss.lower():
            gloss = re.sub(re.escape(src), dst, gloss, flags=re.IGNORECASE)
    if CYRILLIC_RE is not None:
        gloss = re.sub(r"(?<=[А-Яа-яЁё])<(?:с|ес)>", "", CYRILLIC_RE.sub(_cyrillic, gloss)) if CYRILLIC_RE.search(gloss) else gloss
    return gloss


def marian_candidates(phrases):
    cache_path = WORK / "marian-cache.json"
    cache = json.loads(cache_path.read_text(encoding="utf-8")) if cache_path.exists() else {}
    todo = [p for p in phrases if p not in cache]
    if todo:
        from transformers import MarianMTModel, MarianTokenizer

        name = "Helsinki-NLP/opus-mt-en-ru"
        tok = MarianTokenizer.from_pretrained(name)
        model = MarianMTModel.from_pretrained(name)
        for i in range(0, len(todo), 32):
            batch = todo[i:i + 32]
            enc = tok(batch, return_tensors="pt", padding=True)
            out = model.generate(**enc, num_beams=8, num_return_sequences=6, max_new_tokens=24)
            texts = tok.batch_decode(out, skip_special_tokens=True)
            for j, phrase in enumerate(batch):
                cache[phrase] = texts[j * 6:(j + 1) * 6]
            if i % 640 == 0:
                print(f"  mt {i}/{len(todo)}", flush=True)
                cache_path.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
        cache_path.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
    return cache


class Chooser:
    def __init__(self, det: Detector, mt: dict):
        self.det, self.mt = det, mt
        self.lemma_cache = {}

    def lemma(self, word: str) -> str:
        low = word.lower().replace("ё", "е")
        if low not in self.lemma_cache:
            self.lemma_cache[low] = self.det.morph.parse(low)[0].normal_form
        return self.lemma_cache[low]

    def valid(self, english: str, text: str):
        text = html.unescape(text).strip().strip(".!?:;,…").strip()
        text = re.sub(r"[«»\"()]", "", text).strip()
        if not text or LATIN.search(text) or re.search(r"[^А-Яа-яЁё \-]", text):
            return None
        words = TOKEN.findall(text)
        if len(words) > len(english.split()) + 2:
            return None
        if not all(self.det.known(w) for w in words):
            return None
        return text.lower()

    def choose(self, english: str, verse_text: str):
        if english in OVERRIDES:
            return OVERRIDES[english], "manual"
        options = [v for v in (self.valid(english, c) for c in self.mt.get(english, [])) if v]
        if not options:
            return None, "none"
        verse = {self.lemma(w) for w in TOKEN.findall(verse_text)}
        for option in options:
            content = [w for w in TOKEN.findall(option) if len(w) > 2]
            if content and all(self.lemma(w) in verse for w in content):
                return option, "verse"
        return options[0], "mt"


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    det = Detector()
    det.collect_names()
    hits = []
    phrases = Counter()
    left = Counter()
    for path, _data, ch, verse, pos, word in iter_words():
        gloss = word.get("r", "")
        if not gloss:
            continue
        strong = norm_strong(word.get("s", ""))
        pre = pre_replace(gloss)
        runs = det.runs(pre, strong)
        covered = set()
        for start, end, eng in runs:
            phrases[eng] += 1
            covered.update(range(start, end))
        for m in TOKEN.finditer(gloss):
            if m.start() not in covered and not det.known(m.group(0)) and m.group(0)[0].islower():
                left[m.group(0).lower()] += 1
        if runs or pre != gloss or gloss in GLOSS:
            hits.append((path, ch, verse["number"], verse.get("text", ""), pos,
                         word.get("o", ""), word.get("s", ""), gloss, runs))
    print(f"glosses with English: {len(hits)}, phrases {len(phrases)}, unknown left {len(left)}")
    mt = marian_candidates(list(phrases))
    chooser = Chooser(det, mt)
    if "--scan" in sys.argv:
        with open(WORK / "candidates.tsv", "w", encoding="utf-8") as fh:
            for eng, n in phrases.most_common():
                choice, how = chooser.choose(eng, "")
                fh.write(f"{n}\t{eng}\t{choice}\t{how}\t{' | '.join(mt.get(eng, [])[:4])}\n")
        with open(WORK / "left.tsv", "w", encoding="utf-8") as fh:
            for low, n in left.most_common():
                fh.write(f"{n}\t{low}\n")
        return
    if "--apply" in sys.argv:
        by_file = defaultdict(list)
        for hit in hits:
            by_file[hit[0]].append(hit)
        rows, skipped = [], Counter()
        for path, items in by_file.items():
            raw = path.read_text(encoding="utf-8")
            data = json.loads(raw)
            style = detect_style(raw, data)
            chapters = {c["number"]: c for c in data["book"]["chapters"]}
            for _p, ch, vs, vtext, pos, original, strong, gloss, runs in items:
                verse = next(v for v in chapters[ch]["verses"] if v["number"] == vs)
                word = verse["words"][pos]
                new = pre_replace(gloss)
                for start, end, eng in sorted(runs, reverse=True):
                    ru, how = chooser.choose(eng, vtext)
                    if ru is None:
                        skipped[eng] += 1
                        continue
                    if gloss[start:start + 1].isupper() and ru:
                        ru = ru[:1].upper() + ru[1:]
                    tail = re.sub(r"^<(?:с|ес)>", "", new[end:])
                    new = new[:start] + ru + tail
                new = EXACT.get((path.stem, f"{ch}:{vs}", gloss), new)
                for src, dst in STRONG_FIX.get(norm_strong(strong), {}).items():
                    new = re.sub(rf"(?<![А-Яа-яЁё]){src}(?![А-Яа-яЁё])", dst, new)
                new = re.sub(r"\s{2,}", " ", new)
                new = re.sub(r"\s+([,.;:!?])", r"\1", new)
                new = re.sub(r"^[\s,]+", "", new).strip()
                new = GLOSS.get(new, new)
                if new != gloss:
                    word["r"] = new
                    rows.append([path.stem, f"{ch}:{vs}", original, strong, gloss, new])
            path.write_text(dump(data, style), encoding="utf-8")
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        with open(REPORT, "w", encoding="utf-8", newline="") as fh:
            writer = csv.writer(fh)
            writer.writerow(["file", "ref", "original", "strongs", "old_gloss", "new_gloss"])
            writer.writerows(rows)
        (WORK / "skipped.json").write_text(json.dumps(skipped.most_common(), ensure_ascii=False, indent=0), encoding="utf-8")
        print(f"changed {len(rows)} glosses -> {REPORT}; untranslated phrases {len(skipped)}")


STYLES = [((",", ":"), None, ""), ((",", ":"), None, "\n"), ((", ", ": "), None, ""),
          ((",", ": "), 2, "\n"), ((",", ": "), 2, ""), ((",", ": "), 1, "\n")]


def detect_style(raw, data):
    for sep, indent, nl in STYLES:
        if json.dumps(data, ensure_ascii=False, indent=indent, separators=sep) + nl == raw:
            return sep, indent, nl
    raise RuntimeError("unknown JSON style")


def dump(data, style):
    sep, indent, nl = style
    return json.dumps(data, ensure_ascii=False, indent=indent, separators=sep) + nl


if __name__ == "__main__":
    main()
