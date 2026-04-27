# -*- coding: utf-8 -*-
"""Восстанавливает блок animals в KidsPicturedTopics.kt (UTF-8 через кодпоинты)."""
from __future__ import annotations

from pathlib import Path


def ch(*codes: int) -> str:
    return "".join(chr(c) for c in codes)


ROOT = Path(__file__).resolve().parents[1]
KT = ROOT / "app/src/main/java/com/example/bible/data/KidsPicturedTopics.kt"

# Эмодзи как в исходном списке
bear = ch(0x1F43B)
polar = ch(0x1F43B, 0x200D, 0x2744, 0xFE0F)
wolf = ch(0x1F43A)
fox = ch(0x1F98A)
rabbit = ch(0x1F430)
squirrel = ch(0x1F43F, 0xFE0F)
deer = ch(0x1F98C)
moose = ch(0x1FACE)
boar = ch(0x1F417)
horse = ch(0x1F434)
cow = ch(0x1F404)
ox = ch(0x1F402)
pig = ch(0x1F416)
sheep = ch(0x1F411)
goat = ch(0x1F410)
dog = ch(0x1F415)
cat = ch(0x1F408)
elephant = ch(0x1F418)
giraffe = ch(0x1F992)
lion = ch(0x1F981)
tiger = ch(0x1F42F)
zebra = ch(0x1F993)
kangaroo = ch(0x1F998)
panda = ch(0x1F43C)
monkey = ch(0x1F435)
gorilla = ch(0x1F98D)
orangutan = ch(0x1F9A7)
hippo = ch(0x1F99B)
rhino = ch(0x1F98F)
croc = ch(0x1F40A)
camel = ch(0x1F42B)
llama = ch(0x1F999)
penguin = ch(0x1F427)
flamingo = ch(0x1F9A9)
peacock = ch(0x1F99A)
duck = ch(0x1F986)
goose = ch(0x1FABF)
swan = ch(0x1F9A2)
turkey = ch(0x1F983)
chicken = ch(0x1F414)
rooster = ch(0x1F413)
parrot = ch(0x1F99C)
crow = ch(0x1F426, 0x200D, 0x2B1B)
dove = ch(0x1F54A, 0xFE0F)
eagle = ch(0x1F985)
owl = ch(0x1F989)
bird = ch(0x1F426)
raccoon = ch(0x1F99D)
otter = ch(0x1F9A6)
beaver = ch(0x1F9AB)
seal = ch(0x1F9AD)
bat = ch(0x1F987)
hedgehog = ch(0x1F994)
paw = ch(0x1F43E)
snake = ch(0x1F40D)
turtle = ch(0x1F422)
lizard = ch(0x1F98E)
frog = ch(0x1F438)
dolphin = ch(0x1F42C)
whale = ch(0x1F40B)

lines = [
    "    val animals: List<KidsPicturedItem> = listOf(",
    f'        KidsPicturedItem("Медведь", emoji = "{bear}", imageRes = R.drawable.kids_anml_medved, soundRes = sfxBear, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Белый медведь", speak = "Белый медведь", emoji = "{polar}", imageRes = R.drawable.kids_anml_bely_medved, soundRes = sfxBear, soundPitch = 0.92f),',
    f'        KidsPicturedItem("Волк", emoji = "{wolf}", imageRes = R.drawable.kids_anml_volk, soundRes = sfxWolf, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Лиса", emoji = "{fox}", imageRes = R.drawable.kids_anml_lisa, soundRes = sfxFox, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Заяц", emoji = "{rabbit}", imageRes = R.drawable.kids_anml_zayac, soundRes = sfxRabbit, soundPitch = 1.12f),',
    f'        KidsPicturedItem("Белка", emoji = "{squirrel}", imageRes = R.drawable.kids_anml_belka, soundRes = sfxSquirrel, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Олень", emoji = "{deer}", imageRes = R.drawable.kids_anml_olen, soundRes = sfxDeer, soundPitch = 1.05f),',
    f'        KidsPicturedItem("Лось", emoji = "{moose}", imageRes = R.drawable.kids_anml_los, soundRes = sfxDeer, soundPitch = 0.82f),',
    f'        KidsPicturedItem("Кабан", emoji = "{boar}", imageRes = R.drawable.kids_anml_kaban, soundRes = sfxPig, soundPitch = 0.7f),',
    f'        KidsPicturedItem("Лошадь", emoji = "{horse}", imageRes = R.drawable.kids_anml_loshad, soundRes = sfxHorse, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Корова", emoji = "{cow}", imageRes = R.drawable.kids_anml_korova, soundRes = sfxCow, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Бык", emoji = "{ox}", imageRes = R.drawable.kids_anml_byk, soundRes = sfxCow, soundPitch = 0.88f),',
    f'        KidsPicturedItem("Свинья", emoji = "{pig}", imageRes = R.drawable.kids_anml_svinya, soundRes = sfxPig, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Овца", emoji = "{sheep}", imageRes = R.drawable.kids_anml_ovtsa, soundRes = sfxSheep, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Коза", emoji = "{goat}", imageRes = R.drawable.kids_anml_koza, soundRes = sfxGoat, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Собака", emoji = "{dog}", imageRes = R.drawable.kids_anml_sobaka, soundRes = sfxDog, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Кошка", emoji = "{cat}", imageRes = R.drawable.kids_anml_koshka, soundRes = sfxCat, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Слон", emoji = "{elephant}", imageRes = R.drawable.kids_anml_slon, soundRes = sfxElephant, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Жираф", emoji = "{giraffe}", imageRes = R.drawable.kids_anml_zhiraf, soundRes = sfxGiraffe, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Лев", emoji = "{lion}", imageRes = R.drawable.kids_anml_lev, soundRes = sfxLion, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Тигр", emoji = "{tiger}", imageRes = R.drawable.kids_anml_tigr, soundRes = sfxLion, soundPitch = 0.9f),',
    f'        KidsPicturedItem("Зебра", emoji = "{zebra}", imageRes = R.drawable.kids_anml_zebra, soundRes = sfxHorse, soundPitch = 1.06f),',
    f'        KidsPicturedItem("Кенгуру", emoji = "{kangaroo}", imageRes = R.drawable.kids_anml_kenguru, soundRes = sfxChimp, soundPitch = 0.92f),',
    f'        KidsPicturedItem("Панда", emoji = "{panda}", imageRes = R.drawable.kids_anml_panda, soundRes = sfxBear, soundPitch = 1.08f),',
    f'        KidsPicturedItem("Обезьяна", emoji = "{monkey}", imageRes = R.drawable.kids_anml_obezjana, soundRes = sfxChimp, soundPitch = 1.12f),',
    f'        KidsPicturedItem("Горилла", emoji = "{gorilla}", imageRes = R.drawable.kids_anml_gorilla, soundRes = sfxChimp, soundPitch = 0.58f),',
    f'        KidsPicturedItem("Орангутан", speak = "Орангутан", emoji = "{orangutan}", imageRes = R.drawable.kids_anml_orangutan, soundRes = sfxOrangutan, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Бегемот", emoji = "{hippo}", imageRes = R.drawable.kids_anml_begemot, soundRes = sfxHippo, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Носорог", emoji = "{rhino}", imageRes = R.drawable.kids_anml_nosorog, soundRes = sfxRhino, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Крокодил", emoji = "{croc}", imageRes = R.drawable.kids_anml_krokodil, soundRes = sfxCrocodile, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Верблюд", emoji = "{camel}", imageRes = R.drawable.kids_anml_verblud, soundRes = sfxCow, soundPitch = 0.74f),',
    f'        KidsPicturedItem("Лама", emoji = "{llama}", imageRes = R.drawable.kids_anml_lama, soundRes = sfxSheep, soundPitch = 1.08f),',
    f'        KidsPicturedItem("Пингвин", emoji = "{penguin}", imageRes = R.drawable.kids_anml_pingvin, soundRes = sfxDuck, soundPitch = 0.8f),',
    f'        KidsPicturedItem("Фламинго", emoji = "{flamingo}", imageRes = R.drawable.kids_anml_flamingo, soundRes = sfxFlamingo, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Павлин", emoji = "{peacock}", imageRes = R.drawable.kids_anml_pavlin, soundRes = sfxPeacock, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Утка", emoji = "{duck}", imageRes = R.drawable.kids_anml_utka, soundRes = sfxDuck, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Гусь", speak = "Гусь", emoji = "{goose}", imageRes = R.drawable.kids_anml_gus, soundRes = sfxGoose, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Лебедь", emoji = "{swan}", imageRes = R.drawable.kids_anml_lebed, soundRes = sfxSwan, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Индейка", emoji = "{turkey}", imageRes = R.drawable.kids_anml_indeyka, soundRes = sfxTurkey, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Курица", emoji = "{chicken}", imageRes = R.drawable.kids_anml_kuritsa, soundRes = sfxChicken, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Петух", emoji = "{rooster}", imageRes = R.drawable.kids_anml_petukh, soundRes = sfxRooster, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Попугай", emoji = "{parrot}", imageRes = R.drawable.kids_anml_popugay, soundRes = sfxParrot, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Ворона", speak = "Ворона", emoji = "{crow}", imageRes = R.drawable.kids_anml_vorona, soundRes = sfxCrow, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Голубь", emoji = "{dove}", imageRes = R.drawable.kids_anml_golub, soundRes = sfxDove, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Орёл", emoji = "{eagle}", imageRes = R.drawable.kids_anml_orel, soundRes = sfxEagle, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Сова", emoji = "{owl}", imageRes = R.drawable.kids_anml_sova, soundRes = sfxOwl, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Дятел", emoji = "{bird}", imageRes = R.drawable.kids_anml_dyatel, soundRes = sfxWoodpecker, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Енот", emoji = "{raccoon}", imageRes = R.drawable.kids_anml_enot, soundRes = sfxFox, soundPitch = 1.08f),',
    f'        KidsPicturedItem("Выдра", emoji = "{otter}", imageRes = R.drawable.kids_anml_vydra, soundRes = sfxOtter, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Бобр", emoji = "{beaver}", imageRes = R.drawable.kids_anml_bobr, soundRes = sfxSquirrel, soundPitch = 0.86f),',
    f'        KidsPicturedItem("Морж", emoji = "{seal}", imageRes = R.drawable.kids_anml_morzh, soundRes = sfxSeal, soundPitch = 0.66f),',
    f'        KidsPicturedItem("Тюлень", emoji = "{seal}", imageRes = R.drawable.kids_anml_tyulen, soundRes = sfxSeal, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Летучая мышь", speak = "Летучая мышь", emoji = "{bat}", imageRes = R.drawable.kids_anml_letuchaya_mysh, soundRes = sfxBat, soundPitch = 1.0f),',
    f'        KidsPicturedItem("\u0401\u0436", emoji = "{hedgehog}", imageRes = R.drawable.kids_anml_yozh, soundRes = sfxHedgehog, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Крот", emoji = "{paw}", imageRes = R.drawable.kids_anml_krot, soundRes = sfxSquirrel, soundPitch = 0.82f),',
    f'        KidsPicturedItem("Змея", emoji = "{snake}", imageRes = R.drawable.kids_anml_zmeya, soundRes = sfxSnake, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Черепаха", emoji = "{turtle}", imageRes = R.drawable.kids_anml_cherepakha, soundRes = sfxSnake, soundPitch = 0.4f),',
    f'        KidsPicturedItem("Ящерица", emoji = "{lizard}", imageRes = R.drawable.kids_anml_yashcheritsa, soundRes = sfxSnake, soundPitch = 1.22f),',
    f'        KidsPicturedItem("Лягушка", emoji = "{frog}", imageRes = R.drawable.kids_anml_lyagushka, soundRes = sfxFrog, soundPitch = 1.0f),',
    f'        KidsPicturedItem("Дельфин", emoji = "{dolphin}", imageRes = R.drawable.kids_anml_delfin, soundRes = sfxDolphin, soundPitch = 1.08f),',
    f'        KidsPicturedItem("Кит", emoji = "{whale}", imageRes = R.drawable.kids_anml_kit, soundRes = sfxWhale, soundPitch = 1.0f),',
    "    )",
    "",
]

new_block = "\n".join(lines)
text = KT.read_text(encoding="utf-8")
start = text.index("    val animals: List<KidsPicturedItem> = listOf(")
end = text.index("\n    val fish:", start)
KT.write_text(text[:start] + new_block + text[end:], encoding="utf-8")
print("patched", KT)
