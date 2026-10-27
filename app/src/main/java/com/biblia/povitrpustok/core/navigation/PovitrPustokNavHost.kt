package com.biblia.povitrpustok.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biblia.povitrpustok.feature.about.AboutApiScreen
import com.biblia.povitrpustok.feature.about.AboutAppLicenseScreen
import com.biblia.povitrpustok.feature.about.AboutCreditsScreen
import com.biblia.povitrpustok.feature.about.AboutLicensesScreen
import com.biblia.povitrpustok.feature.about.AboutScreen
import com.biblia.povitrpustok.feature.about.AboutSourceEditionScreen
import com.biblia.povitrpustok.feature.books.BookChaptersScreen
import com.biblia.povitrpustok.feature.books.BookIntroductionScreen
import com.biblia.povitrpustok.feature.books.BooksScreen
import com.biblia.povitrpustok.feature.bookmarks.BookmarksScreen
import com.biblia.povitrpustok.feature.dailyreading.DailyReadingScreen
import com.biblia.povitrpustok.feature.reader.ReaderScreen
import com.biblia.povitrpustok.feature.reference.ReferenceEntryScreen
import com.biblia.povitrpustok.feature.search.SearchScreen
import com.biblia.povitrpustok.domain.model.BibleEditions
import com.biblia.povitrpustok.feature.settings.SettingsScreen
import com.biblia.povitrpustok.ui.HomeScreen

/**
 * Application navigation graph (Navigation Compose, implementation plan
 * §31, §14).
 *
 * Home -> Books -> Book Chapters -> Reader and Home -> Search use a single
 * NavController/back stack; every destination takes compact typed
 * identifiers only.
 */
/** Navigation motion (F-19): 200 ms fade plus a small slide; never long. */
private const val NAV_FADE_MS = 200
private const val NAV_SLIDE_MS = 220
private const val NAV_SLIDE_DIVISOR = 16

@Composable
fun PovitrPustokNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppDestination.HOME,
        // F-19: short, declarative transitions. Compose honours the system
        // animation scale, including 0 (instant).
        enterTransition = {
            fadeIn(tween(NAV_FADE_MS)) +
                slideInHorizontally(tween(NAV_SLIDE_MS)) { it / NAV_SLIDE_DIVISOR }
        },
        exitTransition = { fadeOut(tween(NAV_FADE_MS)) },
        popEnterTransition = {
            fadeIn(tween(NAV_FADE_MS)) +
                slideInHorizontally(tween(NAV_SLIDE_MS)) { -it / NAV_SLIDE_DIVISOR }
        },
        popExitTransition = {
            fadeOut(tween(NAV_FADE_MS)) +
                slideOutHorizontally(tween(NAV_SLIDE_MS)) { it / NAV_SLIDE_DIVISOR }
        }
    ) {
        composable(AppDestination.HOME) {
            HomeScreen(
                onOpenBooks = { navController.navigate(AppDestination.BOOKS) },
                onOpenSearch = { navController.navigate(AppDestination.search()) },
                onOpenReference = { navController.navigate(AppDestination.REFERENCE_ENTRY) },
                onOpenBookmarks = { navController.navigate(AppDestination.BOOKMARKS) },
                onContinueReading = { readingState ->
                    navController.navigate(
                        AppDestination.reader(readingState.bookId, readingState.chapterPosition)
                    )
                },
                onOpenDailyReading = { navController.navigate(AppDestination.DAILY_READING) },
                onOpenAbout = { navController.navigate(AppDestination.SETTINGS) }
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
                            destination.verseEnd,
                            destination.passages
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
                            destination.verseEnd,
                            destination.passages,
                            destination.targetVerseNumber
                        )
                    )
                },
                onBack = { navController.popBackStack() },
                onOpenBooks = {
                    // Replace the empty list rather than stacking Books on
                    // top of it: Back from Books returns Home, and repeated
                    // taps cannot pile up destinations (F-16).
                    navController.navigate(AppDestination.BOOKS) {
                        popUpTo(AppDestination.BOOKMARKS) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(AppDestination.BOOKS) {
            BooksScreen(
                onBookSelected = { bookId ->
                    navController.navigate(AppDestination.bookChapters(bookId))
                },
                onBack = { navController.popBackStack() },
                onOpenSearch = { navController.navigate(AppDestination.search()) }
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
                onBack = { navController.popBackStack() },
                onOpenSearch = { navController.navigate(AppDestination.search()) }
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
                onBack = { navController.popBackStack() },
                // F-21: the end-of-introduction link returns to the book's
                // chapters. Introduction is only entered from Chapters, so
                // popping avoids a duplicate Chapters entry on the back stack.
                onOpenChapters = { navController.popBackStack() }
            )
        }

        composable(
            route = AppDestination.READER,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("chapterPosition") { type = NavType.IntType },
                navArgument("verseId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("verseStart") { type = NavType.IntType; defaultValue = -1 },
                navArgument("verseEnd") { type = NavType.IntType; defaultValue = -1 },
                navArgument("passages") { type = NavType.StringType; defaultValue = "" },
                navArgument("targetVerse") { type = NavType.IntType; defaultValue = -1 },
                navArgument("editionId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            val verseId = arguments?.getLong("verseId")?.takeIf { it >= 0 }
            val verseStart = arguments?.getInt("verseStart")?.takeIf { it >= 0 }
            val verseEnd = arguments?.getInt("verseEnd")?.takeIf { it >= 0 }
            val targetVerse = arguments?.getInt("targetVerse")?.takeIf { it >= 0 }
            ReaderScreen(
                bookId = arguments?.getString("bookId").orEmpty(),
                chapterPosition = arguments?.getInt("chapterPosition") ?: 1,
                onBack = { navController.popBackStack() },
                targetVerseId = verseId,
                passageStart = verseStart,
                passageEnd = verseEnd,
                passagesSpec = arguments?.getString("passages").orEmpty(),
                targetVerseNumber = targetVerse,
                editionId = arguments?.getString("editionId")
                    ?.takeIf { it.isNotBlank() }
                    ?: BibleEditions.KONKANI_ID,
                onOpenAbout = { navController.navigate(AppDestination.ABOUT) }
            )
        }

        composable(AppDestination.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(AppDestination.ABOUT) }
            )
        }

        composable(AppDestination.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onOpenSourceEdition = {
                    navController.navigate(AppDestination.ABOUT_SOURCE_EDITION)
                },
                onOpenApi = { navController.navigate(AppDestination.ABOUT_API) },
                onOpenLicenses = { navController.navigate(AppDestination.ABOUT_LICENSES) },
                onOpenCredits = { navController.navigate(AppDestination.ABOUT_CREDITS) }
            )
        }

        composable(AppDestination.ABOUT_SOURCE_EDITION) {
            AboutSourceEditionScreen(onBack = { navController.popBackStack() })
        }

        composable(AppDestination.ABOUT_API) {
            AboutApiScreen(onBack = { navController.popBackStack() })
        }

        composable(AppDestination.ABOUT_LICENSES) {
            AboutLicensesScreen(
                onBack = { navController.popBackStack() },
                onOpenAppLicense = {
                    navController.navigate(AppDestination.ABOUT_APP_LICENSE)
                }
            )
        }

        composable(AppDestination.ABOUT_APP_LICENSE) {
            AboutAppLicenseScreen(onBack = { navController.popBackStack() })
        }

        composable(AppDestination.ABOUT_CREDITS) {
            AboutCreditsScreen(
                onBack = { navController.popBackStack() },
                onOpenSourceEdition = {
                    navController.navigate(AppDestination.ABOUT_SOURCE_EDITION)
                },
                onOpenApi = { navController.navigate(AppDestination.ABOUT_API) },
                onOpenLicenses = { navController.navigate(AppDestination.ABOUT_LICENSES) }
            )
        }

        composable(
            route = AppDestination.SEARCH,
            arguments = listOf(
                navArgument("editionId") {
                    type = NavType.StringType
                    defaultValue = BibleEditions.KONKANI_ID
                }
            )
        ) {
            SearchScreen(
                onBack = { navController.popBackStack() },
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
                            destination.verseEnd,
                            destination.passages
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
