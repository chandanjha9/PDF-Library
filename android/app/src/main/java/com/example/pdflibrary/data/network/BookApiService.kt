package com.example.pdflibrary.data.network

import com.example.pdflibrary.BuildConfig
import com.example.pdflibrary.data.model.ActiveSessionsResponse
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.data.model.BookRequestPayload
import com.example.pdflibrary.data.model.BookRequestResponse
import com.example.pdflibrary.data.model.BooksResponse
import com.example.pdflibrary.data.model.DownloadResponse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// ── Retrofit Interface ──────────────────────────────────────────────────────

interface BookApiService {

    @GET("health")
    suspend fun health(): Response<Map<String, Any>>

    /** Browse/list recent books (empty query state) */
    @GET("books")
    suspend fun listBooks(
        @Query("limit")  limit:  Int = 40,
        @Query("offset") offset: Int = 0,
    ): Response<BooksResponse>

    /** Search by title */
    @GET("books/search")
    suspend fun searchBooks(
        @Query("q")      query:  String,
        @Query("limit")  limit:  Int = 40,
        @Query("offset") offset: Int = 0,
    ): Response<BooksResponse>

    @GET("books/{id}")
    suspend fun getBook(@Path("id") id: Int): Response<Book>

    /** Opens (or reuses) a 20-min pass for this device and returns a backend-relative file URL. */
    @GET("books/{id}/download")
    suspend fun getDownloadUrl(
        @Path("id") id: Int,
        @Query("device_id") deviceId: String,
    ): Response<DownloadResponse>

    @POST("books/request")
    suspend fun requestBook(@Body payload: BookRequestPayload): Response<BookRequestResponse>

    @GET("books/requests")
    suspend fun getActiveSessions(@Query("device_id") deviceId: String): Response<ActiveSessionsResponse>

    @GET("books/session/{id}/download")
    suspend fun getSessionDownloadUrl(@Path("id") sessionId: String): Response<DownloadResponse>
}

// ── Retrofit / OkHttp Factory ────────────────────────────────────────────────

/** Thrown when no backend address answered; carries the addresses tried for the error UI. */
class ServerUnreachableException(val triedHosts: List<String>, cause: IOException?) :
    IOException("No server answered. Tried: ${triedHosts.joinToString()}", cause)

object ApiClient {

    /**
     * Placeholder host. Every request addressed to it is rewritten by
     * [failoverInterceptor] to the first reachable real backend. Requests to any
     * other host (e.g. an absolute CDN URL) pass through untouched — previously
     * *every* URL was rewritten, which would have broken absolute file URLs.
     */
    private const val VIRTUAL_HOST = "backend.invalid"
    val virtualBaseUrl: HttpUrl = "http://$VIRTUAL_HOST/".toHttpUrl()

    /**
     * Built-in backends, tried in order after the user's custom address:
     *  1. BuildConfig.BASE_URL  (public ngrok / production domain)
     *  2. LAN IP of the dev PC  (same Wi-Fi)
     *  3. 127.0.0.1             (phone on USB with `adb reverse tcp:3000 tcp:3000`)
     */
    private val builtInHosts: List<HttpUrl> = listOfNotNull(
        runCatching { BuildConfig.BASE_URL.toHttpUrl() }.getOrNull(),
        "http://192.168.29.200:3000/".toHttpUrl(),
        "http://127.0.0.1:3000/".toHttpUrl(),
    ).distinct()

    /**
     * Address entered in Profile → Server address (e.g. the PC's current Wi-Fi IP
     * or today's ngrok URL). Tried first, so a changed IP no longer needs a rebuild.
     */
    @Volatile var customBaseUrl: HttpUrl? = null
        set(value) {
            field = value
            preferredHost.set(0)
        }

    val candidateHosts: List<HttpUrl>
        get() = (listOfNotNull(customBaseUrl) + builtInHosts).distinct()

    /** Normalises user input like "192.168.1.5:3000" → http://192.168.1.5:3000/ (null if invalid). */
    fun parseServerUrl(input: String): HttpUrl? {
        val t = input.trim().trimEnd('/')
        if (t.isEmpty()) return null
        val withScheme = if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
        val url = "$withScheme/".toHttpUrlOrNull() ?: return null
        return url.newBuilder().encodedPath("/").build()
    }

    /** Index of the host that answered last; tried first next time (avoids a 5 s timeout per call). */
    private val preferredHost = AtomicInteger(0)

    private val failoverInterceptor = Interceptor { chain ->
        val original = chain.request()
        if (original.url.host != VIRTUAL_HOST) return@Interceptor chain.proceed(original)

        val hosts = candidateHosts
        val start = preferredHost.get().coerceIn(0, hosts.size - 1)
        var lastError: IOException? = null
        for (i in hosts.indices) {
            val idx = (start + i) % hosts.size
            val host = hosts[idx]
            val url = original.url.newBuilder()
                .scheme(host.scheme)
                .host(host.host)
                .port(host.port)
                .build()
            try {
                val response = chain.proceed(
                    original.newBuilder()
                        .url(url)
                        // Skip ngrok's free-tier HTML interstitial.
                        .header("ngrok-skip-browser-warning", "1")
                        .build()
                )
                // ngrok answers 404 + Ngrok-Error-Code when the tunnel is offline → try next host.
                val tunnelOffline = response.header("Ngrok-Error-Code") != null ||
                        response.code == 502 && host.host.endsWith("ngrok-free.dev")
                if (!tunnelOffline) {
                    preferredHost.set(idx)
                    return@Interceptor response
                }
                response.close()
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw ServerUnreachableException(hosts.map { it.toString().trimEnd('/') }, lastError)
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(failoverInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val bookApiService: BookApiService by lazy {
        Retrofit.Builder()
            .baseUrl(virtualBaseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BookApiService::class.java)
    }

    /** Resolves a (possibly backend-relative) file URL returned by the API. */
    fun resolve(url: String): HttpUrl =
        if (url.startsWith("http://") || url.startsWith("https://")) url.toHttpUrl()
        else virtualBaseUrl.resolve(url) ?: throw IOException("Invalid file URL: $url")
}
