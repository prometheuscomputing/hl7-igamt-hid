/*
 * Behaviour of the HTML export: contents tree, search, table filters, theme,
 * previews on links and the field detail panel.
 * Inlined at the end of the exported file. No dependency, and the document stays
 * fully readable (and printable) when scripts do not run.
 */
/* text-helpers:start (kept free of the DOM: IgExportTextTest runs this block on its own) */
var IgExportText = (function () {
  'use strict';
  function escapeRegExp(value) { return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }
  /** "PID" for the flavor "PID_IZ". */
  function baseOf(code) { return code.split('_')[0]; }
  function names(code) {
    var base = baseOf(code);
    return escapeRegExp(code) + (base !== code ? '|' + escapeRegExp(base) : '');
  }
  /**
   * Matches a mention of a field or component in running text: "PID-3", "PID_IZ-3", "CX.5" and
   * their sub-elements ("PID-3.1"), but not "PID-30", nor an identifier such as "IZ-PID-3".
   */
  function mentionPattern(code, sequence) {
    return new RegExp('(^|[^A-Za-z0-9_-])(' + names(code) + ')[-.]' + escapeRegExp(sequence) + '(?![0-9])');
  }
  /** Matches the header of a co-constraint column for the field itself: "OBX-3 (code)", not "OBX-3.1". */
  function columnPattern(code, sequence) {
    return new RegExp('^(' + names(code) + ')-' + escapeRegExp(sequence) + '(?![0-9.])');
  }
  /** Matches a location that is the field itself and nothing below it: "PID-3", not "PID-3.1". */
  function locationPattern(code, sequence) {
    return new RegExp('^(' + names(code) + ')[-.]' + escapeRegExp(sequence) + '$');
  }
  return {
    escapeRegExp: escapeRegExp, baseOf: baseOf,
    mentionPattern: mentionPattern, columnPattern: columnPattern, locationPattern: locationPattern
  };
})();
/* text-helpers:end */

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

  // Authors colour words for a white page. On the dark page a colour that is too dark to read is
  // lightened, keeping its hue, until it has the contrast of ordinary text; the light page gets it back.
  function channels(colour) {
    var m = /^rgba?\((\d+),\s*(\d+),\s*(\d+)/.exec(colour);
    return m ? [+m[1], +m[2], +m[3]] : null;
  }
  function luminance(rgb) {
    var c = [0, 0, 0];
    for (var i = 0; i < 3; i++) { var v = rgb[i] / 255; c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
  }
  function contrast(a, b) {
    var la = luminance(a), lb = luminance(b);
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
  }
  function hasOwnBackground(node, stop) {
    for (; node && node !== stop; node = node.parentNode) {
      var behind = node.style ? (node.style.backgroundColor || node.style.background) : '';
      if (behind && !/^(transparent|none|initial|inherit|unset|rgba\(0, 0, 0, 0\))$/.test(behind)) { return true; }
    }
    return false;
  }
  function readableAuthorColours(dark) {
    var content = document.getElementById('ig-content');
    if (!content) { return; }
    var page = channels(getComputedStyle(document.body).backgroundColor) || [16, 24, 31];
    each(content.querySelectorAll('[style*="color"]'), function (node) {
      if (node.hasAttribute('data-ig-colour')) {
        node.style.color = node.getAttribute('data-ig-colour');
        node.removeAttribute('data-ig-colour');
      }
      if (!dark || !node.style.color || hasOwnBackground(node, content)) { return; }
      var rgb = channels(getComputedStyle(node).color);
      if (!rgb || contrast(rgb, page) >= 4.5) { return; }
      var original = node.style.color, mixed = rgb;
      for (var share = 0.1; share <= 1 && contrast(mixed, page) < 4.5; share += 0.1) {
        mixed = [0, 1, 2].map(function (i) { return Math.round(rgb[i] + (255 - rgb[i]) * share); });
      }
      node.setAttribute('data-ig-colour', original);
      node.style.color = 'rgb(' + mixed.join(', ') + ')';
    });
  }

  // the theme can also be set from outside (a host page, a reader's own script)
  if ('MutationObserver' in window) {
    new MutationObserver(function () { readableAuthorColours(root.getAttribute('data-theme') === 'dark'); })
      .observe(root, { attributes: true, attributeFilter: ['data-theme'] });
  }
  // paper is white whatever the theme of the screen
  window.addEventListener('beforeprint', function () { readableAuthorColours(false); });
  window.addEventListener('afterprint', function () { readableAuthorColours(root.getAttribute('data-theme') === 'dark'); });

  var themeButton = document.getElementById('ig-theme');
  function applyTheme(theme) {
    root.setAttribute('data-theme', theme);
    if (!('MutationObserver' in window)) { readableAuthorColours(theme === 'dark'); }
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

  /* ---------------------------------------------------------------- tables that scroll sideways */

  // A table wider than its frame scrolls inside it. With a keyboard that only works when the frame
  // can take the focus, so it becomes a tab stop while it scrolls and has nothing focusable inside.
  function keyboardScroll(wrap) {
    var scrolls = wrap.scrollWidth > wrap.clientWidth + 1 && getComputedStyle(wrap).overflowX !== 'visible';
    if (scrolls && !wrap.hasAttribute('tabindex') && !wrap.querySelector('a[href], button, input, select, [tabindex]')) {
      var caption = wrap.querySelector('caption');
      wrap.setAttribute('tabindex', '0');
      wrap.setAttribute('role', 'group'); // a region would be a landmark, and a guide has hundreds of tables
      wrap.setAttribute('aria-label', (caption ? caption.textContent.trim() + ', ' : '') + 'table that scrolls sideways');
      wrap.setAttribute('data-ig-scrolls', '');
    } else if (!scrolls && wrap.hasAttribute('data-ig-scrolls')) {
      wrap.removeAttribute('tabindex');
      wrap.removeAttribute('role');
      wrap.removeAttribute('aria-label');
      wrap.removeAttribute('data-ig-scrolls');
    }
  }
  var scrollFrames = main.querySelectorAll('.ig-table-wrap');
  var scrollWatch = null;
  if ('IntersectionObserver' in window) {
    // sections off screen are not laid out yet: look at a frame when it comes into view
    scrollWatch = new IntersectionObserver(function (entries) {
      each(entries, function (entry) { if (entry.isIntersecting) { keyboardScroll(entry.target); } });
    }, { rootMargin: '100% 0px' });
    each(scrollFrames, function (wrap) { scrollWatch.observe(wrap); });
  } else {
    each(scrollFrames, keyboardScroll);
  }
  var scrollResize = null;
  window.addEventListener('resize', function () {
    clearTimeout(scrollResize);
    scrollResize = setTimeout(function () {
      if (scrollWatch) {
        // measuring every frame would lay out the whole document: watch again, the frames in view report first
        scrollWatch.disconnect();
        each(scrollFrames, function (wrap) { scrollWatch.observe(wrap); });
      } else {
        each(scrollFrames, keyboardScroll);
      }
    }, 200);
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

  /* ---------------------------------------------------------------- shared lookups */

  function text(node) { return node ? node.textContent.replace(/\s+/g, ' ').trim() : ''; }
  function make(tag, className, content) {
    var node = document.createElement(tag);
    if (className) { node.className = className; }
    if (content !== undefined) { node.textContent = content; }
    return node;
  }
  function resourceOf(node) { return node && node.closest ? node.closest('section.ig-resource') : null; }
  function headingOf(section) { return section ? section.querySelector(':scope > .ig-heading') : null; }
  function describe(section) {
    var heading = headingOf(section);
    return {
      id: heading ? heading.id : '',
      kind: section.getAttribute('data-kind') || '',
      kindLabel: text(heading && heading.querySelector('.ig-kind')),
      code: text(heading && heading.querySelector('.ig-code')),
      name: text(heading && heading.querySelector('.ig-rname'))
    };
  }
  /** A copy that can be shown elsewhere: no ids, no nested links, no filter boxes. */
  function inert(node) {
    var copy = node.cloneNode(true);
    if (copy.removeAttribute) {
      copy.removeAttribute('id');
      copy.removeAttribute('tabindex');
      copy.hidden = false; // a row hidden by the filter of its table is still shown in a copy
      copy.classList.remove('ig-field-row', 'is-selected', 'is-target');
    }
    each(copy.querySelectorAll('[id]'), function (child) { child.removeAttribute('id'); });
    each(copy.querySelectorAll('.ig-table-tools'), function (child) { child.parentNode.removeChild(child); });
    each(copy.querySelectorAll('[hidden]'), function (child) { child.hidden = false; });
    return copy;
  }
  /** A small copy of a table: its header and the given rows. */
  function miniCopy(table, rows, keepLinks) {
    var copy = make('table', table.className);
    copy.classList.add('ig-mini');
    if (table.tHead) { copy.appendChild(inert(table.tHead)); }
    var bodyCopy = document.createElement('tbody');
    each(rows, function (row) { bodyCopy.appendChild(inert(row)); });
    copy.appendChild(bodyCopy);
    if (!keepLinks) {
      each(copy.querySelectorAll('a'), function (link) { link.parentNode.replaceChild(make('span', 'ig-ref-plain', link.textContent), link); });
    }
    return copy;
  }
  /** The first rows of a table, with a note when there are more. */
  function excerpt(table, limit, keepLinks) {
    var holder = make('div', 'ig-excerpt');
    var rows = table.tBodies.length ? table.tBodies[0].rows : [];
    holder.appendChild(miniCopy(table, Array.prototype.slice.call(rows, 0, limit), keepLinks));
    if (rows.length > limit) {
      holder.appendChild(make('p', 'ig-excerpt-more', (rows.length - limit) + ' more ' + (rows.length - limit === 1 ? 'row' : 'rows')));
    }
    return holder;
  }
  function definitionTable(section) {
    var tables = section.querySelectorAll(':scope > .ig-table-wrap > table');
    for (var i = 0; i < tables.length; i++) {
      if (!tables[i].classList.contains('ig-annotations')) { return tables[i]; }
    }
    return null;
  }

  /* ---------------------------------------------------------------- fields and components */

  function isFieldRow(row) { return !!row && row.tagName === 'TR' && row.classList.contains('ig-field-row'); }
  function looksLikeField(row) {
    if (!row || row.tagName !== 'TR' || !row.id) { return false; }
    var section = resourceOf(row);
    if (!section || row.parentNode.parentNode.parentNode.parentNode !== section) { return false; }
    var kind = section.getAttribute('data-kind');
    return (kind === 'Segment' || kind === 'Datatype') && !!row.querySelector('td.c-seq') && !!row.querySelector('td.c-name');
  }
  function fieldOf(row) {
    var section = resourceOf(row);
    var owner = describe(section);
    var segment = owner.kind === 'Segment';
    var sequence = text(row.querySelector('td.c-seq'));
    return {
      row: row,
      section: section,
      owner: owner,
      sequence: sequence,
      name: text(row.querySelector('td.c-name')),
      // the parts of a date or time format (YYYY, MM) are numbered like components
      label: segment ? 'Field' : (row.querySelector('td.c-code') ? 'Part' : 'Component'),
      token: owner.code + (segment ? '-' : '.') + sequence
    };
  }
  function mentionOf(field) { return IgExportText.mentionPattern(field.owner.code, field.sequence); }

  /* ---------------------------------------------------------------- previews on links */

  function previewOf(id) {
    var target = document.getElementById(id);
    if (!target) { return null; }
    var card = make('div', 'ig-preview-body');
    var head = make('div', 'ig-preview-head');
    card.appendChild(head);
    if (isFieldRow(target)) {
      var field = fieldOf(target);
      head.appendChild(make('span', 'ig-preview-kind', field.label));
      head.appendChild(make('span', 'ig-preview-code', field.token));
      head.appendChild(make('span', 'ig-preview-name', field.name));
      card.appendChild(miniCopy(target.parentNode.parentNode, [target], false));
      return card;
    }
    var section = target.closest ? target.closest('section') : null;
    if (!section || !section.classList.contains('ig-resource')) { return null; }
    var info = describe(section);
    head.appendChild(make('span', 'ig-preview-kind', info.kindLabel));
    head.appendChild(make('span', 'ig-preview-code', info.code));
    if (info.name) { head.appendChild(make('span', 'ig-preview-name', info.name)); }
    var definition = definitionTable(section);
    if (definition && definition.tBodies.length && definition.tBodies[0].rows.length) {
      card.appendChild(excerpt(definition, 8, false));
    } else {
      var narrative = section.querySelector(':scope > .ig-narrative, :scope > .ig-note .ig-note-body');
      var summary = text(narrative);
      card.appendChild(make('p', 'ig-preview-empty', summary ? (summary.length > 260 ? summary.slice(0, 260) + '…' : summary)
        : 'No table is listed for this ' + (info.kindLabel || 'resource').toLowerCase() + ' in this export.'));
    }
    return card;
  }

  var preview = make('div', 'ig-preview');
  preview.id = 'ig-preview';
  preview.setAttribute('role', 'tooltip');
  preview.hidden = true;
  body.appendChild(preview);
  var previewLink = null;
  var previewTimer = null;
  var canHover = !window.matchMedia || window.matchMedia('(hover: hover)').matches;

  var previewLeave = null;
  function hidePreview() {
    window.clearTimeout(previewTimer);
    window.clearTimeout(previewLeave);
    previewTimer = null;
    previewLeave = null;
    if (previewLink) { previewLink.removeAttribute('aria-describedby'); }
    previewLink = null;
    preview.hidden = true;
    preview.innerHTML = '';
  }
  function showPreview(link) {
    var href = link.getAttribute('href') || '';
    if (href.charAt(0) !== '#') { return; }
    var content = previewOf(href.slice(1));
    if (!content) { return; }
    hidePreview();
    previewLink = link;
    preview.appendChild(content);
    preview.hidden = false;
    link.setAttribute('aria-describedby', 'ig-preview');
    // below the link when there is room, above otherwise, always inside the window
    var anchor = link.getBoundingClientRect();
    var box = preview.getBoundingClientRect();
    var margin = 12;
    var left = Math.min(Math.max(margin, anchor.left), Math.max(margin, window.innerWidth - box.width - margin));
    var below = anchor.bottom + 8;
    var top = below + box.height + margin <= window.innerHeight ? below
      : (anchor.top - 8 - box.height >= margin ? anchor.top - 8 - box.height : Math.max(margin, window.innerHeight - box.height - margin));
    preview.style.left = Math.round(left) + 'px';
    preview.style.top = Math.round(top) + 'px';
  }
  function referenceOf(event) {
    var link = event.target.closest ? event.target.closest('a.ig-ref') : null;
    return link && !link.closest('.ig-preview') ? link : null;
  }
  if (canHover) {
    document.addEventListener('mouseover', function (event) {
      var link = referenceOf(event);
      if (!link) { return; }
      window.clearTimeout(previewLeave);
      if (link === previewLink) { return; }
      window.clearTimeout(previewTimer);
      previewTimer = window.setTimeout(function () {
        showPreview(link);
        // nothing to show for this link: the card of the previous one does not stay under it
        if (previewLink && previewLink !== link) { hidePreview(); }
      }, 280);
    });
    document.addEventListener('mouseout', function (event) {
      var link = referenceOf(event);
      if (!link) { return; }
      if (event.relatedTarget && link.contains(event.relatedTarget)) { return; }
      if (link !== previewLink) { window.clearTimeout(previewTimer); }
      // a moment to reach the card: it stays while the pointer is on it or back on its link
      window.clearTimeout(previewLeave);
      if (previewLink) { previewLeave = window.setTimeout(hidePreview, 160); }
    });
    preview.addEventListener('mouseenter', function () { window.clearTimeout(previewLeave); });
    preview.addEventListener('mouseleave', function () {
      window.clearTimeout(previewLeave);
      previewLeave = window.setTimeout(hidePreview, 160);
    });
  }
  document.addEventListener('focusin', function (event) {
    var link = referenceOf(event);
    // only for keyboard focus: a click also focuses the link, and then the reader is leaving
    var byKeyboard = false;
    try { byKeyboard = !!link && link.matches(':focus-visible'); } catch (e) { /* older browsers do not know the selector */ }
    if (byKeyboard) { showPreview(link); }
  });
  document.addEventListener('focusout', function (event) { if (referenceOf(event)) { hidePreview(); } });
  document.addEventListener('click', function (event) { if (referenceOf(event)) { hidePreview(); } }, true);
  window.addEventListener('scroll', function () { if (!preview.hidden) { hidePreview(); } }, { passive: true, capture: true });

  /* ---------------------------------------------------------------- field detail panel */

  var panel = make('aside', 'ig-panel');
  panel.id = 'ig-panel';
  panel.hidden = true;
  panel.setAttribute('role', 'complementary');
  panel.setAttribute('aria-labelledby', 'ig-panel-name');
  var panelHead = make('div', 'ig-panel-head');
  var panelTitle = make('div', 'ig-panel-title');
  var panelClose = make('button', 'ig-icon-button ig-panel-close', '×');
  panelClose.type = 'button';
  panelClose.setAttribute('aria-label', 'Close the details');
  panelClose.title = 'Close (Esc)';
  panelHead.appendChild(panelTitle);
  panelHead.appendChild(panelClose);
  var panelBody = make('div', 'ig-panel-body');
  panel.appendChild(panelHead);
  panel.appendChild(panelBody);
  body.appendChild(panel);

  var fieldRows = [];
  each(main.querySelectorAll('section.ig-resource[data-kind="Segment"] > .ig-table-wrap > table > tbody > tr[id], section.ig-resource[data-kind="Datatype"] > .ig-table-wrap > table > tbody > tr[id]'), function (row) {
    if (!looksLikeField(row)) { return; }
    row.classList.add('ig-field-row');
    row.tabIndex = -1;
    fieldRows.push(row);
  });
  // one tab stop per table, the arrow keys move between its rows
  function fieldRowsOf(tbody) {
    return Array.prototype.filter.call(tbody.rows, function (row) { return !row.hidden && row.classList.contains('ig-field-row'); });
  }
  function setTabStop(tbody, wanted) {
    var rows = fieldRowsOf(tbody);
    var stop = wanted && rows.indexOf(wanted) !== -1 ? wanted : null;
    each(tbody.rows, function (row) {
      if (!row.classList.contains('ig-field-row')) { return; }
      if (!stop && !row.hidden && row.tabIndex === 0) { stop = row; }
      row.tabIndex = -1;
    });
    stop = stop || rows[0];
    if (stop) { stop.tabIndex = 0; }
  }
  (function () {
    var bodies = [];
    fieldRows.forEach(function (row) { if (bodies.indexOf(row.parentNode) === -1) { bodies.push(row.parentNode); } });
    bodies.forEach(function (tbody) { setTabStop(tbody, null); });
  })();
  main.addEventListener('focusin', function (event) {
    if (event.target.classList && event.target.classList.contains('ig-field-row')) { setTabStop(event.target.parentNode, event.target); }
  });
  // the filter of a table may hide the row that was its tab stop
  main.addEventListener('input', function (event) {
    if (!event.target.classList || !event.target.classList.contains('ig-table-filter')) { return; }
    var wrap = event.target.closest('.ig-table-wrap');
    var table = wrap ? wrap.querySelector('table') : null;
    if (table && table.tBodies.length) { setTabStop(table.tBodies[0], null); }
  });

  function panelSection(title) {
    var section = make('section', 'ig-panel-section');
    section.appendChild(make('h3', 'ig-panel-subhead', title));
    panelBody.appendChild(section);
    return section;
  }
  function miniTable(table, rows) {
    var wrap = make('div', 'ig-panel-table');
    wrap.appendChild(miniCopy(table, rows, true));
    return wrap;
  }
  /** Rows of a table that are about the field: they name it, or (constant values) carry its name. */
  function rowsAbout(table, field, mention) {
    var byName = table.tHead && /^element name$/i.test(text(table.tHead.rows[0].cells[0]));
    var identifiers = [];
    each(table.tHead ? table.tHead.rows[0].cells : [], function (cell, index) { identifiers[index] = /^id$/i.test(text(cell)); });
    var matched = [];
    each(table.tBodies.length ? table.tBodies[0].rows : [], function (row) {
      // cell by cell: the text of a row runs its cells together ("IZ-PID-01PID-1 ...")
      // and identifiers are no mentions: the statement "IZ-PID-3" may be about another field
      var about = byName && text(row.cells[0]) === field.name;
      for (var i = 0; !about && i < row.cells.length; i++) {
        about = !(identifiers[i]) && mention.test(row.cells[i].textContent);
      }
      if (about) { matched.push(row); }
    });
    return matched;
  }

  function describeField(field) {
    var mention = mentionOf(field);
    var row = field.row;
    var found = 0;

    // what the definition row itself says
    var facts = make('dl', 'ig-panel-facts');
    var table = row.parentNode.parentNode;
    each(row.cells, function (cell, index) {
      var header = table.tHead ? text(table.tHead.rows[0].cells[index]) : '';
      if (!header || cell.classList.contains('c-seq') || cell.classList.contains('c-name')) { return; }
      var item = make('div', 'ig-panel-fact');
      item.appendChild(make('dt', '', header));
      var value = make('dd', '');
      if (cell.classList.contains('ig-na')) {
        value.appendChild(make('span', 'ig-panel-none', 'Not applicable'));
      } else if (!text(cell)) {
        value.appendChild(make('span', 'ig-panel-none', 'None'));
      } else {
        each(inert(cell).childNodes, function (child) { value.appendChild(child.cloneNode(true)); });
      }
      item.appendChild(value);
      facts.appendChild(item);
    });
    panelBody.appendChild(facts);

    // every other block of the same segment or datatype that mentions it
    var title = '';
    var caption = null;
    each(field.section.children, function (child) {
      if (child.classList.contains('ig-subhead')) { title = text(child); caption = null; return; }
      if (child.classList.contains('ig-caption')) { caption = child; return; }
      var block = child.classList.contains('ig-table-wrap') ? child.querySelector('table') : null;
      if (!block || block === table) { caption = null; return; }
      var wholeBlock = caption && (mention.test(caption.textContent)
        || (/^slicing$/i.test(title) && text(caption).indexOf(field.sequence + '.') === 0));
      var rows = wholeBlock ? Array.prototype.slice.call(block.tBodies.length ? block.tBodies[0].rows : []) : rowsAbout(block, field, mention);
      if (rows.length) {
        var section = panelSection(title || 'Related');
        found++;
        if (caption) { section.appendChild(inert(caption)); }
        if (block.classList.contains('ig-annotations')) {
          rows.forEach(function (annotation) {
            var entry = make('div', 'ig-panel-prose');
            var where = text(annotation.cells[0]);
            if (!IgExportText.locationPattern(field.owner.code, field.sequence).test(where)) {
              entry.appendChild(make('span', 'ig-panel-where', where));
            }
            each(inert(annotation.cells[1]).childNodes, function (node) { entry.appendChild(node.cloneNode(true)); });
            section.appendChild(entry);
          });
        } else {
          section.appendChild(miniTable(block, rows));
        }
      }
      caption = null;
    });

    // co-constraint tables, in any profile, that have a column for this field
    if (field.owner.kind === 'Segment') {
      var columnName = IgExportText.columnPattern(field.owner.code, field.sequence);
      var places = [];
      each(main.querySelectorAll('.ig-coconstraint'), function (block, index) {
        var path = text(block.querySelector('.ig-caption .ig-caption-code'));
        // the export says which segment flavor the table is for
        if (block.getAttribute('data-segment') !== field.owner.id) { return; }
        var columns = [];
        each(block.querySelectorAll('thead th'), function (cell) { if (columnName.test(text(cell))) { columns.push(text(cell)); } });
        if (!columns.length) { return; }
        if (!block.id) {
          var blockId = 'ig-coconstraint-' + (index + 1);
          while (document.getElementById(blockId)) { blockId += '-x'; }
          block.id = blockId;
        }
        var table = block.querySelector('table');
        places.push({
          block: block, columns: columns, path: path, profile: describe(resourceOf(block)),
          rows: table && table.tBodies.length ? table.tBodies[0].rows.length : 0,
          condition: text(block.querySelector('.ig-condition'))
        });
      });
      if (places.length) {
        var coSection = panelSection('Co-constraints');
        found++;
        var list = make('ul', 'ig-panel-list');
        places.forEach(function (place) {
          var item = document.createElement('li');
          var link = make('a', 'ig-ref', place.profile.code || 'Co-constraint table');
          link.href = '#' + place.block.id;
          item.appendChild(link);
          item.appendChild(document.createTextNode(' has a column ' + place.columns.join(', ') + ' for ' + place.path +
            ' (' + place.rows + (place.rows === 1 ? ' row' : ' rows') + ')'));
          if (place.condition) { item.appendChild(make('span', 'ig-panel-note', place.condition)); }
          list.appendChild(item);
        });
        coSection.appendChild(list);
      }
    }

    // the datatype and value sets the field points to
    each(row.querySelectorAll('td.c-datatype a.ig-ref, td.c-valueset a.ig-ref'), function (link) {
      var target = document.getElementById((link.getAttribute('href') || '').slice(1));
      var section = target && target.closest ? target.closest('section.ig-resource') : null;
      var definition = section ? definitionTable(section) : null;
      if (!section || !definition || !definition.tBodies.length || !definition.tBodies[0].rows.length) { return; }
      var info = describe(section);
      var related = panelSection(info.kindLabel + ' ' + info.code);
      found++;
      if (info.name) { related.appendChild(make('p', 'ig-panel-note', info.name)); }
      var holder = excerpt(definition, 12, true);
      holder.classList.add('ig-panel-table');
      related.appendChild(holder);
      var more = make('a', 'ig-ref ig-panel-more', 'Open ' + info.code);
      more.href = link.getAttribute('href');
      related.appendChild(more);
    });

    if (!found) {
      panelBody.appendChild(make('p', 'ig-panel-empty', 'Nothing else in this guide refers to this ' + field.label.toLowerCase() + '.'));
    }
  }

  var openRow = null;
  var openedByKeyboard = false;
  function closePanel() {
    if (!openRow) { return; }
    var row = openRow;
    // focus goes back to the row the reader is on, unless it never was in the panel
    // (some browsers do not focus a button that is clicked: focus is then on nothing at all)
    var focused = document.activeElement;
    var restore = openedByKeyboard || !focused || focused === body || panel.contains(focused);
    openRow = null;
    row.classList.remove('is-selected');
    panel.hidden = true;
    body.classList.remove('ig-panel-open');
    panelBody.innerHTML = '';
    // without scrolling: the reader may have moved on in the document while the panel was open
    if (restore && document.body.contains(row)) { row.focus({ preventScroll: true }); }
  }
  /** byKeyboard: focus moves into the panel, and comes back to the row when it closes. */
  function openPanel(row, byKeyboard) {
    if (openRow === row) {
      if (byKeyboard) { openedByKeyboard = true; focusPanel(row); }
      return;
    }
    if (openRow) { openRow.classList.remove('is-selected'); }
    hidePreview();
    var field = fieldOf(row);
    openRow = row;
    openedByKeyboard = !!byKeyboard;
    row.classList.add('is-selected');
    panelTitle.innerHTML = '';
    panelTitle.appendChild(make('span', 'ig-panel-kind', field.label + ' of ' + field.owner.kindLabel.toLowerCase() + ' ' + field.owner.code));
    var heading = make('h2', 'ig-panel-name');
    heading.id = 'ig-panel-name';
    heading.appendChild(make('span', 'ig-panel-code', field.token));
    heading.appendChild(document.createTextNode(' ' + field.name));
    panelTitle.appendChild(heading);
    panelBody.innerHTML = '';
    panelBody.scrollTop = 0;
    describeField(field);
    panel.hidden = false;
    body.classList.add('ig-panel-open');
    if (byKeyboard) { focusPanel(row); }
  }
  /**
   * Now, and again when the browser has followed the link: its jump to the row takes focus with it,
   * at a moment that differs between browsers.
   */
  function focusPanel(row) {
    var until = Date.now() + 700;
    var inputs = ['keydown', 'mousedown', 'pointerdown', 'touchstart'];
    function take() { if (openRow === row && Date.now() < until) { panelClose.focus(); } }
    function taken() { window.setTimeout(take, 0); }
    // the reader's next key or press decides where the focus goes, not this
    function stop() {
      until = 0;
      row.removeEventListener('focus', taken);
      inputs.forEach(function (type) { document.removeEventListener(type, stop, true); });
    }
    panelClose.focus();
    window.setTimeout(take, 0);
    row.addEventListener('focus', taken);
    inputs.forEach(function (type) { document.addEventListener(type, stop, true); });
    window.setTimeout(stop, 700);
  }

  main.addEventListener('click', function (event) {
    var row = event.target.closest ? event.target.closest('tr.ig-field-row') : null;
    if (!row || event.target.closest('a, button, input, label')) { return; }
    if (window.getSelection && String(window.getSelection())) { return; }
    if (openRow === row) { closePanel(); } else { openPanel(row, false); }
  });
  main.addEventListener('keydown', function (event) {
    var row = event.target;
    if (!row.classList || !row.classList.contains('ig-field-row')) { return; }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      if (openRow === row) { closePanel(); } else { openPanel(row, true); }
      return;
    }
    var rows = fieldRowsOf(row.parentNode);
    var at = rows.indexOf(row);
    var next = event.key === 'ArrowDown' ? rows[at + 1] : event.key === 'ArrowUp' ? rows[at - 1]
      : event.key === 'Home' ? rows[0] : event.key === 'End' ? rows[rows.length - 1] : null;
    if (next) { event.preventDefault(); next.focus(); }
  });
  panelClose.addEventListener('click', closePanel);
  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') { return; }
    if (!preview.hidden) { hidePreview(); return; }
    if (openRow && document.activeElement !== document.getElementById('ig-search')) { closePanel(); }
  });
  // a link to a field (search result, "where used") opens its details as well
  document.addEventListener('click', function (event) {
    var link = event.target.closest ? event.target.closest('a[href^="#"]') : null;
    var target = link ? document.getElementById(link.getAttribute('href').slice(1)) : null;
    if (target && isFieldRow(target)) { openPanel(target, keyboardInUse); reveal(target); }
  });
  // whether the reader is using the keyboard: browsers do not agree on how a click made with Enter reports itself
  var keyboardInUse = false;
  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Shift' && event.key !== 'Control' && event.key !== 'Alt' && event.key !== 'Meta') { keyboardInUse = true; }
  }, true);
  ['mousedown', 'pointerdown', 'touchstart'].forEach(function (type) {
    document.addEventListener(type, function () { keyboardInUse = false; }, true);
  });
  // a click that no device made: assistive technology activating a link or a row
  document.addEventListener('click', function (event) { if (event.detail === 0) { keyboardInUse = true; } }, true);
  /** After the browser jumped to a row: the jump puts it at the very top, under the bar. */
  function reveal(row) {
    window.setTimeout(function () {
      var box = row.getBoundingClientRect();
      var bar = document.querySelector('.ig-topbar');
      if (box.top < (bar ? bar.offsetHeight : 0) + 8 || box.bottom > window.innerHeight) { row.scrollIntoView({ block: 'center' }); }
    }, 0);
  }
  function openFromHash() {
    var id = window.location.hash.slice(1);
    try { id = decodeURIComponent(id); } catch (e) { /* a hash that is not valid percent-encoding: taken as written */ }
    var target = id ? document.getElementById(id) : null;
    if (target && isFieldRow(target)) { openPanel(target, false); reveal(target); }
  }
  window.addEventListener('hashchange', openFromHash);
  openFromHash();

  var openedForPrint = [];
  window.addEventListener('beforeprint', function () {
    hidePreview();
    each(main.querySelectorAll('details.ig-used:not([open])'), function (details) { details.open = true; openedForPrint.push(details); });
  });
  window.addEventListener('afterprint', function () {
    openedForPrint.forEach(function (details) { details.open = false; });
    openedForPrint = [];
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
  // The terms are looked up in the text as it is written, and the pieces are escaped afterwards:
  // looked up in escaped text, "amp" would match inside the "&amp;" of "Lamp & Light".
  function highlight(text, terms) {
    var lower = text.toLowerCase();
    var ranges = [];
    terms.forEach(function (term) {
      if (!term) { return; }
      for (var at = lower.indexOf(term); at !== -1; at = lower.indexOf(term, at + term.length)) {
        ranges.push([at, at + term.length]);
      }
    });
    ranges.sort(function (a, b) { return a[0] - b[0] || b[1] - a[1]; });
    var html = '';
    var done = 0;
    ranges.forEach(function (range) {
      if (range[1] <= done) { return; }
      var start = Math.max(range[0], done);
      html += escapeHtml(text.slice(done, start)) + '<mark>' + escapeHtml(text.slice(start, range[1])) + '</mark>';
      done = range[1];
    });
    return html + escapeHtml(text.slice(done));
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
  function go(href, byKeyboard) {
    close();
    searchInput.blur();
    var target = document.getElementById(href.slice(1));
    if (!target) { return; }
    window.location.hash = href;
    if (isFieldRow(target)) { openPanel(target, byKeyboard); }
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
      if (item) { event.preventDefault(); go(item.getAttribute('href'), true); }
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
