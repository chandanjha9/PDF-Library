package com.example.pdflibrary.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pdflibrary.PdfLibraryApp

/**
 * Builds a factory that hands the ViewModel the app-level singletons.
 *
 * NOTE: the app is taken from [PdfLibraryApp.instance], NOT from
 * CreationExtras[APPLICATION_KEY]. Navigation3's per-entry ViewModelStore
 * (rememberViewModelStoreNavEntryDecorator) does not put the Application into
 * CreationExtras, so reading it from there returned null and crashed the app
 * the moment Home opened (right after "Get started").
 */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (PdfLibraryApp) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(PdfLibraryApp.instance) }
}
