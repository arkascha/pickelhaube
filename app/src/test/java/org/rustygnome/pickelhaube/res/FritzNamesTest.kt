package org.rustygnome.pickelhaube.res

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "FRITZ!Box" and the like are names and must not be split over two lines: in the string resources of
 * every language, a "FRITZ!" followed by a letter has a word joiner (written as the escape ⁠) after the "!".
 */
class FritzNamesTest {

    @Test
    fun fritzNamesAreKeptTogether() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val unprotected = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .flatMap { dir -> dir.listFiles { f -> f.extension == "xml" }.orEmpty().toList() }
            .flatMap { file ->
                // Comments blanked out, keeping the line numbers: they may name the names as such.
                val text = file.readText().replace(COMMENT) { it.value.replace(NOT_NEWLINE, " ") }
                text.lines().withIndex()
                    .filter { (_, line) -> UNPROTECTED.containsMatchIn(line) }
                    .map { (i, line) -> "${file.parentFile.name}/${file.name}:${i + 1}: ${line.trim()}" }
            }
        assertTrue("FRITZ! names without a word joiner:\n" + unprotected.joinToString("\n"), unprotected.isEmpty())
    }

    private companion object {
        val UNPROTECTED = Regex("""FRITZ!(?=[A-Za-z])""")
        val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val NOT_NEWLINE = Regex("[^\n]")
    }
}
