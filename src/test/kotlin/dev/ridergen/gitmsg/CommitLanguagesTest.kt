package dev.ridergen.gitmsg

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommitLanguagesTest {

    @Test
    fun `catalogue contains at least one hundred languages`() {
        assertTrue(CommitLanguages.ALL.size >= 100, "only ${CommitLanguages.ALL.size} entries")
        assertEquals(CommitLanguages.ALL.size, CommitLanguages.ALL.map { it.storageValue }.toSet().size)
    }

    @Test
    fun `every entry has a valid country code and non-blank names`() {
        CommitLanguages.ALL.forEach { language ->
            assertTrue(
                language.countryCode.matches(Regex("[A-Z]{2}")),
                "invalid country code: ${language.countryCode}"
            )
            assertTrue(language.englishName.isNotBlank(), "blank English name: $language")
            assertTrue(language.nativeName.isNotBlank(), "blank native name: $language")
        }
    }

    @Test
    fun `required countries are covered`() {
        val required = listOf(
            "CN", "US", "JP", "DE", "FR", "BR", "SA", "IN", "ES", "IT", "KR", "RU", "VN", "TH",
            "TR", "PL", "NL", "SE", "ID", "MY", "UA", "IL", "GR", "CZ", "RO", "HU", "FI", "DK",
            "NO", "PT", "PH", "EG", "ZA", "IR", "PK", "BD", "ET"
        )
        required.forEach { code ->
            assertTrue(
                CommitLanguages.ALL.any { it.countryCode == code },
                "missing country $code"
            )
        }
    }

    @Test
    fun `region variants are separate entries`() {
        assertTrue(CommitLanguages.find("CN", "Chinese (Simplified)") != null)
        assertTrue(CommitLanguages.find("TW", "Chinese (Traditional)") != null)
        assertTrue(CommitLanguages.find("PT", "Portuguese (Portugal)") != null)
        assertTrue(CommitLanguages.find("BR", "Portuguese (Brazil)") != null)
        assertTrue(CommitLanguages.find("US", "English") != null)
        assertTrue(CommitLanguages.find("GB", "English (UK)") != null)
        assertEquals(
            "CN · Chinese (Simplified) · 简体中文",
            CommitLanguages.find("CN", "Chinese (Simplified)")!!.displayLabel()
        )
    }

    @Test
    fun `search matches country code english name and native name`() {
        val byCode = CommitLanguages.search(CommitLanguages.ALL, "cn")
        assertTrue(byCode.isNotEmpty())
        assertTrue(byCode.all { it.countryCode == "CN" }, byCode.joinToString { it.displayLabel() })

        val simplified = CommitLanguages.search(CommitLanguages.ALL, "SIMPLIFIED")
        assertTrue(simplified.any { it.storageValue == "CN|Chinese (Simplified)" })
        assertTrue(simplified.any { it.storageValue == "SG|Chinese (Simplified, Singapore)" })

        val native = CommitLanguages.search(CommitLanguages.ALL, "简体")
        assertTrue(native.any { it.countryCode == "CN" })

        val spanish = CommitLanguages.search(CommitLanguages.ALL, "español")
        assertTrue(spanish.any { it.countryCode == "ES" })

        val arabic = CommitLanguages.search(CommitLanguages.ALL, "العربية")
        assertTrue(arabic.isNotEmpty())
        assertTrue(arabic.all { it.nativeName.contains("العربية") })

        assertNull(CommitLanguages.parse("XX|Unknown"))
        assertTrue(CommitLanguages.search(CommitLanguages.ALL, "zzzzzz").isEmpty())
    }

    @Test
    fun `search is case insensitive for english names`() {
        assertTrue(CommitLanguages.search(CommitLanguages.ALL, "chinese").isNotEmpty())
        assertTrue(CommitLanguages.search(CommitLanguages.ALL, "ChInEsE").isNotEmpty())
        assertTrue(CommitLanguages.search(CommitLanguages.ALL, "JAPANESE").isNotEmpty())
    }

    @Test
    fun `legacy values migrate into the new field`() {
        assertEquals(
            "CN|Chinese (Simplified)",
            CommitLanguages.resolve("", "zh").storageValue
        )
        assertEquals("US|English", CommitLanguages.resolve("", "en").storageValue)
        assertEquals("US|English", CommitLanguages.resolve("", "").storageValue)

        val stored = CommitLanguages.find("TW", "Chinese (Traditional)")!!
        assertEquals(stored, CommitLanguages.resolve(stored.storageValue, "zh"))
    }

    @Test
    fun `storage value round trips`() {
        CommitLanguages.ALL.forEach { language ->
            assertEquals(language, CommitLanguages.parse(language.storageValue))
        }
        assertNull(CommitLanguages.parse(""))
        assertNull(CommitLanguages.parse("US"))
        assertNull(CommitLanguages.parse("ZZ|Klingon"))
    }

    @Test
    fun `shuffling keeps all entries and the selected language`() {
        val selected = CommitLanguages.find("CN", "Chinese (Simplified)")!!
        val shuffled = CommitLanguages.shuffled(CommitLanguages.ALL, Random(42))

        assertEquals(CommitLanguages.ALL.size, shuffled.size)
        assertEquals(
            CommitLanguages.ALL.map { it.storageValue }.toSet(),
            shuffled.map { it.storageValue }.toSet()
        )
        assertNotEquals(
            CommitLanguages.ALL.map { it.storageValue },
            shuffled.map { it.storageValue }
        )
        assertEquals(selected, CommitLanguages.resolve(selected.storageValue, ""))
    }

    @Test
    fun `prompt name includes the native name only when different`() {
        assertEquals(
            "Chinese (Simplified) (简体中文)",
            CommitLanguages.promptName(CommitLanguages.find("CN", "Chinese (Simplified)")!!)
        )
        assertEquals(
            "Arabic (العربية)",
            CommitLanguages.promptName(CommitLanguages.find("SA", "Arabic")!!)
        )
        assertEquals(
            "English",
            CommitLanguages.promptName(CommitLanguages.DEFAULT)
        )
    }
}
