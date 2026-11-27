/* =========================================================
   Padanam web — swipe decks
   One question and one answer at a time, built from the
   existing question library. Deck sessions stay local; card
   progress syncs with the account like attempts do.
   ========================================================= */
(function () {
  'use strict';

  var A = window.Padanam;
  if (!A) return;

  var revealedKey = '';
  var drag = null;
  var SWIPE_THRESHOLD = 82;

  /* Scanned cards live in an optional second Neon database. They are cached in
     localStorage so the deck library keeps working when that database is
     offline; progress for them syncs through the normal account workspace. */
  var REMOTE_ENDPOINT = './api/decks';
  var REMOTE_CACHE_KEY = 'padanam.deck.remote.v1';
  var REMOTE_TTL = 60 * 1000;
  var remoteCards = [];
  var remoteLoadedAt = 0;
  var remotePromise = null;

  function keyFor(setId, questionId) {
    return 'question:' + setId + ':' + questionId;
  }

  function validKey(key) {
    return typeof key === 'string' && key.length > 2 && key.length <= 320 &&
      /^[a-z][a-z0-9_-]*:.+$/.test(key);
  }

  function cleanRemoteText(value, max) {
    return String(value === null || value === undefined ? '' : value)
      .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, max || 200);
  }

  function cleanRemoteId(value) {
    var text = String(value === null || value === undefined ? '' : value)
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9_-]/g, '');
    return /^[a-z0-9][a-z0-9_-]{0,60}$/.test(text) ? text : '';
  }

  function normalizeRemoteCard(item) {
    if (!item || typeof item !== 'object') return null;
    var deckId = cleanRemoteId(item.deckId);
    var cardId = cleanRemoteId(item.cardId);
    var key = String(item.key || ('scan:' + deckId + ':' + cardId)).trim();
    if (!deckId || !cardId || !validKey(key)) return null;
    if (key !== 'scan:' + deckId + ':' + cardId) return null;

    var stem = cleanRemoteText(item.stem, 4000);
    var stemMl = cleanRemoteText(item.stemMl, 4000);
    var answer = cleanRemoteText(item.answer, 2000);
    var answerMl = cleanRemoteText(item.answerMl, 2000);
    if ((!stem && !stemMl) || (!answer && !answerMl)) return null;

    return {
      key: key,
      deckId: deckId,
      cardId: cardId,
      deckTitle: cleanRemoteText(item.deckTitle, 160),
      mainTopic: cleanRemoteText(item.mainTopic, 80),
      subTopic: cleanRemoteText(item.subTopic, 120),
      language: item.language === 'ml' ? 'ml' : 'en',
      stem: stem,
      stemMl: stemMl,
      answer: answer,
      answerMl: answerMl,
      explanation: cleanRemoteText(item.explanation, 2000),
      explanationMl: cleanRemoteText(item.explanationMl, 2000),
      updatedAt: String(item.updatedAt || '')
    };
  }

  function projectRemoteCard(card, lang) {
    var useMl = lang === 'ml' && !!card.stemMl && !!card.answerMl;
    var title = card.deckTitle || 'Scanned deck';
    return {
      key: card.key,
      setId: card.deckId,
      questionId: card.cardId,
      mainTopic: A.normalizeMainTopic(card.mainTopic, title, 'Scanned deck'),
      subTopic: card.subTopic || title,
      setTitle: title,
      subject: 'Scanned deck',
      source: 'scan',
      stem: useMl ? card.stemMl : (card.stem || card.stemMl),
      answer: useMl ? card.answerMl : (card.answer || card.answerMl),
      explanation: useMl
        ? (card.explanationMl || card.explanation)
        : (card.explanation || card.explanationMl),
      lang: useMl ? 'ml' : 'en'
    };
  }

  function readRemoteCache() {
    try {
      var raw = window.localStorage.getItem(REMOTE_CACHE_KEY);
      var list = raw ? JSON.parse(raw) : [];
      remoteCards = Array.isArray(list) ? list.map(normalizeRemoteCard).filter(Boolean) : [];
    } catch (err) {
      remoteCards = [];
    }
  }

  function writeRemoteCache() {
    try {
      window.localStorage.setItem(REMOTE_CACHE_KEY, JSON.stringify(remoteCards));
    } catch (err) {
      /* Private browsing can reject writes; the in-memory copy still works. */
    }
  }

  readRemoteCache();

  function deckLanguage() {
    return A.state.profile.language === 'ml' ? 'ml' : 'en';
  }

  function allCards() {
    var lang = deckLanguage();
    var cards = [];
    var seen = {};

    function add(card) {
      if (!card || seen[card.key]) return;
      seen[card.key] = true;
      cards.push(card);
    }

    (A.state.sets || []).forEach(function (set) {
      var mainTopic = A.mainTopicFor(set);
      var subTopic = A.subTopicFor(set);
      (set.questions || []).forEach(function (question) {
        var view = A.questionFor(question, lang);
        add({
          key: keyFor(set.id, question.id),
          setId: set.id,
          questionId: question.id,
          mainTopic: mainTopic,
          subTopic: subTopic,
          setTitle: set.title,
          subject: set.subject,
          source: set.source,
          stem: view.stem,
          answer: view.options[question.answer] || '',
          explanation: view.explanation || '',
          lang: view.lang
        });
      });
    });

    remoteCards.forEach(function (card) {
      add(projectRemoteCard(card, lang));
    });
    return cards;
  }

  function byKey() {
    var map = {};
    allCards().forEach(function (card) { map[card.key] = card; });
    return map;
  }

  function isKnown(entry) {
    return !!(entry && entry.status === 'known');
  }

  function learnedCount(cards) {
    return cards.filter(function (card) {
      return isKnown(A.state.deckProgress[card.key]);
    }).length;
  }

  function percentOf(cards) {
    if (!cards.length) return 0;
    return Math.round(learnedCount(cards) / cards.length * 100);
  }

  function shuffle(list) {
    var out = list.slice();
    for (var i = out.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var tmp = out[i];
      out[i] = out[j];
      out[j] = tmp;
    }
    return out;
  }

  function canonicalTopic(value) {
    var clean = String(value || '').trim().toLowerCase();
    if (!clean) return '';
    var match = topicGroups().filter(function (topic) {
      return topic.name.toLowerCase() === clean;
    })[0];
    return match ? match.name : String(value || '').trim();
  }

  function canonicalSubTopic(mainTopic, value) {
    var clean = String(value || '').trim().toLowerCase();
    if (!clean) return '';
    var topic = topicGroups().filter(function (item) { return item.name === mainTopic; })[0];
    if (!topic) return String(value || '').trim();
    var match = topic.subtopics.filter(function (sub) {
      return sub.name.toLowerCase() === clean;
    })[0];
    return match ? match.name : String(value || '').trim();
  }

  function topicGroups() {
    var groups = [];
    var map = {};
    allCards().forEach(function (card) {
      var topic = map[card.mainTopic];
      if (!topic) {
        topic = map[card.mainTopic] = {
          name: card.mainTopic,
          cards: [],
          subtopics: [],
          subMap: {}
        };
        groups.push(topic);
      }
      topic.cards.push(card);
      var sub = topic.subMap[card.subTopic];
      if (!sub) {
        sub = topic.subMap[card.subTopic] = {
          name: card.subTopic,
          cards: []
        };
        topic.subtopics.push(sub);
      }
      sub.cards.push(card);
    });

    groups.forEach(function (topic) {
      topic.learned = learnedCount(topic.cards);
      topic.pct = percentOf(topic.cards);
      topic.href = '#/decks/topic/' + encodeURIComponent(topic.name);
      topic.subtopics.sort(function (a, b) { return a.name.localeCompare(b.name); });
      topic.subtopics.forEach(function (sub) {
        sub.learned = learnedCount(sub.cards);
        sub.pct = percentOf(sub.cards);
        sub.href = hrefFor(topic.name, sub.name);
      });
      topic.firstSub = topic.subtopics[0] || null;
      delete topic.subMap;
    });

    groups.sort(function (a, b) {
      return A.topicRank(a.name) - A.topicRank(b.name) || a.name.localeCompare(b.name);
    });
    return groups;
  }

  function overview() {
    var topics = topicGroups();
    var cards = [];
    topics.forEach(function (topic) { cards = cards.concat(topic.cards); });
    var learned = learnedCount(cards);
    return {
      cards: cards.length,
      learned: learned,
      pct: cards.length ? Math.round(learned / cards.length * 100) : 0,
      topics: topics
    };
  }

  function cardsFor(mainTopic, subTopic) {
    var topic = canonicalTopic(mainTopic);
    var sub = subTopic ? canonicalSubTopic(topic, subTopic) : '';
    return allCards().filter(function (card) {
      return card.mainTopic === topic && (!sub || card.subTopic === sub);
    });
  }

  function hrefFor(mainTopic, subTopic) {
    return '#/decks/study/' + encodeURIComponent(mainTopic) +
      (subTopic ? '/' + encodeURIComponent(subTopic) : '');
  }

  function topicHref(mainTopic) {
    return '#/decks/topic/' + encodeURIComponent(mainTopic);
  }

  function sessionFor(mainTopic, subTopic) {
    var session = A.state.deckSession;
    if (!session) return null;
    var topic = canonicalTopic(mainTopic);
    var sub = subTopic ? canonicalSubTopic(topic, subTopic) : '';
    return session.mainTopic === topic && (session.subTopic || '') === sub ? session : null;
  }

  function start(mainTopic, subTopic, options) {
    options = options || {};
    var topic = canonicalTopic(mainTopic);
    var sub = subTopic ? canonicalSubTopic(topic, subTopic) : '';
    var cards = cardsFor(topic, sub);
    if (!cards.length) return null;

    var keys = cards.map(function (card) { return card.key; });
    if (Array.isArray(options.keys) && options.keys.length) {
      var wanted = options.keys.filter(function (key) { return keys.indexOf(key) >= 0; });
      if (wanted.length) keys = wanted;
    }
    if (options.shuffle) keys = shuffle(keys);

    var existing = sessionFor(topic, sub);
    var round = parseInt(options.round, 10);
    if (isNaN(round) || round < 1) round = existing ? existing.round + 1 : 1;

    var session = {
      id: A.uid('deck'),
      mainTopic: topic,
      subTopic: sub,
      scope: sub ? 'subtopic' : 'topic',
      keys: keys,
      actions: [],
      round: round,
      startedAt: new Date().toISOString()
    };

    A.state.deckSession = session;
    revealedKey = '';
    A.save();
    return session;
  }

  function ensureSession(mainTopic, subTopic) {
    var topic = canonicalTopic(mainTopic);
    var sub = subTopic ? canonicalSubTopic(topic, subTopic) : '';
    if (!topic || !cardsFor(topic, sub).length) return null;
    var existing = sessionFor(topic, sub);
    if (existing && sessionSummary(existing).total) return existing;
    return start(topic, sub);
  }

  function sessionSummary(session) {
    if (!session) return null;
    var map = byKey();
    var live = [];
    var seen = {};
    session.keys.forEach(function (key) {
      if (!map[key] || seen[key]) return;
      seen[key] = true;
      live.push(map[key]);
    });

    var last = {};
    session.actions.forEach(function (action) {
      if (seen[action.key]) last[action.key] = action.result;
    });

    var pending = live.filter(function (card) { return !last[card.key]; });
    var gotit = 0;
    var again = 0;
    var missed = [];
    live.forEach(function (card) {
      if (last[card.key] === 'gotit') gotit++;
      else if (last[card.key] === 'again') {
        again++;
        missed.push(card.key);
      }
    });

    var total = live.length;
    var sorted = total - pending.length;
    return {
      session: session,
      cards: live,
      total: total,
      sorted: sorted,
      pending: pending,
      current: pending[0] || null,
      position: pending.length ? Math.min(total, sorted + 1) : total,
      gotit: gotit,
      again: again,
      missed: missed,
      recall: total ? Math.round(gotit / total * 100) : 0,
      complete: total > 0 && pending.length === 0,
      last: last
    };
  }

  function currentSummary() {
    return sessionSummary(A.state.deckSession);
  }

  function saveAndSchedule() {
    A.save();
    if (A.sync && typeof A.sync.schedule === 'function') A.sync.schedule();
  }

  function sort(result) {
    var session = A.state.deckSession;
    var summary = sessionSummary(session);
    if (!summary || summary.complete || !summary.current) return null;
    if (result !== 'gotit' && result !== 'again') return null;

    var card = summary.current;
    var at = new Date().toISOString();
    var before = A.state.deckProgress[card.key] ? A.clone(A.state.deckProgress[card.key]) : null;
    A.state.deckProgress[card.key] = {
      status: result === 'gotit' ? 'known' : 'seen',
      lastResult: result,
      seenAt: at,
      updatedAt: at
    };
    session.actions.push({ key: card.key, result: result, at: at, before: before });
    revealedKey = '';
    saveAndSchedule();
    A.live(result === 'gotit' ? 'Got it. Next card.' : 'Marked again. It will be in the missed pile.');
    return card;
  }

  function undo() {
    var session = A.state.deckSession;
    if (!session || !session.actions.length) return false;
    var action = session.actions.pop();
    var now = new Date().toISOString();
    if (action.before) {
      A.state.deckProgress[action.key] = {
        status: action.before.status,
        lastResult: action.before.lastResult,
        seenAt: action.before.status === 'unseen' ? '' : (action.before.seenAt || now),
        updatedAt: now
      };
    } else {
      A.state.deckProgress[action.key] = {
        status: 'unseen',
        lastResult: '',
        seenAt: '',
        updatedAt: now
      };
    }
    revealedKey = '';
    saveAndSchedule();
    A.live('Last swipe undone.');
    return true;
  }

  function reveal() {
    var summary = currentSummary();
    if (!summary || !summary.current) return false;
    revealedKey = revealedKey === summary.current.key ? '' : summary.current.key;
    return revealedKey !== '';
  }

  function isRevealed(card) {
    return !!(card && revealedKey === card.key);
  }

  function continueInfo() {
    var summary = currentSummary();
    if (!summary || summary.complete || !summary.current) return null;
    return {
      href: hrefFor(summary.session.mainTopic, summary.session.subTopic),
      title: summary.session.subTopic || summary.session.mainTopic,
      mainTopic: summary.session.mainTopic,
      remaining: summary.pending.length,
      total: summary.total,
      sorted: summary.sorted,
      round: summary.session.round
    };
  }

  function home() {
    var data = overview();
    return {
      cards: data.cards,
      learned: data.learned,
      pct: data.pct,
      topics: data.topics,
      topicCount: data.topics.length,
      continueInfo: continueInfo()
    };
  }

  function guideBody() {
    return '<div class="deck-guide">' +
      '<div class="deck-guide-row">' +
        '<span class="deck-guide-icon deck-guide-icon--again">' + A.icon('arrow-left') + '</span>' +
        '<span><strong>Swipe left — Again</strong><small>See it once more in the missed pile.</small></span>' +
      '</div>' +
      '<div class="deck-guide-row">' +
        '<span class="deck-guide-icon">' + A.icon('eye') + '</span>' +
        '<span><strong>Tap the card — Reveal</strong><small>Check the answer before you sort it.</small></span>' +
      '</div>' +
      '<div class="deck-guide-row">' +
        '<span class="deck-guide-icon deck-guide-icon--gotit">' + A.icon('check') + '</span>' +
        '<span><strong>Swipe right — Got it</strong><small>Lock the card into your known pile.</small></span>' +
      '</div>' +
      '<p class="deck-guide-note">' + A.icon('undo') + ' Changed your mind? Undo any swipe.</p>' +
    '</div>';
  }

  function showGuide(force) {
    if (!force && A.state.meta.deckGuideSeen) return;
    if (document.body.classList.contains('modal-open')) return;
    if (!force && !document.querySelector('[data-deck-card]')) return;
    A.state.meta.deckGuideSeen = true;
    A.save();
    A.modal({
      title: 'Two swipes, one tap',
      subtitle: 'That is the whole deck.',
      body: guideBody(),
      footer: '<button type="button" class="btn btn-primary btn-block" data-modal-close data-autofocus>Start swiping</button>'
    });
  }

  function authReady() {
    return !!(A.auth && A.auth.isAccount && A.auth.isAccount() && A.auth.token && A.auth.token());
  }

  function loadRemote(force) {
    if (!authReady()) return Promise.resolve(remoteCards);
    if (!force && remoteLoadedAt && Date.now() - remoteLoadedAt < REMOTE_TTL) {
      return Promise.resolve(remoteCards);
    }
    if (remotePromise) return remotePromise;

    var token = A.auth.token();
    remotePromise = fetch(REMOTE_ENDPOINT, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        Authorization: 'Bearer ' + token
      },
      cache: 'no-store'
    }).then(function (response) {
      return response.text().then(function (text) {
        var data = null;
        try { data = text ? JSON.parse(text) : null; } catch (err) { data = null; }
        if (!data || typeof data !== 'object') {
          throw new Error('The deck server returned an unexpected response.');
        }
        if (!response.ok || data.error) {
          throw new Error(data.error || 'Could not load scanned decks.');
        }
        return data;
      });
    }).then(function (data) {
      remoteCards = Array.isArray(data.cards)
        ? data.cards.map(normalizeRemoteCard).filter(Boolean)
        : [];
      remoteLoadedAt = Date.now();
      writeRemoteCache();
      if (A.refresh) A.refresh();
      return remoteCards;
    }).catch(function (err) {
      if (window.console && console.warn) {
        console.warn('Padanam scanned decks are unavailable', err);
      }
      return remoteCards;
    }).finally(function () {
      remotePromise = null;
    });
    return remotePromise;
  }

  function saveScan(draft) {
    if (!authReady()) {
      return Promise.reject(new Error('Sign in to save scanned decks.'));
    }
    var questions;
    if (draft && Array.isArray(draft.cards)) {
      questions = draft.cards.map(function (card) {
        var en = card.en || {};
        var ml = card.ml || {};
        return {
          stem: en.question || ml.question || '',
          answer: en.answer || ml.answer || '',
          explanation: en.explanation || '',
          stemMl: ml.question || '',
          answerMl: ml.answer || '',
          explanationMl: ml.explanation || ''
        };
      }).filter(function (card) {
        return !!card.stem && !!card.answer;
      });
    } else {
      questions = (draft && draft.questions ? draft.questions : []).map(function (question) {
        var en = A.questionFor(question, 'en');
        var ml = A.questionFor(question, 'ml');
        var answerEn = en.options[question.answer] || '';
        var answerMl = ml.options[question.answer] || '';
        return {
          stem: en.stem || ml.stem,
          answer: answerEn || answerMl,
          explanation: en.explanation || '',
          stemMl: question.ml ? ml.stem : '',
          answerMl: question.ml ? answerMl : '',
          explanationMl: question.ml ? (ml.explanation || '') : ''
        };
      }).filter(function (question) {
        return !!question.stem && !!question.answer;
      });
    }

    if (!questions.length) {
      return Promise.reject(new Error('There are no complete cards to save.'));
    }

    return A.auth.api(REMOTE_ENDPOINT, {
      action: 'save',
      title: (draft && draft.title) || 'Scanned deck',
      mainTopic: (draft && draft.mainTopic) || '',
      subTopic: (draft && draft.subTopic) || '',
      language: 'both',
      questions: questions
    }).then(function (data) {
      return loadRemote(true).then(function () { return data; });
    });
  }

  function resetDrag() {
    if (!drag) return;
    var card = drag.card;
    card.classList.remove('is-dragging', 'is-swiping', 'is-gotit', 'is-again');
    card.style.removeProperty('--drag-x');
    card.style.removeProperty('--drag-rot');
    drag = null;
  }

  function onPointerDown(event) {
    if (event.defaultPrevented) return;
    if (event.button !== undefined && event.button !== 0) return;
    if (document.body.classList.contains('modal-open')) return;
    var target = event.target;
    if (!target || !target.closest) return;
    if (target.closest('button, a, input, textarea, select')) return;
    var card = target.closest('[data-deck-card]');
    if (!card) return;

    drag = {
      card: card,
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      dx: 0,
      dy: 0,
      moved: false
    };
    card.classList.add('is-dragging');
    try { card.setPointerCapture(event.pointerId); } catch (err) { /* pointer capture is a bonus */ }
  }

  function onPointerMove(event) {
    if (!drag || event.pointerId !== drag.pointerId) return;
    var dx = event.clientX - drag.startX;
    var dy = event.clientY - drag.startY;
    drag.dx = dx;
    drag.dy = dy;
    if (Math.abs(dx) > 7 || Math.abs(dy) > 7) drag.moved = true;
    if (Math.abs(dx) <= Math.abs(dy)) return;

    var card = drag.card;
    card.classList.add('is-swiping');
    card.style.setProperty('--drag-x', dx + 'px');
    card.style.setProperty('--drag-rot', A.clamp(dx / 26, -11, 11) + 'deg');
    card.classList.toggle('is-gotit', dx > 38);
    card.classList.toggle('is-again', dx < -38);
  }

  function onPointerUp(event) {
    if (!drag || event.pointerId !== drag.pointerId) return;
    var dx = drag.dx;
    var dy = drag.dy;
    var moved = drag.moved;
    resetDrag();

    if (Math.abs(dx) >= SWIPE_THRESHOLD && Math.abs(dx) > Math.abs(dy)) {
      sort(dx > 0 ? 'gotit' : 'again');
      if (A.refresh) A.refresh();
      return;
    }
    if (!moved) {
      reveal();
      if (A.refresh) A.refresh();
    }
  }

  function onPointerCancel(event) {
    if (!drag || event.pointerId !== drag.pointerId) return;
    resetDrag();
  }

  A.deck = {
    keyFor: keyFor,
    validKey: validKey,
    cards: allCards,
    cardsFor: cardsFor,
    overview: overview,
    topic: function (name) {
      var topic = canonicalTopic(name);
      return topicGroups().filter(function (item) { return item.name === topic; })[0] || null;
    },
    hrefFor: hrefFor,
    topicHref: topicHref,
    start: start,
    ensureSession: ensureSession,
    sessionFor: sessionFor,
    current: function () { return A.state.deckSession; },
    summary: currentSummary,
    sessionSummary: sessionSummary,
    sort: sort,
    undo: undo,
    reveal: reveal,
    isRevealed: isRevealed,
    continueInfo: continueInfo,
    home: home,
    loadRemote: loadRemote,
    saveScan: saveScan,
    remoteCount: function () { return remoteCards.length; },
    showGuide: function () { showGuide(true); },
    mount: function () {
      if (document.querySelector('[data-deck-card]')) showGuide(false);
    }
  };

  if (typeof document !== 'undefined') {
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('pointermove', onPointerMove);
    document.addEventListener('pointerup', onPointerUp);
    document.addEventListener('pointercancel', onPointerCancel);
  }
})();
