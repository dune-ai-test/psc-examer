/* =========================================================
   Padanam web — AI scanning
   Photos are compressed in the browser, then sent straight
   to Gemini, a user-supplied OpenAI-compatible endpoint, or
   the optional secure Cloudflare Pages proxy. They are never
   stored.
   ========================================================= */
(function () {
  'use strict';

  var A = window.Padanam;
  if (!A) return;
  var Seed = window.PadanamSeed;

  var API_ROOT = 'https://generativelanguage.googleapis.com/v1beta/models/';
  var MAX_SIDE = 1600;
  var QUALITY = 0.86;
  var MAX_IMAGES = 8;
  var PROXY_URL = './api/scan';

  var proxyState = {
    checked: false,
    checking: false,
    available: false,
    ready: false,
    provider: '',
    model: '',
    host: '',
    error: ''
  };

  var MODELS = [
    { id: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash', note: 'Fast and accurate — recommended' },
    { id: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro', note: 'Slower, better for dense or faint pages' },
    { id: 'gemini-2.0-flash', label: 'Gemini 2.0 Flash', note: 'Older fallback if a key lacks 2.5 access' }
  ];

  function loadImage(src) {
    return new Promise(function (resolve, reject) {
      var img = new Image();
      img.onload = function () { resolve(img); };
      img.onerror = function () { reject(new Error('That image could not be opened. Try a JPG or PNG photo.')); };
      img.src = src;
    });
  }

  function canvasToDataUrl(canvas) {
    try {
      return canvas.toDataURL('image/jpeg', QUALITY);
    } catch (err) {
      throw new Error('The browser could not process this image.');
    }
  }

  /**
   * Resize a photo to a sensible size for the model and return
   * both a preview data URL and base64 payload.
   */
  A.gemini = {
    MODELS: MODELS,
    MAX_IMAGES: MAX_IMAGES,
    MAX_SIDE: MAX_SIDE,

    provider: function (settings) {
      return providerInfo(settings);
    },

    openAIEndpoint: function (baseUrl) {
      return openAIEndpoint(baseUrl);
    },

    proxy: function () {
      return proxyState;
    },

    refreshProxy: function (force) {
      return refreshProxyStatus(force);
    },

    testProxy: function () {
      return testProxy();
    },

    async compressImage(file) {
      if (!file) throw new Error('No image selected.');
      if (file.type && file.type.indexOf('image/') !== 0) {
        throw new Error('Please choose an image file (JPG, PNG or WEBP).');
      }

      var objectUrl = null;
      var src;
      if (typeof file === 'string') {
        src = file;
      } else {
        objectUrl = URL.createObjectURL(file);
        src = objectUrl;
      }

      try {
        var img = await loadImage(src);
        var width = img.naturalWidth || img.width;
        var height = img.naturalHeight || img.height;
        if (!width || !height) throw new Error('That image appears to be empty.');

        var scale = Math.min(1, MAX_SIDE / Math.max(width, height));
        var targetW = Math.max(1, Math.round(width * scale));
        var targetH = Math.max(1, Math.round(height * scale));

        var canvas = document.createElement('canvas');
        canvas.width = targetW;
        canvas.height = targetH;
        var ctx = canvas.getContext('2d');
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, targetW, targetH);
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
        ctx.drawImage(img, 0, 0, targetW, targetH);

        var dataUrl = canvasToDataUrl(canvas);
        var base64 = dataUrl.split(',')[1] || '';
        if (!base64) throw new Error('The browser could not encode this image.');

        return {
          dataUrl: dataUrl,
          base64: base64,
          mimeType: 'image/jpeg',
          width: targetW,
          height: targetH,
          name: file.name || 'page.jpg',
          bytes: Math.round(base64.length * 0.75)
        };
      } finally {
        if (objectUrl) URL.revokeObjectURL(objectUrl);
      }
    },

    async testKey(opts) {
      var apiKey = String((opts && opts.apiKey) || '').trim();
      var model = String((opts && opts.model) || 'gemini-2.5-flash').trim();
      if (!apiKey) throw new Error('Enter a Gemini API key first.');

      var controller = new AbortController();
      var timer = window.setTimeout(function () { controller.abort(); }, 30000);
      try {
        var response = await fetch(API_ROOT + encodeURIComponent(model) + ':generateContent?key=' + encodeURIComponent(apiKey), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            contents: [{ role: 'user', parts: [{ text: 'Reply with exactly: OK' }] }],
            generationConfig: { temperature: 0, maxOutputTokens: 12 }
          }),
          signal: controller.signal
        });
        var data = await readJson(response);
        if (!response.ok) throw apiError(response.status, data);
        var text = candidateText(data).trim();
        if (!text) throw new Error('Gemini answered, but the reply was empty. Check that the model is available.');
        return { ok: true, text: text };
      } catch (err) {
        if (err.name === 'AbortError') throw new Error('The connection to Gemini timed out. Check your internet connection.');
        if (err instanceof TypeError) throw new Error('Could not reach Google. Check your connection or any browser extension that blocks requests.');
        throw err;
      } finally {
        window.clearTimeout(timer);
      }
    },

    async testOpenAI(opts) {
      opts = opts || {};
      var endpoint = openAIEndpoint(opts.baseUrl);
      var model = String(opts.model || '').trim();
      var apiKey = String(opts.apiKey || '').trim();
      if (!model) throw new Error('Enter a model name first.');
      var host = endpointHost(endpoint);

      var headers = { 'Content-Type': 'application/json' };
      if (apiKey) headers.Authorization = 'Bearer ' + apiKey;

      var controller = new AbortController();
      var timer = window.setTimeout(function () { controller.abort(); }, 30000);
      try {
        var response = await fetch(endpoint, {
          method: 'POST',
          headers: headers,
          body: JSON.stringify({
            model: model,
            messages: [{ role: 'user', content: 'Reply with exactly: OK' }]
          }),
          signal: controller.signal
        });
        var data = await readJson(response);
        if (!response.ok) throw openAIError(response.status, data, endpoint);
        var text = openAIMessageText(data && data.choices && data.choices[0] && data.choices[0].message).trim();
        if (!text) throw new Error('The endpoint answered, but the reply was empty. Check that the model is available.');
        return { ok: true, text: text, endpoint: endpoint, model: model };
      } catch (err) {
        if (err.name === 'AbortError') throw new Error('The connection to ' + host + ' timed out. Check your internet connection.');
        if (err instanceof TypeError) throw new Error('Could not reach ' + host + '. Check the URL and whether the endpoint allows browser (CORS) requests.');
        throw err;
      } finally {
        window.clearTimeout(timer);
      }
    },

    async extract(opts) {
      opts = opts || {};
      if (opts.provider === 'pages') return extractProxy(opts);
      if (opts.provider === 'openai') return extractOpenAI(opts);
      var apiKey = String(opts.apiKey || '').trim();
      var model = String(opts.model || 'gemini-2.5-flash').trim();
      var images = Array.isArray(opts.images) ? opts.images : [];
      var onStage = typeof opts.onStage === 'function' ? opts.onStage : function () {};

      if (!apiKey) throw new Error('Add a Gemini API key in Settings to scan question papers.');
      if (!images.length) throw new Error('Add at least one photo of the question paper.');
      if (images.length > MAX_IMAGES) throw new Error('Please scan at most ' + MAX_IMAGES + ' pages at a time.');
      if (typeof fetch !== 'function') throw new Error('This browser cannot call the Gemini API.');

      var prompt = buildPrompt(opts);

      onStage('reading', 0.18);

      var controller = new AbortController();
      var timer = window.setTimeout(function () { controller.abort(); }, 150000);
      var body = {
        contents: [{
          role: 'user',
          parts: [{ text: prompt }].concat(images.map(function (img) {
            return { inline_data: { mime_type: img.mimeType || 'image/jpeg', data: img.base64 } };
          }))
        }],
        generationConfig: {
          temperature: 0.15,
          topP: 0.9,
          maxOutputTokens: 8192,
          responseMimeType: 'application/json'
        }
      };
      if (model.indexOf('2.5-flash') >= 0) {
        body.generationConfig.thinkingConfig = { thinkingBudget: 0 };
      }

      var response, data;
      try {
        response = await fetch(API_ROOT + encodeURIComponent(model) + ':generateContent?key=' + encodeURIComponent(apiKey), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
          signal: controller.signal
        });
        onStage('reading', 0.55);
        data = await readJson(response);
      } catch (err) {
        if (err.name === 'AbortError') throw new Error('The scan timed out. Try fewer pages, or switch to Gemini 2.5 Flash.');
        if (err instanceof TypeError) throw new Error('Could not reach Google. Check your connection or any browser extension that blocks requests.');
        throw err;
      } finally {
        window.clearTimeout(timer);
      }

      if (!response.ok) throw apiError(response.status, data);
      if (data && data.promptFeedback && data.promptFeedback.blockReason) {
        throw new Error('Gemini blocked this request (' + data.promptFeedback.blockReason + '). Try clearer photos.');
      }

      onStage('structuring', 0.7);
      var raw = candidateText(data);
      if (!raw) {
        var finish = candidateFinish(data);
        throw new Error(finish === 'SAFETY'
          ? 'Gemini could not read these pages safely. Try a clearer photo of the questions.'
          : 'Gemini returned an empty response. Try fewer pages or another model.');
      }

      var extraction = assembleExtraction(raw);
      onStage('validating', 0.88);
      onStage('done', 1);
      return extraction;
    }
  };

  /* ── OpenAI-compatible scanning ──────────────────────── */
  async function extractOpenAI(opts) {
    var baseUrl = String(opts.baseUrl || '').trim();
    var apiKey = String(opts.openaiKey || '').trim();
    var model = String(opts.openaiModel || '').trim();
    var images = Array.isArray(opts.images) ? opts.images : [];
    var onStage = typeof opts.onStage === 'function' ? opts.onStage : function () {};
    var endpoint = openAIEndpoint(baseUrl);

    if (!model) throw new Error('Add an OpenAI-compatible model name in Settings to scan question papers.');
    if (!images.length) throw new Error('Add at least one photo of the question paper.');
    if (images.length > MAX_IMAGES) throw new Error('Please scan at most ' + MAX_IMAGES + ' pages at a time.');
    if (typeof fetch !== 'function') throw new Error('This browser cannot call the API.');

    var host = endpointHost(endpoint);
    var content = [{ type: 'text', text: buildPrompt(opts) }].concat(images.map(function (img) {
      return {
        type: 'image_url',
        image_url: {
          url: img.dataUrl || ('data:' + (img.mimeType || 'image/jpeg') + ';base64,' + img.base64)
        }
      };
    }));

    var headers = { 'Content-Type': 'application/json' };
    if (apiKey) headers.Authorization = 'Bearer ' + apiKey;

    onStage('reading', 0.18);
    var controller = new AbortController();
    var timer = window.setTimeout(function () { controller.abort(); }, 150000);
    var response, data;

    try {
      response = await fetch(endpoint, {
        method: 'POST',
        headers: headers,
        body: JSON.stringify({
          model: model,
          messages: [{ role: 'user', content: content }]
        }),
        signal: controller.signal
      });
      onStage('reading', 0.55);
      data = await readJson(response);
    } catch (err) {
      if (err.name === 'AbortError') throw new Error('The scan timed out. Try fewer pages, or a faster model.');
      if (err instanceof TypeError) throw new Error('Could not reach ' + host + '. Check the URL and whether the endpoint allows browser (CORS) requests.');
      throw err;
    } finally {
      window.clearTimeout(timer);
    }

    if (!response.ok) throw openAIError(response.status, data, endpoint);

    onStage('structuring', 0.7);
    var choice = data && Array.isArray(data.choices) ? data.choices[0] : null;
    var raw = openAIMessageText(choice && choice.message) || (choice && typeof choice.text === 'string' ? choice.text : '');
    if (!raw) {
      throw new Error(choice && choice.finish_reason === 'length'
        ? 'The model ran out of room for the questions. Try fewer pages at a time.'
        : 'The endpoint returned an empty response. Try fewer pages or another model.');
    }

    var extraction = assembleExtraction(raw);
    onStage('validating', 0.88);
    onStage('done', 1);
    return extraction;
  }

  /* ── Cloudflare Pages proxy ──────────────────────────── */
  function applyProxyInfo(data) {
    proxyState.ready = !!(data && data.ready === true);
    proxyState.provider = String((data && data.provider) || '');
    proxyState.model = String((data && data.model) || '');
    proxyState.host = String((data && data.host) || '');
    proxyState.error = String((data && data.error) || '');
  }

  function refreshProxyStatus(force) {
    if (!/^https?:$/.test(window.location.protocol)) {
      proxyState.checked = true;
      proxyState.available = false;
      proxyState.ready = false;
      proxyState.error = 'The Cloudflare proxy only works on a deployed http(s) site.';
      return Promise.resolve(proxyState);
    }
    if (proxyState.checking) return proxyState.promise;
    if (proxyState.checked && !force) return Promise.resolve(proxyState);
    if (typeof fetch !== 'function') {
      proxyState.checked = true;
      proxyState.available = false;
      proxyState.ready = false;
      proxyState.error = 'This browser cannot call the Cloudflare proxy.';
      return Promise.resolve(proxyState);
    }

    proxyState.checking = true;
    proxyState.error = '';
    var controller = new AbortController();
    var timer = window.setTimeout(function () { controller.abort(); }, 12000);

    proxyState.promise = fetch(PROXY_URL, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      cache: 'no-store',
      signal: controller.signal
    }).then(function (response) {
      return response.text().then(function (text) {
        var data = null;
        try { data = JSON.parse(text); } catch (err) { data = null; }
        if (!data || typeof data !== 'object') {
          proxyState.available = false;
          proxyState.ready = false;
          proxyState.error = response.status === 404
            ? 'No Cloudflare proxy was found at /api/scan. Deploy the functions folder with the site.'
            : 'The Cloudflare proxy did not return a valid response.';
          return proxyState;
        }
        applyProxyInfo(data);
        proxyState.available = true;
        return proxyState;
      });
    }).catch(function (err) {
      proxyState.available = false;
      proxyState.ready = false;
      proxyState.error = err && err.name === 'AbortError'
        ? 'The Cloudflare proxy did not respond in time.'
        : 'Could not reach the Cloudflare proxy on this site.';
      return proxyState;
    }).then(function (result) {
      window.clearTimeout(timer);
      proxyState.checking = false;
      proxyState.checked = true;
      return result;
    });

    return proxyState.promise;
  }

  async function testProxy() {
    if (typeof fetch !== 'function') throw new Error('This browser cannot call the Cloudflare proxy.');
    var controller = new AbortController();
    var timer = window.setTimeout(function () { controller.abort(); }, 45000);
    try {
      var response = await fetch(PROXY_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ action: 'test' }),
        signal: controller.signal
      });
      var data = await readJson(response);
      if (!response.ok || !data || data.ok === false) {
        proxyState.checked = true;
        proxyState.available = true;
        proxyState.ready = false;
        proxyState.error = String((data && data.error) || '');
        throw new Error(proxyState.error || 'The Cloudflare proxy returned HTTP ' + response.status + '.');
      }
      if (!data.text) {
        proxyState.ready = false;
        throw new Error('The Cloudflare proxy answered, but the reply was empty.');
      }

      proxyState.checked = true;
      proxyState.available = true;
      proxyState.ready = true;
      proxyState.provider = String(data.provider || proxyState.provider || '');
      proxyState.model = String(data.model || proxyState.model || '');
      proxyState.host = String(data.host || proxyState.host || '');
      proxyState.error = '';
      return {
        ok: true,
        text: data.text,
        provider: proxyState.provider,
        model: proxyState.model,
        host: proxyState.host
      };
    } catch (err) {
      if (err.name === 'AbortError') throw new Error('The Cloudflare proxy test timed out.');
      throw err;
    } finally {
      window.clearTimeout(timer);
    }
  }

  async function extractProxy(opts) {
    var images = Array.isArray(opts.images) ? opts.images : [];
    var onStage = typeof opts.onStage === 'function' ? opts.onStage : function () {};

    if (!images.length) throw new Error('Add at least one photo of the question paper.');
    if (images.length > MAX_IMAGES) throw new Error('Please scan at most ' + MAX_IMAGES + ' pages at a time.');
    if (typeof fetch !== 'function') throw new Error('This browser cannot call the Cloudflare proxy.');

    if (!proxyState.checked) await refreshProxyStatus();
    if (!proxyState.available) {
      throw new Error(proxyState.error || 'The Cloudflare proxy is not available on this site.');
    }
    if (!proxyState.ready) {
      throw new Error(proxyState.error || 'The Cloudflare proxy is not configured. Add the provider secrets in Cloudflare Pages.');
    }

    var payload = {
      action: 'extract',
      prompt: buildPrompt(opts),
      images: images.map(function (img) {
        return {
          mimeType: img.mimeType || 'image/jpeg',
          base64: img.base64 || ''
        };
      })
    };

    onStage('reading', 0.18);
    var controller = new AbortController();
    var timer = window.setTimeout(function () { controller.abort(); }, 150000);
    var response, data;
    try {
      response = await fetch(PROXY_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(payload),
        signal: controller.signal
      });
      onStage('reading', 0.55);
      data = await readJson(response);
    } catch (err) {
      if (err.name === 'AbortError') throw new Error('The scan timed out. Try fewer pages, or a faster model.');
      throw err;
    } finally {
      window.clearTimeout(timer);
    }

    if (!response.ok || !data || data.ok === false) {
      proxyState.ready = false;
      proxyState.error = String((data && data.error) || '');
      throw new Error(proxyState.error || 'The Cloudflare proxy returned HTTP ' + response.status + '.');
    }
    if (!data.text) {
      throw new Error('The Cloudflare proxy returned an empty response. Try fewer pages or another model.');
    }

    onStage('structuring', 0.7);
    var extraction = assembleExtraction(data.text);
    onStage('validating', 0.88);
    onStage('done', 1);
    return extraction;
  }

  /* ── Response helpers ────────────────────────────────── */
  async function readJson(response) {
    var text = await response.text();
    if (!text) return {};
    try {
      return JSON.parse(text);
    } catch (err) {
      return { raw: text };
    }
  }

  function candidateParts(data) {
    var candidates = data && data.candidates;
    if (!Array.isArray(candidates) || !candidates.length) return [];
    var parts = candidates[0].content && candidates[0].content.parts;
    return Array.isArray(parts) ? parts : [];
  }

  function candidateText(data) {
    return candidateParts(data).map(function (part) {
      return part && typeof part.text === 'string' ? part.text : '';
    }).join('');
  }

  function candidateFinish(data) {
    var candidates = data && data.candidates;
    return candidates && candidates[0] ? candidates[0].finishReason : '';
  }

  function apiMessage(data) {
    if (!data) return '';
    if (typeof data.error === 'string') return String(data.error);
    if (data.error && data.error.message) return String(data.error.message);
    if (data.message) return String(data.message);
    if (data.detail) return String(data.detail);
    if (data.raw) return String(data.raw).slice(0, 220);
    return '';
  }

  function apiError(status, data) {
    var detail = apiMessage(data);
    var message;
    if (status === 400) {
      message = detail && /API key/i.test(detail)
        ? 'That API key was rejected. Paste a fresh key from Google AI Studio.'
        : 'Gemini rejected the request. Check the API key and try again.';
    } else if (status === 401) {
      message = 'The API key is not authorised. Create a new key in Google AI Studio.';
    } else if (status === 403) {
      message = 'This key cannot use the Gemini API. Enable the API for the key, or create a new one.';
    } else if (status === 404) {
      message = 'That Gemini model is not available to this key. Choose another model in Settings.';
    } else if (status === 429) {
      message = 'Gemini’s rate limit was reached. Wait a minute, or scan fewer pages at once.';
    } else if (status >= 500) {
      message = 'Gemini is busy right now. Try again in a moment.';
    } else {
      message = 'Gemini returned an error' + (detail ? ': ' + detail : '.');
    }
    var err = new Error(message);
    err.status = status;
    err.detail = detail;
    return err;
  }

  /* ── Provider configuration ──────────────────────────── */
  function providerInfo(settings) {
    settings = settings || {};
    if (settings.provider === 'pages') {
      return {
        id: 'pages',
        label: 'Cloudflare proxy',
        short: 'Cloudflare',
        model: proxyState.model || 'server model',
        host: proxyState.host || 'this site',
        endpoint: '',
        target: proxyState.host || 'Cloudflare Pages',
        ready: proxyState.checked ? proxyState.ready : true,
        checking: !proxyState.checked,
        keyRequired: false,
        backend: proxyState.provider,
        error: proxyState.error
      };
    }

    if (settings.provider === 'openai') {
      var model = String(settings.openaiModel || '').trim();
      var endpoint = '';
      try { endpoint = openAIEndpoint(settings.openaiUrl); } catch (err) { endpoint = ''; }
      var host = endpoint ? endpointHost(endpoint) : '';
      return {
        id: 'openai',
        label: 'OpenAI-compatible',
        short: 'Custom API',
        model: model,
        host: host,
        endpoint: endpoint,
        target: host || 'your custom endpoint',
        ready: !!endpoint && !!model,
        keyRequired: false
      };
    }

    var geminiModel = String(settings.model || 'gemini-2.5-flash').trim() || 'gemini-2.5-flash';
    return {
      id: 'gemini',
      label: 'Gemini',
      short: 'Gemini',
      model: geminiModel,
      host: 'Google Gemini',
      endpoint: '',
      target: 'Google Gemini',
      ready: !!String(settings.apiKey || '').trim(),
      keyRequired: true
    };
  }

  function openAIEndpoint(baseUrl) {
    var value = String(baseUrl || '').trim();
    if (!value) throw new Error('Enter the OpenAI-compatible base URL first.');
    var url;
    try {
      url = new URL(value);
    } catch (err) {
      throw new Error('That API URL is not valid. Include the full address, starting with http:// or https://.');
    }
    if (url.protocol !== 'https:' && url.protocol !== 'http:') {
      throw new Error('The API URL must start with http:// or https://.');
    }

    var path = url.pathname.replace(/\/+$/, '');
    if (!/\/chat\/completions$/i.test(path)) {
      path = (!path || path === '/') ? '/v1/chat/completions' : path + '/chat/completions';
    }
    url.pathname = path;
    url.hash = '';
    return url.toString();
  }

  function endpointHost(endpoint) {
    try {
      return new URL(endpoint).host;
    } catch (err) {
      return 'the API endpoint';
    }
  }

  function openAIMessageText(message) {
    if (!message || typeof message !== 'object') return '';
    var content = message.content;
    if (typeof content === 'string') return content;
    if (Array.isArray(content)) {
      return content.map(function (part) {
        if (typeof part === 'string') return part;
        return part && typeof part.text === 'string' ? part.text : '';
      }).join('');
    }
    return '';
  }

  function openAIError(status, data, endpoint) {
    var detail = apiMessage(data);
    var message;
    if (status === 400) {
      message = detail && /model/i.test(detail)
        ? 'The endpoint rejected the model. Check that it exists and supports images' + (detail ? ': ' + detail : '.')
        : 'The endpoint rejected the request. Check the URL and model' + (detail ? ': ' + detail : '.');
    } else if (status === 401) {
      message = 'The API key was rejected. Check the key, or leave it blank if this endpoint does not need one.';
    } else if (status === 403) {
      message = 'This endpoint refused access. Check the key and the model permissions.';
    } else if (status === 404) {
      message = 'The endpoint or model was not found. Check the base URL (often ending in /v1) and the model name.';
    } else if (status === 413) {
      message = 'The request was too large for this endpoint. Try fewer or smaller pages.';
    } else if (status === 429) {
      message = 'The endpoint’s rate limit was reached. Wait a minute, or scan fewer pages at once.';
    } else if (status >= 500) {
      message = 'The endpoint is having trouble right now. Try again in a moment.';
    } else {
      message = endpointHost(endpoint) + ' returned an error' + (detail ? ': ' + detail : '.');
    }
    var err = new Error(message);
    err.status = status;
    err.detail = detail;
    return err;
  }

  /* ── Prompt & extraction assembly ────────────────────── */
  function buildPrompt(opts) {
    var subjectList = (Seed.subjects || []).map(function (s) { return s.name; }).join(', ');
    var languageRule = opts.language === 'en'
      ? 'Extract only the English version of each question. Set "ml" to null.'
      : 'If the paper prints a Malayalam version, extract it in "ml". Otherwise set "ml" to null. The "en" field is required: if a question exists only in Malayalam, put that text in "en".';

    var titleHint = opts.titleHint ? 'The user calls this paper "' + opts.titleHint + '". Use a cleaner version of it as the title if no printed title is visible.' : '';
    var subjectHint = opts.subject ? 'If the page does not state a subject, prefer "' + opts.subject + '".' : '';

    return [
      'You are a careful exam-question extractor for Kerala PSC (Public Service Commission) question papers.',
      'Read every supplied page image and extract ALL multiple-choice questions, in reading order.',
      '',
      'Return ONLY a JSON object with this exact shape:',
      '{',
      '  "title": "paper title, or a short descriptive title",',
      '  "subject": "one of: ' + subjectList + '",',
      '  "questions": [',
      '    {',
      '      "en": { "stem": "question text", "options": ["option A", "option B", "option C", "option D"], "explanation": "brief reason the answer is correct" },',
      '      "ml": { "stem": "Malayalam question", "options": ["A", "B", "C", "D"], "explanation": "Malayalam reason" },',
      '      "answer": 0',
      '    }',
      '  ]',
      '}',
      '',
      'Rules:',
      '- "answer" is the zero-based index of the correct option. Use the answer key or marked answer on the page when present. If no answer is marked, use your own subject knowledge and choose the correct option.',
      '- Keep each option as a separate array item. Do not merge options, add labels like "(a)" or "A.", or invent options that are not on the page.',
      '- Options must stay in the same order in "en" and "ml". The same "answer" index applies to both languages.',
      '- ' + languageRule,
      '- Preserve Malayalam script exactly. Do not transliterate it.',
      '- Ignore page numbers, instructions, advertisements, and answer-key tables outside the questions.',
      '- Clean up spacing and broken line wraps. Do not include HTML.',
      '- Keep explanations to one or two short sentences. If no explanation can be given, use an empty string.',
      '- If a question is incomplete or has fewer than two options, skip it.',
      '- If the pages contain no multiple-choice questions, return an empty questions array.',
      titleHint,
      subjectHint
    ].filter(Boolean).join('\n');
  }

  function assembleExtraction(raw) {
    var parsed = parseJsonLoose(raw);
    var extraction = normalizeExtraction(parsed);
    if (!extraction.questions.length) {
      throw new Error('No questions were found. Make sure the photos show the question text clearly.');
    }
    extraction.questions = dedupe(extraction.questions);
    if (!extraction.questions.length) throw new Error('The same questions appeared on every page. Try adding a different page.');
    return extraction;
  }

  /* ── JSON & extraction validation ────────────────────── */
  function parseJsonLoose(text) {
    var value = String(text || '').trim();
    value = value.replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/i, '').trim();
    try {
      return JSON.parse(value);
    } catch (err) {
      var start = value.indexOf('{');
      var end = value.lastIndexOf('}');
      if (start >= 0 && end > start) {
        try { return JSON.parse(value.slice(start, end + 1)); } catch (err2) { /* fall through */ }
      }
      throw new Error('The model’s reply was not valid JSON. Try again, or use a clearer photo.');
    }
  }

  function clean(value) {
    return String(value === null || value === undefined ? '' : value)
      .replace(/\s+/g, ' ')
      .replace(/^[\s"']+|[\s"']+$/g, '')
      .trim();
  }

  function cleanOptions(raw) {
    if (!Array.isArray(raw)) return [];
    return raw.map(function (option) {
      return clean(option);
    }).filter(function (option) { return option.length > 0; });
  }

  function normalizeExtraction(parsed) {
    if (!parsed) throw new Error('The model returned no data. Try again.');
    var payload = parsed;
    if (Array.isArray(parsed)) payload = { questions: parsed };
    if (payload.data && Array.isArray(payload.data.questions)) payload = payload.data;
    var rawQuestions = Array.isArray(payload.questions) ? payload.questions : [];
    if (!rawQuestions.length && Array.isArray(payload.mcqs)) rawQuestions = payload.mcqs;

    var questions = rawQuestions.map(function (raw) {
      if (!raw || typeof raw !== 'object') return null;
      var enRaw = raw.en || raw.English || raw.english;
      if (!enRaw && raw.question) {
        enRaw = { stem: raw.question, options: raw.options, explanation: raw.explanation };
      }
      if (!enRaw) return null;
      var enStem = clean(enRaw.stem || enRaw.question || raw.stem);
      var enOptions = cleanOptions(enRaw.options || raw.options);
      if (!enStem || enOptions.length < 2) return null;

      var answer = parseInt(raw.answer !== undefined ? raw.answer : (enRaw.answer !== undefined ? enRaw.answer : raw.correct), 10);
      if (isNaN(answer) || answer < 0 || answer >= enOptions.length) answer = 0;

      var ml = null;
      var mlRaw = raw.ml || raw.Malayalam || raw.malayalam;
      if (mlRaw) {
        var mlStem = clean(mlRaw.stem || mlRaw.question);
        var mlOptions = cleanOptions(mlRaw.options);
        if (mlStem && mlOptions.length === enOptions.length) {
          ml = {
            stem: mlStem,
            options: mlOptions,
            explanation: clean(mlRaw.explanation)
          };
        }
      }

      return {
        id: A.uid('q'),
        answer: answer,
        en: {
          stem: enStem,
          options: enOptions,
          explanation: clean(enRaw.explanation || raw.explanation)
        },
        ml: ml
      };
    }).filter(Boolean);

    var subject = clean(payload.subject);
    var known = (Seed.subjects || []).map(function (s) { return s.name; });
    if (!known.some(function (name) { return name.toLowerCase() === subject.toLowerCase(); })) {
      subject = 'General Knowledge';
    } else {
      subject = known.filter(function (name) { return name.toLowerCase() === subject.toLowerCase(); })[0];
    }

    return {
      title: clean(payload.title) || '',
      subject: subject,
      questions: questions
    };
  }

  function dedupe(questions) {
    var seen = {};
    return questions.filter(function (q) {
      var key = q.en.stem.toLowerCase().replace(/[^a-z0-9\u0d00-\u0d7f]+/g, ' ').trim();
      if (!key || seen[key]) return false;
      seen[key] = true;
      return true;
    });
  }
})();
