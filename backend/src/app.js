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

// Hold requests until the database has loaded instead of returning 500s.
app.use((_req, _res, next) => { ready.then(() => next(), next); });

// ── Routes ───────────────────────────────────────────────────────────────────

// Telegram webhook — receives channel post updates
app.post('/webhook/telegram', webhookHandler);

// Books REST API
app.use('/books', booksRouter);

// Admin: trigger channel sync
app.post('/admin/sync', async (_req, res) => {
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

// Health check
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', time: new Date().toISOString() });
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
