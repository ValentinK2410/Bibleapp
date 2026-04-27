#!/usr/bin/env python3
"""Build a compact Russian Strong's dictionary JSON for the Bible app.

Reads OpenScriptures Strong's JS files and produces a compact JSON:
  { "G0001": { "l": "lemma", "t": "translit", "d": "definition_ru", "o": "origin" }, ... }
"""
import re
import json
import os

EN_RU_DICT = {
    "father": "отец", "mother": "мать", "son": "сын", "daughter": "дочь",
    "brother": "брат", "sister": "сестра", "husband": "муж", "wife": "жена",
    "child": "ребёнок", "children": "дети", "man": "человек", "woman": "женщина",
    "king": "царь", "queen": "царица", "lord": "господин", "servant": "раб",
    "priest": "священник", "prophet": "пророк", "angel": "ангел",
    "God": "Бог", "god": "бог", "spirit": "дух", "soul": "душа",
    "heaven": "небо", "earth": "земля", "world": "мир", "sea": "море",
    "water": "вода", "fire": "огонь", "light": "свет", "darkness": "тьма",
    "day": "день", "night": "ночь", "morning": "утро", "evening": "вечер",
    "year": "год", "month": "месяц", "time": "время",
    "life": "жизнь", "death": "смерть", "love": "любовь", "peace": "мир",
    "joy": "радость", "hope": "надежда", "faith": "вера", "grace": "благодать",
    "mercy": "милость", "truth": "истина", "wisdom": "мудрость",
    "law": "закон", "sin": "грех", "righteousness": "праведность",
    "salvation": "спасение", "judgment": "суд", "covenant": "завет",
    "prayer": "молитва", "sacrifice": "жертва", "offering": "приношение",
    "temple": "храм", "altar": "жертвенник", "house": "дом", "city": "город",
    "land": "земля", "mountain": "гора", "river": "река", "tree": "дерево",
    "bread": "хлеб", "wine": "вино", "blood": "кровь", "body": "тело",
    "heart": "сердце", "hand": "рука", "eye": "глаз", "face": "лицо",
    "head": "голова", "foot": "нога", "mouth": "уста", "voice": "голос",
    "word": "слово", "name": "имя", "way": "путь", "door": "дверь",
    "stone": "камень", "sword": "меч", "crown": "венец", "cross": "крест",
    "good": "хороший", "evil": "зло", "great": "великий", "holy": "святой",
    "true": "истинный", "new": "новый", "old": "старый",
    "strong": "сильный", "weak": "слабый", "rich": "богатый", "poor": "бедный",
    "to say": "говорить", "to come": "приходить", "to go": "идти",
    "to give": "давать", "to take": "брать", "to make": "делать",
    "to see": "видеть", "to hear": "слышать", "to know": "знать",
    "to love": "любить", "to fear": "бояться", "to serve": "служить",
    "to worship": "поклоняться", "to praise": "хвалить",
    "to send": "посылать", "to call": "звать", "to write": "писать",
    "to eat": "есть", "to drink": "пить", "to live": "жить",
    "to die": "умирать", "to kill": "убивать", "to save": "спасать",
    "to heal": "исцелять", "to build": "строить", "to destroy": "разрушать",
    "to fill": "наполнять", "to keep": "хранить",
}


def translate_definition(eng_def: str) -> str:
    """Best-effort translation of Strong's definition to Russian."""
    if not eng_def:
        return ""
    
    text = eng_def.strip().strip('"').strip()
    
    # Clean up markup
    text = re.sub(r'\[idiom\]', '', text)
    text = re.sub(r'\s+', ' ', text).strip()
    
    # Try simple word-for-word for short definitions
    words = text.split(', ')
    translated_parts = []
    for part in words:
        p = part.strip().rstrip('.').strip()
        low = p.lower()
        if low in EN_RU_DICT:
            translated_parts.append(EN_RU_DICT[low])
        elif p in EN_RU_DICT:
            translated_parts.append(EN_RU_DICT[p])
        else:
            # Check if starts with "to " (verb)
            if low.startswith("to ") and low in EN_RU_DICT:
                translated_parts.append(EN_RU_DICT[low])
            else:
                translated_parts.append(p)
    
    return ', '.join(translated_parts)


def parse_js_dict(filepath: str) -> dict:
    with open(filepath) as f:
        text = f.read()
    
    # Find first { that starts a JSON object with H or G prefix
    for prefix in ['"H', '"G']:
        start = text.find('{' + prefix)
        if start >= 0:
            end = text.rfind('}')
            return json.loads(text[start:end+1])
    
    raise ValueError(f"Cannot parse {filepath}")


def build_entry(key: str, raw: dict) -> dict:
    lemma = raw.get('lemma', '')
    translit = raw.get('translit', '') or raw.get('xlit', '')
    pron = raw.get('pron', '')
    strongs_def = raw.get('strongs_def', '')
    kjv_def = raw.get('kjv_def', '')
    derivation = raw.get('derivation', '')
    
    # Clean braces from strongs_def
    strongs_def = strongs_def.strip().strip('{}').strip()
    
    # Build Russian definition
    ru_def = translate_definition(strongs_def)
    
    # Build origin description
    origin = derivation.strip()
    # Clean up derivation refs
    origin = re.sub(r'from (H\d+|G\d+)\s*\([^)]*\)', r'от \1', origin)
    origin = re.sub(r'corresponding to (H\d+|G\d+)\s*\([^)]*\)', r'соответствует \1', origin)
    origin = re.sub(r'of Hebrew origin', 'от евр.', origin)
    origin = re.sub(r'of Aramaic origin', 'от арам.', origin)
    origin = re.sub(r'of Latin origin', 'от лат.', origin)
    origin = re.sub(r'of Greek origin', 'от греч.', origin)
    origin = re.sub(r'a primitive word', 'корневое слово', origin)
    origin = re.sub(r'a primitive root', 'корневой глагол', origin)
    
    entry = {"l": lemma}
    if translit:
        entry["t"] = translit
    if pron:
        entry["p"] = pron
    entry["d"] = ru_def
    if kjv_def:
        entry["k"] = kjv_def.strip()
    if origin:
        entry["o"] = origin.strip().rstrip(';').strip()
    
    return entry


def main():
    print("Parsing Greek dictionary...")
    greek = parse_js_dict('/tmp/strongs_greek.js')
    print(f"  {len(greek)} entries")
    
    print("Parsing Hebrew dictionary...")
    hebrew = parse_js_dict('/tmp/strongs_hebrew.js')
    print(f"  {len(hebrew)} entries")
    
    result = {}
    
    for key, raw in greek.items():
        result[key] = build_entry(key, raw)
    
    for key, raw in hebrew.items():
        result[key] = build_entry(key, raw)
    
    print(f"Total: {len(result)} entries")
    
    # Write to assets
    out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                            'app', 'src', 'main', 'assets', 'strongs_dictionary.json')
    
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, separators=(',', ':'))
    
    size_mb = os.path.getsize(out_path) / (1024 * 1024)
    print(f"Written to {out_path} ({size_mb:.1f} MB)")
    
    # Show sample
    for k in ['G0001', 'G0025', 'G0026', 'H0001', 'H0430']:
        if k in result:
            print(f"  {k}: {json.dumps(result[k], ensure_ascii=False)[:200]}")


if __name__ == '__main__':
    main()
