# -*- coding: utf-8 -*-
"""Скачивает фото животных (JPEG) и недостающие звуки (OGG) с Wikimedia Commons в res проекта."""
from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.join(os.path.dirname(__file__), "..")
DRAW = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw")
UA = "BibleKidsApp/1.0 (educational; Android Bible kids section)"
REFERER = "https://commons.wikimedia.org/"

# slug -> Commons file title (must exist)
IMAGES: dict[str, str] = {
    "medved": "File:Brown bear.jpg",
    "bely_medved": "File:Polar bear.jpg",
    "volk": "File:Canis lupus laying.jpg",
    "lisa": "File:Red fox.jpg",
    "zayac": "File:European hare.jpg",
    "belka": "File:Red squirrel.jpg",
    "olen": "File:Red deer stag.jpg",
    "los": "File:Moose-Gustav.jpg",
    "kaban": "File:A young wild boar in his environment.jpg",
    "loshad": "File:Icelandic horse.jpg",
    "korova": "File:Holstein cow.jpg",
    "byk": "File:Texas Longhorn cow.jpg",
    "svinya": "File:Cochon domestique (Sus scrofa domesticus) (2).jpg",
    "ovtsa": "File:Domestic sheep.jpg",
    "koza": "File:Goat family.jpg",
    "sobaka": "File:YellowLabradorLooking.jpg",
    "koshka": "File:Cat November 2010-1a.jpg",
    "slon": "File:African bush elephant.jpg",
    "zhiraf": "File:Giraffe standing.jpg",
    "lev": "File:Lion waiting in Namibia.jpg",
    "tigr": "File:Siberian tiger.jpg",
    "zebra": "File:Plains Zebra Equus quagga.jpg",
    "kenguru": "File:Eastern grey kangaroo dec07 02.jpg",
    "panda": "File:Grosser_Panda.JPG",
    "obezjana": "File:Japanese macaque.jpg",
    "gorilla": "File:Male gorilla in SF zoo.jpg",
    "orangutan": "File:Orang Utan, Semenggok Forest Reserve, Sarawak, Borneo, Malaysia.JPG",
    "begemot": "File:Hippopotamus in water.jpg",
    "nosorog": "File:White rhino.jpg",
    "krokodil": "File:Nile crocodile.jpg",
    "verblud": "File:Bactrian camel.jpg",
    "lama": "File:Llama lying down.jpg",
    "pingvin": "File:Emperor penguin.jpg",
    "flamingo": "File:Flamingos Laguna Colorada.jpg",
    "pavlin": "File:Indian peacock.jpg",
    "utka": "File:Mallard duck.jpg",
    "gus": "File:Greylag goose.jpg",
    "lebed": "File:Mute swan Vrhnika.jpg",
    "indeyka": "File:Wild Turkey.jpg",
    "kuritsa": "File:Hen in grass.jpg",
    "petukh": "File:Rooster portrait2.jpg",
    "popugay": "File:Ara ararauna Luc Viatour.jpg",
    "vorona": "File:Corvus corone -near Canford Cliffs, Poole, England-8.jpg",
    "golub": "File:Rock dove.jpg",
    "orel": "File:Golden eagle.jpg",
    "sova": "File:Athene noctua.jpg",
    "dyatel": "File:Great Spotted Woodpecker.jpg",
    "enot": "File:Procyon lotor (raccoon).jpg",
    "vydra": "File:Otter in Southwold.jpg",
    "bobr": "File:Castor fiber.jpg",
    "morzh": "File:Odobenus rosmarus.jpg",
    "tyulen": "File:Common seal (Phoca vitulina) 2.jpg",
    "letuchaya_mysh": "File:Egyptian fruit bat.jpg",
    "yozh": "File:Erinaceus europaeus.jpg",
    "krot": "File:Talpa europaea.jpg",
    "zmeya": "File:Grass snake (Natrix natrix).jpg",
    "cherepakha": "File:Green sea turtle.jpg",
    "yashcheritsa": "File:Lacerta viridis.jpg",
    "lyagushka": "File:Rana temporaria.jpg",
    "delfin": "File:Common bottlenose dolphin.jpg",
    "kit": "File:Humpback whale in ocean.jpg",
}

# local raw name without extension -> Commons title (audio); в проект пишется как {name}.ogg
SOUNDS: dict[str, str] = {
    "kids_anml_dog": "File:Sound-of-dog.ogg",
    "kids_anml_cat": "File:Meow.ogg",
    "kids_anml_wolf": "File:Wolf howls.ogg",
    "kids_anml_cow": "File:Mudchute cow 1.ogg",
    "kids_anml_horse": "File:Wiehern.ogg",
    "kids_anml_pig": "File:Pig grunt - Erdie.ogg",
    "kids_anml_sheep": "File:Sheep bleating.ogg",
    "kids_anml_goat": "File:Herd of goats bleating.ogg",
    "kids_anml_rooster": "File:Rooster crowing.ogg",
    "kids_anml_duck": "File:Mudchute duck 1.ogg",
    "kids_anml_deer": "File:Red Deer (Cervus elaphus) (W1CDR0001424 BD3).ogg",
    "kids_anml_hedgehog": "File:Hedgehog O.ogg",
    "kids_anml_big_cat": "File:Giant Feline Sounds.ogg",
    "kids_anml_bear": "File:Bear growl.ogg",
    "kids_anml_ungulate": "File:Various.ungulates.ogg",
    "kids_anml_croc": "File:Crocodile in Vezo.ogg",
    "kids_anml_frog": "File:Frogs croak calling chorus at night.ogg",
    "kids_anml_dolphin": "File:Whales and Dolphins whale nature sounds songs nueva esparta.ogg",
    "kids_anml_whale": "File:Killer whale simple.ogg",
    "kids_anml_monkey": "File:Sound-of-stump-tailed-macaque-(macaca-arctoides).ogg",
    "kids_anml_seal": "File:Arctocephalus forsteri - sound.ogg",
    "kids_anml_squirrel": "File:Three Squirrels chirping.ogg",
    "kids_bird_eagle": "File:Bald Eagle 7261.ogg",
    "kids_bird_owl": "File:Tawny Owl (Strix aluco).ogg",
    "kids_bird_crow": "File:American crow in spring.ogg",
    "kids_bird_parrot": "File:Parrots perroquets.ogg",
    "kids_bird_hen": "File:Hen announcing shes lain an egg.ogg",
    "kids_bird_peacock": "File:Peacock1.ogg",
    "kids_bird_penguin": "File:Little Penguin (Eudyptula minor).ogg",
    "kids_bird_woodpecker": "File:Woodpecker.ogg",
    "kids_snake_rattle": "File:Rattlesnake.ogg",
    "kids_insct_cricket": "File:Stenobothrus nigromaculatus - sound.ogg",
    "kids_insct_grasshopper": "File:Chorthippus biguttulus - sound.oga",
    "kids_insct_bee": "File:Bombus buzz.ogg",
    "kids_fish_splash": "File:Bathtub water splashes.ogg",
}


def api_thumb_url(title: str, width: int = 1280) -> str:
    q = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(
        {
            "action": "query",
            "titles": title,
            "prop": "imageinfo",
            "iiprop": "url",
            "iiurlwidth": str(width),
            "format": "json",
        }
    )
    req = urllib.request.Request(q, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        j = json.load(r)
    p = next(iter(j["query"]["pages"].values()))
    if "missing" in p:
        raise FileNotFoundError(title)
    ii = p["imageinfo"][0]
    return ii.get("thumburl") or ii["url"]


def api_original_url(title: str) -> str:
    q = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(
        {
            "action": "query",
            "titles": title,
            "prop": "imageinfo",
            "iiprop": "url",
            "format": "json",
        }
    )
    req = urllib.request.Request(q, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        j = json.load(r)
    p = next(iter(j["query"]["pages"].values()))
    if "missing" in p:
        raise FileNotFoundError(title)
    return p["imageinfo"][0]["url"]


def download(url: str, dest: str) -> None:
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    req = urllib.request.Request(
        url,
        headers={"User-Agent": UA, "Referer": REFERER},
    )
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
                f.write(r.read())
            time.sleep(1.2)
            return
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < 3:
                time.sleep(8.0 * (attempt + 1))
                continue
            raise


def main() -> None:
    for slug, title in IMAGES.items():
        dest = os.path.join(DRAW, f"kids_anml_{slug}.jpg")
        if os.path.isfile(dest) and os.path.getsize(dest) > 5000:
            print("skip img", slug)
            continue
        u = api_thumb_url(title)
        time.sleep(0.5)
        print("img", slug, u[:80])
        download(u, dest)

    for base, title in SOUNDS.items():
        dest = os.path.join(RAW, f"{base}.ogg")
        if os.path.isfile(dest) and os.path.getsize(dest) > 500:
            print("skip snd", base)
            continue
        u = api_original_url(title)
        time.sleep(0.5)
        print("snd", base, u[:80])
        download(u, dest)

    print("done")


if __name__ == "__main__":
    main()
