/* =========================================================
   Padanam — secure scanner proxy for Cloudflare Pages
   The API key lives in the Neon AI settings row (or in Pages
   Variables & Secrets as a fallback), never in the browser
   bundle. The app posts compressed pages here.
   ========================================================= */

import {
  databaseConfig,
  ensureSchema,
  readAiSettings,
  requireAdmin
} from '../_shared.js';

const MAX_IMAGES = 8;
const MAX_IMAGE_BASE64 = 6 * 1024 * 1024;
const MAX_TOTAL_BASE64 = 20 * 1024 * 1024;
const DEFAULT_GEMINI_MODEL = 'gemini-2.5-flash';
const DEFAULT_OPENAI_MODEL = 'gpt-4o-mini';
const DEFAULT_OPENAI_URL = 'https://api.openai.com/v1';

/* The same fixed taxonomy used by the web app and the Android app. */
export const MAIN_TOPICS = [
  'Biology & Public Health',
  'Constitution',
  'Current Affairs',
  'Economics',
  'English',
  'Indian History',
  'Information Technology',
  'Kerala Governance',
  'Kerala - World Geography',
  'Kerala - World History',
  'Malayalam',
  'Mathematics & Mental Ability',
  'Physical Science',
  'Indian Geography'
];

export async function onRequest(context) {
  if (context.request.method === 'GET') {
    const config = await resolveScanConfig(context.env);
    const admin = await requireAdmin(context.request, context.env).catch(function () {
      return { user: null };
    });
    return json(statusPayload(config, !!admin.user), 200);
  }
  if (context.request.method === 'POST') {
    return handleScan(context);
  }
  return json({ error: 'Method not allowed.' }, 405, { Allow: 'GET, POST' });
}

async function handleScan(context) {
  const config = await resolveScanConfig(context.env);

  let body;
  try {
    body = await context.request.json();
  } catch (err) {
    return json({ error: 'Send a JSON request body.' }, 400);
  }

  const action = body && body.action;
  if (action !== 'test' && action !== 'extract' && action !== 'classify') {
    return json({ error: 'Unknown action. Use "test", "extract" or "classify".' }, 400);
  }

  if (!config.ready) {
    return json({ error: config.error || 'The scanning provider is not configured.' }, 503);
  }

  if (action === 'test') {
    try {
      const text = await runTest(config);
      return json({
        ok: true,
        text,
        provider: config.id,
        model: config.model,
        host: config.host
      });
    } catch (err) {
      return json({ error: err.message || 'The provider could not be reached.' }, 502);
    }
  }

  if (action === 'classify') {
    try {
      const topic = await classifyTopic(config, body);
      return json({
        ok: true,
        mainTopic: topic.mainTopic,
        subTopic: topic.subTopic,
        provider: config.id,
        model: config.model,
        host: config.host
      });
    } catch (err) {
      return json({ error: err.message || 'The provider could not classify this set.' }, 502);
    }
  }

  const prompt = typeof body.prompt === 'string' ? body.prompt.trim() : '';
  if (!prompt) return json({ error: 'The extraction prompt is missing.' }, 400);
  if (prompt.length > 20000) return json({ error: 'The extraction prompt is too long.' }, 400);

  let images;
  try {
    images = validateImages(body.images);
  } catch (err) {
    return json({ error: err.message }, 400);
  }

  try {
    const text = await runExtract(config, prompt, images);
    return json({
      ok: true,
      text,
      provider: config.id,
      model: config.model,
      host: config.host
    });
  } catch (err) {
    return json({ error: err.message || 'The provider could not read these pages.' }, 502);
  }
}

/**
 * Loads the AI settings saved by the administrator in Neon and merges them
 * with the optional Cloudflare environment variables. The database row wins;
 * env vars keep existing deployments working until the admin saves settings.
 */
export async function resolveScanConfig(env) {
  let stored = null;
  const database = databaseConfig(env);
  if (database.configured) {
    try {
      await ensureSchema(database);
      stored = await readAiSettings(database);
    } catch (err) {
      stored = null;
    }
  }
  return scanConfig(env, stored);
}

export function scanConfig(env, stored) {
  stored = stored && typeof stored === 'object' ? stored : null;
  const storedProvider = stored && (stored.provider === 'openai' || stored.provider === 'gemini')
    ? stored.provider
    : '';
  const provider = String(storedProvider || env.SCAN_PROVIDER || 'gemini').trim().toLowerCase();
  const source = storedProvider ? 'database' : 'environment';
  const updatedMs = storedProvider && Number(stored.updatedMs) ? Number(stored.updatedMs) : 0;
  const updatedBy = storedProvider ? String(stored.updatedBy || '') : '';
  const savedKey = storedProvider && stored && typeof stored.apiKey === 'string'
    ? stored.apiKey.trim()
    : '';

  if (provider === 'openai') {
    const key = savedKey || String(env.OPENAI_API_KEY || '').trim();
    const model = String((storedProvider && stored.model) || env.OPENAI_MODEL || '').trim() ||
      DEFAULT_OPENAI_MODEL;
    const baseUrl = String((storedProvider && stored.baseUrl) || env.OPENAI_BASE_URL || '').trim() ||
      DEFAULT_OPENAI_URL;
    let endpoint = '';
    let error = '';

    try {
      endpoint = openAIEndpoint(baseUrl);
    } catch (err) {
      error = err.message;
    }

    const host = endpoint ? endpointHost(endpoint) : '';
    let ready = !!endpoint && !!model;
    if (ready && /(^|\.)openai\.com$/i.test(host) && !key) {
      ready = false;
      error = 'Add the OpenAI API key in Admin console → AI scanning, or set OPENAI_API_KEY in Cloudflare Pages.';
    }

    return {
      id: 'openai',
      ready,
      model,
      host,
      endpoint,
      key,
      baseUrl,
      source,
      updatedMs,
      updatedBy,
      error: ready ? '' : (error || 'Add the OpenAI-compatible base URL and model in Admin console → AI scanning.')
    };
  }

  if (provider !== 'gemini') {
    return {
      id: provider || 'gemini',
      ready: false,
      model: '',
      host: '',
      endpoint: '',
      key: '',
      baseUrl: '',
      source,
      updatedMs,
      updatedBy,
      error: 'SCAN_PROVIDER must be "gemini" or "openai".'
    };
  }

  const key = savedKey || String(env.GEMINI_API_KEY || '').trim();
  const model = String((storedProvider && stored.model) || env.GEMINI_MODEL || '').trim() ||
    DEFAULT_GEMINI_MODEL;
  return {
    id: 'gemini',
    ready: !!key,
    model,
    host: 'generativelanguage.googleapis.com',
    endpoint: '',
    key,
    baseUrl: '',
    source,
    updatedMs,
    updatedBy,
    error: key ? '' : 'Add the Gemini API key in Admin console → AI scanning, or set GEMINI_API_KEY in Cloudflare Pages.'
  };
}

export function statusPayload(config, admin) {
  const payload = {
    ready: config.ready,
    provider: config.id,
    model: config.model,
    host: config.host,
    error: config.error,
    source: config.source || 'environment'
  };
  if (admin) {
    payload.baseUrl = config.baseUrl || '';
    payload.hasKey = !!config.key;
    payload.updatedMs = config.updatedMs || 0;
    payload.updatedBy = config.updatedBy || '';
  }
  return payload;
}

async function runTest(config) {
  if (config.id === 'gemini') {
    const result = await requestJson(
      'https://generativelanguage.googleapis.com/v1beta/models/' +
        encodeURIComponent(config.model) + ':generateContent',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': config.key
        },
        body: JSON.stringify({
          contents: [{ role: 'user', parts: [{ text: 'Reply with exactly: OK' }] }],
          generationConfig: { temperature: 0, maxOutputTokens: 12 }
        })
      },
      30000,
      'Gemini'
    );

    if (!result.response.ok) throw providerError(result.response.status, result.data, 'Gemini');
    const text = geminiText(result.data).trim();
    if (!text) throw new Error('Gemini answered, but the reply was empty. Check that the model is available.');
    return text;
  }

  const result = await requestJson(
    config.endpoint,
    {
      method: 'POST',
      headers: openAIHeaders(config.key),
      body: JSON.stringify({
        model: config.model,
        messages: [{ role: 'user', content: 'Reply with exactly: OK' }]
      })
    },
    30000,
    config.host || 'the endpoint'
  );

  if (!result.response.ok) throw providerError(result.response.status, result.data, 'endpoint');
  const text = openAIText(result.data).trim();
  if (!text) throw new Error('The endpoint answered, but the reply was empty. Check that the model is available.');
  return text;
}

async function runExtract(config, prompt, images) {
  if (config.id === 'gemini') {
    const generationConfig = {
      temperature: 0.15,
      topP: 0.9,
      maxOutputTokens: 8192,
      responseMimeType: 'application/json'
    };
    if (config.model.indexOf('2.5-flash') >= 0) {
      generationConfig.thinkingConfig = { thinkingBudget: 0 };
    }

    const result = await requestJson(
      'https://generativelanguage.googleapis.com/v1beta/models/' +
        encodeURIComponent(config.model) + ':generateContent',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': config.key
        },
        body: JSON.stringify({
          contents: [{
            role: 'user',
            parts: [{ text: prompt }].concat(images.map(function (image) {
              return { inline_data: { mime_type: image.mimeType, data: image.base64 } };
            }))
          }],
          generationConfig
        })
      },
      150000,
      'Gemini'
    );

    if (!result.response.ok) throw providerError(result.response.status, result.data, 'Gemini');
    if (result.data && result.data.promptFeedback && result.data.promptFeedback.blockReason) {
      throw new Error('Gemini blocked this request (' + result.data.promptFeedback.blockReason + '). Try clearer photos.');
    }

    const text = geminiText(result.data);
    if (!text) {
      const finish = geminiFinish(result.data);
      throw new Error(finish === 'SAFETY'
        ? 'Gemini could not read these pages safely. Try a clearer photo of the questions.'
        : 'Gemini returned an empty response. Try fewer pages or another model.');
    }
    return text;
  }

  const content = [{ type: 'text', text: prompt }].concat(images.map(function (image) {
    return {
      type: 'image_url',
      image_url: { url: 'data:' + image.mimeType + ';base64,' + image.base64 }
    };
  }));

  const result = await requestJson(
    config.endpoint,
    {
      method: 'POST',
      headers: openAIHeaders(config.key),
      body: JSON.stringify({
        model: config.model,
        messages: [{ role: 'user', content: content }]
      })
    },
    150000,
    config.host || 'the endpoint'
  );

  if (!result.response.ok) throw providerError(result.response.status, result.data, 'endpoint');

  const choice = result.data && Array.isArray(result.data.choices) ? result.data.choices[0] : null;
  const text = openAIText(result.data);
  if (!text) {
    throw new Error(choice && choice.finish_reason === 'length'
      ? 'The model ran out of room for the questions. Try fewer pages at a time.'
      : 'The endpoint returned an empty response. Try fewer pages or another model.');
  }
  return text;
}

async function classifyTopic(config, body) {
  const title = cleanInput(body && body.title, 160);
  const subject = cleanInput(body && body.subject, 100);
  const currentSubTopic = cleanInput(body && body.subTopic, 120);
  const subTopics = stringList(body && body.subTopics, 30, 80);
  const samples = stringList(body && body.samples, 8, 400);

  if (!title && !subject && !samples.length) {
    throw new Error('Add a set title, subject or a few questions before asking the AI.');
  }

  const prompt = [
    'You classify question sets for a Kerala PSC study library.',
    'Choose exactly one main topic from this fixed list:',
    MAIN_TOPICS.map(function (topic) { return '- ' + topic; }).join('\n'),
    '',
    'Set title: ' + (title || '(not given)'),
    'Legacy subject: ' + (subject || '(not given)'),
    currentSubTopic ? 'Current subtopic: ' + currentSubTopic : '',
    subTopics.length ? 'Existing subtopics (reuse one exactly when it fits):\n' + subTopics.map(function (item) {
      return '- ' + item;
    }).join('\n') : '',
    samples.length ? 'Sample questions:\n' + samples.map(function (item, index) {
      return (index + 1) + '. ' + item;
    }).join('\n') : '',
    '',
    'Return ONLY a JSON object with this exact shape:',
    '{"mainTopic":"one exact main topic from the list","subTopic":"short grouping label"}',
    '',
    'Rules:',
    '- mainTopic must match one of the listed topics exactly, including spelling and punctuation.',
    '- subTopic is a short human label, two to six words, in title case. It groups similar sets together.',
    '- Prefer an existing subtopic when it accurately describes this set.',
    '- Choose the closest main topic even when the set mixes subjects. Never invent a new main topic.',
    '- Do not include question numbers, explanations, markdown or extra keys.'
  ].filter(Boolean).join('\n');

  const text = await runPrompt(config, prompt);
  return parseTopicReply(text, title, subject);
}

async function runPrompt(config, prompt) {
  if (config.id === 'gemini') {
    const generationConfig = {
      temperature: 0.1,
      maxOutputTokens: 500,
      responseMimeType: 'application/json'
    };
    if (config.model.indexOf('2.5-flash') >= 0) {
      generationConfig.thinkingConfig = { thinkingBudget: 0 };
    }

    const result = await requestJson(
      'https://generativelanguage.googleapis.com/v1beta/models/' +
        encodeURIComponent(config.model) + ':generateContent',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': config.key
        },
        body: JSON.stringify({
          contents: [{ role: 'user', parts: [{ text: prompt }] }],
          generationConfig
        })
      },
      60000,
      'Gemini'
    );

    if (!result.response.ok) throw providerError(result.response.status, result.data, 'Gemini');
    if (result.data && result.data.promptFeedback && result.data.promptFeedback.blockReason) {
      throw new Error('Gemini blocked this request (' + result.data.promptFeedback.blockReason + '). Try again with a shorter title.');
    }

    const text = geminiText(result.data);
    if (!text) {
      throw new Error('Gemini returned an empty classification. Try again.');
    }
    return text;
  }

  const result = await requestJson(
    config.endpoint,
    {
      method: 'POST',
      headers: openAIHeaders(config.key),
      body: JSON.stringify({
        model: config.model,
        temperature: 0.1,
        messages: [{ role: 'user', content: prompt }]
      })
    },
    60000,
    config.host || 'the endpoint'
  );

  if (!result.response.ok) throw providerError(result.response.status, result.data, 'endpoint');
  const text = openAIText(result.data);
  if (!text) throw new Error('The endpoint returned an empty classification. Try again.');
  return text;
}

function cleanInput(value, maxLength) {
  return String(value === null || value === undefined ? '' : value)
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, maxLength || 200);
}

function stringList(value, maxItems, maxLength) {
  if (!Array.isArray(value)) return [];
  const out = [];
  value.forEach(function (item) {
    const clean = cleanInput(item, maxLength);
    if (clean && out.indexOf(clean) < 0 && out.length < maxItems) out.push(clean);
  });
  return out;
}

function parseTopicReply(text, title, subject) {
  let value = String(text || '').trim();
  value = value.replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/i, '').trim();

  let parsed = null;
  try {
    parsed = JSON.parse(value);
  } catch (err) {
    const start = value.indexOf('{');
    const end = value.lastIndexOf('}');
    if (start >= 0 && end > start) {
      try { parsed = JSON.parse(value.slice(start, end + 1)); } catch (err2) { parsed = null; }
    }
  }
  if (!parsed || typeof parsed !== 'object') {
    throw new Error('The AI reply was not valid topic JSON. Try again.');
  }

  const requested = parsed.mainTopic || parsed.main_topic || parsed.topic || '';
  const mainTopic = matchMainTopic(requested) || inferMainTopic(title, subject);
  const subTopic = cleanInput(
    parsed.subTopic || parsed.sub_topic || parsed.subtopic || title || 'General',
    120
  ) || 'General';

  return { mainTopic, subTopic };
}

function matchMainTopic(value) {
  const clean = cleanInput(value, 80).toLowerCase();
  if (!clean) return '';
  const exact = MAIN_TOPICS.filter(function (topic) {
    return topic.toLowerCase() === clean;
  })[0];
  if (exact) return exact;
  const partial = MAIN_TOPICS.filter(function (topic) {
    const lower = topic.toLowerCase();
    return lower.indexOf(clean) >= 0 || clean.indexOf(lower) >= 0;
  });
  return partial.length === 1 ? partial[0] : '';
}

function inferMainTopic(title, subject) {
  const text = (String(title || '') + ' ' + String(subject || '')).toLowerCase();

  if (/geograph/.test(text)) {
    return /kerala/.test(text) ? 'Kerala - World Geography' : 'Indian Geography';
  }
  if (/renaissance|world history|kerala history|history of kerala/.test(text)) {
    return 'Kerala - World History';
  }
  if (/indian history|history/.test(text)) return 'Indian History';
  if (/constitution|polity|civics/.test(text)) return 'Constitution';
  if (/econom/.test(text)) return 'Economics';
  if (/english/.test(text)) return 'English';
  if (/malayalam/.test(text)) return 'Malayalam';
  if (/math|mental ability|reasoning|aptitude|numerical/.test(text)) {
    return 'Mathematics & Mental Ability';
  }
  if (/computer|information technology|\bict\b|internet|software/.test(text)) {
    return 'Information Technology';
  }
  if (/biology|botany|zoology|public health|medical|nursing|health/.test(text)) {
    return 'Biology & Public Health';
  }
  if (/physics|chemistry|science/.test(text)) return 'Physical Science';
  if (/governance|administration|kerala/.test(text)) return 'Kerala Governance';
  return 'Current Affairs';
}

function validateImages(value) {
  if (!Array.isArray(value) || !value.length) {
    throw new Error('Add at least one photo of the question paper.');
  }
  if (value.length > MAX_IMAGES) {
    throw new Error('Please scan at most ' + MAX_IMAGES + ' pages at a time.');
  }

  let total = 0;
  return value.map(function (image) {
    const mimeType = String((image && image.mimeType) || 'image/jpeg').toLowerCase();
    if (!/^image\/(jpeg|png|webp)$/.test(mimeType)) {
      throw new Error('Only JPEG, PNG and WEBP page images are supported.');
    }

    const base64 = String((image && image.base64) || '')
      .replace(/^data:[^;]+;base64,/, '')
      .trim();

    if (!base64) throw new Error('One of the page images was empty.');
    if (base64.length > MAX_IMAGE_BASE64) throw new Error('One of the page images is too large.');
    total += base64.length;
    if (total > MAX_TOTAL_BASE64) throw new Error('These pages are too large together. Try fewer pages at a time.');

    return { mimeType, base64 };
  });
}

export async function requestJson(url, init, timeout, label, timeoutMessage) {
  const controller = new AbortController();
  const timer = setTimeout(function () { controller.abort(); }, timeout);

  try {
    const response = await fetch(url, Object.assign({}, init, { signal: controller.signal }));
    const text = await response.text();
    let data = {};
    if (text) {
      try { data = JSON.parse(text); } catch (err) { data = { raw: text.slice(0, 300) }; }
    }
    return { response, data };
  } catch (err) {
    if (err && err.name === 'AbortError') {
      throw new Error(timeoutMessage || label + ' timed out. Try fewer pages, or a faster model.');
    }
    throw new Error('Could not reach ' + label + '. Check the endpoint and your Cloudflare configuration.');
  } finally {
    clearTimeout(timer);
  }
}

export function openAIHeaders(apiKey) {
  const headers = { 'Content-Type': 'application/json' };
  if (apiKey) headers.Authorization = 'Bearer ' + apiKey;
  return headers;
}

export function openAIEndpoint(baseUrl) {
  const value = String(baseUrl || '').trim();
  if (!value) throw new Error('The OpenAI-compatible base URL is empty.');

  let url;
  try {
    url = new URL(value);
  } catch (err) {
    throw new Error('The OpenAI-compatible base URL is not a valid URL.');
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    throw new Error('The OpenAI-compatible base URL must start with http:// or https://.');
  }

  let path = url.pathname.replace(/\/+$/, '');
  if (!/\/chat\/completions$/i.test(path)) {
    path = (!path || path === '/') ? '/v1/chat/completions' : path + '/chat/completions';
  }
  url.pathname = path;
  url.hash = '';
  return url.toString();
}

export function openAIModelsEndpoint(baseUrl) {
  const endpoint = openAIEndpoint(baseUrl);
  const url = new URL(endpoint);
  url.pathname = url.pathname.replace(/\/chat\/completions$/i, '/models');
  return url.toString();
}

export function endpointHost(endpoint) {
  try {
    return new URL(endpoint).host;
  } catch (err) {
    return 'the endpoint';
  }
}

function geminiText(data) {
  const candidates = data && data.candidates;
  const parts = candidates && candidates[0] && candidates[0].content && candidates[0].content.parts;
  return Array.isArray(parts) ? parts.map(function (part) {
    return part && typeof part.text === 'string' ? part.text : '';
  }).join('') : '';
}

function geminiFinish(data) {
  const candidates = data && data.candidates;
  return candidates && candidates[0] ? candidates[0].finishReason : '';
}

function openAIText(data) {
  const message = data && data.choices && data.choices[0] && data.choices[0].message;
  if (!message) return '';
  if (typeof message.content === 'string') return message.content;
  if (Array.isArray(message.content)) {
    return message.content.map(function (part) {
      if (typeof part === 'string') return part;
      return part && typeof part.text === 'string' ? part.text : '';
    }).join('');
  }
  return '';
}

function providerError(status, data, provider) {
  const detail = apiMessage(data);
  let message;

  if (status === 400) {
    message = detail && /model/i.test(detail)
      ? 'The provider rejected the model. Check that it exists and supports image input' + (detail ? ': ' + detail : '.')
      : 'The provider rejected the request. Check the model and endpoint' + (detail ? ': ' + detail : '.');
  } else if (status === 401) {
    message = 'The API key was rejected. Update it in Admin console → AI scanning.';
  } else if (status === 403) {
    message = 'The provider refused access. Check the key and model permissions.';
  } else if (status === 404) {
    message = 'The model or endpoint was not found. Check the model in Admin console → AI scanning' +
      (provider === 'Gemini' ? '.' : ', and the base URL for this endpoint.');
  } else if (status === 413) {
    message = 'The request was too large. Try fewer or smaller pages.';
  } else if (status === 429) {
    message = 'The provider’s rate limit was reached. Wait a minute, or scan fewer pages at once.';
  } else if (status >= 500) {
    message = 'The provider is having trouble right now. Try again in a moment.';
  } else {
    message = 'The provider returned an error' + (detail ? ': ' + detail : '.');
  }

  return new Error(message);
}

export function apiMessage(data) {
  if (!data) return '';
  if (typeof data.error === 'string') return data.error;
  if (data.error && data.error.message) return String(data.error.message);
  if (data.message) return String(data.message);
  if (data.detail) return String(data.detail);
  if (data.raw) return String(data.raw).slice(0, 220);
  return '';
}

export function json(body, status, extraHeaders) {
  const headers = Object.assign({
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff'
  }, extraHeaders || {});

  return new Response(JSON.stringify(body), { status: status || 200, headers });
}
