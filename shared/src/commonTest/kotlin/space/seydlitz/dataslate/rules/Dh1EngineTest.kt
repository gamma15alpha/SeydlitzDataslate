package space.seydlitz.dataslate.rules

import space.seydlitz.dataslate.content.parseCharacter
import space.seydlitz.dataslate.content.parseContentPack
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Dh1EngineTest {
    private val content = parseContentPack(
        """
        {"system": "dh1", "content": {"skills": [
          {"id": "notice", "name": "Внимательность", "characteristic": "per", "kind": "basic"},
          {"id": "lore", "name": "Знание", "characteristic": "int", "kind": "advanced"},
          {"id": "dodge", "name": "Уклонение", "characteristic": "ag", "kind": "basic"}
        ]}}
        """,
    ).content

    private fun sheet(characteristics: String, skills: String = "[]") = parseCharacter(
        """{"id": "x", "name": "N", "system": "dh1", "sheetVersion": 4, "extra": 1,
           "sheet": {"characteristics": $characteristics, "skills": $skills}}""",
    ).sheet

    @Test
    fun characteristicSumsSources() {
        val r = Dh1Engine.evaluate(sheet("""{"ag": {"base": 31, "advances": 2}}"""), content, listOf(Modifier("fatigue", "ag", -10)))
        val ag = r.characteristics.first { it.id == "ag" }
        assertEquals(31, ag.value)
        assertEquals(listOf(Contribution("base", 31), Contribution("advances", 10), Contribution("modifier:fatigue", -10)), ag.contributions)
        assertNull(r.characteristics.first { it.id == "ws" }.value)
    }

    @Test
    fun bonusWithUnnatural() {
        val r = Dh1Engine.evaluate(sheet("""{"t": {"base": 38, "unnatural": 2}}"""), content)
        assertEquals(6, r.bonuses.first { it.id == "t" }.value)
    }

    @Test
    fun untrainedBasicSkillIsHalfRoundedDown() {
        val r = Dh1Engine.evaluate(sheet("""{"ag": {"base": 35}}"""), content)
        val dodge = r.skills.first { it.skill == "dodge" }
        assertEquals(0, dodge.training)
        assertEquals(17, dodge.value)
    }

    @Test
    fun trainedAndMastery() {
        val r = Dh1Engine.evaluate(
            sheet("""{"int": {"base": 30}, "per": {"base": 40}}""", """[{"skill": "lore", "training": 3}, {"skill": "notice"}]"""),
            content,
        )
        assertEquals(50, r.skills.first { it.skill == "lore" }.value)
        assertEquals(40, r.skills.first { it.skill == "notice" }.value)
        assertTrue(r.skills.none { it.skill == "notice" && it.training == 0 })
    }

    @Test
    fun diagnostics() {
        val r = Dh1Engine.evaluate(
            sheet("""{"xx": {"base": 1}}""", """[{"skill": "nope", "training": 5}]"""),
            content,
            listOf(Modifier("m", "ghost", 5)),
        )
        assertEquals(
            setOf(
                Diagnostic("unknown-characteristic", "xx"),
                Diagnostic("unknown-modifier-target", "m"),
                Diagnostic("unknown-skill", "nope"),
                Diagnostic("training-out-of-range", "nope"),
            ),
            r.diagnostics.toSet(),
        )
    }
}

class DiceTest {
    @Test
    fun parse() {
        assertEquals(DiceExpr(2, 10, 3), DiceExpr.parse("2d10+3"))
        assertEquals(DiceExpr(1, 100, 0), DiceExpr.parse("d100"))
        assertEquals(DiceExpr(1, 5, -2), DiceExpr.parse(" 1D5 - 2 "))
        assertNull(DiceExpr.parse("2d"))
        assertNull(DiceExpr.parse("0d6"))
    }

    // Одинаковое зерно — одинаковые броски на JVM и JS.
    @Test
    fun seededRollIsDeterministic() {
        assertEquals(listOf(4, 1, 2), DiceExpr(3, 10, 0).roll(Random(42)).dice)
    }

    @Test
    fun d100Degrees() {
        assertEquals(TestResult(45, 12, true, 3), testD100(45, 12))
        assertEquals(TestResult(45, 45, true, 0), testD100(45, 45))
        assertEquals(TestResult(45, 78, false, 3), testD100(45, 78))
    }
}
