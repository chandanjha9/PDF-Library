'use strict';

/**
 * Books REST API Routes — Dynamic On-Demand Request & 20-Minute Expiring Access
 *
 * POST /books/request                       — User requests book (title required, author optional)
 * GET  /books/requests                      — List active 20-min temporary sessions for device
 * GET  /books/session/:id                   — Get status of 20-min temporary session
 * GET  /books/session/:id/download          — Expiration-checked download info (JSON)
 * GET  /books/session/:id/file              — Expiration-checked PDF stream (proxied)
 * GET  /books/search?q=<query>&author=<aut> — Search library catalog
 * GET  /books                               — List library catalog
 * GET  /books/:id                           — Single book metadata
 * GET  /books/:id/download                  — Open/reuse a 20-min pass for this device + file URL
 *
 * SECURITY: clients only ever receive backend-relative `/books/session/<id>/file`
 * URLs. Previously `file_url` was the raw Telegram URL, which contains the bot
 * token — anyone with the app could extract it and take over the bot.
 */

const express = require('express');
const {
  searchBooks,
  findBestMatches,
  listBooks,
  getBookById,
  getCount,
  createSession,
  getActiveSession,
  getActiveSessionsForDevice,
  isPremium,
  isUnlocked,
  addUnlock,
  getUnlock,
  getThumbFileId,
} = require('../db');
const { streamFile, streamImage, notifyAdminOrder } = require('../webhook');
const { syncChannelThrottled } = require('../sync');

function deviceIdFrom(req) {
  return String(req.body?.device_id || req.query.device_id || req.headers['x-device-id'] || 'default').trim().slice(0, 100) || 'default';
}

function fileUrlFor(sessionId) {
  return `/books/session/${encodeURIComponent(sessionId)}/file`;
}

function downloadPayload(session) {
  return {
    session_id:  session.session_id,
    book_id:     session.book.id,
    title:       session.book.title,
    file_url:    fileUrlFor(session.session_id),
    expires_at:  session.expires_at,
    ttl_seconds: session.ttl_seconds,
  };
}

const router = express.Router();

// Premium (>20 MB) files can only be delivered when a local Telegram Bot API
// server is configured. Until then premium unlocks are refused so nobody pays
// for a download that cannot work.
const LARGE_FILES_ENABLED = process.env.LARGE_FILES_ENABLED
  ? process.env.LARGE_FILES_ENABLED === 'true'   // explicit setting wins
  : (!!process.env.TELEGRAM_API_BASE && !/api\.telegram\.org/.test(process.env.TELEGRAM_API_BASE));

/** Returns an error payload if this device may not download this book, else null. */
function premiumBlock(book, deviceId) {
  if (!isPremium(book)) return null;
  if (!isUnlocked(deviceId, book.id)) {
    return { status: 402, body: { error: 'premium_required', message: 'This is a Premium book. Unlock it for ₹10 or by watching an ad.' } };
  }
  if (!LARGE_FILES_ENABLED) {
    // Paid, but the server can't stream 20 MB+ files: delivered manually by the admin.
    return { status: 413, body: { error: 'manual_delivery', message: 'Your Premium book will be sent to you on WhatsApp/Telegram.' } };
  }
  return null;
}

// ── POST /books/request (User Requests Book) ─────────────────────────────────

router.post('/request', async (req, res) => {
  const title    = (req.body.book_name || req.body.title || '').trim();
  const author   = (req.body.author_name || req.body.author || '').trim();
  const deviceId = deviceIdFrom(req);

  if (!title) {
    return res.status(400).json({ error: 'Book name is required.' });
  }

  try {
    // 1. Search library index using smart fuzzy ranking
    let matches = findBestMatches(title, author || null, 10);

    // 2. If no strong match found, perform on-the-fly sync with Telegram channel
    if (matches.length === 0 || matches[0].score < GRANT_MIN_SCORE) {
      try {
        console.log(`[Request] Searching channel for new posts matching "${title}"...`);
        await syncChannelThrottled(50);
        matches = findBestMatches(title, author || null, 10);
      } catch (syncErr) {
        console.warn('[Request] Background channel sync warning:', syncErr.message);
      }
    }

    // 3. Check best match
    if (matches.length > 0 && matches[0].score >= GRANT_MIN_SCORE) {
      const best = matches[0].book;
      const session = createSession(best, deviceId);
      const related = matches.slice(1, 5).map(m => m.book);

      console.log(`[Request] ✅ Handled request for "${title}" -> Match: "${best.title}" (Score: ${matches[0].score.toFixed(2)})`);

      return res.json({
        found:         true,
        session_id:    session.session_id,
        book:          session.book,
        expires_at:    session.expires_at,
        ttl_seconds:   session.ttl_seconds,
        download_url:  session.download_url,
        related_books: related,
        message:       `Book matched from digital library! Access is valid for 20 minutes.`,
      });
    }

    // 4. No strong match: suggest the closest titles, topped up with recent books
    const seen = new Set();
    const fallbackList = [...matches.map(m => m.book), ...listBooks(6, 0)]
      .filter(b => b && !seen.has(b.id) && seen.add(b.id))
      .slice(0, 6);
    return res.status(404).json({
      found:         false,
      error:         'Book not found',
      message:       `No exact match found for "${title}". Here are related books available in the digital library:`,
      related_books: fallbackList,
    });
  } catch (err) {
    console.error('[/books/request]', err.message);
    return res.status(500).json({ error: 'Failed to process book request.', detail: err.message });
  }
});

// ── GET /books/requests (List active 20-minute sessions for device) ───────────

router.get('/requests', (req, res) => {
  const deviceId = deviceIdFrom(req);
  try {
    const active = getActiveSessionsForDevice(deviceId);
    return res.json({
      active_sessions: active,
      count:           active.length,
    });
  } catch (err) {
    console.error('[/books/requests]', err.message);
    return res.status(500).json({ error: 'Failed to retrieve active sessions.' });
  }
});

// ── GET /books/session/:id (Get session status & remaining time) ─────────────

router.get('/session/:id', (req, res) => {
  const sessionId = req.params.id;
  const session = getActiveSession(sessionId);

  if (!session) {
    return res.status(410).json({
      expired: true,
      error:   'Session has expired or does not exist. (20-minute limit exceeded)',
    });
  }

  return res.json({
    expired:      false,
    session_id:   session.session_id,
    book:         session.book,
    expires_at:   session.expires_at,
    ttl_seconds:  session.ttl_seconds,
    download_url: session.download_url,
  });
});

// ── GET /books/session/:id/download (Expiration-checked download info) ───────

router.get('/session/:id/download', (req, res) => {
  const session = getActiveSession(req.params.id);
  if (!session) {
    return res.status(410).json({
      expired: true,
      error:   'Download link has expired. The 20-minute validity period has ended. Please request the book again.',
    });
  }
  if (req.query.redirect) {
    return res.redirect(302, fileUrlFor(session.session_id));
  }
  return res.json(downloadPayload(session));
});

// ── GET /books/session/:id/file (Expiration-checked PDF stream) ──────────────

router.get('/session/:id/file', async (req, res) => {
  const session = getActiveSession(req.params.id);
  if (!session) {
    return res.status(410).json({
      expired: true,
      error:   'This pass has expired. Please request the book again.',
    });
  }
  const block = premiumBlock(session.book, session.device_id);
  if (block) return res.status(block.status).json(block.body);

  try {
    await streamFile(session.book.file_id, res, session.book.title || 'book');
  } catch (err) {
    console.error('[/books/session/:id/file]', err.message);
    if (err.code === 'FILE_TOO_BIG' && !res.headersSent) {
      return res.status(413).json({
        error:   'File too large',
        message: 'This book is larger than 20 MB, which Telegram does not allow bots to download. It can be enabled by running a local Telegram Bot API server.',
      });
    }
    if (!res.headersSent) {
      return res.status(502).json({ error: 'Could not fetch the file from storage. Please try again.' });
    }
    res.destroy(err);
  }
});

// ── GET /books/search (Search Catalog) ────────────────────────────────────────

router.get('/search', (req, res) => {
  const q      = (req.query.q || '').trim();
  const author = (req.query.author || '').trim();
  const limit  = Math.min(parseInt(req.query.limit  || '20', 10), 100);
  const offset = Math.max(parseInt(req.query.offset || '0',  10), 0);

  if (!q && !author) {
    return res.status(400).json({ error: 'Query parameter "q" or "author" is required.' });
  }

  try {
    const books = searchBooks(q, author || null, limit, offset);
    return res.json({ books, count: books.length, query: q });
  } catch (err) {
    console.error('[/books/search]', err.message);
    return res.status(500).json({ error: 'Search failed.' });
  }
});

// ── GET /books (Browse / List) ───────────────────────────────────────────────

router.get('/', (req, res) => {
  const limit  = Math.min(parseInt(req.query.limit  || '20', 10), 100);
  const offset = Math.max(parseInt(req.query.offset || '0',  10), 0);

  try {
    const books = listBooks(limit, offset);
    const total = getCount();
    return res.json({ books, total, limit, offset });
  } catch (err) {
    console.error('[/books]', err.message);
    return res.status(500).json({ error: 'Failed to fetch books.' });
  }
});

// ── GET /books/:id/download (Open or reuse a pass, return file URL) ──────────
//
// The Android app calls this from the book page. It did not exist before, so
// "Download" always failed with HTTP 404.

router.get('/:id/download', (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) {
    return res.status(400).json({ error: 'Invalid book ID.' });
  }
  const book = getBookById(id);
  if (!book) {
    return res.status(404).json({ error: 'Book not found.' });
  }
  const deviceId = deviceIdFrom(req);
  const block = premiumBlock(book, deviceId);
  if (block) return res.status(block.status).json(block.body);
  try {
    const session = createSession(book, deviceId);
    return res.json(downloadPayload(session));
  } catch (err) {
    console.error('[/books/:id/download]', err.message);
    return res.status(500).json({ error: 'Could not start download.' });
  }
});

// ── POST /books/:id/unlock (Record a Premium unlock) ─────────────────────────
//
// body: { device_id, method: 'upi' | 'ad', ref }
// NOTE: honour-system — the client reports the UPI app's result / ad reward.
// `ref` (UPI txnId / txnRef) is stored so payments can be reconciled against
// the bank statement via GET /admin/unlocks.

router.post('/:id/unlock', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  const book = isNaN(id) ? null : getBookById(id);
  if (!book) return res.status(404).json({ error: 'Book not found.' });
  if (!isPremium(book)) return res.json({ premium: false, unlocked: true, available: true, delivery: 'download' });

  const method = req.body?.method === 'ad' ? 'ad' : req.body?.method === 'upi' ? 'upi' : null;
  if (!method) return res.status(400).json({ error: 'method must be "upi" or "ad".' });

  const contact = String(req.body?.contact || '').trim().slice(0, 100) || null;
  const manual = !LARGE_FILES_ENABLED;
  if (manual && !contact) {
    return res.status(400).json({ error: 'contact_required', message: 'Enter your WhatsApp number or Telegram username so we can send the book.' });
  }

  const deviceId = deviceIdFrom(req);
  const { doc, created } = addUnlock(deviceId, id, method, req.body?.ref, contact, book.title);
  console.log(`[Unlock] #${doc.$loki} book=${id} device=${deviceId.slice(0, 8)}… via ${method} contact=${contact || '-'}${req.body?.restore ? ' (restored)' : ''}`);

  // Notify the admin for new orders (not when the app re-registers an old one after a server restart).
  if (created && manual && !req.body?.restore) {
    notifyAdminOrder(doc, book); // fire-and-forget
  }
  return res.json({ premium: true, unlocked: true, available: true, delivery: manual ? 'manual' : 'download', status: doc.status });
});

// ── GET /books/:id/unlock?device_id= (Premium status for this device) ────────

router.get('/:id/unlock', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const book = isNaN(id) ? null : getBookById(id);
  if (!book) return res.status(404).json({ error: 'Book not found.' });
  const premium = isPremium(book);
  const order = premium ? getUnlock(deviceIdFrom(req), id) : null;
  return res.json({
    premium,
    unlocked:  !premium || !!order,
    available: true,
    delivery:  LARGE_FILES_ENABLED ? 'download' : 'manual',
    status:    order ? order.status : null,
    contact:   order ? order.contact : null,
  });
});

// ── GET /books/:id/cover (Cover image from Telegram's PDF thumbnail) ─────────

router.get('/:id/cover', async (req, res) => {
  const id = parseInt(req.params.id, 10);
  const thumb = isNaN(id) ? null : getThumbFileId(id);
  if (!thumb) return res.status(404).json({ error: 'No cover for this book.' });
  try {
    await streamImage(thumb, res);
  } catch (err) {
    console.error('[/books/:id/cover]', err.message);
    if (!res.headersSent) return res.status(502).json({ error: 'Could not load cover.' });
    res.destroy(err);
  }
});

// ── GET /books/:id (Single Book Metadata) ────────────────────────────────────

router.get('/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (isNaN(id)) {
    return res.status(400).json({ error: 'Invalid book ID.' });
  }

  const book = getBookById(id);
  if (!book) {
    return res.status(404).json({ error: 'Book not found.' });
  }

  return res.json(book);
});

module.exports = router;
module.exports.LARGE_FILES_ENABLED = LARGE_FILES_ENABLED;
