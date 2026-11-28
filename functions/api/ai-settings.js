/* =========================================================
   Padanam — admin AI settings
   Stores the scanner provider, model, endpoint and key in
   Neon so the model can change from the admin console without
   a redeploy. The key is write-only: it is never returned to a
   browser or included in any response.
   ========================================================= */

import {
  databaseConfig,
  deleteAiSettings,
  ensureSchema,
  json,
  readAiSettings,
  readBody,
  requireAdmin,
  sameOrigin,
  writeAiSettings
} from '../_shared.js';
import { openAIEndpoint, resolveScanConfig, scanConfig, statusPayload } from './scan.js';

const MAX_BODY_BYTES = 32 * 1024;
const MAX_MODEL = 200;
const MAX_BASE_URL = 500;
const MAX_KEY = 500;

export function onRequest(context) {
  if (context.request.method === 'GET') return handleRead(context);
  if (context.request.method === 'POST') return handleSave(context);
  return json({ error: 'Method not allowed.' }, 405, { Allow: 'GET, POST' });
}

async function handleRead(context) {
  const guard = await requireAdmin(context.request, context.env);
  if (guard.error) return json({ error: guard.error }, guard.status);

  try {
    const config = await resolveScanConfig(context.env);
    return json(Object.assign({ ok: true }, statusPayload(config, true)), 200);
  } catch (err) {
    return json({ error: err && err.message ? err.message : 'The AI settings could not be loaded.' }, 502);
  }
}

async function handleSave(context) {
  if (!sameOrigin(context.request)) {
    return json({ error: 'This endpoint only accepts requests from the app.' }, 403);
  }

  const guard = await requireAdmin(context.request, context.env);
  if (guard.error) return json({ error: guard.error }, guard.status);

  const database = databaseConfig(context.env);
  if (!database.configured) {
    return json({ error: database.error || 'The account database is not configured.' }, 503);
  }

  let body;
  try {
    body = await readBody(context.request, MAX_BODY_BYTES, 'The AI settings');
  } catch (err) {
    return json({ error: err.message || 'Send a JSON request body.' }, 400);
  }

  const action = String(body.action || 'save').trim();

  try {
    await ensureSchema(database);

    if (action === 'reset') {
      await deleteAiSettings(database);
      const config = scanConfig(context.env, null);
      return json(Object.assign({ ok: true, reset: true }, statusPayload(config, true)), 200);
    }

    if (action !== 'save') {
      return json({ error: 'Unknown action. Use "save" or "reset".' }, 400);
    }

    const provider = body.provider === 'openai'
      ? 'openai'
      : body.provider === 'gemini'
        ? 'gemini'
        : '';
    if (!provider) {
      return json({ error: 'Choose the Gemini or OpenAI-compatible provider.' }, 400);
    }

    const model = cleanText(body.model, MAX_MODEL);
    if (!model) {
      return json({ error: 'Choose a model for the scanner.' }, 400);
    }

    let baseUrl = '';
    if (provider === 'openai') {
      baseUrl = cleanText(body.baseUrl, MAX_BASE_URL);
      if (baseUrl) {
        try {
          openAIEndpoint(baseUrl);
        } catch (err) {
          return json({ error: err.message }, 400);
        }
      }
    }

    const existing = await readAiSettings(database);
    let apiKey = cleanKey(body.apiKey);
    if (body.clearKey === true) {
      apiKey = '';
    } else if (!apiKey && existing && existing.provider === provider &&
        typeof existing.apiKey === 'string') {
      apiKey = existing.apiKey.trim();
    }

    const saved = await writeAiSettings(database, {
      provider: provider,
      model: model,
      baseUrl: baseUrl,
      apiKey: apiKey,
      updatedMs: Date.now(),
      updatedBy: guard.user.username || 'admin'
    });

    const config = scanConfig(context.env, saved);
    return json(Object.assign({ ok: true, saved: true }, statusPayload(config, true)), 200);
  } catch (err) {
    return json({ error: err && err.message ? err.message : 'The AI settings could not be saved.' }, 502);
  }
}

function cleanText(value, max) {
  return String(value == null ? '' : value)
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, max);
}

function cleanKey(value) {
  return String(value == null ? '' : value)
    .replace(/[\u0000-\u001F\u007F]/g, '')
    .trim()
    .slice(0, MAX_KEY);
}
