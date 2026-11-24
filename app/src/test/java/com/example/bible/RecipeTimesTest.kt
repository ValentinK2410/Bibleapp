package com.example.bible

import com.example.bible.data.mynotes.MyNote
import com.example.bible.data.mynotes.MyNoteKind
import com.example.bible.data.mynotes.RecipeLine
import com.example.bible.data.mynotes.RecipeTextPiece
import com.example.bible.data.mynotes.expandRecipeIngredientLines
import com.example.bible.data.mynotes.hasRecipeText
import com.example.bible.data.mynotes.splitRecipeIngredient
import com.example.bible.data.mynotes.splitRecipeIngredientList
import com.example.bible.data.mynotes.splitRecipeTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTimesTest {
    @Test
    fun findsMinutesHoursSecondsAndRanges() {
        assertSpans(
            "Варить 10 мин до мягкости",
            plain("Варить "),
            time("10 мин"),
            plain(" до мягкости"),
        )
        assertSpans("Тушить 1 час", plain("Тушить "), time("1 час"))
        assertSpans("Обжарить 30 сек и снять", plain("Обжарить "), time("30 сек"), plain(" и снять"))
        assertSpans("Оставить на 1,5 ч", plain("Оставить на "), time("1,5 ч"))
        assertSpans(
            "Держать 15–20 минут под крышкой",
            plain("Держать "),
            time("15–20 минут"),
            plain(" под крышкой"),
        )
        assertSpans("1.5 часа на медленном огне", time("1.5 часа"), plain(" на медленном огне"))
        assertSpans("5 часов", time("5 часов"))
        assertSpans("1 минуту", time("1 минуту"))
        assertSpans("45 секунд", time("45 секунд"))
        assertSpans("15-20 мин", time("15-20 мин"))
        assertSpans("15 — 20 мин", time("15 — 20 мин"))
        assertSpans("15–20минут", time("15–20минут"))
        assertSpans("подождать 1 ч.", plain("подождать "), time("1 ч."))
        assertSpans("ещё 2 мин.", plain("ещё "), time("2 мин."))
        assertSpans("10 МИН и 1 ЧАС", time("10 МИН"), plain(" и "), time("1 ЧАС"))
    }

    @Test
    fun mergesAdjacentDurationsAndKeepsSeparateOnes() {
        assertSpans(
            "Томить 2 часа 15 минут, затем остудить",
            plain("Томить "),
            time("2 часа 15 минут"),
            plain(", затем остудить"),
        )
        assertSpans("выдержать 10 мин. 30 сек.", plain("выдержать "), time("10 мин. 30 сек."))
        assertSpans(
            "Сначала 10 мин, потом ещё 5 мин",
            plain("Сначала "),
            time("10 мин"),
            plain(", потом ещё "),
            time("5 мин"),
        )
        assertSpans("Полчаса потомить", time("Полчаса"), plain(" потомить"))
        assertSpans("полтора часа под крышкой", time("полтора часа"), plain(" под крышкой"))
    }

    @Test
    fun doesNotTreatPlainWordsAndSpoonsAsTime() {
        listOf(
            "без времени, часто помешивать",
            "10 яблок и 2 человека",
            "добавьте 1 ч. л. соли",
            "1 чашка бульона",
            "10 миндаля",
            "в 2024 г. выпустить",
        ).forEach { text ->
            assertEquals(listOf(plain(text)), splitRecipeTimes(text))
        }
        assertSpans(
            "добавьте 1 ч. л. соли и варите 10 мин",
            plain("добавьте 1 ч. л. соли и варите "),
            time("10 мин"),
        )
    }

    @Test
    fun piecesRebuildTheOriginalString() {
        val samples = listOf(
            "",
            "просто текст",
            "Варить 10 мин до мягкости",
            "2 часа 15 минут",
            "15–20 минут, затем 30 сек",
            "1,5 ч.\nЕщё 10 мин.",
        )
        samples.forEach { text ->
            assertEquals(text, splitRecipeTimes(text).joinToString("") { it.text })
        }
    }

    @Test
    fun splitsIngredientAmounts() {
        assertAmount("200 г муки", "200 г", "муки")
        assertAmount("200г муки", "200г", "муки")
        assertAmount("1,5 ст. л. сахара", "1,5 ст. л.", "сахара")
        assertAmount("3 ст.л. масла", "3 ст.л.", "масла")
        assertAmount("1 ч. л. корицы", "1 ч. л.", "корицы")
        assertAmount("2-3 зубчика чеснока", "2-3 зубчика", "чеснока")
        assertAmount("1/2 стакана молока", "1/2 стакана", "молока")
        assertAmount("½ лимона", "½", "лимона")
        assertAmount("1 л молока", "1 л", "молока")
        assertAmount("2 груши", "2", "груши")
        assertAmount("по вкусу соль", "по вкусу", "соль")
        assertAmount("щепотка соли", "щепотка", "соли")
        assertAmount("Кефир", null, "Кефир")
        assertAmount("10 мин кипячения", null, "10 мин кипячения")
        assertAmount("   ", null, "")
    }

    @Test
    fun splitsIngredientListsOnNewlinesCommasSemicolonsAndBullets() {
        assertEquals(
            listOf("картошка", "лавровый лист", "морковь"),
            splitRecipeIngredientList("картошка\nлавровый лист\nморковь"),
        )
        assertEquals(
            listOf("картошка", "лавровый лист", "морковь"),
            splitRecipeIngredientList("картошка\r\nлавровый лист\rморковь"),
        )
        assertEquals(
            listOf("картошка", "лавровый лист", "морковь"),
            splitRecipeIngredientList("картошка, лавровый лист, морковь"),
        )
        assertEquals(
            listOf("картошка", "лавровый лист", "морковь"),
            splitRecipeIngredientList("картошка,лавровый лист;морковь"),
        )
        assertEquals(
            listOf("картошка", "лавровый лист"),
            splitRecipeIngredientList("- картошка\n- лавровый лист"),
        )
        assertEquals(
            listOf("картошка", "лавровый лист"),
            splitRecipeIngredientList("-картошка\n•лавровый лист"),
        )
        assertEquals(
            listOf("картошка", "лавровый лист", "лук"),
            splitRecipeIngredientList("• картошка • лавровый лист • лук"),
        )
        assertEquals(
            listOf("картошка", "лавровый лист"),
            splitRecipeIngredientList("* картошка\n* лавровый лист"),
        )
        assertEquals(
            listOf("картошка", "2 лука"),
            splitRecipeIngredientList("картошка - 2 лука"),
        )
        assertEquals(
            listOf("картошка", "лавровый лист", "лук"),
            splitRecipeIngredientList("- картошка, лавровый лист; лук"),
        )
        assertEquals(listOf("Кефир"), splitRecipeIngredientList("Кефир"))
        assertEquals(listOf("какой-то продукт"), splitRecipeIngredientList("какой-то продукт"))
        assertEquals(emptyList<String>(), splitRecipeIngredientList("  \n\t•\n - "))
    }

    @Test
    fun keepsDecimalCommasRangesAndParentheticalCommas() {
        assertEquals(
            listOf("1,5 ст. л. масла", "200 г муки"),
            splitRecipeIngredientList("1,5 ст. л. масла, 200 г муки"),
        )
        assertEquals(
            listOf("2-3 зубчика чеснока"),
            splitRecipeIngredientList("2-3 зубчика чеснока"),
        )
        assertEquals(
            listOf("2 - 3 зубчика чеснока"),
            splitRecipeIngredientList("2 - 3 зубчика чеснока"),
        )
        assertEquals(
            listOf("1,5 - 2 ст. л. сахара", "соль"),
            splitRecipeIngredientList("1,5 - 2 ст. л. сахара, соль"),
        )
        assertEquals(
            listOf("мука (пшеничная, высшего сорта)", "сахар"),
            splitRecipeIngredientList("мука (пшеничная, высшего сорта), сахар"),
        )
        assertEquals(
            listOf("по вкусу соль и перец"),
            splitRecipeIngredientList("по вкусу соль и перец"),
        )
    }

    @Test
    fun expandsStoredLinesIntoSeparateCheckboxRows() {
        val rows = expandRecipeIngredientLines(
            listOf(
                RecipeLine("a", "картошка"),
                RecipeLine("b", "лавровый лист\nморковь, лук"),
                RecipeLine("c", "   "),
                RecipeLine("d", "1,5 ст. л. масла, 200 г муки"),
            ),
        )
        assertEquals(listOf("a", "b#0", "b#1", "b#2", "d#0", "d#1"), rows.map { it.id })
        assertEquals(
            listOf("картошка", "лавровый лист", "морковь", "лук", "масла", "муки"),
            rows.map { it.parts.name },
        )
        assertEquals(null, rows[0].parts.amount)
        assertEquals("1,5 ст. л.", rows[4].parts.amount)
        assertEquals("200 г", rows[5].parts.amount)
    }

    @Test
    fun recipeWithTextOpensInViewAndEmptyOneDoesNot() {
        assertFalse(MyNote(id = "t", kind = MyNoteKind.TEXT, title = "Дневник").hasRecipeText())
        assertFalse(MyNote(id = "r", kind = MyNoteKind.RECIPE).hasRecipeText())
        assertFalse(
            MyNote(
                id = "r",
                kind = MyNoteKind.RECIPE,
                ingredients = listOf(RecipeLine("i", "  ")),
                steps = listOf(RecipeLine("s", "")),
            ).hasRecipeText(),
        )
        assertTrue(MyNote(id = "r", kind = MyNoteKind.RECIPE, title = "Суп").hasRecipeText())
        assertTrue(MyNote(id = "r", kind = MyNoteKind.RECIPE, body = "К чаю").hasRecipeText())
        assertTrue(MyNote(id = "r", kind = MyNoteKind.RECIPE, servings = "4").hasRecipeText())
        assertTrue(MyNote(id = "r", kind = MyNoteKind.RECIPE, cookTime = "25 мин").hasRecipeText())
        assertTrue(
            MyNote(
                id = "r",
                kind = MyNoteKind.RECIPE,
                steps = listOf(RecipeLine("s", "Жарить")),
            ).hasRecipeText(),
        )
    }

    private fun assertAmount(text: String, amount: String?, name: String) {
        val parts = splitRecipeIngredient(text)
        assertEquals(amount, parts.amount)
        assertEquals(name, parts.name)
        if (amount == null && name.isNotEmpty()) {
            assertNull(parts.amount)
        }
    }

    private fun assertSpans(text: String, vararg expected: RecipeTextPiece) {
        assertEquals(expected.toList(), splitRecipeTimes(text))
    }

    private fun time(text: String) = RecipeTextPiece(text, time = true)

    private fun plain(text: String) = RecipeTextPiece(text, time = false)
}
