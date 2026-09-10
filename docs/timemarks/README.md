# Таймкоды Библии на GitHub

В репозитории лежат только метки стихов (JSON). Аудио главы пользователи скачивают в приложении сами.

## Как выложить метки с телефона

1. В редакторе таймкодов сохраните главы.
2. Меню «Поделиться» → **Файл для GitHub (без аудио)**.
3. Отправьте ZIP на компьютер (почта, Telegram, диск).
4. Распакуйте: `catalog.json` и папка `projects/`.
5. Скопируйте JSON из `projects/` в `docs/timemarks/projects/`.
6. Из корня репозитория:

```bash
python3 scripts/rebuild_timemark_catalog.py
```

7. Закоммитьте и запушьте в `master`. После этого кнопка облака в редакторе увидит новые главы.

## Формат файла главы

`docs/timemarks/projects/{перевод}_{книга}_{глава}.json`

Пример: `syn_genesis_1.json`

```json
{
  "id": "gh_syn_genesis_1",
  "translationCode": "SYN",
  "bookId": "genesis",
  "chapter": 1,
  "title": "Бытие 1",
  "narratorId": "bondarenko",
  "cues": [
    { "timeMs": 0, "verseStart": 1 },
    { "timeMs": 4200, "verseStart": 2 }
  ]
}
```
