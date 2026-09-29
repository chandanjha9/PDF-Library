package com.example.pdflibrary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.pdflibrary.ui.detail.DetailScreen
import com.example.pdflibrary.ui.main.MainScreen
import com.example.pdflibrary.ui.onboarding.OnboardingScreen
import com.example.pdflibrary.ui.viewer.PdfViewerScreen

@Composable
fun MainNavigation() {
    val app = LocalContext.current.applicationContext as PdfLibraryApp
    // First launch: Onboarding sits on top of Home and is popped when finished.
    val showOnboarding = remember { !app.prefs.onboardingDone }
    val backStack = if (showOnboarding) rememberNavBackStack(HomeRoute, OnboardingRoute)
                    else rememberNavBackStack(HomeRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        // Without these decorators every `viewModel()` call was scoped to the
        // Activity, so opening a second book reused the first book's
        // DetailViewModel / PdfViewerViewModel (wrong book shown, progress saved
        // to the wrong book) and screen state was never cleared on pop.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {

            entry<OnboardingRoute> {
                OnboardingScreen(
                    onFinish = {
                        app.prefs.onboardingDone = true
                        if (backStack.lastOrNull() == OnboardingRoute) backStack.removeLastOrNull()
                    },
                )
            }

            entry<HomeRoute> {
                MainScreen(
                    onBookClick = { bookId -> backStack.add(DetailRoute(bookId)) },
                    onOpenPdf = { bookId, localPath -> backStack.add(PdfViewerRoute(bookId, localPath)) },
                )
            }

            entry<DetailRoute> { key ->
                DetailScreen(
                    bookId = key.bookId,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenPdf = { localPath -> backStack.add(PdfViewerRoute(key.bookId, localPath)) },
                )
            }

            entry<PdfViewerRoute> { key ->
                PdfViewerScreen(
                    bookId = key.bookId,
                    localPath = key.localPath,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
