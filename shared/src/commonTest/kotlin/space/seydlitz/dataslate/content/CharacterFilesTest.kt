package space.seydlitz.dataslate.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CharacterFilesTest {
    private fun envelope(sheet: String, sheetVersion: Int = 4, extra: String = "") = """
        {"format": "seydlitz.character", "formatVersion": 1, "id": "id-1", "system": "dh1",
         "sheetVersion": $sheetVersion, "name": "Вэйн", "createdAt": "2026-10-01T10:00:00Z",
         "updatedAt": "2026-10-02T10:00:00Z", "sheet": $sheet $extra}
    """

    private fun ok(json: String) = assertIs<ReadResult.Ok>(readCharacter(json))

    private fun error(json: String) = assertIs<ReadResult.Failed>(readCharacter(json)).error

    @Test
    fun currentVersionReadsAsIs() {
        val r = ok(envelope("""{"player": "Игрок", "homeworld": {"id": "forge"}, "modifiers": [{"target": "characteristic.ag", "value": -10, "reason": "Ранен"}]}"""))
        assertNull(r.migratedFrom)
        assertEquals(Choice.Id("forge"), r.file.sheet.homeworld)
        assertEquals(listOf(SheetModifier("characteristic.ag", -10, "Ранен")), r.file.sheet.modifiers)
    }

    @Test
    fun v3StringsBecomeCustomChoices() {
        val r = ok(
            envelope(
                """{"homeworld": "Улей", "career": "", "rank": {"id": "r1"},
                    "skills": [{"skill": "lore", "specialization": "Архивы"}, {"skill": "lore", "specialization": " "}, {"skill": "dodge"}]}""",
                sheetVersion = 3,
            ),
        )
        assertEquals(3, r.migratedFrom)
        assertEquals(DH1_SHEET_VERSION, r.file.sheetVersion)
        assertEquals(Choice.Custom("Улей"), r.file.sheet.homeworld)
        assertNull(r.file.sheet.career)
        assertEquals(Choice.Id("r1"), r.file.sheet.rank)
        assertEquals(listOf(Choice.Custom("Архивы"), null, null), r.file.sheet.skills.map { it.specialization })
    }

    @Test
    fun v1HeaderOnly() {
        val r = ok(envelope("""{"player": "Игрок", "career": "Писарь"}""", sheetVersion = 1))
        assertEquals(Choice.Custom("Писарь"), r.file.sheet.career)
        assertTrue(r.file.sheet.characteristics.isEmpty())
    }

    @Test
    fun errors() {
        assertEquals(ReadError.NOT_JSON, error("{oops"))
        assertEquals(ReadError.NOT_CHARACTER, error("[1, 2]"))
        assertEquals(ReadError.NOT_CHARACTER, error("""{"format": "seydlitz.content"}"""))
        assertEquals(ReadError.FORMAT_TOO_NEW, error(envelope("{}").replace("\"formatVersion\": 1", "\"formatVersion\": 2")))
        assertEquals(ReadError.UNKNOWN_SYSTEM, error(envelope("{}").replace("dh1", "rt")))
        assertEquals(ReadError.SHEET_TOO_NEW, error(envelope("{}", sheetVersion = 5)))
        assertEquals(ReadError.INVALID, error(envelope("[]")))
        assertEquals(ReadError.INVALID, error(envelope("""{"characteristics": {"ag": {"base": "много"}}}""")))
        assertEquals(ReadError.INVALID, error(envelope("{}").replace("\"name\": \"Вэйн\",", "")))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        ok(envelope("""{"future": true}""", extra = """, "portrait": {"sha256": "ab"}"""))
    }

    @Test
    fun writeReadRoundTrip() {
        val author = Author("01a10fdd-b783-7016-adf1-bdcf1a01711e", "Сейдлиц")
        val file = newCharacter("Вэйн", author, now = "2026-10-08T07:00:00.123Z", id = "id-2").copy(
            sheet = Dh1Sheet(
                homeworld = Choice.Custom("Улей"),
                characteristics = mapOf("ag" to Characteristic(31, 2)),
                skills = listOf(SheetSkill("lore", Choice.Id("archives"), 2)),
            ),
        )
        for (pretty in listOf(false, true)) {
            assertEquals(file, ok(writeCharacter(file, pretty)).file)
        }
        val compact = writeCharacter(file)
        assertTrue(compact.startsWith("""{"format":"seydlitz.character","formatVersion":1,"""))
        assertFalse(compact.contains("\"modifiers\""))
        assertEquals(author, file.asCopy().author)
        assertFalse(writeCharacter(newCharacter("Без автора")).contains("\"author\""))
    }

    @Test
    fun newIdsAreUuidV7AndDistinct() {
        val a = newId()
        assertEquals('7', a[14])
        assertNotEquals(a, newId())
        val copy = newCharacter("A").asCopy()
        assertEquals("A", copy.name)
        assertTrue(Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d{1,3})?Z""").matches(nowIso()))
    }
}
