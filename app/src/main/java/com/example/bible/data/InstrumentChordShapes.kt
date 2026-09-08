package com.example.bible.data

/**
 * Аппликатура гитары (стандарт EADGHE, струна H = си) и клавиши пианино.
 * Нотация как на HolyChords: H = си, B = си-бемоль.
 */
object InstrumentChordShapes {

    enum class Instrument { GUITAR, PIANO }

    data class ParsedChord(
        val name: String,
        val root: Int,
        val intervals: List<Int>,
        val bass: Int?,
        val family: Family,
    ) {
        val pitchClasses: List<Int>
            get() = intervals.map { Math.floorMod(root + it, 12) }.distinct()

        val noteNames: List<String>
            get() = pitchClasses.map { SongChordMarkup.noteName(it) }
    }

    enum class Family { MAJOR, MINOR, DIM, AUG, SUS2, SUS4, DOM7, MIN7, MAJ7, OTHER }

    /** 6 струн от басовой E к тонкой e: −1 глушение, 0 открытая, 1+ лад. */
    data class GuitarShape(val frets: List<Int>) {
        init {
            require(frets.size == 6)
        }
    }

    fun parse(name: String): ParsedChord? {
        val raw = name.trim()
        if (raw.isEmpty()) return null
        val slash = raw.indexOf('/')
        val main = if (slash > 0) raw.substring(0, slash) else raw
        val bassToken = if (slash > 0) raw.substring(slash + 1) else null
        val rootLen = rootLength(main) ?: return null
        val root = SongChordMarkup.noteIndex(main.substring(0, rootLen)) ?: return null
        val suffix = main.substring(rootLen)
        val bass = bassToken?.let { token ->
            val len = rootLength(token) ?: return@let null
            SongChordMarkup.noteIndex(token.substring(0, len))
        }
        val (intervals, family) = qualityOf(suffix)
        return ParsedChord(raw, root, intervals, bass, family)
    }

    fun guitarShape(name: String): GuitarShape? {
        val parsed = parse(name) ?: return null
        OPEN_SHAPES[canonicalKey(parsed)]?.let { return it }
        return barreShape(parsed)
    }

    fun pianoPitchClasses(name: String): List<Int> {
        val parsed = parse(name) ?: return emptyList()
        val tones = parsed.pitchClasses.toMutableList()
        parsed.bass?.let { if (it !in tones) tones.add(0, it) }
        return tones
    }

    fun noteLabels(name: String): String {
        val parsed = parse(name) ?: return ""
        val triad = parsed.noteNames.joinToString(" ")
        val bass = parsed.bass
        return if (bass != null && bass != parsed.root) {
            "бас ${SongChordMarkup.noteName(bass)} · $triad"
        } else {
            triad
        }
    }

    private fun canonicalKey(parsed: ParsedChord): String {
        val rootName = SongChordMarkup.noteName(parsed.root)
        val suffix = when (parsed.family) {
            Family.MAJOR -> ""
            Family.MINOR -> "m"
            Family.DIM -> "dim"
            Family.AUG -> "aug"
            Family.SUS2 -> "sus2"
            Family.SUS4 -> "sus4"
            Family.DOM7 -> "7"
            Family.MIN7 -> "m7"
            Family.MAJ7 -> "maj7"
            Family.OTHER -> return ""
        }
        return rootName + suffix
    }

    private fun rootLength(token: String): Int? {
        if (token.isEmpty()) return null
        val two = token.take(2)
        if (SongChordMarkup.noteIndex(two) != null &&
            (two.endsWith("#") || two.endsWith("b"))
        ) {
            return 2
        }
        return if (SongChordMarkup.noteIndex(token.take(1)) != null) 1 else null
    }

    private fun qualityOf(suffix: String): Pair<List<Int>, Family> {
        val s = suffix.trim()
        val low = s.lowercase()
        return when {
            low.startsWith("m7b5") || low.startsWith("m7-5") ->
                listOf(0, 3, 6, 10) to Family.OTHER
            low.startsWith("maj7") || low.startsWith("maj9") ->
                listOf(0, 4, 7, 11) to Family.MAJ7
            low.startsWith("maj") -> listOf(0, 4, 7) to Family.MAJOR
            low.startsWith("dim7") -> listOf(0, 3, 6, 9) to Family.DIM
            low.startsWith("dim") -> listOf(0, 3, 6) to Family.DIM
            low.startsWith("aug") -> listOf(0, 4, 8) to Family.AUG
            low.startsWith("sus2") -> listOf(0, 2, 7) to Family.SUS2
            low.startsWith("7sus") || low.startsWith("sus4") && low.contains("7") ->
                listOf(0, 5, 7, 10) to Family.SUS4
            low.startsWith("sus4") || low == "sus" -> listOf(0, 5, 7) to Family.SUS4
            low.startsWith("m7") || low.startsWith("min7") -> listOf(0, 3, 7, 10) to Family.MIN7
            low.startsWith("m9") || low.startsWith("min9") -> listOf(0, 3, 7, 10, 2) to Family.MIN7
            low.startsWith("m6") || low.startsWith("min6") -> listOf(0, 3, 7, 9) to Family.MINOR
            low.startsWith("min") || low == "m" || (low.startsWith("m") && !low.startsWith("maj")) -> {
                val extra = mutableListOf(0, 3, 7)
                if (low.contains("7")) extra += 10
                if (low.contains("6")) extra += 9
                if (low.contains("9")) extra += 2
                extra to if (extra.contains(10)) Family.MIN7 else Family.MINOR
            }
            low.startsWith("add9") || low.startsWith("add2") ->
                listOf(0, 4, 7, 2) to Family.MAJOR
            low.startsWith("7") -> listOf(0, 4, 7, 10) to Family.DOM7
            low.startsWith("9") -> listOf(0, 4, 7, 10, 2) to Family.DOM7
            low.startsWith("6") -> listOf(0, 4, 7, 9) to Family.MAJOR
            else -> listOf(0, 4, 7) to Family.MAJOR
        }
    }

    private fun barreShape(parsed: ParsedChord): GuitarShape? {
        val kind = when (parsed.family) {
            Family.MAJOR, Family.OTHER -> "maj"
            Family.MINOR -> "min"
            Family.DOM7 -> "7"
            Family.MIN7 -> "m7"
            Family.MAJ7 -> "maj7"
            Family.SUS4 -> "sus4"
            Family.SUS2 -> "sus2"
            Family.DIM -> "dim"
            Family.AUG -> "aug"
        }
        val eFret = Math.floorMod(parsed.root - 4, 12)
        val aFret = Math.floorMod(parsed.root - 9, 12)
        val e = eShape(eFret, kind)
        val a = aShape(aFret, kind)
        val pickE = when {
            eFret in 1..5 && aFret !in 1..4 -> true
            aFret in 1..4 && eFret !in 1..5 -> false
            eFret in 0..4 && aFret > 4 -> true
            aFret in 0..4 && eFret > 4 -> false
            else -> eFret <= aFret
        }
        return GuitarShape(if (pickE) e else a)
    }

    private fun eShape(n: Int, kind: String): List<Int> = when (kind) {
        "min" -> listOf(n, n + 2, n + 2, n, n, n)
        "7" -> listOf(n, n + 2, n, n + 1, n, n)
        "m7" -> listOf(n, n + 2, n, n, n, n)
        "maj7" -> listOf(n, n + 2, n + 1, n + 1, n, n)
        "sus4" -> listOf(n, n + 2, n + 2, n + 2, n, n)
        "sus2" -> listOf(n, n + 2, n + 4, n + 1, n, n)
        "dim" -> listOf(n, n + 1, n + 2, n, n + 1, n)
        "aug" -> listOf(n, n + 3, n + 2, n + 1, n, n)
        else -> listOf(n, n + 2, n + 2, n + 1, n, n)
    }

    private fun aShape(n: Int, kind: String): List<Int> = when (kind) {
        "min" -> listOf(-1, n, n + 2, n + 2, n + 1, n)
        "7" -> listOf(-1, n, n + 2, n, n + 2, n)
        "m7" -> listOf(-1, n, n + 2, n, n + 1, n)
        "maj7" -> listOf(-1, n, n + 2, n + 1, n + 2, n)
        "sus4" -> listOf(-1, n, n + 2, n + 2, n + 3, n)
        "sus2" -> listOf(-1, n, n + 2, n + 2, n, n)
        "dim" -> listOf(-1, n, n + 1, n + 2, n + 1, -1)
        "aug" -> listOf(-1, n, n + 3, n + 2, n + 2, n)
        else -> listOf(-1, n, n + 2, n + 2, n + 2, n)
    }

    private val OPEN_SHAPES: Map<String, GuitarShape> = mapOf(
        "C" to shape("x32010"),
        "C#" to shape("x43121"),
        "D" to shape("xx0232"),
        "D#" to shape("xx1343"),
        "E" to shape("022100"),
        "F" to shape("133211"),
        "F#" to shape("244322"),
        "G" to shape("320003"),
        "G#" to shape("466544"),
        "A" to shape("x02220"),
        "B" to shape("x13331"),
        "H" to shape("x24442"),
        "Cm" to shape("x35543"),
        "C#m" to shape("x46654"),
        "Dm" to shape("xx0231"),
        "D#m" to shape("xx1342"),
        "Em" to shape("022000"),
        "Fm" to shape("133111"),
        "F#m" to shape("244222"),
        "Gm" to shape("355333"),
        "G#m" to shape("466444"),
        "Am" to shape("x02210"),
        "Bm" to shape("x13321"),
        "Hm" to shape("x24432"),
        "C7" to shape("x32310"),
        "D7" to shape("xx0212"),
        "E7" to shape("020100"),
        "F7" to shape("131211"),
        "G7" to shape("320001"),
        "A7" to shape("x02020"),
        "B7" to shape("x13131"),
        "H7" to shape("x21202"),
        "Am7" to shape("x02010"),
        "Dm7" to shape("xx0211"),
        "Em7" to shape("020000"),
        "Hm7" to shape("x20202"),
        "Cmaj7" to shape("x32000"),
        "Dmaj7" to shape("xx0222"),
        "Fmaj7" to shape("133210"),
        "Gmaj7" to shape("320002"),
        "Amaj7" to shape("x02120"),
        "Csus4" to shape("x33011"),
        "Dsus4" to shape("xx0233"),
        "Esus4" to shape("022200"),
        "Gsus4" to shape("320013"),
        "Asus4" to shape("x02230"),
        "Csus2" to shape("x30010"),
        "Dsus2" to shape("xx0230"),
        "Asus2" to shape("x02200"),
    )

    private fun shape(code: String): GuitarShape {
        require(code.length == 6)
        return GuitarShape(
            code.map { ch ->
                when {
                    ch == 'x' || ch == 'X' -> -1
                    ch.isDigit() -> ch.digitToInt()
                    else -> -1
                }
            },
        )
    }
}
