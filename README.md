# PDF Library App

A native Android app + Node.js backend that lets users search and read PDF books sourced from a private Telegram channel — **no Telegram account required** on the user's device.

---

## Project Structure

```
PDF/
├── backend/        ← Node.js + Express API
└── android/        ← Kotlin + Jetpack Compose Android app
```

---

## Quick Start

### 1. Set up the Backend

```bash
cd backend

# Copy env file and fill in your values
copy .env.example .env
# Edit .env: add BOT_TOKEN, CHANNEL_ID, WEBHOOK_URL

# Install dependencies
npm install

# Start server
npm start
```

The server runs on `http://localhost:3000`.

**For local Telegram webhook testing**, install [ngrok](https://ngrok.com/):
```bash
ngrok http 3000
# Copy the https URL → paste into WEBHOOK_URL in .env, then restart server
```

### 2. Set up the Telegram Bot

1. Chat with [@BotFather](https://t.me/BotFather) → `/newbot` → copy the token
2. Add the bot as an **Admin** of your Telegram channel (so it can receive posts)
3. Paste the token in `.env` as `BOT_TOKEN`

### 3. Test the Backend

```bash
# Health check
curl http://localhost:3000/health

# Search books
curl "http://localhost:3000/books/search?q=python"

# List recent books
curl "http://localhost:3000/books"
```

### 4. Build & Run Android App

```bash
cd android

# Build debug APK
.\gradlew.bat assembleDebug

# Or, if you have a connected device / emulator:
android emulator create
android emulator start
android run
```

> **Backend URL**: `BASE_URL` in `app/build.gradle.kts` is tried first; LAN / `adb reverse` fallbacks are in `ApiClient.candidateHosts` (`data/network/BookApiService.kt`). The app remembers whichever host answered last.

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/webhook/telegram` | Receives Telegram channel post updates |
| GET | `/health` | Health check |
| GET | `/books?limit=20&offset=0` | List recent books |
| GET | `/books/search?q=query` | Search books by title |
| GET | `/books/:id` | Get single book metadata |
| GET | `/books/:id/download?device_id=` | Opens/reuses a 20-min pass; returns JSON with a backend-relative `file_url` |
| POST | `/books/request` | Request a book by name → pass or suggestions |
| GET | `/books/requests?device_id=` | Active passes for a device |
| GET | `/books/session/:id/download` | Pass info (JSON) |
| GET | `/books/session/:id/file` | Streams the PDF (410 once the pass expires). The bot token never leaves the server. |

---

## App Screens

| Screen | Description |
|--------|-------------|
| **Onboarding** | First launch only |
| **Home** | Search, categories, For-you carousel, active passes, continue reading |
| **Request** (bottom bar) | Request any book from any tab → 20-min pass or suggestions |
| **Book Detail** | Metadata, save toggle, download with progress |
| **PDF Viewer** | Page render, swipe / slider navigation, double-tap zoom, auto-resume |
| **Library** | Saved + Downloads (real titles, offline check, delete) |
| **Profile** | Name/avatar, live server status, replay tour |

See `docs/AUDIT.md` for the full audit and change log.

---

## Architecture

```
Telegram Channel
      │ (webhook)
      ▼
Node.js Backend (Express + LokiJS)
      │ (REST API)
      ▼
Android App (Kotlin + Compose)
      │
      ├── Retrofit (network)
      ├── Room DB (cache, favorites, downloads, progress)
      └── AndroidPdfViewer (in-app rendering)
```

---

## File Size Limit

The standard Telegram Bot API supports files **up to 20 MB**. This app uses the standard API. If your books exceed 20 MB, you will need to host a [local Telegram Bot API server](https://core.telegram.org/bots/api#using-a-local-bot-api-server).
