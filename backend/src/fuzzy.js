'use strict';

/**
 * Smart Fuzzy & Semantic Matching for Books and PDFs.
 * Supports English, Hindi, and alphanumeric comic/book titles.
 */

function normalize(str) {
  if (!str) return '';
  return str
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[._\-/\\]+/g, ' ')
    .replace(/[^\w\s\u0900-\u097F]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function getTokens(str) {
  return normalize(str).split(' ').filter(w => w.length > 1);
}

function levenshteinDistance(s1, s2) {
  const m = s1.length;
  const n = s2.length;
  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));

  for (let i = 0; i <= m; i++) dp[i][0] = i;
  for (let j = 0; j <= n; j++) dp[0][j] = j;

  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      const cost = s1[i - 1] === s2[j - 1] ? 0 : 1;
      dp[i][j] = Math.min(
        dp[i - 1][j] + 1,
        dp[i][j - 1] + 1,
        dp[i - 1][j - 1] + cost
      );
    }
  }
  return dp[m][n];
}

function similarity(s1, s2) {
  const maxLen = Math.max(s1.length, s2.length);
  if (maxLen === 0) return 1.0;
  const dist = levenshteinDistance(s1, s2);
  return 1.0 - dist / maxLen;
}

/**
 * Score one query token against a list of candidate tokens (0..1).
 *
 * The previous version treated *any* containment as a 0.8 match, so a 2-letter
 * fragment like "no" inside "nonexistent" matched, and gibberish requests were
 * granted a pass to a random book. Containment now needs meaningful length.
 */
function tokenScore(qToken, targetTokens) {
  let best = 0;
  for (const t of targetTokens) {
    if (t === qToken) return 1.0;
    if (qToken.length >= 3 && t.startsWith(qToken)) {
      best = Math.max(best, 0.85);                       // "chem" → "chemistry"
    } else if (qToken.length >= 4 && t.includes(qToken)) {
      best = Math.max(best, 0.6);                        // "man" won't match "german"
    } else if (t.length >= 4 && qToken.includes(t) && t.length / qToken.length >= 0.6) {
      best = Math.max(best, 0.6);                        // "spiderman" ⊃ "spider"
    } else if (qToken.length >= 4 && t.length >= 4) {
      const sim = similarity(qToken, t);                 // typos: "phisics" ~ "physics"
      if (sim >= 0.75) best = Math.max(best, sim * 0.8);
    }
  }
  return best;
}

/**
 * Calculate a match score between 0.0 and 1.0.
 * Title/author tokens count fully; description tokens count at 70%.
 */
function scoreBook(query, book) {
  const normQuery = normalize(query);
  const normTitle = normalize(book.title || '');
  const normDesc  = normalize(book.description || '');

  if (!normQuery) return 1.0;

  // 1. Exact or direct substring match
  if (normTitle === normQuery) return 1.0;
  if (normQuery.length >= 3 && normTitle.includes(normQuery)) return 0.95;
  if (normQuery.length >= 5 && normDesc.includes(normQuery)) return 0.8;

  // 2. Token matching
  const queryTokens = getTokens(query);
  if (queryTokens.length === 0) return 0.0;

  const titleTokens = getTokens(`${book.title || ''} ${book.author || ''}`);
  const descTokens  = getTokens(book.description || '');

  let matched = 0;
  let total = 0;
  for (const q of queryTokens) {
    const s = Math.max(tokenScore(q, titleTokens), tokenScore(q, descTokens) * 0.7);
    if (s >= 0.6) matched++;
    total += s;
  }

  const ratio = total / queryTokens.length;
  if (matched === queryTokens.length) {
    return Math.min(0.9, 0.5 + ratio * 0.4);
  }
  return ratio * (matched / queryTokens.length);
}

/**
 * Rank all books against query and author.
 */
function rankBooks(books, query, author = null) {
  const scored = [];

  for (const book of books) {
    let score = scoreBook(query, book);

    // If author specified, adjust score
    if (author && author.trim()) {
      const authorScore = scoreBook(author, { title: book.author || '', description: book.description || '' });
      if (authorScore > 0.5) {
        score = Math.min(1.0, score + 0.2);
      }
    }

    if (score > 0.15) {
      scored.push({ book, score });
    }
  }

  scored.sort((a, b) => b.score - a.score);
  return scored;
}

module.exports = {
  normalize,
  tokenScore,
  getTokens,
  similarity,
  scoreBook,
  rankBooks,
};
