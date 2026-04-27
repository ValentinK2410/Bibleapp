#!/usr/bin/env python3
"""
Build full interlinear JSON files from STEPBible TAGNT/TAHOT data.
Reads the downloaded TSV files and generates PODSTR JSON for all 66 books.
"""
import json
import os
import re
import sys

SOURCES = os.path.join(os.path.dirname(__file__), "sources")
OUTPUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "bible", "PODSTR")

# Mapping STEPBible book codes -> our JSON filenames
STEP_TO_ID = {
    # OT
    "Gen": "genesis", "Exo": "exodus", "Lev": "leviticus", "Num": "numbers",
    "Deu": "deuteronomy", "Jos": "joshua", "Jdg": "judges", "Rut": "ruth",
    "1Sa": "1_samuel", "2Sa": "2_samuel", "1Ki": "1_kings", "2Ki": "2_kings",
    "1Ch": "1_chronicles", "2Ch": "2_chronicles", "Ezr": "ezra", "Neh": "nehemiah",
    "Est": "esther", "Job": "job", "Psa": "psalms", "Pro": "proverbs",
    "Ecc": "ecclesiastes", "Sng": "song_of_solomon", "Isa": "isaiah",
    "Jer": "jeremiah", "Lam": "lamentations", "Eze": "ezekiel", "Ezk": "ezekiel",
    "Dan": "daniel",
    "Hos": "hosea", "Jol": "joel", "Amo": "amos", "Oba": "obadiah",
    "Jon": "jonah", "Mic": "micah", "Nah": "nahum", "Nam": "nahum",
    "Hab": "habakkuk",
    "Zep": "zephaniah", "Hag": "haggai", "Zec": "zechariah", "Mal": "malachi",
    # NT
    "Mat": "matthew", "Mrk": "mark", "Luk": "luke", "Jhn": "john",
    "Act": "acts", "Rom": "romans", "1Co": "1_corinthians", "2Co": "2_corinthians",
    "Gal": "galatians", "Eph": "ephesians", "Php": "philippians",
    "Col": "colossians", "1Th": "1_thessalonians", "2Th": "2_thessalonians",
    "1Ti": "1_timothy", "2Ti": "2_timothy", "Tit": "titus", "Phm": "philemon",
    "Heb": "hebrews", "Jas": "james", "1Pe": "1_peter", "2Pe": "2_peter",
    "1Jn": "1_john", "2Jn": "2_john", "3Jn": "3_john", "Jud": "jude",
    "Rev": "revelation",
}

BOOK_NAMES = {
    "genesis": "Бытие", "exodus": "Исход", "leviticus": "Левит",
    "numbers": "Числа", "deuteronomy": "Второзаконие", "joshua": "Иисус Навин",
    "judges": "Судей", "ruth": "Руфь", "1_samuel": "1 Царств",
    "2_samuel": "2 Царств", "1_kings": "3 Царств", "2_kings": "4 Царств",
    "1_chronicles": "1 Паралипоменон", "2_chronicles": "2 Паралипоменон",
    "ezra": "Ездра", "nehemiah": "Неемия", "esther": "Есфирь",
    "job": "Иов", "psalms": "Псалтирь", "proverbs": "Притчи",
    "ecclesiastes": "Екклесиаст", "song_of_solomon": "Песня Песней",
    "isaiah": "Исаия", "jeremiah": "Иеремия", "lamentations": "Плач Иеремии",
    "ezekiel": "Иезекииль", "daniel": "Даниил", "hosea": "Осия",
    "joel": "Иоиль", "amos": "Амос", "obadiah": "Авдий",
    "jonah": "Иона", "micah": "Михей", "nahum": "Наум",
    "habakkuk": "Аввакум", "zephaniah": "Софония", "haggai": "Аггей",
    "zechariah": "Захария", "malachi": "Малахия",
    "matthew": "Евангелие от Матфея", "mark": "Евангелие от Марка",
    "luke": "Евангелие от Луки", "john": "Евангелие от Иоанна",
    "acts": "Деяния Апостолов", "romans": "Послание к Римлянам",
    "1_corinthians": "1-е Коринфянам", "2_corinthians": "2-е Коринфянам",
    "galatians": "Послание к Галатам", "ephesians": "Послание к Ефесянам",
    "philippians": "Послание к Филиппийцам", "colossians": "Послание к Колоссянам",
    "1_thessalonians": "1-е Фессалоникийцам", "2_thessalonians": "2-е Фессалоникийцам",
    "1_timothy": "1-е Тимофею", "2_timothy": "2-е Тимофею",
    "titus": "Послание к Титу", "philemon": "Послание к Филимону",
    "hebrews": "Послание к Евреям", "james": "Послание Иакова",
    "1_peter": "1-е Петра", "2_peter": "2-е Петра",
    "1_john": "1-е Иоанна", "2_john": "2-е Иоанна", "3_john": "3-е Иоанна",
    "jude": "Послание Иуды", "revelation": "Откровение",
}

RE_NT_REF = re.compile(r'^([A-Za-z0-9]+)\.(\d+)\.(\d+)#(\d+)=')
RE_OT_REF = re.compile(r'^([A-Za-z0-9]+)\.(\d+)\.(\d+)#(\d+)=')


def clean_english(text):
    """Clean English gloss: remove brackets, extra markers."""
    t = text.strip()
    t = re.sub(r'\[([^\]]*)\]', r'\1', t)
    t = t.replace('<obj.>', '[доп.]')
    t = t.replace('  ', ' ')
    return t.strip()


def extract_strong(raw):
    """Extract Strong number like G1234 or H1234 from field."""
    m = re.search(r'[GH]\d{3,5}', raw)
    return m.group(0) if m else None


def parse_nt_files():
    """Parse TAGNT files and return dict: {book_id: {ch: {verse: [words]}}}"""
    data = {}
    for fname in ["TAGNT_Mat_Jhn.txt", "TAGNT_Act_Rev.txt"]:
        path = os.path.join(SOURCES, fname)
        if not os.path.exists(path):
            print(f"  WARNING: {path} not found, skipping")
            continue
        with open(path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.rstrip('\n')
                m = RE_NT_REF.match(line)
                if not m:
                    continue
                step_book = m.group(1)
                chapter = int(m.group(2))
                verse = int(m.group(3))

                book_id = STEP_TO_ID.get(step_book)
                if not book_id:
                    continue

                fields = line.split('\t')
                if len(fields) < 5:
                    continue

                # fields[1] = Greek (translit)
                greek_field = fields[1].strip()
                english_field = fields[2].strip()
                strong_field = fields[3].strip()

                # Parse Greek word and transliteration
                gm = re.match(r'(.+?)\s*\(([^)]+)\)\s*$', greek_field)
                if gm:
                    greek_word = gm.group(1).strip()
                    translit = gm.group(2).strip()
                else:
                    greek_word = greek_field
                    translit = ""

                english = clean_english(english_field)
                strong = extract_strong(strong_field)

                word = {
                    "o": greek_word,
                    "t": translit if translit else greek_word,
                    "r": english,
                }
                if strong:
                    word["s"] = strong

                data.setdefault(book_id, {}).setdefault(chapter, {}).setdefault(verse, []).append(word)

    return data


def parse_ot_files():
    """Parse TAHOT files and return dict: {book_id: {ch: {verse: [words]}}}"""
    data = {}
    for fname in ["TAHOT_Gen_Deu.txt", "TAHOT_Jos_Est.txt",
                   "TAHOT_Job_Sng.txt", "TAHOT_Isa_Mal.txt"]:
        path = os.path.join(SOURCES, fname)
        if not os.path.exists(path):
            print(f"  WARNING: {path} not found, skipping")
            continue
        with open(path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.rstrip('\n')
                m = RE_OT_REF.match(line)
                if not m:
                    continue
                step_book = m.group(1)
                chapter = int(m.group(2))
                verse = int(m.group(3))

                book_id = STEP_TO_ID.get(step_book)
                if not book_id:
                    continue

                fields = line.split('\t')
                if len(fields) < 5:
                    continue

                # OT format: ref\thebrew\ttranslit\tenglish\tstrong\tmorph
                hebrew = fields[1].strip()
                translit = fields[2].strip()
                english = clean_english(fields[3].strip())
                strong_raw = fields[4].strip()
                strong = extract_strong(strong_raw)

                # Clean up hebrew - remove morpheme separators for display
                hebrew_clean = hebrew.replace('/', '')

                word = {
                    "o": hebrew_clean,
                    "t": translit.replace('.', '').replace('/', ''),
                    "r": english,
                }
                if strong:
                    word["s"] = strong

                data.setdefault(book_id, {}).setdefault(chapter, {}).setdefault(verse, []).append(word)

    return data


def build_text(words):
    """Build plain text from word list."""
    parts = []
    for w in words:
        r = w["r"]
        if r and r != "-" and r != "[доп.]":
            parts.append(r.replace("_", " "))
    return " ".join(parts)


def write_book_json(book_id, book_data):
    """Write a single book JSON file."""
    book_name = BOOK_NAMES.get(book_id, book_id)
    chapters = []
    for ch_num in sorted(book_data.keys()):
        verses = []
        for v_num in sorted(book_data[ch_num].keys()):
            words = book_data[ch_num][v_num]
            text = build_text(words)
            verses.append({
                "number": v_num,
                "text": text,
                "words": words,
            })
        chapters.append({
            "number": ch_num,
            "verses": verses,
        })

    out = {
        "translation": "PODSTR",
        "book": {
            "id": book_id,
            "name": book_name,
            "chapters": chapters,
        }
    }

    path = os.path.join(OUTPUT, f"{book_id}.json")
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    size_mb = os.path.getsize(path) / (1024 * 1024)
    ch_count = len(chapters)
    v_count = sum(len(ch["verses"]) for ch in chapters)
    print(f"  {book_id}: {ch_count} ch, {v_count} verses, {size_mb:.1f} MB")


def main():
    os.makedirs(OUTPUT, exist_ok=True)

    print("Parsing NT Greek data...")
    nt_data = parse_nt_files()
    print(f"  Found {len(nt_data)} NT books")

    print("Parsing OT Hebrew data...")
    ot_data = parse_ot_files()
    print(f"  Found {len(ot_data)} OT books")

    all_data = {}
    all_data.update(ot_data)
    all_data.update(nt_data)

    total_books = len(all_data)
    total_verses = sum(
        sum(len(verses) for verses in chs.values())
        for chs in all_data.values()
    )
    print(f"\nTotal: {total_books} books, {total_verses} verses")
    print(f"\nWriting JSON files...")

    for book_id in sorted(all_data.keys()):
        write_book_json(book_id, all_data[book_id])

    print(f"\nDone! Generated {total_books} book files in {OUTPUT}")


if __name__ == "__main__":
    main()
