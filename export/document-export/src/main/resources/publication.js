/* Progressive enhancements for a portable, offline Public web IG.
   Content and ordinary links remain useful with JavaScript disabled. */
(function () {
  'use strict';
  var script = document.currentScript || document.querySelector('script[src$="publication.js"]');
  var siteRoot;
  try { siteRoot = new URL('../../', script.src); } catch (error) { siteRoot = new URL('./', window.location.href); }
  var toArray = function (items) { return Array.prototype.slice.call(items || []); };
  var normalize = function (value) { return String(value || '').replace(/\s+/g, ' ').trim().toLowerCase(); };
  var termsFor = function (value) { return normalize(value).split(' ').filter(function (part) { return part.length > 0; }).slice(0, 12); };
  var matches = function (text, terms) { return terms.every(function (term) { return text.indexOf(term) !== -1; }); };
  var nextId = 0;
  function make(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function statusNode(className) {
    var node = make('span', className || 'sr-only');
    node.setAttribute('role', 'status');
    node.setAttribute('aria-live', 'polite');
    return node;
  }
  function safeSelect(selector, context) {
    try { return (context || document).querySelector(selector); } catch (error) { return null; }
  }
  function nearest(element, selector) {
    while (element && element.nodeType === 1) {
      if (element.matches(selector)) return element;
      element = element.parentElement;
    }
    return null;
  }
  function copyText(value, container, status) {
    var trigger = document.activeElement;
    function fallback() {
      var field = container.querySelector('.copy-fallback');
      if (!field) {
        field = make('textarea', 'copy-fallback');
        field.readOnly = true;
        field.setAttribute('aria-label', 'Text ready to copy');
        container.appendChild(field);
      }
      field.value = value;
      field.focus();
      field.select();
      var copied = false;
      try { copied = document.execCommand('copy'); } catch (error) { /* Selection remains available. */ }
      if (copied) {
        field.remove();
        if (trigger && trigger.focus) trigger.focus();
        status.textContent = 'Copied.';
      } else {
        status.textContent = 'Text selected. Use your usual copy shortcut.';
      }
    }
    status.textContent = '';
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(value).then(function () { status.textContent = 'Copied.'; }).catch(fallback);
    } else fallback();
  }
  function wirePermanentLink() {
    var heading = document.querySelector('.page h1');
    if (!heading || document.querySelector('.permalink-tools')) return;
    var tools = make('div', 'permalink-tools');
    var button = make('button', '', 'Copy permanent link');
    button.type = 'button';
    var status = statusNode();
    button.addEventListener('click', function () { copyText(window.location.href, tools, status); });
    tools.appendChild(button);
    tools.appendChild(status);
    heading.parentNode.insertBefore(tools, heading.nextSibling);
  }
  function focusHash() {
    if (!window.location.hash) return;
    var id;
    try { id = decodeURIComponent(window.location.hash.slice(1)); } catch (error) { return; }
    var target = document.getElementById(id);
    if (!target) return;
    var hiddenRow = nearest(target, 'tr[data-node-id]');
    if (hiddenRow && hiddenRow._publicationReveal) hiddenRow._publicationReveal();
    var parent = target.parentElement;
    while (parent) {
      if (parent.tagName === 'DETAILS') parent.open = true;
      parent = parent.parentElement;
    }
    if (!target.hasAttribute('tabindex') && !/^(A|BUTTON|INPUT|SELECT|TEXTAREA)$/.test(target.tagName)) target.setAttribute('tabindex', '-1');
    target.focus();
    target.scrollIntoView({ block: 'center' });
  }
  function wireToc() {
    var panel = document.querySelector('.toc-panel');
    if (!panel) return;
    toArray(panel.querySelectorAll('a.toc-link')).forEach(function (link) {
      link.addEventListener('click', function (event) { event.stopPropagation(); });
    });
    var expand = document.getElementById('toc-expand-all');
    var collapse = document.getElementById('toc-collapse-all');
    if (expand) expand.addEventListener('click', function () { toArray(panel.querySelectorAll('details')).forEach(function (node) { node.open = true; }); });
    if (collapse) collapse.addEventListener('click', function () { toArray(panel.querySelectorAll('details')).forEach(function (node) { node.open = false; }); });
    var heading = panel.querySelector('.toc-header');
    if (!heading) return;
    var filter = document.getElementById('toc-filter');
    var wrapper;
    if (!filter) {
      wrapper = make('div', 'toc-filter');
      var label = make('label', 'sr-only', 'Filter table of contents');
      label.htmlFor = 'toc-filter';
      filter = make('input');
      filter.id = 'toc-filter';
      filter.type = 'search';
      filter.placeholder = 'Filter contents…';
      filter.autocomplete = 'off';
      wrapper.appendChild(label);
      wrapper.appendChild(filter);
      heading.appendChild(wrapper);
    } else wrapper = filter.parentElement;
    var status = statusNode('toc-filter-status');
    status.hidden = true;
    wrapper.appendChild(status);
    var details = toArray(panel.querySelectorAll('details'));
    var before = null;
    filter.addEventListener('input', function () {
      var terms = termsFor(filter.value);
      if (terms.length && before === null) before = details.map(function (node) { return node.open; });
      toArray(panel.querySelectorAll('.toc-item')).forEach(function (item) {
        item.hidden = terms.length > 0 && !matches(normalize(item.textContent), terms);
      });
      toArray(panel.querySelectorAll('.toc-group')).forEach(function (group) {
        var visible = toArray(group.querySelectorAll('.toc-item')).some(function (item) { return !item.hidden; });
        group.hidden = terms.length > 0 && !visible;
      });
      if (terms.length) details.forEach(function (node) { node.open = true; });
      else if (before) {
        details.forEach(function (node, i) { node.open = before[i]; });
        before = null;
      }
      var count = toArray(panel.querySelectorAll('.toc-item')).filter(function (item) { return !item.hidden; }).length;
      status.textContent = count ? count + ' matching entries' : 'No matching entries';
      status.hidden = !terms.length;
    });
    filter.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') { filter.value = ''; filter.dispatchEvent(new Event('input')); }
    });
  }
  function tableFor(control) {
    var selector = control.getAttribute('data-table-filter') || control.getAttribute('data-table-expand') || control.getAttribute('data-table-collapse') || control.getAttribute('data-table-density');
    if (selector) {
      var table = safeSelect(selector);
      if (table) return table;
    }
    var region = nearest(control, '.table-panel, .section-card');
    return region ? region.querySelector('table') : null;
  }
  function wireTables() {
    toArray(document.querySelectorAll('table.struct')).forEach(function (table) {
      if (table._publicationTable) return;
      table._publicationTable = true;
      if (!table.id) table.id = 'publication-table-' + (++nextId);
      if (!nearest(table.parentElement, '.table-scroll')) {
        var scroll = make('div', 'table-scroll');
        scroll.tabIndex = 0;
        scroll.setAttribute('role', 'region');
        var caption = table.querySelector('caption');
        scroll.setAttribute('aria-label', (caption ? caption.textContent : 'Data table') + ' — scroll horizontally for more columns');
        table.parentNode.insertBefore(scroll, table);
        scroll.appendChild(table);
      }
      var region = nearest(table, '.table-panel, .section-card');
      var toolbar = region ? toArray(region.querySelectorAll('.table-toolbar')).filter(function (node) {
        var input = node.querySelector('[data-table-filter]');
        return !input || tableFor(input) === table;
      })[0] : null;
      if (!toolbar) {
        toolbar = make('div', 'table-toolbar');
        table.parentNode.parentNode.insertBefore(toolbar, table.parentNode);
      }
      var filter = toolbar.querySelector('input[type="search"]');
      if (!filter) {
        filter = make('input');
        filter.type = 'search';
        filter.placeholder = 'Find in this table…';
        filter.setAttribute('aria-label', 'Find rows in this table');
        filter.setAttribute('data-table-filter', '#' + table.id);
        toolbar.insertBefore(filter, toolbar.firstChild);
      } else if (!filter.getAttribute('aria-label') && !filter.id) filter.setAttribute('aria-label', 'Find rows in this table');
      var rows = toArray(table.querySelectorAll('tbody > tr'));
      if (!rows.length) rows = toArray(table.rows).filter(function (row) { return row.parentElement.tagName !== 'THEAD' && row.querySelector('td'); });
      var byId = Object.create(null);
      rows.forEach(function (row) {
        if (row.getAttribute('data-node-id')) byId[row.getAttribute('data-node-id')] = row;
        row._publicationText = normalize(row.textContent);
        row.style.setProperty('--depth', String(Math.max(0, parseInt(row.getAttribute('data-depth'), 10) || 0)));
        var toggle = row.querySelector('.tree-toggle');
        if (toggle) {
          var existingLabel = toggle.getAttribute('aria-label');
          var labelCell = row.querySelector('.tree-label');
          var fallbackLabel = (labelCell ? labelCell.textContent : row.textContent).replace(/^[\s▾▼▶▷▸]+/, '').replace(/\s+/g, ' ').trim();
          row._publicationLabel = existingLabel ? existingLabel.replace(/^(Expand|Collapse)\s+/i, '') : fallbackLabel.slice(0, 100);
        }
      });
      var collapsed = Object.create(null);
      rows.forEach(function (row) {
        var toggle = row.querySelector('.tree-toggle');
        if (toggle && toggle.getAttribute('aria-expanded') === 'false') collapsed[row.getAttribute('data-node-id')] = true;
      });
      function ancestors(row, action) {
        var id = row.getAttribute('data-parent-id');
        var seen = Object.create(null);
        while (id && byId[id] && !seen[id]) {
          seen[id] = true;
          action(byId[id], id);
          id = byId[id].getAttribute('data-parent-id');
        }
      }
      var status = toolbar.querySelector('.table-status');
      if (!status) { status = statusNode('table-status'); toolbar.appendChild(status); }
      function render() {
        var terms = termsFor(filter.value);
        var keep = [];
        if (terms.length) rows.forEach(function (row) {
          if (matches(row._publicationText, terms)) {
            keep.push(row);
            ancestors(row, function (parent) { keep.push(parent); });
          }
        });
        var visible = 0;
        rows.forEach(function (row) {
          var blocked = false;
          if (terms.length) blocked = keep.indexOf(row) === -1;
          else ancestors(row, function (parent, id) { if (collapsed[id]) blocked = true; });
          row.hidden = blocked;
          if (!blocked) visible++;
          var toggle = row.querySelector('.tree-toggle');
          if (toggle) {
            var expanded = terms.length > 0 || !collapsed[row.getAttribute('data-node-id')];
            toggle.setAttribute('aria-expanded', String(expanded));
            var label = row.getAttribute('data-label') || row._publicationLabel || row._publicationText.slice(0, 90);
            toggle.setAttribute('aria-label', (expanded ? 'Collapse ' : 'Expand ') + label);
          }
        });
        status.textContent = visible + ' of ' + rows.length + ' rows';
        if (terms.length && !visible) status.textContent = 'No matching rows';
      }
      rows.forEach(function (row) {
        var toggle = row.querySelector('.tree-toggle');
        if (toggle) toggle.addEventListener('click', function () {
          if (termsFor(filter.value).length) { filter.value = ''; }
          var id = row.getAttribute('data-node-id');
          if (collapsed[id]) delete collapsed[id]; else collapsed[id] = true;
          render();
        });
        row._publicationReveal = function () {
          filter.value = '';
          ancestors(row, function (parent, id) { delete collapsed[id]; });
          render();
        };
      });
      filter.addEventListener('input', render);
      filter.addEventListener('keydown', function (event) { if (event.key === 'Escape') { filter.value = ''; render(); } });
      var tree = rows.some(function (row) { return row.querySelector('.tree-toggle'); });
      function addControl(attribute, text, callback) {
        var button = toolbar.querySelector('[' + attribute + ']');
        if (!button) { button = make('button', '', text); button.type = 'button'; button.setAttribute(attribute, '#' + table.id); toolbar.insertBefore(button, status); }
        button.addEventListener('click', callback);
        return button;
      }
      if (tree) {
        addControl('data-table-expand', 'Expand all', function () { collapsed = Object.create(null); filter.value = ''; render(); });
        addControl('data-table-collapse', 'Collapse all', function () {
          rows.forEach(function (row) { if (row.querySelector('.tree-toggle')) collapsed[row.getAttribute('data-node-id')] = true; });
          filter.value = ''; render();
        });
      }
      var density = addControl('data-table-density', 'Compact rows', function () {
        var dense = !table.classList.contains('is-dense');
        table.classList.toggle('is-dense', dense);
        density.setAttribute('aria-pressed', String(dense));
        try { window.localStorage.setItem('igamt-publication-density', dense ? 'compact' : 'comfortable'); } catch (error) { /* file:// and private browsing may disallow storage. */ }
      });
      var stored;
      try { stored = window.localStorage.getItem('igamt-publication-density'); } catch (error) { /* Comfortable default. */ }
      table.classList.toggle('is-dense', stored === 'compact');
      density.setAttribute('aria-pressed', String(stored === 'compact'));
      render();
    });
    // Support old fixture markup without coupling the new tree-table controls to it.
    [['struct-expand-all', true], ['struct-collapse-all', false]].forEach(function (pair) {
      var control = document.getElementById(pair[0]);
      if (control) control.addEventListener('click', function () { toArray(document.querySelectorAll('#msg-struct-tree details')).forEach(function (node) { node.open = pair[1]; }); });
    });
  }
  function wireExamples() {
    toArray(document.querySelectorAll('.fr-exmsg-body, .standalone-example pre')).forEach(function (body) {
      if (body.previousElementSibling && body.previousElementSibling.classList.contains('example-tools')) return;
      var tools = make('div', 'example-tools');
      var button = make('button', '', 'Copy ER7');
      button.type = 'button';
      var status = statusNode();
      button.addEventListener('click', function () {
        var lines = toArray(body.querySelectorAll('.fr-exmsg-line'));
        var text = lines.length ? lines.map(function (line) { return line.textContent === '\u00a0' ? '' : line.textContent; }).join('\n') : body.textContent;
        copyText(text, tools, status);
      });
      tools.appendChild(status);
      tools.appendChild(button);
      body.parentNode.insertBefore(tools, body);
    });
  }
  function highlight(parent, value, terms) {
    var text = String(value || '');
    var lower = text.toLowerCase();
    var at = 0;
    while (at < text.length) {
      var best = -1;
      var term = '';
      terms.forEach(function (part) { var index = lower.indexOf(part, at); if (index !== -1 && (best === -1 || index < best)) { best = index; term = part; } });
      if (best === -1) { parent.appendChild(document.createTextNode(text.slice(at))); break; }
      if (best > at) parent.appendChild(document.createTextNode(text.slice(at, best)));
      parent.appendChild(make('mark', '', text.slice(best, best + term.length)));
      at = best + term.length;
    }
  }
  function typeClass(type) {
    var normalized = normalize(type);
    if (normalized.indexOf('profile') !== -1) return 'badge-profile';
    if (normalized.indexOf('segment') !== -1) return 'badge-seg';
    if (normalized.indexOf('datatype') !== -1) return 'badge-dt';
    if (normalized.indexOf('value') !== -1 || normalized.indexOf('code') !== -1) return 'badge-vs';
    if (normalized.indexOf('example') !== -1) return 'badge-comp';
    return 'badge-nar';
  }
  function resultUrl(value) {
    try {
      var url = new URL(String(value || ''), siteRoot);
      if (url.protocol !== siteRoot.protocol || url.origin !== siteRoot.origin || url.pathname.indexOf(siteRoot.pathname) !== 0) return null;
      return url.href;
    } catch (error) { return null; }
  }
  function wireGlobalSearch() {
    var header = document.querySelector('.app-header');
    if (!header) return;
    var input = document.getElementById('publication-search');
    var form;
    if (input) form = nearest(input, '.publication-search') || input.parentElement;
    else {
      form = make('form', 'publication-search');
      form.setAttribute('role', 'search');
      var label = make('label', 'sr-only', 'Search this guide');
      label.htmlFor = 'publication-search';
      input = make('input');
      input.id = 'publication-search';
      input.type = 'search';
      input.placeholder = 'Search this guide…';
      input.autocomplete = 'off';
      form.appendChild(label);
      form.appendChild(input);
      var shortcut = make('kbd', 'search-shortcut', /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘ K' : 'Ctrl K');
      shortcut.setAttribute('aria-hidden', 'true');
      form.appendChild(shortcut);
      var badge = header.querySelector('.app-badge');
      header.insertBefore(form, badge || null);
    }
    input.setAttribute('aria-expanded', 'false');
    input.setAttribute('aria-controls', 'publication-search-results');
    var results = make('section', 'search-results');
    results.id = 'publication-search-results';
    results.hidden = true;
    results.setAttribute('aria-label', 'Search results');
    var resultHeader = make('div', 'search-results-header');
    var status = statusNode('search-count');
    var close = make('button', '', 'Close');
    close.type = 'button';
    close.setAttribute('aria-label', 'Close search results');
    resultHeader.appendChild(status);
    resultHeader.appendChild(close);
    results.appendChild(resultHeader);
    var list = make('ol', 'search-results-list');
    results.appendChild(list);
    var empty = make('p', 'search-results-empty');
    empty.hidden = true;
    results.appendChild(empty);
    form.appendChild(results);
    function dismiss() { results.hidden = true; input.setAttribute('aria-expanded', 'false'); }
    function search() {
      var terms = termsFor(input.value);
      while (list.firstChild) list.removeChild(list.firstChild);
      if (!terms.length) { dismiss(); return; }
      var index = Array.isArray(window.PUBLICATION_SEARCH_INDEX) ? window.PUBLICATION_SEARCH_INDEX : [];
      var scored = [];
      index.forEach(function (entry) {
        var title = normalize(entry.title);
        var type = normalize(entry.type);
        var text = normalize(entry.text);
        var url = resultUrl(entry.url);
        if (!url || !matches(title + ' ' + type + ' ' + text, terms)) return;
        var score = 0;
        terms.forEach(function (term) { if (title.indexOf(term) !== -1) score += 12; if (type.indexOf(term) !== -1) score += 3; });
        if (title === normalize(input.value)) score += 30;
        scored.push({ entry: entry, url: url, score: score });
      });
      scored.sort(function (a, b) { return b.score - a.score || String(a.entry.title).localeCompare(String(b.entry.title)); });
      scored.slice(0, 25).forEach(function (result) {
        var item = make('li', 'search-result');
        var link = make('a');
        link.href = result.url;
        var title = make('span', 'search-result-title');
        var text = make('span');
        highlight(text, result.entry.title || 'Untitled', terms);
        title.appendChild(text);
        title.appendChild(make('span', 'type-badge ' + typeClass(result.entry.type), result.entry.type || 'Narrative'));
        link.appendChild(title);
        var content = String(result.entry.text || '').replace(/\s+/g, ' ');
        var lower = content.toLowerCase();
        var first = -1;
        terms.forEach(function (term) { var position = lower.indexOf(term); if (position !== -1 && (first === -1 || position < first)) first = position; });
        var start = Math.max(0, first - 55);
        var snippet = (start ? '…' : '') + content.slice(start, start + 170) + (content.length > start + 170 ? '…' : '');
        var description = make('span', 'search-result-text');
        highlight(description, snippet, terms);
        link.appendChild(description);
        item.appendChild(link);
        list.appendChild(item);
      });
      status.textContent = scored.length ? (scored.length > 25 ? 'Top 25 of ' + scored.length : scored.length) + (scored.length === 1 ? ' result' : ' results') : 'No results';
      empty.hidden = scored.length > 0;
      empty.textContent = index.length ? 'No results for “' + input.value.trim() + '”. Try a section title, artifact name, or message term.' : 'This publication has no searchable entries.';
      results.hidden = false;
      input.setAttribute('aria-expanded', 'true');
    }
    var timer;
    input.addEventListener('input', function () { window.clearTimeout(timer); timer = window.setTimeout(search, 120); });
    input.addEventListener('focus', function () { if (termsFor(input.value).length) search(); });
    input.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') { dismiss(); event.stopPropagation(); }
      if (event.key === 'ArrowDown' && !results.hidden) {
        var first = list.querySelector('a');
        if (first) { event.preventDefault(); first.focus(); }
      }
    });
    form.addEventListener('submit', function (event) { event.preventDefault(); search(); });
    close.addEventListener('click', function () { dismiss(); input.focus(); dismiss(); });
    results.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') { event.preventDefault(); input.focus(); dismiss(); }
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        var links = toArray(list.querySelectorAll('a'));
        var i = links.indexOf(document.activeElement);
        if (i !== -1) { event.preventDefault(); if (event.key === 'ArrowUp' && i === 0) input.focus(); else links[Math.max(0, Math.min(links.length - 1, i + (event.key === 'ArrowDown' ? 1 : -1)))].focus(); }
      }
    });
    document.addEventListener('click', function (event) { if (!form.contains(event.target)) dismiss(); });
    document.addEventListener('keydown', function (event) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); input.focus(); }
      else if (event.key === '/' && !/^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName) && !event.target.isContentEditable) { event.preventDefault(); input.focus(); }
    });
  }
  function init() {
    wireToc();
    wireTables();
    wireExamples();
    wirePermanentLink();
    wireGlobalSearch();
    window.addEventListener('hashchange', focusHash);
    focusHash();
    // Scroll only the sidebar container; do not move the content on every page load.
    var active = document.querySelector('.toc-panel a[aria-current="page"]');
    var scroll = document.querySelector('.toc-scroll');
    if (active && scroll) scroll.scrollTop = Math.max(0, active.offsetTop - scroll.clientHeight / 3);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
