package com.example.pdflibrary

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.example.pdflibrary.data.AppPrefs
import com.example.pdflibrary.data.BookRepository
import com.example.pdflibrary.data.network.ApiClient
import com.google.android.gms.ads.MobileAds

class PdfLibraryApp : Application(), SingletonImageLoader.Factory {
    val prefs: AppPrefs by lazy { AppPrefs(this) }
    val repository: BookRepository by lazy { BookRepository(this, prefs) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs.serverUrl?.let { ApiClient.customBaseUrl = ApiClient.parseServerUrl(it) }
        // AdMob init is slow; keep it off the main thread.
        Thread { runCatching { MobileAds.initialize(this) {} } }.start()
    }

    /**
     * Cover images go through the same OkHttp client as the API, so relative
     * `/books/{id}/cover` URLs get the same server failover.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { ApiClient.httpClient })) }
            .crossfade(true)
            .build()

    companion object {
        /** Set in onCreate; used by ViewModel factories (see ViewModelFactory.kt). */
        lateinit var instance: PdfLibraryApp
            private set
    }
}
