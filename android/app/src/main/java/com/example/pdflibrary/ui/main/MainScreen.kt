package com.example.pdflibrary.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdflibrary.PdfLibraryApp
import com.example.pdflibrary.ui.common.LocalTourTargets
import com.example.pdflibrary.ui.common.SpotlightTour
import com.example.pdflibrary.ui.common.TourTargetRegistry
import com.example.pdflibrary.ui.components.AppBottomNavBar
import com.example.pdflibrary.ui.components.MainTab
import com.example.pdflibrary.ui.home.HomeScreen
import com.example.pdflibrary.ui.home.HomeViewModel
import com.example.pdflibrary.ui.home.RequestBookDialog
import com.example.pdflibrary.ui.library.LibraryScreen
import com.example.pdflibrary.ui.profile.ProfileScreen

/**
 * Hosts the four persistent modules: Home · Library · Request (dialog) · Profile.
 *
 * Fixes vs. previous version:
 *  - The Request dialog lived inside HomeScreen, so tapping "Request" while on
 *    Library or Profile did nothing (HomeScreen wasn't composed). It is now
 *    hosted here and works from every tab.
 *  - Selected tab used `remember`, so returning from a book opened in Library
 *    dropped the user back on Home. It is now `rememberSaveable`.
 *  - Content now respects the status-bar inset (headers were drawn under it).
 */
@Composable
fun MainScreen(
    onBookClick: (Int) -> Unit,
    onOpenPdf: (bookId: Int, localPath: String) -> Unit,
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val app = LocalContext.current.applicationContext as PdfLibraryApp
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var showTour by rememberSaveable { mutableStateOf(!app.prefs.tourDone) }
    val tourTargets = remember { TourTargetRegistry() }
    val requestState by homeViewModel.request.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalTourTargets provides tourTargets) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    AppBottomNavBar(
                        selected = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onRequestClick = homeViewModel::openRequest,
                    )
                },
            ) { padding ->
                Crossfade(
                    targetState = selectedTab,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    label = "tab",
                ) { tab ->
                    when (tab) {
                        MainTab.Home -> HomeScreen(
                            viewModel = homeViewModel,
                            onBookClick = onBookClick,
                            onOpenPdf = onOpenPdf,
                            onProfileClick = { selectedTab = MainTab.Profile },
                            onRequestClick = homeViewModel::openRequest,
                        )
                        MainTab.Library -> LibraryScreen(
                            onOpenBook = onBookClick,
                            onOpenPdf = onOpenPdf,
                            onBrowse = { selectedTab = MainTab.Home },
                        )
                        MainTab.Profile -> ProfileScreen(
                            onReplayTour = {
                                selectedTab = MainTab.Home
                                showTour = true
                            },
                        )
                    }
                }
            }

            if (requestState.visible) {
                RequestBookDialog(
                    state = requestState,
                    onDismiss = homeViewModel::dismissRequest,
                    onSubmit = homeViewModel::requestBook,
                    onBackToForm = homeViewModel::resetRequestForm,
                    onOpenBook = { id ->
                        homeViewModel.dismissRequest()
                        onBookClick(id)
                    },
                )
            }

            if (showTour && selectedTab == MainTab.Home) {
                SpotlightTour(
                    onDismiss = {
                        showTour = false
                        app.prefs.tourDone = true
                    },
                )
            }
        }
    }
}
