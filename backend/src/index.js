'use strict';

require('dotenv').config();

const app = require('./app');
const { ready } = require('./db');
const { registerWebhook } = require('./webhook');
const { syncChannel } = require('./sync');
const { startKeepAlive } = require('./keepAlive');

const PORT = parseInt(process.env.PORT || '3000', 10);

app.listen(PORT, async () => {
  console.log(`[Server] PDF Library API running on port ${PORT}`);
  console.log(`[Server] Health check: http://localhost:${PORT}/health`);

  // Print the addresses a phone on the same Wi-Fi can use (Profile → Server address).
  const nets = require('os').networkInterfaces();
  const lan = Object.values(nets).flat()
    .filter(n => n && n.family === 'IPv4' && !n.internal)
    .map(n => `http://${n.address}:${PORT}`);
  if (lan.length) {
    console.log('[Server] Phone on same Wi-Fi → enter one of these in the app (Profile → Server address):');
    lan.forEach(u => console.log(`           ${u}`));
  }

  await ready;

  // Register Telegram webhook after server is up
  await registerWebhook();

  // Auto-sync channel on startup to pull existing PDFs
  console.log('[Server] Starting channel sync...');
  try {
    const result = await syncChannel(500);
    console.log(`[Server] Sync complete — ${result.synced} new books added`);
  } catch (err) {
    console.error('[Server] Sync error:', err.message);
  }

  // Start keep-alive pinger to prevent Render free instance from sleeping
  startKeepAlive();
});

