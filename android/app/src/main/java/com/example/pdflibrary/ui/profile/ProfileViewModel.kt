package com.example.pdflibrary.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdflibrary.data.AppPrefs
import com.example.pdflibrary.data.BookRepository
import com.example.pdflibrary.data.UserProfile
import com.example.pdflibrary.data.network.ApiClient
import com.example.pdflibrary.ui.common.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ServerStatus { Checking, Online, Offline }

class ProfileViewModel(
    private val repo: BookRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    val profile: StateFlow<UserProfile> = prefs.profile

    /** Real health check — previously a hard-coded "Online" label. */
    private val _serverStatus = MutableStateFlow(ServerStatus.Checking)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    init { checkServer() }

    fun checkServer() {
        viewModelScope.launch {
            _serverStatus.value = ServerStatus.Checking
            _serverStatus.value = if (repo.isServerOnline()) ServerStatus.Online else ServerStatus.Offline
        }
    }

    fun save(profile: UserProfile) = prefs.saveProfile(profile)

    // ── Server address ──────────────────────────────────────────────────────
    private val _serverUrl = MutableStateFlow(prefs.serverUrl.orEmpty())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    /** Every address the app will try, in order (for display). */
    fun addressesTried(): List<String> = ApiClient.candidateHosts.map { it.toString().trimEnd('/') }

    /** Saves (or clears, if blank) the custom address. Returns false if the input isn't a valid URL. */
    fun saveServerUrl(input: String): Boolean {
        if (input.isBlank()) {
            prefs.serverUrl = null
            ApiClient.customBaseUrl = null
            _serverUrl.value = ""
        } else {
            val url = ApiClient.parseServerUrl(input) ?: return false
            val text = url.toString().trimEnd('/')
            prefs.serverUrl = text
            ApiClient.customBaseUrl = url
            _serverUrl.value = text
        }
        checkServer()
        return true
    }

    companion object {
        val Factory = appViewModelFactory { ProfileViewModel(it.repository, it.prefs) }
    }
}
