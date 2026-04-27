#!/usr/bin/env python3
"""Translate Strong's dictionary definitions and KJV usages to Russian."""
import json
import os
import re

WORD_MAP = {
    "the": "", "a": "", "an": "", "of": "", "or": "или",
    "and": "и", "but": "но", "not": "не", "no": "нет",
    "also": "также", "by": "по", "i.e.": "т.е.",
    "in": "в", "to": "к", "for": "для", "from": "от",
    "with": "с", "as": "как", "at": "при", "on": "на",
    "is": "есть", "be": "быть", "it": "это",
    "that": "который", "which": "который", "this": "это",
    "its": "его", "their": "их", "his": "его", "her": "её",
    "some": "некоторые", "any": "любой",
    "only": "только", "especially": "особенно",
    "specifically": "конкретно", "specially": "особенно",
    "figuratively": "переносно", "literally": "буквально",
    "abstractly": "абстрактно", "concretely": "конкретно",
    "generally": "обычно", "properly": "собственно",
    "apparently": "очевидно", "perhaps": "возможно",
    "probably": "вероятно", "sometimes": "иногда",
    "occasionally": "иногда", "often": "часто",
    "always": "всегда", "never": "никогда",
    "rather": "скорее", "very": "очень",
    "including": "включая", "implied": "подразумевается",
    "implication": "подразумевается", "extension": "расширенно",
    "sense": "смысл", "meaning": "значение",
    "used": "используется", "applied": "применяется",
    "plural": "множественное число", "singular": "единственное число",
    "article": "артикль",
    "primitive": "первичный", "root": "корень",
    "word": "слово", "verb": "глагол", "noun": "существительное",
    "adjective": "прилагательное", "adverb": "наречие",
    "participle": "причастие", "infinitive": "инфинитив",
    "imperative": "повелительное наклонение",
    # Theological/biblical
    "God": "Бог", "god": "бог", "gods": "боги",
    "Lord": "Господь", "lord": "господин",
    "Christ": "Христос", "Jesus": "Иисус",
    "Spirit": "Дух", "spirit": "дух", "spirits": "духи",
    "Holy": "Святой", "holy": "святой",
    "angel": "ангел", "angels": "ангелы",
    "divine": "божественный", "Divine": "Божественный",
    "sacred": "священный", "religious": "религиозный",
    "salvation": "спасение", "redemption": "искупление",
    "grace": "благодать", "mercy": "милость",
    "faith": "вера", "hope": "надежда",
    "love": "любовь", "charity": "милосердие",
    "sin": "грех", "sinful": "грешный",
    "righteousness": "праведность", "righteous": "праведный",
    "judgment": "суд", "justice": "справедливость",
    "truth": "истина", "true": "истинный",
    "wisdom": "мудрость", "wise": "мудрый",
    "law": "закон", "commandment": "заповедь",
    "covenant": "завет", "promise": "обещание",
    "prayer": "молитва", "worship": "поклонение",
    "sacrifice": "жертва", "offering": "приношение",
    "blessing": "благословение", "curse": "проклятие",
    "prophet": "пророк", "prophecy": "пророчество",
    "priest": "священник", "temple": "храм",
    "altar": "жертвенник", "tabernacle": "скиния",
    "heaven": "небо", "heavens": "небеса",
    "earth": "земля", "world": "мир",
    "kingdom": "царство", "king": "царь",
    "eternal": "вечный", "everlasting": "вечный",
    "life": "жизнь", "death": "смерть",
    "soul": "душа", "body": "тело", "flesh": "плоть",
    "blood": "кровь", "heart": "сердце",
    "fire": "огонь", "water": "вода",
    "light": "свет", "darkness": "тьма",
    "bread": "хлеб", "wine": "вино",
    "cross": "крест", "resurrection": "воскресение",
    # Common nouns
    "father": "отец", "mother": "мать",
    "son": "сын", "daughter": "дочь",
    "brother": "брат", "sister": "сестра",
    "husband": "муж", "wife": "жена",
    "man": "человек", "woman": "женщина",
    "child": "дитя", "children": "дети",
    "people": "народ", "nation": "народ",
    "servant": "раб", "master": "господин",
    "friend": "друг", "enemy": "враг",
    "house": "дом", "city": "город",
    "land": "земля", "mountain": "гора",
    "sea": "море", "river": "река",
    "tree": "дерево", "stone": "камень",
    "sword": "меч", "shield": "щит",
    "name": "имя", "voice": "голос",
    "hand": "рука", "eye": "глаз", "face": "лицо",
    "head": "голова", "foot": "нога",
    "mouth": "уста", "ear": "ухо",
    "day": "день", "night": "ночь",
    "morning": "утро", "evening": "вечер",
    "year": "год", "time": "время",
    "way": "путь", "door": "дверь",
    "place": "место", "thing": "вещь",
    "work": "дело", "power": "сила",
    "glory": "слава", "honor": "честь",
    "peace": "мир", "joy": "радость",
    "fear": "страх", "anger": "гнев",
    "good": "добрый", "evil": "злой",
    "great": "великий", "small": "малый",
    "new": "новый", "old": "старый",
    "strong": "сильный", "weak": "слабый",
    "rich": "богатый", "poor": "бедный",
    "clean": "чистый", "unclean": "нечистый",
    "first": "первый", "last": "последний",
    # Common verbs
    "say": "говорить", "speak": "говорить",
    "come": "приходить", "go": "идти",
    "give": "давать", "take": "брать",
    "make": "делать", "do": "делать",
    "see": "видеть", "hear": "слышать",
    "know": "знать", "think": "думать",
    "call": "звать", "send": "посылать",
    "bring": "приносить", "put": "класть",
    "set": "ставить", "keep": "хранить",
    "build": "строить", "destroy": "разрушать",
    "kill": "убивать", "save": "спасать",
    "heal": "исцелять", "help": "помогать",
    "love": "любить", "hate": "ненавидеть",
    "fear": "бояться", "serve": "служить",
    "praise": "хвалить", "bless": "благословлять",
    "judge": "судить", "forgive": "прощать",
    "eat": "есть", "drink": "пить",
    "live": "жить", "die": "умирать",
    "walk": "ходить", "stand": "стоять",
    "sit": "сидеть", "fall": "падать",
    "rise": "восставать", "turn": "обращаться",
    "remain": "оставаться", "return": "возвращаться",
    "run": "бежать", "fight": "сражаться",
    "write": "писать", "read": "читать",
    "teach": "учить", "learn": "учиться",
    "sing": "петь", "weep": "плакать",
    "cry": "кричать", "pray": "молиться",
    # KJV-specific
    "account": "отчёт", "cause": "причина",
    "communication": "общение", "concerning": "касающийся",
    "doctrine": "учение", "fame": "молва",
    "intent": "намерение", "matter": "дело",
    "preaching": "проповедь", "question": "вопрос",
    "reason": "разум", "reckon": "считать",
    "remove": "удалять", "saying": "речение",
    "shew": "показывать", "speaker": "говорящий",
    "speech": "речь", "talk": "разговор",
    "tidings": "вести", "treatise": "трактат",
    "utterance": "изречение",
    "dear": "дорогой", "feast": "пиршество",
    "affection": "привязанность", "benevolence": "благоволение",
    "something": "нечто", "said": "сказанное",
    "thought": "мысль", "topic": "тема",
    "subject": "предмет", "discourse": "рассуждение",
    "reasoning": "рассуждение", "mental": "мыслительный",
    "faculty": "способность", "motive": "мотив",
    "computation": "вычисление", "expression": "выражение",
    "letter": "буква", "alphabet": "алфавит",
    "numeral": "числительное",
    "supreme": "верховный", "deference": "почтение",
    "magistrates": "судьи", "superlative": "превосходный",
    "exceeding": "превосходящий", "mighty": "могущественный",
    "judges": "судьи", "ordinary": "обычный",
    "chief": "начальник", "principal": "главный",
    "patrimony": "наследство",
    "character": "символ", "mark": "знак",
    "number": "число", "image": "образ",
    "likeness": "подобие", "form": "форма",
    "manner": "образ", "kind": "род",
    "type": "вид", "sort": "род",
    "nature": "природа", "quality": "качество",
    "condition": "состояние", "state": "состояние",
    "act": "действие", "action": "действие",
    "deed": "деяние", "activity": "деятельность",
    "effect": "действие", "result": "результат",
    "end": "конец", "beginning": "начало",
    "origin": "происхождение", "source": "источник",
    "object": "предмет", "purpose": "цель",
    "desire": "желание", "will": "воля",
    "mind": "разум", "understanding": "понимание",
    "knowledge": "знание", "skill": "умение",
    "strength": "сила", "might": "могущество",
    "authority": "власть", "rule": "правление",
    "dominion": "владычество",
    "ably": "способно",
}

PUNCT_RE = re.compile(r'^(.*?)([.,;:!?()]+)$')

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
    'zh': 'ж', 'kh': 'х', 'ts': 'ц',
}

LATIN_RE = re.compile(r'[a-zA-Z]')


def transliterate_word(w: str) -> str:
    if not w:
        return w
    is_upper = w[0].isupper()
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
        result.append(TRANSLIT.get(ch, ch))
        i += 1
    out = ''.join(result)
    if is_upper and out:
        out = out[0].upper() + out[1:]
    return out


def translate_word(w: str) -> str:
    if not w:
        return w
    
    # Direct match
    if w in WORD_MAP:
        return WORD_MAP[w]
    
    # Strip punct
    m = PUNCT_RE.match(w)
    if m:
        core, punct = m.group(1), m.group(2)
        tr = translate_word(core)
        return tr + punct if tr != core else w
    
    low = w.lower()
    if low in WORD_MAP:
        r = WORD_MAP[low]
        if w[0].isupper() and r:
            return r[0].upper() + r[1:]
        return r
    
    # Keep Strong's references as-is (G1234, H5678)
    if re.match(r'^[GH]\d+$', w):
        return w
    
    # Keep Hebrew/Greek text as-is
    if not LATIN_RE.search(w):
        return w
    
    # Transliterate remaining English
    return transliterate_word(w)


def translate_text(text: str) -> str:
    if not text:
        return text
    
    # Don't translate if already has significant Cyrillic
    cyrillic_count = len(re.findall(r'[а-яА-ЯёЁ]', text))
    if cyrillic_count > len(text) * 0.5:
        return text
    
    # Keep certain patterns intact
    text = text.replace('i.e.', 'т.е.')
    text = text.replace('(i.e.', '(т.е.')
    
    # Translate word by word
    tokens = re.split(r'(\s+)', text)
    translated = []
    for token in tokens:
        if token.isspace():
            translated.append(token)
        else:
            translated.append(translate_word(token))
    
    return ''.join(translated)


def translate_kjv(kjv: str) -> str:
    """Translate KJV usage line - comma-separated list of English words."""
    if not kjv:
        return kjv
    
    parts = kjv.split(', ')
    translated = []
    for part in parts:
        p = part.strip()
        if not p:
            continue
        # Skip X prefix (KJV notation)
        if p.startswith('X '):
            p = p[2:]
        if p.startswith('[idiom] '):
            p = p[8:]
        # Handle parenthetical
        p_clean = re.sub(r'\([^)]*\)', '', p).strip()
        
        tr = translate_word(p_clean) if p_clean else ''
        if tr and tr != p_clean:
            translated.append(tr)
        elif p_clean:
            translated.append(translate_word(p_clean))
    
    return ', '.join(translated) if translated else kjv


def main():
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                        'app', 'src', 'main', 'assets', 'strongs_dictionary.json')
    
    with open(path) as f:
        data = json.load(f)
    
    total = len(data)
    for key, entry in data.items():
        # Translate definition
        if 'd' in entry:
            entry['d'] = translate_text(entry['d'])
        # Translate KJV usage
        if 'k' in entry:
            entry['k'] = translate_kjv(entry['k'])
        # Translate origin
        if 'o' in entry:
            entry['o'] = translate_text(entry['o'])
    
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
    
    print(f"Translated {total} entries")
    
    # Verify
    for k in ['G3056', 'G0026', 'H0001', 'H0430']:
        if k in data:
            print(f"\n{k}:")
            print(f"  d: {data[k].get('d','')[:200]}")
            print(f"  k: {data[k].get('k','')[:200]}")
            print(f"  o: {data[k].get('o','')[:200]}")


if __name__ == '__main__':
    main()
