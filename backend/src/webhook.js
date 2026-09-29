'use strict';

/**
 * Telegram Webhook Handler
 *
 * Registers the webhook with Telegram on startup and handles incoming
 * channel post updates. When a new PDF document is posted to the channel,
 * its metadata is extracted and stored in the database.
 */

const fetch = require('node-fetch');
const { insertBook } = require('./db');

const BOT_TOKEN     = process.env.BOT_TOKEN;
const WEBHOOK_URL   = process.env.WEBHOOK_URL;
const WEBHOOK_SECRET = process.env.WEBHOOK_SECRET || '';

// Override with a local Bot API server (https://github.com/tdlib/telegram-bot-api)
// to lift the 20 MB download limit, or for tests.
const TELEGRAM_API_BASE = (process.env.TELEGRAM_API_BASE || 'https://api.telegram.org').replace(/\/+$/, '');
const TELEGRAM_API = `${TELEGRAM_API_BASE}/bot${BOT_TOKEN}`;

// ── Webhook Registration ─────────────────────────────────────────────────────

/**
 * Register (or re-register) the webhook with Telegram.
 * Call once at server startup.
 */
async function registerWebhook() {
  if (!BOT_TOKEN || BOT_TOKEN === 'YOUR_BOT_TOKEN_HERE') {
    console.warn('[Webhook] BOT_TOKEN not set — skipping webhook registration.');
    return;
  }
  if (!WEBHOOK_URL || WEBHOOK_URL === 'https://your-domain.com') {
    console.warn('[Webhook] WEBHOOK_URL not set — skipping webhook registration.');
    return;
  }

  const webhookEndpoint = `${WEBHOOK_URL}/webhook/telegram`;

  const body = {
    url: webhookEndpoint,
    allowed_updates: ['channel_post'],
    drop_pending_updates: true,
  };

  if (WEBHOOK_SECRET) {
    body.secret_token = WEBHOOK_SECRET;
  }

  try {
    const res = await fetch(`${TELEGRAM_API}/setWebhook`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await res.json();
    if (data.ok) {
      console.log(`[Webhook] Registered at ${webhookEndpoint}`);
    } else {
      console.error('[Webhook] Registration failed:', data.description);
    }
  } catch (err) {
    console.error('[Webhook] Network error during registration:', err.message);
  }
}

// ── Telegram File URL ────────────────────────────────────────────────────────

/**
 * Resolve a Telegram file_id into a download URL.
 * Returns { file_path, url } or throws on error.
 *
 * SECURITY: the returned URL contains the bot token. It must only be used
 * server-side (see streamFile) — never sent to clients.
 */
async function resolveFileUrl(file_id) {
  const res = await fetch(`${TELEGRAM_API}/getFile?file_id=${encodeURIComponent(file_id)}`);
  const data = await res.json();
  if (!data.ok) {
    const err = new Error(`getFile failed: ${data.description}`);
    // Standard Bot API refuses files > 20 MB ("Bad Request: file is too big").
    if (/too big/i.test(data.description || '')) err.code = 'FILE_TOO_BIG';
    throw err;
  }
  const file_path = data.result.file_path;
  const url = `${TELEGRAM_API_BASE}/file/bot${BOT_TOKEN}/${file_path}`;
  return { file_path, url };
}

/**
 * Stream a Telegram file to an Express response without exposing the token.
 * Handles upstream errors and client disconnects.
 */
async function streamFile(file_id, res, filename = 'book.pdf') {
  const { url } = await resolveFileUrl(file_id);
  const upstream = await fetch(url);
  if (!upstream.ok) {
    throw new Error(`Telegram file download failed: HTTP ${upstream.status}`);
  }

  const safeName = filename.replace(/[^\w\s.\-()]+/g, '').trim().slice(0, 120) || 'book';
  res.setHeader('Content-Type', 'application/pdf');
  res.setHeader('Content-Disposition', `attachment; filename="${safeName.endsWith('.pdf') ? safeName : safeName + '.pdf'}"`);
  res.setHeader('Cache-Control', 'private, no-store');
  const len = upstream.headers.get('content-length');
  if (len) res.setHeader('Content-Length', len);

  await new Promise((resolve, reject) => {
    const body = upstream.body;
    res.on('close', () => { body.destroy(); resolve(); });
    body.on('error', reject);
    body.pipe(res).on('finish', resolve);
  });
}

// ── Update Parser ────────────────────────────────────────────────────────────

/**
 * Parse a Telegram update object and persist any new PDF book.
 * Handles both `channel_post` and `message` update types.
 *
 * @param {object} update - Raw Telegram update JSON
 */
function handleUpdate(update) {
  // Accept both channel_post (from channels) and message (for testing via DMs)
  const post = update.channel_post || update.message;
  if (!post) return;

  const doc = post.document;
  if (!doc) return;

  // Only process PDF documents
  const mime = doc.mime_type || '';
  if (mime !== 'application/pdf' && !doc.file_name?.toLowerCase().endsWith('.pdf')) {
    return;
  }

  // Extract title from caption, falling back to the file name
  const caption  = post.caption || '';
  const fileName = doc.file_name || 'untitled.pdf';
  const title    = caption.split('\n')[0].trim() || fileName.replace(/\.pdf$/i, '').trim();

  // Everything after the first line of the caption is treated as description
  const lines = caption.split('\n');
  const description = lines.slice(1).join('\n').trim() || null;

  const book = {
    title,
    description,
    file_id:    doc.file_id,
    file_size:  doc.file_size || 0,
    date_added: post.date || Math.floor(Date.now() / 1000),
    cover_url:  null,
    author:     null,
  };

  const result = insertBook(book);
  if (result.inserted) {
    console.log(`[Webhook] New book indexed: "${title}" (id=${result.id})`);
  } else {
    console.log(`[Webhook] Duplicate skipped: "${title}"`);
  }
}

// ── Express Middleware ───────────────────────────────────────────────────────

/**
 * Express route handler for POST /webhook/telegram
 */
function webhookHandler(req, res) {
  // Optional secret token validation
  if (WEBHOOK_SECRET) {
    const incoming = req.headers['x-telegram-bot-api-secret-token'];
    if (incoming !== WEBHOOK_SECRET) {
      console.warn('[Webhook] Invalid secret token — rejected request.');
      return res.status(403).json({ error: 'Forbidden' });
    }
  }

  try {
    handleUpdate(req.body);
  } catch (err) {
    console.error('[Webhook] Error processing update:', err.message);
  }

  // Always respond 200 immediately so Telegram doesn't retry
  res.status(200).json({ ok: true });
}

module.exports = { registerWebhook, resolveFileUrl, streamFile, webhookHandler };
