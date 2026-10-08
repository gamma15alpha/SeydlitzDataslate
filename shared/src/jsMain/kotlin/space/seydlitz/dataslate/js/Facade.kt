@file:OptIn(ExperimentalJsExport::class)

package space.seydlitz.dataslate.js

import space.seydlitz.dataslate.content.Choice
import space.seydlitz.dataslate.content.Dh1Content
import space.seydlitz.dataslate.content.FileJson
import space.seydlitz.dataslate.content.LocalizedText
import space.seydlitz.dataslate.content.parseCharacter
import space.seydlitz.dataslate.content.parseContentPack
import space.seydlitz.dataslate.content.termParts
import space.seydlitz.dataslate.rules.Contribution
import space.seydlitz.dataslate.rules.DiceExpr
import space.seydlitz.dataslate.rules.Dh1Engine
import space.seydlitz.dataslate.rules.Modifier
import space.seydlitz.dataslate.rules.StatValue
import kotlin.random.Random

@JsExport
class JsContribution(val source: String, val value: Int)

@JsExport
class JsStat(val id: String, val label: String, val value: Int?, val contributions: Array<JsContribution>)

@JsExport
class JsSkill(val skill: String, val label: String, val training: Int, val value: Int?, val contributions: Array<JsContribution>)

@JsExport
class JsDiagnostic(val code: String, val subject: String)

@JsExport
class JsEvaluation(
    val characteristics: Array<JsStat>,
    val bonuses: Array<JsStat>,
    val skills: Array<JsSkill>,
    val diagnostics: Array<JsDiagnostic>,
)

@JsExport
class JsModifier(val id: String, val target: String, val value: Int)

@JsExport
class JsDiceRoll(val dice: Array<Int>, val total: Int)

// Разобранный пакет держится на стороне Kotlin: не разбирать его на каждый пересчёт.
@JsExport
class Dh1Session(contentJson: String) {
    private val content: Dh1Content = parseContentPack(contentJson).content

    fun evaluate(characterJson: String, lang: String, modifiers: Array<JsModifier> = emptyArray()): JsEvaluation {
        val sheet = parseCharacter(characterJson).sheet
        val result = Dh1Engine.evaluate(sheet, content, modifiers.map { Modifier(it.id, it.target, it.value) })
        return JsEvaluation(
            result.characteristics.map { stat(it, lang) }.toTypedArray(),
            result.bonuses.map { stat(it, lang) }.toTypedArray(),
            result.skills.map { s ->
                val name = content.skills.firstOrNull { it.id == s.skill }?.name?.pick(lang) ?: s.skill
                val spec = when (val c = s.specialization) {
                    is Choice.Id -> content.find("specializations/${c.id}")?.name?.pick(lang) ?: c.id
                    is Choice.Custom -> c.text
                    null -> null
                }
                JsSkill(s.skill, if (spec == null) name else "$name ($spec)", s.training, s.value, contributions(s.contributions))
            }.toTypedArray(),
            result.diagnostics.map { JsDiagnostic(it.code, it.subject) }.toTypedArray(),
        )
    }

    private fun stat(s: StatValue, lang: String): JsStat {
        val label = content.find("characteristics/${s.id}")?.termParts(lang)?.let { it.abbreviation ?: it.name } ?: s.id
        return JsStat(s.id, label, s.value, contributions(s.contributions))
    }
}

private fun contributions(list: List<Contribution>) = list.map { JsContribution(it.source, it.value) }.toTypedArray()

@JsExport
fun pickText(textJson: String, lang: String): String =
    FileJson.decodeFromString(LocalizedText.serializer(), textJson).pick(lang)

// null — выражение не разобрано.
@JsExport
fun rollDice(text: String, seed: Int? = null): JsDiceRoll? {
    val expr = DiceExpr.parse(text) ?: return null
    val roll = expr.roll(if (seed == null) Random.Default else Random(seed))
    return JsDiceRoll(roll.dice.toTypedArray(), roll.total)
}
