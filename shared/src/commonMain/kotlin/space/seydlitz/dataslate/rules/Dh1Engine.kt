package space.seydlitz.dataslate.rules

import space.seydlitz.dataslate.content.Choice
import space.seydlitz.dataslate.content.Dh1Content
import space.seydlitz.dataslate.content.Dh1Sheet

val DH1_CHARACTERISTICS = listOf("ws", "bs", "s", "t", "ag", "int", "per", "wp", "fel")

// target — id характеристики или навыка.
data class Modifier(val id: String, val target: String, val value: Int)

data class Contribution(val source: String, val value: Int)

data class StatValue(val id: String, val value: Int?, val contributions: List<Contribution>)

data class SkillValue(
    val skill: String,
    val specialization: Choice?,
    val characteristic: String,
    val training: Int,
    val value: Int?,
    val contributions: List<Contribution>,
)

data class Diagnostic(val code: String, val subject: String)

data class Dh1Evaluation(
    val characteristics: List<StatValue>,
    val bonuses: List<StatValue>,
    val skills: List<SkillValue>,
    val diagnostics: List<Diagnostic>,
)

object Dh1Engine {
    fun evaluate(sheet: Dh1Sheet, content: Dh1Content, modifiers: List<Modifier> = emptyList()): Dh1Evaluation {
        val diagnostics = mutableListOf<Diagnostic>()
        sheet.characteristics.keys.filter { it !in DH1_CHARACTERISTICS }
            .forEach { diagnostics += Diagnostic("unknown-characteristic", it) }
        modifiers.filter { m -> m.target !in DH1_CHARACTERISTICS && content.skills.none { it.id == m.target } }
            .forEach { diagnostics += Diagnostic("unknown-modifier-target", it.id) }

        val characteristics = DH1_CHARACTERISTICS.map { id ->
            val c = sheet.characteristics[id]
            if (c?.base == null) {
                StatValue(id, null, emptyList())
            } else {
                val parts = buildList {
                    add(Contribution("base", c.base))
                    if (c.advances != 0) add(Contribution("advances", 5 * c.advances))
                    modifiers.filter { it.target == id }.forEach { add(Contribution("modifier:${it.id}", it.value)) }
                }
                StatValue(id, parts.sumOf { it.value }, parts)
            }
        }
        val byId = characteristics.associateBy { it.id }

        val bonuses = characteristics.map { stat ->
            val unnatural = sheet.characteristics[stat.id]?.unnatural ?: 1
            val value = stat.value?.let { it / 10 * unnatural }
            val parts = if (value == null) emptyList() else buildList {
                add(Contribution("tens:${stat.id}", stat.value / 10))
                if (unnatural != 1) add(Contribution("unnatural", unnatural))
            }
            StatValue(stat.id, value, parts)
        }

        val learned = sheet.skills.map { s ->
            val def = content.skills.firstOrNull { it.id == s.skill }
            if (def == null) diagnostics += Diagnostic("unknown-skill", s.skill)
            if (s.training !in 1..3) diagnostics += Diagnostic("training-out-of-range", s.skill)
            skill(s.skill, s.specialization, def?.characteristic ?: "", s.training, byId, modifiers)
        }
        val untrained = content.skills
            .filter { it.kind == "basic" && sheet.skills.none { s -> s.skill == it.id } }
            .map { skill(it.id, null, it.characteristic, 0, byId, modifiers) }

        return Dh1Evaluation(characteristics, bonuses, learned + untrained, diagnostics)
    }

    // D44, D64: необученный базовый — половина с отбрасыванием дробной части; +10 / +20 за мастерство.
    private fun skill(
        id: String,
        specialization: Choice?,
        characteristic: String,
        training: Int,
        stats: Map<String, StatValue>,
        modifiers: List<Modifier>,
    ): SkillValue {
        val base = stats[characteristic]?.value ?: return SkillValue(id, specialization, characteristic, training, null, emptyList())
        val parts = buildList {
            if (training == 0) add(Contribution("half:$characteristic", base / 2)) else add(Contribution(characteristic, base))
            when (training) {
                2 -> add(Contribution("mastery", 10))
                3 -> add(Contribution("mastery", 20))
            }
            modifiers.filter { it.target == id }.forEach { add(Contribution("modifier:${it.id}", it.value)) }
        }
        return SkillValue(id, specialization, characteristic, training, parts.sumOf { it.value }, parts)
    }
}
