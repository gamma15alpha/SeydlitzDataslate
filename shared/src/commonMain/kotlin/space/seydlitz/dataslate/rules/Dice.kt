package space.seydlitz.dataslate.rules

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.random.Random

data class DiceExpr(val count: Int, val sides: Int, val bonus: Int) {
    fun roll(random: Random): DiceRoll {
        val dice = List(count) { random.nextInt(1, sides + 1) }
        return DiceRoll(this, dice, dice.sum() + bonus)
    }

    companion object {
        private val PATTERN = Regex("""^\s*(\d*)d(\d+)\s*(?:([+-])\s*(\d+))?\s*$""", RegexOption.IGNORE_CASE)

        fun parse(text: String): DiceExpr? {
            val m = PATTERN.matchEntire(text) ?: return null
            val (count, sides, sign, bonus) = m.destructured
            val n = if (count.isEmpty()) 1 else count.toInt()
            val s = sides.toInt()
            if (n !in 1..100 || s !in 2..1000) return null
            val b = if (bonus.isEmpty()) 0 else bonus.toInt() * (if (sign == "-") -1 else 1)
            return DiceExpr(n, s, b)
        }
    }
}

data class DiceRoll(val expr: DiceExpr, val dice: List<Int>, val total: Int)

@OptIn(ExperimentalJsExport::class)
@JsExport
data class TestResult(val target: Int, val roll: Int, val success: Boolean, val degrees: Int)

// DH1: успех — бросок не выше цели; степень — за каждые полные 10 разницы.
@OptIn(ExperimentalJsExport::class)
@JsExport
fun testD100(target: Int, roll: Int): TestResult {
    val success = roll <= target
    val margin = if (success) target - roll else roll - target
    return TestResult(target, roll, success, margin / 10)
}
