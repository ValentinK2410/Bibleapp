#!/usr/bin/env python3
"""Transliterate remaining English words to Russian."""
import json
import glob
import os
import re

TRANSLIT = {
    'a': 'а', 'b': 'б', 'c': 'к', 'd': 'д', 'e': 'е',
    'f': 'ф', 'g': 'г', 'h': 'х', 'i': 'и', 'j': 'дж',
    'k': 'к', 'l': 'л', 'm': 'м', 'n': 'н', 'o': 'о',
    'p': 'п', 'q': 'к', 'r': 'р', 's': 'с', 't': 'т',
    'u': 'у', 'v': 'в', 'w': 'в', 'x': 'кс', 'y': 'й',
    'z': 'з',
}

DIGRAPHS = {
    'sh': 'ш', 'ch': 'ч', 'th': 'ф', 'ph': 'ф',
    'zh': 'ж', 'kh': 'х', 'ts': 'ц', 'tz': 'ц',
    'ee': 'и', 'oo': 'у', 'ou': 'у',
}

LATIN_RE = re.compile(r'[a-zA-Z]')
WORD_RE = re.compile(r'([a-zA-Z]+)')


def transliterate_word(w: str) -> str:
    if not w:
        return w
    
    is_upper_first = w[0].isupper()
    lower = w.lower()
    result = []
    i = 0
    while i < len(lower):
        if i + 1 < len(lower):
            di = lower[i:i+2]
            if di in DIGRAPHS:
                result.append(DIGRAPHS[di])
                i += 2
                continue
        ch = lower[i]
        if ch in TRANSLIT:
            result.append(TRANSLIT[ch])
        else:
            result.append(ch)
        i += 1
    
    out = ''.join(result)
    if is_upper_first and out:
        out = out[0].upper() + out[1:]
    return out


def transliterate_gloss(gloss: str) -> str:
    if not gloss:
        return gloss
    if not LATIN_RE.search(gloss):
        return gloss
    
    def replace_match(m):
        return transliterate_word(m.group(1))
    
    return WORD_RE.sub(replace_match, gloss)


def main():
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                        'app', 'src', 'main', 'assets', 'bible', 'PODSTR')
    files = sorted(glob.glob(os.path.join(base, '*.json')))
    
    total = 0
    changed = 0
    still_latin = 0
    
    for fp in files:
        with open(fp) as f:
            data = json.load(f)
        
        modified = False
        for ch in data['book']['chapters']:
            for v in ch['verses']:
                for w in v.get('words', []):
                    total += 1
                    r = w['r']
                    if r and LATIN_RE.search(r):
                        new = transliterate_gloss(r)
                        if new != r:
                            w['r'] = new
                            changed += 1
                            modified = True
                        if LATIN_RE.search(new):
                            still_latin += 1
        
        if modified:
            with open(fp, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=None, separators=(',', ':'))
    
    print(f"Transliterated {changed}/{total} words")
    print(f"Still Latin: {still_latin}")
    print(f"Fully Russian: {total - still_latin} ({100*(total-still_latin)/total:.1f}%)")


if __name__ == '__main__':
    main()
