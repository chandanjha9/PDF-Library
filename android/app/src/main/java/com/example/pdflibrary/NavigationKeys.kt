package com.example.pdflibrary

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute : NavKey
@Serializable data object HomeRoute       : NavKey
@Serializable data class  DetailRoute(val bookId: Int) : NavKey
@Serializable data class  PdfViewerRoute(val bookId: Int, val localPath: String) : NavKey
