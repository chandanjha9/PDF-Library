'use strict';

const fetch = globalThis.fetch || require('node-fetch');

/**
 * Keep-alive pinger for Render and free-tier hosts.
 * Periodically sends an HTTP GET request to the service's /health endpoint
 * to prevent the free instance from spinning down due to inactivity.
 */
function startKeepAlive() {
  if (process.env.KEEP_ALIVE_ENABLED === 'false') {
    console.log('[KeepAlive] Disabled via KEEP_ALIVE_ENABLED=false');
    return;
  }

  // Determine external URL:
  // Render sets RENDER_EXTERNAL_URL automatically (e.g. https://pdf-library-backend.onrender.com).
  // Can also be overridden by KEEP_ALIVE_URL, APP_URL, or inferred from WEBHOOK_URL.
  let rawUrl = process.env.KEEP_ALIVE_URL ||
               process.env.RENDER_EXTERNAL_URL ||
               process.env.APP_URL;

  if (!rawUrl && process.env.WEBHOOK_URL) {
    try {
      const parsed = new URL(process.env.WEBHOOK_URL);
      rawUrl = `${parsed.protocol}//${parsed.host}`;
    } catch {
      // ignore
    }
  }

  if (!rawUrl) {
    console.log('[KeepAlive] No public URL found (set RENDER_EXTERNAL_URL or KEEP_ALIVE_URL). Keep-alive inactive.');
    return;
  }

  const pingUrl = `${rawUrl.replace(/\/+$/, '')}/health`;
  const intervalMs = parseInt(process.env.KEEP_ALIVE_INTERVAL_MS || '60000', 10); // default: 1 minute (60s)

  console.log(`[KeepAlive] Scheduled to ping ${pingUrl} every ${Math.round(intervalMs / 1000)}s`);

  // Wait 30 seconds after server start before triggering the first ping
  setTimeout(() => {
    ping();
    setInterval(ping, intervalMs);
  }, 30000);

  async function ping() {
    try {
      const res = await fetch(pingUrl, {
        headers: { 'User-Agent': 'PDFLibrary-KeepAlive/1.0' }
      });
      if (res.ok) {
        console.log(`[KeepAlive] Ping successful (${res.status}) at ${new Date().toISOString()}`);
      } else {
        console.warn(`[KeepAlive] Ping returned status ${res.status}`);
      }
    } catch (err) {
      console.warn(`[KeepAlive] Ping error: ${err.message}`);
    }
  }
}

module.exports = { startKeepAlive };
