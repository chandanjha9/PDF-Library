'use strict';

/**
 * Channel Sync — Scrapes existing PDF & book documents from the Telegram channel.
 */

const fetch = require('node-fetch');
const { insertBook } = require('./db');

const BOT_TOKEN    = process.env.BOT_TOKEN;
const CHANNEL_ID   = process.env.CHANNEL_ID || '@ministrore';
const TELEGRAM_API_BASE = (process.env.TELEGRAM_API_BASE || 'https://api.telegram.org').replace(/\/+$/, '');
const TELEGRAM_API = `${TELEGRAM_API_BASE}/bot${BOT_TOKEN}`;

/**
 * Try to inspect a single message from the channel using forwardMessage into channel itself,
 * capturing the metadata, and immediately deleting the temporary forwarded message.
 */
async function tryInspectMessage(messageId) {
  try {
    const res = await fetch(`${TELEGRAM_API}/forwardMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chat_id: CHANNEL_ID,
        from_chat_id: CHANNEL_ID,
        message_id: messageId,
        disable_notification: true,
      }),
    });
    const data = await res.json();
    if (data.ok) {
      // Immediately delete the temp forwarded message to keep channel clean
      await fetch(`${TELEGRAM_API}/deleteMessage`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          chat_id: CHANNEL_ID,
          message_id: data.result.message_id,
        }),
      });
      return data.result;
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * Extract book / document info from a message.
 */
function extractPdfFromMessage(msg) {
  const doc = msg.document;
  if (!doc) return null;

  const fileName = doc.file_name || 'untitled.pdf';
  const cleanTitle = (msg.caption || '')
    .split('\n')[0]
    .trim() || fileName.replace(/\.[a-zA-Z0-9]+$/i, '').replace(/[_.-]+/g, ' ').trim();

  const lines = (msg.caption || '').split('\n');
  const description = lines.slice(1).join('\n').trim() || `File: ${fileName} (${(doc.file_size / (1024 * 1024)).toFixed(2)} MB)`;

  return {
    title:       cleanTitle,
    description: description,
    file_id:     doc.file_id,
    file_size:   doc.file_size || 0,
    date_added:  msg.forward_date || msg.date || Math.floor(Date.now() / 1000),
    cover_url:   null,
    author:      null,
  };
}

/**
 * Sync messages from the channel.
 */
async function syncChannel(maxMessages = 200) {
  if (!BOT_TOKEN || !CHANNEL_ID) {
    throw new Error('BOT_TOKEN and CHANNEL_ID must be set');
  }

  console.log(`[Sync] Starting sync from channel ${CHANNEL_ID} (scanning up to ${maxMessages} message IDs)...`);

  let synced = 0;
  let skipped = 0;
  let errors = 0;
  let consecutiveEmpty = 0;

  for (let msgId = 1; msgId <= maxMessages; msgId++) {
    const msg = await tryInspectMessage(msgId);

    if (!msg) {
      consecutiveEmpty++;
      if (consecutiveEmpty > 25 && msgId > 40) {
        console.log(`[Sync] Reached end of messages at ID ${msgId}`);
        break;
      }
      continue;
    }

    consecutiveEmpty = 0;

    const book = extractPdfFromMessage(msg);
    if (!book) {
      skipped++;
      continue;
    }

    try {
      const result = insertBook(book);
      if (result.inserted) {
        synced++;
        console.log(`[Sync] ✅ Indexed: "${book.title}" (File: ${book.file_id.slice(0, 15)}...)`);
      } else {
        skipped++;
      }
    } catch (err) {
      errors++;
      console.error(`[Sync] ❌ Error inserting msg #${msgId}:`, err.message);
    }

    // Rate limit ~ 100ms
    await new Promise(r => setTimeout(r, 120));
  }

  console.log(`[Sync] Complete. Synced: ${synced}, Skipped: ${skipped}, Errors: ${errors}`);
  return { synced, skipped, errors };
}

// ── Throttled, single-flight sync for on-demand use ─────────────────────────
//
// /books/request used to run a full 50-message channel scan on *every* miss,
// adding several seconds per request and forwarding/deleting 50 messages in the
// channel each time. Now concurrent callers share one run and repeat scans are
// skipped within the cooldown window.

const ON_DEMAND_COOLDOWN_MS = parseInt(process.env.SYNC_COOLDOWN_MS || String(5 * 60 * 1000), 10);
let inFlight = null;
let lastRunAt = 0;

async function syncChannelThrottled(maxMessages = 50) {
  if (inFlight) return inFlight;
  if (Date.now() - lastRunAt < ON_DEMAND_COOLDOWN_MS) {
    return { synced: 0, skipped: 0, errors: 0, throttled: true };
  }
  lastRunAt = Date.now();
  inFlight = syncChannel(maxMessages).finally(() => { inFlight = null; });
  return inFlight;
}

module.exports = { syncChannel, syncChannelThrottled };
