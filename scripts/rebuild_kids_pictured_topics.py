# -*- coding: utf-8 -*-
"""Восстанавливает KidsPicturedTopics.kt (животные со звуком, рыбы с фото, остальное как в генераторе)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "app/src/main/java/com/example/bible/data/KidsPicturedTopics.kt"
CORRUPT = DEST


def e(*codes: int) -> str:
    return "".join(chr(c) for c in codes)


def K(label: str, em: str, speak: str | None = None) -> str:
    if speak:
        return "        KidsPicturedItem(%s, speak = %s, emoji = %s)," % (
            json.dumps(label, ensure_ascii=False),
            json.dumps(speak, ensure_ascii=False),
            json.dumps(em, ensure_ascii=False),
        )
    return "        KidsPicturedItem(%s, emoji = %s)," % (
        json.dumps(label, ensure_ascii=False),
        json.dumps(em, ensure_ascii=False),
    )


def Ka(label: str, em: str, speak: str | None, sfx: str | None, pitch: float) -> str:
    sfx_part = ""
    if sfx == "d":
        sfx_part = ", soundRes = sfxDog, soundPitch = %sf" % pitch
    elif sfx == "c":
        sfx_part = ", soundRes = sfxCat, soundPitch = %sf" % pitch
    if speak:
        return "        KidsPicturedItem(%s, speak = %s, emoji = %s%s)," % (
            json.dumps(label, ensure_ascii=False),
            json.dumps(speak, ensure_ascii=False),
            json.dumps(em, ensure_ascii=False),
            sfx_part,
        )
    return "        KidsPicturedItem(%s, emoji = %s%s)," % (
        json.dumps(label, ensure_ascii=False),
        json.dumps(em, ensure_ascii=False),
        sfx_part,
    )


def main() -> None:
    bear = e(0x1F43B)
    polar = e(0x1F43B, 0x200D, 0x2744, 0xFE0F)
    wolf = e(0x1F43A)
    fox = e(0x1F98A)
    rabbit = e(0x1F430)
    squirrel = e(0x1F43F, 0xFE0F)
    deer = e(0x1F98C)
    moose = e(0x1FACE)
    boar = e(0x1F417)
    horse = e(0x1F434)
    cow = e(0x1F404)
    ox = e(0x1F402)
    pig = e(0x1F416)
    sheep = e(0x1F411)
    goat = e(0x1F410)
    dog = e(0x1F415)
    cat = e(0x1F408)
    elephant = e(0x1F418)
    giraffe = e(0x1F992)
    lion = e(0x1F981)
    tiger = e(0x1F42F)
    zebra = e(0x1F993)
    kangaroo = e(0x1F998)
    panda = e(0x1F43C)
    monkey = e(0x1F435)
    gorilla = e(0x1F98D)
    orangutan = e(0x1F9A7)
    hippo = e(0x1F99B)
    rhino = e(0x1F98F)
    croc = e(0x1F40A)
    camel2 = e(0x1F42B)
    llama = e(0x1F999)
    penguin = e(0x1F427)
    flamingo = e(0x1F9A9)
    peacock = e(0x1F99A)
    duck = e(0x1F986)
    goose = e(0x1FABF)
    swan = e(0x1F9A2)
    turkey = e(0x1F983)
    chicken = e(0x1F414)
    rooster = e(0x1F413)
    parrot = e(0x1F99C)
    crow = e(0x1F426, 0x200D, 0x2B1B)
    dove = e(0x1F54A, 0xFE0F)
    eagle = e(0x1F985)
    owl = e(0x1F989)
    bird = e(0x1F426)
    raccoon = e(0x1F99D)
    otter = e(0x1F9A6)
    beaver = e(0x1F9AB)
    seal = e(0x1F9AD)
    bat = e(0x1F987)
    hedgehog = e(0x1F994)
    paw = e(0x1F43E)
    snake = e(0x1F40D)
    turtle = e(0x1F422)
    lizard = e(0x1F98E)
    frog = e(0x1F438)
    dolphin = e(0x1F42C)
    whale = e(0x1F40B)

    ant = e(0x1F41C)
    bee = e(0x1F41D)
    butterfly = e(0x1F98B)
    fly = e(0x1FAB0)
    mosquito = e(0x1F99F)
    cricket = e(0x1F997)
    beetle = e(0x1FAB2)
    cockroach = e(0x1FAB3)
    worm = e(0x1FAB1)
    ladybug = e(0x1F41E)
    spider = e(0x1F577, 0xFE0F)
    scorpion = e(0x1F982)
    microbe = e(0x1F9A0)
    sparkle = e(0x2728)

    tree_deciduous = e(0x1F333)
    tree_evergreen = e(0x1F332)
    palm = e(0x1F334)
    chestnut_emoji = e(0x1F330)
    herb = e(0x1F33F)
    blossom = e(0x1F33C)
    rose = e(0x1F339)
    tulip = e(0x1F337)
    sunflower = e(0x1F33B)
    hibiscus = e(0x1F33A)
    cherry_blossom = e(0x1F338)
    bouquet = e(0x1F490)
    seedling = e(0x1F331)
    potted = e(0x1FAB4)
    cactus = e(0x1F335)
    four_leaf = e(0x1F340)
    sheaf = e(0x1F33E)
    lotus = e(0x1FAB7)
    hyacinth = e(0x1FABB)
    snowflake = e(0x2744, 0xFE0F)
    corn = e(0x1F33D)
    coral = e(0x1FAB8)

    yo_zh = "\u0401\u0436"

    animals_rows: list[tuple[str, str, str | None, str | None, float]] = [
        ("Медведь", bear, None, "d", 0.6),
        ("Белый медведь", polar, "Белый медведь", "d", 0.55),
        ("Волк", wolf, None, "d", 0.75),
        ("Лиса", fox, None, "d", 1.15),
        ("Заяц", rabbit, None, "c", 1.35),
        ("Белка", squirrel, None, "c", 1.4),
        ("Олень", deer, None, None, 1.0),
        ("Лось", moose, None, None, 1.0),
        ("Кабан", boar, None, "d", 0.7),
        ("Лошадь", horse, None, None, 1.0),
        ("Корова", cow, None, None, 1.0),
        ("Бык", ox, None, None, 1.0),
        ("Свинья", pig, None, None, 1.0),
        ("Овца", sheep, None, None, 1.0),
        ("Коза", goat, None, None, 1.0),
        ("Собака", dog, None, "d", 1.0),
        ("Кошка", cat, None, "c", 1.0),
        ("Слон", elephant, None, "d", 0.5),
        ("Жираф", giraffe, None, None, 1.0),
        ("Лев", lion, None, "c", 0.85),
        ("Тигр", tiger, None, "c", 0.9),
        ("Зебра", zebra, None, None, 1.0),
        ("Кенгуру", kangaroo, None, None, 1.0),
        ("Панда", panda, None, None, 1.0),
        ("Обезьяна", monkey, None, "c", 1.1),
        ("Горилла", gorilla, None, "d", 0.55),
        ("Орангутан", orangutan, "Орангутан", "c", 1.05),
        ("Бегемот", hippo, None, "d", 0.48),
        ("Носорог", rhino, None, "d", 0.52),
        ("Крокодил", croc, None, "d", 0.4),
        ("Верблюд", camel2, None, "d", 0.78),
        ("Лама", llama, None, None, 1.0),
        ("Пингвин", penguin, None, None, 1.0),
        ("Фламинго", flamingo, None, "c", 1.2),
        ("Павлин", peacock, None, "c", 1.15),
        ("Утка", duck, None, "c", 1.25),
        ("Гусь", goose, "Гусь", "c", 1.2),
        ("Лебедь", swan, None, "c", 1.22),
        ("Индейка", turkey, None, "c", 1.18),
        ("Курица", chicken, None, "c", 1.3),
        ("Петух", rooster, None, "c", 1.25),
        ("Попугай", parrot, None, "c", 1.28),
        ("Ворона", crow, "Ворона", "c", 1.15),
        ("Голубь", dove, None, "c", 1.2),
        ("Орёл", eagle, None, "c", 1.1),
        ("Сова", owl, None, "c", 0.95),
        ("Дятел", bird, None, "c", 1.3),
        ("Енот", raccoon, None, "d", 1.05),
        ("Выдра", otter, None, "d", 1.0),
        ("Бобр", beaver, None, "d", 0.95),
        ("Морж", seal, None, "d", 0.68),
        ("Тюлень", seal, None, "d", 0.72),
        ("Летучая мышь", bat, "Летучая мышь", "c", 1.4),
        (yo_zh, hedgehog, None, None, 1.0),
        ("Крот", paw, None, None, 1.0),
        ("Змея", snake, None, "c", 0.75),
        ("Черепаха", turtle, None, None, 1.0),
        ("Ящерица", lizard, None, None, 1.0),
        ("Лягушка", frog, None, "c", 1.35),
        ("Дельфин", dolphin, None, "d", 1.15),
        ("Кит", whale, None, "d", 0.6),
    ]

    insects = [
        K("Муравей", ant),
        K("Пчела", bee),
        K("Шмель", bee),
        K("Оса", bee),
        K("Шершень", bee),
        K("Муха", fly),
        K("Комар", mosquito),
        K("Бабочка", butterfly),
        K("Моль", butterfly),
        K("Стрекоза", cricket),
        K("Кузнечик", cricket),
        K("Сверчок", cricket),
        K("Кобылка", cricket),
        K("Жук", beetle),
        K("Богомол", beetle),
        K("Светлячок", sparkle),
        K("Гусеница", worm),
        K("Паук", spider),
        K("Клещ", microbe),
        K("Скорпион", scorpion),
        K("Божья коровка", ladybug),
        K("Таракан", cockroach),
        K("Клоп", beetle),
        K("Блоха", beetle),
        K("Вошь", microbe),
        K("Жук-олень", beetle),
        K("Майский жук", beetle),
        K("Рогач", beetle),
        K("Подёнка", fly),
        K("Сороконожка", worm),
        K("Жук-носорог", beetle),
        K("Палочник", cricket),
        K("Уховёртка", beetle),
        K("Водомерка", fly),
        K("Цикада", cricket),
        K("Тля", microbe),
        K("Горошница", beetle),
        K("Капустница", butterfly),
        K("Медляница", butterfly),
        K("Мокрица", beetle),
        K("Двувостка", fly),
        K("Сколопендра", worm),
        K("Овод", fly),
        K("Журчалка", fly),
        K("Слепень", fly),
        K("Мошка", fly),
        K("Сетчатокрыл", butterfly),
        K("Муха цеце", fly, "Муха цеце"),
        K("Комар долгоножка", mosquito, "Комар долгоножка"),
        K("Термиты", ant, "Термиты"),
        K("Роевые муравьи", ant, "Роевые муравьи"),
        K("Пчела плотоядная", bee, "Пчела плотоядная"),
        K("Шмель земляной", bee, "Шмель земляной"),
        K("Муравьиный лев", ant, "Муравьиный лев"),
        K("Трипсы", fly, "Трипсы"),
        K("Листовёртка", butterfly, "Листовёртка"),
        K("Комар-подкожник", mosquito, "Комар-подкожник"),
        K("Рудый пилильщик", beetle, "Рудый пилильщик"),
        K("Златка", beetle),
        K("Усач", beetle),
        K("\u0429\u0435\u043b\u043a\u0443\u043d", beetle),
        K("Нарывник", beetle),
        K("Песчанка", cricket),
        K("Сверчок домовой", cricket, "Сверчок домовой"),
        K("Кузнечик зелёный", cricket, "Зелёный кузнечик"),
    ]

    trees = [
        K("Берёза", tree_deciduous),
        K("Дуб", tree_deciduous),
        K("Ель", tree_evergreen),
        K("Сосна", tree_evergreen),
        K("Липа", tree_deciduous),
        K("Клён", tree_deciduous),
        K("Рябина", tree_deciduous),
        K("Яблоня", tree_deciduous),
        K("Вишня", cherry_blossom),
        K("Тополь", tree_deciduous),
        K("Ива", tree_deciduous),
        K("Каштан", chestnut_emoji),
        K("Бук", tree_deciduous),
        K("Граб", tree_deciduous),
        K("Ольха", tree_deciduous),
        K("Тис", tree_evergreen),
        K("Можжевельник", tree_evergreen),
        K("Пихта", tree_evergreen),
        K("Кедр", tree_evergreen),
        K("Лиственница", tree_evergreen),
        K("Туя", tree_evergreen),
        K("Кипарис", tree_evergreen),
        K("Секвойя", tree_evergreen),
        K("Метасеквойя", tree_evergreen),
        K("Бархат амурский", tree_deciduous, "Бархат амурский"),
        K("Орешник", chestnut_emoji),
        K("Орех грецкий", chestnut_emoji, "Грецкий орех"),
        K("Вяз", tree_deciduous),
        K("Боярышник", blossom),
        K("Шелковица", tree_deciduous),
        K("Инжир", tree_deciduous),
        K("Тутовник", tree_deciduous),
        K("Эвкалипт", tree_evergreen),
        K("Баобаб", tree_deciduous),
        K("Пальма", palm),
        K("Кокосовая пальма", palm, "Кокосовая пальма"),
        K("Банан", palm),
        K("Сакура", cherry_blossom),
        K("Магнолия", blossom),
        K("Платан", tree_deciduous),
        K("Сикомор", tree_deciduous),
        K("Черёмуха", cherry_blossom),
        K("Осина", tree_deciduous),
        K("Калина", blossom),
        K("Жимолость", blossom),
        K("Смородина", herb),
        K("Малина", blossom),
        K("Ежевика", herb),
        K("Облепиха", herb),
        K("Шиповник", blossom),
    ]

    plants = [
        K("Роза", rose),
        K("Ромашка", blossom),
        K("Ландыш", herb),
        K("Тюльпан", tulip),
        K("Подсолнух", sunflower),
        K("Мак", hibiscus),
        K("Василёк", hyacinth),
        K("Одуванчик", blossom),
        K("Клевер", four_leaf),
        K("Папоротник", herb),
        K("Кактус", cactus),
        K("Мята", herb),
        K("Базилик", herb),
        K("Петрушка", herb),
        K("Колокольчик", tulip),
        K("Нарцисс", blossom),
        K("Ирис", hyacinth),
        K("Пион", blossom),
        K("Гортензия", bouquet),
        K("Лилия", lotus),
        K("Орхидея", hibiscus),
        K("Гвоздика", rose),
        K("Астра", blossom),
        K("Хризантема", blossom),
        K("Незабудка", hyacinth),
        K("Фиалка", blossom),
        K("Фуксия", hibiscus),
        K("Жасмин", blossom),
        K("Лаванда", herb),
        K("Вербена", blossom),
        K("Гиацинт", hyacinth),
        K("Лотос", lotus),
        K("Алоэ", potted),
        K("Агава", cactus),
        K("Монстера", herb),
        K("Фикус", herb),
        K("Бегония", blossom),
        K("Герань", blossom),
        K("Петуния", blossom),
        K("Барвинок", herb),
        K("Ранункулюс", blossom),
        K("Эустома", blossom),
        K("Эдельвейс", snowflake),
        K("Репейник", seedling),
        K("Чистяк", herb),
        K("Овсюг", seedling),
        K("Кувшинка", lotus),
        K("Водоросли", coral),
        K("Мох", herb),
        K("Лишайник", herb),
        K("Пшеница", sheaf),
        K("Рожь", sheaf),
        K("Рис", sheaf),
        K("Кукуруза", corn),
        K("Гладиолус", tulip),
        K("Крокус", blossom),
        K("Первоцвет", blossom),
        K("Фиалка душистая", blossom, "Фиалка душистая"),
        K("Маргаритка", blossom),
        K("Георгин", blossom),
        K("Календула", blossom),
        K("Бархатцы", blossom),
        K("Лобелия", blossom),
        K("Розмарин", herb),
        K("Тимьян", herb),
        K("Орегано", herb),
        K("Шалфей", herb),
        K("Укроп", herb),
        K("Зелёный лук", herb, "Зелёный лук"),
        K("Сельдерей", herb),
        K("Кинза", herb),
        K("\u0429\u0430\u0432\u0435\u043b\u044c", herb),
        K("Клевер луговой", four_leaf, "Клевер"),
    ]

    corrupt = CORRUPT.read_text(encoding="utf-8")
    start = corrupt.index("    val fish:")
    end = corrupt.index("\n\n    val insects:")
    fish_block = corrupt[start:end].rstrip() + "\n"

    out: list[str] = []
    out.append("package com.example.bible.data")
    out.append("")
    out.append("import com.example.bible.R")
    out.append("")
    out.append("/**")
    out.append(" * Расширенные списки для экранов-плиток «Детям».")
    out.append(
        " * Звуки животных: [R.raw.kids_anml_dog] / [R.raw.kids_anml_cat]; "
        "иллюстрации рыб — фото (часть с Wikimedia Commons, см. комментарий в репозитории)."
    )
    out.append(" */")
    out.append("object KidsPicturedTopics {")
    out.append("    private val sfxDog = R.raw.kids_anml_dog")
    out.append("    private val sfxCat = R.raw.kids_anml_cat")
    out.append("")
    out.append("    val animals: List<KidsPicturedItem> = listOf(")
    for label, em, sp, sfx, pitch in animals_rows:
        if sfx is None:
            out.append(K(label, em, sp) if sp else K(label, em))
        else:
            out.append(Ka(label, em, sp, sfx, pitch))
    out.append("    )")
    out.append("")
    out.append(fish_block)
    out.append("")
    out.append("    val insects: List<KidsPicturedItem> = listOf(")
    out.extend(insects)
    out.append("    )")
    out.append("")
    out.append("    val trees: List<KidsPicturedItem> = listOf(")
    out.extend(trees)
    out.append("    )")
    out.append("")
    out.append("    val plants: List<KidsPicturedItem> = listOf(")
    out.extend(plants)
    out.append("    )")
    out.append("}")

    DEST.write_text("\n".join(out) + "\n", encoding="utf-8")
    print("Wrote", DEST)


if __name__ == "__main__":
    main()
