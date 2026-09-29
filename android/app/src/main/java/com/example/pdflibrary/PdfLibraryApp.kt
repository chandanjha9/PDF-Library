package com.example.pdflibrary

import android.app.Application
import com.example.pdflibrary.data.AppPrefs
import com.example.pdflibrary.data.BookRepository
import com.example.pdflibrary.data.network.ApiClient

class PdfLibraryApp : Application() {
    val prefs: AppPrefs by lazy { AppPrefs(this) }
    val repository: BookRepository by lazy { BookRepository(this, prefs) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs.serverUrl?.let { ApiClient.customBaseUrl = ApiClient.parseServerUrl(it) }
    }

    companion object {
        /** Set in onCreate; used by ViewModel factories (see ViewModelFactory.kt). */
        lateinit var instance: PdfLibraryApp
            private set
    }
}
