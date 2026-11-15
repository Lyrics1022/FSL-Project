package com.example.sign_language_translator

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Local unit tests verifying vocabulary alignment between vocab.json and FslVocabularyData.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun vocabulary_has105AlignedActiveSigns() {
        val items = FslVocabularyData.items
        assertEquals(105, items.size)
        assertEquals((1..105).toList(), items.map { it.id })

        val itemNames = items.map { it.name }.toSet()
        assertEquals(105, itemNames.size)

        val candidates = listOf(
            File("src/main/assets/vocab.json"),
            File("app/src/main/assets/vocab.json")
        )
        val vocabFile = candidates.firstOrNull { it.exists() }
        assertNotNull("vocab.json asset should exist", vocabFile)

        val entries = Regex("\"([^\"]+)\"")
            .findAll(vocabFile!!.readText())
            .map { it.groupValues[1] }
            .toList()
        assertEquals(105, entries.size)

        val assetNames = entries.map { raw ->
            raw.removePrefix("FSL_Dataset_")
                .replace("_", " ")
                .replace("WEELCHAIR", "WHEELCHAIR")
                .trim()
        }.toSet()

        assertEquals(itemNames, assetNames)
    }
}