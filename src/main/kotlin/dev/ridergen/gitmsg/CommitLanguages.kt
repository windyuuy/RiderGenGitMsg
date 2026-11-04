package dev.ridergen.gitmsg

import java.util.Random

/**
 * One selectable commit-message language.
 *
 * [countryCode] is an ISO 3166-1 alpha-2 code. Region variants are separate entries,
 * e.g. CN vs TW, BR vs PT, US vs GB.
 */
data class CommitLanguage(
    val countryCode: String,
    val englishName: String,
    val nativeName: String
) {
    /** Persisted form, e.g. `US|English`. Field name in ridergen-gitmsg.xml: `commitLanguage`. */
    val storageValue: String
        get() = "$countryCode|$englishName"

    /** `CN · Chinese (Simplified) · 简体中文` */
    fun displayLabel(): String = "$countryCode · $englishName · $nativeName"
}

/**
 * Built-in language catalogue, search and legacy migration helpers.
 * Deliberately free of IntelliJ Platform dependencies so it can be unit-tested directly.
 */
object CommitLanguages {

    val DEFAULT: CommitLanguage = CommitLanguage("US", "English", "English")

    /**
     * Ordered by nothing in particular — the settings UI shuffles it on every open.
     * Every `countryCode + englishName` pair is unique.
     */
    val ALL: List<CommitLanguage> = listOf(
        // East Asia
        CommitLanguage("CN", "Chinese (Simplified)", "简体中文"),
        CommitLanguage("TW", "Chinese (Traditional)", "繁體中文"),
        CommitLanguage("HK", "Chinese (Traditional, Hong Kong)", "繁體中文（香港）"),
        CommitLanguage("SG", "Chinese (Simplified, Singapore)", "简体中文（新加坡）"),
        CommitLanguage("SG", "English (Singapore)", "English"),
        CommitLanguage("SG", "Malay (Singapore)", "Bahasa Melayu"),
        CommitLanguage("JP", "Japanese", "日本語"),
        CommitLanguage("KR", "Korean", "한국어"),
        CommitLanguage("KP", "Korean (North Korea)", "한국어"),
        CommitLanguage("MN", "Mongolian", "Монгол"),
        // Southeast Asia
        CommitLanguage("VN", "Vietnamese", "Tiếng Việt"),
        CommitLanguage("TH", "Thai", "ไทย"),
        CommitLanguage("LA", "Lao", "ລາວ"),
        CommitLanguage("KH", "Khmer", "ភាសាខ្មែរ"),
        CommitLanguage("MM", "Burmese", "မြန်မာ"),
        CommitLanguage("MY", "Malay", "Bahasa Melayu"),
        CommitLanguage("MY", "Chinese (Simplified, Malaysia)", "简体中文（马来西亚）"),
        CommitLanguage("MY", "Tamil (Malaysia)", "தமிழ்"),
        CommitLanguage("ID", "Indonesian", "Bahasa Indonesia"),
        CommitLanguage("ID", "Javanese", "Basa Jawa"),
        CommitLanguage("ID", "Sundanese", "Basa Sunda"),
        CommitLanguage("PH", "Filipino", "Filipino"),
        CommitLanguage("PH", "Cebuano", "Cebuano"),
        CommitLanguage("TL", "Tetum", "Tetun"),
        // South Asia
        CommitLanguage("IN", "Hindi", "हिन्दी"),
        CommitLanguage("IN", "Bengali (India)", "বাংলা"),
        CommitLanguage("IN", "Tamil", "தமிழ்"),
        CommitLanguage("IN", "Telugu", "తెలుగు"),
        CommitLanguage("IN", "Marathi", "मराठी"),
        CommitLanguage("IN", "Gujarati", "ગુજરાતી"),
        CommitLanguage("IN", "Kannada", "ಕನ್ನಡ"),
        CommitLanguage("IN", "Malayalam", "മലയാളം"),
        CommitLanguage("IN", "Punjabi (India)", "ਪੰਜਾਬੀ"),
        CommitLanguage("IN", "Urdu (India)", "اردو"),
        CommitLanguage("IN", "English (India)", "English"),
        CommitLanguage("PK", "Urdu", "اردو"),
        CommitLanguage("PK", "Punjabi (Pakistan)", "پنجابی"),
        CommitLanguage("PK", "Sindhi", "سنڌي"),
        CommitLanguage("PK", "Pashto (Pakistan)", "پښتو"),
        CommitLanguage("BD", "Bengali", "বাংলা"),
        CommitLanguage("LK", "Sinhala", "සිංහල"),
        CommitLanguage("LK", "Tamil (Sri Lanka)", "தமிழ்"),
        CommitLanguage("NP", "Nepali", "नेपाली"),
        CommitLanguage("AF", "Pashto", "پښتو"),
        CommitLanguage("AF", "Dari", "دری"),
        // Middle East & Central Asia
        CommitLanguage("IR", "Persian", "فارسی"),
        CommitLanguage("SA", "Arabic", "العربية"),
        CommitLanguage("AE", "Arabic (United Arab Emirates)", "العربية"),
        CommitLanguage("EG", "Arabic (Egypt)", "العربية (مصر)"),
        CommitLanguage("MA", "Arabic (Morocco)", "العربية (المغرب)"),
        CommitLanguage("DZ", "Arabic (Algeria)", "العربية (الجزائر)"),
        CommitLanguage("TN", "Arabic (Tunisia)", "العربية (تونس)"),
        CommitLanguage("LY", "Arabic (Libya)", "العربية (ليبيا)"),
        CommitLanguage("IQ", "Arabic (Iraq)", "العربية (العراق)"),
        CommitLanguage("IQ", "Kurdish (Sorani)", "کوردی"),
        CommitLanguage("JO", "Arabic (Jordan)", "العربية (الأردن)"),
        CommitLanguage("LB", "Arabic (Lebanon)", "العربية (لبنان)"),
        CommitLanguage("SY", "Arabic (Syria)", "العربية (سوريا)"),
        CommitLanguage("KW", "Arabic (Kuwait)", "العربية (الكويت)"),
        CommitLanguage("QA", "Arabic (Qatar)", "العربية (قطر)"),
        CommitLanguage("BH", "Arabic (Bahrain)", "العربية (البحرين)"),
        CommitLanguage("OM", "Arabic (Oman)", "العربية (عُمان)"),
        CommitLanguage("YE", "Arabic (Yemen)", "العربية (اليمن)"),
        CommitLanguage("SD", "Arabic (Sudan)", "العربية (السودان)"),
        CommitLanguage("PS", "Arabic (Palestine)", "العربية (فلسطين)"),
        CommitLanguage("IL", "Hebrew", "עברית"),
        CommitLanguage("IL", "Arabic (Israel)", "العربية"),
        CommitLanguage("TR", "Turkish", "Türkçe"),
        CommitLanguage("CY", "Turkish (Cyprus)", "Türkçe"),
        CommitLanguage("CY", "Greek (Cyprus)", "Ελληνικά"),
        CommitLanguage("GE", "Georgian", "ქართული"),
        CommitLanguage("AM", "Armenian", "Հայերեն"),
        CommitLanguage("AZ", "Azerbaijani", "Azərbaycanca"),
        CommitLanguage("KZ", "Kazakh", "Қазақша"),
        CommitLanguage("KZ", "Russian (Kazakhstan)", "Русский"),
        CommitLanguage("UZ", "Uzbek", "Oʻzbekcha"),
        CommitLanguage("KG", "Kyrgyz", "Кыргызча"),
        CommitLanguage("TJ", "Tajik", "Тоҷикӣ"),
        CommitLanguage("TM", "Turkmen", "Türkmençe"),
        // Europe
        CommitLanguage("US", "English", "English"),
        CommitLanguage("US", "Spanish (United States)", "Español"),
        CommitLanguage("US", "Hawaiian", "ʻŌlelo Hawaiʻi"),
        CommitLanguage("GB", "English (UK)", "English"),
        CommitLanguage("GB", "Welsh", "Cymraeg"),
        CommitLanguage("GB", "Scottish Gaelic", "Gàidhlig"),
        CommitLanguage("IE", "English (Ireland)", "English"),
        CommitLanguage("IE", "Irish", "Gaeilge"),
        CommitLanguage("CA", "English (Canada)", "English"),
        CommitLanguage("CA", "French (Canada)", "Français"),
        CommitLanguage("AU", "English (Australia)", "English"),
        CommitLanguage("NZ", "English (New Zealand)", "English"),
        CommitLanguage("NZ", "Maori", "Te Reo Māori"),
        CommitLanguage("DE", "German", "Deutsch"),
        CommitLanguage("AT", "German (Austria)", "Deutsch"),
        CommitLanguage("CH", "German (Switzerland)", "Deutsch"),
        CommitLanguage("CH", "French (Switzerland)", "Français"),
        CommitLanguage("CH", "Italian (Switzerland)", "Italiano"),
        CommitLanguage("CH", "Romansh", "Rumantsch"),
        CommitLanguage("FR", "French", "Français"),
        CommitLanguage("BE", "French (Belgium)", "Français"),
        CommitLanguage("BE", "Dutch (Belgium)", "Nederlands"),
        CommitLanguage("LU", "Luxembourgish", "Lëtzebuergesch"),
        CommitLanguage("NL", "Dutch", "Nederlands"),
        CommitLanguage("ES", "Spanish", "Español"),
        CommitLanguage("ES", "Catalan", "Català"),
        CommitLanguage("ES", "Galician", "Galego"),
        CommitLanguage("ES", "Basque", "Euskara"),
        CommitLanguage("AD", "Catalan (Andorra)", "Català"),
        CommitLanguage("PT", "Portuguese (Portugal)", "Português (Portugal)"),
        CommitLanguage("BR", "Portuguese (Brazil)", "Português (Brasil)"),
        CommitLanguage("AO", "Portuguese (Angola)", "Português"),
        CommitLanguage("MZ", "Portuguese (Mozambique)", "Português"),
        CommitLanguage("CV", "Portuguese (Cape Verde)", "Português"),
        CommitLanguage("IT", "Italian", "Italiano"),
        CommitLanguage("MT", "Maltese", "Malti"),
        CommitLanguage("GR", "Greek", "Ελληνικά"),
        CommitLanguage("SE", "Swedish", "Svenska"),
        CommitLanguage("NO", "Norwegian Bokmål", "Norsk bokmål"),
        CommitLanguage("NO", "Norwegian Nynorsk", "Norsk nynorsk"),
        CommitLanguage("DK", "Danish", "Dansk"),
        CommitLanguage("FI", "Finnish", "Suomi"),
        CommitLanguage("FI", "Swedish (Finland)", "Svenska"),
        CommitLanguage("IS", "Icelandic", "Íslenska"),
        CommitLanguage("FO", "Faroese", "Føroyskt"),
        CommitLanguage("GL", "Greenlandic", "Kalaallisut"),
        CommitLanguage("EE", "Estonian", "Eesti"),
        CommitLanguage("LV", "Latvian", "Latviešu"),
        CommitLanguage("LT", "Lithuanian", "Lietuvių"),
        CommitLanguage("PL", "Polish", "Polski"),
        CommitLanguage("CZ", "Czech", "Čeština"),
        CommitLanguage("SK", "Slovak", "Slovenčina"),
        CommitLanguage("HU", "Hungarian", "Magyar"),
        CommitLanguage("RO", "Romanian", "Română"),
        CommitLanguage("MD", "Romanian (Moldova)", "Română"),
        CommitLanguage("BG", "Bulgarian", "Български"),
        CommitLanguage("RS", "Serbian", "Српски"),
        CommitLanguage("HR", "Croatian", "Hrvatski"),
        CommitLanguage("SI", "Slovenian", "Slovenščina"),
        CommitLanguage("BA", "Bosnian", "Bosanski"),
        CommitLanguage("MK", "Macedonian", "Македонски"),
        CommitLanguage("AL", "Albanian", "Shqip"),
        CommitLanguage("UA", "Ukrainian", "Українська"),
        CommitLanguage("BY", "Belarusian", "Беларуская"),
        CommitLanguage("RU", "Russian", "Русский"),
        // Latin America & Caribbean
        CommitLanguage("MX", "Spanish (Mexico)", "Español (México)"),
        CommitLanguage("GT", "Spanish (Guatemala)", "Español (Guatemala)"),
        CommitLanguage("HN", "Spanish (Honduras)", "Español (Honduras)"),
        CommitLanguage("SV", "Spanish (El Salvador)", "Español (El Salvador)"),
        CommitLanguage("NI", "Spanish (Nicaragua)", "Español (Nicaragua)"),
        CommitLanguage("CR", "Spanish (Costa Rica)", "Español (Costa Rica)"),
        CommitLanguage("PA", "Spanish (Panama)", "Español (Panamá)"),
        CommitLanguage("DO", "Spanish (Dominican Republic)", "Español (República Dominicana)"),
        CommitLanguage("CU", "Spanish (Cuba)", "Español (Cuba)"),
        CommitLanguage("PR", "Spanish (Puerto Rico)", "Español (Puerto Rico)"),
        CommitLanguage("CO", "Spanish (Colombia)", "Español (Colombia)"),
        CommitLanguage("VE", "Spanish (Venezuela)", "Español (Venezuela)"),
        CommitLanguage("EC", "Spanish (Ecuador)", "Español (Ecuador)"),
        CommitLanguage("PE", "Spanish (Peru)", "Español (Perú)"),
        CommitLanguage("BO", "Spanish (Bolivia)", "Español (Bolivia)"),
        CommitLanguage("PY", "Spanish (Paraguay)", "Español (Paraguay)"),
        CommitLanguage("UY", "Spanish (Uruguay)", "Español (Uruguay)"),
        CommitLanguage("AR", "Spanish (Argentina)", "Español (Argentina)"),
        CommitLanguage("CL", "Spanish (Chile)", "Español (Chile)"),
        CommitLanguage("HT", "Haitian Creole", "Kreyòl ayisyen"),
        CommitLanguage("JM", "Jamaican Patois", "Patwa"),
        CommitLanguage("TT", "English (Trinidad and Tobago)", "English"),
        // Africa
        CommitLanguage("NG", "English (Nigeria)", "English"),
        CommitLanguage("NG", "Hausa", "Hausa"),
        CommitLanguage("NG", "Yoruba", "Yorùbá"),
        CommitLanguage("NG", "Igbo", "Igbo"),
        CommitLanguage("GH", "English (Ghana)", "English"),
        CommitLanguage("GH", "Twi", "Twi"),
        CommitLanguage("KE", "Swahili", "Kiswahili"),
        CommitLanguage("KE", "English (Kenya)", "English"),
        CommitLanguage("TZ", "Swahili (Tanzania)", "Kiswahili"),
        CommitLanguage("UG", "Luganda", "Luganda"),
        CommitLanguage("RW", "Kinyarwanda", "Ikinyarwanda"),
        CommitLanguage("BI", "Kirundi", "Ikirundi"),
        CommitLanguage("ET", "Amharic", "አማርኛ"),
        CommitLanguage("ET", "Oromo", "Afaan Oromoo"),
        CommitLanguage("ER", "Tigrinya", "ትግርኛ"),
        CommitLanguage("SO", "Somali", "Soomaali"),
        CommitLanguage("DJ", "Somali (Djibouti)", "Soomaali"),
        CommitLanguage("ZA", "English (South Africa)", "English"),
        CommitLanguage("ZA", "Afrikaans", "Afrikaans"),
        CommitLanguage("ZA", "Zulu", "isiZulu"),
        CommitLanguage("ZA", "Xhosa", "isiXhosa"),
        CommitLanguage("NA", "Afrikaans (Namibia)", "Afrikaans"),
        CommitLanguage("BW", "Tswana", "Setswana"),
        CommitLanguage("ZW", "Shona", "chiShona"),
        CommitLanguage("ZM", "Bemba", "Ichibemba"),
        CommitLanguage("MW", "Chichewa", "Chichewa"),
        CommitLanguage("MG", "Malagasy", "Malagasy"),
        CommitLanguage("MU", "Mauritian Creole", "Kreol Morisien"),
        CommitLanguage("SC", "Seychellois Creole", "Kreol Seselwa"),
        CommitLanguage("CI", "French (Côte d'Ivoire)", "Français"),
        CommitLanguage("SN", "French (Senegal)", "Français"),
        CommitLanguage("CM", "French (Cameroon)", "Français"),
        CommitLanguage("CD", "French (DR Congo)", "Français"),
        CommitLanguage("ML", "Bambara", "Bamanankan"),
        CommitLanguage("BF", "Mooré", "Mooré"),
        CommitLanguage("NE", "Hausa (Niger)", "Hausa"),
        CommitLanguage("TD", "Arabic (Chad)", "العربية"),
        CommitLanguage("GM", "Mandinka", "Mandinka"),
        CommitLanguage("GN", "French (Guinea)", "Français"),
        CommitLanguage("SL", "Krio", "Krio"),
        // Oceania
        CommitLanguage("WS", "Samoan", "Gagana Sāmoa"),
        CommitLanguage("TO", "Tongan", "Lea Fakatonga"),
        CommitLanguage("FJ", "Fijian", "Na Vosa Vakaviti"),
        CommitLanguage("PG", "Tok Pisin", "Tok Pisin"),
        CommitLanguage("VU", "Bislama", "Bislama"),
        CommitLanguage("PF", "French (French Polynesia)", "Français"),
        CommitLanguage("NC", "French (New Caledonia)", "Français")
    )

    fun find(countryCode: String, englishName: String): CommitLanguage? =
        ALL.firstOrNull {
            it.countryCode.equals(countryCode, ignoreCase = true) &&
                it.englishName.equals(englishName, ignoreCase = true)
        }

    /** Parses the persisted `US|English` form. Returns null for blanks or unknown values. */
    fun parse(storageValue: String): CommitLanguage? {
        val trimmed = storageValue.trim()
        if (trimmed.isEmpty()) return null
        val separator = trimmed.indexOf('|')
        if (separator <= 0) return null
        return find(trimmed.substring(0, separator), trimmed.substring(separator + 1))
    }

    /**
     * 0.2–0.5 stored only `responseLanguage` (`zh` / `en`).
     * Migration: `zh` → CN · Chinese (Simplified), `en` → US · English.
     * A blank legacy value (fresh install) resolves to [DEFAULT].
     */
    fun fromLegacy(responseLanguage: String): CommitLanguage = when (responseLanguage.trim().lowercase()) {
        "zh" -> find("CN", "Chinese (Simplified)") ?: DEFAULT
        "en" -> find("US", "English") ?: DEFAULT
        else -> DEFAULT
    }

    /** Prefers the new field; falls back to the legacy `zh`/`en` value; otherwise [DEFAULT]. */
    fun resolve(commitLanguage: String, responseLanguage: String): CommitLanguage =
        parse(commitLanguage) ?: fromLegacy(responseLanguage)

    /** Substring match on country code, English name or native name; case-insensitive. */
    fun search(languages: List<CommitLanguage>, query: String): List<CommitLanguage> {
        val needle = query.trim()
        if (needle.isEmpty()) return languages
        return languages.filter {
            it.countryCode.contains(needle, ignoreCase = true) ||
                it.englishName.contains(needle, ignoreCase = true) ||
                it.nativeName.contains(needle, ignoreCase = true)
        }
    }

    /** Shuffled copy; the original order is never used by the settings UI directly. */
    fun shuffled(languages: List<CommitLanguage> = ALL, random: Random = Random()): List<CommitLanguage> =
        languages.shuffled(random)

    /** `English`, or `Chinese (Simplified) (简体中文)` when the names differ. */
    fun promptName(language: CommitLanguage): String =
        if (language.englishName == language.nativeName) {
            language.englishName
        } else {
            "${language.englishName} (${language.nativeName})"
        }
}
