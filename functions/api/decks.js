/* =========================================================
   Padanam — scanned swipe decks
   Reads and writes the optional second Neon database. The
   main library still feeds decks through /api/sync; this
   endpoint only adds cards captured by the scanner.
   ========================================================= */

import {
  deckDatabaseConfig,
  ensureDeckSchema,
  json,
  neonQuery,
  payloadValue,
  readBody,
  requireAdmin,
  rows,
  sameOrigin,
  sessionFromRequest
} from '../_shared.js';

const MAX_BODY_BYTES = 4 * 1024 * 1024;
const MAX_CARDS_PER_SAVE = 500;
const MAX_CARDS_READ = 20000;
const MAX_STEM = 4000;
const MAX_ANSWER = 2000;
const MAX_EXPLANATION = 2000;

export function onRequest(context) {
  if (context.request.method === 'GET') {
    return handleList(context);
  }
  if (context.request.method === 'POST') {
    return handleSave(context);
  }
  return json({ error: 'Method not allowed.' }, 405, { Allow: 'GET, POST' });
}

/* ── Read ──────────────────────────────────────────────── */
async function handleList(context) {
  const session = await sessionFromRequest(context.request, context.env);
  if (!session.user) {
    return json({ error: session.error || 'Sign in to load decks.' }, session.status || 401);
  }

  const config = deckDatabaseConfig(context.env);
  if (!config.configured) {
    return json({
      ok: true,
      configured: false,
      cards: [],
      error: config.error || ''
    });
  }

  try {
    await ensureDeckSchema(config);
    const result = await neonQuery(
      config,
      'SELECT deck_id, card_id, main_topic, sub_topic, language, stem, answer, ' +
        'explanation, updated_ms, payload FROM padanam_deck_cards ' +
        'ORDER BY updated_ms DESC LIMIT $1',
      [MAX_CARDS_READ]
    );
    const cards = rows(result).map(toCard).filter(Boolean);
    return json({
      ok: true,
      configured: true,
      count: cards.length,
      cards: cards
    });
  } catch (err) {
    return json({ error: err && err.message ? err.message : 'Could not read the deck database.' }, 502);
  }
}

function toCard(row) {
  const deckId = cleanId(row.deck_id);
  const cardId = cleanId(row.card_id);
  if (!deckId || !cardId) return null;

  const payload = payloadValue(row.payload) || {};
  const stem = cleanText(row.stem, MAX_STEM);
  const stemMl = cleanText(payload.stemMl, MAX_STEM);
  const answer = cleanText(row.answer, MAX_ANSWER);
  const answerMl = cleanText(payload.answerMl, MAX_ANSWER);
  if ((!stem && !stemMl) || (!answer && !answerMl)) return null;

  const key = 'scan:' + deckId + ':' + cardId;
  if (key.length > 320) return null;

  return {
    key: key,
    deckId: deckId,
    cardId: cardId,
    deckTitle: cleanText(payload.deckTitle, 160),
    mainTopic: cleanText(row.main_topic, 80),
    subTopic: cleanText(row.sub_topic, 120),
    language: row.language === 'ml' ? 'ml' : 'en',
    stem: stem || stemMl,
    answer: answer || answerMl,
    explanation: cleanText(row.explanation, MAX_EXPLANATION),
    stemMl: stemMl,
    answerMl: answerMl,
    explanationMl: cleanText(payload.explanationMl, MAX_EXPLANATION),
    updatedAt: isoFromMs(row.updated_ms)
  };
}

/* ── Save ──────────────────────────────────────────────── */
async function handleSave(context) {
  const config = deckDatabaseConfig(context.env);
  if (!config.configured) {
    return json({
      error: config.error || 'Set DECK_NEON_CONNECTION_STRING in Cloudflare Pages to save scanned decks.'
    }, 503);
  }
  if (!sameOrigin(context.request)) {
    return json({ error: 'This endpoint only accepts requests from the app.' }, 403);
  }

  const admin = await requireAdmin(context.request, context.env);
  if (!admin.user) {
    return json({ error: admin.error || 'Admin access is required.' }, admin.status || 403);
  }

  let body;
  try {
    body = await readBody(context.request, MAX_BODY_BYTES, 'The deck payload');
  } catch (err) {
    return json({ error: err.message || 'Send a JSON request body.' }, 400);
  }

  if (body.action && body.action !== 'save') {
    return json({ error: 'Unknown action. Use "save".' }, 400);
  }

  let deck;
  try {
    deck = validateDeck(body);
  } catch (err) {
    return json({ error: err.message || 'The deck payload is invalid.' }, 400);
  }

  try {
    await ensureDeckSchema(config);
    await neonQuery(
      config,
      'INSERT INTO padanam_deck_cards ' +
        '(deck_id, card_id, main_topic, sub_topic, language, stem, answer, explanation, updated_ms, payload) ' +
        'SELECT $1, item->>\'cardId\', item->>\'mainTopic\', item->>\'subTopic\', item->>\'language\', ' +
        'item->>\'stem\', item->>\'answer\', item->>\'explanation\', (item->>\'updatedMs\')::bigint, item->\'payload\' ' +
        'FROM jsonb_array_elements($2::jsonb) AS item ' +
        'ON CONFLICT (deck_id, card_id) DO UPDATE SET ' +
        'main_topic = EXCLUDED.main_topic, sub_topic = EXCLUDED.sub_topic, ' +
        'language = EXCLUDED.language, stem = EXCLUDED.stem, answer = EXCLUDED.answer, ' +
        'explanation = EXCLUDED.explanation, updated_ms = EXCLUDED.updated_ms, ' +
        'payload = EXCLUDED.payload ' +
        'WHERE EXCLUDED.updated_ms >= padanam_deck_cards.updated_ms',
      [deck.deckId, JSON.stringify(deck.items)]
    );
    return json({
      ok: true,
      deckId: deck.deckId,
      saved: deck.items.length,
      title: deck.title
    });
  } catch (err) {
    return json({ error: err && err.message ? err.message : 'Could not save to the deck database.' }, 502);
  }
}

function validateDeck(body) {
  const title = cleanText(body.title, 160) || 'Scanned deck';
  const mainTopic = cleanText(body.mainTopic, 80);
  const subTopic = cleanText(body.subTopic, 120) || title;
  const language = body.language === 'ml' ? 'ml' : 'en';
  const questions = Array.isArray(body.questions)
    ? body.questions
    : Array.isArray(body.cards) ? body.cards : [];

  if (!questions.length) throw new Error('Add at least one scanned question.');
  if (questions.length > MAX_CARDS_PER_SAVE) {
    throw new Error('Save at most ' + MAX_CARDS_PER_SAVE + ' scanned cards at once.');
  }

  const now = Date.now();
  const deckId = cleanId(body.deckId) || ('scan_' + now.toString(36) + '_' + randomToken(6));
  const used = Object.create(null);
  const items = [];

  questions.forEach(function (question, index) {
    if (!question || typeof question !== 'object') {
      throw new Error('One of the scanned cards is invalid.');
    }

    const stem = cleanText(
      question.stem || question.question || question.questionEn || question.stemEn,
      MAX_STEM
    );
    const stemMl = cleanText(question.stemMl || question.questionMl, MAX_STEM);
    const answer = cleanText(
      question.answer || question.answerText || question.correctAnswer || question.answerEn,
      MAX_ANSWER
    );
    const answerMl = cleanText(question.answerMl || question.correctAnswerMl, MAX_ANSWER);
    if ((!stem && !stemMl) || (!answer && !answerMl)) {
      throw new Error('Every scanned card needs a question and an answer.');
    }

    let cardId = cleanId(question.cardId || question.id);
    if (!cardId) cardId = 'c_' + (index + 1) + '_' + randomToken(5);
    let unique = cardId;
    let suffix = 2;
    while (used[unique]) {
      unique = cardId.slice(0, 54) + '_' + suffix;
      suffix += 1;
    }
    used[unique] = true;

    const explanation = cleanText(question.explanation || question.explanationEn, MAX_EXPLANATION);
    const explanationMl = cleanText(question.explanationMl, MAX_EXPLANATION);

    items.push({
      cardId: unique,
      mainTopic: cleanText(question.mainTopic, 80) || mainTopic,
      subTopic: cleanText(question.subTopic, 120) || subTopic,
      language: language,
      stem: stem || stemMl,
      answer: answer || answerMl,
      explanation: explanation,
      updatedMs: now,
      payload: {
        deckTitle: title,
        stemMl: stemMl,
        answerMl: answerMl,
        explanationMl: explanationMl,
        source: 'scan'
      }
    });
  });

  return {
    deckId: deckId,
    title: title,
    items: items
  };
}

/* ── Helpers ───────────────────────────────────────────── */
function cleanText(value, maxLength) {
  const text = String(value === null || value === undefined ? '' : value)
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  return text.slice(0, maxLength || 200);
}

function cleanId(value) {
  const text = String(value === null || value === undefined ? '' : value)
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, '');
  return /^[a-z0-9][a-z0-9_-]{0,60}$/.test(text) ? text : '';
}

function randomToken(length) {
  const bytes = new Uint8Array(Math.max(4, length || 6));
  crypto.getRandomValues(bytes);
  return Array.from(bytes).map(function (byte) {
    return byte.toString(36).padStart(2, '0');
  }).join('').slice(0, length || 6);
}

function isoFromMs(value) {
  const ms = Number(value);
  if (!Number.isFinite(ms) || ms <= 0) return '';
  return new Date(ms).toISOString();
}
