/* =========================================================
   Padanam — static site behaviour
   1. header / mobile navigation
   2. scroll reveals + section spy
   3. three-question bilingual demo
   4. best-effort release metadata (no dependencies)
   ========================================================= */
(function () {
  "use strict";

  var $ = function (selector, root) { return (root || document).querySelector(selector); };
  var $$ = function (selector, root) { return Array.prototype.slice.call((root || document).querySelectorAll(selector)); };

  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ── 1. Header ──────────────────────────────────────── */
  var header = $("[data-header]");
  var nav = $("#siteNav");
  var navToggle = $("[data-nav-toggle]");
  var navToggleLabel = navToggle ? $(".sr-only", navToggle) : null;

  function setScrolled() {
    if (!header) return;
    header.classList.toggle("is-scrolled", window.scrollY > 12);
  }
  setScrolled();
  window.addEventListener("scroll", setScrolled, { passive: true });

  function closeNav() {
    if (!nav || !navToggle) return;
    nav.classList.remove("is-open");
    navToggle.setAttribute("aria-expanded", "false");
    if (navToggleLabel) navToggleLabel.textContent = "Open menu";
  }

  if (nav && navToggle) {
    navToggle.addEventListener("click", function () {
      var open = nav.classList.toggle("is-open");
      navToggle.setAttribute("aria-expanded", String(open));
      if (navToggleLabel) navToggleLabel.textContent = open ? "Close menu" : "Open menu";
    });

    document.addEventListener("click", function (event) {
      if (!nav.classList.contains("is-open")) return;
      if (!nav.contains(event.target) && !navToggle.contains(event.target)) closeNav();
    });

    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && nav.classList.contains("is-open")) {
        closeNav();
        navToggle.focus();
      }
    });

    $$("a", nav).forEach(function (link) {
      link.addEventListener("click", closeNav);
    });

    var wide = window.matchMedia("(min-width: 961px)");
    var onWide = function (event) { if (event.matches) closeNav(); };
    if (wide.addEventListener) wide.addEventListener("change", onWide);
    else if (wide.addListener) wide.addListener(onWide);
  }

  /* ── 2. Reveals & section spy ───────────────────────── */
  var revealEls = $$("[data-reveal]");
  var revealedAny = false;

  if (reduceMotion || !("IntersectionObserver" in window)) {
    revealEls.forEach(function (el) { el.classList.add("is-visible"); });
  } else {
    var revealObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        revealedAny = true;
        entry.target.classList.add("is-visible");
        revealObserver.unobserve(entry.target);
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -8% 0px" });

    revealEls.forEach(function (el) { revealObserver.observe(el); });

    /* Safety net: if the observer never runs, never leave content hidden. */
    window.setTimeout(function () {
      if (revealedAny) return;
      revealEls.forEach(function (el) { el.classList.add("is-visible"); });
    }, 2200);
  }

  var sectionIds = ["features", "workflow", "demo", "privacy", "faq"];
  var navLinks = {};
  $$("#siteNav a").forEach(function (link) {
    var id = (link.getAttribute("href") || "").replace(/^#/, "");
    if (id) navLinks[id] = link;
  });

  if ("IntersectionObserver" in window && Object.keys(navLinks).length) {
    var visible = {};
    var spy = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        visible[entry.target.id] = entry.isIntersecting;
      });
      var current = null;
      sectionIds.forEach(function (id) { if (!current && visible[id]) current = id; });
      sectionIds.forEach(function (id) {
        var link = navLinks[id];
        if (!link) return;
        var active = id === current;
        link.classList.toggle("is-active", active);
        if (active) link.setAttribute("aria-current", "true");
        else link.removeAttribute("aria-current");
      });
    }, { rootMargin: "-28% 0px -56% 0px", threshold: 0 });

    sectionIds.forEach(function (id) {
      var section = document.getElementById(id);
      if (section) spy.observe(section);
    });
  }

  /* ── 3. Demo quiz ───────────────────────────────────── */
  var QUESTIONS = [
    {
      topic: { en: "Kerala Geography", ml: "കേരള ഭൂമിശാസ്ത്രം" },
      level: { en: "Easy", ml: "എളുപ്പം" },
      stem: {
        en: "Which is the longest river in Kerala?",
        ml: "കേരളത്തിലെ ഏറ്റവും നീളമുള്ള നദി ഏതാണ്?"
      },
      options: [
        { en: "Bharathappuzha", ml: "ഭാരതപ്പുഴ" },
        { en: "Periyar", ml: "പെരിയാർ" },
        { en: "Pamba", ml: "പമ്പ" },
        { en: "Chalakudy", ml: "ചാലക്കുടിപ്പുഴ" }
      ],
      answer: 1,
      why: {
        en: "The Periyar runs about 244 km from Sivagiri in Idukki to the Arabian Sea, making it the longest river in Kerala. The Bharathappuzha is second.",
        ml: "ഇടുക്കി ജില്ലയിലെ ശിവഗിരിയിൽ നിന്ന് ഏകദേശം 244 കി.മീ. സഞ്ചരിച്ച് അറബിക്കടലിൽ പതിക്കുന്ന പെരിയാറാണ് കേരളത്തിലെ ഏറ്റവും നീളമുള്ള നദി. ഭാരതപ്പുഴയാണ് രണ്ടാമത്."
      }
    },
    {
      topic: { en: "Kerala History", ml: "കേരള ചരിത്രം" },
      level: { en: "Easy", ml: "എളുപ്പം" },
      stem: {
        en: "In which year was the state of Kerala formed?",
        ml: "കേരള സംസ്ഥാനം രൂപീകൃതമായത് ഏത് വർഷത്തിലാണ്?"
      },
      options: [
        { en: "1956", ml: "1956" },
        { en: "1947", ml: "1947" },
        { en: "1960", ml: "1960" },
        { en: "1969", ml: "1969" }
      ],
      answer: 0,
      why: {
        en: "Kerala was formed on 1 November 1956 under the States Reorganisation Act, when Travancore–Cochin was merged with the Malabar district.",
        ml: "1956 നവംബർ 1-ന് സംസ്ഥാന പുനഃസംഘടനാ നിയമപ്രകാരം തിരുവിതാംകൂർ–കൊച്ചിയും മലബാറും ചേർത്ത് കേരളം രൂപീകൃതമായി."
      }
    },
    {
      topic: { en: "Kerala Renaissance", ml: "കേരള നവോത്ഥാനം" },
      level: { en: "Medium", ml: "ഇടത്തരം" },
      stem: {
        en: "Who is known as the father of the Kerala Renaissance?",
        ml: "കേരള നവോത്ഥാനത്തിന്റെ പിതാവ് എന്ന് അറിയപ്പെടുന്നത് ആരാണ്?"
      },
      options: [
        { en: "Sree Narayana Guru", ml: "ശ്രീനാരായണ ഗുരു" },
        { en: "Chattampi Swamikal", ml: "ചട്ടമ്പിസ്വാമികൾ" },
        { en: "Ayyankali", ml: "അയ്യങ്കാളി" },
        { en: "Kumaran Asan", ml: "കുമാരനാശാൻ" }
      ],
      answer: 0,
      why: {
        en: "Sree Narayana Guru (1856–1928) led the SNDP Yogam and the temple-entry movement, and is widely called the father of the Kerala Renaissance.",
        ml: "എസ്.എൻ.ഡി.പി. യോഗവും ക്ഷേത്രപ്രവേശന പ്രസ്ഥാനവും നയിച്ച ശ്രീനാരായണ ഗുരുവിനെ (1856–1928) കേരള നവോത്ഥാനത്തിന്റെ പിതാവ് എന്ന് വിളിക്കുന്നു."
      }
    }
  ];

  var shell = $("#quizShell");
  var status = $("#demoStatus");

  var quiz = {
    view: "question",
    lang: "en",
    index: 0,
    selected: null,
    checked: false,
    answers: []
  };

  function esc(value) {
    return String(value).replace(/[&<>"']/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
    });
  }

  function letter(index) { return "ABCD".charAt(index); }

  function pad(value) { return value < 10 ? "0" + value : String(value); }

  function score() {
    return quiz.answers.reduce(function (total, answer) {
      return total + (answer && answer.correct ? 1 : 0);
    }, 0);
  }

  function announce(message) {
    if (status) status.textContent = message;
  }

  function icon(name, cls) {
    return '<svg class="icon ' + (cls || "") + '" aria-hidden="true"><use href="#i-' + name + '"/></svg>';
  }

  function quizHead() {
    return '' +
      '<div class="quiz-head">' +
        '<span class="quiz-kicker">Practice · Demo set</span>' +
        '<div class="lang-toggle" role="group" aria-label="Question language">' +
          '<button type="button" class="lang-btn' + (quiz.lang === "en" ? " is-active" : "") + '" data-lang="en" aria-pressed="' + (quiz.lang === "en") + '">EN</button>' +
          '<button type="button" class="lang-btn' + (quiz.lang === "ml" ? " is-active" : "") + '" data-lang="ml" aria-pressed="' + (quiz.lang === "ml") + '" lang="ml">മലയാളം</button>' +
        '</div>' +
      '</div>';
  }

  function questionTemplate() {
    var q = QUESTIONS[quiz.index];
    var lang = quiz.lang;
    var total = QUESTIONS.length;
    var progress = Math.round(((quiz.index + 1) / total) * 1000) / 10;
    var isLast = quiz.index === total - 1;
    var doneCount = quiz.answers.filter(Boolean).length;

    var options = q.options.map(function (option, i) {
      var stateClass = "";
      var mark = "";
      if (quiz.checked) {
        if (i === q.answer) {
          stateClass = " is-correct";
          mark = icon("check", "opt-mark");
        } else if (i === quiz.selected) {
          stateClass = " is-wrong";
          mark = icon("close", "opt-mark");
        } else {
          stateClass = " is-dim";
        }
      } else if (i === quiz.selected) {
        stateClass = " is-selected";
      }

      return '' +
        '<button type="button" class="option' + stateClass + '" data-opt="' + i + '" aria-pressed="' + (i === quiz.selected) + '"' + (quiz.checked ? " disabled" : "") + '>' +
          '<span class="opt-letter">' + letter(i) + '</span>' +
          '<span class="opt-text" lang="' + lang + '">' + esc(option[lang]) + '</span>' +
          mark +
        '</button>';
    }).join("");

    var feedback = "";
    if (quiz.checked) {
      var result = quiz.answers[quiz.index];
      var ok = result && result.correct;
      var answerLabel = lang === "ml" ? "ഉത്തരം" : "Answer";
      feedback = '' +
        '<div class="feedback' + (ok ? "" : " is-wrong") + '" role="note">' +
          '<div class="feedback-head">' + icon(ok ? "check" : "close") + (ok ? "Correct" : "Not quite") + '</div>' +
          '<p lang="' + lang + '"><strong>' + answerLabel + ': ' + letter(q.answer) + '.</strong> ' + esc(q.why[lang]) + '</p>' +
        '</div>';
    }

    var hint = !quiz.checked
      ? (quiz.selected === null ? "Choose one answer." : "Tap check when you’re ready.")
      : "Explanation added to your review.";

    var action = quiz.checked
      ? '<button type="button" class="btn btn-primary" data-action="next">' + (isLast ? "See result" : "Next question") + " " + icon("arrow") + '</button>'
      : '<button type="button" class="btn btn-primary" data-action="check"' + (quiz.selected === null ? " disabled" : "") + '>Check answer</button>';

    return '' +
      '<div class="quiz" data-view="question">' +
        quizHead() +
        '<div class="quiz-progress" role="progressbar" aria-label="Demo progress" aria-valuemin="1" aria-valuemax="' + total + '" aria-valuenow="' + (quiz.index + 1) + '"><span style="width:' + progress + '%"></span></div>' +
        '<div class="quiz-count-row"><span>Question ' + pad(quiz.index + 1) + " / " + pad(total) + '</span><span class="quiz-score-live">' + doneCount + " checked · " + score() + ' correct</span></div>' +
        '<div class="quiz-meta"><span class="chip chip--indigo" lang="' + lang + '">' + esc(q.topic[lang]) + '</span><span class="chip" lang="' + lang + '">' + esc(q.level[lang]) + '</span></div>' +
        '<h3 class="quiz-stem" id="quizStem" tabindex="-1" lang="' + lang + '">' + esc(q.stem[lang]) + '</h3>' +
        '<div class="options" role="group" aria-labelledby="quizStem">' + options + '</div>' +
        feedback +
        '<div class="quiz-actions"><span class="quiz-hint">' + hint + '</span>' + action + '</div>' +
      '</div>';
  }

  function resultTemplate() {
    var total = QUESTIONS.length;
    var correct = score();
    var percent = Math.round((correct / total) * 100);
    var passed = percent >= 60;
    var lang = quiz.lang;
    var headlines = ["First pass done.", "Keep going.", "Solid start.", "Clean sweep."];
    var headline = headlines[Math.min(correct, 3)];
    var copy = "You got " + correct + " of " + total + " right. Every attempt in Padanam is stored with this same review trail — the question, your answer and a bilingual explanation.";

    var review = QUESTIONS.map(function (q, i) {
      var answer = quiz.answers[i];
      var ok = answer && answer.correct;
      return '' +
        '<li>' +
          '<span class="num mono">' + pad(i + 1) + '</span>' +
          '<span class="q-text" lang="' + lang + '">' + esc(q.stem[lang]) + '</span>' +
          '<span class="result-mark' + (ok ? "" : " is-wrong") + '" role="img" aria-label="' + (ok ? "Correct" : "Incorrect") + '">' + icon(ok ? "check" : "close") + '</span>' +
        '</li>';
    }).join("");

    return '' +
      '<div class="quiz quiz--result" data-view="result">' +
        '<div class="result-eyebrow"><span>Demo complete · ' + total + ' questions</span><span class="chip ' + (passed ? "chip--success" : "") + '">' + (passed ? "Pass · 60%" : "Keep practising") + '</span></div>' +
        '<div class="score-ring" style="--pct:' + percent + '" role="img" aria-label="Score: ' + percent + ' percent">' +
          '<span class="score-ring-inner"><strong>' + correct + '<span>/' + total + '</span></strong><small>score</small></span>' +
        '</div>' +
        '<h3 class="result-headline" tabindex="-1">' + headline + '</h3>' +
        '<p class="result-copy">' + copy + '</p>' +
        '<div class="result-stats">' +
          '<div><strong>' + correct + '</strong><span>Correct</span></div>' +
          '<div><strong>' + (total - correct) + '</strong><span>Wrong</span></div>' +
          '<div><strong>' + percent + '%</strong><span>Score</span></div>' +
        '</div>' +
        '<ul class="result-review">' + review + '</ul>' +
        '<div class="quiz-result-actions">' +
          '<button type="button" class="btn btn-ghost" data-action="restart">' + icon("replay") + 'Try again</button>' +
          '<a class="btn btn-primary" href="#download">Get the app ' + icon("arrow") + '</a>' +
        '</div>' +
        '<p class="result-foot-note">Demo only · scores stay in this tab</p>' +
      '</div>';
  }

  function render(focusSelector) {
    if (!shell) return;
    shell.innerHTML = quiz.view === "result" ? resultTemplate() : questionTemplate();
    if (focusSelector) {
      var target = $(focusSelector, shell);
      if (target) {
        try { target.focus({ preventScroll: true }); } catch (err) { target.focus(); }
      }
    }
  }

  function selectOption(index) {
    if (!shell || quiz.checked) return;
    quiz.selected = index;

    $$(".option", shell).forEach(function (button, i) {
      var active = i === index;
      button.classList.toggle("is-selected", active);
      button.setAttribute("aria-pressed", String(active));
    });

    var check = $('[data-action="check"]', shell);
    if (check) check.disabled = false;
    var hint = $(".quiz-hint", shell);
    if (hint) hint.textContent = "Tap check when you’re ready.";
    announce("Option " + letter(index) + " selected.");
  }

  function checkAnswer() {
    if (!shell || quiz.selected === null || quiz.checked) return;
    quiz.checked = true;
    var q = QUESTIONS[quiz.index];
    quiz.answers[quiz.index] = {
      selected: quiz.selected,
      correct: quiz.selected === q.answer
    };

    render('[data-action="next"]');
    var ok = quiz.answers[quiz.index].correct;
    announce((ok ? "Correct. " : "Not quite. Answer " + letter(q.answer) + ". ") + q.why.en);
  }

  function nextQuestion() {
    if (!shell) return;
    if (quiz.index < QUESTIONS.length - 1) {
      quiz.index += 1;
      quiz.selected = null;
      quiz.checked = false;
      render(".quiz-stem");
      announce("Question " + (quiz.index + 1) + " of " + QUESTIONS.length + ".");
    } else {
      quiz.view = "result";
      render(".result-headline");
      announce("Demo complete. You scored " + score() + " out of " + QUESTIONS.length + ".");
    }
  }

  function restartDemo() {
    if (!shell) return;
    quiz.view = "quiz";
    quiz.index = 0;
    quiz.selected = null;
    quiz.checked = false;
    quiz.answers = [];
    render(".quiz-stem");
    announce("Demo reset. Question 1 of " + QUESTIONS.length + ".");
  }

  function setLanguage(lang) {
    if (!shell || lang === quiz.lang) return;
    quiz.lang = lang;
    render('[data-lang="' + lang + '"]');
    announce(lang === "ml" ? "Question content switched to Malayalam." : "Question content switched to English.");
  }

  if (shell) {
    shell.addEventListener("click", function (event) {
      var langButton = event.target.closest("[data-lang]");
      if (langButton && shell.contains(langButton)) {
        setLanguage(langButton.getAttribute("data-lang"));
        return;
      }

      var option = event.target.closest("[data-opt]");
      if (option && shell.contains(option) && !quiz.checked) {
        selectOption(Number(option.getAttribute("data-opt")));
        return;
      }

      var action = event.target.closest("[data-action]");
      if (!action || !shell.contains(action)) return;
      switch (action.getAttribute("data-action")) {
        case "check": checkAnswer(); break;
        case "next": nextQuestion(); break;
        case "restart": restartDemo(); break;
      }
    });

    quiz.view = "question";
    render();
  }

  /* ── 4. Release metadata ────────────────────────────── */
  var RELEASES_API = "https://api.github.com/repos/dune-ai-test/psc-examer/releases?per_page=10";

  function hydrateRelease() {
    if (!("fetch" in window) || !("AbortController" in window)) return;

    var controller = new AbortController();
    var timeout = window.setTimeout(function () { controller.abort(); }, 7000);

    fetch(RELEASES_API, {
      headers: { Accept: "application/vnd.github+json" },
      signal: controller.signal
    }).then(function (response) {
      if (!response.ok) throw new Error("release metadata unavailable");
      return response.json();
    }).then(function (releases) {
      if (!Array.isArray(releases)) return;

      var release = null;
      var asset = null;
      releases.some(function (candidate) {
        if (candidate.draft || !Array.isArray(candidate.assets)) return false;
        var apk = candidate.assets.filter(function (item) {
          return /\.apk$/i.test(item.name || "");
        })[0];
        if (!apk) return false;
        release = candidate;
        asset = apk;
        return true;
      });
      if (!release || !asset) return;

      $$('[data-apk-link]').forEach(function (link) {
        link.href = asset.browser_download_url;
      });

      var version = String(release.tag_name || "").replace(/^v/, "");
      if (version) {
        $$('[data-apk-version]').forEach(function (el) { el.textContent = version; });
      }

      if (asset.size) {
        var size = (asset.size / (1024 * 1024)).toFixed(1) + " MB";
        $$('[data-apk-size]').forEach(function (el) { el.textContent = size; });
      }

      if (release.published_at) {
        var date = new Date(release.published_at);
        if (!isNaN(date.getTime())) {
          var formatted = date.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
          $$('[data-apk-date]').forEach(function (el) { el.textContent = formatted; });
        }
      }

      if (asset.digest) {
        $$('[data-apk-sha]').forEach(function (el) { el.textContent = asset.digest; });
      }
    }).catch(function () {
      /* Private repo, offline, or rate limited: static release links remain. */
    }).then(function () {
      window.clearTimeout(timeout);
    });
  }

  if (document.readyState === "complete") hydrateRelease();
  else window.addEventListener("load", hydrateRelease, { once: true });

  /* ── 5. Footer year ─────────────────────────────────── */
  $$("[data-year]").forEach(function (el) {
    el.textContent = String(new Date().getFullYear());
  });
})();
