/* =========================================================
   Padanam — secure scanner proxy for Cloudflare Pages
   The API key lives in Pages Variables & Secrets, never in
   the browser bundle. The app posts compressed pages here.
   ========================================================= */

const MAX_IMAGES = 8;
const MAX_IMAGE_BASE64 = 6 * 1024 * 1024;
const MAX_TOTAL_BASE64 = 20 * 1024 * 1024;
const DEFAULT_GEMINI_MODEL = 'gemini-2.5-flash';
const DEFAULT_OPENAI_MODEL = 'gpt-4o-mini';
const DEFAULT_OPENAI_URL = 'https://api.openai.com/v1';

export function onRequest(context) {
  if (context.request.method === 'GET') {
    return json(statusPayload(scanConfig(context.env)), 200);
  }
  if (context.request.method === 'POST') {
    return handleScan(context);
  }
  return json({ error: 'Method not allowed.' }, 405, { Allow: 'GET, POST' });
}

async function handleScan(context) {
  const config = scanConfig(context.env);

  let body;
  try {
    body = await context.request.json();
  } catch (err) {
    return json({ error: 'Send a JSON request body.' }, 400);
  }

  const action = body && body.action;
  if (action !== 'test' && action !== 'extract') {
    return json({ error: 'Unknown action. Use "test" or "extract".' }, 400);
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

export function scanConfig(env) {
  const provider = String(env.SCAN_PROVIDER || 'gemini').trim().toLowerCase();

  if (provider === 'openai') {
    const key = String(env.OPENAI_API_KEY || '').trim();
    const model = String(env.OPENAI_MODEL || '').trim() || DEFAULT_OPENAI_MODEL;
    const baseUrl = String(env.OPENAI_BASE_URL || '').trim() || DEFAULT_OPENAI_URL;
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
      error = 'Set the OPENAI_API_KEY secret in Cloudflare Pages.';
    }

    return {
      id: 'openai',
      ready,
      model,
      host,
      endpoint,
      key,
      error: ready ? '' : (error || 'Set OPENAI_BASE_URL and OPENAI_MODEL in Cloudflare Pages.')
    };
  }

  if (provider !== 'gemini') {
    return {
      id: provider || 'gemini',
      ready: false,
      model: '',
      host: '',
      key: '',
      error: 'SCAN_PROVIDER must be "gemini" or "openai".'
    };
  }

  const key = String(env.GEMINI_API_KEY || '').trim();
  const model = String(env.GEMINI_MODEL || '').trim() || DEFAULT_GEMINI_MODEL;
  return {
    id: 'gemini',
    ready: !!key,
    model,
    host: 'generativelanguage.googleapis.com',
    key,
    error: key ? '' : 'Set the GEMINI_API_KEY secret in Cloudflare Pages.'
  };
}

function statusPayload(config) {
  return {
    ready: config.ready,
    provider: config.id,
    model: config.model,
    host: config.host,
    error: config.error
  };
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
  if (!value) throw new Error('OPENAI_BASE_URL is empty.');

  let url;
  try {
    url = new URL(value);
  } catch (err) {
    throw new Error('OPENAI_BASE_URL is not a valid URL.');
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    throw new Error('OPENAI_BASE_URL must start with http:// or https://.');
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
    message = 'The API key was rejected. Update the secret in Cloudflare Pages.';
  } else if (status === 403) {
    message = 'The provider refused access. Check the key and model permissions.';
  } else if (status === 404) {
    message = 'The model or endpoint was not found. Check ' +
      (provider === 'Gemini' ? 'GEMINI_MODEL.' : 'OPENAI_BASE_URL and OPENAI_MODEL.');
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
