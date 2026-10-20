package com.biblia.povitrpustok.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biblia.povitrpustok.feature.books.BookChaptersScreen
import com.biblia.povitrpustok.feature.books.BookIntroductionScreen
import com.biblia.povitrpustok.feature.books.BooksScreen
import com.biblia.povitrpustok.feature.bookmarks.BookmarksScreen
import com.biblia.povitrpustok.feature.dailyreading.DailyReadingScreen
import com.biblia.povitrpustok.feature.reader.ReaderScreen
import com.biblia.povitrpustok.feature.reference.ReferenceEntryScreen
import com.biblia.povitrpustok.feature.search.SearchScreen
import com.biblia.povitrpustok.ui.HomeScreen

/**
 * Application navigation graph (Navigation Compose, implementation plan
 * §31, §14).
 *
 * Home -> Books -> Book Chapters -> Reader and Home -> Search use a single
 * NavController/back stack; every destination takes compact typed
 * identifiers only.
 */
@Composable
fun PovitrPustokNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppDestination.HOME
    ) {
        composable(AppDestination.HOME) {
            HomeScreen(
                onOpenBooks = { navController.navigate(AppDestination.BOOKS) },
                onOpenSearch = { navController.navigate(AppDestination.SEARCH) },
                onOpenReference = { navController.navigate(AppDestination.REFERENCE_ENTRY) },
                onOpenBookmarks = { navController.navigate(AppDestination.BOOKMARKS) },
                onContinueReading = { readingState ->
                    navController.navigate(
                        AppDestination.reader(readingState.bookId, readingState.chapterPosition)
                    )
                },
                onOpenDailyReading = { navController.navigate(AppDestination.DAILY_READING) }
            )
        }

        composable(AppDestination.DAILY_READING) {
            DailyReadingScreen(
                onOpenReading = { destination ->
                    navController.navigate(
                        AppDestination.reader(
                            destination.bookId,
                            destination.chapterPosition,
                            destination.verseId,
                            destination.verseStart,
                            destination.verseEnd
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppDestination.BOOKMARKS) {
            BookmarksScreen(
                onOpenBookmark = { destination ->
                    navController.navigate(
                        AppDestination.reader(
                            destination.bookId,
                            destination.chapterPosition,
                            destination.verseId,
                            destination.verseStart,
                            destination.verseEnd
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppDestination.BOOKS) {
            BooksScreen(
                onBookSelected = { bookId ->
                    navController.navigate(AppDestination.bookChapters(bookId))
                }
            )
        }

        composable(
            route = AppDestination.BOOK_CHAPTERS,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
            BookChaptersScreen(
                bookId = bookId,
                onChapterSelected = { chapterPosition ->
                    navController.navigate(AppDestination.reader(bookId, chapterPosition))
                },
                onIntroductionSelected = {
                    navController.navigate(AppDestination.bookIntroduction(bookId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppDestination.BOOK_INTRODUCTION,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
            BookIntroductionScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppDestination.READER,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("chapterPosition") { type = NavType.IntType },
                navArgument("verseId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("verseStart") { type = NavType.IntType; defaultValue = -1 },
                navArgument("verseEnd") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            val verseId = arguments?.getLong("verseId")?.takeIf { it >= 0 }
            val verseStart = arguments?.getInt("verseStart")?.takeIf { it >= 0 }
            val verseEnd = arguments?.getInt("verseEnd")?.takeIf { it >= 0 }
            ReaderScreen(
                bookId = arguments?.getString("bookId").orEmpty(),
                chapterPosition = arguments?.getInt("chapterPosition") ?: 1,
                onBack = { navController.popBackStack() },
                targetVerseId = verseId,
                passageStart = verseStart,
                passageEnd = verseEnd
            )
        }

        composable(AppDestination.SEARCH) {
            SearchScreen(
                onOpenResult = { result ->
                    navController.navigate(
                        AppDestination.reader(result.bookId, result.chapterPosition, result.verseId)
                    )
                }
            )
        }

        composable(AppDestination.REFERENCE_ENTRY) {
            ReferenceEntryScreen(
                onOpenResolved = { destination ->
                    navController.navigate(
                        AppDestination.reader(
                            destination.bookId,
                            destination.chapterPosition,
                            destination.verseId,
                            destination.verseStart,
                            destination.verseEnd
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
