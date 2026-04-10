package com.cmscure.sdk

/**
 * Describes whether a language is rendered left-to-right or right-to-left.
 * Mirrors the iOS SDK's `LanguageDirection` enum.
 */
enum class LanguageDirection {
    LTR,
    RTL;

    /** `true` when the direction is [RTL]. */
    val isRTL: Boolean get() = this == RTL

    companion object {
        private val rtlLanguages = setOf(
            "ar", "he", "fa", "ur", "ps", "sd", "ku", "yi", "ckb", "dv", "ug"
        )

        /** Returns the layout direction for the given BCP-47 language code. */
        fun direction(languageCode: String): LanguageDirection {
            val base = languageCode.lowercase().split("-", "_").firstOrNull() ?: languageCode.lowercase()
            return if (base in rtlLanguages) RTL else LTR
        }
    }
}

/**
 * Provides flag emojis and human-readable display names for language codes.
 * Mirrors the iOS SDK's `CureLanguageDisplay` helper.
 */
object CureLanguageDisplay {

    private val flags = mapOf(
        "af" to "🇿🇦", "am" to "🇪🇹", "ar" to "🇸🇦", "az" to "🇦🇿",
        "be" to "🇧🇾", "bg" to "🇧🇬", "bn" to "🇧🇩", "bs" to "🇧🇦",
        "ca" to "🇪🇸", "cs" to "🇨🇿", "cy" to "🏴\u200D☠️", "da" to "🇩🇰",
        "de" to "🇩🇪", "el" to "🇬🇷", "en" to "🇺🇸", "es" to "🇪🇸",
        "et" to "🇪🇪", "eu" to "🇪🇸", "fa" to "🇮🇷", "fi" to "🇫🇮",
        "fil" to "🇵🇭", "fr" to "🇫🇷", "ga" to "🇮🇪", "gl" to "🇪🇸",
        "gu" to "🇮🇳", "ha" to "🇳🇬", "he" to "🇮🇱", "hi" to "🇮🇳",
        "hr" to "🇭🇷", "hu" to "🇭🇺", "hy" to "🇦🇲", "id" to "🇮🇩",
        "ig" to "🇳🇬", "is" to "🇮🇸", "it" to "🇮🇹", "ja" to "🇯🇵",
        "jv" to "🇮🇩", "ka" to "🇬🇪", "kk" to "🇰🇿", "km" to "🇰🇭",
        "kn" to "🇮🇳", "ko" to "🇰🇷", "ku" to "🇮🇶", "ky" to "🇰🇬",
        "lo" to "🇱🇦", "lt" to "🇱🇹", "lv" to "🇱🇻", "mg" to "🇲🇬",
        "mk" to "🇲🇰", "ml" to "🇮🇳", "mn" to "🇲🇳", "mr" to "🇮🇳",
        "ms" to "🇲🇾", "mt" to "🇲🇹", "my" to "🇲🇲", "nb" to "🇳🇴",
        "ne" to "🇳🇵", "nl" to "🇳🇱", "nn" to "🇳🇴", "no" to "🇳🇴",
        "or" to "🇮🇳", "pa" to "🇮🇳", "pl" to "🇵🇱", "ps" to "🇦🇫",
        "pt" to "🇵🇹", "ro" to "🇷🇴", "ru" to "🇷🇺", "rw" to "🇷🇼",
        "sd" to "🇵🇰", "si" to "🇱🇰", "sk" to "🇸🇰", "sl" to "🇸🇮",
        "so" to "🇸🇴", "sq" to "🇦🇱", "sr" to "🇷🇸", "sv" to "🇸🇪",
        "sw" to "🇹🇿", "ta" to "🇮🇳", "te" to "🇮🇳", "tg" to "🇹🇯",
        "th" to "🇹🇭", "tk" to "🇹🇲", "tl" to "🇵🇭", "tr" to "🇹🇷",
        "uk" to "🇺🇦", "ur" to "🇵🇰", "uz" to "🇺🇿", "vi" to "🇻🇳",
        "xh" to "🇿🇦", "yi" to "🇮🇱", "yo" to "🇳🇬", "zh" to "🇨🇳",
        "zu" to "🇿🇦",
        // Regional variants
        "en-gb" to "🇬🇧", "en-au" to "🇦🇺", "en-ca" to "🇨🇦", "en-in" to "🇮🇳",
        "es-mx" to "🇲🇽", "es-ar" to "🇦🇷", "fr-ca" to "🇨🇦", "fr-be" to "🇧🇪",
        "pt-br" to "🇧🇷", "zh-tw" to "🇹🇼", "zh-hk" to "🇭🇰"
    )

    private val displayNames = mapOf(
        "af" to "Afrikaans", "am" to "Amharic", "ar" to "Arabic", "az" to "Azerbaijani",
        "be" to "Belarusian", "bg" to "Bulgarian", "bn" to "Bengali", "bs" to "Bosnian",
        "ca" to "Catalan", "cs" to "Czech", "cy" to "Welsh", "da" to "Danish",
        "de" to "German", "el" to "Greek", "en" to "English", "es" to "Spanish",
        "et" to "Estonian", "eu" to "Basque", "fa" to "Persian", "fi" to "Finnish",
        "fil" to "Filipino", "fr" to "French", "ga" to "Irish", "gl" to "Galician",
        "gu" to "Gujarati", "ha" to "Hausa", "he" to "Hebrew", "hi" to "Hindi",
        "hr" to "Croatian", "hu" to "Hungarian", "hy" to "Armenian", "id" to "Indonesian",
        "ig" to "Igbo", "is" to "Icelandic", "it" to "Italian", "ja" to "Japanese",
        "jv" to "Javanese", "ka" to "Georgian", "kk" to "Kazakh", "km" to "Khmer",
        "kn" to "Kannada", "ko" to "Korean", "ku" to "Kurdish", "ky" to "Kyrgyz",
        "lo" to "Lao", "lt" to "Lithuanian", "lv" to "Latvian", "mg" to "Malagasy",
        "mk" to "Macedonian", "ml" to "Malayalam", "mn" to "Mongolian", "mr" to "Marathi",
        "ms" to "Malay", "mt" to "Maltese", "my" to "Burmese", "nb" to "Norwegian Bokmål",
        "ne" to "Nepali", "nl" to "Dutch", "nn" to "Norwegian Nynorsk", "no" to "Norwegian",
        "or" to "Odia", "pa" to "Punjabi", "pl" to "Polish", "ps" to "Pashto",
        "pt" to "Portuguese", "ro" to "Romanian", "ru" to "Russian", "rw" to "Kinyarwanda",
        "sd" to "Sindhi", "si" to "Sinhala", "sk" to "Slovak", "sl" to "Slovenian",
        "so" to "Somali", "sq" to "Albanian", "sr" to "Serbian", "sv" to "Swedish",
        "sw" to "Swahili", "ta" to "Tamil", "te" to "Telugu", "tg" to "Tajik",
        "th" to "Thai", "tk" to "Turkmen", "tl" to "Tagalog", "tr" to "Turkish",
        "uk" to "Ukrainian", "ur" to "Urdu", "uz" to "Uzbek", "vi" to "Vietnamese",
        "xh" to "Xhosa", "yi" to "Yiddish", "yo" to "Yoruba", "zh" to "Chinese",
        "zu" to "Zulu",
        // Regional variants
        "en-gb" to "English (UK)", "en-au" to "English (Australia)", "en-ca" to "English (Canada)",
        "en-in" to "English (India)", "es-mx" to "Spanish (Mexico)", "es-ar" to "Spanish (Argentina)",
        "fr-ca" to "French (Canada)", "fr-be" to "French (Belgium)", "pt-br" to "Portuguese (Brazil)",
        "zh-tw" to "Chinese (Traditional)", "zh-hk" to "Chinese (Hong Kong)"
    )

    /** Returns the flag emoji for the given language code, or a globe emoji if unknown. */
    fun flag(code: String): String {
        val lower = code.lowercase()
        return flags[lower] ?: flags[lower.split("-", "_").firstOrNull() ?: lower] ?: "🌐"
    }

    /** Returns a human-readable display name for the given language code. */
    fun displayName(code: String): String {
        val lower = code.lowercase()
        return displayNames[lower] ?: displayNames[lower.split("-", "_").firstOrNull() ?: lower] ?: code.uppercase()
    }
}
