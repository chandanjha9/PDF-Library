'use strict';

/**
 * Database Layer — LokiJS
 *
 * Maintains:
 * 1. `books`: Real Telegram PDF documents synced or received via webhook.
 * 2. `sessions`: Dynamic on-demand book requests with 20-minute TTL.
 */

const path   = require('path');
const fs     = require('fs');
const crypto = require('crypto');
const Loki   = require('lokijs');

const DATA_DIR = path.join(__dirname, '..', 'data');
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// LIBRARY_DB_PATH lets tests / staging point at a different file.
const DB_PATH = process.env.LIBRARY_DB_PATH || path.join(DATA_DIR, 'library.json');

// ── Initialisation ───────────────────────────────────────────────────────────

let booksCollection;
let sessionsCollection;

// Resolves once LokiJS has loaded library.json. Requests arriving before that
// used to fail with "Database not yet initialised." (HTTP 500).
let markReady;
const ready = new Promise(resolve => { markReady = resolve; });

const loki = new Loki(DB_PATH, {
  autosave:         true,
  autosaveInterval: 3000,
  autoload:         true,
  autoloadCallback: initialise,
  persistenceMethod: 'fs',
});

function initialise() {
  booksCollection = loki.getCollection('books');
  if (!booksCollection) {
    booksCollection = loki.addCollection('books', {
      unique:  ['file_id'],
      indices: ['title', 'date_added', 'author'],
    });
  }

  // Remove legacy mock/test items (test_* file_ids) so no static fake books remain
  const fakeBooks = booksCollection.find({ file_id: { $regex: /^test_/i } });
  if (fakeBooks.length > 0) {
    fakeBooks.forEach(b => booksCollection.remove(b));
    console.log(`[DB] Purged ${fakeBooks.length} legacy test book(s).`);
  }

  sessionsCollection = loki.getCollection('sessions');
  if (!sessionsCollection) {
    sessionsCollection = loki.addCollection('sessions', {
      unique:  ['session_id'],
      indices: ['expires_at', 'created_at', 'device_id'],
    });
  }

  console.log(`[DB] Initialised. Real Books: ${booksCollection.count()}, Active Sessions: ${sessionsCollection.count()}`);

  // Start periodic 30-second TTL cleanup (unref: don't keep the process alive on its own)
  setInterval(cleanupExpiredSessions, 30 * 1000).unref();
  markReady();
}

function getBooksCol() {
  if (!booksCollection) throw new Error('Database not yet initialised.');
  return booksCollection;
}

function getSessionsCol() {
  if (!sessionsCollection) throw new Error('Database not yet initialised.');
  return sessionsCollection;
}

// ── Book Helpers ─────────────────────────────────────────────────────────────

function insertBook(book) {
  const col = getBooksCol();

  const existing = col.findOne({ file_id: book.file_id });
  if (existing) {
    return { inserted: false, id: existing.$loki };
  }

  const doc = col.insert({
    title:       book.title       || 'Untitled',
    description: book.description || null,
    file_id:     book.file_id,
    file_size:   book.file_size   || 0,
    date_added:  book.date_added  || Math.floor(Date.now() / 1000),
    cover_url:   book.cover_url   || null,
    author:      book.author      || null,
  });

  return { inserted: true, id: doc.$loki };
}

const { rankBooks } = require('./fuzzy');

function searchBooks(query, author = null, limit = 20, offset = 0) {
  const col = getBooksCol();
  const all = col.chain().data();

  if (!query && !author) {
    const paged = all.slice(offset, offset + limit);
    return paged.map(sanitise);
  }

  const ranked = rankBooks(all, query, author);
  const paged = ranked.slice(offset, offset + limit).map(r => sanitise(r.book));
  return paged;
}

function findBestMatches(query, author = null, limit = 10) {
  const col = getBooksCol();
  const all = col.chain().data();
  return rankBooks(all, query, author).slice(0, limit).map(r => ({
    book: sanitise(r.book),
    score: r.score,
  }));
}

function listBooks(limit = 20, offset = 0) {
  const col = getBooksCol();
  const results = col
    .chain()
    .simplesort('date_added', true)
    .offset(offset)
    .limit(limit)
    .data();
  return results.map(sanitise);
}

function getBookById(id) {
  const col = getBooksCol();
  const doc = col.get(id);
  return doc ? sanitise(doc) : null;
}

function getCount() {
  return getBooksCol().count();
}

// ── 20-Minute Temporary Session Management ────────────────────────────────────

const SESSION_TTL_MS = 20 * 60 * 1000; // 20 minutes in milliseconds

/**
 * Creates a 20-minute temporary access session for a requested book.
 */
function createSession(book, deviceId = 'default') {
  const col = getSessionsCol();
  const now = Date.now();
  const expiresAt = now + SESSION_TTL_MS;
  const sessionId = crypto.randomUUID ? crypto.randomUUID() : crypto.randomBytes(16).toString('hex');
  const cleanBook = book.$loki ? sanitise(book) : book;

  // Check if an active session already exists for this book & device
  const existing = col.findOne({
    book_id:   cleanBook.id,
    device_id: deviceId,
    expires_at: { $gt: now },
  });

  if (existing) {
    return {
      session_id:   existing.session_id,
      book:         existing.book,
      expires_at:   existing.expires_at,
      ttl_seconds:  Math.max(0, Math.floor((existing.expires_at - now) / 1000)),
      download_url: `/books/session/${existing.session_id}/download`,
    };
  }

  const doc = col.insert({
    session_id:   sessionId,
    book_id:      cleanBook.id,
    book:         cleanBook,
    device_id:    deviceId,
    created_at:   now,
    expires_at:   expiresAt,
    download_url: `/books/session/${sessionId}/download`,
  });

  return {
    session_id:   doc.session_id,
    book:         doc.book,
    expires_at:   doc.expires_at,
    ttl_seconds:  Math.floor(SESSION_TTL_MS / 1000),
    download_url: doc.download_url,
  };
}

/**
 * Get active non-expired session by ID.
 */
function getActiveSession(sessionId) {
  const col = getSessionsCol();
  const now = Date.now();
  const doc = col.findOne({ session_id: sessionId });
  if (!doc) return null;
  if (doc.expires_at <= now) {
    col.remove(doc); // Clean up immediately on access if expired
    return null;
  }
  return {
    session_id:   doc.session_id,
    book:         doc.book,
    expires_at:   doc.expires_at,
    ttl_seconds:  Math.max(0, Math.floor((doc.expires_at - now) / 1000)),
    download_url: doc.download_url,
  };
}

/**
 * Get all active sessions for a device.
 */
function getActiveSessionsForDevice(deviceId = 'default') {
  const col = getSessionsCol();
  const now = Date.now();
  const list = col.find({
    device_id:  deviceId,
    expires_at: { $gt: now },
  });

  return list.map(doc => ({
    session_id:   doc.session_id,
    book:         doc.book,
    expires_at:   doc.expires_at,
    ttl_seconds:  Math.max(0, Math.floor((doc.expires_at - now) / 1000)),
    download_url: doc.download_url,
  }));
}

/**
 * Background auto-cleanup of sessions older than 20 minutes.
 */
function cleanupExpiredSessions() {
  if (!sessionsCollection) return;
  const now = Date.now();
  const expired = sessionsCollection.find({ expires_at: { $lte: now } });
  if (expired.length > 0) {
    expired.forEach(s => sessionsCollection.remove(s));
    console.log(`[DB] Auto-cleaned ${expired.length} expired 20-minute session(s).`);
  }
}

// ── Internal helpers ─────────────────────────────────────────────────────────

function sanitise(doc) {
  if (!doc) return null;
  // eslint-disable-next-line no-unused-vars
  const { $loki, meta, ...rest } = doc;
  return { id: $loki, ...rest };
}

module.exports = {
  loki,
  ready,
  insertBook,
  searchBooks,
  findBestMatches,
  listBooks,
  getBookById,
  getCount,
  createSession,
  getActiveSession,
  getActiveSessionsForDevice,
  cleanupExpiredSessions,
  sanitise,
};
