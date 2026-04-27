#!/usr/bin/env python3
"""Convert MyBible SQLite3 dictionaries to compact JSON for Android assets."""
import sqlite3
import json
import os
import re
import html

DICTS = {
    'brockhaus': {
        'sqlite': '/tmp/mybible_dicts/brockhaus.dictionary.SQLite3',
        'label': 'Брокгауз',
    },
    'vikhlyantsev': {
        'sqlite': '/tmp/mybible_dicts/vikhlyantsev.dictionary.SQLite3',
        'label': 'Вихлянцев',
    },
    'nystrem': {
        'sqlite': '/tmp/mybible_dicts/nystrem.dictionary.SQLite3',
        'label': 'Нюстрем',
    },
    'nikifor': {
        'sqlite': '/tmp/mybible_dicts/nikifor.dictionary.SQLite3',
        'label': 'Никифор',
    },
}

TAG_RE = re.compile(r'<[^>]+>')
MULTI_SPACE = re.compile(r'[ \t]+')
MULTI_NL = re.compile(r'\n{3,}')


def clean_html(text: str) -> str:
    if not text:
        return ''
    # Replace <br> variants with newlines
    text = re.sub(r'<br\s*/?>', '\n', text, flags=re.IGNORECASE)
    # Replace </p> and </div> with newlines
    text = re.sub(r'</(?:p|div|li|tr)>', '\n', text, flags=re.IGNORECASE)
    # Remove all remaining tags
    text = TAG_RE.sub('', text)
    # Decode HTML entities
    text = html.unescape(text)
    # Normalize whitespace
    text = MULTI_SPACE.sub(' ', text)
    text = MULTI_NL.sub('\n\n', text)
    return text.strip()


def convert_dict(name: str, info: dict, out_dir: str):
    sqlite_path = info['sqlite']
    if not os.path.exists(sqlite_path):
        print(f"  SKIP {name}: {sqlite_path} not found")
        return

    conn = sqlite3.connect(sqlite_path)
    cursor = conn.cursor()
    cursor.execute('SELECT topic, definition FROM dictionary ORDER BY topic')
    
    result = {}
    for topic, definition in cursor.fetchall():
        topic = topic.strip()
        if not topic:
            continue
        clean = clean_html(definition)
        if clean:
            key = topic.lower()
            if key not in result or len(clean) > len(result[key]):
                result[key] = clean
            # Also store with original case as display key
            result[f"_display_{key}"] = topic

    conn.close()

    # Build final compact format: {"word": {"t": "Topic", "d": "definition"}}
    final = {}
    for key, val in result.items():
        if key.startswith('_display_'):
            continue
        display = result.get(f'_display_{key}', key)
        final[key] = {"t": display, "d": val}

    out_path = os.path.join(out_dir, f'{name}.json')
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(final, f, ensure_ascii=False, separators=(',', ':'))

    size_mb = os.path.getsize(out_path) / (1024 * 1024)
    print(f"  {name}: {len(final)} entries, {size_mb:.1f} MB")
    
    # Show sample
    for k in list(final.keys())[:3]:
        d = final[k]['d'][:100]
        print(f"    '{k}' -> {d}...")


def main():
    out_dir = os.path.join(
        os.path.dirname(os.path.abspath(__file__)), '..',
        'app', 'src', 'main', 'assets', 'dictionaries',
    )
    os.makedirs(out_dir, exist_ok=True)

    print("Converting MyBible dictionaries to JSON...")
    for name, info in DICTS.items():
        print(f"\n{info['label']}:")
        convert_dict(name, info, out_dir)

    print("\nDone!")
    total = sum(
        os.path.getsize(os.path.join(out_dir, f))
        for f in os.listdir(out_dir) if f.endswith('.json')
    )
    print(f"Total size: {total / (1024*1024):.1f} MB")


if __name__ == '__main__':
    main()
