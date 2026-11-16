/* =========================================================
   Padanam web — router, actions, forms and app lifecycle
   ========================================================= */
(function () {
  'use strict';

  var A = window.Padanam;
  var views = A && A.views;
  if (!A || !views) return;

  var viewEl = null;
  var current = null;
  var clock = null;
  var searchTimer = null;
  var examFinishing = false;

  /* ── Routes ──────────────────────────────────────────── */
  function decode(value) {
    try {
      return decodeURIComponent(value);
    } catch (err) {
      return value;
    }
  }

  function parseQuery(search) {
    var out = {};
    String(search || '').split('&').forEach(function (pair) {
      if (!pair) return;
      var eq = pair.indexOf('=');
      var key = eq < 0 ? pair : pair.slice(0, eq);
      var value = eq < 0 ? '' : pair.slice(eq + 1);
      out[decode(key.replace(/\+/g, ' '))] = decode(value.replace(/\+/g, ' '));
    });
    return out;
  }

  function parseRoute() {
    var raw = window.location.hash ? window.location.hash.replace(/^#/, '') : '';
    if (!raw) return { name: 'home', params: {}, query: {} };

    var q = raw.indexOf('?');
    var search = '';
    if (q >= 0) {
      search = raw.slice(q + 1);
      raw = raw.slice(0, q);
    }

    var parts = raw.replace(/\/+$/, '').split('/').filter(Boolean).map(decode);
    var query = parseQuery(search);
    var params = {};

    if (!parts.length || parts[0] === 'home') return { name: 'home', params: params, query: query };
    if (parts.length === 1 && parts[0] === 'library') return { name: 'library', params: params, query: query };
    if (parts.length === 1 && parts[0] === 'scan') return { name: 'scan', params: params, query: query };
    if (parts.length === 1 && parts[0] === 'progress') return { name: 'progress', params: params, query: query };
    if (parts.length === 1 && parts[0] === 'profile') return { name: 'profile', params: params, query: query };
    if (parts.length === 1 && parts[0] === 'settings') return { name: 'settings', params: params, query: query };

    if (parts[0] === 'set' && parts[1]) {
      params.id = parts[1];
      return { name: 'set', params: params, query: query };
    }
    if (parts[0] === 'exam' && parts[1] === 'setup' && parts[2]) {
      params.id = parts[2];
      return { name: 'setup', params: params, query: query };
    }
    if (parts[0] === 'exam' && parts[1] === 'run') {
      return { name: 'exam', params: params, query: query };
    }
    if (parts[0] === 'result' && parts[1]) {
      params.id = parts[1];
      return { name: 'result', params: params, query: query };
    }

    return { name: 'notFound', params: params, query: query };
  }

  function routeView(route) {
    switch (route.name) {
      case 'home':
        return {
          name: 'home',
          nav: 'home',
          html: views.home(),
          title: 'Padanam — PSC practice, in your browser'
        };
      case 'library':
        return {
          name: 'library',
          nav: 'library',
          html: views.library(),
          title: 'Library — Padanam'
        };
      case 'set':
        return {
          name: 'set',
          nav: 'library',
          html: views.setDetail(route.params.id),
          title: (A.getSet(route.params.id) ? A.getSet(route.params.id).title : 'Set') + ' — Padanam'
        };
      case 'scan':
        return {
          name: 'scan',
          nav: 'scan',
          html: views.scan(),
          title: 'Scan a paper — Padanam'
        };
      case 'setup':
        return {
          name: 'setup',
          nav: 'library',
          html: views.examSetup(route.params.id, route.query.mode),
          title: 'Exam setup — Padanam'
        };
      case 'exam':
        return {
          name: 'exam',
          nav: '',
          html: views.examRun(),
          title: 'Test in progress — Padanam'
        };
      case 'result':
        return {
          name: 'result',
          nav: 'progress',
          html: views.result(route.params.id),
          title: 'Result — Padanam'
        };
      case 'progress':
        return {
          name: 'progress',
          nav: 'progress',
          html: views.progress(),
          title: 'Progress — Padanam'
        };
      case 'profile':
        return {
          name: 'profile',
          nav: 'profile',
          html: views.profile(),
          title: 'Profile — Padanam'
        };
      case 'settings':
        return {
          name: 'settings',
          nav: 'settings',
          html: views.settings(),
          title: 'Settings — Padanam'
        };
      default:
        return {
          name: 'notFound',
          nav: '',
          html: views.notFound('That page does not exist. Check the address, or head back to your library.'),
          title: 'Not found — Padanam'
        };
    }
  }

  /* ── Shell ───────────────────────────────────────────── */
  function setBound(name, value) {
    document.querySelectorAll('[data-bind="' + name + '"]').forEach(function (el) {
      el.textContent = value;
    });
  }

  function syncShell(view) {
    var active = view.nav || '';
    document.querySelectorAll('[data-nav]').forEach(function (link) {
      var on = link.getAttribute('data-nav') === active;
      link.classList.toggle('is-active', on);
      if (on) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });

    var goal = A.goal();
    setBound('goal-done', goal.done);
    setBound('goal-total', goal.total);
    setBound('goal-note', goal.note);
    document.querySelectorAll('[data-bind="goal-bar"]').forEach(function (el) {
      el.style.width = goal.pct + '%';
    });

    var name = String(A.state.profile.name || '').trim();
    setBound('avatar', A.initials(name));
    setBound('user-name', name || 'Set up profile');
  }

  function render(opts) {
    opts = opts || {};
    if (!viewEl) viewEl = document.getElementById('view');
    if (!viewEl) return;

    var route = parseRoute();
    var scroll = window.scrollY || window.pageYOffset || 0;
    var view;

    try {
      view = routeView(route);
    } catch (err) {
      console.error('Padanam view error', err);
      view = {
        name: 'notFound',
        nav: '',
        html: views.notFound('Something went wrong while opening this page. Go back home and try again.'),
        title: 'Something went wrong — Padanam'
      };
    }

    current = route;
    current.view = view;
    viewEl.innerHTML = view.html;

    var focusedExam = view.name === 'exam' && !!A.state.activeExam;
    document.body.classList.toggle('is-exam', focusedExam);
    if (view.name !== 'exam') examFinishing = false;
    syncShell(view);
    document.title = view.title;

    try {
      views.mount(view.name);
    } catch (err) {
      console.error('Padanam mount error', err);
    }

    if (opts.preserveScroll) window.scrollTo(0, scroll);
    else window.scrollTo(0, 0);

    if (opts.routeChange) {
      try {
        viewEl.focus({ preventScroll: true });
      } catch (err) {
        viewEl.focus();
      }
      A.live(view.title.replace(/ — Padanam$/, ''));
    }

    updateClock();
  }

  function refreshLibraryUI() {
    var host = document.getElementById('libraryResults');
    if (!host) {
      render({ preserveScroll: true });
      return;
    }
    var search = document.querySelector('[data-library-search]');
    if (search) A.ui.library.q = search.value;
    host.innerHTML = views.libraryResults();
    document.querySelectorAll('.filter-chip[data-action="library-source"]').forEach(function (btn) {
      btn.classList.toggle('is-active', btn.getAttribute('data-value') === A.ui.library.source);
    });
  }

  /* ── Clock ───────────────────────────────────────────── */
  function updateClock() {
    if (!current || current.name !== 'exam') return;
    var exam = A.state.activeExam;
    if (!exam) return;

    var valueEl = document.getElementById('examTimerValue');
    var labelEl = document.getElementById('examTimerLabel');
    var box = document.getElementById('examTimer');
    if (!valueEl) return;

    if (exam.timeLimitSec) {
      var remaining = A.examRemaining(exam);
      valueEl.textContent = A.fmtDuration(remaining);
      if (labelEl) labelEl.textContent = remaining > 0 ? 'Time left' : 'Time up';
      if (box) box.classList.toggle('is-urgent', remaining <= 60);
      if (remaining <= 0 && !examFinishing) {
        finishExam(true);
      }
    } else {
      valueEl.textContent = A.fmtDuration(A.examElapsed(exam));
      if (labelEl) labelEl.textContent = 'Elapsed';
      if (box) box.classList.remove('is-urgent');
    }
  }

  function finishExam(auto) {
    if (examFinishing) return;
    var exam = A.state.activeExam;
    if (!exam || !exam.questions || !exam.questions.length) return;

    examFinishing = true;
    var attempt = A.buildAttempt(exam);
    A.state.activeExam = null;
    A.state.attempts.unshift(attempt);
    A.save();

    A.go('#/result/' + attempt.id);
    A.toast({
      title: auto ? 'Time is up' : 'Test submitted',
      message: auto
        ? 'Your answers were submitted automatically and saved to this browser.'
        : 'Your result is ready. Take a moment to review the answers.',
      kind: auto ? 'info' : 'success'
    });
  }

  /* ── Helpers for action handlers ─────────────────────── */
  function examOrNull() {
    return A.state.activeExam || null;
  }

  function refreshExam() {
    render({ preserveScroll: true });
  }

  function gotoExam(index) {
    var exam = examOrNull();
    if (!exam) return;
    var next = A.clamp(parseInt(index, 10) || 0, 0, exam.questions.length - 1);
    if (next === exam.current) return;
    exam.current = next;
    A.save();
    render({ preserveScroll: false });
  }

  function stepExam(delta) {
    var exam = examOrNull();
    if (!exam) return;
    var next = A.clamp(exam.current + delta, 0, exam.questions.length - 1);
    if (next === exam.current) return;
    exam.current = next;
    A.save();
    render({ preserveScroll: false });
  }

  function chooseExamOption(index) {
    var exam = examOrNull();
    if (!exam) return;
    var question = exam.questions[exam.current];
    var choice = parseInt(index, 10);
    if (!question || isNaN(choice) || choice < 0 || choice >= question.en.options.length) return;
    exam.responses[exam.current] = choice;
    A.save();
    refreshExam();
  }

  function toggleExamFlag() {
    var exam = examOrNull();
    if (!exam) return;
    exam.flags[exam.current] = !exam.flags[exam.current];
    A.save();
    refreshExam();
  }

  function clearExamAnswer() {
    var exam = examOrNull();
    if (!exam) return;
    exam.responses[exam.current] = null;
    A.save();
    refreshExam();
  }

  function setExamLanguage(lang) {
    var exam = examOrNull();
    if (!exam || (lang !== 'en' && lang !== 'ml')) return;
    exam.lang = lang;
    A.save();
    refreshExam();
  }

  function submitExam() {
    var exam = examOrNull();
    if (!exam) return;
    var answered = exam.responses.filter(A.isAnswered).length;
    var unanswered = exam.questions.length - answered;

    if (!unanswered) {
      finishExam(false);
      return;
    }

    A.confirm({
      title: 'Submit with ' + A.plural(unanswered, 'unanswered question') + '?',
      message: 'Unanswered questions will be marked as skipped. You cannot change your answers after submitting.',
      confirmText: 'Submit test',
      cancelText: 'Keep working'
    }).then(function (ok) {
      if (ok) finishExam(false);
    });
  }

  function noteBox(kind, iconName, html) {
    return '<div class="inline-note' + (kind ? ' inline-note--' + kind : '') + '">' +
      A.icon(iconName) + '<div>' + html + '</div></div>';
  }

  /* ── Actions ─────────────────────────────────────────── */
  var actions = {};

  actions['open-set-form'] = function (btn) {
    views.openSetForm(btn.getAttribute('data-set') || null);
  };

  actions['add-question'] = function (btn) {
    var setId = btn.getAttribute('data-set');
    if (!setId) return;
    views.openQuestionForm(setId, null);
  };

  actions['edit-question'] = function (btn) {
    var setId = btn.getAttribute('data-set');
    var qid = btn.getAttribute('data-q');
    if (!setId || !qid) return;
    views.openQuestionForm(setId, qid);
  };

  actions['delete-question'] = function (btn) {
    var setId = btn.getAttribute('data-set');
    var qid = btn.getAttribute('data-q');
    if (!setId || !qid) return;
    return A.confirm({
      title: 'Delete this question?',
      message: 'It will be removed from this set. Questions saved inside past attempts are not affected.',
      confirmText: 'Delete question',
      danger: true
    }).then(function (ok) {
      if (!ok) return;
      A.deleteQuestion(setId, qid);
      A.refresh();
      A.toast({ title: 'Question deleted', message: 'The set has been updated.', kind: 'info' });
    });
  };

  actions['delete-set'] = function (btn) {
    var setId = btn.getAttribute('data-set');
    var set = A.getSet(setId);
    if (!set) return;

    var message = '“' + set.title + '” and all of its questions will be removed from this browser. Past results stay in your history.';
    if (A.state.activeExam && A.state.activeExam.setId === setId) {
      message += ' Your open test on this set will also be discarded.';
    }

    return A.confirm({
      title: 'Delete this set?',
      message: message,
      confirmText: 'Delete set',
      danger: true
    }).then(function (ok) {
      if (!ok) return;
      A.deleteSet(setId);
      A.go('#/library');
      A.toast({ title: 'Set deleted', message: 'The questions were removed from your library.', kind: 'info' });
    });
  };

  actions['restore-demos'] = function () {
    var before = A.state.sets.length;
    A.restoreDemos(true);
    var added = A.state.sets.length - before;
    A.refresh();
    A.toast({
      title: added ? added + ' demo ' + (added === 1 ? 'set' : 'sets') + ' restored' : 'Demo sets are already in your library',
      message: added ? 'Bilingual questions are ready to practise.' : 'Nothing needed restoring.',
      kind: 'info'
    });
  };

  actions['library-source'] = function (btn) {
    var value = btn.getAttribute('data-value') || 'all';
    A.ui.library.source = value;
    refreshLibraryUI();
  };

  actions['clear-library-filters'] = function () {
    A.ui.library.q = '';
    A.ui.library.subject = 'all';
    A.ui.library.source = 'all';
    A.ui.library.sort = 'recent';
    render({ preserveScroll: true });
    var search = document.querySelector('[data-library-search]');
    if (search) search.focus();
  };

  actions['set-lang'] = function (btn) {
    A.ui.setLang = btn.getAttribute('data-value') === 'ml' ? 'ml' : 'en';
    render({ preserveScroll: true });
  };

  actions['setup-choice'] = function (btn) {
    var form = btn.closest('form[data-form="start-exam"]');
    var group = btn.closest('[data-choice-group]');
    if (!form || !group) return;
    var name = btn.getAttribute('data-name');
    if (!name || !form.elements[name]) return;

    group.querySelectorAll('[data-action="setup-choice"]').forEach(function (choice) {
      choice.classList.toggle('is-active', choice === btn);
    });
    form.elements[name].value = btn.getAttribute('data-value');
    views.updateSetupSummary();
  };

  actions['discard-exam'] = function () {
    var exam = examOrNull();
    if (!exam) {
      A.refresh();
      return;
    }
    return A.confirm({
      title: 'Discard this test?',
      message: 'The open test and the answers saved in it will be deleted. Completed results are not affected.',
      confirmText: 'Discard test',
      danger: true
    }).then(function (ok) {
      if (!ok) return;
      A.state.activeExam = null;
      A.save();
      if (current && current.name === 'exam') A.go('#/home');
      else A.refresh();
      A.toast({ title: 'Test discarded', message: 'Your library and results were left untouched.', kind: 'info' });
    });
  };

  actions['exam-goto'] = function (btn) {
    gotoExam(btn.getAttribute('data-index'));
  };

  actions['exam-prev'] = function () {
    stepExam(-1);
  };

  actions['exam-next'] = function () {
    stepExam(1);
  };

  actions['exam-select'] = function (btn) {
    chooseExamOption(btn.getAttribute('data-index'));
  };

  actions['exam-clear'] = function () {
    clearExamAnswer();
  };

  actions['exam-flag'] = function () {
    toggleExamFlag();
  };

  actions['exam-lang'] = function (btn) {
    setExamLanguage(btn.getAttribute('data-value'));
  };

  actions['exam-submit'] = function () {
    submitExam();
  };

  actions['exam-quit'] = function () {
    var exam = examOrNull();
    if (!exam) {
      A.go('#/home');
      return;
    }
    return A.confirm({
      title: 'Leave this test?',
      message: 'Your answers are saved in this browser. ' +
        (exam.timeLimitSec ? 'The timer keeps running while you are away. ' : '') +
        'Resume from Home whenever you are ready.',
      confirmText: 'Leave test',
      cancelText: 'Keep working'
    }).then(function (ok) {
      if (!ok) return;
      A.go('#/home');
      A.toast({ title: 'Test saved', message: 'Resume it from Home when you are ready.', kind: 'info' });
    });
  };

  actions['review-filter'] = function (btn) {
    var value = btn.getAttribute('data-value') || 'all';
    A.ui.reviewFilter = value;
    document.querySelectorAll('.review-filters [data-action="review-filter"]').forEach(function (item) {
      item.classList.toggle('is-active', item.getAttribute('data-value') === value);
    });
    var host = document.getElementById('reviewList');
    var attempt = current && current.name === 'result' ? A.getAttempt(current.params.id) : null;
    if (host && attempt) host.innerHTML = views.reviewList(attempt);
  };

  actions['scroll-review'] = function () {
    var section = document.getElementById('reviewSection');
    if (section) section.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  actions['clear-attempts'] = function () {
    return A.confirm({
      title: 'Clear attempt history?',
      message: 'All saved results, scores and streaks will be removed. Your question library stays exactly as it is.',
      confirmText: 'Clear history',
      danger: true
    }).then(function (ok) {
      if (!ok) return;
      A.state.attempts = [];
      A.save();
      A.refresh();
      A.toast({ title: 'History cleared', message: 'Your library is unchanged.', kind: 'info' });
    });
  };

  actions['goal-step'] = function (btn) {
    var input = document.getElementById('profileGoal');
    if (!input) return;
    var delta = parseInt(btn.getAttribute('data-delta'), 10) || 0;
    var value = A.clamp((parseInt(input.value, 10) || 20) + delta, 5, 200);
    input.value = value;
    input.dispatchEvent(new Event('change', { bubbles: true }));
  };

  actions['profile-lang'] = function (btn) {
    var value = btn.getAttribute('data-value') === 'ml' ? 'ml' : 'en';
    var form = btn.closest('form');
    if (!form) return;
    var hidden = form.elements.language;
    if (hidden) hidden.value = value;
    form.querySelectorAll('.lang-choice').forEach(function (choice) {
      choice.classList.toggle('is-active', choice.getAttribute('data-value') === value);
    });
  };

  function settingsStatusText(form, provider) {
    if (provider === 'pages') {
      var proxy = A.gemini.proxy();
      if (!proxy.checked) return 'Run Test connection after saving to check the Cloudflare proxy.';
      if (proxy.ready) {
        return 'Cloudflare proxy is ready with ' + proxy.model + ' via ' +
          (proxy.provider === 'openai' ? 'an OpenAI-compatible endpoint.' : 'Gemini.');
      }
      return proxy.error || 'The Cloudflare proxy is not configured. Add the provider secret in Cloudflare Pages.';
    }

    if (provider === 'openai') {
      var url = String((form.elements.openaiUrl && form.elements.openaiUrl.value) || '').trim();
      var model = String((form.elements.openaiModel && form.elements.openaiModel.value) || '').trim();
      var valid = false;
      try { valid = !!A.gemini.openAIEndpoint(url); } catch (err) { valid = false; }
      return valid && model
        ? 'Custom API is ready with ' + model + '.'
        : 'Add a base URL and model to enable custom scanning.';
    }

    var key = String((form.elements.apiKey && form.elements.apiKey.value) || '').trim();
    var geminiModel = String((form.elements.model && form.elements.model.value) || '').trim();
    return key
      ? 'Gemini is ready with ' + geminiModel + '.'
      : 'Add a Gemini API key to enable scanning.';
  }

  function updateProxyPanel(form) {
    var proxy = A.gemini.proxy();
    var panel = form && form.querySelector('[data-provider-panel="pages"]');

    if (panel) {
      var backend = panel.querySelector('[data-proxy-backend]');
      if (backend) {
        backend.textContent = proxy.provider === 'openai'
          ? 'OpenAI-compatible'
          : proxy.provider === 'gemini'
            ? 'Gemini'
            : proxy.checked ? 'Not configured' : 'Not checked yet';
      }

      var model = panel.querySelector('[data-proxy-model]');
      if (model) model.textContent = proxy.model || (proxy.checked ? 'Not set' : 'Set in Pages');

      var host = panel.querySelector('[data-proxy-host]');
      if (host) host.textContent = proxy.host || '/api/scan';
    }

    if (!form || !form.elements.provider || form.elements.provider.value !== 'pages') return;

    var chip = form.querySelector('.settings-card-head .chip');
    if (chip) {
      var ready = proxy.checked && proxy.ready;
      chip.className = 'chip ' + (ready ? 'chip--success' : 'chip--amber');
      chip.textContent = ready ? 'Ready' : proxy.checked ? 'Not set' : 'Check';
    }
  }

  function selectSettingsProvider(form, value) {
    var provider = value === 'openai' ? 'openai' : value === 'pages' ? 'pages' : 'gemini';
    var hidden = form.elements.provider;
    if (hidden) hidden.value = provider;

    form.querySelectorAll('[data-action="settings-provider"]').forEach(function (choice) {
      var on = choice.getAttribute('data-value') === provider;
      choice.classList.toggle('is-active', on);
      choice.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
    form.querySelectorAll('[data-provider-panel]').forEach(function (panel) {
      panel.hidden = panel.getAttribute('data-provider-panel') !== provider;
    });

    var status = document.getElementById('settingsProviderStatus');
    if (status) status.textContent = settingsStatusText(form, provider);

    if (provider === 'pages') {
      if (status) status.textContent = 'Checking the Cloudflare proxy…';
      updateProxyPanel(form);
      A.gemini.refreshProxy(true).then(function () {
        if (status) status.textContent = settingsStatusText(form, 'pages');
        updateProxyPanel(form);
      });
    }

    var out = document.getElementById('keyTestResult');
    if (out) out.innerHTML = '';
  }

  actions['settings-provider'] = function (btn) {
    var form = btn.closest('form');
    if (form) selectSettingsProvider(form, btn.getAttribute('data-value'));
  };

  actions['export-data'] = function () {
    var payload = {
      app: 'padanam',
      kind: 'backup',
      version: A.VERSION,
      exportedAt: new Date().toISOString(),
      profile: A.clone(A.state.profile),
      settings: {
        provider: A.state.settings.provider,
        model: A.state.settings.model,
        openaiUrl: A.state.settings.openaiUrl,
        openaiModel: A.state.settings.openaiModel
      },
      sets: A.clone(A.state.sets),
      attempts: A.clone(A.state.attempts),
      meta: { createdAt: A.state.meta.createdAt }
    };
    var filename = 'padanam-backup-' + A.todayKey() + '.json';
    A.download(filename, JSON.stringify(payload, null, 2), 'application/json');
    A.toast({
      title: 'Backup downloaded',
      message: 'The file has ' + A.plural(payload.sets.length, 'set') + ' and ' + A.plural(payload.attempts.length, 'attempt') + '. Your API keys are not included.',
      kind: 'success'
    });
  };

  actions['import-data'] = function () {
    var input = document.getElementById('importFile');
    if (input) input.click();
  };

  actions['reset-data'] = function () {
    return A.confirm({
      title: 'Reset Padanam?',
      message: 'This removes every set, attempt and profile detail from this browser, and clears saved Gemini and custom API keys. Export a backup first if you may need any of it.',
      confirmText: 'Reset everything',
      danger: true
    }).then(function (ok) {
      if (!ok) return;
      A.resetAll(false);
      A.state.meta.seeded = true;
      A.state.activeExam = null;
      A.save();
      views.scanClear();
      A.go('#/home');
      A.toast({ title: 'Padanam was reset', message: 'Start fresh with a scan, a manual set or the demo library.', kind: 'info' });
    });
  };

  actions['toggle-key'] = function (btn) {
    var targetId = (btn && btn.getAttribute('data-target')) || 'apiKey';
    var input = document.getElementById(targetId);
    var isCustom = targetId === 'openaiKey';
    if (isCustom) A.ui.showOpenaiKey = !A.ui.showOpenaiKey;
    else A.ui.showKey = !A.ui.showKey;
    var shown = isCustom ? A.ui.showOpenaiKey : A.ui.showKey;
    if (input) input.type = shown ? 'text' : 'password';
    if (btn) btn.setAttribute('aria-label', shown ? 'Hide API key' : 'Show API key');
  };

  actions['test-key'] = function (btn) {
    var form = document.getElementById('settingsForm');
    var out = document.getElementById('keyTestResult');
    if (!form || !out) return Promise.resolve();

    var providerValue = form.elements.provider ? form.elements.provider.value : 'gemini';
    var provider = providerValue === 'openai' ? 'openai' : providerValue === 'pages' ? 'pages' : 'gemini';
    var original = btn.innerHTML;

    function start(detail) {
      btn.disabled = true;
      btn.innerHTML = '<span class="spinner spinner--sm"></span> Testing…';
      out.innerHTML = noteBox('', 'spark', detail);
    }

    function finish() {
      btn.disabled = false;
      btn.innerHTML = original;
    }

    if (provider === 'pages') {
      start('Contacting this site’s <b>/api/scan</b> function. The key stays in Cloudflare…');

      return A.gemini.testProxy().then(function (result) {
        var status = document.getElementById('settingsProviderStatus');
        if (status) status.textContent = settingsStatusText(form, 'pages');
        updateProxyPanel(form);
        out.innerHTML = noteBox('success', 'check-circle',
          'The Cloudflare proxy works with <b>' + A.esc(result.model || 'the server model') + '</b> via <b>' + A.esc(result.host || 'this site') + '</b>. It replied “' + A.esc(String(result.text || 'OK').slice(0, 30)) + '”.');
        A.toast({ title: 'Cloudflare proxy works', message: 'The API key stays in Pages secrets.', kind: 'success' });
      }).catch(function (err) {
        var status = document.getElementById('settingsProviderStatus');
        if (status) status.textContent = settingsStatusText(form, 'pages');
        updateProxyPanel(form);
        out.innerHTML = noteBox('danger', 'alert', A.esc(err && err.message ? err.message : 'The Cloudflare proxy could not be tested.'));
      }).then(finish);
    }

    if (provider === 'openai') {
      var baseUrl = String(form.elements.openaiUrl.value || '').trim();
      var customKey = String(form.elements.openaiKey.value || '').trim();
      var customModel = String(form.elements.openaiModel.value || '').trim();

      if (!baseUrl) {
        out.innerHTML = noteBox('danger', 'alert', 'Enter the OpenAI-compatible base URL first.');
        return Promise.resolve();
      }
      if (!customModel) {
        out.innerHTML = noteBox('danger', 'alert', 'Enter a model name first.');
        return Promise.resolve();
      }

      var endpoint, host = baseUrl;
      try {
        endpoint = A.gemini.openAIEndpoint(baseUrl);
        host = new URL(endpoint).host;
      } catch (err) {
        out.innerHTML = noteBox('danger', 'alert', A.esc(err.message));
        return Promise.resolve();
      }

      start('Contacting <b>' + A.esc(host) + '</b>. This sends one tiny text request…');

      return A.gemini.testOpenAI({ baseUrl: baseUrl, apiKey: customKey, model: customModel }).then(function (result) {
        out.innerHTML = noteBox('success', 'check-circle',
          'The endpoint works with <b>' + A.esc(customModel) + '</b>. It replied “' + A.esc(String(result.text || 'OK').slice(0, 30)) + '”.');
        A.toast({ title: 'Connection works', message: 'You can scan papers with ' + customModel + '.', kind: 'success' });
      }).catch(function (err) {
        out.innerHTML = noteBox('danger', 'alert', A.esc(err && err.message ? err.message : 'The endpoint could not be tested.'));
      }).then(finish);
    }

    var apiKey = String(form.elements.apiKey.value || '').trim();
    var model = String(form.elements.model.value || 'gemini-2.5-flash').trim();
    if (!apiKey) {
      out.innerHTML = noteBox('danger', 'alert', 'Enter a Gemini API key first.');
      return Promise.resolve();
    }

    start('Contacting Gemini. This sends one tiny request…');

    return A.gemini.testKey({ apiKey: apiKey, model: model }).then(function (result) {
      out.innerHTML = noteBox('success', 'check-circle',
        'The key works with <b>' + A.esc(model) + '</b>. Gemini replied “' + A.esc(String(result.text || 'OK').slice(0, 30)) + '”.');
      A.toast({ title: 'Key works', message: 'You can scan papers with ' + model + '.', kind: 'success' });
    }).catch(function (err) {
      out.innerHTML = noteBox('danger', 'alert', A.esc(err && err.message ? err.message : 'The key could not be tested.'));
    }).then(finish);
  };

  actions['scan-remove'] = function (btn) {
    views.scanRemove(parseInt(btn.getAttribute('data-index'), 10));
  };

  actions['scan-pick'] = function () {
    var input = document.getElementById('scanFiles');
    if (input) input.click();
  };

  actions['scan-camera'] = function () {
    var input = document.getElementById('scanCamera');
    if (input) input.click();
  };

  actions['scan-run'] = function (btn) {
    return views.scanRun(btn.getAttribute('data-demo') === '1');
  };

  actions['scan-demo'] = function () {
    return views.scanRun(true);
  };

  actions['scan-save'] = function (btn) {
    views.scanSave(btn.getAttribute('data-start') === '1');
  };

  actions['scan-discard'] = function () {
    return A.confirm({
      title: 'Discard this draft?',
      message: 'The extracted questions and the photos in this draft will be removed. Nothing has been added to your library yet.',
      confirmText: 'Discard draft',
      danger: true
    }).then(function (ok) {
      if (!ok) return;
      views.scanClear();
      A.toast({ title: 'Draft discarded', message: 'Your library was not changed.', kind: 'info' });
    });
  };

  /* ── Forms ───────────────────────────────────────────── */
  function field(form, name) {
    var el = form && form.elements ? form.elements[name] : null;
    return el && typeof el.value === 'string' ? el.value : '';
  }

  function trim(value) {
    return String(value || '').trim();
  }

  function reportError(err) {
    console.error('Padanam action error', err);
    A.toast({
      title: 'Something went wrong',
      message: err && err.message ? err.message : 'Please try that again.',
      kind: 'error'
    });
  }

  function saveSetForm(form) {
    var id = field(form, 'setId');
    var title = trim(field(form, 'title'));
    var titleMl = trim(field(form, 'titleMl'));
    var subject = trim(field(form, 'subject')) || 'General Knowledge';

    if (!title) {
      A.toast({ title: 'Add a title', message: 'Give this set a short name so you can find it later.', kind: 'error' });
      return;
    }

    if (id) {
      var updated = A.updateSet(id, { title: title, titleMl: titleMl, subject: subject });
      if (!updated) {
        A.toast({ title: 'Set not found', message: 'It may have been removed in another tab.', kind: 'error' });
        A.closeModal();
        return;
      }
      A.closeModal();
      A.refresh();
      A.toast({ title: 'Set updated', message: 'Your changes are saved.', kind: 'success' });
      return;
    }

    var now = new Date().toISOString();
    var set = {
      id: A.uid('set'),
      title: title,
      titleMl: titleMl,
      subject: subject,
      source: 'manual',
      createdAt: now,
      updatedAt: now,
      questions: []
    };
    A.addSet(set);
    A.closeModal();
    A.toast({
      title: 'Set created',
      message: 'Add questions by hand, or scan a paper to fill it.',
      kind: 'success'
    });
    A.go('#/set/' + set.id);
  }

  function saveQuestionForm(form) {
    var setId = field(form, 'setId');
    var set = A.getSet(setId);
    if (!set) {
      A.toast({ title: 'Set not found', message: 'The set may have been removed in another tab.', kind: 'error' });
      A.closeModal();
      return;
    }

    var qid = field(form, 'qid');
    var count = A.clamp(parseInt(field(form, 'optionCount'), 10) || 4, 2, 6);
    var stemEn = trim(field(form, 'stemEn'));
    var enOptions = [];
    var mlOptions = [];

    for (var i = 0; i < count; i++) {
      enOptions.push(trim(field(form, 'optEn' + i)));
      mlOptions.push(trim(field(form, 'optMl' + i)));
    }

    if (!stemEn) {
      A.toast({ title: 'Add the question text', message: 'The English question is required.', kind: 'error' });
      return;
    }
    if (enOptions.some(function (option) { return !option; })) {
      A.toast({ title: 'Fill every option', message: 'Every English option in the editor needs text.', kind: 'error' });
      return;
    }

    var answer = A.clamp(parseInt(field(form, 'answer'), 10) || 0, 0, enOptions.length - 1);
    var mlStem = trim(field(form, 'stemMl'));
    var mlFilled = mlOptions.filter(Boolean).length;
    var ml = null;

    if (mlStem || mlFilled) {
      if (!mlStem || mlOptions.some(function (option) { return !option; })) {
        A.toast({
          title: 'Finish the Malayalam version',
          message: 'Fill the Malayalam question and all of its options, or clear the whole section.',
          kind: 'error'
        });
        return;
      }
      ml = {
        stem: mlStem,
        options: mlOptions,
        explanation: trim(field(form, 'explainMl'))
      };
    }

    var question = {
      id: qid || A.uid('q'),
      answer: answer,
      en: {
        stem: stemEn,
        options: enOptions,
        explanation: trim(field(form, 'explainEn'))
      },
      ml: ml
    };

    if (qid) {
      var index = set.questions.findIndex(function (item) { return item.id === qid; });
      if (index < 0) {
        A.toast({ title: 'Question not found', message: 'It may have been removed in another tab.', kind: 'error' });
        A.closeModal();
        return;
      }
      set.questions[index] = question;
    } else {
      set.questions.push(question);
    }

    set.updatedAt = new Date().toISOString();
    A.save();
    A.closeModal();
    A.refresh();
    A.toast({
      title: qid ? 'Question updated' : 'Question added',
      message: set.title + ' now has ' + A.plural(set.questions.length, 'question') + '.',
      kind: 'success'
    });
  }

  function saveProfileForm(form) {
    var name = trim(field(form, 'name'));
    var target = trim(field(form, 'target')) || 'Kerala PSC';
    var examDate = trim(field(form, 'examDate'));
    var goal = A.clamp(parseInt(field(form, 'dailyGoal'), 10) || 20, 5, 200);
    var language = field(form, 'language') === 'ml' ? 'ml' : 'en';

    A.state.profile.name = name;
    A.state.profile.target = target;
    A.state.profile.examDate = examDate;
    A.state.profile.dailyGoal = goal;
    A.state.profile.language = language;
    A.save();
    A.refresh();
    A.toast({
      title: 'Profile saved',
      message: name ? 'Welcome, ' + name.split(/\s+/)[0] + '.' : 'Your preferences are updated.',
      kind: 'success'
    });
  }

  function saveSettingsForm(form) {
    var providerValue = field(form, 'provider');
    var provider = providerValue === 'openai' ? 'openai' : providerValue === 'pages' ? 'pages' : 'gemini';
    var apiKey = trim(field(form, 'apiKey'));
    var model = trim(field(form, 'model')) || 'gemini-2.5-flash';
    var openaiUrl = trim(field(form, 'openaiUrl'));
    var openaiKey = trim(field(form, 'openaiKey'));
    var openaiModel = trim(field(form, 'openaiModel'));

    if (provider === 'openai' && openaiUrl) {
      try {
        A.gemini.openAIEndpoint(openaiUrl);
      } catch (err) {
        A.toast({ title: 'Check the API URL', message: err.message, kind: 'error' });
        var urlInput = form.elements.openaiUrl;
        if (urlInput) {
          try { urlInput.focus(); } catch (ignore) { /* focus is best-effort */ }
        }
        return;
      }
    }

    A.state.settings.provider = provider;
    A.state.settings.apiKey = apiKey;
    A.state.settings.model = model;
    A.state.settings.openaiUrl = openaiUrl;
    A.state.settings.openaiKey = openaiKey;
    A.state.settings.openaiModel = openaiModel;
    A.save();
    A.refresh();

    var active = A.gemini.provider(A.state.settings);
    var message;
    var kind = active.ready ? 'success' : 'info';
    if (provider === 'pages') {
      var proxy = A.gemini.proxy();
      if (!proxy.checked) {
        message = 'Cloudflare proxy selected. Run Test connection to check the Pages secrets.';
        kind = 'info';
      } else if (proxy.ready) {
        message = 'Cloudflare proxy is ready with ' + proxy.model + ' via ' + proxy.host + '.';
      } else {
        message = proxy.error || 'The Cloudflare proxy is not configured. Add the provider secret in Cloudflare Pages.';
        kind = 'info';
      }
    } else if (provider === 'openai') {
      message = active.ready
        ? 'Custom scanning is ready with ' + openaiModel + ' via ' + active.host + '.'
        : !openaiUrl
          ? 'Add an OpenAI-compatible base URL to enable custom scanning.'
          : !openaiModel
            ? 'Add a model name to enable custom scanning.'
            : 'Custom scanning is not ready yet.';
    } else {
      message = apiKey
        ? 'Gemini scanning is ready with ' + model + '.'
        : 'No key saved. Scanning will use the sample until you add one.';
    }

    A.toast({
      title: 'Settings saved',
      message: message,
      kind: kind
    });
  }

  function shuffleList(list) {
    for (var i = list.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var tmp = list[i];
      list[i] = list[j];
      list[j] = tmp;
    }
    return list;
  }

  function startExam(form) {
    var setId = form.getAttribute('data-set-id');
    var set = A.getSet(setId);
    if (!set || !set.questions.length) {
      A.toast({ title: 'This set has no questions', message: 'Add or scan questions before starting a test.', kind: 'error' });
      return Promise.resolve();
    }

    var mode = field(form, 'mode') === 'practice' ? 'practice' : 'mock';
    var count = A.clamp(parseInt(field(form, 'count'), 10) || set.questions.length, 1, set.questions.length);
    var lang = field(form, 'lang') === 'ml' ? 'ml' : 'en';
    var pace = parseInt(field(form, 'pace'), 10);
    if (isNaN(pace) || pace < 10 || pace > 300) pace = 45;
    var shuffle = !!(form.elements.shuffle && form.elements.shuffle.checked);

    function build() {
      var questions = A.clone(set.questions);
      if (shuffle && questions.length > 1) shuffleList(questions);
      if (questions.length > count) questions = questions.slice(0, count);

      A.state.activeExam = {
        id: A.uid('exam'),
        setId: set.id,
        setTitle: set.title,
        titleMl: set.titleMl || '',
        subject: set.subject,
        mode: mode,
        lang: lang,
        startedAt: new Date().toISOString(),
        current: 0,
        questions: questions,
        responses: questions.map(function () { return null; }),
        flags: questions.map(function () { return false; }),
        timeLimitSec: mode === 'mock' ? questions.length * pace : null
      };
      examFinishing = false;
      A.save();
      A.go('#/exam/run');
      A.toast({
        title: mode === 'mock' ? 'Mock test started' : 'Practice started',
        message: questions.length + ' ' + (questions.length === 1 ? 'question' : 'questions') +
          (mode === 'mock' ? ' · ' + A.fmtMinutes(questions.length * pace) + ' on the clock' : ' · untimed'),
        kind: 'success'
      });
    }

    var active = A.state.activeExam;
    if (!active) {
      build();
      return Promise.resolve();
    }

    return A.confirm({
      title: 'Replace the open test?',
      message: 'You already have an unfinished ' + (active.mode === 'practice' ? 'practice run' : 'mock test') +
        ' on “' + active.setTitle + '”. Starting this test will discard it.',
      confirmText: 'Replace test',
      cancelText: 'Keep open test'
    }).then(function (ok) {
      if (ok) build();
    });
  }

  function handleImportFile(file) {
    if (!file) return Promise.resolve();

    return A.readFileText(file).then(function (text) {
      var parsed;
      try {
        parsed = JSON.parse(text);
      } catch (err) {
        throw new Error('That file is not valid JSON. Choose a Padanam backup file.');
      }
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        throw new Error('That file does not look like a Padanam backup.');
      }

      var source = parsed.data && typeof parsed.data === 'object' ? parsed.data : parsed;
      var recognised = Array.isArray(source.sets) || Array.isArray(source.attempts) || (source.profile && typeof source.profile === 'object');
      if (!recognised) {
        throw new Error('That file does not contain a Padanam library. Choose a backup made from Settings or Profile.');
      }

      var normalized = A.normalizeState(source);
      var setCount = normalized.sets.length;
      var attemptCount = normalized.attempts.length;

      return A.confirm({
        title: 'Import this backup?',
        message: '“' + file.name + '” has ' + A.plural(setCount, 'set') + ' and ' + A.plural(attemptCount, 'attempt') +
          '. Importing replaces your current library, results and profile. Your saved API keys stay in this browser.',
        confirmText: 'Import backup',
        danger: true
      }).then(function (ok) {
        if (!ok) return;

        normalized.settings.apiKey = A.state.settings.apiKey;
        normalized.settings.openaiKey = A.state.settings.openaiKey;
        normalized.activeExam = null;
        normalized.meta.seeded = true;
        A.state = normalized;
        A.save();

        A.ui.library.q = '';
        A.ui.library.subject = 'all';
        A.ui.library.source = 'all';
        A.ui.library.sort = 'recent';
        A.ui.setLang = null;
        A.ui.reviewFilter = 'all';
        views.scanClear();
        A.go('#/home');
        A.toast({
          title: 'Backup imported',
          message: A.plural(setCount, 'set') + ' and ' + A.plural(attemptCount, 'attempt') + ' restored.',
          kind: 'success'
        });
      });
    });
  }

  /* ── Events ──────────────────────────────────────────── */
  function onClick(event) {
    var target = event.target;
    if (!target || !target.closest) return;
    var btn = target.closest('[data-action]');
    if (!btn) return;
    if (btn.disabled) return;

    var name = btn.getAttribute('data-action');
    var handler = actions[name];
    if (!handler) return;

    event.preventDefault();
    var job;
    try {
      job = handler(btn, event);
    } catch (err) {
      reportError(err);
      return;
    }
    if (job && typeof job.catch === 'function') job.catch(reportError);
  }

  function onSubmit(event) {
    var target = event.target;
    if (!target || !target.closest) return;
    var form = target.closest('form[data-form]');
    if (!form) return;

    var kind = form.getAttribute('data-form');
    var handler = null;
    if (kind === 'set') handler = saveSetForm;
    else if (kind === 'question') handler = saveQuestionForm;
    else if (kind === 'profile') handler = saveProfileForm;
    else if (kind === 'settings') handler = saveSettingsForm;
    else if (kind === 'start-exam') handler = startExam;
    if (!handler) return;

    event.preventDefault();
    var job;
    try {
      job = handler(form);
    } catch (err) {
      reportError(err);
      return;
    }
    if (job && typeof job.catch === 'function') job.catch(reportError);
  }

  function onInput(event) {
    var target = event.target;
    if (!target || !target.matches) return;

    if (target.matches('[data-library-search]')) {
      var value = target.value;
      window.clearTimeout(searchTimer);
      searchTimer = window.setTimeout(function () {
        A.ui.library.q = value;
        refreshLibraryUI();
      }, 120);
      return;
    }

    if (target.matches('[data-scan-field]')) {
      views.scanField(target.getAttribute('data-scan-field'), target.value);
    }
  }

  function onChange(event) {
    var target = event.target;
    if (!target || !target.matches) return;

    if (target.matches('[data-library-subject]')) {
      A.ui.library.subject = target.value || 'all';
      refreshLibraryUI();
      return;
    }

    if (target.matches('[data-library-sort]')) {
      A.ui.library.sort = target.value || 'recent';
      refreshLibraryUI();
      return;
    }

    if (target.matches('[data-scan-field]')) {
      views.scanField(target.getAttribute('data-scan-field'), target.value);
      return;
    }

    if (target.id === 'scanFiles' || target.id === 'scanCamera') {
      var files = Array.prototype.slice.call(target.files || []);
      target.value = '';
      if (files.length) {
        Promise.resolve(views.scanAddFiles(files)).catch(reportError);
      }
      return;
    }

    if (target.id === 'importFile') {
      var file = target.files && target.files[0];
      target.value = '';
      if (file) handleImportFile(file).catch(reportError);
      return;
    }

    if (target.id === 'answerSelect') {
      var form = target.form;
      var grid = form ? form.querySelector('.option-input-grid') : null;
      if (grid) {
        var answer = parseInt(target.value, 10);
        grid.querySelectorAll('.option-input').forEach(function (row, index) {
          row.classList.toggle('is-correct', index === answer);
        });
      }
    }
  }

  function onKeydown(event) {
    if (event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey) return;
    if (!current || current.name !== 'exam') return;
    if (document.body.classList.contains('modal-open')) return;

    var target = event.target;
    var tag = target && target.tagName ? target.tagName : '';
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || (target && target.isContentEditable)) return;

    var key = event.key;
    if ((key === 'Enter' || key === ' ') && target && target.closest &&
        target.closest('button, a, summary, [role="button"]')) {
      return;
    }

    var exam = examOrNull();
    if (!exam) return;

    if (key === 'ArrowRight') {
      event.preventDefault();
      stepExam(1);
      return;
    }
    if (key === 'ArrowLeft') {
      event.preventDefault();
      stepExam(-1);
      return;
    }
    if (key === 'f' || key === 'F') {
      event.preventDefault();
      toggleExamFlag();
      return;
    }
    if (key === 'Enter') {
      event.preventDefault();
      if (exam.current < exam.questions.length - 1) stepExam(1);
      else submitExam();
      return;
    }
    if (/^[1-8]$/.test(key)) {
      var choice = parseInt(key, 10) - 1;
      var question = exam.questions[exam.current];
      if (question && choice < question.en.options.length) {
        event.preventDefault();
        chooseExamOption(choice);
      }
    }
  }

  /* ── Start ───────────────────────────────────────────── */
  function init() {
    viewEl = document.getElementById('view');
    if (!viewEl) return;

    A.onRefresh = function () {
      render({ preserveScroll: true });
    };

    document.addEventListener('click', onClick);
    document.addEventListener('submit', onSubmit);
    document.addEventListener('input', onInput);
    document.addEventListener('change', onChange);
    document.addEventListener('keydown', onKeydown);
    document.addEventListener('visibilitychange', function () {
      if (!document.hidden) updateClock();
    });
    window.addEventListener('focus', updateClock);
    window.addEventListener('hashchange', function () {
      render({ routeChange: true });
    });

    render({ routeChange: true });
    clock = window.setInterval(updateClock, 1000);

    if ('serviceWorker' in navigator && /^https?:$/.test(window.location.protocol)) {
      window.addEventListener('load', function () {
        navigator.serviceWorker.register('./sw.js').catch(function () {
          /* Offline caching is a bonus; the app still works online without it. */
        });
      });
    }
  }

  init();
})();
