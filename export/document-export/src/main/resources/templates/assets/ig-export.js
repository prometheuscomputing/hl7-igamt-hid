/*
 * Behaviour of the HTML export: contents tree, search, table filters, theme.
 * Inlined at the end of the exported file. No dependency, and the document stays
 * fully readable (and printable) when scripts do not run.
 */
(function () {
  'use strict';

  var root = document.documentElement;
  var body = document.body;
  var main = document.getElementById('ig-main');
  var sidebar = document.getElementById('ig-sidebar');
  if (!main || !sidebar) { return; }

  function each(list, fn) { Array.prototype.forEach.call(list, fn); }
  function store(key, value) {
    try {
      if (value === undefined) { return window.localStorage.getItem(key); }
      window.localStorage.setItem(key, value);
    } catch (e) { /* storage is not available from every file:// context */ }
    return null;
  }

  /* ---------------------------------------------------------------- theme */

  var themeButton = document.getElementById('ig-theme');
  function applyTheme(theme) {
    root.setAttribute('data-theme', theme);
    if (themeButton) {
      themeButton.textContent = theme === 'dark' ? 'Light' : 'Dark';
      themeButton.setAttribute('aria-label', theme === 'dark' ? 'Switch to the light theme' : 'Switch to the dark theme');
    }
  }
  var savedTheme = store('ig-theme');
  var prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  applyTheme(savedTheme || (prefersDark ? 'dark' : 'light'));
  if (themeButton) {
    themeButton.addEventListener('click', function () {
      var next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      applyTheme(next);
      store('ig-theme', next);
    });
  }

  var printButton = document.getElementById('ig-print');
  if (printButton) { printButton.addEventListener('click', function () { window.print(); }); }

  /* ---------------------------------------------------------------- contents */

  var narrow = window.matchMedia ? window.matchMedia('(max-width: 1000px)') : { matches: false };
  var navToggle = document.getElementById('ig-nav-toggle');
  if (navToggle) {
    navToggle.addEventListener('click', function () {
      body.classList.toggle(narrow.matches ? 'ig-nav-open' : 'ig-nav-hidden');
    });
  }

  var tocLinks = sidebar.querySelectorAll('.ig-toc-row > a');
  var linkById = {};
  each(tocLinks, function (link) { linkById[link.getAttribute('href').slice(1)] = link; });

  function setCollapsed(item, collapsed) {
    item.classList.toggle('is-collapsed', collapsed);
    var toggle = item.querySelector(':scope > .ig-toc-row > .ig-toc-toggle');
    if (toggle) { toggle.setAttribute('aria-expanded', collapsed ? 'false' : 'true'); }
  }
  function depthOf(item) {
    var depth = 0;
    for (var node = item.parentNode; node && node !== sidebar; node = node.parentNode) {
      if (node.tagName === 'UL') { depth++; }
    }
    return depth;
  }
  var parents = sidebar.querySelectorAll('li.ig-toc-parent');
  // Long lists (every segment, every value set) start folded so the outline stays scannable.
  each(parents, function (item) {
    var children = item.querySelectorAll(':scope > .ig-toc > li').length;
    if (depthOf(item) >= 2 && children > 6) { setCollapsed(item, true); }
  });
  sidebar.addEventListener('click', function (event) {
    var toggle = event.target.closest ? event.target.closest('.ig-toc-toggle') : null;
    if (toggle) {
      var item = toggle.parentNode.parentNode;
      setCollapsed(item, !item.classList.contains('is-collapsed'));
      return;
    }
    if (event.target.closest && event.target.closest('a') && narrow.matches) { body.classList.remove('ig-nav-open'); }
  });
  var collapseAll = document.getElementById('ig-toc-collapse');
  if (collapseAll) {
    collapseAll.addEventListener('click', function () {
      var collapse = collapseAll.textContent.indexOf('Collapse') === 0;
      each(parents, function (item) { setCollapsed(item, collapse); });
      collapseAll.textContent = collapse ? 'Expand all' : 'Collapse all';
    });
  }

  function reveal(link) {
    for (var node = link.parentNode; node && node !== sidebar; node = node.parentNode) {
      if (node.tagName === 'LI' && node.classList.contains('is-collapsed') && !node.contains(link.parentNode) === false
          && node !== link.parentNode.parentNode) {
        setCollapsed(node, false);
      }
    }
  }

  var activeLink = null;
  function setActive(id) {
    var link = linkById[id];
    if (!link || link === activeLink) { return; }
    if (activeLink) { activeLink.classList.remove('is-active'); activeLink.removeAttribute('aria-current'); }
    activeLink = link;
    link.classList.add('is-active');
    link.setAttribute('aria-current', 'location');
    reveal(link);
    var nav = sidebar.querySelector('nav');
    var linkBox = link.getBoundingClientRect();
    var navBox = nav.getBoundingClientRect();
    if (linkBox.top < navBox.top + 8 || linkBox.bottom > navBox.bottom - 8) {
      nav.scrollTop += linkBox.top - navBox.top - navBox.height / 3;
    }
  }

  var headings = main.querySelectorAll('.ig-heading[id]');
  var ticking = false;
  function updateActive() {
    ticking = false;
    var offset = 150;
    var current = null;
    var low = 0;
    var high = headings.length - 1;
    // headings are in document order: find the last one above the reading line
    while (low <= high) {
      var mid = (low + high) >> 1;
      if (headings[mid].getBoundingClientRect().top <= offset) { current = headings[mid]; low = mid + 1; } else { high = mid - 1; }
    }
    if (current) { setActive(current.id); }
    var toTop = document.getElementById('ig-to-top');
    if (toTop) { toTop.hidden = window.pageYOffset < 900; }
  }
  window.addEventListener('scroll', function () {
    if (!ticking) { ticking = true; window.requestAnimationFrame(updateActive); }
  }, { passive: true });
  updateActive();

  var toTopButton = document.getElementById('ig-to-top');
  if (toTopButton) { toTopButton.addEventListener('click', function () { window.scrollTo(0, 0); }); }

  /* ---------------------------------------------------------------- section links */

  main.addEventListener('click', function (event) {
    var anchor = event.target.closest ? event.target.closest('.ig-anchor') : null;
    if (!anchor) { return; }
    var url = window.location.href.split('#')[0] + anchor.getAttribute('href');
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url).then(function () {
        anchor.classList.add('is-copied');
        window.setTimeout(function () { anchor.classList.remove('is-copied'); }, 1400);
      }, function () { /* the link still navigates */ });
    }
  });

  /* ---------------------------------------------------------------- usage codes */

  var usageNames = {
    R: 'Required', RE: 'Required, may be empty', O: 'Optional', C: 'Conditional', CE: 'Conditional, may be empty',
    X: 'Not supported', B: 'Backward compatibility', W: 'Withdrawn', IX: 'Not supported, ignored', CAB: 'Conditional',
    P: 'Permitted', E: 'Excluded', D: 'Deprecated'
  };
  each(main.querySelectorAll('.ig-usage:not([title])'), function (chip) {
    var name = usageNames[chip.textContent.trim()];
    if (name) { chip.title = name; }
  });

  /* ---------------------------------------------------------------- table filters */

  each(main.querySelectorAll('.ig-table-wrap'), function (wrap) {
    var table = wrap.querySelector('table');
    if (!table || !table.tBodies.length || table.classList.contains('ig-structure') || table.classList.contains('ig-grid')) { return; }
    var rows = table.tBodies[0].rows;
    if (rows.length < 40) { return; }
    var tools = document.createElement('div');
    tools.className = 'ig-table-tools';
    var input = document.createElement('input');
    input.type = 'search';
    input.className = 'ig-table-filter';
    input.placeholder = 'Filter ' + rows.length + ' rows';
    input.setAttribute('aria-label', 'Filter the rows of this table');
    var count = document.createElement('span');
    count.className = 'ig-table-count';
    tools.appendChild(input);
    tools.appendChild(count);
    wrap.insertBefore(tools, table);
    var texts = null;
    input.addEventListener('input', function () {
      if (!texts) { texts = Array.prototype.map.call(rows, function (row) { return row.textContent.toLowerCase(); }); }
      var terms = input.value.toLowerCase().split(/\s+/).filter(Boolean);
      var shown = 0;
      for (var i = 0; i < rows.length; i++) {
        var match = terms.every(function (term) { return texts[i].indexOf(term) !== -1; });
        rows[i].hidden = !match;
        if (match) { shown++; }
      }
      count.textContent = terms.length ? shown + ' of ' + rows.length : '';
    });
  });

  /* ---------------------------------------------------------------- search */

  var searchInput = document.getElementById('ig-search');
  var resultsBox = document.getElementById('ig-search-results');
  if (!searchInput || !resultsBox) { return; }

  var entries = null;
  function buildIndex() {
    entries = [];
    each(tocLinks, function (link) {
      var code = link.getAttribute('data-code') || '';
      var name = link.querySelector('.ig-toc-name, .ig-toc-title');
      var number = link.querySelector('.ig-toc-num');
      entries.push({
        kind: link.getAttribute('data-kind') || 'Section',
        code: code,
        title: name ? name.textContent : '',
        context: number ? number.textContent : '',
        href: link.getAttribute('href'),
        weight: code ? 3 : 2
      });
    });
    // fields and components, so "patient name" or "PID-5" lands on the row itself
    each(main.querySelectorAll('section.ig-resource'), function (section) {
      var kind = section.getAttribute('data-kind');
      if (kind !== 'Segment' && kind !== 'Datatype') { return; }
      var codeNode = section.querySelector(':scope > .ig-heading .ig-code');
      var table = section.querySelector(':scope > .ig-table-wrap table');
      if (!codeNode || !table || !table.tBodies.length) { return; }
      var owner = codeNode.textContent;
      each(table.tBodies[0].rows, function (row, position) {
        var seq = row.querySelector('td.c-seq');
        var name = row.querySelector('td.c-name');
        if (!seq || !name || !name.textContent.trim()) { return; }
        if (!row.id) { row.id = section.querySelector(':scope > .ig-heading').id + '-r' + (position + 1); }
        var separator = kind === 'Segment' ? '-' : '.';
        var base = owner.split('_')[0];
        entries.push({
          kind: kind === 'Segment' ? 'Field' : 'Component',
          // a flavor is also found under the name of its base: "PID-3" finds PID_IZ-3
          alias: base !== owner ? base + separator + seq.textContent.trim() : '',
          code: owner + separator + seq.textContent.trim(),
          title: name.textContent.trim(),
          context: owner,
          href: '#' + row.id,
          weight: 1
        });
      });
    });
    entries.forEach(function (entry) {
      entry.haystack = (entry.code + ' ' + (entry.alias || '') + ' ' + entry.title).toLowerCase();
      entry.aliasLower = (entry.alias || '').toLowerCase();
      entry.codeLower = entry.code.toLowerCase();
    });
  }

  function escapeHtml(text) {
    return text.replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; });
  }
  function highlight(text, terms) {
    var html = escapeHtml(text);
    terms.forEach(function (term) {
      var safe = escapeHtml(term).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      html = html.replace(new RegExp('(' + safe + ')(?![^<]*>)', 'ig'), '<mark>$1</mark>');
    });
    return html;
  }

  var active = -1;
  function render(query) {
    if (!entries) { buildIndex(); }
    var terms = query.toLowerCase().split(/\s+/).filter(Boolean);
    if (!terms.length) { resultsBox.hidden = true; resultsBox.innerHTML = ''; return; }
    var scored = [];
    for (var i = 0; i < entries.length; i++) {
      var entry = entries[i];
      var ok = true;
      for (var t = 0; t < terms.length; t++) { if (entry.haystack.indexOf(terms[t]) === -1) { ok = false; break; } }
      if (!ok) { continue; }
      var score = entry.weight;
      if (entry.codeLower === terms[0] || entry.aliasLower === terms[0]) { score += 20; } else if (entry.codeLower.indexOf(terms[0]) === 0) { score += 10; }
      if (entry.title.toLowerCase().indexOf(terms[0]) === 0) { score += 4; }
      scored.push({ entry: entry, score: score, order: i });
    }
    scored.sort(function (a, b) { return b.score - a.score || a.order - b.order; });
    var total = scored.length;
    scored = scored.slice(0, 40);
    if (!scored.length) {
      resultsBox.innerHTML = '<div class="ig-search-empty">Nothing in this document matches "' + escapeHtml(query) + '".</div>';
    } else {
      resultsBox.innerHTML = scored.map(function (item) {
        var entry = item.entry;
        return '<a class="ig-result" href="' + escapeHtml(entry.href) + '">' +
          '<span class="ig-result-kind">' + escapeHtml(entry.kind) + '</span>' +
          '<span class="ig-result-title">' + (entry.code ? '<code>' + highlight(entry.code, terms) + '</code> ' : '') + highlight(entry.title, terms) + '</span>' +
          '<span class="ig-result-context">' + escapeHtml(entry.context) + '</span></a>';
      }).join('') + (total > scored.length ? '<div class="ig-search-empty">' + (total - scored.length) + ' more. Keep typing to narrow down.</div>' : '');
    }
    active = -1;
    resultsBox.hidden = false;
    move(1);
  }
  function move(step) {
    var items = resultsBox.querySelectorAll('.ig-result');
    if (!items.length) { return; }
    if (active >= 0 && items[active]) { items[active].classList.remove('is-active'); }
    active = (active + step + items.length) % items.length;
    items[active].classList.add('is-active');
    items[active].scrollIntoView({ block: 'nearest' });
  }
  function close() { resultsBox.hidden = true; }
  function go(href) {
    close();
    searchInput.blur();
    var target = document.getElementById(href.slice(1));
    if (!target) { return; }
    window.location.hash = href;
    if (target.tagName === 'TR') {
      target.classList.add('is-target');
      window.setTimeout(function () { target.classList.remove('is-target'); }, 2600);
      target.scrollIntoView({ block: 'center' });
    }
  }

  searchInput.addEventListener('input', function () { render(searchInput.value); });
  searchInput.addEventListener('focus', function () { if (searchInput.value) { render(searchInput.value); } });
  searchInput.addEventListener('keydown', function (event) {
    if (event.key === 'ArrowDown') { event.preventDefault(); move(1); }
    else if (event.key === 'ArrowUp') { event.preventDefault(); move(-1); }
    else if (event.key === 'Enter') {
      var item = resultsBox.querySelectorAll('.ig-result')[active];
      if (item) { event.preventDefault(); go(item.getAttribute('href')); }
    } else if (event.key === 'Escape') { close(); searchInput.blur(); }
  });
  resultsBox.addEventListener('click', function (event) {
    var item = event.target.closest ? event.target.closest('.ig-result') : null;
    if (item) { event.preventDefault(); go(item.getAttribute('href')); }
  });
  document.addEventListener('click', function (event) {
    if (!resultsBox.hidden && !event.target.closest('.ig-search')) { close(); }
  });
  document.addEventListener('keydown', function (event) {
    var tag = event.target.tagName;
    if (event.key === '/' && tag !== 'INPUT' && tag !== 'TEXTAREA' && !event.ctrlKey && !event.metaKey) {
      event.preventDefault();
      searchInput.focus();
      searchInput.select();
    }
  });
})();
