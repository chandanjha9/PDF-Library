# PDF Library: Audit & Change Log

**Date:** 29 Sep 2026  **Scope:** `android/` (Kotlin + Compose) and `backend/` (Node + Express)

---

## 1. Summary

| Area | Before | After |
|---|---|---|
| Download from book page | **Always failed** (app called `GET /books/:id/download`, route didn't exist → 404) | Route added; streams the PDF through the backend with progress |
| Opening a 2nd book | **Showed the 1st book again**; reading progress saved to the wrong book | Per-screen ViewModels (Navigation3 decorators + per-book keys) |
| Bottom modules (Library / Request / Profile) | Bar drawn under system nav bar; Request dead on Library/Profile tabs; `/apk` served an old build without them | Inset-aware bar; Request works from every tab; `/apk` serves newest build |
| PDF viewer | Crashed on fast swiping; never resumed at last page; crashed on corrupt files | Serialized renderer, resume works, error state, slider + double-tap zoom |
| Colours | Crimson + GitHub greys + random gradients, unrelated to logo; red used for both brand and errors | One logo-derived palette; all text pairs pass WCAG AA |
| Text | Colours baked into typography overrode button/chip text colours; status-bar icons invisible on light-mode phones; headers under status bar | Colour-free typography; forced light status icons; insets respected |
| Security | **Telegram bot token sent to every phone** inside `file_url` | Token never leaves the server |

> ⚠️ **Action needed:** rotate the bot token in @BotFather. It was exposed to clients, and the real `.env` is inside the project zip.

---

## 2. The "three missing modules"

Interpreted as **Library, Request and Profile**, the three bottom-nav modules besides Home. Four independent causes:

1. **Edge-to-edge insets.** `enableEdgeToEdge()` was on, but the custom bottom bar never padded for the system navigation bar, so on most phones it sat under the 3-button/gesture bar. → `AppBottomNavBar` now applies `WindowInsets.navigationBars`.
2. **Request only existed inside HomeScreen.** Tapping *Request* while on Library/Profile updated state for a dialog that wasn't composed. → Dialog hoisted to `MainScreen`.
3. **Library showed `Book #12`.** Favorites/downloads stored only IDs, and the book cache was wiped on every refresh. → Cache is upsert-only; Library rows are a Room `LEFT JOIN` with real titles/authors/page.
4. **`GET /apk` served `PDFLibrary_NewDesign.apk`**, an older build that has no nav shell, Profile or tour (verified by listing its classes). → Serves the newest build found.

Also: Onboarding existed but was unreachable (now shown on first launch), and the selected tab reset to Home after returning from a book (now `rememberSaveable`).

---

## 3. Design system ("Midnight Library")

Derived from the logo's periwinkle sky (`#8BA6D3`). One accent family for everything interactive; amber only for time-sensitive info; red only for errors/destructive actions.

| Token | Hex | Use | Contrast |
|---|---|---|---|
| Ink950 | `#0E1320` | Background | n/a |
| Ink900 / 850 / 800 | `#161C2B` / `#1C2436` / `#252F45` | Bars & dialogs / cards / inputs | n/a |
| TextHigh | `#E7EBF3` | Titles | 15.5:1 on bg |
| TextMedium | `#A6B0C3` | Secondary | 8.5:1 on bg, 6.1:1 on inputs |
| TextLow | `#8C98AF` | Placeholders | ≥ 4.5:1 on cards |
| Periwinkle | `#9DB6E6` | Primary buttons, active | 9.1:1 on bg |
| OnPeriwinkle | `#0D1A33` | Text on primary | 8.5:1 |
| Amber | `#F2B872` | Timers, bookmarks | 8.8:1 on cards |
| Danger | `#F4868A` | Errors, delete | 6.4:1 on cards |
| Cover gradients (7) | slate, teal, plum, leather, sage, mulberry, graphite | Generated covers | white text ≥ 5.7:1 |

Rules enforced in code: screens read `MaterialTheme.colorScheme` (no raw hex), `Type.kt` styles carry **no colour**, and shared components (`BookCover`, `AppCard`, `EmptyState`, `NoticeBanner`, `MetaPill`, `appTextFieldColors`) replace per-screen one-offs. The window theme is now dark (no white flash on launch), and the launcher-icon background matches the logo sky.

---

## 4. Stability & performance fixes (Android)

- **ViewModel scoping:** added `rememberSaveableStateHolderNavEntryDecorator()` + `rememberViewModelStoreNavEntryDecorator()` to `NavDisplay`; Detail/Viewer VMs are also keyed per book.
- **PDF renderer** moved into `PdfViewerViewModel`: all access is behind a `Mutex`, superseded renders are cancelled, render width follows the screen (capped, aspect-correct) instead of a fixed 2×, and damaged/password-protected/missing files show an error state instead of crashing.
- **Downloads:** go through OkHttp (timeouts + failover), write to a `.part` file, check the `%PDF` header, then rename atomically, so there are no half-written "downloaded" books. Progress and cancel are supported.
- **Networking:** the failover interceptor remembers the last working host. Previously every call could wait 5 s on an unreachable LAN IP first. It only rewrites backend requests (not absolute URLs), sends the ngrok skip-warning header, and logs only in debug.
- **Per-install device ID** replaces the shared `"android_user"`. Previously every phone saw every other phone's 20-minute passes.
- **Error handling:** 404 "not found" requests show the server message + suggestions, not raw JSON. Home shows network errors with Retry (before, the screen was just blank). Offline mode falls back to cached books.
- **Recomposition:** the 1-second countdown ticked the entire Home UI state. Now only the pass card recomposes. Home is a `LazyColumn` with pull-to-refresh.
- **Honest UI:** removed the fake reading progress (`(id*17)%65+30`%), placeholder author "Second Chance Romance", and the hard-coded "Backend: Online" label (now a real health check).
- **Category filter** no longer silently shows *all* books when a category is empty.
- **Tour:** spotlights use measured on-screen positions (were hard-coded screen %), and the overlay now blocks taps from reaching the UI underneath. Text is English for consistency (it was Hinglish; easy to change back in `SpotlightTour.kt`).
- **Manifest/resources:** removed unused `RECEIVE_BOOT_COMPLETED`; moved `windowSoftInputMode` to the activity (it was invalid on `<application>`); downloaded PDFs are excluded from cloud backup; `proguard-rules.pro` added (it was referenced but missing).
- **Tests:** the template tests referenced non-existent classes, so `gradlew test` couldn't compile. They're replaced with `FormattersTest`, `BookCategoryTest` (unit) and `BottomNavBarTest` (instrumented; asserts all four modules render and respond).

## 5. Backend fixes

- `GET /books/:id/download` added (opens/reuses a device pass).
- `GET /books/session/:id/file` streams the PDF; `file_url`s are backend-relative. **The bot token is no longer sent to clients.**
- **Fuzzy matcher:** gibberish like "zzqxv nonexistent" was granted a pass to a random book, because 2-letter fragments counted as matches. Tightened, with a grant threshold of 0.5 (`GRANT_MIN_SCORE`). Misses now suggest the closest titles.
- **On-demand channel sync** is single-flight with a 5-minute cooldown. Before, every miss re-scanned 50 messages, adding seconds and churning the channel.
- **Webhook** logged every new book as "Duplicate skipped" (it checked SQLite fields on a LokiJS result). Fixed.
- **Startup:** requests before the DB loaded returned 500. They now wait.
- New optional env vars: `TELEGRAM_API_BASE`, `GRANT_MIN_SCORE`, `SYNC_COOLDOWN_MS`, `LIBRARY_DB_PATH`, `APK_PATH` (documented in `.env.example`).

---

## 6. Verification

| Check | Result |
|---|---|
| Kotlin syntax (all 37 files, Kotlin 2.0.21 compiler) | ✅ 0 syntax errors |
| Unit tests: real `Formatters`, `BookCategory`, `Book`, `Color` sources | ✅ 8/8 pass |
| Backend end-to-end vs. mock Telegram (download route, streaming, headers, no token leak, per-device passes, 400/404/410 paths, request found/not-found, webhook secret + indexing, `/apk`) | ✅ all pass |
| Palette contrast (script) | ✅ all AA |
| **Full Android build** | ⏳ **Not run.** Google Maven / Gradle are blocked in the audit sandbox. Run the steps below. |

The Kotlin was hand-checked against the Compose Material3 / Navigation3 1.0 / Room / OkHttp APIs pinned in `libs.versions.toml`. If the first build reports anything, it will most likely be an API-name mismatch in `Navigation.kt` (decorator function names), so fix those first.

### Build & install
```bash
cd android
./gradlew clean testDebugUnitTest assembleDebug     # Windows: .\gradlew.bat …
# APK: android/app/build/outputs/apk/debug/app-debug.apk  (also served at GET /apk)
./gradlew connectedDebugAndroidTest                 # with a device/emulator attached
```
Uninstall the old build first, or clear app data, so onboarding shows once.

---

## 7. Recommended next steps

1. Rotate the bot token; keep `.env` out of shared zips / VCS.
2. Enable R8 for release (`isMinifyEnabled = true`; rules are in place). `material-icons-extended` is most of the ~21 MB APK.
3. Replace the hard-coded LAN IP with a real HTTPS domain, then drop `usesCleartextTraffic`.
4. Add a real `category` field to books in the backend; the keyword filter is a stop-gap.
