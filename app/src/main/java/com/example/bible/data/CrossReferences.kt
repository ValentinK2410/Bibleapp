package com.example.bible.data

data class CrossRef(
    val bookId: String,
    val chapter: Int,
    val verse: Int,
) {
    fun label(): String {
        val canon = BibleCanon.byId(bookId)
        val name = canon?.abbrRu ?: bookId
        return "$name $chapter:$verse"
    }
}

object CrossReferences {

    private val refs: Map<String, List<CrossRef>> = buildMap {
        fun key(bookId: String, ch: Int, v: Int) = "$bookId:$ch:$v"

        put(key("genesis", 1, 1), listOf(
            CrossRef("john", 1, 1), CrossRef("hebrews", 11, 3), CrossRef("psalms", 33, 6),
        ))
        put(key("genesis", 1, 26), listOf(
            CrossRef("genesis", 5, 1), CrossRef("colossians", 3, 10),
        ))
        put(key("genesis", 3, 15), listOf(
            CrossRef("galatians", 4, 4), CrossRef("revelation", 12, 9),
        ))
        put(key("genesis", 12, 1), listOf(
            CrossRef("acts", 7, 3), CrossRef("hebrews", 11, 8),
        ))
        put(key("genesis", 22, 18), listOf(
            CrossRef("galatians", 3, 8), CrossRef("acts", 3, 25),
        ))
        put(key("exodus", 12, 46), listOf(
            CrossRef("john", 19, 36), CrossRef("numbers", 9, 12),
        ))
        put(key("exodus", 20, 3), listOf(
            CrossRef("deuteronomy", 5, 7), CrossRef("matthew", 4, 10),
        ))
        put(key("deuteronomy", 6, 5), listOf(
            CrossRef("matthew", 22, 37), CrossRef("mark", 12, 30),
        ))
        put(key("deuteronomy", 18, 15), listOf(
            CrossRef("acts", 3, 22), CrossRef("acts", 7, 37),
        ))
        put(key("psalms", 2, 7), listOf(
            CrossRef("acts", 13, 33), CrossRef("hebrews", 1, 5), CrossRef("hebrews", 5, 5),
        ))
        put(key("psalms", 16, 10), listOf(
            CrossRef("acts", 2, 27), CrossRef("acts", 13, 35),
        ))
        put(key("psalms", 22, 1), listOf(
            CrossRef("matthew", 27, 46), CrossRef("mark", 15, 34),
        ))
        put(key("psalms", 22, 18), listOf(
            CrossRef("john", 19, 24), CrossRef("matthew", 27, 35),
        ))
        put(key("psalms", 23, 1), listOf(
            CrossRef("john", 10, 11), CrossRef("hebrews", 13, 20), CrossRef("1_peter", 2, 25),
        ))
        put(key("psalms", 110, 1), listOf(
            CrossRef("matthew", 22, 44), CrossRef("acts", 2, 34), CrossRef("hebrews", 1, 13),
        ))
        put(key("psalms", 118, 22), listOf(
            CrossRef("matthew", 21, 42), CrossRef("acts", 4, 11), CrossRef("1_peter", 2, 7),
        ))
        put(key("isaiah", 7, 14), listOf(
            CrossRef("matthew", 1, 23),
        ))
        put(key("isaiah", 9, 6), listOf(
            CrossRef("luke", 2, 11), CrossRef("john", 1, 14),
        ))
        put(key("isaiah", 40, 3), listOf(
            CrossRef("matthew", 3, 3), CrossRef("mark", 1, 3), CrossRef("john", 1, 23),
        ))
        put(key("isaiah", 53, 5), listOf(
            CrossRef("1_peter", 2, 24), CrossRef("romans", 4, 25),
        ))
        put(key("isaiah", 53, 7), listOf(
            CrossRef("acts", 8, 32), CrossRef("1_peter", 1, 19),
        ))
        put(key("jeremiah", 31, 31), listOf(
            CrossRef("hebrews", 8, 8), CrossRef("hebrews", 10, 16),
        ))
        put(key("daniel", 7, 13), listOf(
            CrossRef("matthew", 24, 30), CrossRef("revelation", 1, 7),
        ))
        put(key("micah", 5, 2), listOf(
            CrossRef("matthew", 2, 6), CrossRef("john", 7, 42),
        ))
        put(key("zechariah", 9, 9), listOf(
            CrossRef("matthew", 21, 5), CrossRef("john", 12, 15),
        ))
        put(key("malachi", 3, 1), listOf(
            CrossRef("matthew", 11, 10), CrossRef("mark", 1, 2),
        ))
        put(key("matthew", 1, 23), listOf(
            CrossRef("isaiah", 7, 14),
        ))
        put(key("matthew", 3, 3), listOf(
            CrossRef("isaiah", 40, 3),
        ))
        put(key("matthew", 22, 37), listOf(
            CrossRef("deuteronomy", 6, 5), CrossRef("mark", 12, 30),
        ))
        put(key("matthew", 28, 19), listOf(
            CrossRef("mark", 16, 15), CrossRef("acts", 1, 8),
        ))
        put(key("john", 1, 1), listOf(
            CrossRef("genesis", 1, 1), CrossRef("1_john", 1, 1), CrossRef("revelation", 19, 13),
        ))
        put(key("john", 3, 16), listOf(
            CrossRef("romans", 5, 8), CrossRef("1_john", 4, 9), CrossRef("romans", 8, 32),
        ))
        put(key("john", 10, 11), listOf(
            CrossRef("psalms", 23, 1), CrossRef("hebrews", 13, 20), CrossRef("1_peter", 5, 4),
        ))
        put(key("john", 14, 6), listOf(
            CrossRef("acts", 4, 12), CrossRef("1_timothy", 2, 5),
        ))
        put(key("acts", 2, 38), listOf(
            CrossRef("acts", 3, 19), CrossRef("acts", 10, 43),
        ))
        put(key("romans", 3, 23), listOf(
            CrossRef("romans", 5, 12), CrossRef("1_john", 1, 8),
        ))
        put(key("romans", 5, 8), listOf(
            CrossRef("john", 3, 16), CrossRef("1_john", 4, 10),
        ))
        put(key("romans", 6, 23), listOf(
            CrossRef("romans", 5, 12), CrossRef("james", 1, 15),
        ))
        put(key("romans", 8, 28), listOf(
            CrossRef("ephesians", 1, 11), CrossRef("1_thessalonians", 5, 18),
        ))
        put(key("romans", 10, 9), listOf(
            CrossRef("acts", 16, 31), CrossRef("1_john", 4, 15),
        ))
        put(key("1_corinthians", 13, 4), listOf(
            CrossRef("colossians", 3, 14), CrossRef("1_john", 4, 8),
        ))
        put(key("2_corinthians", 5, 17), listOf(
            CrossRef("galatians", 6, 15), CrossRef("ephesians", 4, 24),
        ))
        put(key("galatians", 2, 20), listOf(
            CrossRef("romans", 6, 6), CrossRef("philippians", 1, 21),
        ))
        put(key("galatians", 5, 22), listOf(
            CrossRef("ephesians", 5, 9), CrossRef("colossians", 3, 12),
        ))
        put(key("ephesians", 2, 8), listOf(
            CrossRef("romans", 3, 24), CrossRef("titus", 3, 5),
        ))
        put(key("philippians", 4, 13), listOf(
            CrossRef("2_corinthians", 12, 9), CrossRef("2_timothy", 4, 17),
        ))
        put(key("hebrews", 11, 1), listOf(
            CrossRef("romans", 8, 24), CrossRef("2_corinthians", 5, 7),
        ))
        put(key("revelation", 3, 20), listOf(
            CrossRef("john", 14, 23), CrossRef("song_of_solomon", 5, 2),
        ))
        put(key("revelation", 21, 4), listOf(
            CrossRef("isaiah", 25, 8), CrossRef("isaiah", 65, 19),
        ))
    }

    fun forVerse(bookId: String, chapter: Int, verse: Int): List<CrossRef> {
        return refs["$bookId:$chapter:$verse"] ?: emptyList()
    }
}
