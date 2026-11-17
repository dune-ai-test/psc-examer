/* =========================================================
   Padanam — live model discovery for Cloudflare Pages
   Lists the models available to the server-side provider so
   the browser can show the same list without ever seeing the
   API key. Only ids and display labels are returned.
   ========================================================= */

import {
  scanConfig,
  openAIHeaders,
  openAIModelsEndpoint,
  requestJson,
  apiMessage,
  json
} from './scan.js';

const MAX_MODELS = 250;
const MODEL_TIMEOUT_MS = 20000;
const GEMINI_LIST_URL = 'https://generativelanguage.googleapis.com/v1beta/models';
const GEMINI_PREFERRED = ['gemini-2.5-flash', 'gemini-2.5-pro', 'gemini-2.0-flash'];

export function onRequest(context) {
  if (context.request.method !== 'GET') {
    return json({ error: 'Method not allowed.' }, 405, { Allow: 'GET' });
  }
  return handleModels(context);
}

async function handleModels(context) {
  const config = scanConfig(context.env);
  if (!config.ready) {
    return json({ error: config.error || 'The scanning provider is not configured.' }, 503);
  }

  try {
    const models = config.id === 'gemini'
      ? await geminiModels(config)
      : await openAIModels(config);

    return json({
      ok: true,
      provider: config.id,
      host: config.host,
      model: config.model,
      count: models.length,
      models
    }, 200);
  } catch (err) {
    return json({ error: err.message || 'The provider model list could not be loaded.' }, 502);
  }
}

async function geminiModels(config) {
  const entries = [];
  let pageToken = '';

  for (let page = 0; page < 4; page += 1) {
    const url = new URL(GEMINI_LIST_URL);
    url.searchParams.set('pageSize', '1000');
    if (pageToken) url.searchParams.set('pageToken', pageToken);

    const result = await requestJson(
      url.toString(),
      {
        method: 'GET',
        headers: {
          Accept: 'application/json',
          'x-goog-api-key': config.key
        }
      },
      MODEL_TIMEOUT_MS,
      'Gemini',
      'The Gemini model list took too long. Try again.'
    );

    if (!result.response.ok) throw listError(result.response.status, result.data, 'gemini');

    const batch = result.data && Array.isArray(result.data.models) ? result.data.models : [];
    batch.forEach(function (item) {
      const methods = item && Array.isArray(item.supportedGenerationMethods)
        ? item.supportedGenerationMethods
        : [];
      if (methods.indexOf('generateContent') < 0) return;
      entries.push({
        id: item && item.name,
        label: item && item.displayName
      });
    });

    pageToken = String((result.data && result.data.nextPageToken) || '');
    if (!pageToken) break;
  }

  return finalizeModels(entries, 'gemini');
}

async function openAIModels(config) {
  const endpoint = openAIModelsEndpoint(config.endpoint);
  const headers = Object.assign({ Accept: 'application/json' }, openAIHeaders(config.key));

  const result = await requestJson(
    endpoint,
    {
      method: 'GET',
      headers
    },
    MODEL_TIMEOUT_MS,
    config.host || 'the endpoint',
    'The model list took too long to load. Try again.'
  );

  if (!result.response.ok) throw listError(result.response.status, result.data, 'openai');
  return finalizeModels(modelEntries(result.data), 'openai');
}

function modelEntries(data) {
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data.data)) return data.data;
  if (data && Array.isArray(data.models)) return data.models;
  if (data && Array.isArray(data.result)) return data.result;
  return [];
}

function finalizeModels(entries, provider) {
  const seen = new Set();
  const models = [];

  entries.forEach(function (entry) {
    let id = typeof entry === 'string'
      ? entry
      : String((entry && (entry.id || entry.name || entry.model)) || '');
    id = id.trim().replace(/^models\//, '');
    if (!id || id.length > 200 || /[\u0000-\u001f\u007f]/.test(id)) return;

    const key = id.toLowerCase();
    if (seen.has(key)) return;
    seen.add(key);

    const label = typeof entry === 'object' && entry
      ? cleanLabel(entry.label || entry.displayName || id)
      : cleanLabel(id);
    models.push({ id, label: label || id });
  });

  models.sort(provider === 'gemini' ? compareGemini : compareModels);
  return models.slice(0, MAX_MODELS);
}

function cleanLabel(value) {
  return String(value || '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 120);
}

function compareModels(a, b) {
  return a.id.localeCompare(b.id, 'en', { sensitivity: 'base' });
}

function compareGemini(a, b) {
  const ai = GEMINI_PREFERRED.indexOf(a.id);
  const bi = GEMINI_PREFERRED.indexOf(b.id);
  if (ai !== bi) {
    if (ai < 0) return 1;
    if (bi < 0) return -1;
    return ai - bi;
  }
  return compareModels(a, b);
}

function listError(status, data, provider) {
  const detail = apiMessage(data);

  if (status === 400) {
    return new Error('The provider rejected the model-list request. Check the API key and endpoint' +
      (detail ? ': ' + detail : '.'));
  }
  if (status === 401) {
    return new Error('The API key was rejected. Update the secret in Cloudflare Pages.');
  }
  if (status === 403) {
    return new Error('The provider refused access. Check the key and its API permissions.');
  }
  if (status === 404) {
    return new Error(provider === 'gemini'
      ? 'The Gemini models endpoint was not found. Try again later.'
      : 'The /models endpoint was not found. Check OPENAI_BASE_URL; it usually ends in /v1.');
  }
  if (status === 429) {
    return new Error('The provider’s rate limit was reached. Wait a minute and try again.');
  }
  if (status >= 500) {
    return new Error('The provider is having trouble right now. Try again in a moment.');
  }
  return new Error('The provider returned an error' + (detail ? ': ' + detail : '.'));
}
