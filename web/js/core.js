/* =========================================================
   Padanam web — state, persistence, shared helpers, overlays
   ========================================================= */
(function () {
  'use strict';

  var A = window.Padanam = window.Padanam || {};
  var Seed = window.PadanamSeed;

  A.VERSION = 1;
  A.STORAGE_KEY = 'padanam.web.v1';
  A.storageScope = 'local';
  A.PASS_MARK = 40;

  A.storageKey = function (scope) {
    var value = scope === undefined ? A.storageScope : scope;
    return value && value !== 'local'
      ? A.STORAGE_KEY + '.' + value
      : A.STORAGE_KEY;
  };

  A.tombstoneKey = function (scope) {
    var value = scope === undefined ? A.storageScope : scope;
    return 'padanam.tombstones.v1' + (value && value !== 'local' ? '.' + value : '');
  };

  A.setStorageScope = function (scope) {
    A.storageScope = String(scope || 'local');
    return A.storageScope;
  };

  A.migrateLegacyState = function (scope) {
    if (!scope || scope === 'local') return false;
    try {
      var target = A.storageKey(scope);
      if (window.localStorage.getItem(target)) return false;
      var source = window.localStorage.getItem(A.STORAGE_KEY);
      if (!source) return false;
      window.localStorage.setItem(target, source);

      var sourceTombstones = window.localStorage.getItem(A.tombstoneKey('local'));
      var targetTombstones = window.localStorage.getItem(A.tombstoneKey(scope));
      if (sourceTombstones && !targetTombstones) {
        window.localStorage.setItem(A.tombstoneKey(scope), sourceTombstones);
      }
      return true;
    } catch (err) {
      return false;
    }
  };

  /* ── Small utilities ─────────────────────────────────── */
  A.uid = function (prefix) {
    return (prefix || 'id') + '_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
  };

  A.esc = function (value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  };

  A.text = function (value) {
    return A.esc(value).replace(/\r?\n/g, '<br>');
  };

  A.icon = function (name, cls) {
    return '<svg class="icon' + (cls ? ' ' + cls : '') + '" aria-hidden="true" focusable="false"><use href="#i-' + name + '"/></svg>';
  };

  A.clone = function (value) {
    return JSON.parse(JSON.stringify(value));
  };

  A.clamp = function (n, min, max) {
    return Math.min(max, Math.max(min, n));
  };

  A.plural = function (n, word, plural) {
    var form = n === 1 ? word : (plural || word + 's');
    return n + ' ' + form;
  };

  A.initials = function (name) {
    var clean = String(name || '').trim();
    if (!clean) return 'P';
    var parts = clean.split(/\s+/).filter(Boolean);
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  };

  A.optionLetter = function (i) {
    return 'ABCDEFGH'[i] || String(i + 1);
  };

  A.subjectMl = function (name) {
    var found = (Seed.subjects || []).filter(function (s) { return s.name === name; })[0];
    return found ? found.ml : '';
  };

  A.sourceLabel = function (source) {
    return source === 'scan' ? 'Scanned' : source === 'manual' ? 'Manual' : 'Demo';
  };

  A.sourceChip = function (source) {
    var cls = source === 'scan' ? 'chip--indigo' : source === 'manual' ? 'chip--plain' : 'chip--amber';
    return '<span class="chip ' + cls + '">' + A.sourceLabel(source) + '</span>';
  };

  /* ── Dates & formatting ──────────────────────────────── */
  A.pad = function (n) { return String(n).padStart(2, '0'); };

  A.localKey = function (date) {
    var d = date instanceof Date ? date : new Date(date);
    if (isNaN(d.getTime())) return '';
    return d.getFullYear() + '-' + A.pad(d.getMonth() + 1) + '-' + A.pad(d.getDate());
  };

  A.todayKey = function () { return A.localKey(new Date()); };

  A.parseLocal = function (value) {
    if (!value) return null;
    var d = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(value + 'T00:00:00') : new Date(value);
    return isNaN(d.getTime()) ? null : d;
  };

  A.fmtDate = function (value) {
    var d = A.parseLocal(value);
    if (!d) return '—';
    return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  };

  A.fmtDateShort = function (value) {
    var d = A.parseLocal(value);
    if (!d) return '—';
    return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
  };

  A.fmtDateTime = function (value) {
    var d = A.parseLocal(value);
    if (!d) return '—';
    return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }) +
      ' · ' + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  };

  A.fmtRelative = function (value) {
    var d = A.parseLocal(value);
    if (!d) return '—';
    var diff = Date.now() - d.getTime();
    if (diff < 0) return A.fmtDateShort(value);
    var mins = Math.floor(diff / 60000);
    if (mins < 1) return 'Just now';
    if (mins < 60) return mins + 'm ago';
    var hrs = Math.floor(mins / 60);
    if (hrs < 24) return hrs + 'h ago';
    var days = Math.floor(hrs / 24);
    if (days === 1) return 'Yesterday';
    if (days < 7) return days + 'd ago';
    return A.fmtDateShort(value);
  };

  A.fmtDuration = function (seconds) {
    if (seconds === null || seconds === undefined || isNaN(seconds)) return '—';
    var s = Math.max(0, Math.round(seconds));
    var h = Math.floor(s / 3600);
    var m = Math.floor((s % 3600) / 60);
    var sec = s % 60;
    if (h > 0) return h + ':' + A.pad(m) + ':' + A.pad(sec);
    return m + ':' + A.pad(sec);
  };

  A.fmtMinutes = function (seconds) {
    if (!seconds) return '0 min';
    if (seconds < 60) return seconds + ' sec';
    var mins = Math.round(seconds / 60 * 10) / 10;
    return (mins % 1 === 0 ? mins.toFixed(0) : mins.toFixed(1)) + ' min';
  };

  A.daysUntil = function (value) {
    var d = A.parseLocal(value);
    if (!d) return null;
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    d.setHours(0, 0, 0, 0);
    return Math.round((d.getTime() - today.getTime()) / 86400000);
  };

  A.greeting = function () {
    var h = new Date().getHours();
    return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  };

  /* ── State ───────────────────────────────────────────── */
  function defaults() {
    return {
      version: A.VERSION,
      profile: {
        name: '',
        target: 'Kerala PSC',
        examDate: '',
        dailyGoal: 20,
        language: 'en'
      },
      settings: {
        provider: 'gemini',
        apiKey: '',
        model: 'gemini-2.5-flash',
        openaiUrl: '',
        openaiKey: '',
        openaiModel: ''
      },
      sets: [],
      attempts: [],
      activeExam: null,
      meta: {
        seeded: false,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        profileUpdatedAt: new Date().toISOString(),
        attemptsClearedAt: '',
        syncEpochMs: 0,
        lastSyncAt: ''
      }
    };
  }

  function normalizeQuestion(q) {
    if (!q || typeof q !== 'object' || !q.en || typeof q.en !== 'object' ||
        !Array.isArray(q.en.options) || !q.en.stem) return null;

    var options = q.en.options.map(function (o) { return String(o || '').trim(); }).filter(Boolean);
    if (options.length < 2) return null;

    var answer = parseInt(q.answer, 10);
    if (isNaN(answer) || answer < 0 || answer >= options.length) answer = 0;

    var ml = null;
    if (q.ml && q.ml.stem && Array.isArray(q.ml.options) && q.ml.options.length === options.length) {
      ml = {
        stem: String(q.ml.stem),
        options: q.ml.options.map(function (o) { return String(o || '').trim(); }),
        explanation: String(q.ml.explanation || '')
      };
    }

    return {
      id: q.id || A.uid('q'),
      answer: answer,
      en: {
        stem: String(q.en.stem),
        options: options,
        explanation: String(q.en.explanation || '')
      },
      ml: ml
    };
  }

  function normalizeResponses(responses, questions) {
    return responses.map(function (r, index) {
      var n = parseInt(r, 10);
      if (isNaN(n) || n < 0 || n >= questions[index].en.options.length) return null;
      return n;
    });
  }

  function normalizeActiveExam(exam) {
    if (!exam || typeof exam !== 'object' ||
        !Array.isArray(exam.questions) || !Array.isArray(exam.responses) ||
        !exam.questions.length || exam.questions.length !== exam.responses.length) return null;

    var questions = exam.questions.map(normalizeQuestion);
    if (questions.some(function (q) { return !q; })) return null;

    var responses = normalizeResponses(exam.responses, questions);
    var flags = Array.isArray(exam.flags) ? exam.flags : [];
    var current = parseInt(exam.current, 10);
    if (isNaN(current)) current = 0;
    var timeLimit = parseInt(exam.timeLimitSec, 10);
    var started = new Date(exam.startedAt).getTime();
    var mode = exam.mode === 'practice' ? 'practice' : 'mock';

    return {
      id: exam.id || A.uid('exam'),
      setId: String(exam.setId || ''),
      setTitle: String(exam.setTitle || 'Practice test'),
      titleMl: String(exam.titleMl || ''),
      subject: String(exam.subject || 'General Knowledge'),
      mode: mode,
      lang: exam.lang === 'ml' ? 'ml' : 'en',
      startedAt: isNaN(started) ? new Date().toISOString() : exam.startedAt,
      current: A.clamp(current, 0, questions.length - 1),
      questions: questions,
      responses: responses,
      flags: questions.map(function (q, index) { return !!flags[index]; }),
      timeLimitSec: mode === 'mock' && timeLimit > 0 ? timeLimit : null
    };
  }

  function normalize(state) {
    var base = defaults();
    if (!state || typeof state !== 'object') return base;
    var out = base;

    if (state.profile && typeof state.profile === 'object') {
      Object.keys(base.profile).forEach(function (key) {
        if (state.profile[key] !== undefined && state.profile[key] !== null) out.profile[key] = state.profile[key];
      });
    }
    if (state.settings && typeof state.settings === 'object') {
      Object.keys(base.settings).forEach(function (key) {
        if (typeof state.settings[key] === 'string') out.settings[key] = state.settings[key];
      });
    }
    out.profile.dailyGoal = A.clamp(parseInt(out.profile.dailyGoal, 10) || 20, 1, 500);
    out.profile.language = out.profile.language === 'ml' ? 'ml' : 'en';
    out.settings.provider = out.settings.provider === 'openai'
      ? 'openai'
      : out.settings.provider === 'pages'
        ? 'pages'
        : 'gemini';
    out.settings.apiKey = String(out.settings.apiKey || '').trim();
    out.settings.model = String(out.settings.model || '').trim() || 'gemini-2.5-flash';
    out.settings.openaiUrl = String(out.settings.openaiUrl || '').trim();
    out.settings.openaiKey = String(out.settings.openaiKey || '').trim();
    out.settings.openaiModel = String(out.settings.openaiModel || '').trim();

    if (Array.isArray(state.sets)) {
      out.sets = state.sets.map(function (set) {
        if (!set || typeof set !== 'object') return null;
        var questions = Array.isArray(set.questions) ? set.questions.map(normalizeQuestion).filter(Boolean) : [];
        return {
          id: set.id || A.uid('set'),
          title: String(set.title || 'Untitled set'),
          titleMl: String(set.titleMl || ''),
          subject: String(set.subject || 'General Knowledge'),
          source: ['demo', 'manual', 'scan'].indexOf(set.source) >= 0 ? set.source : 'manual',
          createdAt: set.createdAt || new Date().toISOString(),
          updatedAt: set.updatedAt || set.createdAt || new Date().toISOString(),
          questions: questions
        };
      }).filter(Boolean);
    }

    if (Array.isArray(state.attempts)) {
      out.attempts = state.attempts.map(function (a) {
        if (!a || !a.id || !Array.isArray(a.questions) || !Array.isArray(a.responses) ||
            !a.questions.length || a.questions.length !== a.responses.length) return null;
        var questions = a.questions.map(normalizeQuestion);
        if (questions.some(function (q) { return !q; })) return null;

        return {
          id: a.id,
          setId: a.setId || '',
          setTitle: a.setTitle || 'Practice test',
          titleMl: a.titleMl || '',
          subject: a.subject || 'General Knowledge',
          mode: a.mode === 'practice' ? 'practice' : 'mock',
          lang: a.lang === 'ml' ? 'ml' : 'en',
          startedAt: a.startedAt || a.finishedAt || new Date().toISOString(),
          finishedAt: a.finishedAt || new Date().toISOString(),
          durationSec: Math.max(0, parseInt(a.durationSec, 10) || 0),
          timeLimitSec: a.timeLimitSec ? parseInt(a.timeLimitSec, 10) : null,
          questions: questions,
          responses: normalizeResponses(a.responses, questions),
          flags: questions.map(function (q, index) {
            return !!(Array.isArray(a.flags) && a.flags[index]);
          })
        };
      }).filter(Boolean);
    }

    out.activeExam = normalizeActiveExam(state.activeExam);
    out.meta = base.meta;
    if (state.meta && typeof state.meta === 'object') {
      out.meta.seeded = !!state.meta.seeded;
      out.meta.createdAt = state.meta.createdAt || base.meta.createdAt;
      out.meta.updatedAt = state.meta.updatedAt || base.meta.updatedAt;
      out.meta.profileUpdatedAt = Date.parse(state.meta.profileUpdatedAt)
        ? state.meta.profileUpdatedAt
        : out.meta.updatedAt;
      out.meta.attemptsClearedAt = Date.parse(state.meta.attemptsClearedAt)
        ? state.meta.attemptsClearedAt
        : '';
      out.meta.syncEpochMs = Math.max(0, Number(state.meta.syncEpochMs) || 0);
      out.meta.lastSyncAt = Date.parse(state.meta.lastSyncAt) ? state.meta.lastSyncAt : '';
    }
    out.version = A.VERSION;
    return out;
  }

  A.state = defaults();
  A.storageAvailable = true;
  A.normalizeState = normalize;

  A.load = function (options) {
    options = options || {};
    var raw = null;
    try {
      raw = window.localStorage.getItem(A.storageKey());
    } catch (err) {
      A.storageAvailable = false;
    }

    if (raw) {
      try {
        A.state = normalize(JSON.parse(raw));
      } catch (err) {
        A.state = defaults();
      }
    } else {
      A.state = defaults();
    }

    if (!A.state.meta.seeded && A.state.sets.length === 0 && options.seedDemos !== false) {
      A.restoreDemos(true);
    }
    return A.state;
  };

  A.save = function () {
    try {
      A.state.meta.updatedAt = new Date().toISOString();
      window.localStorage.setItem(A.storageKey(), JSON.stringify(A.state));
      A.storageAvailable = true;
      return true;
    } catch (err) {
      A.storageAvailable = false;
      if (!A._warnedSave) {
        A._warnedSave = true;
        A.toast({
          title: 'Could not save',
          message: 'Your browser blocked local storage. Use Export to keep a copy of your library.',
          kind: 'error'
        });
      }
      return false;
    }
  };

  A.resetAll = function (keepDemos) {
    A.state = defaults();
    if (keepDemos) A.restoreDemos(true);
    A.save();
  };

  function scheduleSync() {
    if (A.sync && typeof A.sync.schedule === 'function') A.sync.schedule();
  }

  function noteRestoredSet(id) {
    if (A.sync && typeof A.sync.noteRestoredSet === 'function') A.sync.noteRestoredSet(id);
  }

  function noteDeletedSet(id) {
    if (A.sync && typeof A.sync.noteDeletedSet === 'function') A.sync.noteDeletedSet(id);
  }

  A.restoreDemos = function (markSeeded) {
    var now = Date.now();
    var restored = [];
    (Seed.demoSets || []).forEach(function (demo, index) {
      var exists = A.state.sets.some(function (s) { return s.id === demo.id; });
      if (exists) return;
      var copy = A.clone(demo);
      copy.createdAt = new Date(now - (index + 2) * 86400000).toISOString();
      copy.updatedAt = new Date(now).toISOString();
      copy.questions = copy.questions.map(function (q) {
        if (!q.id) q.id = A.uid('q');
        return q;
      });
      A.state.sets.push(copy);
      restored.push(demo.id);
    });
    if (markSeeded) A.state.meta.seeded = true;
    A.save();
    restored.forEach(noteRestoredSet);
    scheduleSync();
  };

  A.getSet = function (id) {
    return A.state.sets.filter(function (s) { return s.id === id; })[0] || null;
  };

  A.getAttempt = function (id) {
    return A.state.attempts.filter(function (a) { return a.id === id; })[0] || null;
  };

  A.deleteSet = function (id) {
    A.state.sets = A.state.sets.filter(function (s) { return s.id !== id; });
    if (A.state.activeExam && A.state.activeExam.setId === id) A.state.activeExam = null;
    noteDeletedSet(id);
    A.save();
    scheduleSync();
  };

  A.deleteQuestion = function (setId, qid) {
    var set = A.getSet(setId);
    if (!set) return;
    set.questions = set.questions.filter(function (q) { return q.id !== qid; });
    set.updatedAt = new Date().toISOString();
    A.save();
    scheduleSync();
  };

  A.updateSet = function (setId, patch) {
    var set = A.getSet(setId);
    if (!set) return null;
    Object.keys(patch || {}).forEach(function (key) {
      if (key !== 'id' && key !== 'questions') set[key] = patch[key];
    });
    set.updatedAt = new Date().toISOString();
    A.save();
    scheduleSync();
    return set;
  };

  A.addSet = function (set) {
    A.state.sets.unshift(set);
    A.save();
    scheduleSync();
    return set;
  };

  /* ── Derived stats ───────────────────────────────────── */
  A.isAnswered = function (response) {
    return response !== null && response !== undefined && response !== '';
  };

  A.attemptBreakdown = function (attempt) {
    var total = attempt.questions.length;
    var correct = 0, wrong = 0, skipped = 0;
    attempt.questions.forEach(function (q, i) {
      var r = attempt.responses[i];
      if (!A.isAnswered(r)) skipped++;
      else if (parseInt(r, 10) === q.answer) correct++;
      else wrong++;
    });
    var answered = total - skipped;
    var pct = total ? Math.round(correct / total * 100) : 0;
    return {
      total: total,
      correct: correct,
      wrong: wrong,
      skipped: skipped,
      answered: answered,
      pct: pct,
      accuracy: answered ? Math.round(correct / answered * 100) : 0,
      passed: pct >= A.PASS_MARK
    };
  };

  A.statsAll = function () {
    var attempts = A.state.attempts;
    var answered = 0, correct = 0, pctSum = 0, best = 0;
    attempts.forEach(function (a) {
      var b = A.attemptBreakdown(a);
      answered += b.answered;
      correct += b.correct;
      pctSum += b.pct;
      if (b.pct > best) best = b.pct;
    });
    return {
      attempts: attempts.length,
      answered: answered,
      correct: correct,
      accuracy: answered ? Math.round(correct / answered * 100) : 0,
      avg: attempts.length ? Math.round(pctSum / attempts.length) : 0,
      best: best
    };
  };

  A.setBreakdown = function (setId) {
    var set = A.getSet(setId);
    var attempts = A.state.attempts.filter(function (a) { return a.setId === setId; });
    var best = 0;
    attempts.forEach(function (a) {
      var b = A.attemptBreakdown(a);
      if (b.pct > best) best = b.pct;
    });
    return {
      attempts: attempts.length,
      best: best,
      last: attempts.length ? attempts[0].finishedAt : null,
      bilingual: set ? set.questions.filter(function (q) { return !!q.ml; }).length : 0
    };
  };

  A.todayAnswered = function () {
    var key = A.todayKey();
    return A.state.attempts.reduce(function (sum, attempt) {
      if (A.localKey(attempt.finishedAt) !== key) return sum;
      return sum + A.attemptBreakdown(attempt).answered;
    }, 0);
  };

  A.goal = function () {
    var total = parseInt(A.state.profile.dailyGoal, 10) || 20;
    var done = A.todayAnswered();
    var pct = Math.min(100, Math.round(done / total * 100));
    var note = done === 0 ? 'No questions yet today' :
      done >= total ? 'Goal met — nicely done' :
      (total - done) + ' more to reach today’s goal';
    return { done: done, total: total, pct: pct, note: note };
  };

  A.startOfWeek = function () {
    var d = new Date();
    d.setHours(0, 0, 0, 0);
    var day = d.getDay();
    var diff = day === 0 ? 6 : day - 1;
    d.setDate(d.getDate() - diff);
    return d;
  };

  A.weekStats = function () {
    var start = A.startOfWeek();
    var attempts = A.state.attempts.filter(function (a) { return new Date(a.finishedAt) >= start; });
    var answered = 0, pctSum = 0, best = 0;
    attempts.forEach(function (a) {
      var b = A.attemptBreakdown(a);
      answered += b.answered;
      pctSum += b.pct;
      if (b.pct > best) best = b.pct;
    });
    return {
      tests: attempts.length,
      answered: answered,
      avg: attempts.length ? Math.round(pctSum / attempts.length) : 0,
      best: best
    };
  };

  A.streak = function () {
    var days = {};
    A.state.attempts.forEach(function (a) {
      var key = A.localKey(a.finishedAt);
      if (key) days[key] = true;
    });
    var d = new Date();
    d.setHours(0, 0, 0, 0);
    if (!days[A.localKey(d)]) d.setDate(d.getDate() - 1);
    var count = 0;
    while (days[A.localKey(d)]) {
      count++;
      d.setDate(d.getDate() - 1);
    }
    return count;
  };

  A.lastSevenDays = function () {
    var days = {};
    A.state.attempts.forEach(function (a) {
      var key = A.localKey(a.finishedAt);
      if (key) days[key] = true;
    });
    var out = [];
    for (var i = 6; i >= 0; i--) {
      var d = new Date();
      d.setHours(0, 0, 0, 0);
      d.setDate(d.getDate() - i);
      out.push({
        label: d.toLocaleDateString('en-GB', { weekday: 'narrow' }),
        key: A.localKey(d),
        done: !!days[A.localKey(d)],
        today: i === 0
      });
    }
    return out;
  };

  A.subjectStats = function () {
    var map = {};
    A.state.attempts.forEach(function (a) {
      var subject = a.subject || 'General Knowledge';
      if (!map[subject]) map[subject] = { subject: subject, answered: 0, correct: 0, tests: 0 };
      var b = A.attemptBreakdown(a);
      map[subject].answered += b.answered;
      map[subject].correct += b.correct;
      map[subject].tests++;
    });
    return Object.keys(map).map(function (key) {
      var row = map[key];
      row.accuracy = row.answered ? Math.round(row.correct / row.answered * 100) : 0;
      return row;
    }).sort(function (a, b) { return b.answered - a.answered; });
  };

  A.libraryStats = function () {
    var questions = 0, bilingual = 0;
    A.state.sets.forEach(function (set) {
      questions += set.questions.length;
      bilingual += set.questions.filter(function (q) { return !!q.ml; }).length;
    });
    return { sets: A.state.sets.length, questions: questions, bilingual: bilingual };
  };

  A.storageBytes = function () {
    try {
      return new Blob([JSON.stringify(A.state)]).size;
    } catch (err) {
      return JSON.stringify(A.state).length;
    }
  };

  A.fmtBytes = function (bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(2) + ' MB';
  };

  A.bestSet = function () {
    var sets = A.state.sets.slice();
    sets.sort(function (a, b) {
      if (b.questions.length !== a.questions.length) return b.questions.length - a.questions.length;
      return new Date(b.updatedAt) - new Date(a.updatedAt);
    });
    return sets[0] || null;
  };

  A.findSetForAttempt = function (attempt) {
    return attempt ? A.getSet(attempt.setId) : null;
  };

  /* ── Bilingual access ────────────────────────────────── */
  A.questionFor = function (question, lang) {
    var useMl = lang === 'ml' && question.ml && question.ml.stem &&
      Array.isArray(question.ml.options) && question.ml.options.length === question.en.options.length;
    var source = useMl ? question.ml : question.en;
    return {
      stem: source.stem,
      options: source.options,
      explanation: source.explanation || '',
      answer: question.answer,
      lang: useMl ? 'ml' : 'en',
      fallback: lang === 'ml' && !useMl
    };
  };

  A.correctOption = function (question, lang) {
    var q = A.questionFor(question, lang);
    return A.optionLetter(q.answer);
  };

  /* ── Exam helpers ────────────────────────────────────── */
  A.examRemaining = function (exam) {
    if (!exam || !exam.timeLimitSec) return null;
    var end = new Date(exam.startedAt).getTime() + exam.timeLimitSec * 1000;
    return Math.max(0, Math.round((end - Date.now()) / 1000));
  };

  A.examElapsed = function (exam) {
    if (!exam) return 0;
    return Math.max(0, Math.round((Date.now() - new Date(exam.startedAt).getTime()) / 1000));
  };

  A.buildAttempt = function (exam) {
    var finishedAt = new Date().toISOString();
    var elapsed = Math.max(1, Math.round((Date.now() - new Date(exam.startedAt).getTime()) / 1000));
    return {
      id: A.uid('att'),
      setId: exam.setId,
      setTitle: exam.setTitle,
      titleMl: exam.titleMl || '',
      subject: exam.subject,
      mode: exam.mode,
      lang: exam.lang,
      startedAt: exam.startedAt,
      finishedAt: finishedAt,
      durationSec: exam.timeLimitSec ? Math.min(elapsed, exam.timeLimitSec + 1) : elapsed,
      timeLimitSec: exam.timeLimitSec || null,
      questions: A.clone(exam.questions),
      responses: exam.responses.slice(),
      flags: exam.flags.slice()
    };
  };

  A.addAttempt = function (attempt) {
    A.state.attempts.unshift(attempt);
    A.save();
    scheduleSync();
  };

  /* ── Toast, modal, confirm ───────────────────────────── */
  A.toast = function (opts) {
    var root = document.getElementById('toastRoot');
    if (!root) return;
    var kind = opts && opts.kind ? opts.kind : 'success';
    var title = opts && opts.title ? opts.title : '';
    var message = opts && opts.message ? opts.message : (typeof opts === 'string' ? opts : '');
    if (root.children.length >= 3) root.removeChild(root.firstElementChild);
    var el = document.createElement('div');
    el.className = 'toast toast--' + kind;
    el.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    el.innerHTML =
      A.icon(kind === 'error' ? 'alert' : kind === 'info' ? 'spark' : 'check-circle') +
      '<div>' + (title ? '<strong>' + A.esc(title) + '</strong>' : '') +
      (message ? '<p>' + A.esc(message) + '</p>' : '') + '</div>';
    root.appendChild(el);
    window.setTimeout(function () {
      if (el.parentNode) el.parentNode.removeChild(el);
    }, 4600);
  };

  A.live = function (message) {
    var el = document.getElementById('live');
    if (el) el.textContent = message;
  };

  var modalStack = [];

  A.modal = function (opts) {
    opts = opts || {};
    var root = document.getElementById('modalRoot');
    if (!root) return function () {};
    var backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';
    backdrop.innerHTML =
      '<div class="modal' + (opts.wide ? ' modal--wide' : '') + '" role="dialog" aria-modal="true" aria-labelledby="modalTitle">' +
        '<div class="modal-head">' +
          '<div><h3 id="modalTitle">' + A.esc(opts.title || '') + '</h3>' +
          (opts.subtitle ? '<p>' + A.esc(opts.subtitle) + '</p>' : '') + '</div>' +
          '<button type="button" class="icon-btn icon-btn--bare modal-close" aria-label="Close dialog">' +
            A.icon('close') +
          '</button>' +
        '</div>' +
        '<div class="modal-body">' + (opts.body || '') + '</div>' +
        (opts.footer ? '<div class="modal-foot">' + opts.footer + '</div>' : '') +
      '</div>';
    root.appendChild(backdrop);
    document.body.classList.add('modal-open');

    var previousFocus = document.activeElement;
    var closed = false;

    function close() {
      if (closed) return;
      closed = true;
      document.removeEventListener('keydown', onKey);
      backdrop.remove();
      modalStack = modalStack.filter(function (entry) { return entry.backdrop !== backdrop; });
      if (modalStack.length === 0) document.body.classList.remove('modal-open');
      if (opts.onClose) opts.onClose();
      if (previousFocus && previousFocus.isConnected && previousFocus.focus) {
        try { previousFocus.focus({ preventScroll: true }); } catch (err) { previousFocus.focus(); }
      }
    }

    function onKey(event) {
      var top = modalStack[modalStack.length - 1];
      if (!top || top.backdrop !== backdrop) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        close();
        return;
      }
      if (event.key === 'Tab') {
        var focusables = backdrop.querySelectorAll('a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])');
        if (!focusables.length) return;
        var first = focusables[0];
        var last = focusables[focusables.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    }

    modalStack.push({ backdrop: backdrop, close: close });
    backdrop.querySelector('.modal-close').addEventListener('click', close);
    backdrop.querySelectorAll('[data-modal-close]').forEach(function (btn) {
      btn.addEventListener('click', close);
    });
    backdrop.addEventListener('click', function (event) {
      if (event.target === backdrop) close();
    });
    document.addEventListener('keydown', onKey);

    if (opts.onMount) opts.onMount(backdrop, close);

    window.setTimeout(function () {
      var target = backdrop.querySelector('[data-autofocus]') ||
        backdrop.querySelector('input:not([type="hidden"]), textarea, select, button.btn-primary');
      if (target && target.focus) target.focus();
    }, 30);

    return close;
  };

  A.confirm = function (opts) {
    opts = opts || {};
    var resolved = false;
    return new Promise(function (resolve) {
      function finish(value) {
        if (resolved) return;
        resolved = true;
        resolve(value);
      }
      A.modal({
        title: opts.title || 'Are you sure?',
        body: '<p style="color:var(--muted);font-size:13px;line-height:1.65">' + A.esc(opts.message || '') + '</p>',
        footer:
          '<button type="button" class="btn btn-ghost" data-confirm="0">' + A.esc(opts.cancelText || 'Cancel') + '</button>' +
          '<button type="button" class="btn ' + (opts.danger ? 'btn-danger-solid' : 'btn-primary') + '" data-confirm="1" data-autofocus>' +
            A.esc(opts.confirmText || 'Confirm') + '</button>',
        onMount: function (root, close) {
          root.querySelectorAll('[data-confirm]').forEach(function (btn) {
            btn.addEventListener('click', function () {
              finish(btn.getAttribute('data-confirm') === '1');
              close();
            });
          });
        },
        onClose: function () { finish(false); }
      });
    });
  };

  /* ── Downloads / files ───────────────────────────────── */
  A.download = function (filename, text, mime) {
    var blob = new Blob([text], { type: mime || 'application/json' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.setTimeout(function () { URL.revokeObjectURL(url); }, 1500);
  };

  A.readFileText = function (file) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () { resolve(String(reader.result || '')); };
      reader.onerror = function () { reject(reader.error || new Error('Could not read file')); };
      reader.readAsText(file);
    });
  };

  A.later = function (fn) {
    if (A.onRefresh) A.onRefresh();
    else if (fn) fn();
  };

  /* ── Navigation ──────────────────────────────────────── */
  A.go = function (path) {
    if (!path) return;
    var hash = path.charAt(0) === '#' ? path : '#' + (path.charAt(0) === '/' ? path : '/' + path);
    if (window.location.hash === hash) {
      if (A.onRefresh) A.onRefresh();
    } else {
      window.location.hash = hash;
    }
  };

  A.refresh = function () {
    if (A.onRefresh) A.onRefresh();
  };

  A.closeModal = function () {
    var top = modalStack[modalStack.length - 1];
    if (top) top.close();
  };

  /* ── Init ────────────────────────────────────────────── */
  /* Boot with an empty shell. Auth decides the real scope and
     whether the demo library should be seeded (local mode only),
     so a logged-out visitor never gets demo sets copied into an
     account workspace by the legacy migration. */
  A.load({ seedDemos: false });
})();
