package com.xrmx.countdown_game.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordDefinitionsTest {
    private val json = """
        [{"word":"ration","phonetics":[],"meanings":[
          {"partOfSpeech":"noun","definitions":[
            {"definition":"A portion, especially of food, allotted to someone.","synonyms":[]},
            {"definition":"Food for a \"day\" \u2013 or more.","example":"x"}]},
          {"partOfSpeech":"verb","definitions":[
            {"definition":"To limit (food, etc.) to a fixed amount.","synonyms":[]}]}]},
         {"word":"ration","meanings":[{"partOfSpeech":"noun","definitions":[
            {"definition":"A portion, especially of food, allotted to someone."}]}]}]
    """.trimIndent()

    @Test
    fun readsSensesWithTheirPartOfSpeech() {
        val senses = WordDefinitions.parse(json)
        assertEquals(
            listOf(
                WordSense("noun", "A portion, especially of food, allotted to someone."),
                WordSense("noun", "Food for a \"day\" \u2013 or more."),
                WordSense("verb", "To limit (food, etc.) to a fixed amount."),
            ),
            senses,
        )
    }

    @Test
    fun limitsTheNumberOfSensesAndHandlesNotFound() {
        assertEquals(1, WordDefinitions.parse(json, limit = 1).size)
        assertTrue(WordDefinitions.parse("""{"title":"No Definitions Found"}""").isEmpty())
    }

    @Test
    fun buildsLowerCaseLinks() {
        assertEquals("https://api.dictionaryapi.dev/api/v2/entries/en/ration", WordDefinitions.url(" RATION "))
        assertEquals("https://en.wiktionary.org/wiki/ration", WordDefinitions.moreUrl("Ration"))
    }
}
