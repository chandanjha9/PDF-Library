package com.example.pdflibrary.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class UserProfile(
    val name: String = DEFAULT_NAME,
    val bio: String = "",
    val avatar: String = DEFAULT_AVATAR,
) {
    companion object {
        const val DEFAULT_NAME = "Reader"
        const val DEFAULT_AVATAR = "📚"
        val AVATARS = listOf("📚", "👓", "🚀", "🧙", "🎨", "🌟")
    }
}

/**
 * Single owner of all SharedPreferences keys. Previously Home and Profile each
 * opened "user_profile" with different defaults ("Reader" vs "Ratul"), so the
 * greeting and the profile page disagreed until the user pressed Save.
 */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val legacyProfile = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    private val legacyTour = context.getSharedPreferences("app_tour_prefs", Context.MODE_PRIVATE)

    /**
     * Stable per-install id sent to the backend. Previously every phone sent the
     * literal "android_user", so all users saw each other's 20-minute passes.
     */
    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_DEVICE_ID, it) }
        }
    }

    // ── Profile ──────────────────────────────────────────────────────────────
    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private fun readProfile(): UserProfile {
        // Migrate values saved by the old ProfileScreen, if any.
        val src = if (prefs.contains(KEY_NAME)) prefs else legacyProfile
        return UserProfile(
            name   = src.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: UserProfile.DEFAULT_NAME,
            bio    = src.getString(KEY_BIO, null).orEmpty(),
            avatar = src.getString(KEY_AVATAR, null) ?: UserProfile.DEFAULT_AVATAR,
        )
    }

    fun saveProfile(profile: UserProfile) {
        val clean = profile.copy(
            name = profile.name.trim().ifBlank { UserProfile.DEFAULT_NAME },
            bio  = profile.bio.trim(),
        )
        prefs.edit {
            putString(KEY_NAME, clean.name)
            putString(KEY_BIO, clean.bio)
            putString(KEY_AVATAR, clean.avatar)
        }
        _profile.value = clean
    }

    // ── Server address override (Profile → Server address) ───────────────────
    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() }
        set(v) = prefs.edit { if (v.isNullOrBlank()) remove(KEY_SERVER_URL) else putString(KEY_SERVER_URL, v) }

    // ── Premium unlocks ─────────────────────────────────────────────────────
    // Kept locally so a purchase survives server restarts (Render free has no
    // persistent disk); the app re-registers it with the server when needed.
    data class LocalUnlock(val method: String, val ref: String?, val contact: String?)

    fun localUnlock(bookId: Int): LocalUnlock? {
        val method = prefs.getString("unlock_${bookId}_method", null) ?: return null
        return LocalUnlock(
            method = method,
            ref = prefs.getString("unlock_${bookId}_ref", null),
            contact = prefs.getString("unlock_${bookId}_contact", null),
        )
    }

    fun saveLocalUnlock(bookId: Int, method: String, ref: String?, contact: String?) = prefs.edit {
        putString("unlock_${bookId}_method", method)
        putString("unlock_${bookId}_ref", ref)
        putString("unlock_${bookId}_contact", contact)
        if (!contact.isNullOrBlank()) putString(KEY_CONTACT, contact)
    }

    /** Last WhatsApp/Telegram contact the user entered (prefills the next order). */
    val lastContact: String get() = prefs.getString(KEY_CONTACT, null).orEmpty()

    // ── First-run flags ──────────────────────────────────────────────────────
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(v) = prefs.edit { putBoolean(KEY_ONBOARDING, v) }

    var tourDone: Boolean
        get() = prefs.getBoolean(KEY_TOUR, false) ||
                legacyTour.getBoolean("has_completed_spotlight_tour_v2", false)
        set(v) = prefs.edit { putBoolean(KEY_TOUR, v) }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_NAME = "user_name"
        const val KEY_BIO = "user_bio"
        const val KEY_AVATAR = "user_avatar"
        const val KEY_ONBOARDING = "onboarding_done_v1"
        const val KEY_TOUR = "tour_done_v3"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_CONTACT = "delivery_contact"
    }
}
