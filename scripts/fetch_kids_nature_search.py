# -*- coding: utf-8 -*-
"""Ищет фото на Wikimedia Commons по виду/названию и сохраняет в drawable-nodpi.

Запуск: python3 scripts/fetch_kids_nature_search.py

Файлы: kids_fish_*.jpg, kids_tree_*.jpg, kids_ins_*.jpg, kids_plant_*.jpg
"""
from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.join(os.path.dirname(__file__), "..")
DRAW = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
UA = "BibleKidsApp/1.0 (educational; kids nature images)"
REFERER = "https://commons.wikimedia.org/"

SKIP_SUBSTR = ("diagram", "map ", " logo", "icon ", "chart", "poster", "cover", "stamp")

# В названии файла Commons — не взрослая блоха (личинка, куконка и т.д.).
SKIP_SUBSTR_FLEA = ("larva", "nymphe", "nymph", "l1", "l2", "l3", "oeuf", "oeufs", "cocon", "exuv")

# Мошка (Ceratopogonidae): цельное насекомое, не личинка / СЭМ / фрагменты.
# Для деревьев: не гербарий и не схемы — нужно фото дерева/куста целиком.
TREE_PHOTO_SKIP = (
    "herbarium",
    " herbar ",
    "specimen sheet",
    " pressed ",
    "diagram",
    "illustration",
    "drawing ",
    "coat of arms",
    "logo",
    "icon ",
    "pollen",
    "microscope",
    "cross-section",
    "cross section",
    "epidermis",
    "microscop",
    " cells",
    "cell ",
    " halved",
    "fruit halved",
    "comparison of",
    "nursery catalog",
    "parrys'",
    "1897)",
    "1850)",
    " bark.jpg",
    "tree-climbing lions",
    "lion) in",
    "detail of",
    "magnolia flower",
    " halved fig",
    "weeping deodar",
    "pendula’ leaf",
    "pendula' leaf",
    " cone",
    "distribution.jpg",
    "distribution map",
    "tree book",
    " mhnt",
    "bot.200",
    " bud.jpg",
    " bombus ",
    "bombus",
    "communis fruits",
    "handbook of the trees",
    "soulangeana",
    " female flowers",
    " female flower",
    "raspberry - whole",
)

SKIP_SUBSTR_MOSHK = (
    "larva",
    "larvae",
    "pupa",
    "pupae",
    "sem ",
    "electron",
    "micrograph",
    "head capsule",
    "life cycle",
    "cretaceous",
    "myanmar amber",
    "eyes composed",
    "figure 1",
    "figure 2",
    "figure 3",
    "zookeys",
)


def api_thumb_url(title: str, width: int = 1024) -> str:
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


def search_first_image_title(query: str, extra_skip: tuple[str, ...] = ()) -> str | None:
    q = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(
        {
            "action": "query",
            "list": "search",
            "srsearch": query,
            "srnamespace": "6",
            "format": "json",
            "srlimit": "22",
        }
    )
    req = urllib.request.Request(q, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        j = json.load(r)
    for it in j.get("query", {}).get("search", []):
        t = it["title"]
        if not t.startswith("File:"):
            continue
        tl = t.lower()
        if not tl.endswith((".jpg", ".jpeg", ".png", ".webp")):
            continue
        if any(s in tl for s in SKIP_SUBSTR):
            continue
        if any(s in tl for s in extra_skip):
            continue
        return t
    return None


def download(url: str, dest: str) -> None:
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": REFERER})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
                f.write(r.read())
            time.sleep(2.2)
            return
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < 5:
                time.sleep(18.0 * (attempt + 1))
                continue
            raise


def grab(
    prefix: str,
    slug: str,
    query: str,
    extra_skip: tuple[str, ...] = (),
    force: bool = False,
) -> bool:
    dest = os.path.join(DRAW, f"{prefix}_{slug}.jpg")
    if (
        not force
        and os.path.isfile(dest)
        and os.path.getsize(dest) > 4000
    ):
        print("skip", prefix, slug)
        return True
    title = search_first_image_title(query, extra_skip=extra_skip)
    time.sleep(0.75)
    if not title:
        print("NO_SEARCH", prefix, slug, query)
        return False
    try:
        u = api_thumb_url(title)
    except Exception as e:
        print("NO_THUMB", prefix, slug, title, e)
        return False
    time.sleep(0.75)
    print("img", prefix, slug, title[:70])
    try:
        download(u, dest)
    except Exception as e:
        print("FAIL_DL", prefix, slug, e)
        return False
    return True


def main() -> None:
    import sys

    argv = list(sys.argv[1:])
    force = "--force" in argv
    argv = [a for a in argv if a != "--force"]
    mode = argv[0] if argv else "all"
    if mode not in ("all", "fish", "trees", "insects", "plants"):
        print("Usage: fetch_kids_nature_search.py [all|fish|trees|insects|plants] [--force]")
        raise SystemExit(2)
    fish: list[tuple[str, str]] = [
        ("shchuka", "Esox lucius fish"),
        ("okun", "Perca fluviatilis fish"),
        ("karp", "Cyprinus carpio"),
        ("karas", "Carassius carassius"),
        ("sudak", "Sander lucioperca"),
        ("som", "Silurus glanis"),
        ("losos", "Salmo salar Atlantic salmon"),
        ("forel", "Salmo trutta river"),
        ("treska", "Gadus morhua cod fish"),
        ("tunets", "Thunnus tuna fish"),
        ("seld", "Clupea harengus herring"),
        ("skumbria", "Scomber scombrus mackerel"),
        ("paltus", "Hippoglossus halibut"),
        ("moyva", "Mallotus villosus capelin"),
        ("sazan", "Grass carp Ctenopharyngodon"),
        ("lech", "Abramis brama bream fish"),
        ("plotva", "Rutilus rutilus roach fish"),
        ("krasnoperka", "Scardinius erythrophthalmus rudd"),
        ("lin", "Tinca tinca tench"),
        ("nalim", "Lota lota burbot"),
        ("kharius", "Thymallus thymallus grayling"),
        ("ugor", "Anguilla anguilla eel"),
        ("oset", "Acipenser sturgeon fish"),
        ("sevruga", "Acipenser stellatus"),
        ("beluga_ryba", "Huso huso beluga sturgeon"),
        ("gorbusha", "Oncorhynchus gorbuscha pink salmon"),
        ("keta", "Oncorhynchus keta chum salmon"),
        ("nerka", "Oncorhynchus nerka sockeye"),
        ("kizhuch", "Oncorhynchus kisutch coho salmon"),
        ("tolstolobik", "Hypophthalmichthys molitrix"),
        ("amur", "Ctenopharyngodon idella"),
        ("zherekh", "Aspius aspius asp fish"),
        ("chekhon", "Pelecus cultratus fish"),
        ("sig", "Coregonus lavaretus whitefish"),
        ("ryapushka", "Osmerus eperlanus smelt"),
        ("ukleyka", "Alburnus alburnus bleak"),
        ("vobla", "Rutilus caspicus vobla"),
        ("kilka", "Clupeonella sprat"),
        ("akula", "Carcharodon carcharias shark"),
        ("skat", "Dasyatis stingray fish"),
        ("klovn", "Amphiprion ocellaris clownfish"),
        ("angel", "Pomacanthus angelfish"),
        ("piranha", "Pygocentrus piranha fish"),
        ("murena", "Muraena moray eel"),
        ("mech", "Xiphias gladius swordfish"),
        ("sargan", "Belone belone garfish"),
        ("morskoy_okun", "Dicentrarchus labrax seabass"),
        ("kalmari", "Loligo squid"),
        ("krab", "Carcinus maenas crab"),
        ("krevetka", "Palaemon shrimp"),
        ("omar", "Homarus americanus lobster"),
        ("ustrica", "Ostrea oyster shell"),
        ("zvezda", "Asterias rubens starfish"),
        ("meduza", "Aurelia aurita jellyfish"),
        ("osminog", "Octopus vulgaris"),
    ]
    trees: list[tuple[str, str]] = [
        ("bereza", "Betula pendula silver birch tree summer"),
        ("dub", "Quercus robur pedunculate oak habit tree"),
        ("el", "Picea abies Norway spruce tree"),
        ("sosna", "Pinus sylvestris Scots pine tree"),
        ("lipa", "Tilia cordata tree Estonia"),
        ("klen", "Acer platanoides Norway maple tree summer"),
        ("ryabina", "Sorbus aucuparia rowan tree summer"),
        ("yablonya", "Malus domestica apple tree summer"),
        ("vishnya", "Prunus avium wild cherry tree"),
        ("topol", "Populus nigra Lombardy poplar tree"),
        ("iva", "Salix alba white willow tree"),
        ("kashtan", "Aesculus hippocastanum horse chestnut tree summer"),
        ("buk", "Fagus sylvatica European beech tree summer"),
        ("grab", "Carpinus betulus European hornbeam tree"),
        ("olha", "Alnus glutinosa common alder tree"),
        ("tis", "Taxus baccata English yew tree"),
        ("mozhzhevelnik", "Juniperus communis common juniper shrub"),
        ("pihta", "Abies alba European silver fir tree"),
        ("kedr", "Cedrus deodara deodar cedar tree"),
        ("listvennitsa", "Larix decidua European larch tree summer"),
        ("tuya", "Thuja occidentalis eastern arborvitae tree"),
        ("kiparis", "Cupressus sempervirens Mediterranean cypress tree"),
        ("sekvoiya", "Sequoia sempervirens coast redwood tree"),
        ("metasekvoiya", "Metasequoia glyptostroboides dawn redwood tree"),
        ("barkhat_amur", "Phellodendron amurense Amur cork tree"),
        ("oreshnik", "Corylus avellana common hazel shrub"),
        ("orekh_gretskiy", "Juglans regia walnut tree"),
        ("vyaz", "Ulmus glabra wych elm tree"),
        ("boyaryshnik", "Crataegus monogyna common hawthorn shrub"),
        ("shelkovitsa", "Morus alba white mulberry tree"),
        ("inzhir", "Ficus carica fig tree orchard"),
        ("tutovnik", "Morus nigra mulberry tree geograph"),
        ("evkalipt", "Eucalyptus globulus southern blue gum tree"),
        ("baobab", "Adansonia digitata baobab tree"),
        ("palma", "Phoenix dactylifera date palm tree"),
        ("kokos", "Cocos nucifera coconut palm tree"),
        ("banan", "Musa acuminata banana plant tree"),
        ("sakura", "Prunus serrulata sakura ornamental cherry tree"),
        ("magnoliya", "Magnolia grandiflora evergreen magnolia tree"),
        ("platan", "Platanus orientalis Old World plane tree"),
        ("sikomor", "Ficus sycomorus sycamore fig tree"),
        ("cheremukha", "Prunus padus bird cherry tree"),
        ("osina", "Populus tremula European aspen tree"),
        ("kalina", "Viburnum opulus guelder rose shrub"),
        ("zhimolost", "Lonicera periclymenum woodbine honeysuckle"),
        ("smorodina", "Ribes nigrum black currant bush"),
        ("malina", "Wild raspberry Rubus idaeus geograph"),
        ("ezhevika", "Rubus fruticosus blackberry bush"),
        ("oblepiha", "Hippophae rhamnoides sea buckthorn bush"),
        ("shipovnik", "Rosa canina dog rose shrub"),
    ]
    insects: list[tuple[str, str]] = [
        ("muravey", "Formica rufa red ant"),
        ("pchela", "Apis mellifera honey bee"),
        ("shmel", "Bombus terrestris bumblebee"),
        ("osa", "Vespula vulgaris wasp"),
        ("shershen", "Vespa crabro hornet"),
        ("muha", "Musca domestica housefly"),
        ("komar", "Culex pipiens mosquito"),
        ("babochka", "Danaus plexippus monarch butterfly"),
        ("mol", "Saturnia pyri giant peacock moth"),
        ("strekoza", "Anisoptera dragonfly"),
        ("kuznechik", "Tettigonia viridissima grasshopper"),
        ("sverchok", "Gryllus campestris cricket insect"),
        ("kobylka", "Locusta migratoria locust"),
        ("zhuk", "Coleoptera beetle insect"),
        ("bogomol", "Mantis religiosa praying mantis"),
        ("svetlyak", "Lampyridae firefly beetle"),
        ("gusenitsa", "Papilio caterpillar"),
        ("pauk", "Araneus diadematus garden spider"),
        ("kleshch", "Ixodes ricinus tick"),
        ("skorpion", "Buthus occitanus scorpion"),
        ("bozhya_korovka", "Coccinella septempunctata ladybird"),
        ("tarakan", "Blattella germanica cockroach"),
        ("klop", "Cimex lectularius bed bug"),
        ("bloha", "Ctenocephalides felis flea"),
        ("vosh", "Pediculus humanus louse"),
        ("zhuk_olen", "Lucanus cervus stag beetle"),
        ("maiskiy_zhuk", "Melolontha melolontha cockchafer"),
        ("rogach", "Dynastinae rhinoceros beetle"),
        ("podenka", "Ephemera danica mayfly"),
        ("sorokonogka", "Scolopendra centipede"),
        ("zhuk_nosorog", "Oryctes nasicornis rhinoceros beetle"),
        ("palochinik", "Phasmatodea stick insect"),
        ("ukhovortka", "Forficula auricularia earwig"),
        ("vodomerska", "Gerridae water strider"),
        ("tsikada", "Cicadidae cicada insect"),
        ("tlya", "Aphidoidea aphid"),
        ("goroshnitsa", "Bruchus pisorum pea weevil"),
        ("kapustnitsa", "Pieris brassicae cabbage white"),
        ("medlianitsa", "Operophtera brumata winter moth"),
        ("mokritsa", "Oniscus asellus woodlouse"),
        ("dvuvostka", "Chironomidae midge"),
        ("skolopendra", "Scolopendra subspinipes"),
        ("ovod", "Oestridae bot fly"),
        ("zhurchalka", "Chironomus plumosus"),
        ("slepen", "Tabanus horse fly"),
        ("moshka", "Ceratopogonidae biting midge fly insect"),
        ("setchatokryl", "Chrysopa lacewing"),
        ("muha_tsetse", "Glossina tsetse fly"),
        ("komar_dolgonozhka", "Tipulidae crane fly"),
        ("termites", "Termite insect colony"),
        ("roevye_muravi", "Eciton burchellii army ant"),
        ("pchela_plotoyadnaya", "Lestrimelitta limao"),
        ("shmel_zemlyanoy", "Bombus terrestris nest bumblebee"),
        ("muraviiniy_lev", "Myrmeleon formicarius antlion"),
        ("tripsy", "Thrips insect"),
        ("listovertka", "Tortricidae tortrix moth"),
        ("komar_podkozhnik", "Dermatobia hominis botfly"),
        ("rudy_pililschik", "Tenthredo sawfly"),
        ("zlatka", "Buprestis jewel beetle"),
        ("usach", "Morimus funereus longhorn beetle"),
        ("shchelkun", "Agriotes click beetle"),
        ("naryvnik", "Oedemera nobilis flower beetle"),
        ("peschanka", "Gryllotalpa mole cricket"),
        ("sverchok_domovoy", "Acheta domesticus house cricket"),
        ("kuznechik_zeleny", "Tettigonia viridissima great green bush cricket"),
    ]
    plants: list[tuple[str, str]] = [
        ("roza", "Rosa rubiginosa rose flower"),
        ("romashka", "Matricaria chamomilla chamomile"),
        ("landysh", "Convallaria majalis lily of the valley"),
        ("tulpan", "Tulipa gesneriana tulip flower"),
        ("podsolnukh", "Helianthus annuus sunflower"),
        ("mak", "Papaver rhoeas poppy flower"),
        ("vasilek", "Centaurea cyanus cornflower"),
        ("oduvanchik", "Taraxacum officinale dandelion"),
        ("klover", "Trifolium repens white clover"),
        ("paporotnik", "Pteridium aquilinum bracken fern"),
        ("kaktus", "Cactus echinopsis plant"),
        ("myata", "Mentha spicata mint plant"),
        ("bazilik", "Ocimum basilicum basil"),
        ("petrushka", "Petroselinum crispum parsley"),
        ("kolokolchik", "Campanula persicifolia bellflower"),
        ("nartsiss", "Narcissus pseudonarcissus daffodil"),
        ("iris", "Iris germanica bearded iris"),
        ("pion", "Paeonia lactiflora peony"),
        ("gortenziya", "Hydrangea macrophylla"),
        ("liliya", "Lilium candidum madonna lily"),
        ("orhideya", "Phalaenopsis orchid flower"),
        ("gvozdika", "Dianthus caryophyllus carnation"),
        ("astra", "Callistephus chinensis china aster"),
        ("khrizantema", "Chrysanthemum morifolium"),
        ("nezabutka", "Myosotis sylvatica forget me not"),
        ("fialka", "Viola odorata violet flower"),
        ("fuksiya", "Fuchsia magellanica"),
        ("zhasmin", "Jasminum officinale jasmine"),
        ("lavanda", "Lavandula angustifolia lavender"),
        ("verbena", "Verbena bonariensis"),
        ("giatsint", "Hyacinthus orientalis hyacinth"),
        ("lotos", "Nelumbo nucifera lotus flower"),
        ("aloe", "Aloe vera plant"),
        ("agava", "Agave americana century plant"),
        ("monstera", "Monstera deliciosa"),
        ("fikus", "Ficus benjamina weeping fig"),
        ("begoniya", "Begonia rex flower"),
        ("geran", "Pelargonium zonale geranium"),
        ("petuniya", "Petunia hybrida flower"),
        ("barvinok", "Vinca minor periwinkle"),
        ("ranunkulyus", "Ranunculus asiaticus persian buttercup"),
        ("evstoma", "Eustoma russellianum lisianthus"),
        ("edelveys", "Leontopodium alpinum edelweiss"),
        ("repeynik", "Arctium lappa burdock"),
        ("chistyak", "Glebionis coronaria chrysanthemum coronarium"),
        ("ovsjug", "Tripleurospermum inodorum mayweed"),
        ("kuvshinka", "Nymphaea alba white water lily"),
        ("vodorosli", "Brown algae seaweed"),
        ("mokh", "Bryophyta moss close"),
        ("lishaynik", "Parmelia sulcata lichen"),
        ("pshenitsa", "Triticum aestivum wheat field"),
        ("rozh", "Secale cereale rye field"),
        ("ris", "Oryza sativa rice paddy plant"),
        ("kukuruza", "Zea mays maize corn field"),
        ("gladiolus", "Gladiolus hortulanus"),
        ("krokus", "Crocus vernus"),
        ("pervotsvet", "Primula vulgaris primrose"),
        ("fialka_dushistaya", "Viola odorata sweet violet"),
        ("margaritka", "Bellis perennis daisy"),
        ("georgin", "Dahlia pinnata flower"),
        ("kalendula", "Calendula officinalis marigold"),
        ("barkhattsy", "Tagetes erecta marigold"),
        ("lobeliya", "Lobelia erinus"),
        ("rozmarin", "Rosmarinus officinalis rosemary"),
        ("timyan", "Thymus vulgaris thyme"),
        ("oregano", "Origanum vulgare oregano"),
        ("shalfey", "Salvia officinalis sage herb"),
        ("ukrop", "Anethum graveolens dill"),
        ("zeleny_luk", "Allium fistulosum spring onion"),
        ("selderey", "Apium graveolens celery"),
        ("kinza", "Coriandrum sativum coriander"),
        ("shavel", "Rumex acetosa sorrel"),
        ("klover_lugovoy", "Trifolium pratense red clover"),
    ]

    if mode in ("all", "fish"):
        for slug, q in fish:
            grab("kids_fish", slug, q, force=force)
    if mode in ("all", "trees"):
        for slug, q in trees:
            grab(
                "kids_tree",
                slug,
                q,
                extra_skip=SKIP_SUBSTR + TREE_PHOTO_SKIP,
                force=force,
            )
    if mode in ("all", "insects"):
        for slug, q in insects:
            if slug == "bloha":
                extra = SKIP_SUBSTR_FLEA
            elif slug == "moshka":
                extra = SKIP_SUBSTR_MOSHK
            else:
                extra = ()
            grab("kids_ins", slug, q, extra_skip=extra, force=force)
    if mode in ("all", "plants"):
        for slug, q in plants:
            grab("kids_plant", slug, q, force=force)
    print("done")


if __name__ == "__main__":
    main()
