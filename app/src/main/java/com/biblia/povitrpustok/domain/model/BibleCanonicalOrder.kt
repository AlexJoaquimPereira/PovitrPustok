package com.biblia.povitrpustok.domain.model

/**
 * Explicit canonical order of the 73-book Catholic Bible canon
 * (corrective phase, Issue 2).
 *
 * Keyed by the actual corpus ids (BibleCatalog) — never by display names,
 * English names, or filenames. This mapping is the single source of truth
 * for both ordering and testament grouping, independent of the corpus's
 * (sometimes incorrect) testament metadata — e.g. tesalonikarank1/2 are
 * canonically New Testament. The raw corpus metadata remains untouched for
 * database consumption.
 */
object BibleCanonicalOrder {

    val OLD_TESTAMENT_IDS: List<String> = listOf(
        "Utpoti", // Genesis
        "Suttka", // Exodus
        "Levi_Xastr", // Leviticus
        "Lokgonnti", // Numbers
        "Dusri_Somurt", // Deuteronomy
        "Joshuachem_Pustok", // Joshua
        "Fuddareanchem_Pustok", // Judges
        "Ruthachem_Pustok", // Ruth
        "Samuelachem_Poilem_Pustok", // 1 Samuel
        "Samuelachem_Dusrem_Pustok", // 2 Samuel
        "Razanchem_Poilem_Pustok", // 1 Kings
        "Razanchem_Dusrem_Pustok", // 2 Kings
        "Itihasachem_Poilem_Pustok", // 1 Chronicles
        "Itihasachem_Dusrem_Pustok", // 2 Chronicles
        "Ezrachem_Pustok", // Ezra
        "Nehemiasachem_Pustok", // Nehemiah
        "Tobit", // Tobit
        "Judit", // Judith
        "Esther", // Esther
        "Makabeusachem_Poilem_Pustok", // 1 Maccabees
        "Makabeusachem_Dusrem_Pustok", // 2 Maccabees
        "Jobachem_Pustok", // Job
        "Stotranchem_Pustok", // Psalms
        "Mhonnneo", // Proverbs
        "Upodexok", // Ecclesiastes (Qoheleth)
        "Gitantlem_Git", // Song of Songs
        "Zannvaiechem_Pustok", // Wisdom
        "Sirakachem_Pustok", // Sirach
        "Izaias", // Isaiah
        "Jeremias", // Jeremiah
        "Vilap", // Lamentations
        "Barukachem_Pustak", // Baruch
        "Ezekiel", // Ezekiel
        "Daniel", // Daniel
        "Hosea", // Hosea
        "Joel", // Joel
        "Amos", // Amos
        "Obadia", // Obadiah
        "Jonas", // Jonah
        "Mika", // Micah
        "Nahum", // Nahum
        "Habakuk", // Habakkuk
        "Zefania", // Zephaniah
        "Haggai", // Haggai
        "Zekaria", // Zechariah
        "Malaki" // Malachi
    )

    val NEW_TESTAMENT_IDS: List<String> = listOf(
        "matev", // Matthew
        "mark", // Mark
        "lukas", // Luke
        "juanv", // John
        "dhormdutancho_itihas", // Acts
        "romkarank", // Romans
        "korintkarant1", // 1 Corinthians
        "korintkarant2", // 2 Corinthians
        "galasiekarank", // Galatians
        "efezkarank", // Ephesians
        "filipkarank", // Philippians
        "koloskarank", // Colossians
        "tesalonikarank1", // 1 Thessalonians
        "tesalonikarank2", // 2 Thessalonians
        "timotak1", // 1 Timothy
        "timotak2", // 2 Timothy
        "tilak", // Titus
        "filemonak", // Philemon
        "hebrevank", // Hebrews
        "jakob", // James
        "pedru1", // 1 Peter
        "pedru2", // 2 Peter
        "juanv1", // 1 John
        "juanv2", // 2 John
        "juanv3", // 3 John
        "judas", // Jude
        "juanvak_prokasovnni" // Revelation
    )

    val CANONICAL_IDS: List<String> = OLD_TESTAMENT_IDS + NEW_TESTAMENT_IDS

    private val ID_TO_POSITION: Map<String, Int> =
        CANONICAL_IDS.withIndex().associate { (index, id) -> id to index }

    /** Canonical position (0-based) of a book id, or null if not canonical. */
    fun position(bookId: String): Int? = ID_TO_POSITION[bookId]

    /** Canonical testament of a book id, or null if not canonical. */
    fun testament(bookId: String): Testament? = when {
        bookId in OLD_TESTAMENT_IDS -> Testament.OLD_TESTAMENT
        bookId in NEW_TESTAMENT_IDS -> Testament.NEW_TESTAMENT
        else -> null
    }
}
