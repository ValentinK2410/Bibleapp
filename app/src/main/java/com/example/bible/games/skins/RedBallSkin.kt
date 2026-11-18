package com.example.bible.games.skins

/**
 * Оформление «Красного мяча».
 *
 * [RedBallSkinId.CLASSIC] рисуется процедурами, как раньше.
 * Главы 1–5 — наборы PNG. Собраны глава 1 «Зелёные холмы» и глава 2 «Тёмный лес».
 * Выбор хранится отдельно от JSON уровня, старые сохранения не меняются.
 * Мигание после удара не зависит от главы: и классика, и спрайты читают [com.example.bible.games.RedBallSim.hurtBlink].
 *
 * Как добавить главу 3–5:
 * 1. Положить PNG в `assets/redball/<ключ>/` и закрыть каждый [RedBallSkinSlot].
 *    Спрайты и иконки лучше брать из `src/2x` пакета (на экране они крупнее тайла),
 *    холмы, землю и небо — из финальной 1× папки: так APK не раздувается.
 * 2. Добавить значение в [RedBallSkinId] со стабильным [RedBallSkinId.storageKey].
 * 3. Собрать [RedBallSkin] и дописать его в [RedBallSkins.chapters].
 * Редактор и игра подхватят главу сами: список чипов строится из [RedBallSkins.selectable].
 */
enum class RedBallSkinId(
    val storageKey: String,
    val title: String,
    val procedural: Boolean,
    /**
     * ARGB подписей меню, которые лежат прямо на небе. 0 — прежний тёплый коричневый:
     * он читается на светлом небе классики и «Зелёных холмов».
     * На тёмном фиолетовом небе нужен светлый цвет, иначе заголовок пропадает.
     */
    val menuInk: Long = 0L,
    /** Более тихий оттенок второй строки меню. 0 — прежний кирпичный. */
    val menuInkSoft: Long = 0L,
) {
    GREEN_HILLS("green_hills", "Зелёные холмы", procedural = false),
    DEEP_FOREST(
        "deep_forest",
        "Тёмный лес",
        procedural = false,
        menuInk = 0xFFFFF6E8,
        menuInkSoft = 0xFFE4D4FF,
    ),
    CLASSIC("classic", "Классика", procedural = true),
    ;

    companion object {
        val DEFAULT = GREEN_HILLS

        fun fromStored(raw: String?): RedBallSkinId {
            if (raw.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.storageKey == raw } ?: DEFAULT
        }
    }
}

/**
 * Всё, что глава обязана нарисовать. Имена совпадают с игровыми предметами,
 * а не с именами файлов: следующая глава закрывает те же слоты своими PNG.
 */
enum class RedBallSkinSlot {
    HERO_HAPPY,
    HERO_SURPRISED,
    HERO_SCARED,
    ENEMY_SQUARE,
    BOSS,
    BOULDER,
    CRATE,
    CONCRETE,
    GRASS_TOP,
    GRASS_EDGE,
    SOIL,
    LADDER,
    SPIKES,
    SPRING_IDLE,
    SPRING_COMPRESSED,
    WATER_SURFACE,
    WATER_BODY,
    STAR,
    MAGNET,
    HEART,
    FLAG,
    PORTAL_ENTRY,
    PORTAL_EXIT,
    BUSH,
    TREE,
    SKY,
    HILLS_FAR,
    HILLS_NEAR,
    CLOUD_A,
    CLOUD_B,
    CLOUD_C,
    ICON_HEART,
    ICON_STAR,
    ICON_PAUSE,
    ICON_COIN,
}

/**
 * Описание главы: какие файлы в `assets/` закрывают слоты.
 * Сами картинки грузит [RedBallSkinCache] и держит их в памяти.
 */
class RedBallSkin(
    val id: RedBallSkinId,
    val title: String,
    val chapter: Int,
    /** Каталог внутри assets, без ведущего слэша. Пример: `redball/green_hills`. */
    val assetRoot: String,
    val files: Map<RedBallSkinSlot, String>,
) {
    init {
        require(assetRoot.isNotBlank() && !assetRoot.contains("..") && !assetRoot.startsWith("/")) {
            "плохой корень ассетов: $assetRoot"
        }
        for (path in files.values) {
            require(path.isNotBlank() && !path.contains("..") && !path.startsWith("/")) {
                "плохой путь скина: $path"
            }
        }
    }

    fun assetPath(slot: RedBallSkinSlot): String = "$assetRoot/${files.getValue(slot)}"

    fun missingSlots(): List<RedBallSkinSlot> = RedBallSkinSlot.entries.filter { it !in files }
}

object RedBallSkins {
    /**
     * Одинаковые относительные пути у глав 1 и 2.
     * Спрайты и иконки — мастера 2× (резче на плотном экране).
     * Крупные тайлы (холмы, земля, небо, бетон, вода, ящик) — финальные 1×.
     * Своей подстановки нет: общие с холмами герой, звезда, магнит, сердце,
     * флаг, порталы и иконки лежат копией в каталоге главы.
     */
    private val chapterFiles: Map<RedBallSkinSlot, String> = mapOf(
            RedBallSkinSlot.HERO_HAPPY to "sprites/hero_red_ball_happy.png",
            RedBallSkinSlot.HERO_SURPRISED to "sprites/hero_red_ball_surprised.png",
            RedBallSkinSlot.HERO_SCARED to "sprites/hero_red_ball_scared.png",
            RedBallSkinSlot.ENEMY_SQUARE to "sprites/enemy_square.png",
            RedBallSkinSlot.BOSS to "sprites/enemy_square_boss.png",
            RedBallSkinSlot.BOULDER to "sprites/boulder.png",
            RedBallSkinSlot.CRATE to "tiles/wood_crate.png",
            RedBallSkinSlot.CONCRETE to "tiles/concrete.png",
            RedBallSkinSlot.GRASS_TOP to "tiles/grass_top.png",
            RedBallSkinSlot.GRASS_EDGE to "tiles/grass_edge.png",
            RedBallSkinSlot.SOIL to "tiles/soil.png",
            RedBallSkinSlot.LADDER to "sprites/ladder.png",
            RedBallSkinSlot.SPIKES to "sprites/spikes.png",
            RedBallSkinSlot.SPRING_IDLE to "sprites/spring_idle.png",
            RedBallSkinSlot.SPRING_COMPRESSED to "sprites/spring_compressed.png",
            RedBallSkinSlot.WATER_SURFACE to "tiles/water_surface.png",
            RedBallSkinSlot.WATER_BODY to "tiles/water_body.png",
            RedBallSkinSlot.STAR to "sprites/star.png",
            RedBallSkinSlot.MAGNET to "sprites/magnet.png",
            RedBallSkinSlot.HEART to "sprites/heart.png",
            RedBallSkinSlot.FLAG to "sprites/flag.png",
            RedBallSkinSlot.PORTAL_ENTRY to "sprites/portal_entry.png",
            RedBallSkinSlot.PORTAL_EXIT to "sprites/portal_exit.png",
            RedBallSkinSlot.BUSH to "sprites/bush.png",
            RedBallSkinSlot.TREE to "sprites/tree.png",
            RedBallSkinSlot.SKY to "tiles/sky_gradient.png",
            RedBallSkinSlot.HILLS_FAR to "tiles/hills_far.png",
            RedBallSkinSlot.HILLS_NEAR to "tiles/hills_near.png",
            RedBallSkinSlot.CLOUD_A to "sprites/cloud_a.png",
            RedBallSkinSlot.CLOUD_B to "sprites/cloud_b.png",
            RedBallSkinSlot.CLOUD_C to "sprites/cloud_c.png",
            RedBallSkinSlot.ICON_HEART to "icons/heart.png",
            RedBallSkinSlot.ICON_STAR to "icons/star.png",
            RedBallSkinSlot.ICON_PAUSE to "icons/pause.png",
            RedBallSkinSlot.ICON_COIN to "icons/coin.png",
    )

    val greenHills: RedBallSkin = RedBallSkin(
        id = RedBallSkinId.GREEN_HILLS,
        title = RedBallSkinId.GREEN_HILLS.title,
        chapter = 1,
        assetRoot = "redball/green_hills",
        files = chapterFiles,
    )

    val deepForest: RedBallSkin = RedBallSkin(
        id = RedBallSkinId.DEEP_FOREST,
        title = RedBallSkinId.DEEP_FOREST.title,
        chapter = 2,
        assetRoot = "redball/deep_forest",
        files = chapterFiles,
    )

    /** Главы со спрайтами. Классика сюда не входит: у неё нет файлов. */
    val chapters: List<RedBallSkin> = listOf(greenHills, deepForest)

    /** Порядок чипов. Первой идёт глава по умолчанию. */
    val selectable: List<RedBallSkinId> = listOf(
        RedBallSkinId.GREEN_HILLS,
        RedBallSkinId.DEEP_FOREST,
        RedBallSkinId.CLASSIC,
    )

    fun chapter(id: RedBallSkinId): RedBallSkin? = chapters.firstOrNull { it.id == id }
}
