#!/usr/bin/env python3
"""Rebuild Strong's dictionary with full Russian translations."""
import re
import json
import os

WORD_MAP = {
    # articles / preps / conj — empty or translated
    "the": "", "a": "", "an": "",
    "of": "", "or": "или", "and": "и", "but": "но",
    "not": "не", "no": "нет", "nor": "ни",
    "also": "также", "by": "по", "in": "в", "to": "к",
    "for": "для", "from": "от", "with": "с", "as": "как",
    "at": "при", "on": "на", "is": "есть", "be": "быть",
    "it": "это", "that": "тот", "which": "который",
    "this": "это", "its": "его", "their": "их",
    "his": "его", "her": "её", "he": "он", "she": "она",
    "some": "некоторые", "any": "любой", "all": "все",
    "every": "каждый", "each": "каждый",
    "only": "только", "even": "даже", "yet": "ещё",
    "so": "так", "thus": "таким образом",
    "if": "если", "when": "когда", "then": "тогда",
    "than": "чем", "like": "подобно", "such": "такой",
    "about": "около", "after": "после", "before": "перед",
    "over": "над", "under": "под", "upon": "на",
    "through": "через", "into": "в", "out": "из",
    "up": "вверх", "down": "вниз", "off": "прочь",
    "away": "прочь", "back": "назад", "forth": "вперёд",
    "around": "вокруг", "near": "около", "between": "между",
    "without": "без", "within": "внутри",
    "against": "против", "along": "вдоль",
    "together": "вместе", "apart": "отдельно",
    "again": "снова", "more": "более",
    "very": "очень", "much": "много", "many": "много",
    "less": "менее", "most": "наиболее",
    "well": "хорошо", "fully": "полностью",
    "still": "ещё", "just": "просто",
    "here": "здесь", "there": "там",
    "where": "где", "what": "что", "who": "кто",
    "whether": "ли", "how": "как", "why": "почему",
    "other": "другой", "another": "другой",
    "same": "тот же", "own": "собственный",
    "self": "сам", "oneself": "себя",
    "one": "один", "two": "два", "three": "три",
    "four": "четыре", "five": "пять", "six": "шесть",
    "seven": "семь", "eight": "восемь", "nine": "девять",
    "ten": "десять", "hundred": "сто", "thousand": "тысяча",
    "unto": "к", "let": "пусть", "has": "имеет",
    "have": "иметь", "had": "имел", "having": "имея",
    "been": "был", "being": "бытие",
    "made": "сделанный", "become": "стать",
    "used": "используется", "using": "используя",
    "certain": "определённый", "full": "полный",
    "rather": "скорее",

    # Linguistic / grammar terms
    "i.e.": "т.е.", "e.g.": "напр.",
    "etc": "и т.д.", "etc.": "и т.д.",
    "figuratively": "переносно", "figurative": "переносный",
    "literally": "буквально", "literal": "буквальный",
    "abstractly": "абстрактно", "abstract": "абстрактный",
    "concretely": "конкретно", "concrete": "конкретный",
    "generally": "обычно", "general": "общий",
    "properly": "собственно", "proper": "собственный",
    "apparently": "очевидно", "apparent": "очевидный",
    "perhaps": "возможно", "probably": "вероятно",
    "presumably": "предположительно", "presumed": "предполагаемый",
    "sometimes": "иногда", "occasionally": "изредка",
    "often": "часто", "usually": "обычно",
    "always": "всегда", "never": "никогда",
    "specifically": "конкретно", "specially": "особенно",
    "especially": "особенно",
    "primarily": "прежде всего", "primary": "первичный",
    "including": "включая", "implied": "подразумеваемый",
    "implication": "подразумевание", "extension": "расширение",
    "sense": "смысл", "meaning": "значение",
    "idea": "идея", "notion": "понятие",
    "application": "применение", "applications": "применения",
    "analogy": "аналогия", "comparison": "сравнение",
    "compare": "сравни", "variation": "вариант",
    "alternate": "вариант", "shortened": "сокращённый",
    "prolonged": "продлённая форма", "corrected": "исправленный",
    "original": "оригинальный", "derivative": "производное",
    "derivation": "происхождение",
    "plural": "мн. число", "singular": "ед. число",
    "dual": "двойственное число",
    "feminine": "ж. род", "masculine": "м. род", "neuter": "ср. род",
    "article": "артикль",
    "primitive": "первичный", "root": "корень",
    "compound": "составное", "prefix": "приставка",
    "particle": "частица", "preposition": "предлог",
    "adverb": "наречие", "adverbially": "как наречие",
    "verb": "глагол", "noun": "существительное",
    "adjective": "прилагательное",
    "participle": "причастие", "infinitive": "инфинитив",
    "imperative": "повелительное наклонение",
    "active": "действительный залог", "passive": "страдательный залог",
    "middle": "средний залог",
    "genitive": "родительный падеж",
    "causatively": "в каузативном смысле",
    "intransitively": "непереходно",
    "reflexively": "возвратно",
    "passively": "страдательно",
    "denominative": "отымённый",
    "patronymically": "по отечеству",
    "collectively": "собирательно",
    "morally": "в нравственном смысле",
    "phrase": "выражение",
    "corresponding": "соответствующий",
    "unused": "неупотребляемый",
    "base": "основа",

    # Origin/derivation
    "foreign": "иноязычный", "hebrew": "еврейский",
    "aramaic": "арамейский", "persian": "персидский",
    "egyptian": "египетский", "greek": "греческий",
    "latin": "латинский", "christian": "христианский",
    "origin": "происхождение",
    "akin": "родственно", "affinity": "родство",
    "patrial": "жители", "inhabitant": "житель",
    "israelite": "израильтянин", "israelites": "израильтяне",
    "descendant": "потомок", "descendants": "потомки",
    "palestine": "Палестина", "jordan": "Иордан",
    "region": "область", "country": "страна",
    "desert": "пустыня", "east": "восток",

    # Theological/biblical
    "God": "Бог", "god": "бог", "gods": "боги",
    "Lord": "Господь", "lord": "господин",
    "Christ": "Христос", "Jesus": "Иисус",
    "Spirit": "Дух", "spirit": "дух", "spirits": "духи",
    "Holy": "Святой", "holy": "святой",
    "angel": "ангел", "angels": "ангелы",
    "divine": "божественный", "sacred": "священный",
    "salvation": "спасение", "redemption": "искупление",
    "grace": "благодать", "mercy": "милость", "merciful": "милосердный",
    "faith": "вера", "faithful": "верный",
    "hope": "надежда", "love": "любовь",
    "charity": "милосердие",
    "sin": "грех", "sinful": "грешный", "sinner": "грешник",
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
    "heaven": "небо", "heavens": "небеса", "heavenly": "небесный",
    "earth": "земля", "world": "мир", "worldly": "мирской",
    "kingdom": "царство", "king": "царь",
    "eternal": "вечный", "everlasting": "вечный",
    "life": "жизнь", "death": "смерть",
    "soul": "душа", "body": "тело", "flesh": "плоть",
    "blood": "кровь", "heart": "сердце",
    "fire": "огонь", "water": "вода",
    "light": "свет", "darkness": "тьма",
    "bread": "хлеб", "wine": "вино",
    "cross": "крест", "resurrection": "воскресение",
    "jah": "Ях",

    # Common nouns
    "father": "отец", "mother": "мать",
    "son": "сын", "daughter": "дочь",
    "brother": "брат", "sister": "сестра",
    "husband": "муж", "wife": "жена",
    "man": "человек", "woman": "женщина",
    "child": "дитя", "children": "дети", "young": "молодой",
    "people": "народ", "nation": "народ", "nations": "народы",
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
    "year": "год", "month": "месяц", "time": "время",
    "way": "путь", "door": "дверь",
    "place": "место", "thing": "вещь", "things": "вещи",
    "work": "дело", "power": "сила",
    "glory": "слава", "honor": "честь",
    "peace": "мир", "joy": "радость",
    "fear": "страх", "anger": "гнев", "wrath": "гнев",
    "good": "добрый", "evil": "злой", "bad": "плохой",
    "great": "великий", "small": "малый", "little": "малый",
    "new": "новый", "old": "старый",
    "strong": "сильный", "weak": "слабый",
    "rich": "богатый", "poor": "бедный",
    "clean": "чистый", "unclean": "нечистый",
    "first": "первый", "last": "последний",
    "right": "правый", "left": "левый",
    "long": "длинный", "high": "высокий",
    "deep": "глубокий", "wide": "широкий",
    "food": "пища", "rest": "покой", "side": "сторона",
    "part": "часть", "pieces": "куски",
    "end": "конец", "beginning": "начало",
    "number": "число", "mark": "знак",
    "image": "образ", "form": "форма",
    "manner": "образ", "kind": "род", "sort": "род",
    "nature": "природа", "quality": "качество",
    "condition": "состояние", "state": "состояние",
    "act": "действие", "deed": "деяние",
    "effect": "действие", "result": "результат",
    "source": "источник", "object": "предмет",
    "purpose": "цель", "desire": "желание",
    "will": "воля", "mind": "разум",
    "understanding": "понимание", "knowledge": "знание",
    "strength": "сила", "might": "могущество",
    "authority": "власть", "rule": "правление",
    "dominion": "владычество",
    "person": "лицо", "character": "характер",
    "company": "общество", "case": "случай",
    "measure": "мера", "sound": "звук",
    "letter": "буква", "word": "слово",
    "garment": "одежда", "vessel": "сосуд",
    "seed": "семя", "fruit": "плод",
    "horn": "рог", "wing": "крыло",
    "camp": "стан", "wall": "стена",
    "gate": "врата", "field": "поле",

    # Common verbs
    "say": "говорить", "speak": "говорить", "said": "сказанный",
    "come": "приходить", "go": "идти", "walk": "ходить",
    "give": "давать", "take": "брать",
    "make": "делать", "do": "делать",
    "see": "видеть", "look": "смотреть",
    "hear": "слышать", "know": "знать",
    "think": "думать",
    "call": "звать", "send": "посылать",
    "bring": "приносить", "put": "класть",
    "set": "ставить", "keep": "хранить",
    "build": "строить", "destroy": "разрушать", "destruction": "разрушение",
    "kill": "убивать", "save": "спасать",
    "heal": "исцелять", "help": "помогать",
    "hate": "ненавидеть",
    "serve": "служить", "praise": "хвалить",
    "bless": "благословлять", "judge": "судить",
    "forgive": "прощать",
    "eat": "есть", "drink": "пить",
    "live": "жить", "die": "умирать",
    "stand": "стоять", "sit": "сидеть",
    "fall": "падать", "rise": "восставать",
    "turn": "поворачивать", "return": "возвращаться",
    "remain": "оставаться", "leave": "оставлять",
    "run": "бежать", "fight": "сражаться",
    "write": "писать", "read": "читать",
    "teach": "учить", "learn": "учиться",
    "sing": "петь", "weep": "плакать",
    "cry": "кричать", "pray": "молиться",
    "carry": "нести", "bear": "нести",
    "lead": "вести", "pass": "проходить",
    "break": "разбивать", "cut": "резать",
    "cast": "бросать", "lay": "класть",
    "hold": "держать", "cover": "покрывать",
    "open": "открывать", "gather": "собирать",
    "spread": "распространять", "draw": "тянуть",
    "shew": "показывать", "show": "показывать",
    "get": "получать",

    # KJV / Strong's specific
    "account": "отчёт", "cause": "причина",
    "communication": "общение", "concerning": "касающийся",
    "doctrine": "учение", "fame": "молва",
    "intent": "намерение", "matter": "дело",
    "preaching": "проповедь", "question": "вопрос",
    "reason": "причина", "reckon": "считать",
    "remove": "удалять", "saying": "речение",
    "speaker": "говорящий", "speech": "речь",
    "talk": "разговор", "tidings": "вести",
    "treatise": "трактат", "utterance": "изречение",
    "dear": "дорогой", "feast": "пиршество",
    "affection": "привязанность", "benevolence": "благоволение",
    "something": "нечто", "nothing": "ничто",
    "thought": "мысль", "topic": "тема",
    "subject": "предмет", "discourse": "рассуждение",
    "reasoning": "рассуждение", "mental": "мыслительный",
    "faculty": "способность", "motive": "мотив",
    "computation": "вычисление", "expression": "выражение",
    "alphabet": "алфавит", "numeral": "числительное",
    "supreme": "верховный", "deference": "почтение",
    "magistrate": "судья", "magistrates": "судьи",
    "superlative": "превосходная степень",
    "exceeding": "превосходящий", "mighty": "могущественный",
    "judges": "судьи", "ordinary": "обычный",
    "chief": "начальник", "principal": "главный",
    "patrimony": "наследство",
    "trouble": "беда", "ward": "стража",
    "present": "настоящий", "round": "круглый",
    "utterly": "совершенно", "surely": "несомненно",
    "comparative": "сравнительный",
    "negative": "отрицательный",
    "intensive": "усилительный",
    "contracted": "сокращённый",
    "transitively": "переходно",
    "intransitive": "непереходный", "transitive": "переходный",
    "subjectively": "субъективно", "objectively": "объективно",
    "obsolete": "устаревший",
    "strengthened": "усиленный",
    "prepositional": "предложный",
    "diminutive": "уменьшительный",
    "accusative": "винительный падеж",
    "reduplication": "удвоение",
    "patronymic": "отчество", "contraction": "сокращение",
    "vowel": "гласная",
    "tenses": "времена",
    "interposed": "вставленный",
    "physically": "физически",
    "mentally": "мысленно",
    "causative": "каузативный",

    # Frequent content words missing from above
    "hence": "отсюда", "uncertain": "неопределённый",
    "identical": "тождественный",
    "various": "различный", "variety": "разнообразие",
    "means": "средство", "margin": "край",
    "order": "порядок", "among": "среди",
    "because": "потому что", "now": "теперь",
    "above": "выше", "below": "ниже",
    "towards": "к", "forward": "вперёд",
    "abroad": "за границей", "aside": "в сторону",
    "thoroughly": "тщательно", "wholly": "полностью",
    "indeed": "действительно", "otherwise": "иначе",
    "therefore": "поэтому", "further": "далее",
    "beyond": "за пределами", "both": "оба",
    "these": "эти", "those": "те", "them": "них",
    "they": "они", "him": "него", "itself": "само",
    "selves": "сами",
    "ever": "когда-либо",

    # Nouns
    "lie": "ложь", "ruin": "разрушение", "age": "возраст",
    "men": "мужи", "male": "мужской", "female": "женский",
    "sore": "рана", "idol": "идол", "animal": "животное",
    "pleasure": "удовольствие", "heap": "куча",
    "tribe": "колено", "spot": "пятно",
    "portion": "доля", "fat": "жир",
    "charge": "ответственность", "force": "сила",
    "piece": "кусок", "point": "точка",
    "sorrow": "печаль", "branch": "ветвь",
    "wound": "рана", "waste": "пустошь",
    "delight": "наслаждение", "sight": "зрение",
    "space": "пространство", "rock": "скала",
    "ground": "почва", "meat": "мясо",
    "wind": "ветер", "secret": "тайна",
    "reward": "награда", "shame": "стыд",
    "hair": "волосы", "grain": "зерно",
    "gift": "дар", "dark": "тёмный",
    "abundance": "изобилие", "seat": "место",
    "pain": "боль", "gold": "золото",
    "height": "высота", "pit": "яма",
    "prince": "князь", "bird": "птица",
    "home": "дом", "grief": "горе",
    "care": "забота", "rain": "дождь",
    "burden": "бремя", "possession": "владение",
    "family": "семья", "season": "время года",
    "fountain": "источник", "tower": "башня",
    "heat": "жар", "violence": "насилие",
    "linen": "лён", "cloth": "ткань",
    "foundation": "основание", "distress": "бедствие",
    "step": "шаг", "beast": "зверь",
    "army": "войско", "language": "язык",
    "plain": "равнина", "instrument": "орудие",
    "chain": "цепь", "lot": "жребий",
    "building": "здание", "sun": "солнце",
    "money": "деньги", "office": "должность",
    "course": "ход", "wealth": "богатство",
    "bed": "постель", "ruler": "правитель",
    "village": "селение", "multitude": "множество",
    "color": "цвет", "cattle": "скот",
    "salt": "соль", "resemblance": "подобие",
    "title": "титул",
    "mistake": "ошибка",
    "war": "война",
    "occasion": "повод",
    "Roman": "римский", "roman": "римский",
    "jewish": "иудейский", "chaldee": "халдейский",
    "babylonian": "вавилонский", "syrian": "сирийский",
    "edomite": "идумеянин",
    "patriarch": "патриарх",
    "jerusalem": "Иерусалим", "egypt": "Египет",
    "asia": "Асия", "syria": "Сирия",
    "abraham": "Авраам",
    "deity": "божество",
    "lemma": "лемма",

    # Adjectives
    "false": "ложный", "vain": "тщетный",
    "quiet": "тихий", "clear": "ясный",
    "hard": "тяжёлый", "fast": "быстрый",
    "wild": "дикий", "empty": "пустой",
    "fine": "тонкий", "red": "красный",
    "white": "белый", "bright": "яркий",
    "sweet": "сладкий", "pleasant": "приятный",
    "afraid": "боящийся", "sick": "больной",
    "dead": "мёртвый", "wicked": "нечестивый",
    "ready": "готовый", "broken": "сломанный",
    "complete": "полный", "perfect": "совершенный",
    "native": "местный", "minor": "малый",
    "appointed": "назначенный", "double": "двойной",
    "moral": "нравственный", "public": "общественный",
    "large": "большой", "low": "низкий",
    "whole": "целый", "hollow": "полый",
    "born": "рождённый",

    # Verbs
    "fail": "терпеть неудачу", "grow": "расти",
    "depart": "уходить", "move": "двигать",
    "increase": "увеличивать", "deliver": "избавлять",
    "suffer": "страдать", "meet": "встречать",
    "pour": "лить", "drive": "гнать",
    "receive": "получать", "burn": "гореть",
    "wait": "ждать", "close": "закрывать",
    "declare": "объявлять", "bind": "связывать",
    "appoint": "назначать", "regard": "уважать",
    "hide": "прятать", "utter": "произносить",
    "consider": "рассматривать", "commit": "совершать",
    "follow": "следовать", "shut": "закрывать",
    "spoil": "грабить", "throw": "бросать",
    "shake": "трясти", "divide": "разделять",
    "deal": "обращаться", "stretch": "простирать",
    "continue": "продолжать", "cease": "прекращать",
    "consume": "поглощать", "tell": "говорить",
    "slaughter": "заклание", "lift": "поднимать",
    "press": "давить", "separate": "разделять",
    "perish": "погибать", "watch": "сторожить",
    "dwell": "обитать", "blow": "дуть",
    "pluck": "вырывать", "render": "воздавать",
    "raise": "поднимать", "answer": "отвечать",
    "change": "менять", "join": "присоединять",
    "sleep": "спать", "fold": "складывать",
    "seek": "искать", "beat": "бить",
    "direct": "направлять", "trust": "доверять",
    "shine": "сиять", "scatter": "рассеивать",
    "smite": "поражать", "fill": "наполнять",
    "offer": "приносить в жертву",
    "search": "искать", "slay": "убивать",
    "respect": "уважать", "travel": "путешествовать",
    "rejoice": "радоваться", "shoot": "стрелять",
    "thrust": "пронзать", "appear": "являться",
    "flow": "течь", "tear": "рвать",
    "sail": "плыть",
    "can": "мочь", "given": "данный",
    "rising": "восхождение",

    # Verbs in -ing form
    "including": "включая", "meaning": "означающий",
    "being": "будучи", "covering": "покрытие",
    "burning": "горение", "missing": "пропущенный",

    # Misc
    "use": "употребление", "early": "ранний",
    "second": "второй", "third": "третий",
    "rank": "ранг",
    "desolate": "опустошённый", "faint": "слабый",
    "spring": "источник", "fore": "передний",
    "persons": "люди", "words": "слова",
    "others": "прочие", "times": "раз",
    "places": "места", "soever": "-либо",
    "behold": "вот", "journey": "путешествие",
    "escape": "бегство",
    "religious": "религиозный",

    # KJV notation
    "idiom": "",
    "ful": "",

    # Additional frequent words from Strong's
    "immediate": "непосредственный", "immediately": "непосредственно",
    "remote": "удалённый", "remotely": "удалённо",
    "applied": "применённый", "apply": "применять",
    "derived": "производный", "derive": "происходить",
    "messenger": "вестник", "pastor": "пастырь",
    "divinity": "божественность",
    "hebraism": "гебраизм",
    "godly": "благочестивый", "godward": "богонаправленный",
    "goddess": "богиня",
    "forefather": "праотец", "fatherless": "безотцовый",
    "none": "никакой", "me": "мне", "my": "мой",
    "we": "мы", "us": "нас", "you": "ты", "your": "твой",
    "immediate": "непосредственный",
    "applied": "применённый",
    "feast": "пиршество",
    "verbal": "глагольный",
    "implied": "подразумеваемый",
    "notion": "понятие",
    "abstract": "отвлечённый",
    "literally": "буквально",
    "specifically": "конкретно",
    "express": "выражать",
    "describe": "описывать",
    "denote": "обозначать",
    "refer": "относиться",
    "relate": "относиться",
    "involve": "включать",
    "imply": "подразумевать",
    "assume": "предполагать",
    "suppose": "предполагать",
    "represent": "представлять",
    "correspond": "соответствовать",
    "designate": "обозначать",
    "indicate": "указывать",
    "signify": "означать",
    "apply": "применять",
    "employ": "употреблять",
    "collect": "собирать",
    "constitute": "составлять",
    "establish": "утверждать",
    "maintain": "поддерживать",
    "obtain": "получать",
    "perform": "совершать",
    "produce": "производить",
    "provide": "обеспечивать",
    "require": "требовать",
    "suppose": "предполагать",
    "wither": "увядать",
    "approach": "приближаться",
    "attach": "присоединять",
    "defend": "защищать",
    "descend": "нисходить",
    "ascend": "восходить",
    "embrace": "обнимать",
    "inherit": "наследовать",
    "inhabit": "населять",
    "prepare": "готовить",
    "prevail": "преобладать",
    "possess": "владеть",
    "protect": "защищать",
    "pursue": "преследовать",
    "restore": "восстанавливать",
    "reveal": "открывать",
    "surround": "окружать",
    "swallow": "проглатывать",
    "transgress": "нарушать",
    "trample": "попирать",
    "wrap": "оборачивать",
    "adorn": "украшать",
    "anoint": "помазывать",
    "assemble": "собирать",
    "avenge": "мстить",
    "bewail": "оплакивать",
    "bore": "просверливать",
    "cleave": "рассекать",
    "clothe": "одевать",
    "contend": "состязаться",
    "create": "создавать",
    "defraud": "обманывать",
    "devote": "посвящать",
    "devour": "пожирать",
    "exalt": "возвышать",
    "exchange": "обменивать",
    "forbid": "запрещать",
    "glorify": "прославлять",
    "groan": "стонать",
    "kindle": "зажигать",
    "lament": "оплакивать",
    "mourn": "скорбеть",
    "murmur": "роптать",
    "nourish": "питать",
    "oppress": "угнетать",
    "ordain": "рукополагать",
    "overthrow": "опрокидывать",
    "pierce": "пронзать",
    "plead": "молить",
    "pledge": "залог",
    "plunder": "грабить",
    "pollute": "осквернять",
    "proclaim": "провозглашать",
    "profit": "выгода",
    "prosper": "процветать",
    "provoke": "провоцировать",
    "purify": "очищать",
    "redeem": "искупать",
    "refine": "очищать",
    "refuse": "отказываться",
    "release": "отпускать",
    "repent": "каяться",
    "reproach": "укорять",
    "resist": "противиться",
    "revive": "оживлять",
    "sanctify": "освящать",
    "seize": "схватить",
    "subdue": "покорять",
    "sustain": "поддерживать",
    "swear": "клясться",
    "testify": "свидетельствовать",
    "tremble": "трепетать",
    "trespass": "преступать",
    "trouble": "смущать",
    "wander": "блуждать",
    "warn": "предупреждать",
    "witness": "свидетель",
    "wonder": "чудо",
    "inhabitant": "житель",
    "inhabitants": "жители",
    "offering": "приношение",
    "overflow": "переполнять",
    "overshadow": "осенять",
    "practice": "практиковать",

    # Colors, materials
    "blue": "голубой", "green": "зелёный",
    "purple": "пурпурный", "scarlet": "алый",
    "brass": "медь", "iron": "железо",
    "silver": "серебро", "copper": "медь",
    "clay": "глина", "wood": "дерево",
    "oil": "масло", "honey": "мёд",
    "milk": "молоко", "wool": "шерсть",
    "ivory": "слоновая кость",

    # Body parts
    "bone": "кость", "flesh": "плоть",
    "neck": "шея", "shoulder": "плечо",
    "knee": "колено", "finger": "палец",
    "lip": "губа", "lips": "губы",
    "tongue": "язык", "tooth": "зуб",
    "teeth": "зубы", "rib": "ребро",
    "bosom": "лоно", "belly": "чрево",
    "thigh": "бедро", "heel": "пята",

    # Nature
    "star": "звезда", "moon": "луна",
    "cloud": "облако", "storm": "буря",
    "snow": "снег", "frost": "мороз",
    "dew": "роса", "hail": "град",
    "dust": "прах", "sand": "песок",
    "valley": "долина", "hill": "холм",
    "cave": "пещера", "forest": "лес",
    "garden": "сад", "vineyard": "виноградник",
    "wilderness": "пустыня",
    "harvest": "жатва", "flock": "стадо",
    "ox": "вол", "lamb": "агнец",
    "sheep": "овца", "goat": "козёл",
    "horse": "конь", "donkey": "осёл",
    "camel": "верблюд", "lion": "лев",
    "serpent": "змей", "fish": "рыба",
    "dove": "голубь", "eagle": "орёл",
    "worm": "червь", "locust": "саранча",
    "insect": "насекомое",

    # Household / objects
    "cup": "чаша", "bowl": "чаша",
    "pot": "горшок", "basket": "корзина",
    "lamp": "светильник", "candle": "свеча",
    "table": "стол", "pillar": "столб",
    "tent": "шатёр", "roof": "крыша",
    "floor": "пол", "window": "окно",
    "curtain": "завеса", "cord": "верёвка",
    "nail": "гвоздь", "yoke": "ярмо",
    "plow": "плуг", "net": "сеть",
    "snare": "западня", "trap": "ловушка",

    # Warfare
    "bow": "лук", "arrow": "стрела",
    "spear": "копьё", "helmet": "шлем",
    "armor": "доспехи", "chariot": "колесница",
    "battle": "битва", "victory": "победа",
    "captive": "пленник", "captivity": "плен",
    "siege": "осада", "terror": "ужас",
    "stranger": "чужеземец", "alien": "пришелец",

    # Social / religious
    "widow": "вдова", "orphan": "сирота",
    "virgin": "дева", "bride": "невеста",
    "marriage": "брак", "divorce": "развод",
    "oath": "клятва", "vow": "обет",
    "tithe": "десятина", "firstborn": "первенец",
    "circumcision": "обрезание",
    "sabbath": "суббота", "passover": "пасха",
    "jubilee": "юбилей",
    "scribe": "книжник", "elder": "старейшина",
    "deacon": "диакон", "apostle": "апостол",
    "disciple": "ученик",
    "gentile": "язычник", "gentiles": "язычники",
    "heathen": "язычник",
    "pagan": "языческий",
    "congregation": "собрание",
    "synagogue": "синагога",
    "church": "церковь",
    "baptism": "крещение",
    "communion": "причастие",
    "repentance": "покаяние",
    "atonement": "искупление",
    "intercession": "ходатайство",
    "revelation": "откровение",
    "parable": "притча",
    "miracle": "чудо",
    "sign": "знамение",
    "glory": "слава",
    "majesty": "величие",
    "throne": "престол",
    "scepter": "скипетр",
    "banner": "знамя",

    # Biblical names
    "John": "Иоанн", "john": "Иоанн",
    "Moses": "Моисей", "moses": "Моисей",
    "David": "Давид", "david": "Давид",
    "Solomon": "Соломон", "solomon": "Соломон",
    "Israel": "Израиль", "israel": "Израиль",
    "Jacob": "Иаков", "jacob": "Иаков",
    "Isaac": "Исаак", "isaac": "Исаак",
    "Joseph": "Иосиф", "joseph": "Иосиф",
    "Aaron": "Аарон", "aaron": "Аарон",
    "Daniel": "Даниил", "daniel": "Даниил",
    "Paul": "Павел", "paul": "Павел",
    "Peter": "Пётр", "peter": "Пётр",
    "Babylon": "Вавилон", "babylon": "Вавилон",
    "Zion": "Сион", "zion": "Сион",
    "Adam": "Адам", "adam": "Адам",
    "Noah": "Ной", "noah": "Ной",
    "Canaan": "Ханаан", "canaan": "Ханаан",
    "Judah": "Иуда", "judah": "Иуда",
    "Alpha": "Альфа", "alpha": "альфа",
    "Omega": "Омега", "omega": "омега",
    "Abi": "Аби", "abi": "аби",

    # More words to avoid transliteration
    "include": "включать", "including": "включая",
    "subject": "предмет", "object": "предмет",
    "the": "", "with": "с",
    "me": "мне", "my": "мой",
    "consider": "рассматривать",
    "regard": "уважать",
    "probable": "вероятный", "probably": "вероятно",
    "possible": "возможный", "possibly": "возможно",
    "necessary": "необходимый",
    "natural": "природный", "naturally": "естественно",
    "similar": "подобный", "differently": "по-другому",
    "simply": "просто", "directly": "прямо",
    "exactly": "точно", "entirely": "полностью",
    "chiefly": "главным образом",
    "ordinary": "обычный", "ordinarily": "обычно",
    "usually": "обычно",
    "separate": "отдельный", "separately": "отдельно",
    "collect": "собирать", "collectively": "собирательно",
    "necessary": "необходимый",
    "voluntary": "добровольный",
    "actual": "действительный", "actually": "действительно",
    "certain": "определённый", "certainly": "несомненно",
    "relative": "относительный", "relatively": "относительно",
    "physical": "физический", "physically": "физически",
    "typical": "типичный", "typically": "типично",
    "common": "общий", "commonly": "обычно",
    "particular": "частный", "particularly": "в частности",
    "local": "местный", "locally": "местно",
    "special": "особый", "special": "особый",
    "general": "общий", "generally": "обычно",
    "specific": "конкретный",

    # Compounds and forms still slipping through
    "love-feast": "вечеря любви",
    "god-ward": "к Богу",
    "love-feast": "вечеря любви",

    # Verbs that appear often but were missing
    "include": "включать",
    "exclude": "исключать",
    "attach": "присоединять",
    "determine": "определять",
    "accomplish": "совершать",
    "accomplish": "совершать",
    "acquire": "приобретать",
    "acknowledge": "признавать",
    "administer": "управлять",
    "affect": "влиять",
    "affirm": "утверждать",
    "agree": "соглашаться",
    "allow": "позволять",
    "announce": "объявлять",
    "attempt": "пытаться",
    "attend": "присутствовать",
    "belong": "принадлежать",
    "care": "заботиться",
    "celebrate": "праздновать",
    "combine": "сочетать",
    "command": "повелевать",
    "compare": "сравнивать",
    "complete": "завершать",
    "conceal": "скрывать",
    "concern": "касаться",
    "confirm": "подтверждать",
    "connect": "соединять",
    "contain": "содержать",
    "correct": "исправлять",
    "corrupt": "развращать",
    "cultivate": "возделывать",
    "define": "определять",
    "depend": "зависеть",
    "deprive": "лишать",
    "differ": "различаться",
    "diminish": "уменьшать",
    "discover": "обнаруживать",
    "display": "показывать",
    "distribute": "распределять",
    "disturb": "нарушать",
    "enable": "давать возможность",
    "encourage": "ободрять",
    "endure": "переносить",
    "enforce": "применять",
    "enjoy": "наслаждаться",
    "enter": "входить",
    "examine": "исследовать",
    "exist": "существовать",
    "expand": "расширять",
    "expect": "ожидать",
    "explain": "объяснять",
    "extend": "простирать",
    "honor": "почитать",
    "humble": "смиренный",
    "identify": "определять",
    "ignore": "игнорировать",
    "imagine": "воображать",
    "improve": "улучшать",
    "influence": "влиять",
    "inform": "сообщать",
    "injure": "ранить",
    "inspire": "вдохновлять",
    "intend": "намереваться",
    "introduce": "вводить",
    "invite": "приглашать",
    "judge": "судить",
    "justify": "оправдывать",
    "manage": "управлять",
    "mention": "упоминать",
    "multiply": "умножать",
    "neglect": "пренебрегать",
    "notice": "замечать",
    "observe": "наблюдать",
    "occupy": "занимать",
    "overcome": "побеждать",
    "owe": "быть должным",
    "pardon": "прощать",
    "permit": "позволять",
    "persuade": "убеждать",
    "preach": "проповедовать",
    "prefer": "предпочитать",
    "preserve": "сохранять",
    "prevent": "предотвращать",
    "promise": "обещать",
    "punish": "наказывать",
    "recognize": "признавать",
    "recommend": "рекомендовать",
    "reduce": "уменьшать",
    "reflect": "отражать",
    "reform": "реформировать",
    "reject": "отвергать",
    "remember": "помнить",
    "remove": "удалять",
    "renew": "обновлять",
    "repeat": "повторять",
    "replace": "заменять",
    "report": "сообщать",
    "rescue": "спасать",
    "resemble": "походить",
    "resolve": "решать",
    "restrain": "удерживать",
    "retain": "удерживать",
    "satisfy": "удовлетворять",
    "select": "выбирать",
    "share": "разделять",
    "spare": "щадить",
    "succeed": "преуспевать",
    "support": "поддерживать",
    "suspect": "подозревать",
    "threaten": "угрожать",
    "touch": "касаться",
    "transfer": "переносить",
    "transform": "преображать",
    "unite": "объединять",
    "value": "ценить",
    "violate": "нарушать",
    "wipe": "стирать",

    # Remaining frequent words found in Strong's definitions
    "else": "иначе", "different": "различный",
    "firm": "твёрдый", "trustworthy": "надёжный",
    "Jehovah": "Иегова", "jehovah": "Иегова",
    "beth": "бет", "baal": "Ваал",
    "dry": "сухой", "was": "был", "are": "суть",
    "while": "пока", "far": "далёкий",
    "able": "способный", "plant": "растение",
    "wax": "расти", "stay": "оставаться",
    "strike": "ударять", "past": "прошлый",
    "fellow": "товарищ", "palm": "ладонь",
    "room": "комната", "treasure": "сокровище",
    "pure": "чистый", "gem": "драгоценный камень",
    "according": "согласно",
    "stick": "палка", "vision": "видение",
    "flame": "пламя", "south": "юг",
    "toil": "труд", "were": "были",
    "substance": "вещество", "feed": "кормить",
    "yield": "приносить", "front": "передний",
    "habitation": "жилище", "service": "служение",
    "teacher": "учитель",
    "sure": "верный", "soon": "скоро",
    "tarry": "медлить", "violent": "насильственный",
    "conjunction": "союз", "punishment": "наказание",
    "catch": "ловить", "prison": "темница",
    "opposite": "противоположный", "ordinal": "порядковый",
    "dwelling": "жилище", "ornament": "украшение",
    "except": "кроме", "hot": "горячий",
    "flee": "бежать", "exercise": "упражнять",
    "greatly": "весьма", "better": "лучший",
    "short": "короткий", "advance": "продвигать",
    "though": "хотя", "clothing": "одежда",
    "equivalent": "равнозначный", "bound": "связанный",
    "decree": "указ", "alone": "один",
    "weight": "вес", "silence": "молчание",
    "national": "национальный", "Jewish": "иудейский",
    "Israelitess": "израильтянка",
    "orthographical": "орфографический",
    "nethinim": "нефинеи",
    "going": "хождение",

    # Common KJV-style words
    "good": "добрый", "self-existent": "Самосущий",
    "ably": "способно", "less": "менее",
    "ish": "", "ite": "", "ites": "",

    # More adjectives / adverbs
    "exact": "точный", "proper": "правильный",
    "common": "обычный", "frequent": "частый",
    "severe": "тяжёлый", "gentle": "кроткий",
    "humble": "смиренный", "proud": "гордый",
    "safe": "безопасный", "dangerous": "опасный",
    "cruel": "жестокий", "bitter": "горький",
    "precious": "драгоценный", "abundant": "обильный",
    "diligent": "усердный", "faithful": "верный",
    "foolish": "глупый", "guilty": "виновный",
    "innocent": "невинный", "naked": "нагой",
    "rough": "грубый", "smooth": "гладкий",
    "straight": "прямой", "crooked": "кривой",
    "thick": "толстый", "thin": "тонкий",
    "narrow": "узкий", "broad": "широкий",
    "swift": "быстрый", "slow": "медленный",
    "heavy": "тяжёлый", "soft": "мягкий",
    "sharp": "острый", "dull": "тупой",
    "strange": "странный", "wonderful": "чудный",
    "terrible": "страшный", "glorious": "славный",
    "beautiful": "красивый", "ugly": "безобразный",
    "ancient": "древний", "modern": "современный",
    "sacred": "священный", "profane": "нечестивый",
    "northern": "северный", "southern": "южный",
    "eastern": "восточный", "western": "западный",
    "upper": "верхний", "lower": "нижний",
    "inner": "внутренний", "outer": "внешний",
    "visible": "видимый", "invisible": "невидимый",
    "personal": "личный", "central": "центральный",
    "national": "национальный", "royal": "царский",
    "noble": "благородный",

    # Remaining verbs
    "fly": "летать", "swim": "плавать",
    "crawl": "ползать", "leap": "прыгать",
    "dance": "танцевать", "wave": "махать",
    "bow": "кланяться", "kneel": "преклонять колени",
    "climb": "взбираться", "dig": "копать",
    "sow": "сеять", "reap": "жать",
    "grind": "молоть", "weave": "ткать",
    "spin": "прясть", "melt": "плавить",
    "boil": "кипеть", "roast": "жарить",
    "bake": "печь", "wash": "мыть",
    "drown": "тонуть", "strangle": "удушать",
    "hang": "вешать", "crucify": "распинать",
    "bury": "погребать", "baptize": "крестить",
    "circumcise": "обрезать", "elect": "избирать",
    "condemn": "осуждать", "acquit": "оправдывать",
    "accuse": "обвинять", "betray": "предавать",
    "tempt": "искушать", "flee": "убегать",
    "march": "маршировать", "conquer": "побеждать",
    "surrender": "сдаваться", "plunder": "грабить",
    "loot": "добыча",
    "enslave": "порабощать",
    "confess": "исповедовать",
    "prophesy": "пророчествовать",
    "interpret": "толковать",
    "translate": "переводить",
    "count": "считать",
    "measure": "измерять",
    "satisfy": "удовлетворять",
    "comfort": "утешать",
    "mourn": "скорбеть",
    "fast": "поститься",
    "feast": "пиршествовать",
    "marry": "жениться",
    "beget": "рождать",
    "conceive": "зачинать",
    "nurse": "кормить грудью",
    "wean": "отнимать от груди",
    "adopt": "усыновлять",
    "borrow": "занимать",
    "lend": "давать взаймы",
    "steal": "красть",
    "rob": "грабить",
    "cheat": "обманывать",
    "swear": "клясться",
    "curse": "проклинать",
    "mock": "насмехаться",
    "scorn": "презирать",
    "envy": "завидовать",
    "covet": "вожделеть",
    "lust": "похоть",
    "stumble": "спотыкаться",
    "stray": "заблудиться",
    "wander": "блуждать",
    "err": "ошибаться",
    "transgress": "нарушать",
    "sin": "грешить",

    # More nouns frequently in Strong's
    "joy": "радость", "gladness": "веселие",
    "grief": "горе", "anguish": "мука",
    "woe": "горе", "tribulation": "скорбь",
    "affliction": "скорбь", "persecution": "гонение",
    "plague": "мор", "pestilence": "язва",
    "famine": "голод", "drought": "засуха",
    "flood": "потоп", "earthquake": "землетрясение",
    "thunder": "гром", "lightning": "молния",
    "rainbow": "радуга",
    "pillar": "столб", "column": "столб",
    "beam": "балка", "rod": "жезл",
    "staff": "посох", "rope": "верёвка",
    "thread": "нить", "needle": "игла",
    "wheel": "колесо", "axle": "ось",
    "boat": "лодка", "ship": "корабль",
    "sail": "парус", "anchor": "якорь",
    "shore": "берег", "island": "остров",
    "harbor": "гавань",
    "tent": "шатёр", "booth": "шалаш",
    "altar": "жертвенник", "incense": "фимиам",
    "perfume": "благовоние",
    "scroll": "свиток", "book": "книга",
    "ink": "чернила", "seal": "печать",
    "coin": "монета", "talent": "талант",
    "wage": "плата", "wages": "заработок",
    "tax": "подать", "tribute": "дань",
    "debt": "долг", "pledge": "залог",
    "gift": "дар", "bribe": "взятка",
    "ransom": "выкуп", "price": "цена",
    "profit": "прибыль",
    "grain": "зерно", "wheat": "пшеница",
    "barley": "ячмень", "grape": "виноград",
    "fig": "смоква", "olive": "маслина",
    "cedar": "кедр", "oak": "дуб",
    "reed": "тростник", "thorn": "колючка",
    "thistle": "чертополох",
    "rose": "роза", "lily": "лилия",

    # Suffixes that appear as standalone fragments
    "ing": "", "ly": "", "er": "", "ness": "",
    "ed": "", "en": "", "ful": "", "ish": "",
    "un": "не", "ite": "", "th": "",
    "ion": "", "ment": "", "ive": "",
    "xlit": "",  # artifact from data

    # Final batch of missing words
    "interjection": "междометие",
    "spoken": "произнесённый",
    "connective": "соединительный",
    "womb": "чрево",
    "exclamation": "восклицание",
    "affirmation": "утверждение",
    "negation": "отрицание",
    "interrogation": "вопрос",
    "denoting": "обозначающий",
    "expressing": "выражающий",
    "indefinite": "неопределённый",
    "definite": "определённый",
    "demonstrative": "указательный",
    "possessive": "притяжательный",
    "reciprocal": "взаимный",
    "reflexive": "возвратный",
    "emphatic": "усилительный",
    "enclitic": "энклитический",
    "proclitic": "проклитический",
    "copulative": "соединительный",
    "disjunctive": "разделительный",
    "relative": "относительный",
    "conditional": "условный",
    "temporal": "временный",
    "causal": "причинный",
    "final": "целевой",
    "consecutive": "последовательный",
    "concessive": "уступительный",
    "adversative": "противительный",
    "explanatory": "пояснительный",
    "distributive": "разделительный",
    "correlative": "соотносительный",
    "Hebraism": "гебраизм",
    "Chaldaism": "халдаизм",
    "Aramaism": "арамеизм",

    # Extra nouns/adjectives
    "north": "север", "west": "запад",
    "narrow": "узкий", "broad": "широкий",
    "royal": "царский",
    "numerous": "многочисленный",
    "poor": "нищий",
    "sorrowful": "печальный",
    "joyful": "радостный",
    "merciful": "милосердный",
    "gracious": "милостивый",
    "jealous": "ревнивый",
    "zealous": "ревностный",
    "careful": "тщательный",
    "powerful": "могущественный",
    "fearful": "боязливый",
    "grateful": "благодарный",
    "trustful": "доверчивый",
    "skillful": "искусный",
    "rightful": "законный",
    "plentiful": "обильный",
    "bountiful": "щедрый",
    "cheerful": "весёлый",
    "fruitful": "плодоносный",
    "peaceful": "мирный",
    "shameful": "постыдный",
    "respectful": "почтительный",
    "wrathful": "гневный",
    "sinful": "греховный",
    "hateful": "ненавистный",
    "deceitful": "обманчивый",
    "unfaithful": "неверный",
}

PUNCT_END_RE = re.compile(r'^(.*?)([.,;:!?()\[\]{}"\']+)$')
PUNCT_START_RE = re.compile(r'^([.,;:!?()\[\]{}"\']+)(.*?)$')
LATIN_RE = re.compile(r'[a-zA-Z]')

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
    'zh': 'ж', 'kh': 'х', 'ts': 'ц', 'oo': 'у',
}


def transliterate(w):
    if not w:
        return w
    cap = w[0].isupper()
    lo = w.lower()
    r = []
    i = 0
    while i < len(lo):
        if i + 1 < len(lo) and lo[i:i+2] in DIGRAPHS:
            r.append(DIGRAPHS[lo[i:i+2]])
            i += 2
        else:
            r.append(TRANSLIT.get(lo[i], lo[i]))
            i += 1
    out = ''.join(r)
    return (out[0].upper() + out[1:]) if cap and out else out


def tr_word(w):
    if not w:
        return w
    if w in WORD_MAP:
        return WORD_MAP[w]
    # Strip leading punctuation
    ms = PUNCT_START_RE.match(w)
    if ms:
        punct, core = ms.group(1), ms.group(2)
        t = tr_word(core)
        return punct + t
    # Strip trailing punctuation
    m = PUNCT_END_RE.match(w)
    if m:
        core, punct = m.group(1), m.group(2)
        t = tr_word(core)
        return t + punct
    lo = w.lower()
    if lo in WORD_MAP:
        r = WORD_MAP[lo]
        if w[0].isupper() and r:
            return r[0].upper() + r[1:]
        return r
    # Handle hyphenated words: translate each part
    if '-' in w:
        parts = w.split('-')
        translated = [tr_word(p) for p in parts]
        return '-'.join(translated)
    # Handle -ly suffix
    if lo.endswith('ly') and lo[:-2] in WORD_MAP:
        return WORD_MAP[lo[:-2]]
    # Handle -ness suffix
    if lo.endswith('ness') and lo[:-4] in WORD_MAP:
        return WORD_MAP[lo[:-4]]
    # Handle -ed suffix
    if lo.endswith('ed') and lo[:-2] in WORD_MAP:
        return WORD_MAP[lo[:-2]]
    if lo.endswith('ed') and lo[:-1] in WORD_MAP:
        return WORD_MAP[lo[:-1]]
    if lo.endswith('ed') and lo[:-2] + 'y' in WORD_MAP:
        return WORD_MAP[lo[:-2] + 'y']
    # Handle -er suffix
    if lo.endswith('er') and lo[:-2] in WORD_MAP:
        return WORD_MAP[lo[:-2]]
    if lo.endswith('er') and lo[:-1] in WORD_MAP:
        return WORD_MAP[lo[:-1]]
    # Handle -s suffix (plural)
    if lo.endswith('s') and lo[:-1] in WORD_MAP:
        return WORD_MAP[lo[:-1]]
    if lo.endswith('es') and lo[:-2] in WORD_MAP:
        return WORD_MAP[lo[:-2]]
    # Handle -ing suffix (multiple patterns)
    if lo.endswith('ing'):
        stem = lo[:-3]
        if stem in WORD_MAP:
            return WORD_MAP[stem]
        if stem + 'e' in WORD_MAP:
            return WORD_MAP[stem + 'e']
        if len(stem) > 1 and stem[-1] == stem[-2] and stem[:-1] in WORD_MAP:
            return WORD_MAP[stem[:-1]]
    # Handle -tion suffix
    if lo.endswith('tion') and lo[:-4] + 'te' in WORD_MAP:
        return WORD_MAP[lo[:-4] + 'te']
    # Handle -ment suffix
    if lo.endswith('ment') and lo[:-4] in WORD_MAP:
        return WORD_MAP[lo[:-4]]
    if lo.endswith('ment') and lo[:-4] + 'e' in WORD_MAP:
        return WORD_MAP[lo[:-4] + 'e']
    # Keep Strong's references
    if re.match(r'^[GH]\d+$', w):
        return w
    # Keep non-Latin as-is
    if not LATIN_RE.search(w):
        return w
    # Single chars like x, s, i, e (from KJV)
    if len(w) == 1:
        return w
    return transliterate(w)


def translate_text(text):
    if not text:
        return text
    cyrillic = len(re.findall(r'[а-яА-ЯёЁ]', text))
    if cyrillic > len(text) * 0.5:
        return text
    text = text.replace('i.e.', 'т.е.')
    text = text.replace('e.g.', 'напр.')
    tokens = re.split(r'(\s+)', text)
    result = ''.join(t if t.isspace() else tr_word(t) for t in tokens)
    result = re.sub(r'  +', ' ', result).strip()
    return result


def translate_kjv(kjv):
    if not kjv:
        return kjv
    parts = kjv.split(', ')
    out = []
    for part in parts:
        p = part.strip()
        if not p:
            continue
        # Remove [idiom], X prefix
        p = re.sub(r'\[idiom\]\s*', '', p)
        if p.startswith('X '):
            p = p[2:]
        # Translate each word
        words = p.split()
        tw = [tr_word(w) for w in words]
        result = ' '.join(w for w in tw if w)
        if result:
            out.append(result)
    return ', '.join(out) if out else kjv


def translate_origin(origin):
    if not origin:
        return origin
    # Specific patterns first
    text = origin
    text = re.sub(r'from (H\d+|G\d+)\s*\(([^)]*)\)', r'от \1 (\2)', text)
    text = re.sub(r'corresponding to (H\d+|G\d+)\s*\(([^)]*)\)', r'соответствует \1 (\2)', text)
    text = re.sub(r'of Hebrew origin', 'от евр.', text)
    text = re.sub(r'of Aramaic origin', 'от арам.', text)
    text = re.sub(r'of Latin origin', 'от лат.', text)
    text = re.sub(r'of Greek origin', 'от греч.', text)
    text = re.sub(r'a primitive word', 'корневое слово', text)
    text = re.sub(r'a primitive root', 'корневой глагол', text)
    text = re.sub(r'an unused root', 'неупотребляемый корень', text)
    text = re.sub(r'a prolonged form of', 'продлённая форма', text)
    text = re.sub(r'plural of', 'мн. число от', text)
    text = re.sub(r'feminine of', 'ж. род от', text)
    text = re.sub(r'masculine of', 'м. род от', text)
    text = re.sub(r'the same as', 'то же что', text)
    text = re.sub(r'Compare', 'Сравни', text)
    text = re.sub(r'compare', 'сравни', text)
    # Translate remaining words
    tokens = re.split(r'(\s+)', text)
    result = ''.join(t if t.isspace() else tr_word(t) for t in tokens)
    result = re.sub(r'  +', ' ', result).strip()
    return result


def parse_js_dict(filepath):
    with open(filepath) as f:
        text = f.read()
    for prefix in ['"H', '"G']:
        start = text.find('{' + prefix)
        if start >= 0:
            end = text.rfind('}')
            return json.loads(text[start:end+1])
    raise ValueError(f"Cannot parse {filepath}")


def normalize_key(key):
    prefix = key[0]
    num = key[1:]
    return f"{prefix}{int(num):04d}"


def build_entry(key, raw):
    lemma = raw.get('lemma', '')
    translit = raw.get('translit', '') or raw.get('xlit', '')
    pron = raw.get('pron', '')
    strongs_def = raw.get('strongs_def', '').strip().strip('{}').strip()
    kjv_def = raw.get('kjv_def', '')
    derivation = raw.get('derivation', '')

    entry = {"l": lemma}
    if translit:
        entry["t"] = translit
    if pron:
        entry["p"] = pron
    entry["d"] = translate_text(strongs_def)
    if kjv_def:
        entry["k"] = translate_kjv(kjv_def.strip())
    if derivation:
        entry["o"] = translate_origin(derivation.strip().rstrip(';').strip())
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
        result[normalize_key(key)] = build_entry(key, raw)
    for key, raw in hebrew.items():
        result[normalize_key(key)] = build_entry(key, raw)

    print(f"Total: {len(result)} entries")

    out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                            'app', 'src', 'main', 'assets', 'strongs_dictionary.json')
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, separators=(',', ':'))

    size_mb = os.path.getsize(out_path) / (1024 * 1024)
    print(f"Written to {out_path} ({size_mb:.1f} MB)")

    # Show samples
    for k in ['G3056', 'G0026', 'H0001', 'H0430', 'G2316', 'G0032']:
        if k in result:
            print(f"\n{k}:")
            for f2 in ['l', 'd', 'k', 'o']:
                print(f"  {f2}: {result[k].get(f2, '')}")

    # Count remaining Latin
    latin_count = 0
    total_fields = 0
    for e in result.values():
        for f2 in ['d', 'k']:
            text = e.get(f2, '')
            if text:
                total_fields += 1
                cleaned = re.sub(r'[GH]\d+', '', text)
                cleaned = re.sub(r"[-'()]", '', cleaned)
                if LATIN_RE.search(cleaned):
                    latin_count += 1
    pct = 100 * (total_fields - latin_count) / total_fields if total_fields else 0
    print(f"\nFully Russian: {total_fields - latin_count}/{total_fields} ({pct:.1f}%)")
    if latin_count > 0:
        shown = 0
        for k, e in result.items():
            for f2 in ['d', 'k']:
                text = e.get(f2, '')
                cleaned = re.sub(r'[GH]\d+', '', text)
                cleaned = re.sub(r"[-'()]", '', cleaned)
                if LATIN_RE.search(cleaned) and shown < 10:
                    # Find the latin words
                    words = re.findall(r'[a-zA-Z]+', cleaned)
                    print(f"  {k}.{f2}: latin words: {words[:5]}")
                    shown += 1


if __name__ == '__main__':
    main()
