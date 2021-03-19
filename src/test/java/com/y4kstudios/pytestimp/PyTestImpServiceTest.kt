package com.y4kstudios.pytestimp

import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ConvertWildcardPatternToRegexPatternTest(
    pattern: String,
    private val matches: Collection<String>,
    private val non_matches: Collection<String>)
    : TestCase()
{
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name="{0}")
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf("abc", listOf("abc"), listOf("def")),
            arrayOf("a?c", listOf("abc"), listOf("def")),
            arrayOf("a[0-9]", listOf("a1"), listOf("d2")),
            arrayOf("a[!0-9]", listOf("ab"), listOf("a2")),
            arrayOf("Taco-*", listOf("TacoSalad"), listOf("Tacos")),
            arrayOf("C[0-9][0-9][0-9][0-9][0-9]", listOf("C12345"), listOf("F23")),

            // Unclosed character classes should result in a RegEx that matches nothing
            arrayOf("C[0-9][0-9][0-9][0-9[0-9]", emptyList<String>(), listOf("C12345", "Test", "AnythingAtAllShouldNotMatch")),
        )
    }

    private val regexRaw: String = convertWildcardPatternToRegexPattern(pattern, true)
    private val regex: Regex = Regex(regexRaw)

    @Test
    fun testMatches() {
        for (text in matches) {
            assert(text.matches(regex))
        }
    }

    @Test
    fun testNonMatches() {
        for (text in non_matches) {
            assert(!text.matches(regex))
        }
    }
}
