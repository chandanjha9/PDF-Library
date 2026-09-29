'use strict';

const express = require('express');
const cors    = require('cors');

const { webhookHandler } = require('./webhook');
const booksRouter        = require('./routes/books');
const { syncChannel }    = require('./sync');

const { ready } = require('./db');

const app = express();

// Behind ngrok / a reverse proxy: trust X-Forwarded-* for req.protocol / req.ip.
app.set('trust proxy', true);

// ── Middleware ───────────────────────────────────────────────────────────────

// Allow cross-origin requests from the Android app (and local development)
app.use(cors());

// Parse JSON bodies (Telegram sends JSON for webhook updates)
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: false }));

// Hold requests until the database has loaded instead of returning 500s.
app.use((_req, _res, next) => { ready.then(() => next(), next); });

// ── Routes ───────────────────────────────────────────────────────────────────

// Telegram webhook — receives channel post updates
app.post('/webhook/telegram', webhookHandler);

// Books REST API
app.use('/books', booksRouter);

// Admin: trigger channel sync
app.post('/admin/sync', async (req, res) => {
  if (!process.env.ADMIN_KEY || req.query.key !== process.env.ADMIN_KEY) {
    return res.status(403).json({ error: 'Forbidden' });
  }
  try {
    const result = await syncChannel(500);
    res.json({ status: 'ok', ...result });
  } catch (err) {
    console.error('[Admin Sync]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// Direct APK download route for phone.
// Previously this served PDFLibrary_NewDesign.apk — an OLD build without the
// bottom-nav shell, Profile screen or app tour. Phones installing from /apk got
// the "missing modules" build. It now serves the freshest build available.
const path = require('path');
const fs   = require('fs');
app.get('/apk', (_req, res) => {
  const root = path.join(__dirname, '..', '..');
  const candidates = [
    process.env.APK_PATH,
    path.join(root, 'android', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk'),
    path.join(root, 'PDFLibrary-debug.apk'),
  ].filter(Boolean).filter(p => fs.existsSync(p));
  if (candidates.length === 0) return res.status(404).json({ error: 'APK not found on server.' });
  const newest = candidates.sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs)[0];
  res.download(newest, 'PDFLibrary.apk');
});

// Health check (+ capabilities the app reads)
const { PREMIUM_MIN_BYTES, listUnlocks, markUnlockSent } = require('./db');
app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    time: new Date().toISOString(),
    large_files: booksRouter.LARGE_FILES_ENABLED,
    premium_min_bytes: PREMIUM_MIN_BYTES,
  });
});

// ── Admin: Premium orders ────────────────────────────────────────────────────
// Open in a browser:  /admin/orders?key=<ADMIN_KEY>
function adminOk(req, res) {
  if (!process.env.ADMIN_KEY || (req.query.key || req.body?.key) !== process.env.ADMIN_KEY) {
    res.status(403).json({ error: 'Forbidden' });
    return false;
  }
  return true;
}

app.get('/admin/unlocks', (req, res) => {
  if (!adminOk(req, res)) return;
  res.json({ unlocks: listUnlocks(500) });
});

app.post('/admin/orders/:id/sent', (req, res) => {
  if (!adminOk(req, res)) return;
  markUnlockSent(parseInt(req.params.id, 10));
  res.redirect(303, `/admin/orders?key=${encodeURIComponent(req.query.key)}`);
});

app.get('/admin/orders', (req, res) => {
  if (!adminOk(req, res)) return;
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const key = encodeURIComponent(req.query.key);
  const rows = listUnlocks(500).map(o => `
    <tr class="${o.status}">
      <td>#${o.id}</td>
      <td>${new Date(o.created_at).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })}</td>
      <td>${esc(o.book_title || '#' + o.book_id)}</td>
      <td><b>${esc(o.contact || '—')}</b></td>
      <td>${o.method === 'upi' ? 'UPI ₹10' : 'Ad'}<br><small>${esc(o.ref || '')}</small></td>
      <td>${o.status === 'sent' ? '✅ Sent' : `<form method="post" action="/admin/orders/${o.id}/sent?key=${key}"><button>Mark sent</button></form>`}</td>
    </tr>`).join('');
  res.type('html').send(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Premium orders</title><style>
body{font-family:system-ui,sans-serif;background:#0E1320;color:#E7EBF3;margin:16px}
table{border-collapse:collapse;width:100%;font-size:14px}td,th{padding:8px;border-bottom:1px solid #2E3850;text-align:left;vertical-align:top}
th{color:#A6B0C3}small{color:#8C98AF;word-break:break-all}tr.pending td{background:#1C2436}
button{background:#9DB6E6;color:#0D1A33;border:0;border-radius:8px;padding:6px 10px;font-weight:600}
</style></head><body><h2>Premium orders</h2><p style="color:#A6B0C3">Pending orders are highlighted. Note: on Render's free plan this list resets when the server restarts — the Telegram notifications are the permanent record.</p>
<table><tr><th>#</th><th>Time</th><th>Book</th><th>Send to</th><th>Payment</th><th>Status</th></tr>${rows || '<tr><td colspan="6">No orders yet.</td></tr>'}</table></body></html>`);
});

// 404 catch-all
app.use((_req, res) => {
  res.status(404).json({ error: 'Not found' });
});

// Global error handler
app.use((err, _req, res, _next) => {
  console.error('[App Error]', err);
  res.status(500).json({ error: 'Internal server error' });
});

module.exports = app;
