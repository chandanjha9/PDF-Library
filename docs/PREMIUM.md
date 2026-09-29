# Premium books (20 MB+) & cover photos

## Rules
- A book is **Premium** when its file is larger than 20 MB (`PREMIUM_MIN_BYTES`).
- Unlock once per device per book: **Pay ₹10 via UPI** (to `9576840671@axl`) **or watch a rewarded ad**.
- **Manual delivery (current mode, `LARGE_FILES_ENABLED=false`):** the user enters a WhatsApp number or
  Telegram @username, pays ₹10 or watches an ad → the order is saved and your bot sends you (ADMIN_CHAT_ID)
  a Telegram message with the order + the book file. Forward the file to the customer, then press
  **Mark sent** on `/admin/orders?key=<ADMIN_KEY>` — the customer's app then shows "Sent!".
- **Automatic mode (`LARGE_FILES_ENABLED=true`):** once a local Bot API server serves 20 MB+ files, unlocking
  starts the in-app download instead.

## How payment works (and its limits)
- The app opens the phone's UPI apps with payee, ₹10.00 and a unique `txnRef` (`PDFL<bookId>T<time>`).
- It unlocks when the UPI app reports `Status=SUCCESS`; if the UPI app reports nothing, the user is
  asked "Did the payment go through?" and unlocks on "Yes, I paid". **This is honour-system** (chosen design):
  it cannot be verified server-side.
- Reconcile: `/admin/orders?key=<ADMIN_KEY>` (page) or `/admin/unlocks?key=…` (JSON) lists every order with
  contact, method and UPI reference. Render free wipes this list on restart — the Telegram messages are the
  permanent record.
  Compare `txnRef` / `txnId` with your bank/UPI app statement.
- Some UPI apps (notably Google Pay) may refuse pre-filled payments to a personal UPI ID. For reliable,
  verified payments move to a gateway (Razorpay/Cashfree) — the app/server structure is ready for it.
- Unlocks are saved on the phone too and re-registered automatically, because Render's free plan
  wipes server data on restart.

## Ads
- AdMob rewarded ads, currently Google's **test IDs** (`app/build.gradle.kts`):
  `manifestPlaceholders["admobAppId"]` and `ADMOB_REWARDED_ID`. Replace both with your own IDs from
  admob.google.com before real users. Never click your own live ads.
- AdMob policy: don't monetise content you don't hold rights to.

## Cover photos
- Telegram generates a first-page thumbnail for most PDFs. The backend stores it (`thumb_file_id`) from the
  webhook and channel sync, and serves it at `GET /books/:id/cover`; books get `cover_url` automatically.
- Existing books are back-filled on the next channel sync (runs at every server start).
- Books without a Telegram thumbnail keep the generated coloured cover.
