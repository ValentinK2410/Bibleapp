package com.example.bible.data.genealogy

/**
 * Пол человека — для формулировок «сын/дочь», «отец/мать» на русском.
 */
enum class GenealogySex {
    M,
    F,
    UNKNOWN,
}

/**
 * Родство по прямой линии (биология) или юридическое (усыновление, родословие Мф 1).
 */
enum class ParentLinkKind {
    BIOLOGICAL,
    /** Например, Иосиф — законный отец Иисуса по Мф 1. */
    LEGAL,
}

data class GenealogyPerson(
    val id: String,
    val nameRu: String,
    val sex: GenealogySex,
    /** Краткий контекст: кто это, в какой книге упомянут. */
    val noteRu: String,
    /** Ссылки на Писание (для справки). */
    val scriptureRefs: List<String>,
)

data class ParentChildEdge(
    val parentId: String,
    val childId: String,
    val kind: ParentLinkKind = ParentLinkKind.BIOLOGICAL,
)

/**
 * Результат поиска пути между двумя персонами.
 */
sealed class GenealogyPathResult {
    data class Found(
        val steps: List<GenealogyPathStep>,
    ) : GenealogyPathResult()

    data class SamePerson(
        val person: GenealogyPerson,
    ) : GenealogyPathResult()

    data class NotFound(
        val reasonRu: String,
    ) : GenealogyPathResult()
}

/**
 * Один шаг цепочки (ребро графа).
 */
data class GenealogyPathStep(
    val from: GenealogyPerson,
    val to: GenealogyPerson,
    val relationRu: String,
)
