/* Browser compat for Android WebView / Chrome 60–84 (e-ink devices).
 * Load before other app scripts. Safe no-ops on modern browsers.
 */
(function () {
  'use strict';

  // ---- Promise.finally (Chrome < 63) ----
  if (typeof Promise !== 'undefined' && !Promise.prototype.finally) {
    Promise.prototype.finally = function (cb) {
      var onFinally = function (value) {
        return Promise.resolve(typeof cb === 'function' ? cb() : null).then(function () { return value; });
      };
      var onReject = function (reason) {
        return Promise.resolve(typeof cb === 'function' ? cb() : null).then(function () { throw reason; });
      };
      return this.then(onFinally, onReject);
    };
  }

  // ---- Object.fromEntries (Chrome < 73) ----
  if (!Object.fromEntries) {
    Object.fromEntries = function (iterable) {
      var obj = {}, list = iterable;
      if (typeof iterable === 'object' && typeof iterable.entries === 'function' && !Array.isArray(iterable)) {
        list = iterable.entries();
      }
      if (typeof list[Symbol.iterator] === 'function') {
        var it = list[Symbol.iterator](), step;
        while (!(step = it.next()).done) {
          var pair = step.value;
          obj[pair[0]] = pair[1];
        }
      } else if (Array.isArray(list)) {
        for (var i = 0; i < list.length; i++) obj[list[i][0]] = list[i][1];
      }
      return obj;
    };
  }

  // ---- Array.flat / flatMap (Chrome < 69) ----
  if (!Array.prototype.flat) {
    Array.prototype.flat = function (depth) {
      depth = depth === undefined ? 1 : Math.floor(depth);
      var result = [];
      (function flatten(arr, d) {
        for (var i = 0; i < arr.length; i++) {
          if (Array.isArray(arr[i]) && d > 0) flatten(arr[i], d - 1);
          else result.push(arr[i]);
        }
      })(this, depth);
      return result;
    };
  }
  if (!Array.prototype.flatMap) {
    Array.prototype.flatMap = function (fn, thisArg) {
      return Array.prototype.map.call(this, fn, thisArg).flat();
    };
  }

  // ---- String.matchAll (Chrome < 73) ----
  if (!String.prototype.matchAll) {
    String.prototype.matchAll = function (re) {
      var flags = (re && re.flags != null) ? re.flags : '';
      if (flags.indexOf('g') === -1) flags += 'g';
      var rx = new RegExp(re.source, flags);
      var str = String(this), matches = [], m;
      while ((m = rx.exec(str)) !== null) {
        matches.push(m);
        if (!m[0]) rx.lastIndex++;
      }
      var i = 0;
      return {
        next: function () {
          if (i < matches.length) return { value: matches[i++], done: false };
          return { value: undefined, done: true };
        },
        [Symbol.iterator]: function () { return this; }
      };
    };
  }

  // ---- AbortController stub (Chrome < 66): timeout won't cancel fetch ----
  if (typeof AbortController === 'undefined') {
    window.AbortController = function () {
      this.signal = {
        aborted: false,
        addEventListener: function () {},
        removeEventListener: function () {},
        dispatchEvent: function () { return false; }
      };
      this.abort = function () { this.signal.aborted = true; };
    };
  }

  // ---- Element.remove (Chrome < 23 OK; IE no — keep for WebView safety) ----
  if (!Element.prototype.remove) {
    Element.prototype.remove = function () {
      if (this.parentNode) this.parentNode.removeChild(this);
    };
  }

  // ---- NodeList.forEach (Chrome < 51 rare; keep) ----
  if (window.NodeList && !NodeList.prototype.forEach) {
    NodeList.prototype.forEach = Array.prototype.forEach;
  }

  // ---- matchMedia addEventListener (Chrome < 88 uses addListener) ----
  try {
    var mq = window.matchMedia('(prefers-color-scheme: dark)');
    if (mq && !mq.addEventListener && mq.addListener) {
      MediaQueryList.prototype.addEventListener = function (type, listener) {
        if (type === 'change') this.addListener(listener);
      };
      MediaQueryList.prototype.removeEventListener = function (type, listener) {
        if (type === 'change') this.removeListener(listener);
      };
    }
  } catch (e) {}

  // ---- scrollTo / scrollIntoView ----
  // Chrome < 61: options objects are NOT supported (object coerced to NaN, no throw).
  // Detect via CSS scroll-behavior (same Chrome 61 cutoff as ScrollOptions).
  var supportsScrollOpts = false;
  try {
    supportsScrollOpts = 'scrollBehavior' in document.documentElement.style;
  } catch (e) {}

  function normalizeScrollBehavior(opts) {
    if (!opts || typeof opts !== 'object') return opts;
    if (opts.behavior === 'instant') {
      var copy = {};
      for (var k in opts) if (Object.prototype.hasOwnProperty.call(opts, k)) copy[k] = opts[k];
      copy.behavior = 'auto';
      return copy;
    }
    return opts;
  }

  var _scrollTo = window.scrollTo;
  if (typeof _scrollTo === 'function') {
    window.scrollTo = function (a, b) {
      if (typeof a === 'object' && a !== null) {
        a = normalizeScrollBehavior(a);
        if (!supportsScrollOpts) {
          var x = a.left != null ? a.left : (window.scrollX || window.pageXOffset || 0);
          var y = a.top != null ? a.top : (window.scrollY || window.pageYOffset || 0);
          return _scrollTo.call(window, x, y);
        }
        try { return _scrollTo.call(window, a); }
        catch (e) {
          return _scrollTo.call(
            window,
            a.left != null ? a.left : (window.scrollX || window.pageXOffset || 0),
            a.top != null ? a.top : (window.scrollY || window.pageYOffset || 0)
          );
        }
      }
      return _scrollTo.call(window, a, b);
    };
  }

  function applyElScroll(el, a, b) {
    if (typeof a === 'object' && a !== null) {
      if (a.left != null) el.scrollLeft = a.left;
      if (a.top != null) el.scrollTop = a.top;
      return;
    }
    if (a != null) el.scrollLeft = a;
    if (b != null) el.scrollTop = b;
  }

  if (Element.prototype.scrollTo) {
    var _elScrollTo = Element.prototype.scrollTo;
    Element.prototype.scrollTo = function (a, b) {
      if (typeof a === 'object' && a !== null) {
        a = normalizeScrollBehavior(a);
        if (!supportsScrollOpts) { applyElScroll(this, a); return; }
        try { return _elScrollTo.call(this, a); }
        catch (e) { applyElScroll(this, a); return; }
      }
      return _elScrollTo.call(this, a, b);
    };
  } else {
    Element.prototype.scrollTo = function (a, b) {
      applyElScroll(this, typeof a === 'object' && a !== null ? normalizeScrollBehavior(a) : a, b);
    };
  }

  if (Element.prototype.scrollIntoView) {
    var _siv = Element.prototype.scrollIntoView;
    Element.prototype.scrollIntoView = function (arg) {
      if (typeof arg === 'object' && arg !== null) {
        arg = normalizeScrollBehavior(arg);
        if (!supportsScrollOpts) {
          // Pre-options API only accepts a boolean (alignToTop)
          return _siv.call(this, arg.block !== 'end' && arg.block !== 'nearest');
        }
        try { return _siv.call(this, arg); }
        catch (e) { return _siv.call(this, true); }
      }
      return _siv.call(this, arg);
    };
  }

  // ---- Feature detection classes on <html> ----
  var html = document.documentElement;

  // Flex gap (Chrome < 84 ignores gap on flex)
  (function detectFlexGap() {
    try {
      var flex = document.createElement('div');
      flex.style.display = 'flex';
      flex.style.flexDirection = 'column';
      flex.style.rowGap = '1px';
      flex.appendChild(document.createElement('div'));
      flex.appendChild(document.createElement('div'));
      flex.style.position = 'absolute';
      flex.style.visibility = 'hidden';
      document.documentElement.appendChild(flex);
      var supported = flex.scrollHeight === 1;
      document.documentElement.removeChild(flex);
      if (!supported) html.classList.add('no-flex-gap');
    } catch (e) {
      html.classList.add('no-flex-gap');
    }
  })();

  // aspect-ratio
  try {
    if (!CSS || !CSS.supports || !CSS.supports('aspect-ratio', '1')) {
      html.classList.add('no-aspect-ratio');
    }
  } catch (e) {
    html.classList.add('no-aspect-ratio');
  }

  // Rough Chromium major from UA (Android WebView / Chrome)
  function chromeMajor() {
    var ua = navigator.userAgent || '';
    var m = ua.match(/(?:Chrome|Chromium|CriOS)\/(\d+)/);
    if (m) return parseInt(m[1], 10);
    m = ua.match(/AppleWebKit\/(\d+)/);
    // Map old WebKit build numbers loosely — treat unknown mobile as legacy
    if (m && /Android/i.test(ua)) return 70;
    return 999;
  }

  var major = chromeMajor();
  if (major > 0 && major < 84) {
    html.classList.add('legacy-webview');
  }

  // One-time: clear einkMode that older compat.js auto-wrote for dual-core
  // Android + Chrome < 85. Manual toggle still works afterwards.
  try {
    if (!localStorage.getItem('einkAutoMigratedv1')) {
      localStorage.setItem('einkAutoMigratedv1', '1');
      var ua = navigator.userAgent || '';
      var cores = navigator.hardwareConcurrency || 0;
      var wasAutoTarget = /Android/i.test(ua) && major < 85 && cores > 0 && cores <= 2;
      if (wasAutoTarget && localStorage.getItem('einkMode') === 'on') {
        localStorage.removeItem('einkMode');
        var prevMode = localStorage.getItem('readModeBeforeEink');
        if (prevMode) {
          localStorage.setItem('readMode', prevMode);
          localStorage.removeItem('readModeBeforeEink');
        }
      }
    }
  } catch (e) {}

  // Expose for debugging
  window.__fanqieCompat = { chromeMajor: major, noFlexGap: html.classList.contains('no-flex-gap') };
})();
