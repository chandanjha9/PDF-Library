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
} = require('../db');
const { streamFile } = require('../webhook');
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

// Minimum fuzzy score for granting a pass. Below this the user gets suggestions
// instead of a pass to a book they didn't ask for.
const GRANT_MIN_SCORE = parseFloat(process.env.GRANT_MIN_SCORE || '0.5');

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

  try {
    await streamFile(session.book.file_id, res, session.book.title || 'book');
  } catch (err) {
    console.error('[/books/session/:id/file]', err.message);
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
  try {
    const session = createSession(book, deviceIdFrom(req));
    return res.json(downloadPayload(session));
  } catch (err) {
    console.error('[/books/:id/download]', err.message);
    return res.status(500).json({ error: 'Could not start download.' });
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
