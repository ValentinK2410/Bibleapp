package com.example.bible.data

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes

/** Элемент тематики «Детям»: подпись на карточке и фраза для озвучки (можно различать). */
data class KidsTopicItem(
    val label: String,
    val speak: String = label,
    /** ARGB; если задан — кнопка на экране «Цвета» заливается этим цветом (квадратная плитка). */
    val swatchArgb: ULong? = null,
)

/**
 * Плитка с картинкой: [emoji] как универсальная иллюстрация или [imageRes] (PNG в drawable).
 * [soundRes] — короткий звук из res/raw; [soundPitch] — тембр (1f = как в файле), только Android 6+.
 *
 * [itemKey] — ключ для подстановки пользовательских правок (по умолчанию совпадает с [label]).
 * [customImagePath] / [customSoundPath] — пути **относительно** [android.content.Context.getFilesDir].
 * [detailFullScreen] — в диалоге просмотра: на весь экран или компактно «как плитка».
 */
data class KidsPicturedItem(
    val label: String,
    val speak: String = label,
    val emoji: String,
    @DrawableRes val imageRes: Int? = null,
    @RawRes val soundRes: Int? = null,
    val soundPitch: Float = 1f,
    val itemKey: String = label,
    val customImagePath: String? = null,
    val customSoundPath: String? = null,
    val detailFullScreen: Boolean = true,
)

object KidsTopicsRepository {
    val colors: List<KidsTopicItem> = listOf(
        KidsTopicItem("Красный", swatchArgb = 0xFFE53935UL),
        KidsTopicItem("Оранжевый", swatchArgb = 0xFFFB8C00UL),
        KidsTopicItem("Жёлтый", swatchArgb = 0xFFFDD835UL),
        KidsTopicItem("Зелёный", swatchArgb = 0xFF43A047UL),
        KidsTopicItem("Голубой", swatchArgb = 0xFF29B6F6UL),
        KidsTopicItem("Синий", swatchArgb = 0xFF1E88E5UL),
        KidsTopicItem("Фиолетовый", swatchArgb = 0xFF8E24AAUL),
        KidsTopicItem("Розовый", swatchArgb = 0xFFEC407AUL),
        KidsTopicItem("Коричневый", swatchArgb = 0xFF6D4C41UL),
        KidsTopicItem("Чёрный", swatchArgb = 0xFF212121UL),
        KidsTopicItem("Белый", swatchArgb = 0xFFFFFFFFUL),
        KidsTopicItem("Серый", swatchArgb = 0xFF9E9E9EUL),
    )

}

/**
 * Страна: русское название, ISO 3166-1 alpha-2 для флага, приветствие на основном языке(ах) страны,
 * тег локали BCP 47 для TTS. [speak] — полное русское название для озвучки имени (при необходимости).
 */
data class KidsCountryItem(
    val nameRu: String,
    val isoCode: String,
    val helloNative: String,
    val ttsLocaleTag: String,
    val speak: String = nameRu,
)

object KidsCountries {
    private fun row(
        nameRu: String,
        iso: String,
        hello: String,
        locale: String,
        speakRu: String? = null,
    ): KidsCountryItem = KidsCountryItem(
        nameRu = nameRu,
        isoCode = iso,
        helloNative = hello,
        ttsLocaleTag = locale,
        speak = speakRu ?: nameRu,
    )

    val all: List<KidsCountryItem> = listOf(
        row("Россия", "RU", "Привет", "ru-RU"),
        row("Украина", "UA", "Привіт", "uk-UA"),
        row("Беларусь", "BY", "Прывітанне", "be-BY"),
        row("Казахстан", "KZ", "Сәлем", "kk-KZ"),
        row("Узбекистан", "UZ", "Salom", "uz-UZ"),
        row("Киргизия", "KG", "Салам", "ky-KG"),
        row("Таджикистан", "TJ", "Салом", "tg-TJ"),
        row("Туркменистан", "TM", "Salam", "tk-TM"),
        row("Азербайджан", "AZ", "Salam", "az-AZ"),
        row("Армения", "AM", "\u0532\u0561\u0580\u0565\u0582", "hy-AM"),
        row("Грузия", "GE", "\u10d2\u10d0\u10db\u10d0\u10e0\u10df\u10dd\u10d1\u10d0", "ka-GE"),
        row("Молдова", "MD", "Bună ziua", "ro-MD"),
        row("Латвия", "LV", "Sveiki", "lv-LV"),
        row("Литва", "LT", "Labas", "lt-LT"),
        row("Эстония", "EE", "Tere", "et-EE"),
        row("Польша", "PL", "Cześć", "pl-PL"),
        row("Германия", "DE", "Hallo", "de-DE"),
        row("Франция", "FR", "Bonjour", "fr-FR"),
        row("Италия", "IT", "Ciao", "it-IT"),
        row("Испания", "ES", "Hola", "es-ES"),
        row("Португалия", "PT", "Olá", "pt-PT"),
        row("Великобритания", "GB", "Hello", "en-GB"),
        row("Ирландия", "IE", "Hello", "en-IE"),
        row("Нидерланды", "NL", "Hallo", "nl-NL"),
        row("Бельгия", "BE", "Bonjour", "fr-BE"),
        row("Швейцария", "CH", "Grüezi", "de-CH"),
        row("Австрия", "AT", "Servus", "de-AT"),
        row("Чехия", "CZ", "Ahoj", "cs-CZ"),
        row("Словакия", "SK", "Ahoj", "sk-SK"),
        row("Венгрия", "HU", "Szia", "hu-HU"),
        row("Румыния", "RO", "Salut", "ro-RO"),
        row("Болгария", "BG", "Здравей", "bg-BG"),
        row("Греция", "GR", "Γεια σου", "el-GR"),
        row("Хорватия", "HR", "Bok", "hr-HR"),
        row("Сербия", "RS", "Zdravo", "sr-RS"),
        row("Словения", "SI", "Živjo", "sl-SI"),
        row("Северная Македония", "MK", "Здраво", "mk-MK"),
        row("Албания", "AL", "Përshëndetje", "sq-AL"),
        row("Босния и Герцеговина", "BA", "Zdravo", "bs-BA"),
        row("Черногория", "ME", "Zdravo", "sr-ME"),
        row("Косово", "XK", "Përshëndetje", "sq-XK"),
        row("Норвегия", "NO", "Hei", "nb-NO"),
        row("Швеция", "SE", "Hej", "sv-SE"),
        row("Финляндия", "FI", "Hei", "fi-FI"),
        row("Дания", "DK", "Hej", "da-DK"),
        row("Исландия", "IS", "Hæ", "is-IS"),
        row("Турция", "TR", "Merhaba", "tr-TR"),
        row("Кипр", "CY", "Γεια σου", "el-CY"),
        row("Израиль", "IL", "שלום", "he-IL"),
        row(
            "Соединённые Штаты Америки",
            "US",
            "Hello",
            "en-US",
            speakRu = "Соединённые Штаты Америки",
        ),
        row("Канада", "CA", "Hello", "en-CA"),
        row("Мексика", "MX", "Hola", "es-MX"),
        row("Куба", "CU", "Hola", "es-CU"),
        row("Бразилия", "BR", "Olá", "pt-BR"),
        row("Аргентина", "AR", "Hola", "es-AR"),
        row("Чили", "CL", "Hola", "es-CL"),
        row("Перу", "PE", "Hola", "es-PE"),
        row("Колумбия", "CO", "Hola", "es-CO"),
        row("Венесуэла", "VE", "Hola", "es-VE"),
        row("Уругвай", "UY", "Hola", "es-UY"),
        row("Парагвай", "PY", "Hola", "es-PY"),
        row("Эквадор", "EC", "Hola", "es-EC"),
        row("Боливия", "BO", "Hola", "es-BO"),
        row("Китай", "CN", "你好", "zh-CN"),
        row("Япония", "JP", "こんにちは", "ja-JP"),
        row("Южная Корея", "KR", "\uc548\ub155\ud558\uc138\uc694", "ko-KR"),
        row("Северная Корея", "KP", "\uc548\ub155\ud558\uc138\uc694", "ko-KR"),
        row("Монголия", "MN", "Сайн байна уу", "mn-MN"),
        row("Индия", "IN", "नमस्ते", "hi-IN"),
        row("Пакистан", "PK", "سلام", "ur-PK"),
        row("Бангладеш", "BD", "হ্যালো", "bn-BD"),
        row("Непал", "NP", "नमस्ते", "ne-NP"),
        row("Шри-Ланка", "LK", "\u0d86\u0dc4\u0dd4\u0db6\u0ddc\u0dc0\u0db1\u0dca", "si-LK"),
        row("Таиланд", "TH", "สวัสดี", "th-TH"),
        row("Вьетнам", "VN", "Xin chào", "vi-VN"),
        row("Индонезия", "ID", "Halo", "id-ID"),
        row("Малайзия", "MY", "Halo", "ms-MY"),
        row("Сингапур", "SG", "Hello", "en-SG"),
        row("Филиппины", "PH", "Kamusta", "fil-PH"),
        row("Австралия", "AU", "Hello", "en-AU"),
        row("Новая Зеландия", "NZ", "Kia ora", "mi-NZ"),
        row("Фиджи", "FJ", "Bula", "en-AU"),
        row("Египет", "EG", "مرحبا", "ar-EG"),
        row("ЮАР", "ZA", "Hello", "en-ZA"),
        row("Нигерия", "NG", "Hello", "en-NG"),
        row("Кения", "KE", "Jambo", "sw-KE"),
        row("Марокко", "MA", "Salam", "ar-MA"),
        row("Алжир", "DZ", "Salam", "ar-DZ"),
        row("Тунис", "TN", "Aslema", "ar-TN"),
        row("Саудовская Аравия", "SA", "Marhaba", "ar-SA"),
        row(
            "ОАЭ",
            "AE",
            "Marhaba",
            "ar-AE",
            speakRu = "Объединённые Арабские Эмираты",
        ),
        row("Катар", "QA", "Marhaba", "ar-QA"),
        row("Кувейт", "KW", "Marhaba", "ar-KW"),
        row("Иран", "IR", "سلام", "fa-IR"),
        row("Ирак", "IQ", "Marhaba", "ar-IQ"),
        row("Сирия", "SY", "Marhaba", "ar-SY"),
        row("Ливан", "LB", "Marhaba", "ar-LB"),
        row("Иордания", "JO", "Marhaba", "ar-JO"),
        row("Афганистан", "AF", "سلام", "fa-AF"),
    )
}
