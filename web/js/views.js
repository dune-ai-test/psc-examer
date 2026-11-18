/* =========================================================
   Padanam web — views
   ========================================================= */
(function () {
  'use strict';

  var A = window.Padanam;
  var Seed = window.PadanamSeed;
  var e = A.esc;
  var icon = A.icon;

  var ui = A.ui = A.ui || {};
  ui.library = ui.library || { q: '', subject: 'all', source: 'all', sort: 'recent' };
  ui.setLang = ui.setLang || null;
  ui.reviewFilter = ui.reviewFilter || 'all';
  ui.showKey = false;
  ui.showOpenaiKey = false;
  ui.showSyncToken = false;

  var scan = {
    images: [],
    busy: false,
    adding: 0,
    result: null,
    error: '',
    stage: '',
    progress: 0,
    title: '',
    subject: '',
    language: 'both',
    targetSetId: ''
  };

  var views = A.views = {};

  /* ── Tiny helpers ────────────────────────────────────── */
  function clip(value, length) {
    var text = String(value || '');
    return text.length > length ? text.slice(0, length - 1) + '…' : text;
  }

  function langAttr(lang) {
    return lang === 'ml' ? ' lang="ml"' : '';
  }

  function mlClass(lang) {
    return lang === 'ml' ? ' ml' : '';
  }

  function firstName() {
    var name = String(A.state.profile.name || '').trim();
    return name ? name.split(/\s+/)[0] : '';
  }

  function isAdmin() {
    return !!(A.auth && A.auth.isAdmin());
  }

  function displayName() {
    return String(A.state.profile.name || '').trim() || 'Guest learner';
  }

  function pageHead(eyebrow, title, sub, actions, back) {
    return '<div class="page-head">' +
      '<div class="page-head-main">' +
        (back ? '<a class="back-link" href="' + e(back.href) + '">' + icon('chevron-left') + ' ' + e(back.label) + '</a>' : '') +
        '<p class="eyebrow"><span class="eyebrow-dot"></span>' + e(eyebrow) + '</p>' +
        '<h1 class="page-title">' + title + '</h1>' +
        (sub ? '<p class="page-sub">' + sub + '</p>' : '') +
      '</div>' +
      (actions ? '<div class="head-actions">' + actions + '</div>' : '') +
    '</div>';
  }

  function emptyState(iconName, title, text, actions) {
    return '<div class="empty">' +
      '<span class="empty-icon">' + icon(iconName || 'book') + '</span>' +
      '<h3>' + e(title) + '</h3>' +
      '<p>' + text + '</p>' +
      (actions ? '<div class="empty-actions">' + actions + '</div>' : '') +
    '</div>';
  }

  function scoreClass(pct) {
    if (pct >= 60) return '';
    if (pct >= 40) return ' is-low';
    return ' is-fail';
  }

  function scoreChip(pct) {
    return '<span class="attempt-score' + scoreClass(pct) + '">' + pct + '%</span>';
  }

  function modeLabel(mode) {
    return mode === 'practice' ? 'Practice' : 'Mock';
  }

  function subjectOptions(selected) {
    var names = (Seed.subjects || []).map(function (s) { return s.name; });
    if (selected && names.indexOf(selected) < 0) names.unshift(selected);
    if (!selected) selected = 'General Knowledge';
    return names.map(function (name) {
      return '<option value="' + e(name) + '"' + (name === selected ? ' selected' : '') + '>' + e(name) + '</option>';
    }).join('');
  }

  function sourceCounts() {
    var counts = { all: A.state.sets.length, demo: 0, scan: 0, manual: 0 };
    A.state.sets.forEach(function (set) { if (counts[set.source] !== undefined) counts[set.source]++; });
    return counts;
  }

  function countdownText() {
    var days = A.daysUntil(A.state.profile.examDate);
    if (days === null) return '';
    if (days < 0) return '';
    if (days === 0) return 'Your exam is today. Trust your preparation.';
    return days + ' ' + (days === 1 ? 'day' : 'days') + ' to your exam';
  }

  function setRowHTML(set) {
    var b = A.setBreakdown(set.id);
    var trailing = b.best
      ? '<span class="chip chip--success">Best ' + b.best + '%</span>'
      : '<span class="chip">' + set.questions.length + ' Qs</span>';
    return '<a class="set-row" href="#/set/' + e(set.id) + '">' +
      '<span class="set-row-icon">' + icon('book') + '</span>' +
      '<span class="set-row-copy">' +
        '<strong>' + e(set.title) + '</strong>' +
        '<small>' + e(set.subject) + ' · ' + A.plural(set.questions.length, 'question') + '</small>' +
      '</span>' +
      '<span class="set-row-score">' + trailing + '</span>' +
      icon('chevron-right') +
    '</a>';
  }

  function attemptRowHTML(attempt) {
    var b = A.attemptBreakdown(attempt);
    return '<a class="attempt-row" href="#/result/' + e(attempt.id) + '">' +
      '<span class="attempt-row-copy">' +
        '<strong>' + e(attempt.setTitle) + '</strong>' +
        '<small>' + modeLabel(attempt.mode) + ' · ' + A.fmtRelative(attempt.finishedAt) + ' · ' + b.answered + '/' + b.total + ' answered</small>' +
      '</span>' +
      scoreChip(b.pct) +
    '</a>';
  }

  function quickAction(href, iconName, title, sub) {
    return '<a class="quick-action" href="' + e(href) + '">' +
      '<span class="quick-icon">' + icon(iconName) + '</span>' +
      '<span><strong>' + e(title) + '</strong><small>' + e(sub) + '</small></span>' +
    '</a>';
  }

  function setUpMarkup(n) {
    return '<div class="ring" style="--pct:100"><div class="ring-inner"><strong>' + n + '</strong><small>Questions</small></div></div>';
  }

  function toastFlash() {
    var flash = A._flash;
    if (!flash) return;
    A._flash = null;
    A.toast(flash);
  }

  /* ── Home ────────────────────────────────────────────── */
  views.home = function () {
    var profile = A.state.profile;
    var admin = isAdmin();
    var goal = A.goal();
    var stats = A.statsAll();
    var week = A.weekStats();
    var streak = A.streak();
    var active = A.state.activeExam;
    var bestSet = A.bestSet();
    var recentSets = A.state.sets.slice().sort(function (a, b) {
      return new Date(b.updatedAt) - new Date(a.updatedAt);
    }).slice(0, 4);
    var recentAttempts = A.state.attempts.slice(0, 4);
    var first = firstName();
    var greeting = A.greeting() + (first ? ', ' + e(first) : '') + '.';
    var dateLabel = new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' });
    var countdown = countdownText();
    var setupHref = bestSet ? '#/exam/setup/' + bestSet.id : '#/library';
    var practiceHref = bestSet ? '#/exam/setup/' + bestSet.id + '?mode=practice' : '#/library';
    var reviewHref = A.state.attempts.length ? '#/result/' + A.state.attempts[0].id : '#/progress';

    var goalActions = active
      ? '<a class="btn btn-white" href="#/exam/run">' + icon('play') + ' Resume test</a>' +
        (admin
          ? '<a class="btn btn-dark-ghost" href="#/scan">' + icon('scan') + ' Scan a paper</a>'
          : '<a class="btn btn-dark-ghost" href="#/library">' + icon('book') + ' Browse library</a>')
      : '<a class="btn btn-white" href="' + setupHref + '">' + icon('play') + ' Start a test</a>' +
        (admin
          ? '<a class="btn btn-dark-ghost" href="#/scan">' + icon('scan') + ' Scan a paper</a>'
          : '<a class="btn btn-dark-ghost" href="#/library">' + icon('book') + ' Browse library</a>');

    var sideOne = active
      ? (function () {
          var total = active.questions.length;
          var answered = active.responses.filter(A.isAnswered).length;
          var timeText = active.timeLimitSec
            ? (A.examRemaining(active) > 0 ? A.fmtDuration(A.examRemaining(active)) : 'Time is up')
            : A.fmtDuration(A.examElapsed(active));
          return '<div class="card side-card resume-card">' +
            '<div class="side-card-top">' +
              '<span class="side-card-title">' + icon('clock') + ' Exam in progress</span>' +
              '<span class="chip chip--indigo">' + modeLabel(active.mode) + '</span>' +
            '</div>' +
            '<p class="resume-timer">' + timeText + '</p>' +
            '<p>' + e(active.setTitle) + ' · Question ' + (active.current + 1) + ' of ' + total + ' · ' + answered + ' answered</p>' +
            '<div class="resume-actions">' +
              '<a class="btn btn-indigo btn-sm" href="#/exam/run">' + icon('play') + ' Resume</a>' +
              '<button type="button" class="btn btn-ghost btn-sm" data-action="discard-exam">Discard</button>' +
            '</div>' +
          '</div>';
        })()
      : '<div class="card side-card">' +
          '<div class="side-card-top">' +
            '<span class="side-card-title">' + icon('chart') + ' This week</span>' +
            '<span class="chip">' + week.tests + ' ' + (week.tests === 1 ? 'test' : 'tests') + '</span>' +
          '</div>' +
          '<div class="week-stats">' +
            '<div><strong>' + week.tests + '</strong><span>Tests</span></div>' +
            '<div><strong>' + week.answered + '</strong><span>Answered</span></div>' +
            '<div><strong>' + (week.tests ? week.avg + '%' : '—') + '</strong><span>Average</span></div>' +
          '</div>' +
          '<p>' + (week.tests
            ? 'Your best score this week is ' + week.best + '%. Keep the streak going.'
            : 'No tests this week yet. A short 10-question mock is a good start.') + '</p>' +
        '</div>';

    var tips = [
      'Read every option before answering. PSC distractors are often almost right.',
      'Keep a small error log. Reviewing wrong answers is where most marks are won.',
      'Take one timed mock a week, then spend twice as long reviewing it.',
      'Scan a paper after each study session while the topic is still fresh.',
      'Aim for accuracy first. Speed follows once the basics are automatic.',
      'Study in short, focused blocks. Twenty minutes beats an unfocused hour.'
    ];
    var tip = tips[new Date().getDate() % tips.length];
    var examDays = A.daysUntil(profile.examDate);
    var sideTwo = countdown
      ? '<div class="card side-card">' +
          '<div class="side-card-top">' +
            '<span class="side-card-title">' + icon('target') + ' Exam countdown</span>' +
            '<span class="chip chip--indigo">' + (examDays === 0 ? 'Today' : examDays + ' days') + '</span>' +
          '</div>' +
          '<p style="margin-top:10px;font-family:var(--font-display);font-size:20px;line-height:1.2;color:var(--navy);letter-spacing:-.02em">' + e(profile.examDate ? A.fmtDate(profile.examDate) : '') + '</p>' +
          '<p>' + e(countdown) + '. Keep your daily goal small and repeatable.</p>' +
        '</div>'
      : '<div class="card side-card">' +
          '<div class="side-card-top">' +
            '<span class="side-card-title">' + icon('spark') + ' Study tip</span>' +
            '<span class="chip">Today</span>' +
          '</div>' +
          '<p style="margin-top:9px;font-size:12px;color:var(--ink-soft)">' + e(tip) + '</p>' +
        '</div>';

    var libraryBlock;
    if (recentSets.length) {
      libraryBlock = '<div class="section-head"><h2 class="section-title">Your library</h2><a class="link" href="#/library">All sets →</a></div>' +
        '<div class="set-list">' + recentSets.map(setRowHTML).join('') + '</div>';
    } else {
      libraryBlock = admin
        ? '<div class="card welcome-card">' +
          '<p class="eyebrow"><span class="eyebrow-dot"></span>Start here</p>' +
          '<h2>Build your first question set.</h2>' +
          '<p>Scan a PSC question paper and let Gemini or your own OpenAI-compatible endpoint turn it into a bilingual practice set. You can also type questions in by hand, or restore the demo sets to look around.</p>' +
          '<div class="welcome-actions">' +
            '<a class="btn btn-primary" href="#/scan">' + icon('scan') + ' Scan a paper</a>' +
            '<button type="button" class="btn btn-ghost" data-action="open-set-form">' + icon('plus') + ' Create manually</button>' +
            '<button type="button" class="btn btn-subtle" data-action="restore-demos">' + icon('refresh') + ' Restore demos</button>' +
          '</div>' +
          '<div class="welcome-points">' +
            '<div><strong>1 · Add a paper</strong><span>Photos or typed questions</span></div>' +
            '<div><strong>2 · Assign it</strong><span>Admin console → accounts</span></div>' +
            '<div><strong>3 · Practise</strong><span>Timed or untimed</span></div>' +
          '</div>' +
        '</div>'
        : '<div class="card welcome-card">' +
          '<p class="eyebrow"><span class="eyebrow-dot"></span>Library</p>' +
          '<h2>Your sets will appear here.</h2>' +
          '<p>The administrator assigns question sets to your account. Once a set is assigned, open it from the library to take a timed mock or practise without a timer.</p>' +
          '<div class="welcome-actions">' +
            '<a class="btn btn-primary" href="#/library">' + icon('book') + ' Open library</a>' +
            '<a class="btn btn-ghost" href="#/progress">' + icon('chart') + ' See progress</a>' +
          '</div>' +
          '<div class="welcome-points">' +
            '<div><strong>1 · Wait for a set</strong><span>Assigned by the admin</span></div>' +
            '<div><strong>2 · Practise</strong><span>Timed or untimed</span></div>' +
            '<div><strong>3 · Review</strong><span>Answers and explanations</span></div>' +
          '</div>' +
        '</div>';
    }

    var attemptsBlock = recentAttempts.length
      ? '<div class="attempt-list">' + recentAttempts.map(attemptRowHTML).join('') + '</div>'
      : '<p class="muted" style="font-size:11.5px;line-height:1.6">Your first completed test will appear here. Attempts are saved in this browser.</p>';

    return pageHead(dateLabel + ' · ' + (profile.target || 'Kerala PSC'), greeting,
      'A quiet desk for ' + e(profile.target || 'Kerala PSC') + ' prep — practise, review and keep the streak alive.',
      (admin ? '<a class="btn btn-ghost" href="#/scan">' + icon('scan') + ' Scan</a>' : '') +
      '<a class="btn btn-primary" href="#/library">' + icon('book') + ' Library</a>') +

      '<div class="home-hero">' +
        '<section class="dark-card goal-card">' +
          '<div class="goal-top">' +
            '<span class="goal-kicker">' + icon('flame') + ' Daily goal</span>' +
            (streak ? '<span class="chip chip--dark">' + icon('flame') + ' ' + streak + ' day streak</span>' : '<span class="chip chip--dark">' + stats.attempts + ' tests done</span>') +
          '</div>' +
          '<p class="goal-value">' + goal.done + ' <span>/ ' + goal.total + '</span></p>' +
          '<p class="goal-caption">questions answered today</p>' +
          '<div class="goal-bar-row">' +
            '<div class="progress progress--dark"><span style="width:' + goal.pct + '%"></span></div>' +
            '<span class="goal-percent">' + goal.pct + '%</span>' +
          '</div>' +
          '<div class="goal-actions">' + goalActions + '</div>' +
          '<p class="goal-note">' + e(goal.note) + (countdown ? ' · ' + e(countdown) : '') + '</p>' +
        '</section>' +
        '<div class="side-stack">' + sideOne + sideTwo + '</div>' +
      '</div>' +

      '<div class="quick-grid">' +
        (admin
          ? quickAction('#/scan', 'scan', 'Scan a paper', 'Photos → questions')
          : quickAction('#/library', 'book', 'Assigned library', 'Sets from your admin')) +
        quickAction(setupHref, 'clock', 'Mock test', 'Timed, exam-style') +
        quickAction(practiceHref, 'book', 'Practice', 'No timer, no pressure') +
        quickAction(reviewHref, 'list', 'Last result', A.state.attempts.length ? 'Review your answers' : 'No attempts yet') +
      '</div>' +

      '<div class="home-bottom">' +
        '<section class="home-col">' + libraryBlock + '</section>' +
        '<section class="home-col">' +
          '<div class="section-head"><h2 class="section-title">Recent attempts</h2><a class="link" href="#/progress">Progress →</a></div>' +
          '<div class="card card-pad-sm">' + attemptsBlock + '</div>' +
        '</section>' +
      '</div>';
  };

  /* ── Library ─────────────────────────────────────────── */
  views.library = function () {
    var L = ui.library;
    var admin = isAdmin();
    var sets = A.state.sets;
    var subjects = [];
    sets.forEach(function (set) {
      if (subjects.indexOf(set.subject) < 0) subjects.push(set.subject);
    });
    if (L.subject !== 'all' && subjects.indexOf(L.subject) < 0) L.subject = 'all';
    var counts = sourceCounts();

    var subjectSelect = '<select class="select" data-library-subject aria-label="Filter by subject">' +
      '<option value="all">All subjects</option>' +
      subjects.sort().map(function (name) {
        return '<option value="' + e(name) + '"' + (L.subject === name ? ' selected' : '') + '>' + e(name) + '</option>';
      }).join('') +
    '</select>';

    var sortSelect = '<select class="select" data-library-sort aria-label="Sort sets">' +
      '<option value="recent"' + (L.sort === 'recent' ? ' selected' : '') + '>Recently updated</option>' +
      '<option value="title"' + (L.sort === 'title' ? ' selected' : '') + '>Title A–Z</option>' +
      '<option value="questions"' + (L.sort === 'questions' ? ' selected' : '') + '>Most questions</option>' +
      '<option value="best"' + (L.sort === 'best' ? ' selected' : '') + '>Best score</option>' +
    '</select>';

    var sources = [
      ['all', 'All sets'],
      ['demo', 'Demo'],
      ['scan', 'Scanned'],
      ['manual', 'Manual']
    ].map(function (pair) {
      return '<button type="button" class="filter-chip' + (L.source === pair[0] ? ' is-active' : '') + '" data-action="library-source" data-value="' + pair[0] + '">' +
        pair[1] + '<span class="count">' + counts[pair[0]] + '</span></button>';
    }).join('');

    return pageHead('Question library', 'Your question bank',
      admin
        ? 'Every paper you scan or type lives here. Search across sets, questions and subjects.'
        : 'The sets assigned to your account live here. Search across sets, questions and subjects.',
      (admin
        ? '<a class="btn btn-ghost" href="#/scan">' + icon('scan') + ' Scan paper</a>' +
          '<button type="button" class="btn btn-primary" data-action="open-set-form">' + icon('plus') + ' New set</button>'
        : '<span class="chip chip--indigo">' + icon('lock') + ' Assigned by admin</span>')) +

      '<div class="library-tools">' +
        '<label class="search-box">' +
          icon('search') +
          '<input class="input" type="search" placeholder="Search sets, subjects or questions…" value="' + e(L.q) + '" data-library-search aria-label="Search library">' +
        '</label>' +
        subjectSelect +
        sortSelect +
      '</div>' +
      '<div class="filter-row" aria-label="Filter by source">' + sources + '</div>' +
      '<div id="libraryResults">' + views.libraryResults() + '</div>';
  };

  views.libraryResults = function () {
    var L = ui.library;
    var query = String(L.q || '').trim().toLowerCase();
    var sets = A.state.sets.filter(function (set) {
      if (L.source !== 'all' && set.source !== L.source) return false;
      if (L.subject !== 'all' && set.subject !== L.subject) return false;
      if (!query) return true;
      if ((set.title + ' ' + set.titleMl + ' ' + set.subject).toLowerCase().indexOf(query) >= 0) return true;
      return set.questions.some(function (q) {
        var hay = (q.en.stem + ' ' + (q.ml ? q.ml.stem : '') + ' ' + q.en.options.join(' ') + ' ' + (q.ml ? q.ml.options.join(' ') : '')).toLowerCase();
        return hay.indexOf(query) >= 0;
      });
    });

    sets.sort(function (a, b) {
      if (L.sort === 'title') return a.title.localeCompare(b.title);
      if (L.sort === 'questions') return b.questions.length - a.questions.length;
      if (L.sort === 'best') return A.setBreakdown(b.id).best - A.setBreakdown(a.id).best;
      return new Date(b.updatedAt) - new Date(a.updatedAt);
    });

    var showNew = isAdmin() && L.source === 'all' && L.subject === 'all' && !query;
    var cards = sets.map(function (set) {
      var b = A.setBreakdown(set.id);
      var bilingual = set.questions.some(function (q) { return !!q.ml; });
      return '<a class="set-card" href="#/set/' + e(set.id) + '">' +
        '<div class="set-card-top">' +
          '<span class="set-subject">' + e(set.subject) + '</span>' +
          A.sourceChip(set.source) +
        '</div>' +
        '<h3>' + e(set.title) + '</h3>' +
        '<div class="set-card-meta">' +
          '<span>' + set.questions.length + ' questions</span>' +
          '<span class="dot-sep">·</span><span>' + (bilingual ? 'Bilingual' : 'English') + '</span>' +
          '<span class="dot-sep">·</span><span>' + A.fmtRelative(set.updatedAt) + '</span>' +
        '</div>' +
        '<div class="set-card-foot">' +
          '<span class="set-card-score">' +
            (b.attempts ? '<span class="chip chip--success">Best ' + b.best + '%</span> ' + A.plural(b.attempts, 'attempt') : '<span class="chip">New</span> Not attempted') +
          '</span>' +
          '<span class="set-card-play">' + icon('play') + '</span>' +
        '</div>' +
      '</a>';
    }).join('');

    if (showNew) {
      cards += '<button type="button" class="new-card" data-action="open-set-form">' +
        '<span class="new-card-icon">' + icon('plus') + '</span>' +
        '<strong>Create a set</strong>' +
        '<small>Start blank and add questions</small>' +
      '</button>';
    }

    var total = A.state.sets.length;
    if (!sets.length) {
      var action = total
        ? '<button type="button" class="btn btn-ghost btn-sm" data-action="clear-library-filters">Clear filters</button>'
        : isAdmin()
          ? '<a class="btn btn-primary" href="#/scan">' + icon('scan') + ' Scan a paper</a><button type="button" class="btn btn-ghost" data-action="restore-demos">Restore demos</button>'
          : '<a class="btn btn-ghost" href="#/home">' + icon('home') + ' Home</a>';
      return '<div class="card">' + emptyState('search', total ? 'No sets match' : (isAdmin() ? 'Your library is empty' : 'No sets assigned yet'),
        total
          ? 'Try a different search, subject or source filter.'
          : isAdmin()
            ? 'Scan a question paper, create a set by hand, or restore the demo sets.'
            : 'Your administrator assigns sets to this account. They will appear here after the next sync.', action) + '</div>';
    }

    return '<div class="set-grid">' + cards + '</div>' +
      '<p class="library-count">Showing ' + sets.length + ' of ' + total + ' sets · ' + A.libraryStats().questions + ' questions in your library</p>';
  };

  /* ── Set detail ──────────────────────────────────────── */
  views.setDetail = function (setId) {
    var set = A.getSet(setId);
    if (!set) return views.notFound('That set no longer exists.');

    var admin = isAdmin();
    var b = A.setBreakdown(setId);
    var hasMl = set.questions.some(function (q) { return !!q.ml; });
    var lang = ui.setLang || A.state.profile.language;
    if (lang === 'ml' && !hasMl) lang = 'en';
    var attempts = A.state.attempts.filter(function (a) { return a.setId === setId; }).slice(0, 4);

    var langToggle = hasMl
      ? '<div class="segmented" aria-label="Question language">' +
          '<button type="button" class="' + (lang === 'en' ? 'is-active' : '') + '" data-action="set-lang" data-value="en">EN</button>' +
          '<button type="button" class="' + (lang === 'ml' ? 'is-active' : '') + '" data-action="set-lang" data-value="ml">ML</button>' +
        '</div>'
      : '';

    var questions = set.questions.length
      ? '<div class="question-stack">' + set.questions.map(function (q, i) {
          var v = A.questionFor(q, lang);
          return '<details class="q-card">' +
            '<summary>' +
              '<span class="q-num">' + A.pad(i + 1) + '</span>' +
              '<span class="q-summary-copy">' +
                '<strong class="' + mlClass(v.lang) + '">' + A.text(clip(v.stem, 165)) + '</strong>' +
                '<span>Answer ' + A.optionLetter(q.answer) + ' · ' + v.options.length + ' options' + (v.fallback ? ' · English only' : '') + '</span>' +
              '</span>' +
            '</summary>' +
            '<div class="q-body">' +
              '<div class="q-options">' + v.options.map(function (option, index) {
                var correct = index === q.answer;
                return '<div class="q-option' + (correct ? ' is-correct' : '') + '">' +
                  '<span class="letter">' + A.optionLetter(index) + '</span>' +
                  '<span class="' + mlClass(v.lang) + '">' + A.text(option) + '</span>' +
                  (correct ? icon('check') : '') +
                '</div>';
              }).join('') + '</div>' +
              (v.explanation ? '<div class="q-explain"><strong>Why:</strong> ' + A.text(v.explanation) + '</div>' : '') +
              (admin
                ? '<div class="q-actions">' +
                    '<button type="button" class="btn btn-subtle btn-sm" data-action="edit-question" data-set="' + e(set.id) + '" data-q="' + e(q.id) + '">' + icon('edit') + ' Edit</button>' +
                  '</div>'
                : '') +
            '</div>' +
          '</details>';
        }).join('') + '</div>'
      : emptyState('list', 'No questions yet',
          admin
            ? 'Add questions by hand, or scan a question paper to fill this set automatically.'
            : 'This set is still being prepared by the administrator.',
          admin
            ? '<button type="button" class="btn btn-primary" data-action="add-question" data-set="' + e(set.id) + '">' + icon('plus') + ' Add question</button><a class="btn btn-ghost" href="#/scan">Scan instead</a>'
            : '');

    var attemptList = attempts.length
      ? '<div class="aside-list">' + attempts.map(function (a) {
          var ab = A.attemptBreakdown(a);
          return '<a href="#/result/' + e(a.id) + '"><span>' + modeLabel(a.mode) + ' · ' + A.fmtDateShort(a.finishedAt) + '</span><span><strong>' + ab.pct + '%</strong></span></a>';
        }).join('') + '</div>'
      : '<p>No attempts yet. Your scores will show up here.</p>';

    var startActions = set.questions.length
      ? '<a class="btn btn-primary btn-block" href="#/exam/setup/' + e(set.id) + '">' + icon('play') + ' Start a mock test</a>' +
        '<a class="btn btn-ghost btn-block" href="#/exam/setup/' + e(set.id) + '?mode=practice">' + icon('book') + ' Practice without a timer</a>'
      : admin
        ? '<button type="button" class="btn btn-primary btn-block" data-action="add-question" data-set="' + e(set.id) + '">' + icon('plus') + ' Add the first question</button>'
        : '<p class="muted" style="font-size:11px">Questions will appear once the administrator finishes this set.</p>';

    return pageHead('Set details', 'Practice this set',
      'Review the question bank, then start a timed mock or an untimed practice run.',
      admin
        ? '<button type="button" class="btn btn-ghost" data-action="open-set-form" data-set="' + e(set.id) + '">' + icon('edit') + ' Edit</button>'
        : '',
      { href: '#/library', label: 'Library' }) +

      '<section class="set-hero">' +
        '<div class="set-hero-top">' +
          '<div>' +
            '<span class="set-subject">' + e(set.subject) + '</span>' +
            '<h1>' + e(set.title) + '</h1>' +
            (set.titleMl ? '<p class="ml" style="margin-top:4px;color:var(--muted);font-size:14px">' + e(set.titleMl) + '</p>' : '') +
            '<div class="set-hero-sub">' +
              A.sourceChip(set.source) +
              '<span class="chip">' + set.questions.length + ' questions</span>' +
              (hasMl ? '<span class="chip chip--indigo">Bilingual</span>' : '<span class="chip">English</span>') +
              '<span>Updated ' + A.fmtRelative(set.updatedAt) + '</span>' +
            '</div>' +
          '</div>' +
          '<div class="set-hero-actions">' +
            (set.questions.length
              ? '<a class="btn btn-primary" href="#/exam/setup/' + e(set.id) + '">' + icon('play') + ' Mock test</a>' +
                '<a class="btn btn-ghost" href="#/exam/setup/' + e(set.id) + '?mode=practice">' + icon('book') + ' Practice</a>'
              : '') +
            (admin ? '<button type="button" class="btn btn-ghost" data-action="add-question" data-set="' + e(set.id) + '">' + icon('plus') + ' Question</button>' : '') +
          '</div>' +
        '</div>' +
        '<div class="set-hero-stats">' +
          '<div><strong>' + set.questions.length + '</strong><span>Questions</span></div>' +
          '<div><strong>' + b.bilingual + '</strong><span>Bilingual</span></div>' +
          '<div><strong>' + (b.attempts ? b.best + '%' : '—') + '</strong><span>Best score</span></div>' +
          '<div><strong>' + b.attempts + '</strong><span>Attempts</span></div>' +
        '</div>' +
      '</section>' +

      '<div class="detail-grid">' +
        '<section class="card card-pad detail-main">' +
          '<div class="section-head" style="flex-wrap:wrap">' +
            '<div><h2 class="section-title">Questions</h2><p class="muted" style="font-size:10.5px;margin-top:2px">Tap a question to reveal options and the answer.</p></div>' +
            '<div style="display:flex;align-items:center;gap:7px;flex-wrap:wrap">' +
              langToggle +
              (admin ? '<button type="button" class="btn btn-soft btn-sm" data-action="add-question" data-set="' + e(set.id) + '">' + icon('plus') + ' Add</button>' : '') +
            '</div>' +
          '</div>' +
          questions +
        '</section>' +
        '<aside class="detail-aside">' +
          '<div class="card aside-card">' +
            '<h3>Ready when you are</h3>' +
            '<p>' + (set.questions.length
              ? 'Choose a timed mock to simulate exam pressure, or take your time in practice mode.'
              : 'This set is waiting for its first question.') + '</p>' +
            startActions +
          '</div>' +
          '<div class="card aside-card">' +
            '<h3>Attempts</h3>' +
            '<p>Results for this set only.</p>' +
            attemptList +
          '</div>' +
          '<div class="card aside-card">' +
            '<h3>About this set</h3>' +
            '<div class="about-list">' +
              '<div><span>Source</span><strong>' + A.sourceLabel(set.source) + '</strong></div>' +
              '<div><span>Created</span><strong>' + A.fmtDate(set.createdAt) + '</strong></div>' +
              '<div><span>Updated</span><strong>' + A.fmtDate(set.updatedAt) + '</strong></div>' +
              '<div><span>Languages</span><strong>' + (hasMl ? 'English + Malayalam' : 'English') + '</strong></div>' +
            '</div>' +
            (admin ? '<button type="button" class="btn btn-ghost btn-sm btn-block" style="margin-top:12px" data-action="open-set-form" data-set="' + e(set.id) + '">' + icon('edit') + ' Edit details</button>' : '') +
          '</div>' +
        '</aside>' +
      '</div>';
  };

  /* ── Set & question editors ──────────────────────────── */
  views.openSetForm = function (setId) {
    var set = setId ? A.getSet(setId) : null;
    var editing = !!set;
    var body =
      '<form id="setForm" data-form="set">' +
        '<input type="hidden" name="setId" value="' + e(editing ? set.id : '') + '">' +
        '<div class="field">' +
          '<label class="field-label" for="setTitle">Set title</label>' +
          '<input class="input" id="setTitle" name="title" required maxlength="90" placeholder="e.g. Kerala Geography — 2024 paper" value="' + e(editing ? set.title : '') + '" data-autofocus>' +
        '</div>' +
        '<div class="form-grid" style="margin-top:14px">' +
          '<div class="field">' +
            '<label class="field-label" for="setSubject">Subject</label>' +
            '<select class="select" id="setSubject" name="subject">' + subjectOptions(editing ? set.subject : 'General Knowledge') + '</select>' +
          '</div>' +
          '<div class="field">' +
            '<label class="field-label" for="setTitleMl">Malayalam title <span class="faint">(optional)</span></label>' +
            '<input class="input" id="setTitleMl" name="titleMl" lang="ml" maxlength="90" placeholder="മലയാളം തലക്കെട്ട്" value="' + e(editing ? set.titleMl : '') + '">' +
          '</div>' +
        '</div>' +
        '<p class="field-hint" style="margin-top:10px">' + (editing
          ? 'Renaming a set does not change questions or past results.'
          : 'Next you can add questions by hand, or scan a paper to fill the set.') + '</p>' +
      '</form>';

    A.modal({
      title: editing ? 'Edit set details' : 'Create a question set',
      subtitle: editing ? 'Change how this set appears in your library.' : 'A blank set you can fill at your own pace.',
      body: body,
      footer:
        '<button type="button" class="btn btn-ghost" data-modal-close>Cancel</button>' +
        '<button type="submit" class="btn btn-primary" form="setForm">' + (editing ? 'Save changes' : 'Create set') + '</button>'
    });
  };

  views.openQuestionForm = function (setId, qid) {
    var set = A.getSet(setId);
    if (!set) return;
    var q = qid ? set.questions.filter(function (item) { return item.id === qid; })[0] : null;
    var count = q ? Math.max(2, Math.min(6, q.en.options.length)) : 4;
    var enOptions = q ? q.en.options.slice() : [];
    var mlOptions = q && q.ml ? q.ml.options.slice() : [];
    var answer = q ? q.answer : 0;

    var enOptionFields = '';
    var mlOptionFields = '';
    for (var i = 0; i < count; i++) {
      enOptionFields +=
        '<div class="option-input' + (i === answer ? ' is-correct' : '') + '">' +
          '<span class="letter-badge">' + A.optionLetter(i) + '</span>' +
          '<input class="input" name="optEn' + i + '" required maxlength="160" placeholder="Option ' + A.optionLetter(i) + '" value="' + e(enOptions[i] || '') + '">' +
        '</div>';
      mlOptionFields +=
        '<div class="option-input">' +
          '<span class="letter-badge">' + A.optionLetter(i) + '</span>' +
          '<input class="input" name="optMl' + i + '" lang="ml" maxlength="160" placeholder="ഓപ്ഷൻ ' + A.optionLetter(i) + '" value="' + e(mlOptions[i] || '') + '">' +
        '</div>';
    }

    var answerOptions = '';
    for (var j = 0; j < count; j++) {
      answerOptions += '<option value="' + j + '"' + (j === answer ? ' selected' : '') + '>Option ' + A.optionLetter(j) + '</option>';
    }

    var body =
      '<form id="questionForm" data-form="question">' +
        '<input type="hidden" name="setId" value="' + e(set.id) + '">' +
        '<input type="hidden" name="qid" value="' + e(q ? q.id : '') + '">' +
        '<input type="hidden" name="optionCount" value="' + count + '">' +
        '<div class="form-section-title">Question · English</div>' +
        '<div class="field">' +
          '<label class="field-label" for="stemEn">Question text</label>' +
          '<textarea class="textarea" id="stemEn" name="stemEn" required rows="3" placeholder="Type the full question…" data-autofocus>' + e(q ? q.en.stem : '') + '</textarea>' +
        '</div>' +
        '<div class="field" style="margin-top:12px">' +
          '<span class="field-label">Options</span>' +
          '<div class="option-input-grid">' + enOptionFields + '</div>' +
        '</div>' +
        '<div class="form-grid" style="margin-top:12px">' +
          '<div class="field">' +
            '<label class="field-label" for="answerSelect">Correct answer</label>' +
            '<select class="select" id="answerSelect" name="answer">' + answerOptions + '</select>' +
            '<span class="field-hint">Applies to both languages, so keep options in the same order.</span>' +
          '</div>' +
          '<div class="field">' +
            '<span class="field-label">Explanation · English</span>' +
            '<textarea class="textarea" name="explainEn" rows="3" placeholder="Optional — why is this answer correct?" style="min-height:74px">' + e(q ? q.en.explanation : '') + '</textarea>' +
          '</div>' +
        '</div>' +
        '<details class="details-block"' + (q && q.ml ? ' open' : '') + '>' +
          '<summary>Malayalam version <span class="faint" style="font-weight:400">optional</span></summary>' +
          '<div class="details-block-body">' +
            '<div class="field">' +
              '<label class="field-label" for="stemMl">Question in Malayalam</label>' +
              '<textarea class="textarea" id="stemMl" name="stemMl" lang="ml" rows="3" placeholder="മലയാളത്തിൽ ചോദ്യം…">' + e(q && q.ml ? q.ml.stem : '') + '</textarea>' +
            '</div>' +
            '<div class="field" style="margin-top:10px">' +
              '<span class="field-label">Malayalam options</span>' +
              '<div class="option-input-grid">' + mlOptionFields + '</div>' +
            '</div>' +
            '<div class="field" style="margin-top:10px">' +
              '<span class="field-label">Explanation · Malayalam</span>' +
              '<textarea class="textarea" name="explainMl" lang="ml" rows="2" style="min-height:64px">' + e(q && q.ml ? q.ml.explanation : '') + '</textarea>' +
            '</div>' +
            '<p class="field-hint" style="margin-top:8px">Fill the Malayalam stem and all its options, or leave the whole section blank. Options must match the English order.</p>' +
          '</div>' +
        '</details>' +
      '</form>';

    A.modal({
      title: q ? 'Edit question' : 'Add a question',
      subtitle: e(set.title) + ' · ' + set.questions.length + ' in this set',
      body: body,
      wide: true,
      footer:
        '<button type="button" class="btn btn-ghost" data-modal-close>Cancel</button>' +
        '<button type="submit" class="btn btn-primary" form="questionForm">' + (q ? 'Save question' : 'Add question') + '</button>'
    });
  };

  /* ── Scan ────────────────────────────────────────────── */
  var SCAN_STAGES = [
    { key: 'reading', label: 'Reading the pages' },
    { key: 'structuring', label: 'Structuring questions' },
    { key: 'validating', label: 'Checking options and answers' },
    { key: 'done', label: 'Ready to review' }
  ];

  function scanTargetSet() {
    if (!scan.targetSetId) return null;
    var set = A.getSet(scan.targetSetId);
    if (!set) scan.targetSetId = '';
    return set || null;
  }

  function scanTargetField(selectId) {
    if (!A.state.sets.length) return '';
    var target = scanTargetSet();
    var options = A.state.sets.slice()
      .sort(function (a, b) { return String(a.title).localeCompare(String(b.title)); })
      .map(function (set) {
        return '<option value="' + e(set.id) + '"' + (target && target.id === set.id ? ' selected' : '') + '>' +
          e(set.title) + ' · ' + e(set.subject) + ' · ' + A.plural(set.questions.length, 'question') +
        '</option>';
      }).join('');

    return '<div class="field">' +
      '<label class="field-label" for="' + e(selectId) + '">Save into</label>' +
      '<select class="select" id="' + e(selectId) + '" data-scan-field="targetSetId"' + (scan.busy ? ' disabled' : '') + '>' +
        '<option value="">Create a new set</option>' +
        '<optgroup label="Add to an existing set">' + options + '</optgroup>' +
      '</select>' +
      '<span class="field-hint">' + (target
        ? 'The extracted questions will be appended to <b>' + e(target.title) + '</b>. Its title and subject stay unchanged.'
        : 'Keep this on “Create a new set”, or choose an existing topic to continue it.') + '</span>' +
    '</div>';
  }

  views.scan = function () {
    var settings = A.state.settings;
    var provider = A.gemini.provider(settings);
    var ready = provider.ready;
    var images = scan.images;
    var busy = scan.busy || scan.adding > 0;
    var result = scan.result;
    var target = scanTargetSet();
    var titleValue = scan.title || (result ? result.title : '');
    var subjectValue = scan.subject || (result ? result.subject : 'General Knowledge');
    var language = scan.language || 'both';
    var atLimit = images.length >= A.gemini.MAX_IMAGES;

    var previews = images.length
      ? '<div class="scan-previews">' + images.map(function (img, index) {
          return '<div class="scan-thumb">' +
            '<img src="' + img.dataUrl + '" alt="Scanned page ' + (index + 1) + '">' +
            '<button type="button" class="scan-thumb-remove" data-action="scan-remove" data-index="' + index + '" aria-label="Remove page ' + (index + 1) + '">' + icon('close') + '</button>' +
            '<span class="scan-thumb-tag">PAGE ' + (index + 1) + '</span>' +
          '</div>';
        }).join('') + '</div>'
      : '';

    var drop = atLimit
      ? '<div class="inline-note" style="margin-top:12px">' + icon('check-circle') + ' Maximum of ' + A.gemini.MAX_IMAGES + ' pages reached. Remove one to add another.</div>'
      : '<div class="scan-drop" id="scanDrop">' +
          '<span class="scan-drop-icon">' + icon('image') + '</span>' +
          '<h3>' + (images.length ? 'Add more pages' : 'Add your question paper') + '</h3>' +
          '<p>Drop photos here, choose files, or use your camera. Up to ' + A.gemini.MAX_IMAGES + ' pages — each one is compressed in your browser before scanning.</p>' +
          '<div class="scan-drop-actions">' +
            '<button type="button" class="btn btn-primary btn-sm" data-action="scan-pick"' + (busy ? ' disabled' : '') + '>' + icon('upload') + ' Choose photos</button>' +
            '<button type="button" class="btn btn-ghost btn-sm" data-action="scan-camera"' + (busy ? ' disabled' : '') + '>' + icon('camera') + ' Use camera</button>' +
          '</div>' +
          '<p class="scan-drop-hint">JPG, PNG or WEBP · straight, well-lit pages work best</p>' +
        '</div>';

    var pagesBlocked = provider.id === 'pages' && !ready;
    var runLabel = ready ? 'Extract questions' : (pagesBlocked ? 'Check provider' : 'Try sample extraction');
    var runHint;
    if (ready) {
      if (provider.id === 'openai') {
        runHint = 'Requests go directly from this browser to <b>' + e(provider.host) + '</b> using <b>' + e(provider.model) + '</b>. Photos are sent only when you press extract.';
      } else if (provider.id === 'pages') {
        runHint = 'The key stays in Cloudflare. Photos go to this site’s <span class="mono">/api/scan</span> function, which forwards them to <b>' + e(provider.model) + '</b> only when you press extract.';
      } else {
        runHint = 'Your key is used directly from this browser. Photos are sent to Google only when you press extract.';
      }
    } else if (provider.id === 'pages') {
      runHint = e(provider.error || 'The Cloudflare proxy is not ready. Add the provider secret in Cloudflare Pages, then test the connection in Settings.');
    } else if (provider.id === 'openai') {
      runHint = 'No custom endpoint configured yet. Try the sample, add a base URL and model in Settings, or type questions manually.';
    } else {
      runHint = 'No Gemini key yet. Try the sample to preview the flow, add a key in Settings, or type questions manually.';
    }

    var actions = '<div class="scan-result-actions" style="margin-top:14px">' +
      '<button type="button" class="btn btn-primary"' + (busy ? ' disabled' : '') + ' data-action="scan-run" data-demo="' + (ready || pagesBlocked ? '0' : '1') + '">' +
        icon(ready ? 'spark' : 'play') + ' ' + runLabel +
      '</button>' +
      (ready
        ? '<button type="button" class="btn btn-ghost"' + (busy ? ' disabled' : '') + ' data-action="scan-demo">Load sample</button>'
        : '<button type="button" class="btn btn-ghost" data-action="open-set-form">' + icon('plus') + ' Add manually</button>') +
    '</div>' +
    '<p class="field-hint" style="margin-top:10px">' + runHint + '</p>';

    var checkProxy = provider.id === 'pages' && !A.gemini.proxy().checked;
    var keyState = checkProxy
      ? '<div class="key-state"><span class="dot"></span><span>Cloudflare proxy · key stays on the server</span><a href="#/settings">Manage</a></div>'
      : ready
        ? '<div class="key-state"><span class="dot"></span><span>' + e(provider.short) + ' ready · ' + e(provider.model) + '</span><a href="#/settings">Manage</a></div>'
        : '<div class="key-state is-missing"><span class="dot"></span><span>' + e(provider.short) + ' not configured</span><a href="#/settings">Set up</a></div>';

    return pageHead('Scan & import', 'Turn a paper into a question set',
      'Photograph a previous PSC paper. ' + e(provider.label) + ' extracts the questions; review them, then save as a new set or continue an existing topic.',
      '<a class="btn btn-ghost" href="#/library">' + icon('book') + ' Library</a>') +

      '<div class="scan-grid">' +
        '<div class="scan-main">' +
          '<section class="card card-pad">' +
            '<input type="file" id="scanFiles" accept="image/*" multiple hidden>' +
            '<input type="file" id="scanCamera" accept="image/*" capture="environment" hidden>' +
            drop +
            previews +
            '<div class="scan-form">' +
              (A.state.sets.length ? '<div style="margin-bottom:14px">' + scanTargetField('scanTarget') + '</div>' : '') +
              (target
                ? ''
                : '<div class="field">' +
                    '<label class="field-label" for="scanTitle">Set title <span class="faint">(optional)</span></label>' +
                    '<input class="input" id="scanTitle" data-scan-field="title" maxlength="90" placeholder="e.g. PSC LD Clerk — 2023" value="' + e(titleValue) + '">' +
                  '</div>') +
              '<div class="form-grid" style="margin-top:12px">' +
                (target
                  ? ''
                  : '<div class="field">' +
                      '<label class="field-label" for="scanSubject">Subject</label>' +
                      '<select class="select" id="scanSubject" data-scan-field="subject">' + subjectOptions(subjectValue) + '</select>' +
                    '</div>') +
                '<div class="field">' +
                  '<label class="field-label" for="scanLanguage">Paper language</label>' +
                  '<select class="select" id="scanLanguage" data-scan-field="language">' +
                    '<option value="both"' + (language === 'both' ? ' selected' : '') + '>English + Malayalam</option>' +
                    '<option value="en"' + (language === 'en' ? ' selected' : '') + '>English only</option>' +
                  '</select>' +
                '</div>' +
              '</div>' +
              actions +
            '</div>' +
          '</section>' +
          '<div id="scanRunner">' + views.scanRunner() + '</div>' +
        '</div>' +
        '<aside class="scan-aside">' +
          '<div class="card scan-aside-card">' +
            '<h3>' + icon('spark') + ' ' + e(provider.label) + ' scanning</h3>' +
            keyState +
            '<p>Questions come back as a draft. Nothing is saved until you press <b>Save to library</b> or append them to an existing set.</p>' +
            (provider.id === 'gemini'
              ? '<a class="link" style="display:inline-block;margin-top:10px;font-size:10.5px" href="https://aistudio.google.com/app/apikey" target="_blank" rel="noopener">Get a free API key ↗</a>'
              : '<a class="link" style="display:inline-block;margin-top:10px;font-size:10.5px" href="#/settings">Open provider settings →</a>') +
          '</div>' +
          '<div class="card scan-aside-card">' +
            '<h3>' + icon('list') + ' How it works</h3>' +
            '<ol class="scan-steps">' +
              '<li><span class="step-num">1</span><p><strong>Add pages.</strong> Photograph the questions — answers are optional; the model can solve them.</p></li>' +
              '<li><span class="step-num">2</span><p><strong>Extract.</strong> Bilingual questions, options and explanations are pulled into a draft set.</p></li>' +
              '<li><span class="step-num">3</span><p><strong>Review & save.</strong> Keep a new set, or append the questions to an existing topic so its library continues to grow.</p></li>' +
            '</ol>' +
          '</div>' +
          '<div class="card scan-aside-card">' +
            '<h3>' + icon('lock') + ' Photos stay private</h3>' +
            (provider.id === 'gemini'
              ? '<p>Images are resized on this device and sent directly to Google for extraction. They are never added to your library, and Padanam has no server of its own.</p>'
              : provider.id === 'pages'
                ? '<p>Images are resized on this device and sent to the Cloudflare Pages Function at <span class="mono">/api/scan</span>, which forwards them to the provider configured in your Pages settings. They are never added to your library.</p>'
                : '<p>Images are resized on this device and sent directly to the endpoint you configured for extraction. They are never added to your library, and Padanam has no server of its own.</p>') +
          '</div>' +
        '</aside>' +
      '</div>';
  };

  views.scanRunner = function () {
    if (scan.adding > 0) {
      return '<section class="card scan-status">' +
        '<div class="scan-status-head"><span class="spinner"></span><h3>Optimising photos</h3></div>' +
        '<p>Resizing ' + scan.adding + ' ' + (scan.adding === 1 ? 'image' : 'images') + ' for scanning. This stays on your device.</p>' +
      '</section>';
    }

    if (scan.busy) {
      var current = SCAN_STAGES.findIndex(function (stage) { return stage.key === scan.stage; });
      if (current < 0) current = 0;
      if (scan.progress >= 1) current = SCAN_STAGES.length - 1;
      var stages = SCAN_STAGES.map(function (stage, index) {
        var cls = 'scan-stage';
        var mark = '<span class="scan-stage-dot"></span>';
        if (index < current || scan.progress >= 1) {
          cls += ' scan-stage--done';
          mark = icon('check');
        } else if (index === current) {
          cls += ' scan-stage--active';
          mark = '<span class="spinner"></span>';
        }
        return '<div class="' + cls + '">' + mark + '<span>' + stage.label + '</span></div>';
      }).join('');
      return '<section class="card scan-status">' +
        '<div class="scan-status-head"><span class="spinner"></span><h3>Scanning your paper</h3></div>' +
        '<p>Keep this tab open. A dense paper can take up to a minute.</p>' +
        '<div class="progress"><span style="width:' + Math.round(scan.progress * 100) + '%"></span></div>' +
        '<div class="scan-stage-list">' + stages + '</div>' +
      '</section>';
    }

    if (scan.error) {
      return '<section class="card scan-status">' +
        '<div class="inline-note inline-note--danger">' + icon('alert') + '<div><strong>Scan didn’t finish.</strong><br>' + e(scan.error) + '</div></div>' +
        '<p>Check your scanning settings in <a class="link" href="#/settings">Settings</a>, then try again. Sample extraction always works without a configured provider.</p>' +
      '</section>';
    }

    if (!scan.result) return '';

    var result = scan.result;
    var target = scanTargetSet();
    var bilingual = result.questions.filter(function (q) { return !!q.ml; }).length;
    var preview = result.questions.slice(0, 3).map(function (q, index) {
      var v = A.questionFor(q, A.state.profile.language);
      return '<div class="q-card" style="padding:12px 14px">' +
        '<div style="display:flex;align-items:flex-start;gap:10px">' +
          '<span class="q-num">' + A.pad(index + 1) + '</span>' +
          '<div class="q-summary-copy" style="flex:1">' +
            '<strong class="' + mlClass(v.lang) + '">' + A.text(clip(v.stem, 170)) + '</strong>' +
            '<span>' + v.options.length + ' options · Answer ' + A.optionLetter(q.answer) + (q.ml ? ' · Malayalam included' : ' · English only') + '</span>' +
          '</div>' +
        '</div>' +
      '</div>';
    }).join('');

    return '<section class="card scan-status scan-result">' +
      '<div class="scan-status-head">' +
        '<span class="empty-icon" style="width:36px;height:36px;border-radius:11px;margin:0">' + icon('check') + '</span>' +
        '<div><h3>Draft ready</h3><p>' + (target
          ? 'Nothing is saved yet. These questions will be appended to <b>' + e(target.title) + '</b>.'
          : 'Nothing is saved yet. Check the questions, then add them to your library.') + '</p></div>' +
      '</div>' +
      '<div class="scan-result-count"><strong>' + result.questions.length + '</strong><span>questions extracted' + (bilingual ? ' · ' + bilingual + ' bilingual' : '') + '</span></div>' +
      '<div class="tag-row" style="margin-top:8px"><span class="chip chip--indigo">' + e(target ? target.subject : (result.subject || 'General Knowledge')) + '</span>' +
        (target ? '<span class="chip">→ ' + e(target.title) + '</span>' : '') +
        '<span class="chip chip--success">Draft</span></div>' +
      (A.state.sets.length ? '<div style="margin-top:14px">' + scanTargetField('scanResultTarget') + '</div>' : '') +
      '<div style="display:flex;flex-direction:column;gap:8px;margin-top:14px">' + preview + '</div>' +
      (result.questions.length > 3
        ? '<p class="field-hint" style="margin-top:8px">+ ' + (result.questions.length - 3) + (target ? ' more will be added.' : ' more will be saved.') + '</p>'
        : '') +
      '<div class="scan-result-actions">' +
        '<button type="button" class="btn btn-primary" data-action="scan-save">' + icon('book') + (target ? ' Add to this set' : ' Save to library') + '</button>' +
        '<button type="button" class="btn btn-ghost" data-action="scan-save" data-start="1">' + icon('play') + (target ? ' Add & start test' : ' Save & start test') + '</button>' +
        '<button type="button" class="btn btn-subtle" data-action="scan-discard">' + icon('trash') + ' Discard</button>' +
      '</div>' +
    '</section>';
  };

  views.updateScanRunner = function () {
    var target = document.getElementById('scanRunner');
    if (target) target.innerHTML = views.scanRunner();
  };

  views.scanField = function (name, value) {
    if (name === 'targetSetId') {
      scan.targetSetId = value && A.getSet(value) ? String(value) : '';
      A.refresh();
      return;
    }
    if (name === 'title' || name === 'subject' || name === 'language') scan[name] = value;
  };

  views.scanRemove = function (index) {
    if (scan.busy) return;
    scan.images.splice(index, 1);
    A.refresh();
  };

  views.scanAddFiles = async function (files) {
    var list = Array.prototype.slice.call(files || []);
    if (!list.length) return;
    var room = A.gemini.MAX_IMAGES - scan.images.length;
    if (room <= 0) {
      A.toast({ title: 'Page limit reached', message: 'You can add up to ' + A.gemini.MAX_IMAGES + ' pages at a time.', kind: 'info' });
      return;
    }
    if (list.length > room) {
      list = list.slice(0, room);
      A.toast({ title: 'Using the first ' + room + ' pages', message: 'Remove a page before adding more.', kind: 'info' });
    }

    scan.adding = list.length;
    scan.error = '';
    A.refresh();
    var added = 0;

    for (var i = 0; i < list.length; i++) {
      try {
        if (list[i].size && list[i].size > 30 * 1024 * 1024) throw new Error('That photo is larger than 30 MB.');
        var image = await A.gemini.compressImage(list[i]);
        scan.images.push(image);
        added++;
      } catch (err) {
        A.toast({ title: 'Skipped an image', message: err.message, kind: 'error' });
      }
      scan.adding--;
      views.updateScanRunner();
    }

    scan.adding = 0;
    A.refresh();
    if (added) A.toast({ title: added + ' ' + (added === 1 ? 'page added' : 'pages added'), message: 'Ready to extract when you are.', kind: 'info' });
  };

  views.scanRun = async function (demo) {
    if (scan.busy || scan.adding > 0) return;
    var settings = A.state.settings;
    var target = scanTargetSet();
    var provider = A.gemini.provider(settings);
    if (!demo && provider.id === 'pages' && !A.gemini.proxy().checked) {
      await A.gemini.refreshProxy();
      provider = A.gemini.provider(settings);
    }
    if (!demo && provider.id === 'pages' && !provider.ready) {
      A.toast({
        title: 'Cloudflare proxy not ready',
        message: provider.error || 'Add the provider secret in Cloudflare Pages, then test the connection in Settings.',
        kind: 'error'
      });
      return;
    }
    if (!demo && !provider.ready) demo = true;
    if (!demo && !scan.images.length) {
      A.toast({ title: 'Add a page first', message: 'Choose at least one photo of the question paper.', kind: 'error' });
      return;
    }

    scan.busy = true;
    scan.error = '';
    scan.result = null;
    scan.stage = 'reading';
    scan.progress = 0.05;
    A.refresh();

    function stage(key, progress) {
      scan.stage = key;
      scan.progress = progress;
      views.updateScanRunner();
    }

    function wait(ms) { return new Promise(function (resolve) { window.setTimeout(resolve, ms); }); }

    try {
      var extraction;
      if (demo) {
        await wait(520);
        stage('reading', 0.3);
        await wait(620);
        stage('structuring', 0.62);
        await wait(620);
        stage('validating', 0.84);
        await wait(480);
        var sample = A.clone(Seed.demoScan);
        sample.questions.forEach(function (q) { q.id = A.uid('q'); });
        extraction = sample;
      } else {
        extraction = await A.gemini.extract({
          provider: provider.id,
          apiKey: settings.apiKey,
          model: settings.model,
          baseUrl: settings.openaiUrl,
          openaiKey: settings.openaiKey,
          openaiModel: settings.openaiModel,
          language: scan.language || 'both',
          titleHint: target ? target.title : scan.title,
          subject: target ? target.subject : scan.subject,
          images: scan.images,
          onStage: stage
        });
      }

      if (!extraction.questions || !extraction.questions.length) {
        throw new Error('No questions were found in these pages.');
      }

      scan.result = extraction;
      scan.busy = false;
      scan.stage = 'done';
      scan.progress = 1;
      A.refresh();
      A.toast({
        title: extraction.questions.length + ' questions extracted',
        message: target
          ? 'Review the draft, then add it to ' + target.title + '.'
          : 'Review the draft, then save it to your library.',
        kind: 'success'
      });
    } catch (err) {
      scan.busy = false;
      scan.error = err && err.message ? err.message : 'Something went wrong while scanning.';
      A.refresh();
      A.toast({ title: 'Scan failed', message: scan.error, kind: 'error' });
    }
  };

  views.scanSave = function (start) {
    if (!scan.result || scan.busy) return;
    var target = scanTargetSet();
    var now = new Date().toISOString();
    var questions = scan.result.questions.map(function (q) {
      q.id = A.uid('q');
      return q;
    });
    var setId;
    var count = questions.length;
    var appendedTo = '';

    if (target) {
      target.questions = target.questions.concat(questions);
      target.updatedAt = now;
      A.save();
      if (A.sync && typeof A.sync.schedule === 'function') A.sync.schedule();
      setId = target.id;
      appendedTo = target.title;
    } else {
      var title = String(scan.title || scan.result.title || '').trim() ||
        ('Scanned paper · ' + A.fmtDateShort(now));
      var subject = scan.subject || scan.result.subject || 'General Knowledge';
      var set = {
        id: A.uid('set'),
        title: title,
        titleMl: '',
        subject: subject,
        source: 'scan',
        createdAt: now,
        updatedAt: now,
        questions: questions
      };
      A.addSet(set);
      setId = set.id;
    }

    scan.images = [];
    scan.busy = false;
    scan.adding = 0;
    scan.result = null;
    scan.error = '';
    scan.stage = '';
    scan.progress = 0;
    scan.title = '';
    scan.subject = '';
    scan.language = 'both';
    scan.targetSetId = '';

    A.toast({
      title: appendedTo ? 'Added to ' + appendedTo : 'Saved to your library',
      message: count + ' questions added' + (start ? '. Opening exam setup…' : '.'),
      kind: 'success'
    });
    A.go(start ? '#/exam/setup/' + setId : '#/set/' + setId);
  };

  views.scanClear = function () {
    scan.images = [];
    scan.busy = false;
    scan.adding = 0;
    scan.result = null;
    scan.error = '';
    scan.stage = '';
    scan.progress = 0;
    scan.title = '';
    scan.subject = '';
    scan.language = 'both';
    scan.targetSetId = '';
    A.refresh();
  };

  views.mount = function (route) {
    if (route !== 'scan') return;
    var zone = document.getElementById('scanDrop');
    if (!zone) return;

    ['dragenter', 'dragover'].forEach(function (name) {
      zone.addEventListener(name, function (event) {
        event.preventDefault();
        zone.classList.add('is-over');
      });
    });
    ['dragleave', 'dragend'].forEach(function (name) {
      zone.addEventListener(name, function () {
        zone.classList.remove('is-over');
      });
    });
    zone.addEventListener('drop', function (event) {
      event.preventDefault();
      zone.classList.remove('is-over');
      if (event.dataTransfer && event.dataTransfer.files) views.scanAddFiles(event.dataTransfer.files);
    });
  };

  /* ── Exam setup ──────────────────────────────────────── */
  views.examSetup = function (setId, presetMode) {
    var set = A.getSet(setId);
    if (!set || !set.questions.length) return views.notFound('That set has no questions yet.');

    var n = set.questions.length;
    var hasMl = set.questions.some(function (q) { return !!q.ml; });
    var mode = presetMode === 'practice' ? 'practice' : 'mock';
    var counts = [];
    [5, 10, n].forEach(function (value) {
      if (value > 0 && value <= n && counts.indexOf(value) < 0) counts.push(value);
    });
    if (!counts.length) counts.push(n);
    var defaultCount = n <= 10 ? n : 10;
    if (counts.indexOf(defaultCount) < 0) defaultCount = counts[counts.length - 1];
    var lang = A.state.profile.language === 'ml' && hasMl ? 'ml' : 'en';
    var active = A.state.activeExam;

    var countButtons = counts.map(function (value) {
      var label = value === n ? 'All ' + n : String(value);
      return '<button type="button" class="choice' + (value === defaultCount ? ' is-active' : '') + '" data-action="setup-choice" data-name="count" data-value="' + value + '">' + label + '</button>';
    }).join('');

    var modeButtons =
      '<button type="button" class="mode-option' + (mode === 'mock' ? ' is-active' : '') + '" data-action="setup-choice" data-name="mode" data-value="mock">' +
        '<span class="mode-icon">' + icon('clock') + '</span>' +
        '<span><strong>Mock test</strong><small>Timer on. A PSC-style timed run with a score at the end.</small></span>' +
      '</button>' +
      '<button type="button" class="mode-option' + (mode === 'practice' ? ' is-active' : '') + '" data-action="setup-choice" data-name="mode" data-value="practice">' +
        '<span class="mode-icon">' + icon('book') + '</span>' +
        '<span><strong>Practice</strong><small>No timer. Work through the questions at your own pace.</small></span>' +
      '</button>';

    var paceButtons = [30, 45, 60].map(function (seconds) {
      return '<button type="button" class="choice' + (seconds === 45 ? ' is-active' : '') + '" data-action="setup-choice" data-name="pace" data-value="' + seconds + '">' + seconds + ' sec</button>';
    }).join('');

    var activeNote = active
      ? '<div class="card aside-card" style="border-color:#F3D9AE;background:var(--amber-soft)">' +
          '<h3 style="font-size:13px">A test is already open</h3>' +
          '<p>You have an unfinished ' + modeLabel(active.mode).toLowerCase() + ' on <b>' + e(active.setTitle) + '</b>. Starting this one will replace it.</p>' +
          '<a class="btn btn-ghost btn-sm btn-block" style="margin-top:10px" href="#/exam/run">Resume the open test</a>' +
        '</div>'
      : '';

    return pageHead('Exam setup', 'Set up your test',
      'Pick a mode, length and language. Your answers save automatically as you go.',
      '<a class="btn btn-ghost" href="#/set/' + e(set.id) + '">' + icon('chevron-left') + ' Back to set</a>') +

      '<div class="setup-grid">' +
        '<div class="setup-main">' +
          '<form data-form="start-exam" data-set-id="' + e(set.id) + '" style="display:flex;flex-direction:column;gap:12px">' +
            '<section class="card setup-head">' +
              '<div class="setup-head-top">' +
                '<div>' +
                  '<span class="set-subject">' + e(set.subject) + '</span>' +
                  '<h2>' + e(set.title) + '</h2>' +
                  '<p>' + set.questions.length + ' questions' + (hasMl ? ' · English + Malayalam' : ' · English') + '</p>' +
                '</div>' +
                setUpMarkup(n) +
              '</div>' +
            '</section>' +

            '<section class="card">' +
              '<div class="setup-section">' +
                '<div class="setup-label"><span class="field-label">Mode</span><small>Timed or relaxed</small></div>' +
                '<div class="mode-grid" data-choice-group="mode">' + modeButtons + '</div>' +
              '</div>' +

              '<div class="setup-section">' +
                '<div class="setup-label"><span class="field-label">Questions</span><small>Up to ' + n + '</small></div>' +
                '<div class="choice-row" data-choice-group="count">' + countButtons + '</div>' +
              '</div>' +

              '<div class="setup-section">' +
                '<div class="setup-label"><span class="field-label">Language</span><small>Falls back to English if needed</small></div>' +
                '<div class="choice-row" data-choice-group="lang">' +
                  '<button type="button" class="choice' + (lang === 'en' ? ' is-active' : '') + '" data-action="setup-choice" data-name="lang" data-value="en">English</button>' +
                  '<button type="button" class="choice' + (lang === 'ml' ? ' is-active' : '') + '" data-action="setup-choice" data-name="lang" data-value="ml"' + (hasMl ? '' : ' disabled title="This set has no Malayalam questions"') + '>മലയാളം</button>' +
                '</div>' +
                (hasMl ? '' : '<p class="field-hint" style="margin-top:8px">Add Malayalam versions to questions to unlock the Malayalam mode.</p>') +
              '</div>' +

              '<div class="setup-section" id="paceSection"' + (mode === 'practice' ? ' hidden' : '') + '>' +
                '<div class="setup-label"><span class="field-label">Time per question</span><small>Applies to mock tests</small></div>' +
                '<div class="choice-row" data-choice-group="pace">' + paceButtons + '</div>' +
              '</div>' +

              '<div class="setup-section">' +
                '<label class="check-row" style="margin-bottom:12px"><input type="checkbox" name="shuffle" checked><span>Shuffle question order for this attempt</span></label>' +
                '<div class="setup-summary">' +
                  '<span><b id="sumQuestions">' + (defaultCount === n ? 'All ' + n : defaultCount) + '</b> questions</span>' +
                  '<span><b id="sumMode">' + (mode === 'mock' ? 'Mock test' : 'Practice') + '</b></span>' +
                  '<span><b id="sumTime">' + (mode === 'mock' ? A.fmtMinutes(defaultCount * 45) : 'Untimed') + '</b></span>' +
                  '<span><b id="sumLang">' + (lang === 'ml' ? 'Malayalam' : 'English') + '</b></span>' +
                '</div>' +
                '<div class="setup-actions">' +
                  '<button type="submit" class="btn btn-primary btn-lg" id="startExamBtn">' + icon('play') + ' ' + (mode === 'mock' ? 'Start mock test' : 'Start practice') + '</button>' +
                  '<a class="btn btn-ghost btn-lg" href="#/set/' + e(set.id) + '">Back to set</a>' +
                '</div>' +
              '</div>' +

              '<input type="hidden" name="mode" value="' + mode + '">' +
              '<input type="hidden" name="count" value="' + defaultCount + '">' +
              '<input type="hidden" name="lang" value="' + lang + '">' +
              '<input type="hidden" name="pace" value="45">' +
            '</section>' +
          '</form>' +
        '</div>' +

        '<aside class="setup-aside">' +
          activeNote +
          '<div class="card aside-card">' +
            '<h3>' + icon('target') + ' Before you start</h3>' +
            '<ul class="tip-list">' +
              '<li>In a mock test the timer keeps running if you leave the exam page.</li>' +
              '<li>Every answer is saved to this browser as you tap it.</li>' +
              '<li>You can flag questions and return to them from the palette.</li>' +
              '<li>Submit early if you finish — unanswered questions are marked as skipped.</li>' +
            '</ul>' +
          '</div>' +
          '<div class="card aside-card">' +
            '<h3>' + icon('check-circle') + ' Marking</h3>' +
            '<p>One mark per correct answer, no negative marking. Scores below 40% are shown as “below the cut-off” for reference.</p>' +
          '</div>' +
        '</aside>' +
      '</div>';
  };

  views.updateSetupSummary = function () {
    var form = document.querySelector('form[data-form="start-exam"]');
    if (!form) return;
    var set = A.getSet(form.getAttribute('data-set-id'));
    var n = set ? set.questions.length : 0;
    var mode = form.elements.mode.value;
    var count = parseInt(form.elements.count.value, 10) || n;
    var pace = parseInt(form.elements.pace.value, 10) || 45;
    var lang = form.elements.lang.value;

    var questionsEl = document.getElementById('sumQuestions');
    var modeEl = document.getElementById('sumMode');
    var timeEl = document.getElementById('sumTime');
    var langEl = document.getElementById('sumLang');
    var paceSection = document.getElementById('paceSection');
    var startBtn = document.getElementById('startExamBtn');

    if (questionsEl) questionsEl.textContent = count === n ? 'All ' + n : String(count);
    if (modeEl) modeEl.textContent = mode === 'practice' ? 'Practice' : 'Mock test';
    if (timeEl) timeEl.textContent = mode === 'practice' ? 'Untimed' : A.fmtMinutes(count * pace);
    if (langEl) langEl.textContent = lang === 'ml' ? 'Malayalam' : 'English';
    if (paceSection) paceSection.hidden = mode === 'practice';
    if (startBtn) startBtn.innerHTML = icon('play') + ' ' + (mode === 'practice' ? 'Start practice' : 'Start mock test');
  };

  /* ── Exam runner ─────────────────────────────────────── */
  function paletteHTML(exam) {
    return exam.questions.map(function (q, index) {
      var cls = 'palette-btn';
      if (A.isAnswered(exam.responses[index])) cls += ' is-answered';
      if (index === exam.current) cls += ' is-current';
      if (exam.flags[index]) cls += ' is-flagged';
      var label = 'Question ' + (index + 1);
      if (A.isAnswered(exam.responses[index])) label += ', answered';
      if (exam.flags[index]) label += ', flagged';
      return '<button type="button" class="' + cls + '" data-action="exam-goto" data-index="' + index + '" aria-label="' + label + '"' + (index === exam.current ? ' aria-current="true"' : '') + '>' + (index + 1) + '</button>';
    }).join('');
  }

  views.examRun = function () {
    var exam = A.state.activeExam;
    if (!exam || !exam.questions || !exam.questions.length) {
      return pageHead('Exam', 'No exam is open', 'Start a mock test or practice session from your library.',
        '<a class="btn btn-primary" href="#/library">' + icon('book') + ' Open library</a>');
    }

    var total = exam.questions.length;
    var current = Math.min(exam.current, total - 1);
    var question = exam.questions[current];
    var v = A.questionFor(question, exam.lang);
    var selected = exam.responses[current];
    var answered = exam.responses.filter(A.isAnswered).length;
    var flagged = exam.flags[current];
    var hasMl = exam.questions.some(function (q) { return !!q.ml; });
    var isLast = current === total - 1;
    var remaining = A.examRemaining(exam);
    var timerValue = exam.timeLimitSec
      ? (remaining > 0 ? A.fmtDuration(remaining) : '0:00')
      : A.fmtDuration(A.examElapsed(exam));
    var urgent = exam.timeLimitSec && remaining !== null && remaining <= 60;

    var options = v.options.map(function (option, index) {
      var isSelected = parseInt(selected, 10) === index;
      return '<button type="button" class="option' + (isSelected ? ' is-selected' : '') + '" data-action="exam-select" data-index="' + index + '" aria-pressed="' + isSelected + '">' +
        '<span class="option-letter">' + A.optionLetter(index) + '</span>' +
        '<span class="option-text' + mlClass(v.lang) + '"' + langAttr(v.lang) + '>' + A.text(option) + '</span>' +
        icon('check-circle', 'option-mark') +
      '</button>';
    }).join('');

    return '<div class="exam-shell">' +
      '<div class="exam-top">' +
        '<button type="button" class="icon-btn icon-btn--bare" data-action="exam-quit" aria-label="Leave exam">' + icon('chevron-left') + '</button>' +
        '<div class="exam-top-copy">' +
          '<span class="mono">' + modeLabel(exam.mode) + ' · Question ' + (current + 1) + ' of ' + total + '</span>' +
          '<strong>' + e(exam.setTitle) + '</strong>' +
        '</div>' +
        '<div class="exam-timer' + (urgent ? ' is-urgent' : '') + '" id="examTimer" aria-live="off">' +
          '<span id="examTimerLabel">' + (exam.timeLimitSec ? 'Time left' : 'Elapsed') + '</span>' +
          '<strong id="examTimerValue">' + timerValue + '</strong>' +
        '</div>' +
        (hasMl
          ? '<div class="segmented" aria-label="Question language">' +
              '<button type="button" class="' + (exam.lang === 'en' ? 'is-active' : '') + '" data-action="exam-lang" data-value="en">EN</button>' +
              '<button type="button" class="' + (exam.lang === 'ml' ? 'is-active' : '') + '" data-action="exam-lang" data-value="ml">ML</button>' +
            '</div>'
          : '<span class="chip">English</span>') +
        '<button type="button" class="btn btn-primary btn-sm" data-action="exam-submit">Submit</button>' +
      '</div>' +

      '<div class="exam-progress-row">' +
        '<div class="progress"><span style="width:' + Math.round(answered / total * 100) + '%"></span></div>' +
        '<span>' + answered + ' / ' + total + ' answered</span>' +
      '</div>' +

      '<div class="exam-layout">' +
        '<div class="exam-main">' +
          '<div class="mobile-palette" aria-label="Question palette">' + paletteHTML(exam) + '</div>' +
          '<section class="card exam-q">' +
            '<div class="exam-q-meta">' +
              '<span class="chip chip--indigo">Question ' + (current + 1) + '</span>' +
              '<span class="chip">' + e(exam.subject) + '</span>' +
              (v.fallback ? '<span class="chip chip--amber">English only</span>' : '') +
              (flagged ? '<span class="chip chip--amber">' + icon('flag') + ' Flagged</span>' : '') +
              '<span class="chip">1 mark</span>' +
            '</div>' +
            '<h2 class="exam-stem' + mlClass(v.lang) + '"' + langAttr(v.lang) + '>' + A.text(v.stem) + '</h2>' +
            '<div class="option-list" role="group" aria-label="Answer options">' + options + '</div>' +
            '<div class="exam-q-foot">' +
              '<div style="display:flex;gap:7px;flex-wrap:wrap">' +
                '<button type="button" class="btn btn-subtle btn-sm" data-action="exam-clear"' + (A.isAnswered(selected) ? '' : ' disabled') + '>Clear answer</button>' +
                '<button type="button" class="btn btn-ghost btn-sm flag-btn' + (flagged ? ' is-active' : '') + '" data-action="exam-flag">' + icon('flag') + (flagged ? ' Flagged' : ' Flag') + '</button>' +
              '</div>' +
              '<div class="exam-nav-btns">' +
                '<button type="button" class="btn btn-ghost btn-sm" data-action="exam-prev"' + (current === 0 ? ' disabled' : '') + '>' + icon('arrow-left') + ' Prev</button>' +
                (isLast
                  ? '<button type="button" class="btn btn-primary btn-sm" data-action="exam-submit">Finish ' + icon('check') + '</button>'
                  : '<button type="button" class="btn btn-primary btn-sm" data-action="exam-next">Next ' + icon('arrow-right') + '</button>') +
              '</div>' +
            '</div>' +
          '</section>' +
        '</div>' +

        '<aside class="exam-side">' +
          '<div class="card palette-card">' +
            '<div class="palette-head"><h3>Question palette</h3><span>' + answered + '/' + total + '</span></div>' +
            '<div class="palette-grid">' + paletteHTML(exam) + '</div>' +
            '<div class="palette-legend">' +
              '<span><i class="answered"></i> Answered</span>' +
              '<span><i class="current"></i> Current</span>' +
              '<span><i class="flagged"></i> Flagged</span>' +
            '</div>' +
            '<button type="button" class="btn btn-primary btn-block" style="margin-top:14px" data-action="exam-submit">' + icon('check') + ' Submit test</button>' +
          '</div>' +
          '<div class="card exam-side-card">' +
            '<h4>' + icon('check-circle') + ' No negative marking</h4>' +
            '<p>One mark for each correct answer. Unanswered questions are counted as skipped.</p>' +
          '</div>' +
          '<div class="card exam-side-card">' +
            '<h4>' + icon('target') + ' Keyboard shortcuts</h4>' +
            '<p><b>1–4</b> answer · <b>← →</b> move · <b>F</b> flag · <b>Enter</b> next.</p>' +
          '</div>' +
        '</aside>' +
      '</div>' +
    '</div>';
  };

  /* ── Results & review ────────────────────────────────── */
  views.result = function (attemptId) {
    var attempt = A.getAttempt(attemptId);
    if (!attempt) return views.notFound('That result is no longer available.');
    var b = A.attemptBreakdown(attempt);
    var set = A.getSet(attempt.setId);
    var name = firstName();
    var verdict = b.pct >= 80 ? 'Excellent work' : b.pct >= 60 ? 'Solid effort' : b.pct >= 40 ? 'Good progress' : 'Keep going';
    var heading = verdict + (name ? ', ' + e(name) : '') + '.';
    var ringClass = b.pct >= 60 ? 'ring--success' : b.pct >= 40 ? 'ring--amber' : 'ring--danger';
    var reviewHref = set ? '#/set/' + set.id : '#/progress';
    var retakeHref = '#/exam/setup/' + e(attempt.setId) + (attempt.mode === 'practice' ? '?mode=practice' : '');

    var filters = [['all', 'All'], ['wrong', 'Wrong'], ['skipped', 'Skipped']].map(function (pair) {
      return '<button type="button" class="' + (ui.reviewFilter === pair[0] ? 'is-active' : '') + '" data-action="review-filter" data-value="' + pair[0] + '">' + pair[1] + '</button>';
    }).join('');

    return pageHead('Result · ' + modeLabel(attempt.mode), 'Review your attempt',
      'Go through every answer, see the explanation, and decide what to revise next.',
      '<a class="btn btn-ghost" href="#/progress">' + icon('chart') + ' Progress</a>' +
      (set ? '<a class="btn btn-primary" href="' + reviewHref + '">' + icon('book') + ' Back to set</a>' : ''),
      { href: '#/library', label: 'Library' }) +

      '<section class="result-hero' + (b.passed ? '' : ' is-fail') + '">' +
        '<div class="result-hero-top">' +
          '<div class="result-hero-copy">' +
            '<p class="eyebrow"><span class="eyebrow-dot"></span>' + (b.passed ? 'Above the 40% mark' : 'Below the 40% mark') + '</p>' +
            '<h1>' + heading + '</h1>' +
            '<p class="result-hero-sub">' + e(attempt.setTitle) + ' · ' + A.fmtDate(attempt.finishedAt) + ' · ' + modeLabel(attempt.mode).toLowerCase() + ' · ' + A.fmtDuration(attempt.durationSec) + '</p>' +
            '<div class="result-hero-actions">' +
              '<button type="button" class="btn btn-primary" data-action="scroll-review">' + icon('list') + ' Review answers</button>' +
              '<a class="btn btn-ghost" href="' + retakeHref + '">' + icon('refresh') + ' Retake</a>' +
              (set ? '<a class="btn btn-subtle" href="' + reviewHref + '">' + icon('book') + ' Open set</a>' : '') +
            '</div>' +
          '</div>' +
          '<div class="' + ringClass + ' ring ring--lg" style="--pct:' + b.pct + '">' +
            '<div class="ring-inner"><strong>' + b.pct + '%</strong><small>Score</small></div>' +
          '</div>' +
        '</div>' +
        '<div class="result-stats">' +
          '<div><strong>' + b.correct + '</strong><span>Correct</span></div>' +
          '<div><strong>' + b.wrong + '</strong><span>Wrong</span></div>' +
          '<div><strong>' + b.skipped + '</strong><span>Skipped</span></div>' +
          '<div><strong>' + A.fmtDuration(attempt.durationSec) + '</strong><span>Time taken</span></div>' +
        '</div>' +
      '</section>' +

      '<section class="review-section" id="reviewSection">' +
        '<div class="section-head" style="align-items:flex-end">' +
          '<div><h2 class="section-title">Answer review</h2><p class="muted" style="font-size:10.5px;margin-top:2px">' + b.accuracy + '% accuracy on answered questions.</p></div>' +
          '<div class="segmented review-filters" aria-label="Filter answers">' + filters + '</div>' +
        '</div>' +
        '<div class="review-list" id="reviewList">' + views.reviewList(attempt) + '</div>' +
      '</section>';
  };

  views.reviewList = function (attempt) {
    var b = A.attemptBreakdown(attempt);
    var filter = ui.reviewFilter;
    var items = [];

    attempt.questions.forEach(function (q, index) {
      var response = attempt.responses[index];
      var skipped = !A.isAnswered(response);
      var correct = !skipped && parseInt(response, 10) === q.answer;
      if (filter === 'wrong' && (skipped || correct)) return;
      if (filter === 'skipped' && !skipped) return;
      items.push({ q: q, index: index, response: response, skipped: skipped, correct: correct });
    });

    if (!items.length) {
      return '<div class="card card-pad"><p class="muted" style="font-size:12px">' +
        (filter === 'wrong' ? 'No wrong answers in this attempt — nicely done.' :
         filter === 'skipped' ? 'You answered every question in this attempt.' :
         'There are no answers to review.') + '</p></div>';
    }

    return items.map(function (item) {
      var v = A.questionFor(item.q, attempt.lang);
      var statusChip = item.skipped
        ? '<span class="chip chip--amber">Skipped</span>'
        : item.correct
          ? '<span class="chip chip--success">' + icon('check') + ' Correct</span>'
          : '<span class="chip chip--danger">' + icon('close') + ' Wrong</span>';
      var options = v.options.map(function (option, index) {
        var isCorrect = index === item.q.answer;
        var isSelected = parseInt(item.response, 10) === index;
        var cls = 'review-option';
        if (isCorrect) cls += ' is-correct';
        if (isSelected && !isCorrect) cls += ' is-selected';
        var mark = isCorrect ? icon('check') : (isSelected ? icon('close') : '');
        return '<div class="' + cls + '">' +
          '<span class="letter">' + A.optionLetter(index) + '</span>' +
          '<span class="' + mlClass(v.lang) + '"' + langAttr(v.lang) + '>' + A.text(option) + '</span>' +
          mark +
        '</div>';
      }).join('');

      return '<article class="review-card' + (item.skipped ? ' is-skipped' : item.correct ? '' : ' is-wrong') + '">' +
        '<div class="review-top">' +
          '<span class="review-index">Q ' + A.pad(item.index + 1) + '</span>' +
          (v.fallback ? '<span class="chip chip--amber">English only</span>' : '') +
          (attempt.flags[item.index] ? '<span class="chip chip--amber">Flagged</span>' : '') +
          statusChip +
        '</div>' +
        '<h3' + langAttr(v.lang) + ' class="' + mlClass(v.lang) + '">' + A.text(v.stem) + '</h3>' +
        '<div class="review-options">' + options + '</div>' +
        (v.explanation ? '<div class="review-explain"><span class="mono">Why</span><p class="' + mlClass(v.lang) + '"' + langAttr(v.lang) + '>' + A.text(v.explanation) + '</p></div>' : '') +
      '</article>';
    }).join('') +
    '<p class="muted" style="font-size:10px;text-align:right">' + b.correct + ' correct · ' + b.wrong + ' wrong · ' + b.skipped + ' skipped</p>';
  };

  /* ── Progress ────────────────────────────────────────── */
  function trendChartHTML(attempts) {
    if (!attempts.length) {
      return '<div class="chart-empty">' + icon('chart') +
        '<p>Finish a test to see your score trend here.</p>' +
        '<a class="btn btn-soft btn-sm" style="margin-top:12px" href="#/library">' + icon('play') + ' Take a test</a>' +
      '</div>';
    }

    var w = 640, h = 210, padL = 32, padR = 14, padT = 14, padB = 30;
    var n = attempts.length;
    var innerW = w - padL - padR;
    var innerH = h - padT - padB;

    function x(i) {
      return n === 1 ? padL + innerW / 2 : padL + (i * innerW) / (n - 1);
    }
    function y(value) {
      return padT + ((100 - value) / 100) * innerH;
    }

    var grid = [100, 75, 50, 25, 0].map(function (value) {
      return '<line class="grid-line" x1="' + padL + '" y1="' + y(value) + '" x2="' + (w - padR) + '" y2="' + y(value) + '"/>' +
        '<text class="axis-label" x="0" y="' + (y(value) + 3) + '">' + value + '%</text>';
    }).join('');

    var points = attempts.map(function (attempt, index) {
      var b = A.attemptBreakdown(attempt);
      return { x: x(index), y: y(b.pct), pct: b.pct, date: attempt.finishedAt };
    });

    var line = points.map(function (point, index) {
      return (index === 0 ? 'M' : 'L') + point.x.toFixed(1) + ' ' + point.y.toFixed(1);
    }).join(' ');
    var area = n > 1
      ? line + ' L' + points[n - 1].x.toFixed(1) + ' ' + (h - padB) + ' L' + points[0].x.toFixed(1) + ' ' + (h - padB) + ' Z'
      : '';

    var labels = points.map(function (point) {
      return '<text class="axis-label" x="' + point.x.toFixed(1) + '" y="' + (h - 7) + '" text-anchor="middle">' + A.fmtDateShort(point.date) + '</text>';
    }).join('');

    var dots = points.map(function (point) {
      return '<circle class="dot' + (point.pct >= 40 ? ' pass' : '') + '" cx="' + point.x.toFixed(1) + '" cy="' + point.y.toFixed(1) + '" r="4.5"/>';
    }).join('');

    return '<svg class="trend-chart" viewBox="0 0 ' + w + ' ' + h + '" role="img" aria-label="Score trend for your last ' + n + ' attempts">' +
      '<defs><linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">' +
        '<stop offset="0%" stop-color="#5B4BF6" stop-opacity=".24"/>' +
        '<stop offset="100%" stop-color="#5B4BF6" stop-opacity="0"/>' +
      '</linearGradient></defs>' +
      grid +
      (area ? '<path class="area" d="' + area + '"/>' : '') +
      (n > 1 ? '<path class="line" d="' + line + '"/>' : '') +
      dots + labels +
    '</svg>';
  }

  views.progress = function () {
    var stats = A.statsAll();
    var attempts = A.state.attempts;
    var chartAttempts = attempts.slice(0, 8).reverse();
    var streak = A.streak();
    var days = A.lastSevenDays();
    var subjects = A.subjectStats();
    var recent = attempts.slice(0, 6);

    var dayRow = days.map(function (day) {
      return '<span class="streak-day' + (day.done ? ' is-done' : '') + (day.today ? ' is-today' : '') + '"><i></i>' + e(day.label) + '</span>';
    }).join('');

    var subjectRows = subjects.length
      ? '<div class="subject-list">' + subjects.map(function (row) {
          return '<div class="subject-row">' +
            '<div class="subject-row-top"><strong>' + e(row.subject) + '</strong><span>' + row.accuracy + '% · ' + A.plural(row.tests, 'test') + '</span></div>' +
            '<div class="progress progress--success progress--slim"><span style="width:' + row.accuracy + '%"></span></div>' +
          '</div>';
        }).join('') + '</div>'
      : '<p class="subject-empty">Subject accuracy appears after your first test.</p>';

    var recentRows = recent.length
      ? recent.map(function (attempt) {
          var b = A.attemptBreakdown(attempt);
          return '<a class="attempt-row attempt-row--detailed" href="#/result/' + e(attempt.id) + '">' +
            '<span class="attempt-date">' + A.fmtDateShort(attempt.finishedAt) + '</span>' +
            '<span class="attempt-row-copy">' +
              '<strong>' + e(attempt.setTitle) + '</strong>' +
              '<small>' + modeLabel(attempt.mode) + ' · ' + b.correct + '/' + b.total + ' correct · ' + A.fmtDuration(attempt.durationSec) + '</small>' +
            '</span>' +
            '<span class="attempt-mode chip">' + modeLabel(attempt.mode) + '</span>' +
            scoreChip(b.pct) +
          '</a>';
        }).join('')
      : '<p class="muted" style="font-size:11.5px;line-height:1.6">No completed tests yet. Your attempts will be listed here with their scores.</p>';

    return pageHead('Progress', 'Your preparation, in numbers',
      'Scores, streaks and subject accuracy — all calculated from the tests saved in this browser.',
      '<a class="btn btn-primary" href="#/library">' + icon('play') + ' Take a test</a>') +

      '<div class="kpi-grid progress-kpis">' +
        '<div class="stat-tile">' + icon('trophy') + '<strong>' + (attempts.length ? stats.best + '%' : '—') + '</strong><span>Best score</span></div>' +
        '<div class="stat-tile">' + icon('target') + '<strong>' + (attempts.length ? stats.accuracy + '%' : '—') + '</strong><span>Overall accuracy</span></div>' +
        '<div class="stat-tile">' + icon('list') + '<strong>' + stats.answered + '</strong><span>Questions answered</span></div>' +
        '<div class="stat-tile">' + icon('chart') + '<strong>' + attempts.length + '</strong><span>Tests taken</span></div>' +
      '</div>' +

      '<div class="progress-top">' +
        '<section class="card chart-card">' +
          '<div class="chart-head">' +
            '<div><h2>Score trend</h2><p>Last ' + (chartAttempts.length || 8) + ' attempts, oldest to newest.</p></div>' +
            (attempts.length ? '<span class="chart-badge">' + icon('chart') + ' Avg ' + stats.avg + '%</span>' : '') +
          '</div>' +
          '<div class="chart-wrap">' + trendChartHTML(chartAttempts) + '</div>' +
          (attempts.length
            ? '<div class="chart-foot"><span><i></i> Attempt</span><span><i class="pass"></i> 40% or above</span><span>' + stats.attempts + ' total attempts</span></div>'
            : '') +
        '</section>' +
        '<section class="dark-card streak-panel">' +
          '<span class="mono">Study streak</span>' +
          '<div class="streak-value">' + icon('flame') + '<strong>' + streak + '</strong><span>' + (streak === 1 ? 'day' : 'days') + '</span></div>' +
          '<p>' + (streak
            ? 'One test a day keeps the streak alive. Reviewing counts too — take a short practice run if you are low on time.'
            : 'Take a test today to start a streak. Even five questions count.') + '</p>' +
          '<div class="streak-days">' + dayRow + '</div>' +
        '</section>' +
      '</div>' +

      '<div class="progress-bottom">' +
        '<section class="card card-pad">' +
          '<div class="section-head"><h2 class="section-title">Accuracy by subject</h2><span class="chip">' + A.plural(subjects.length, 'subject') + '</span></div>' +
          subjectRows +
        '</section>' +
        '<section class="card card-pad">' +
          '<div class="section-head">' +
            '<h2 class="section-title">Recent attempts</h2>' +
          '</div>' +
          '<div class="attempt-table">' + recentRows + '</div>' +
        '</section>' +
      '</div>';
  };

  /* ── Profile ─────────────────────────────────────────── */
  views.profile = function () {
    var profile = A.state.profile;
    var stats = A.statsAll();
    var library = A.libraryStats();
    var bytes = A.storageBytes();
    var pct = Math.min(100, Math.max(2, Math.round(bytes / (5 * 1024 * 1024) * 100)));
    var account = A.auth ? A.auth.user() : null;
    var signedIn = !!(A.auth && A.auth.isAccount() && account);
    var name = String(profile.name || '').trim();
    var initials = A.initials(name);
    var countdown = A.daysUntil(profile.examDate);

    return pageHead('Your profile', 'A little about you',
      signedIn
        ? 'Your name, target and daily goal are saved to your account and sync with your other devices.'
        : 'Your name, target and daily goal stay on this device. They shape greetings and goals, nothing else.',
      '<a class="btn btn-ghost" href="#/settings">' + icon('settings') + ' Settings</a>') +

      '<div class="profile-grid">' +
        '<div class="profile-main">' +
          '<section class="card profile-card">' +
            '<div class="profile-head">' +
              '<span class="avatar avatar--lg">' + e(initials) + '</span>' +
              '<div class="profile-head-copy">' +
                '<h1>' + e(name || 'Guest learner') + '</h1>' +
                '<p>' + e(profile.target || 'Kerala PSC') + (profile.examDate ? ' · exam ' + A.fmtDate(profile.examDate) : '') + '</p>' +
                '<span class="chip chip--indigo">' + icon('lock') + ' ' +
                  (signedIn ? (account.role === 'admin' ? 'Admin account' : 'Learner account') : 'Local profile') +
                '</span>' +
                (countdown !== null && countdown >= 0 ? '<span class="chip chip--amber" style="margin-left:4px">' + (countdown === 0 ? 'Exam today' : countdown + ' days to go') + '</span>' : '') +
              '</div>' +
              '<a class="btn btn-ghost btn-sm" href="#/progress">' + icon('chart') + ' Progress</a>' +
            '</div>' +
            '<div class="profile-stats">' +
              '<div><strong>' + stats.attempts + '</strong><span>Tests taken</span></div>' +
              '<div><strong>' + stats.answered + '</strong><span>Answered</span></div>' +
              '<div><strong>' + library.sets + '</strong><span>Sets</span></div>' +
            '</div>' +

            '<form class="profile-form" data-form="profile">' +
              '<h2>Profile details</h2>' +
              '<div class="form-grid">' +
                '<div class="field">' +
                  '<label class="field-label" for="profileName">Your name</label>' +
                  '<input class="input" id="profileName" name="name" maxlength="60" placeholder="e.g. Anjali" value="' + e(profile.name) + '">' +
                '</div>' +
                '<div class="field">' +
                  '<label class="field-label" for="profileTarget">Target exam</label>' +
                  '<input class="input" id="profileTarget" name="target" maxlength="70" list="targetList" placeholder="Kerala PSC" value="' + e(profile.target) + '">' +
                  '<datalist id="targetList">' +
                    '<option value="Kerala PSC"></option>' +
                    '<option value="Kerala PSC LD Clerk"></option>' +
                    '<option value="Kerala PSC LGS"></option>' +
                    '<option value="Kerala PSC Degree Level"></option>' +
                    '<option value="University Assistant"></option>' +
                    '<option value="SSC / RRB"></option>' +
                  '</datalist>' +
                '</div>' +
              '</div>' +
              '<div class="form-grid" style="margin-top:14px">' +
                '<div class="field">' +
                  '<label class="field-label" for="profileExamDate">Exam date <span class="faint">(optional)</span></label>' +
                  '<input class="input" id="profileExamDate" type="date" name="examDate" value="' + e(profile.examDate) + '">' +
                '</div>' +
                '<div class="field">' +
                  '<label class="field-label" for="profileGoal">Daily question goal</label>' +
                  '<div class="goal-stepper">' +
                    '<button type="button" class="btn btn-ghost" data-action="goal-step" data-delta="-5" aria-label="Decrease goal">−</button>' +
                    '<input class="input" id="profileGoal" type="number" name="dailyGoal" min="5" max="200" step="5" value="' + profile.dailyGoal + '">' +
                    '<button type="button" class="btn btn-ghost" data-action="goal-step" data-delta="5" aria-label="Increase goal">+</button>' +
                  '</div>' +
                '</div>' +
              '</div>' +
              '<div class="field" style="margin-top:14px">' +
                '<span class="field-label">Preferred question language</span>' +
                '<div class="lang-options">' +
                  '<button type="button" class="lang-choice' + (profile.language === 'en' ? ' is-active' : '') + '" data-action="profile-lang" data-value="en"><strong>English</strong><small>Default for sets and exams</small></button>' +
                  '<button type="button" class="lang-choice' + (profile.language === 'ml' ? ' is-active' : '') + '" data-action="profile-lang" data-value="ml"><strong>മലയാളം</strong><small>Questions fall back to English when needed</small></button>' +
                '</div>' +
                '<input type="hidden" name="language" value="' + profile.language + '">' +
              '</div>' +
              '<div class="form-actions">' +
                '<button type="submit" class="btn btn-primary">' + icon('check') + ' Save profile</button>' +
                '<span class="muted" style="font-size:10.5px">Changes apply immediately across the app.</span>' +
              '</div>' +
            '</form>' +
          '</section>' +
        '</div>' +

        '<aside class="profile-aside">' +
          '<section class="card storage-card">' +
            '<h3>Your data</h3>' +
            '<p>Your library, results and profile are tied to your account and kept in this browser. If cloud sync is on, they also update your Neon workspace.</p>' +
            '<div class="storage-meter"><span style="width:' + pct + '%"></span></div>' +
            '<div class="storage-meta"><span>' + A.fmtBytes(bytes) + ' used</span><span>of ~5 MB</span></div>' +
            '<div class="data-actions">' +
              '<a class="btn btn-ghost btn-sm" href="#/settings">' + icon('settings') + ' Account settings</a>' +
            '</div>' +
          '</section>' +
          '<section class="card storage-card">' +
            '<h3>' + icon('lock') + ' Account-first' +
            '</h3>' +
            '<p>' + (A.auth && A.auth.isAccount()
              ? 'You are signed in as <b>' + e(A.auth.displayName()) + '</b>. Question sets are assigned by the administrator; your attempts and profile stay private to your account.'
              : 'This local copy is not connected to an account server. Scanning and backups are available in the admin console.') + '</p>' +
            '<a class="link" style="display:inline-block;margin-top:8px;font-size:10.5px" href="#/settings">Review settings →</a>' +
          '</section>' +
          '<section class="card storage-card">' +
            '<h3>' + icon('target') + ' Today</h3>' +
            '<p>' + A.goal().done + ' of ' + A.goal().total + ' questions answered. ' + e(A.goal().note) + '.</p>' +
            '<a class="btn btn-soft btn-sm btn-block" style="margin-top:10px" href="#/library">' + icon('play') + ' Start a session</a>' +
          '</section>' +
        '</aside>' +
      '</div>';
  };

  /* ── Accounts ────────────────────────────────────────── */
  views.login = function () {
    var error = A.auth ? A.auth.error() : '';
    return '<div class="auth-gate">' +
      '<section class="auth-card">' +
        '<a class="auth-brand" href="#/login">' +
          '<img src="assets/logo.svg" alt="" width="46" height="46">' +
          '<span><strong>Padanam</strong><small>PSC study library</small></span>' +
        '</a>' +
        '<p class="eyebrow"><span class="eyebrow-dot"></span>Sign in</p>' +
        '<h1>Your study desk is private.</h1>' +
        '<p class="auth-sub">Sign in with the account your administrator created. The same login works for admin and learner accounts.</p>' +
        (error ? '<div class="auth-error" role="alert">' + e(error) + '</div>' : '') +
        '<form data-form="login" id="loginForm" class="auth-form">' +
          '<div class="field">' +
            '<label class="field-label" for="loginUsername">Username</label>' +
            '<input class="input" id="loginUsername" name="username" autocomplete="username" autocapitalize="none" spellcheck="false" required autofocus placeholder="e.g. anjali">' +
          '</div>' +
          '<div class="field">' +
            '<label class="field-label" for="loginPassword">Password</label>' +
            '<input class="input" id="loginPassword" name="password" type="password" autocomplete="current-password" required placeholder="Your password">' +
          '</div>' +
          '<div class="auth-error" id="loginError" role="alert"></div>' +
          '<button type="submit" class="btn btn-primary btn-block">' + icon('lock') + ' Sign in</button>' +
        '</form>' +
        '<p class="auth-note">Accounts are created by the administrator. Ask them if you need access or a new library.</p>' +
      '</section>' +
    '</div>';
  };

  views.setup = function () {
    var error = A.auth ? A.auth.error() : '';
    return '<div class="auth-gate">' +
      '<section class="auth-card">' +
        '<a class="auth-brand" href="#/home">' +
          '<img src="assets/logo.svg" alt="" width="46" height="46">' +
          '<span><strong>Padanam</strong><small>PSC study library</small></span>' +
        '</a>' +
        '<p class="eyebrow"><span class="eyebrow-dot"></span>Setup needed</p>' +
        '<h1>Accounts are not configured.</h1>' +
        '<p class="auth-sub">This site needs three Cloudflare Pages secrets before anyone can sign in. Add them, then redeploy or retry.</p>' +
        (error ? '<div class="auth-error" role="alert">' + e(error) + '</div>' : '') +
        '<div class="setup-list">' +
          '<div><code>AUTH_SECRET</code><span>At least 32 random characters for signing sessions.</span></div>' +
          '<div><code>ADMIN_USERNAME</code><span>The first admin username.</span></div>' +
          '<div><code>ADMIN_PASSWORD</code><span>At least 8 characters for that admin.</span></div>' +
        '</div>' +
        '<p class="auth-note">The Neon connection string is also required. See <span class="mono">web/README.md</span> for the full secret list.</p>' +
        '<button type="button" class="btn btn-primary btn-block" data-action="reload-app">' + icon('refresh') + ' Retry setup check</button>' +
      '</section>' +
    '</div>';
  };

  function adminUsersCard() {
    if (!A.auth || !A.auth.isAccount()) {
      return '<section class="card settings-card" id="adminUsers">' +
        '<div class="settings-card-head">' +
          '<span class="settings-icon">' + icon('user') + '</span>' +
          '<div><h2>Accounts</h2><p>User accounts live in Neon, so they are created from the deployed site.</p></div>' +
          '<span class="chip">Local mode</span>' +
        '</div>' +
        '<div class="inline-note">' + icon('info') +
          '<div>This copy is running without the account server. Scanning, appearance and local backups still work here. Deploy the <span class="mono">functions/</span> folder with Pages and set <span class="mono">AUTH_SECRET</span>, <span class="mono">ADMIN_USERNAME</span> and <span class="mono">ADMIN_PASSWORD</span> to create learner accounts.</div>' +
        '</div>' +
      '</section>';
    }

    var info = A.admin ? A.admin.info() : { checked: false, loading: false, error: '', users: [], sets: [] };
    var users = Array.isArray(info.users) ? info.users : [];
    var sets = Array.isArray(info.sets) ? info.sets : [];
    var byId = {};
    sets.forEach(function (set) { byId[set.id] = set; });

    var body;
    if (info.loading && !users.length) {
      body = '<div class="inline-note">' + icon('refresh') + '<div>Loading accounts from Neon…</div></div>';
    } else if (info.error && !users.length) {
      body = '<div class="inline-note inline-note--danger">' + icon('alert') + '<div>' + e(info.error) + '</div></div>';
    } else if (!users.length) {
      body = '<div class="admin-empty">' +
        '<span class="empty-icon">' + icon('user') + '</span>' +
        '<h3>No learner accounts yet</h3>' +
        '<p>Create the first account above. Then sync your library and assign a few sets.</p>' +
      '</div>';
    } else {
      body = '<div class="admin-user-list">' + users.map(function (user) {
        var assigned = Array.isArray(user.setIds) ? user.setIds : [];
        var available = sets.filter(function (set) { return assigned.indexOf(set.id) < 0; });
        var chips = assigned.length
          ? assigned.map(function (id) {
              var set = byId[id];
              return '<span class="chip chip--indigo">' + icon('book') + ' ' + e(set ? set.title : id) + '</span>';
            }).join('')
          : '<span class="muted" style="font-size:10.5px">No sets assigned yet</span>';

        var picker;
        if (available.length) {
          picker = '<details class="admin-assign">' +
            '<summary>Assign sets <span class="faint">· ' + available.length + ' available</span></summary>' +
            '<form data-form="assign-sets" data-user-id="' + e(user.id) + '">' +
              '<div class="admin-set-list">' + available.map(function (set) {
                return '<label class="check-row admin-set-choice">' +
                  '<input type="checkbox" name="setIds" value="' + e(set.id) + '">' +
                  '<span><strong>' + e(set.title) + '</strong><small>' + e(set.subject) + ' · ' + A.plural(set.questions, 'question') + '</small></span>' +
                '</label>';
              }).join('') + '</div>' +
              '<div class="form-actions" style="margin-top:10px">' +
                '<button type="submit" class="btn btn-primary btn-sm">' + icon('check') + ' Assign selected</button>' +
              '</div>' +
            '</form>' +
          '</details>';
        } else {
          picker = '<p class="field-hint">' + (sets.length
            ? 'Every set in the admin library is already assigned.'
            : 'No sets in the admin workspace yet. Add or import sets, press Sync now, then refresh this list.') + '</p>';
        }

        var passwordReset = '<details class="admin-assign">' +
          '<summary>Reset password</summary>' +
          '<form data-form="reset-password" data-user-id="' + e(user.id) + '">' +
            '<div class="field">' +
              '<label class="field-label" for="resetPassword-' + e(user.id) + '">New password for @' + e(user.username) + '</label>' +
              '<input class="input" id="resetPassword-' + e(user.id) + '" name="password" type="password" autocomplete="new-password" required minlength="8" maxlength="200" placeholder="At least 8 characters">' +
              '<span class="field-hint">Share it privately. The learner can keep using the current session until it expires.</span>' +
            '</div>' +
            '<div class="form-actions" style="margin-top:10px">' +
              '<button type="submit" class="btn btn-primary btn-sm">' + icon('lock') + ' Set new password</button>' +
            '</div>' +
          '</form>' +
        '</details>';

        return '<article class="admin-user">' +
          '<div class="admin-user-head">' +
            '<span class="avatar avatar--sm">' + e(A.initials(user.displayName)) + '</span>' +
            '<div class="admin-user-copy">' +
              '<strong>' + e(user.displayName) + '</strong>' +
              '<small>@' + e(user.username) + (user.createdAt ? ' · joined ' + e(A.fmtDate(user.createdAt)) : '') + '</small>' +
            '</div>' +
            '<span class="chip">' + A.plural(assigned.length, 'set') + '</span>' +
          '</div>' +
          '<div class="admin-user-sets">' + chips + '</div>' +
          picker +
          passwordReset +
        '</article>';
      }).join('') + '</div>';
    }

    return '<section class="card settings-card" id="adminUsers">' +
      '<div class="settings-card-head">' +
        '<span class="settings-icon">' + icon('user') + '</span>' +
        '<div><h2>Accounts</h2><p>Create a learner account, then assign sets from the admin library. Users cannot create or delete sets.</p></div>' +
        '<span class="chip">' + e(A.plural(users.length, 'account')) + '</span>' +
      '</div>' +
      '<form class="admin-create" data-form="create-user" id="createUserForm">' +
        '<div class="form-grid">' +
          '<div class="field">' +
            '<label class="field-label" for="adminUsername">Username</label>' +
            '<input class="input" id="adminUsername" name="username" autocomplete="off" autocapitalize="none" spellcheck="false" required minlength="3" maxlength="32" placeholder="e.g. anjali">' +
            '<span class="field-hint">Lowercase letters, numbers, dots, dashes or underscores.</span>' +
          '</div>' +
          '<div class="field">' +
            '<label class="field-label" for="adminDisplayName">Display name</label>' +
            '<input class="input" id="adminDisplayName" name="displayName" maxlength="80" placeholder="e.g. Anjali S.">' +
          '</div>' +
        '</div>' +
        '<div class="field" style="margin-top:14px">' +
          '<label class="field-label" for="adminPassword">First password</label>' +
          '<input class="input" id="adminPassword" name="password" type="password" autocomplete="new-password" required minlength="8" maxlength="200" placeholder="At least 8 characters">' +
          '<span class="field-hint">Share this password with the learner privately. You can reset it later from the account list.</span>' +
        '</div>' +
        '<div class="form-actions" style="margin-top:14px">' +
          '<button type="submit" class="btn btn-primary">' + icon('plus') + ' Create account</button>' +
          '<button type="button" class="btn btn-ghost btn-sm" data-action="admin-refresh">' + icon('refresh') + ' Refresh accounts</button>' +
        '</div>' +
      '</form>' +
      '<div id="adminUserList" style="margin-top:18px">' + body + '</div>' +
    '</section>';
  }

  /* ── Settings & admin console ────────────────────────── */
  function settingsPage(adminConsole) {
    var who = A.auth ? A.auth.user() : null;
    var settings = A.state.settings;
    var provider = A.gemini.provider(settings);
    var bytes = A.storageBytes();
    var library = A.libraryStats();
    var syncInfo = A.sync ? A.sync.info() : null;
    var syncConfig = A.sync ? A.sync.config() : { token: '', auto: false };
    var accountMode = !!(A.auth && A.auth.isAccount());
    var syncReady = !!(syncInfo && syncInfo.checked && syncInfo.available && syncInfo.configured);
    var syncConnected = accountMode
      ? !!(syncReady && A.auth.signedIn())
      : !!(syncReady && syncConfig.token);
    var syncChip = !syncInfo
      ? 'Off'
      : syncInfo.syncing
        ? 'Syncing'
        : syncConnected
          ? 'On'
          : syncReady
            ? 'Ready'
            : syncInfo.checked && !syncInfo.available
              ? 'Off'
              : 'Check';
    var syncStatus;
    if (!syncInfo) {
      syncStatus = 'The cloud sync module did not load. Reload the page and try again.';
    } else if (syncInfo.syncing) {
      syncStatus = 'Syncing with Neon…';
    } else if (!syncInfo.checked) {
      syncStatus = 'Checking the sync function on this site…';
    } else if (!syncInfo.available) {
      syncStatus = syncInfo.error || 'Cloud sync is not available on this site.';
    } else if (!syncInfo.configured) {
      syncStatus = syncInfo.error || 'The sync function is online, but the Neon secrets are missing in Cloudflare Pages.';
    } else if (accountMode) {
      syncStatus = 'Connected to ' + (syncInfo.host || 'Neon') + '.';
      if (syncInfo.lastSyncAt) syncStatus += ' Last synced ' + A.fmtRelative(syncInfo.lastSyncAt) + '.';
      if (syncInfo.lastResult) {
        syncStatus += ' Cloud has ' + A.plural(syncInfo.lastResult.sets, 'set') +
          ' and ' + A.plural(syncInfo.lastResult.attempts, 'attempt') + '.';
      }
    } else if (!syncConfig.token) {
      syncStatus = 'Ready on ' + (syncInfo.host || 'Neon') + '. Enter the sync code you set in Cloudflare Pages.';
    } else {
      syncStatus = 'Connected to ' + (syncInfo.host || 'Neon') + '.';
      if (syncInfo.lastSyncAt) syncStatus += ' Last synced ' + A.fmtRelative(syncInfo.lastSyncAt) + '.';
      if (syncInfo.lastResult) {
        syncStatus += ' Cloud has ' + A.plural(syncInfo.lastResult.sets, 'set') +
          ' and ' + A.plural(syncInfo.lastResult.attempts, 'attempt') + '.';
      }
    }

    var geminiCatalog = A.gemini.catalog('gemini');
    var models = geminiCatalog ? geminiCatalog.models.slice() : A.gemini.MODELS.slice();
    if (!models.some(function (m) { return m.id === settings.model; })) {
      models.unshift({ id: settings.model, label: settings.model, note: 'Saved model' });
    }

    var modelOptions = models.map(function (model) {
      return '<option value="' + e(model.id) + '"' + (model.id === settings.model ? ' selected' : '') + '>' + e(model.label) + '</option>';
    }).join('');

    var geminiModelStatus = geminiCatalog
      ? 'Fetched ' + A.plural(geminiCatalog.models.length, 'model') + ' from ' + geminiCatalog.host + '. Flash is faster; Pro handles dense pages better.'
      : 'Fetch the list to see every model this key can use. Flash is faster and cheaper; Pro handles dense, faint pages better.';

    var openaiCatalog = A.gemini.catalog('openai');
    var openaiModels = openaiCatalog
      ? openaiCatalog.models
      : A.gemini.OPENAI_MODELS.map(function (id) { return { id: id, label: id }; });
    var openaiModelOptions = openaiModels.map(function (model) {
      return '<option value="' + e(model.id) + '">' + (model.label && model.label !== model.id ? e(model.label) : '') + '</option>';
    }).join('');

    var openaiModelStatus = openaiCatalog
      ? 'Fetched ' + A.plural(openaiCatalog.models.length, 'model') + ' from ' + openaiCatalog.host + '. The model field stays editable.'
      : 'Use a vision-capable model. Fetch the list from your endpoint, or type any model name.';

    var pagesCatalog = A.gemini.catalog('pages');
    var pagesCatalogHtml = pagesCatalog
      ? pagesCatalog.models.map(function (model) {
          return '<span class="model-chip mono" title="' + e(model.id) + '">' + e(model.id) + '</span>';
        }).join('')
      : '';
    var pagesModelStatus = pagesCatalog
      ? 'Fetched ' + A.plural(pagesCatalog.models.length, 'model') + ' from this site’s /api/models function. The server picks the model used for scans.'
      : 'Load the list to see which models this site’s Pages function can use.';

    var providerOption = function (id, iconName, title, note) {
      var on = provider.id === id;
      return '<button type="button" class="mode-option provider-option' + (on ? ' is-active' : '') + '" data-action="settings-provider" data-value="' + id + '" aria-pressed="' + (on ? 'true' : 'false') + '">' +
        '<span class="mode-icon">' + icon(iconName) + '</span>' +
        '<span><strong>' + title + '</strong><small>' + note + '</small></span>' +
      '</button>';
    };

    var themePref = A.theme ? A.theme.preference() : 'system';
    var themeOption = function (id, iconName, label) {
      var on = themePref === id;
      return '<button type="button"' + (on ? ' class="is-active"' : '') + ' data-action="set-theme" data-value="' + id + '" aria-pressed="' + (on ? 'true' : 'false') + '">' +
        icon(iconName) + '<span>' + label + '</span></button>';
    };

    var proxy = A.gemini.proxy();
    var providerReady = provider.id === 'pages' && !proxy.checked ? false : provider.ready;
    var providerStatus;
    if (provider.id === 'pages') {
      if (!proxy.checked) {
        providerStatus = 'Cloudflare proxy selected. Run Test connection after saving to check the server secrets.';
      } else if (proxy.ready) {
        providerStatus = 'Cloudflare proxy is ready with ' + proxy.model + ' via ' +
          (proxy.provider === 'openai' ? 'an OpenAI-compatible endpoint' : 'Gemini') + '.';
      } else {
        providerStatus = proxy.error || 'The Cloudflare proxy is not configured yet. Add the secrets in Cloudflare Pages.';
      }
    } else {
      providerStatus = provider.ready
        ? provider.short + ' is ready with ' + provider.model + '.'
        : provider.id === 'openai'
          ? 'Not configured yet. Add a base URL and model to enable custom scanning.'
          : 'Not configured yet. Add a Gemini API key to enable scanning.';
    }

    return pageHead(
      adminConsole ? 'Admin console' : 'Settings',
      adminConsole ? 'Accounts, scanning and backups' : 'Appearance and account',
      adminConsole
        ? 'Create accounts, assign libraries, and configure the scanner and Neon sync for this site.'
        : 'Choose a theme, review the account signed in on this browser, and read about Padanam.',
      adminConsole
        ? '<a class="btn btn-ghost" href="#/settings">' + icon('settings') + ' Settings</a>'
        : (isAdmin() ? '<a class="btn btn-ghost" href="#/admin">' + icon('lock') + ' Admin console</a>' : '') +
          '<a class="btn btn-ghost" href="#/profile">' + icon('user') + ' Profile</a>') +

      '<div class="settings-grid">' +
        '<div class="settings-main">' +
          (adminConsole ? adminUsersCard() : '') +
          '<section class="card settings-card" id="appearance">' +
            '<div class="settings-card-head">' +
              '<span class="settings-icon">' + icon('moon') + '</span>' +
              '<div><h2>Appearance</h2><p>Pick a theme for this browser. System follows your device setting automatically.</p></div>' +
            '</div>' +
            '<div class="segmented theme-switch" role="group" aria-label="Color theme">' +
              themeOption('light', 'sun', 'Light') +
              themeOption('dark', 'moon', 'Dark') +
              themeOption('system', 'monitor', 'System') +
            '</div>' +
          '</section>' +

          '<section class="card settings-card" id="account">' +
            '<div class="settings-card-head">' +
              '<span class="settings-icon settings-icon--success">' + icon('user') + '</span>' +
              '<div><h2>Account</h2><p>' + (A.auth && A.auth.isAccount()
                ? 'Signed in as <b>' + e(A.auth.displayName()) + '</b> · @' + e(who ? who.username : '') + '.'
                : 'This copy is running in local mode without an account server.') + '</p></div>' +
              '<span class="chip chip--indigo">' + e(who && who.role === 'admin' ? 'Admin' : 'Learner') + '</span>' +
            '</div>' +
            '<div class="about-list">' +
              '<div><span>Role</span><strong>' + (who && who.role === 'admin' ? 'Administrator' : 'User') + '</strong></div>' +
              '<div><span>Library access</span><strong>' + (who && who.role === 'admin' ? 'Create, scan and assign' : 'Assigned by the admin') + '</strong></div>' +
              '<div><span>Cloud sync</span><strong>' + (A.auth && A.auth.isAccount() ? 'Account · Neon' : 'Local only') + '</strong></div>' +
            '</div>' +
            '<div class="form-actions" style="margin-top:14px">' +
              (A.auth && A.auth.isAccount()
                ? '<button type="button" class="btn btn-ghost" data-action="sign-out">' + icon('logout') + ' Sign out</button>'
                : '') +
              (isAdmin() ? '<a class="btn btn-ghost" href="#/admin">' + icon('lock') + ' Admin console</a>' : '') +
            '</div>' +
          '</section>' +

          (adminConsole ? '<form class="card settings-card" data-form="settings" id="settingsForm">' +
            '<div class="settings-card-head">' +
              '<span class="settings-icon">' + icon('spark') + '</span>' +
              '<div><h2>AI scanning</h2><p>Currently using <b>' + e(provider.label) + '</b>. Choose Gemini, an OpenAI-compatible endpoint, or a Cloudflare Pages proxy that keeps the key on the server.</p></div>' +
              '<span class="chip ' + (providerReady ? 'chip--success' : 'chip--amber') + '">' + (providerReady ? 'Ready' : (provider.id === 'pages' && !proxy.checked ? 'Check' : 'Not set')) + '</span>' +
            '</div>' +
            '<div class="field">' +
              '<span class="field-label">Provider</span>' +
              '<div class="mode-grid provider-grid" role="group" aria-label="Scanning provider">' +
                providerOption('gemini', 'spark', 'Gemini', 'Google AI Studio key') +
                providerOption('openai', 'cloud', 'OpenAI-compatible', 'Custom base URL and model') +
                providerOption('pages', 'lock', 'Cloudflare Pages', 'Key stays in Pages secrets') +
              '</div>' +
              '<input type="hidden" name="provider" value="' + provider.id + '">' +
              '<span class="field-hint" id="settingsProviderStatus">' + e(providerStatus) + '</span>' +
            '</div>' +

            '<div class="provider-panel" data-provider-panel="gemini"' + (provider.id === 'gemini' ? '' : ' hidden') + '>' +
              '<div class="field">' +
                '<label class="field-label" for="apiKey">Gemini API key</label>' +
                '<div class="key-field">' +
                  '<input class="input" id="apiKey" name="apiKey" type="' + (ui.showKey ? 'text' : 'password') + '" autocomplete="off" spellcheck="false" placeholder="AIza… paste your Gemini key" value="' + e(settings.apiKey) + '">' +
                  '<button type="button" class="key-toggle" data-action="toggle-key" data-target="apiKey" aria-label="' + (ui.showKey ? 'Hide API key' : 'Show API key') + '">' + icon('eye') + '</button>' +
                '</div>' +
                '<span class="field-hint">Create a key in <a class="link" href="https://aistudio.google.com/app/apikey" target="_blank" rel="noopener">Google AI Studio</a>. Free-tier keys work for occasional scanning.</span>' +
              '</div>' +
              '<div class="field" style="margin-top:14px">' +
                '<div class="field-label-row">' +
                  '<label class="field-label" for="model">Gemini model</label>' +
                  '<button type="button" class="btn btn-ghost btn-sm model-fetch" data-action="fetch-models" data-provider="gemini">' + icon('refresh') + ' Fetch models</button>' +
                '</div>' +
                '<select class="select" id="model" name="model">' + modelOptions + '</select>' +
                '<span class="field-hint model-status" id="geminiModelStatus" aria-live="polite">' + e(geminiModelStatus) + '</span>' +
              '</div>' +
            '</div>' +

            '<div class="provider-panel" data-provider-panel="openai"' + (provider.id === 'openai' ? '' : ' hidden') + '>' +
              '<div class="field">' +
                '<label class="field-label" for="openaiUrl">Base URL</label>' +
                '<input class="input" id="openaiUrl" name="openaiUrl" type="text" inputmode="url" autocomplete="off" spellcheck="false" placeholder="https://api.openai.com/v1" value="' + e(settings.openaiUrl) + '">' +
                '<span class="field-hint">The base address of an OpenAI-compatible API. <span class="mono">/chat/completions</span> is added automatically. The server must allow browser (CORS) requests.</span>' +
              '</div>' +
              '<div class="form-grid" style="margin-top:14px">' +
                '<div class="field">' +
                  '<label class="field-label" for="openaiKey">API key <span class="faint">(optional)</span></label>' +
                  '<div class="key-field">' +
                    '<input class="input" id="openaiKey" name="openaiKey" type="' + (ui.showOpenaiKey ? 'text' : 'password') + '" autocomplete="off" spellcheck="false" placeholder="sk-… or leave blank" value="' + e(settings.openaiKey) + '">' +
                    '<button type="button" class="key-toggle" data-action="toggle-key" data-target="openaiKey" aria-label="' + (ui.showOpenaiKey ? 'Hide API key' : 'Show API key') + '">' + icon('eye') + '</button>' +
                  '</div>' +
                  '<span class="field-hint">Sent as a Bearer token. Leave blank for local endpoints that do not need one.</span>' +
                '</div>' +
                '<div class="field">' +
                  '<div class="field-label-row">' +
                    '<label class="field-label" for="openaiModel">Model</label>' +
                    '<button type="button" class="btn btn-ghost btn-sm model-fetch" data-action="fetch-models" data-provider="openai">' + icon('refresh') + ' Fetch models</button>' +
                  '</div>' +
                  '<input class="input" id="openaiModel" name="openaiModel" list="openaiModelList" autocomplete="off" spellcheck="false" placeholder="gpt-4o-mini" value="' + e(settings.openaiModel) + '">' +
                  '<datalist id="openaiModelList">' + openaiModelOptions + '</datalist>' +
                  '<span class="field-hint model-status" id="openaiModelStatus" aria-live="polite">' + e(openaiModelStatus) + '</span>' +
                '</div>' +
              '</div>' +
            '</div>' +

            '<div class="provider-panel" data-provider-panel="pages"' + (provider.id === 'pages' ? '' : ' hidden') + '>' +
              '<div class="settings-note" style="margin-top:0">' + icon('lock') +
                '<span>The browser never sees an API key in this mode. A Pages Function at <span class="mono">/api/scan</span> reads <span class="mono">GEMINI_API_KEY</span> or <span class="mono">OPENAI_API_KEY</span> from your Cloudflare project secrets and forwards the pages.</span>' +
              '</div>' +
              '<div class="about-list" style="margin-top:12px">' +
                '<div><span>Server provider</span><strong data-proxy-backend>' + e(proxy.provider === 'openai' ? 'OpenAI-compatible' : proxy.provider === 'gemini' ? 'Gemini' : 'Not checked yet') + '</strong></div>' +
                '<div><span>Model</span><strong class="mono" data-proxy-model>' + e(proxy.model || 'Set in Pages') + '</strong></div>' +
                '<div><span>Endpoint</span><strong class="mono" data-proxy-host>' + e(proxy.host || '/api/scan') + '</strong></div>' +
                '<div><span>Keys in this browser</span><strong>None</strong></div>' +
              '</div>' +
              '<div class="field" style="margin-top:14px">' +
                '<div class="field-label-row">' +
                  '<span class="field-label">Server models</span>' +
                  '<button type="button" class="btn btn-ghost btn-sm model-fetch" data-action="fetch-models" data-provider="pages">' + icon('refresh') + ' Fetch models</button>' +
                '</div>' +
                '<div class="model-catalog" id="pagesModelCatalog">' + pagesCatalogHtml + '</div>' +
                '<span class="field-hint model-status" id="pagesModelStatus" aria-live="polite">' + e(pagesModelStatus) + '</span>' +
              '</div>' +
              '<p class="field-hint" style="margin-top:10px">Set <span class="mono">SCAN_PROVIDER</span> plus the matching key in <b>Cloudflare Pages → Settings → Variables and Secrets</b>, then redeploy. <a class="link" href="https://developers.cloudflare.com/pages/functions/bindings/#secrets" target="_blank" rel="noopener">Cloudflare guide ↗</a></p>' +
            '</div>' +

            '<div class="form-actions" style="margin-top:18px">' +
              '<button type="submit" class="btn btn-primary">' + icon('check') + ' Save settings</button>' +
              '<button type="button" class="btn btn-ghost" data-action="test-key">' + icon('spark') + ' Test connection</button>' +
              '<span class="muted" style="font-size:10.5px">Testing sends one tiny text request.</span>' +
            '</div>' +
            '<div class="test-result" id="keyTestResult"></div>' +
            '<div class="settings-note">' + icon('lock') +
              '<span>Direct Gemini or custom keys are stored in this browser and can be read by anything running on this origin. The Cloudflare Pages proxy keeps its key in encrypted Pages secrets instead. No key is ever included in exports.</span>' +
            '</div>' +
          '</form>' : '') +

          (adminConsole ? '<section class="card settings-card" id="cloudBackup">' +
            '<div class="settings-card-head">' +
              '<span class="settings-icon">' + icon('cloud') + '</span>' +
              '<div><h2>Cloud backups</h2><p>' + (A.auth && A.auth.isAccount()
                ? 'Your account workspace syncs sets, questions, attempts and profile with Neon.'
                : 'Connect this local copy to a Neon workspace with the legacy sync code.') + '</p></div>' +
              '<span class="chip ' + (syncConnected ? 'chip--success' : 'chip--amber') + '">' + e(syncChip) + '</span>' +
            '</div>' +
            (A.auth && A.auth.isAccount()
              ? '<div class="about-list">' +
                  '<div><span>Signed in</span><strong>' + e(A.auth.displayName()) + '</strong></div>' +
                  '<div><span>Workspace</span><strong class="mono">' + (who && who.role === 'admin' ? 'admin:' + e(who.username) : 'private account') + '</strong></div>' +
                  '<div><span>Auto sync</span><strong>On</strong></div>' +
                '</div>' +
                '<div class="form-actions" style="margin-top:14px">' +
                  '<button type="button" class="btn btn-primary" data-action="sync-now">' + icon('refresh') + ' Sync now</button>' +
                '</div>' +
                '<div class="test-result" id="syncTestResult"></div>'
              : '<form data-form="sync" id="syncForm">' +
                  '<div class="field">' +
                    '<label class="field-label" for="syncToken">Sync code</label>' +
                    '<div class="key-field">' +
                      '<input class="input" id="syncToken" name="token" type="' + (ui.showSyncToken ? 'text' : 'password') + '" autocomplete="off" spellcheck="false" placeholder="The SYNC_TOKEN you set in Cloudflare Pages" value="' + e(syncConfig.token) + '">' +
                      '<button type="button" class="key-toggle" data-action="toggle-sync-token" aria-label="' + (ui.showSyncToken ? 'Hide sync code' : 'Show sync code') + '">' + icon('eye') + '</button>' +
                    '</div>' +
                    '<span class="field-hint">At least 16 characters, exactly matching the <span class="mono">SYNC_TOKEN</span> secret.</span>' +
                  '</div>' +
                  '<label class="check-row sync-auto" for="syncAuto">' +
                    '<input type="checkbox" id="syncAuto" name="auto"' + (syncConfig.auto ? ' checked' : '') + '>' +
                    '<span><strong>Automatic sync</strong><small>Push changes a few seconds after you edit, and pull once when Padanam opens.</small></span>' +
                  '</label>' +
                  '<div class="form-actions" style="margin-top:14px">' +
                    '<button type="submit" class="btn btn-primary">' + icon('check') + ' Save sync code</button>' +
                    '<button type="button" class="btn btn-ghost" data-action="sync-now">' + icon('refresh') + ' Sync now</button>' +
                    (syncConfig.token ? '<button type="button" class="btn btn-ghost btn-sm" data-action="sync-forget">' + icon('close') + ' Forget code</button>' : '') +
                  '</div>' +
                  '<div class="test-result" id="syncTestResult"></div>' +
                '</form>') +
            '<div class="settings-note">' + icon('lock') +
              '<span>' + e(syncStatus) + ' Only sets, questions, attempts and profile are stored in Neon. Photos, scan images and AI keys are never uploaded.</span>' +
            '</div>' +
          '</section>' : '') +

          (adminConsole ? '<section class="card settings-card">' +
            '<div class="settings-card-head">' +
              '<span class="settings-icon settings-icon--success">' + icon('database') + '</span>' +
              '<div><h2>Data &amp; backups</h2><p>Export the admin library and import an earlier Padanam backup. Neon never receives photos or API keys.</p></div>' +
            '</div>' +
            '<div class="about-list">' +
              '<div><span>Sets in library</span><strong>' + library.sets + '</strong></div>' +
              '<div><span>Questions</span><strong>' + library.questions + ' (' + library.bilingual + ' bilingual)</strong></div>' +
              '<div><span>Attempts saved</span><strong>' + A.state.attempts.length + '</strong></div>' +
              '<div><span>Local storage used</span><strong class="mono">' + A.fmtBytes(bytes) + '</strong></div>' +
            '</div>' +
            '<div class="danger-actions">' +
              '<button type="button" class="btn btn-ghost btn-sm" data-action="export-data">' + icon('download') + ' Export a backup file</button>' +
              '<button type="button" class="btn btn-ghost btn-sm" data-action="import-data">' + icon('import') + ' Import from a backup</button>' +
              '<button type="button" class="btn btn-subtle btn-sm" data-action="restore-demos">' + icon('refresh') + ' Add the demo sets</button>' +
            '</div>' +
            '<div class="settings-note">' + icon('info') +
              '<span>Press <b>Sync now</b> above after adding or importing sets so they reach Neon. Assigned sets are pulled by each user on their next sync.</span>' +
            '</div>' +
          '</section>' : '') +

          '<section class="card settings-card">' +
            '<div class="settings-card-head">' +
              '<span class="settings-icon settings-icon--amber">' + icon('file') + '</span>' +
              '<div><h2>About Padanam</h2><p>A local-first study desk for Kerala PSC aspirants.</p></div>' +
            '</div>' +
            '<div class="about-list">' +
              '<div><span>Version</span><strong class="mono">1.0.0 · web</strong></div>' +
              '<div><span>Storage</span><strong>localStorage</strong></div>' +
              '<div><span>Cloud sync</span><strong>' + (A.auth && A.auth.isAccount() ? 'Account · Neon' : 'Optional · Neon') + '</strong></div>' +
              '<div><span>Questions</span><strong>English + Malayalam</strong></div>' +
              '<div><span>Scanning</span><strong>Gemini, custom API or Cloudflare proxy</strong></div>' +
              '<div><span>Accounts</span><strong>' + (A.auth && A.auth.isAccount() ? 'Admin + users' : 'Local mode') + '</strong></div>' +
            '</div>' +
          '</section>' +
        '</div>' +

        '<aside class="settings-aside">' +
          (adminConsole
            ? '<section class="card note-card">' +
                '<h3>' + icon('scan') + ' Connecting a scanner</h3>' +
                '<p>For Gemini, create a key in Google AI Studio. For a custom endpoint, paste its base URL, optional key and model. For a deployed site, choose Cloudflare Pages so the key stays in your Pages secrets.</p>' +
                '<a class="link" href="https://aistudio.google.com/app/apikey" target="_blank" rel="noopener">Open Google AI Studio ↗</a>' +
              '</section>' +
              '<section class="card note-card">' +
                '<h3>' + icon('user') + ' Assigning libraries</h3>' +
                '<p>Create an account, then tick the sets that account should receive. Users pull assigned sets on their next sync; their attempts and profile stay private to them.</p>' +
              '</section>'
            : '<section class="card note-card">' +
                '<h3>' + icon('user') + ' Your account</h3>' +
                '<p>' + (A.auth && A.auth.isAccount()
                  ? 'You are signed in as <b>' + e(A.auth.displayName()) + '</b>. Ask the administrator for another account or for a set to be assigned.'
                  : 'Local mode keeps everything in this browser. The deployed site uses accounts and Neon sync.') + '</p>' +
              '</section>') +
          '<section class="card note-card">' +
            '<h3>' + icon('lock') + ' What leaves this browser</h3>' +
            '<p>Scan photos go only to the provider you configured, and only when you press extract. With accounts on, sets, questions, attempts and profile sync to your Neon workspace. Photos and AI keys are never uploaded.</p>' +
          '</section>' +
        '</aside>' +
      '</div>';
  }

  views.settings = function () {
    return settingsPage(false);
  };

  views.admin = function () {
    return settingsPage(true);
  };

  /* ── Not found ───────────────────────────────────────── */
  views.notFound = function (message) {
    return '<div class="card">' +
      emptyState('alert', 'Page not found', e(message || 'That page does not exist, or the item was removed from this browser.'),
        '<a class="btn btn-primary" href="#/home">' + icon('home') + ' Go home</a><a class="btn btn-ghost" href="#/library">Open library</a>') +
    '</div>';
  };
})();
