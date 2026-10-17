package com.vikash.mp3player.data

/**
 * Lets "tum hi ho" find "तुम ही हो" and "कल हो ना हो" find "Kal Ho Naa Ho".
 *
 * Both sides are turned into a rough sound key: Devanagari is spelled out in Latin letters, then
 * the spellings people mix up in Hinglish are folded together (aa/a, ee/i, oo/u, w/v, z/j, f/ph...).
 * The letter "a" is dropped completely, which side-steps Hindi's silent inherent vowel ("tera"
 * and "तेरा" both become "ter"). It is deliberately loose: a search box, not a dictionary.
 */
object HinglishSearch {

    /** True when [query] matches [text] directly or by sound key. [query] must already be a key from [key]. */
    fun matches(text: String, query: String, queryKey: String): Boolean =
        text.contains(query, ignoreCase = true) || (queryKey.length >= 2 && key(text).contains(queryKey))

    fun key(s: String): String {
        val latin = StringBuilder(s.length + 8)
        for (c in s.lowercase()) {
            val mapped = DEVANAGARI[c]
            when {
                mapped != null -> latin.append(mapped)
                c in 'a'..'z' || c in '0'..'9' -> latin.append(c)
                c == NUKTA || c == VIRAMA -> Unit
                else -> latin.append(' ')
            }
        }
        var k = latin.toString()
        for ((from, to) in FOLDS) k = k.replace(from, to)
        k = k.replace("a", "")
        // Collapse runs of the same letter ("dill" ~ "dil") and of spaces.
        val out = StringBuilder(k.length)
        for (c in k) if (out.isEmpty() || out.last() != c) out.append(c)
        return out.toString().trim()
    }

    private const val NUKTA = '़'
    private const val VIRAMA = '्'

    /** Longest patterns first, so "chh" folds before "ch". */
    private val FOLDS = listOf(
        "chh" to "ch", "ph" to "f", "ee" to "i", "ii" to "i", "oo" to "u", "uu" to "u",
        "ai" to "i", "au" to "u", "ou" to "u", "w" to "v", "z" to "j", "q" to "k", "y" to "i",
    )

    private val DEVANAGARI: Map<Char, String> = mapOf(
        // Vowels and vowel signs (the inherent "a" is never written, so nothing to drop for it).
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "i", 'ओ' to "o", 'औ' to "u", 'ऑ' to "o",
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'ृ' to "ri",
        'े' to "e", 'ै' to "i", 'ो' to "o", 'ौ' to "u", 'ॉ' to "o",
        'ं' to "n", 'ँ' to "n", 'ः' to "h",
        // Consonants.
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "ch", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u095C' to "r", '\u095D' to "rh", '\u0958' to "k", '\u0959' to "kh", '\u095A' to "g", '\u095B' to "j", '\u095E' to "f",
        '०' to "0", '१' to "1", '२' to "2", '३' to "3", '४' to "4", '५' to "5", '६' to "6", '७' to "7", '८' to "8", '९' to "9",
    )
}
