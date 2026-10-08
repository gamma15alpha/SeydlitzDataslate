package space.seydlitz.dataslate.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.seydlitz.dataslate.api.ApiJson
import java.io.File

class ContentTest {
    private fun example(name: String) = File("../../schemas/examples/$name").readText()
    private val pack = ApiJson.decodeFromString<ContentPack>(example("dh1-mock.content.json"))

    @Test
    fun pickFollowsSchemaRule() {
        val text = LocalizedText(mapOf("ru" to "Ловкость", "en" to "Agility", "en-GB" to "Agility (UK)"))
        assertEquals("Ловкость", LocalizedText("Ловкость").pick("en"))
        assertEquals("Agility (UK)", text.pick("en-GB"))
        assertEquals("Agility", text.pick("en-US"))
        assertEquals("Ловкость", text.pick("de"))
        assertEquals("Agility", LocalizedText(mapOf("en" to "Agility", "fr" to "Agilité")).pick("de"))
        assertEquals("Agilité", LocalizedText(mapOf("fr" to "Agilité")).pick("ru"))
    }

    @Test
    fun termPartsDuplicateEnglishOnlyForNonEnglish() {
        val ag = Entry("ag", LocalizedText(mapOf("ru" to "Ловкость", "en" to "Agility")), abbreviation = LocalizedText("Ag"))
        assertEquals(TermParts("Ag", "Ловкость", "Agility"), ag.termParts("ru"))
        assertEquals(TermParts("Ag", "Agility", null), ag.termParts("en-GB"))
        val armour = Entry("x", LocalizedText(mapOf("ru" to "Броня", "en" to "Armor", "en-GB" to "Armour")))
        assertNull(armour.termParts("en-GB").english)
        assertNull(Entry("x", LocalizedText("Улей Примус")).termParts("ru").english)
    }

    @Test
    fun allPutsOriginalFirst() {
        assertEquals(listOf("ru" to "Б", "en" to "A"), LocalizedText(mapOf("en" to "A", "ru" to "Б")).all())
    }

    @Test
    fun findsNestedEntries() {
        val c = pack.content
        assertEquals("per", c.find("characteristics/per")?.id)
        assertEquals("test-scribe-2", c.find("ranks/test-scribe-2")?.id)
        assertEquals("test-lore-archives", c.find("specializations/test-lore-archives")?.id)
        assertNull(c.find("skills/missing"))
        assertNull(c.find("secrets/test-notice"))
    }

    @Test
    fun everyLinkInMockPackResolves() {
        val refs = Regex("""dataslate:([a-z]+/[a-z0-9-]+)""").findAll(example("dh1-mock.content.json")).map { it.groupValues[1] }.toList()
        assertTrue(refs.isNotEmpty())
        refs.forEach { assertNotNull(it, pack.content.find(it)) }
    }

    @Test
    fun parsesCharacterV4() {
        val character = ApiJson.decodeFromString<CharacterFile>(example("dh1.character.json"))
        assertEquals(4, character.sheetVersion)
        assertEquals("Игрок", character.author?.name)
        assertEquals(Choice.Id("test-forge"), character.sheet.homeworld)
        assertEquals(Choice.Custom("Младший архивариус"), character.sheet.rank)
        assertEquals(40, character.sheet.characteristics["int"]?.value)
        assertEquals(Choice.Custom("Ереси сектора"), character.sheet.skills.last().specialization)
    }
}
