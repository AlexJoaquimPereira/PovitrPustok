package com.biblia.povitrpustok.domain.model

/**
 * Canonical static catalog of all Bible books (implementation plan §12).
 *
 * Derived from the actual per-book SQLite corpus (meta/books rows) during
 * Phase 2; ids and databaseAsset values match the real asset filenames in
 * assets/bible/. Rendering this catalog never opens a database.
 */
object BibleCatalog {

    val books: List<BibleBook> = listOf(
        BibleBook(id = "Amos", displayName = "Amos", englishName = "Amos", testament = Testament.OLD_TESTAMENT, position = 1, databaseAsset = "bible/Amos.sqlite"),
        BibleBook(id = "Barukachem_Pustak", displayName = "Barukachem Pustak", englishName = "Baruch", testament = Testament.OLD_TESTAMENT, position = 2, databaseAsset = "bible/Barukachem_Pustak.sqlite"),
        BibleBook(id = "Daniel", displayName = "Daniel", englishName = "Daniel", testament = Testament.OLD_TESTAMENT, position = 3, databaseAsset = "bible/Daniel.sqlite"),
        BibleBook(id = "Dusri_Somurt", displayName = "Dusri Somurt", englishName = "Deuteronomy", testament = Testament.OLD_TESTAMENT, position = 4, databaseAsset = "bible/Dusri_Somurt.sqlite"),
        BibleBook(id = "Esther", displayName = "Esther", englishName = "Esther", testament = Testament.OLD_TESTAMENT, position = 5, databaseAsset = "bible/Esther.sqlite"),
        BibleBook(id = "Ezekiel", displayName = "Ezekiel", englishName = "Ezekiel", testament = Testament.OLD_TESTAMENT, position = 6, databaseAsset = "bible/Ezekiel.sqlite"),
        BibleBook(id = "Ezrachem_Pustok", displayName = "Ezrachem Pustok", englishName = "Ezra", testament = Testament.OLD_TESTAMENT, position = 7, databaseAsset = "bible/Ezrachem_Pustok.sqlite"),
        BibleBook(id = "Fuddareanchem_Pustok", displayName = "Fuddareañachem Pustok", englishName = "Judges", testament = Testament.OLD_TESTAMENT, position = 8, databaseAsset = "bible/Fuddareanchem_Pustok.sqlite"),
        BibleBook(id = "Gitantlem_Git", displayName = "Gitantlem Git", englishName = "Song of Songs", testament = Testament.OLD_TESTAMENT, position = 9, databaseAsset = "bible/Gitantlem_Git.sqlite"),
        BibleBook(id = "Habakuk", displayName = "Habakuk", englishName = "Habakkuk", testament = Testament.OLD_TESTAMENT, position = 10, databaseAsset = "bible/Habakuk.sqlite"),
        BibleBook(id = "Haggai", displayName = "Haggai", englishName = "Haggai", testament = Testament.OLD_TESTAMENT, position = 11, databaseAsset = "bible/Haggai.sqlite"),
        BibleBook(id = "Hosea", displayName = "Hosea", englishName = "Hosea", testament = Testament.OLD_TESTAMENT, position = 12, databaseAsset = "bible/Hosea.sqlite"),
        BibleBook(id = "Itihasachem_Dusrem_Pustok", displayName = "Itihasachem Dusrem Pustok", englishName = "2 Chronicles", testament = Testament.OLD_TESTAMENT, position = 13, databaseAsset = "bible/Itihasachem_Dusrem_Pustok.sqlite"),
        BibleBook(id = "Itihasachem_Poilem_Pustok", displayName = "Itihasachem Poilem Pustok", englishName = "1 Chronicles", testament = Testament.OLD_TESTAMENT, position = 14, databaseAsset = "bible/Itihasachem_Poilem_Pustok.sqlite"),
        BibleBook(id = "Izaias", displayName = "Izaias", englishName = "Isaiah", testament = Testament.OLD_TESTAMENT, position = 15, databaseAsset = "bible/Izaias.sqlite"),
        BibleBook(id = "Jeremias", displayName = "Jeremias", englishName = "Jeremiah", testament = Testament.OLD_TESTAMENT, position = 16, databaseAsset = "bible/Jeremias.sqlite"),
        BibleBook(id = "Jobachem_Pustok", displayName = "Jobachem Pustok", englishName = "Job", testament = Testament.OLD_TESTAMENT, position = 17, databaseAsset = "bible/Jobachem_Pustok.sqlite"),
        BibleBook(id = "Joel", displayName = "Joel", englishName = "Joel", testament = Testament.OLD_TESTAMENT, position = 18, databaseAsset = "bible/Joel.sqlite"),
        BibleBook(id = "Jonas", displayName = "Jonas", englishName = "Jonah", testament = Testament.OLD_TESTAMENT, position = 19, databaseAsset = "bible/Jonas.sqlite"),
        BibleBook(id = "Joshuachem_Pustok", displayName = "Joshuachem Pustok", englishName = "Joshua", testament = Testament.OLD_TESTAMENT, position = 20, databaseAsset = "bible/Joshuachem_Pustok.sqlite"),
        BibleBook(id = "Judit", displayName = "Judit", englishName = "Judith", testament = Testament.OLD_TESTAMENT, position = 21, databaseAsset = "bible/Judit.sqlite"),
        BibleBook(id = "Levi_Xastr", displayName = "Levi Xastr", englishName = "Leviticus", testament = Testament.OLD_TESTAMENT, position = 22, databaseAsset = "bible/Levi_Xastr.sqlite"),
        BibleBook(id = "Lokgonnti", displayName = "Lokgonnti", englishName = "Numbers", testament = Testament.OLD_TESTAMENT, position = 23, databaseAsset = "bible/Lokgonnti.sqlite"),
        BibleBook(id = "Makabeusachem_Dusrem_Pustok", displayName = "Makabeusachem Dusrem Pustok", englishName = "2 Maccabees", testament = Testament.OLD_TESTAMENT, position = 24, databaseAsset = "bible/Makabeusachem_Dusrem_Pustok.sqlite"),
        BibleBook(id = "Makabeusachem_Poilem_Pustok", displayName = "Makabeusachem Poilem Pustok", englishName = "1 Maccabees", testament = Testament.OLD_TESTAMENT, position = 25, databaseAsset = "bible/Makabeusachem_Poilem_Pustok.sqlite"),
        BibleBook(id = "Malaki", displayName = "Malaki", englishName = "Malachi", testament = Testament.OLD_TESTAMENT, position = 26, databaseAsset = "bible/Malaki.sqlite"),
        BibleBook(id = "Mhonnneo", displayName = "Mhonnneo", englishName = "Proverbs", testament = Testament.OLD_TESTAMENT, position = 27, databaseAsset = "bible/Mhonnneo.sqlite"),
        BibleBook(id = "Mika", displayName = "Mika", englishName = "Micah", testament = Testament.OLD_TESTAMENT, position = 28, databaseAsset = "bible/Mika.sqlite"),
        BibleBook(id = "Nahum", displayName = "Nahum", englishName = "Nahum", testament = Testament.OLD_TESTAMENT, position = 29, databaseAsset = "bible/Nahum.sqlite"),
        BibleBook(id = "Nehemiasachem_Pustok", displayName = "Nehemiasachem Pustok", englishName = "Nehemiah", testament = Testament.OLD_TESTAMENT, position = 30, databaseAsset = "bible/Nehemiasachem_Pustok.sqlite"),
        BibleBook(id = "Obadia", displayName = "Obadia", englishName = "Obadiah", testament = Testament.OLD_TESTAMENT, position = 31, databaseAsset = "bible/Obadia.sqlite"),
        BibleBook(id = "Razanchem_Dusrem_Pustok", displayName = "Razanchem Dusrem Pustok", englishName = "2 Kings", testament = Testament.OLD_TESTAMENT, position = 32, databaseAsset = "bible/Razanchem_Dusrem_Pustok.sqlite"),
        BibleBook(id = "Razanchem_Poilem_Pustok", displayName = "Razanchem Poilem Pustok", englishName = "1 Kings", testament = Testament.OLD_TESTAMENT, position = 33, databaseAsset = "bible/Razanchem_Poilem_Pustok.sqlite"),
        BibleBook(id = "Ruthachem_Pustok", displayName = "Ruthachem Pustok", englishName = "Ruth", testament = Testament.OLD_TESTAMENT, position = 34, databaseAsset = "bible/Ruthachem_Pustok.sqlite"),
        BibleBook(id = "Samuelachem_Dusrem_Pustok", displayName = "Samuelachem Dusrem Pustok", englishName = "2 Samuel", testament = Testament.OLD_TESTAMENT, position = 35, databaseAsset = "bible/Samuelachem_Dusrem_Pustok.sqlite"),
        BibleBook(id = "Samuelachem_Poilem_Pustok", displayName = "Samuelachem Poilem Pustok", englishName = "1 Samuel", testament = Testament.OLD_TESTAMENT, position = 36, databaseAsset = "bible/Samuelachem_Poilem_Pustok.sqlite"),
        BibleBook(id = "Sirakachem_Pustok", displayName = "Sirakachem Pustok", englishName = "Sirach", testament = Testament.OLD_TESTAMENT, position = 37, databaseAsset = "bible/Sirakachem_Pustok.sqlite"),
        BibleBook(id = "Stotranchem_Pustok", displayName = "Stotranchem Pustok", englishName = "Psalms", testament = Testament.OLD_TESTAMENT, position = 38, databaseAsset = "bible/Stotranchem_Pustok.sqlite"),
        BibleBook(id = "Suttka", displayName = "Suttka", englishName = "Exodus", testament = Testament.OLD_TESTAMENT, position = 39, databaseAsset = "bible/Suttka.sqlite"),
        BibleBook(id = "Tobit", displayName = "Tobit", englishName = "Tobit", testament = Testament.OLD_TESTAMENT, position = 40, databaseAsset = "bible/Tobit.sqlite"),
        BibleBook(id = "Upodexok", displayName = "Upodexok", englishName = "Qoheleth", testament = Testament.OLD_TESTAMENT, position = 41, databaseAsset = "bible/Upodexok.sqlite"),
        BibleBook(id = "Utpoti", displayName = "Utpoti", englishName = "Genesis", testament = Testament.OLD_TESTAMENT, position = 42, databaseAsset = "bible/Utpoti.sqlite"),
        BibleBook(id = "Vilap", displayName = "Vilap", englishName = "Lamentations", testament = Testament.OLD_TESTAMENT, position = 43, databaseAsset = "bible/Vilap.sqlite"),
        BibleBook(id = "Zannvaiechem_Pustok", displayName = "Zannvaiechem Pustok", englishName = "Wisdom", testament = Testament.OLD_TESTAMENT, position = 44, databaseAsset = "bible/Zannvaiechem_Pustok.sqlite"),
        BibleBook(id = "Zefania", displayName = "Zefania", englishName = "Zephaniah", testament = Testament.OLD_TESTAMENT, position = 45, databaseAsset = "bible/Zefania.sqlite"),
        BibleBook(id = "Zekaria", displayName = "Zekaria", englishName = "Zechariah", testament = Testament.OLD_TESTAMENT, position = 46, databaseAsset = "bible/Zekaria.sqlite"),
        BibleBook(id = "dhormdutancho_itihas", displayName = "Dhormdutancho Itihas", englishName = "Acts", testament = Testament.NEW_TESTAMENT, position = 47, databaseAsset = "bible/dhormdutancho_itihas.sqlite"),
        BibleBook(id = "efezkarank", displayName = "Efezkarank", englishName = "Ephesians", testament = Testament.NEW_TESTAMENT, position = 48, databaseAsset = "bible/efezkarank.sqlite"),
        BibleBook(id = "filemonak", displayName = "Filemonak", englishName = "Philemon", testament = Testament.NEW_TESTAMENT, position = 49, databaseAsset = "bible/filemonak.sqlite"),
        BibleBook(id = "filipkarank", displayName = "Filipkarank", englishName = "Philippians", testament = Testament.NEW_TESTAMENT, position = 50, databaseAsset = "bible/filipkarank.sqlite"),
        BibleBook(id = "galasiekarank", displayName = "Galasiek", englishName = "Galatians", testament = Testament.NEW_TESTAMENT, position = 51, databaseAsset = "bible/galasiekarank.sqlite"),
        BibleBook(id = "hebrevank", displayName = "Hebrevank", englishName = "Hebrews", testament = Testament.NEW_TESTAMENT, position = 52, databaseAsset = "bible/hebrevank.sqlite"),
        BibleBook(id = "jakob", displayName = "Jakobachem", englishName = "James", testament = Testament.NEW_TESTAMENT, position = 53, databaseAsset = "bible/jakob.sqlite"),
        BibleBook(id = "juanv", displayName = "Juanva", englishName = "John", testament = Testament.NEW_TESTAMENT, position = 54, databaseAsset = "bible/juanv.sqlite"),
        BibleBook(id = "juanv1", displayName = "Juanvachem Poilem", englishName = "1 John", testament = Testament.NEW_TESTAMENT, position = 55, databaseAsset = "bible/juanv1.sqlite"),
        BibleBook(id = "juanv2", displayName = "Juanvachem Dusrem", englishName = "2 John", testament = Testament.NEW_TESTAMENT, position = 56, databaseAsset = "bible/juanv2.sqlite"),
        BibleBook(id = "juanv3", displayName = "Juanvachem Tisrem", englishName = "3 John", testament = Testament.NEW_TESTAMENT, position = 57, databaseAsset = "bible/juanv3.sqlite"),
        BibleBook(id = "juanvak_prokasovnni", displayName = "Juanvak", englishName = "Revelation", testament = Testament.NEW_TESTAMENT, position = 58, databaseAsset = "bible/juanvak_prokasovnni.sqlite"),
        BibleBook(id = "judas", displayName = "Judasachem", englishName = "Jude", testament = Testament.NEW_TESTAMENT, position = 59, databaseAsset = "bible/judas.sqlite"),
        BibleBook(id = "koloskarank", displayName = "Koloskarank", englishName = "Colossians", testament = Testament.NEW_TESTAMENT, position = 60, databaseAsset = "bible/koloskarank.sqlite"),
        BibleBook(id = "korintkarant1", displayName = "Korintkarank Pauluchem Poilem Potr", englishName = "1 Corinthians", testament = Testament.NEW_TESTAMENT, position = 61, databaseAsset = "bible/korintkarant1.sqlite"),
        BibleBook(id = "korintkarant2", displayName = "Korintkarank Pauluchem Dusrem Potr", englishName = "2 Corinthians", testament = Testament.NEW_TESTAMENT, position = 62, databaseAsset = "bible/korintkarant2.sqlite"),
        BibleBook(id = "lukas", displayName = "Luka", englishName = "Luke", testament = Testament.NEW_TESTAMENT, position = 63, databaseAsset = "bible/lukas.sqlite"),
        BibleBook(id = "mark", displayName = "Marku", englishName = "Mark", testament = Testament.NEW_TESTAMENT, position = 64, databaseAsset = "bible/mark.sqlite"),
        BibleBook(id = "matev", displayName = "Mateva", englishName = "Matthew", testament = Testament.NEW_TESTAMENT, position = 65, databaseAsset = "bible/matev.sqlite"),
        BibleBook(id = "pedru1", displayName = "Pedruchem Poilem", englishName = "1 Peter", testament = Testament.NEW_TESTAMENT, position = 66, databaseAsset = "bible/pedru1.sqlite"),
        BibleBook(id = "pedru2", displayName = "Pedruchem Dusrem", englishName = "2 Peter", testament = Testament.NEW_TESTAMENT, position = 67, databaseAsset = "bible/pedru2.sqlite"),
        BibleBook(id = "romkarank", displayName = "Romkarank Pauluchem Potr", englishName = "Romans", testament = Testament.NEW_TESTAMENT, position = 68, databaseAsset = "bible/romkarank.sqlite"),
        BibleBook(id = "tesalonikarank1", displayName = "Tesalonikarank Pauluchem Poilem Potr", englishName = "1 Thessalonians", testament = Testament.NEW_TESTAMENT, position = 69, databaseAsset = "bible/tesalonikarank1.sqlite"),
        BibleBook(id = "tesalonikarank2", displayName = "Tesalonikarank Pauluchem Dusrem Potr", englishName = "2 Thessalonians", testament = Testament.NEW_TESTAMENT, position = 70, databaseAsset = "bible/tesalonikarank2.sqlite"),
        BibleBook(id = "tilak", displayName = "Titak", englishName = "Titus", testament = Testament.NEW_TESTAMENT, position = 71, databaseAsset = "bible/tilak.sqlite"),
        BibleBook(id = "timotak1", displayName = "Timotak Pauluchem Poilem Potr", englishName = "1 Timothy", testament = Testament.NEW_TESTAMENT, position = 72, databaseAsset = "bible/timotak1.sqlite"),
        BibleBook(id = "timotak2", displayName = "Timotak Pauluchem Dusrem Potr", englishName = "2 Timothy", testament = Testament.NEW_TESTAMENT, position = 73, databaseAsset = "bible/timotak2.sqlite"),
    )

    fun byId(bookId: String): BibleBook? =
        books.firstOrNull { it.id == bookId }

    fun byPosition(position: Int): BibleBook? =
        books.firstOrNull { it.position == position }
}
