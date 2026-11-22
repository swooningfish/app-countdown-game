package com.xrmx.countdown_game

import com.xrmx.countdown_game.game.WordDictionary
import com.xrmx.countdown_game.game.WordListInfo
import com.xrmx.countdown_game.game.WordListParser
import com.xrmx.countdown_game.platform.deleteStoredText
import com.xrmx.countdown_game.platform.downloadText
import com.xrmx.countdown_game.platform.readStoredText
import com.xrmx.countdown_game.platform.writeStoredText
import com.xrmx.countdown_game.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class LoadedWordList(val dictionary: WordDictionary, val info: WordListInfo)

/** Loads the bundled word list, or a replacement the user downloaded or uploaded. */
object WordListRepository {
    private const val WORDS_FILE = "custom-words.txt"
    private const val INFO_FILE = "custom-words-info.txt"

    suspend fun load(): LoadedWordList = withContext(Dispatchers.Default) {
        loadCustom() ?: loadBuiltIn()
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadBuiltIn(): LoadedWordList {
        val dictionary = WordDictionary.parse(Res.readBytes("files/words.txt").decodeToString())
        return LoadedWordList(dictionary, WordListInfo(true, WordListInfo.BUILT_IN_SOURCE, dictionary.size, null))
    }

    private fun loadCustom(): LoadedWordList? = runCatching {
        val text = readStoredText(WORDS_FILE) ?: return null
        val dictionary = WordDictionary.parse(text)
        if (dictionary.size < WordListParser.MIN_WORDS) return null
        val meta = readStoredText(INFO_FILE).orEmpty().lines()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        LoadedWordList(dictionary, WordListInfo(false, meta["source"] ?: "Custom word list", dictionary.size, meta["updated"]))
    }.getOrNull()

    suspend fun download(url: String): LoadedWordList {
        val text = downloadText(url.trim())
        return import(text, source = url.trim())
    }

    @OptIn(ExperimentalTime::class)
    suspend fun import(text: String, source: String): LoadedWordList = withContext(Dispatchers.Default) {
        val words = WordListParser.parse(text)
        require(words.size >= WordListParser.MIN_WORDS) {
            "Only ${words.size} usable words found. A word list needs at least ${WordListParser.MIN_WORDS}, one per line."
        }
        val updated = Clock.System.now().toString().take(10)
        writeStoredText(WORDS_FILE, words.joinToString("\n"))
        writeStoredText(INFO_FILE, "source=${source.replace('\n', ' ')}\nupdated=$updated")
        LoadedWordList(WordDictionary(words), WordListInfo(false, source, words.size, updated))
    }

    suspend fun resetToBuiltIn(): LoadedWordList = withContext(Dispatchers.Default) {
        deleteStoredText(WORDS_FILE)
        deleteStoredText(INFO_FILE)
        loadBuiltIn()
    }
}
