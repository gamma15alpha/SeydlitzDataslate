package space.seydlitz.dataslate

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StringsTest {
    private fun keys(dir: String): Set<String> {
        val xml = File("src/main/res/$dir/strings.xml").readText()
        return Regex("""<string name="(\w+)"(?! translatable="false")""").findAll(xml).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun everyStringIsTranslated() {
        assertEquals(keys("values"), keys("values-en"))
    }
}
