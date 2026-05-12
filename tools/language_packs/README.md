# Пакеты слов для «Изучение языков»

## Формат zip (в корне архива)

- `pack.json` — `{"lang":"english|greek|arabic|irit","version":"1.0.0","schema":1}`  
  Для иврита в приложении код языка `irit`, в пакете указывайте `"lang":"irit"`.
- `words.jsonl` — одна строка = один JSON-объект:
  - обязательные: `id`, `lemma`, `glossRu`
  - опциональные: `display`, `ipa`, `pos`, `frequencyRank`, `exampleL2`, `exampleRu`, `mnemonicHint`, `morphologyNotes`

## Сборка ≥1000 слов

```bash
cd tools/language_packs
python3 -m venv .venv && source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python3 build_packs.py   # нужен интернет (MyMemory для EN, Google через deep-translator для el/ar/iw)
```

В каталоге `dist/` появятся `english_v1.zip`, `greek_v1.zip`, `arabic_v1.zip`, `irit_v1.zip`.

Скопируйте их в приложение:

`app/src/main/assets/language_packs/bundled/`

## Лицензии и качество перевода

- Частотный порядок лемм опирается на библиотеку **wordfreq** (MIT):  
  https://github.com/rspeer/wordfreq
- Поле `glossRu` заполняется машинным переводом (**MyMemory** API при сборке) и для английского дополняется словарём `data/en_ru_core.json` (имеет приоритет над автоматическим переводом).
- Для **английского** в пакете задаются IPA (`eng-to-ipa`) и блок примеров: готовые фразы для частых служебных слов + универсальный учебный шаблон для остальных.
- Для **иврита, греческого, арабского** машинный перевод формы слова на русский заменяет прежние заглушки; вычитка приветствуется.

Автоматические глоссарии могут ошибаться — проверяйте по надёжным словарям.
