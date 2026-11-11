// See README.md in this folder.
module.exports = async (page) => {
  // where the four exports are served, and an optional folder (with a trailing slash) for screenshots
  const BASE = 'http://127.0.0.1:8777/';
  const SHOTS = null;
  const shot = (name) => (SHOTS ? page.screenshot({ path: SHOTS + name, type: 'jpeg', quality: 70 }) : null);
  const pages = ['rich-all', 'rich-default', 'derived-diff', 'base-default'];
  const report = {};
  const failures = [];
  const fail = (name, message) => failures.push(name + ': ' + message);

  for (const name of pages) {
    const errors = [];
    const onError = (e) => errors.push(String(e));
    const onConsole = (m) => { if (m.type() === 'error') errors.push(m.text()); };
    page.on('pageerror', onError);
    page.on('console', onConsole);
    await page.setViewportSize({ width: 1360, height: 900 });
    await page.goto(BASE + name + '.html?t=' + Date.now());
    await page.waitForTimeout(300);
    const r = (report[name] = {});

    // ---- where used: recompute the reverse index independently and compare
    const used = await page.evaluate(() => {
      const problems = [];
      const expected = {};
      const sections = document.querySelectorAll('section.ig-resource');
      const ownerOf = (node) => node.closest('section.ig-resource');
      document.querySelectorAll('#ig-content a.ig-ref').forEach((a) => {
        if (a.closest('.ig-used')) return;
        const owner = ownerOf(a);
        if (!owner) return;
        const ownerId = owner.querySelector(':scope > .ig-heading').id;
        const target = document.getElementById(a.getAttribute('href').slice(1));
        if (!target) { problems.push('dangling link ' + a.getAttribute('href')); return; }
        const targetSection = target.closest('section.ig-resource');
        if (!targetSection || target !== targetSection.querySelector(':scope > .ig-heading')) return;
        if (target.id === ownerId) return;
        (expected[target.id] = expected[target.id] || {})[ownerId] = true;
      });
      const actual = {};
      let blocks = 0, rowLinks = 0;
      sections.forEach((section) => {
        const heading = section.querySelector(':scope > .ig-heading');
        const block = section.querySelector(':scope > details.ig-used');
        if (!block) return;
        blocks++;
        const owners = {};
        block.querySelectorAll('.ig-used-entry > a.ig-ref').forEach((a) => {
          const id = a.getAttribute('href').slice(1);
          if (owners[id]) problems.push('owner listed twice in ' + heading.id);
          owners[id] = true;
          const ownerHeading = document.getElementById(id);
          if (!ownerHeading) { problems.push('where-used link without target'); return; }
          const code = ownerHeading.querySelector('.ig-code');
          if (code && code.textContent !== a.textContent) problems.push('label ' + a.textContent + ' != ' + code.textContent);
          if (id === heading.id) problems.push('self reference in ' + heading.id);
        });
        block.querySelectorAll('a.ig-ref-row').forEach((a) => {
          rowLinks++;
          const row = document.getElementById(a.getAttribute('href').slice(1));
          if (!row || row.tagName !== 'TR') { problems.push('row link without row ' + a.getAttribute('href')); return; }
          // the row really points at this resource
          const points = Array.prototype.some.call(row.querySelectorAll('a.ig-ref'), (l) => l.getAttribute('href') === '#' + heading.id);
          if (!points) problems.push('row ' + a.textContent + ' does not reference ' + heading.id);
          const seq = row.querySelector('td.c-seq').textContent.trim();
          if (!new RegExp('[-.]' + seq + '$').test(a.textContent)) problems.push('row label ' + a.textContent + ' vs seq ' + seq);
        });
        // the summary sentence counts what is listed
        const total = block.querySelectorAll('.ig-used-entry').length;
        const sum = (block.querySelector('.ig-used-summary').textContent.match(/\d+/g) || []).reduce((a, b) => a + Number(b), 0);
        if (sum !== total) problems.push('summary ' + sum + ' != ' + total + ' in ' + heading.id);
        actual[heading.id] = owners;
      });
      const keys = new Set(Object.keys(expected).concat(Object.keys(actual)));
      keys.forEach((id) => {
        const e = Object.keys(expected[id] || {}).sort().join(',');
        const a = Object.keys(actual[id] || {}).sort().join(',');
        if (e !== a) problems.push('where-used mismatch for ' + id + ': expected [' + e + '] got [' + a + ']');
      });
      const ids = {};
      let duplicateIds = 0;
      document.querySelectorAll('[id]').forEach((el) => { if (ids[el.id]) duplicateIds++; ids[el.id] = true; });
      if (duplicateIds) problems.push(duplicateIds + ' duplicate ids');
      return { problems: problems.slice(0, 8), problemCount: problems.length, blocks, rowLinks, targets: Object.keys(expected).length };
    });
    r.whereUsed = used;
    if (used.problemCount) fail(name, 'where used: ' + used.problems.join(' | '));
    if (name !== 'base-default' && used.blocks === 0) fail(name, 'no where-used block');

    // ---- field panel: open every field and component row
    const panel = await page.evaluate(() => {
      const problems = [];
      const rows = document.querySelectorAll('tr.ig-field-row');
      const panelEl = document.getElementById('ig-panel');
      let withDetails = 0, empty = 0;
      const titles = {};
      rows.forEach((row) => {
        row.click();
        if (panelEl.hidden) { problems.push('panel did not open for ' + row.id); return; }
        if (!row.classList.contains('is-selected')) problems.push('row not selected ' + row.id);
        if (document.querySelectorAll('tr.is-selected').length !== 1) problems.push('several rows selected at ' + row.id);
        const section = row.closest('section.ig-resource');
        const code = section.querySelector(':scope > .ig-heading .ig-code').textContent;
        const seq = row.querySelector('td.c-seq').textContent.trim();
        const token = code + (section.dataset.kind === 'Segment' ? '-' : '.') + seq;
        const shown = panelEl.querySelector('.ig-panel-code').textContent;
        if (shown !== token) problems.push('title ' + shown + ' != ' + token);
        const nameShown = panelEl.querySelector('.ig-panel-name').textContent.replace(shown, '').trim();
        if (nameShown !== row.querySelector('td.c-name').textContent.replace(/\s+/g, ' ').trim()) problems.push('name mismatch for ' + token);
        if (panelEl.querySelectorAll('.ig-panel-fact').length === 0) problems.push('no facts for ' + token);
        if (panelEl.querySelector('[id]:not(#ig-panel-name)')) problems.push('copied id inside panel for ' + token);
        const sections = panelEl.querySelectorAll('.ig-panel-section').length;
        const emptyNote = !!panelEl.querySelector('.ig-panel-empty');
        if ((sections === 0) !== emptyNote) problems.push('empty note inconsistent for ' + token);
        sections ? withDetails++ : empty++;
        panelEl.querySelectorAll('.ig-panel-subhead').forEach((h) => { titles[h.textContent.replace(/ [A-Z0-9_]+$/, ' *')] = (titles[h.textContent.replace(/ [A-Z0-9_]+$/, ' *')] || 0) + 1; });
        // every link in the panel resolves
        panelEl.querySelectorAll('a[href^="#"]').forEach((a) => { if (!document.getElementById(a.getAttribute('href').slice(1))) problems.push('dead panel link ' + a.getAttribute('href') + ' for ' + token); });
      });
      // clicking the open row again closes it
      const last = rows[rows.length - 1];
      if (last) { last.click(); if (!panelEl.hidden) problems.push('second click did not close'); }
      return { rows: rows.length, withDetails, empty, titles, problems: problems.slice(0, 8), problemCount: problems.length };
    });
    r.panel = panel;
    if (panel.problemCount) fail(name, 'panel: ' + panel.problems.join(' | '));

    // every row with a sequence and a name in a segment or datatype is clickable
    const coverage = await page.evaluate(() => {
      let candidates = 0;
      document.querySelectorAll('section.ig-resource[data-kind="Segment"], section.ig-resource[data-kind="Datatype"]').forEach((s) => {
        s.querySelectorAll(':scope > .ig-table-wrap > table > tbody > tr').forEach((row) => {
          if (row.querySelector('td.c-seq') && row.querySelector('td.c-name') && row.querySelector('td.c-seq').textContent.trim()) candidates++;
        });
      });
      return { candidates, clickable: document.querySelectorAll('tr.ig-field-row').length };
    });
    r.coverage = coverage;
    if (coverage.candidates !== coverage.clickable) fail(name, 'clickable rows ' + coverage.clickable + ' of ' + coverage.candidates);

    // ---- previews: hover a sample of links of every kind
    const sample = await page.evaluate(() => {
      const picked = [];
      const seenKind = {};
      const links = document.querySelectorAll('#ig-content a.ig-ref');
      for (const a of links) {
        const target = document.getElementById(a.getAttribute('href').slice(1));
        if (!target) continue;
        const kind = target.tagName === 'TR' ? 'row' : (target.closest('section') ? target.closest('section').dataset.kind : 'x');
        const where = a.closest('.ig-used') ? 'used' : a.closest('.ig-grid') ? 'grid' : a.closest('.ig-structure') ? 'structure' : 'table';
        const key = kind + '/' + where;
        seenKind[key] = (seenKind[key] || 0) + 1;
        if (seenKind[key] <= 2) { a.setAttribute('data-test-ref', picked.length); picked.push(key); }
      }
      return { picked, total: links.length };
    });
    r.previews = { total: sample.total, hovered: sample.picked.length, kinds: Array.from(new Set(sample.picked)) };
    for (let i = 0; i < sample.picked.length; i++) {
      const link = page.locator('[data-test-ref="' + i + '"]');
      await link.evaluate((el) => { el.closest('details') && (el.closest('details').open = true); el.scrollIntoView({ block: 'center' }); });
      await page.waitForTimeout(60);
      await link.hover();
      await page.waitForTimeout(380);
      // sections that render late can move the link away from under the pointer (and their
      // scroll closes the card): once the page has settled, the pointer goes back on the link
      if (await page.evaluate(() => document.getElementById('ig-preview').hidden)) {
        await page.mouse.move(5, 300);
        await link.hover();
        await page.waitForTimeout(450);
      }
      const state = await page.evaluate((i) => {
        const card = document.getElementById('ig-preview');
        const link = document.querySelector('[data-test-ref="' + i + '"]');
        const box = card.getBoundingClientRect();
        const target = document.getElementById(link.getAttribute('href').slice(1));
        const expectedCode = target.tagName === 'TR' ? null : target.querySelector('.ig-code') && target.querySelector('.ig-code').textContent;
        return {
          hidden: card.hidden,
          code: card.querySelector('.ig-preview-code') ? card.querySelector('.ig-preview-code').textContent : null,
          expectedCode,
          hasBody: !!card.querySelector('table.ig-mini, .ig-preview-empty'),
          inside: box.left >= 0 && box.top >= 0 && box.right <= window.innerWidth + 1 && box.bottom <= window.innerHeight + 1,
          links: card.querySelectorAll('a').length,
          describedBy: link.getAttribute('aria-describedby')
        };
      }, i);
      if (state.hidden) fail(name, 'preview hidden for ' + sample.picked[i]);
      else {
        if (state.expectedCode && state.code !== state.expectedCode) fail(name, 'preview code ' + state.code + ' != ' + state.expectedCode);
        if (!state.hasBody) fail(name, 'preview without body for ' + sample.picked[i]);
        if (!state.inside) fail(name, 'preview outside window for ' + sample.picked[i]);
        if (state.links) fail(name, 'preview contains links');
        if (state.describedBy !== 'ig-preview') fail(name, 'aria-describedby missing');
      }
      if (i === 0 && name === 'rich-all') await shot('f-preview.jpg');
      await page.mouse.move(5, 300);
      await page.waitForTimeout(260);
      const gone = await page.evaluate(() => document.getElementById('ig-preview').hidden);
      if (!gone) fail(name, 'preview stayed after leaving ' + sample.picked[i]);
    }

    // ---- interactions
    const firstRow = page.locator('tr.ig-field-row').first();
    if (await firstRow.count()) {
      // a link inside a row navigates and does not open the panel
      const inner = await page.evaluate(() => {
        const row = Array.prototype.find.call(document.querySelectorAll('tr.ig-field-row'), (r) => r.querySelector('a.ig-ref'));
        if (!row) return null;
        row.querySelector('a.ig-ref').click();
        return { hidden: document.getElementById('ig-panel').hidden, hash: location.hash, expected: row.querySelector('a.ig-ref').getAttribute('href') };
      });
      if (inner && (!inner.hidden || inner.hash !== inner.expected)) fail(name, 'link inside a row opened the panel or did not navigate');

      // keyboard: Enter opens and moves focus to the close button, Escape closes and returns focus
      await firstRow.evaluate((el) => el.scrollIntoView({ block: 'center' }));
      await firstRow.focus();
      await page.keyboard.press('Enter');
      let k = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, focus: document.activeElement.className }));
      if (k.hidden || k.focus.indexOf('ig-panel-close') === -1) fail(name, 'keyboard open failed: ' + JSON.stringify(k));
      await page.keyboard.press('Escape');
      k = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, focusIsRow: document.activeElement.classList.contains('ig-field-row') }));
      if (!k.hidden || !k.focusIsRow) fail(name, 'keyboard close failed: ' + JSON.stringify(k));

      // real mouse click, then the close button
      await firstRow.locator('td.c-name').click();
      if (await page.evaluate(() => document.getElementById('ig-panel').hidden)) fail(name, 'mouse click did not open');
      await page.locator('.ig-panel-close').click();
      if (!(await page.evaluate(() => document.getElementById('ig-panel').hidden))) fail(name, 'close button did not close');

      // opened with the mouse, left from inside the panel: focus goes back to the row, not to the top of the page
      await firstRow.locator('td.c-name').click();
      await page.locator('.ig-panel-close').focus();
      await page.keyboard.press('Escape');
      k = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, focusIsRow: document.activeElement.classList.contains('ig-field-row') }));
      if (!k.hidden || !k.focusIsRow) fail(name, 'focus lost after closing a panel opened by mouse: ' + JSON.stringify(k));

      // one tab stop per table, arrow keys between rows, honest semantics
      const stops = await page.evaluate(() => {
        const problems = [];
        const bodies = new Set();
        document.querySelectorAll('tr.ig-field-row').forEach((row) => {
          bodies.add(row.parentNode);
          if (row.hasAttribute('aria-haspopup') || row.hasAttribute('title')) problems.push('row ' + row.id + ' has aria-haspopup or title');
        });
        bodies.forEach((tbody) => {
          const zero = Array.prototype.filter.call(tbody.rows, (row) => row.classList.contains('ig-field-row') && row.tabIndex === 0);
          if (zero.length !== 1) problems.push(zero.length + ' tab stops in the table of ' + tbody.rows[0].id);
        });
        const panel = document.getElementById('ig-panel');
        if (panel.getAttribute('role') !== 'complementary' || panel.getAttribute('aria-labelledby') !== 'ig-panel-name') problems.push('panel role or label');
        return { problems: problems.slice(0, 5), count: problems.length, tables: bodies.size, stops: document.querySelectorAll('tr.ig-field-row[tabindex="0"]').length };
      });
      if (stops.count || stops.stops !== stops.tables) fail(name, 'tab stops: ' + JSON.stringify(stops));
      r.tabStops = stops.stops;
      const arrows = await page.evaluate(() => {
        const tbody = Array.prototype.map.call(document.querySelectorAll('tr.ig-field-row'), (row) => row.parentNode)
          .find((body) => body.querySelectorAll('tr.ig-field-row').length > 2);
        if (!tbody) return null;
        const rows = tbody.querySelectorAll('tr.ig-field-row');
        rows[0].focus();
        const press = (key) => document.activeElement.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        press('ArrowDown');
        const down = document.activeElement === rows[1] && rows[1].tabIndex === 0 && rows[0].tabIndex === -1;
        press('End');
        const end = document.activeElement === rows[rows.length - 1];
        press('ArrowDown');
        const stays = document.activeElement === rows[rows.length - 1];
        press('Home');
        const home = document.activeElement === rows[0] && rows[0].tabIndex === 0;
        return { down, end, stays, home, hidden: document.getElementById('ig-panel').hidden };
      });
      if (arrows && !(arrows.down && arrows.end && arrows.stays && arrows.home && arrows.hidden)) fail(name, 'arrow keys: ' + JSON.stringify(arrows));

      // deep link to a field opens its details
      const rowId = await firstRow.getAttribute('id');
      await page.goto(BASE + name + '.html?t=' + Date.now() + '#' + rowId);
      await page.waitForTimeout(300);
      const deep = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, selected: (document.querySelector('tr.is-selected') || {}).id }));
      if (deep.hidden || deep.selected !== rowId) fail(name, 'deep link did not open the panel');
      await page.keyboard.press('Escape');
    }
    if (errors.length) fail(name, 'console errors: ' + errors.slice(0, 3).join(' | '));
    r.errors = errors.length;
    page.off('pageerror', onError);
    page.off('console', onConsole);
  }

  // ---- known content of the rich guide
  await page.goto(BASE + 'rich-all.html?t=' + Date.now());
  await page.waitForTimeout(300);
  const known = await page.evaluate(() => {
    const out = {};
    const open = (code, seq) => {
      const section = Array.prototype.find.call(document.querySelectorAll('section.ig-resource'), (s) => {
        const c = s.querySelector(':scope > .ig-heading .ig-code');
        return c && c.textContent === code;
      });
      const row = Array.prototype.find.call(section.querySelectorAll('tr.ig-field-row'), (r) => r.querySelector('td.c-seq').textContent.trim() === String(seq));
      row.click();
      const panel = document.getElementById('ig-panel');
      const sections = {};
      panel.querySelectorAll('.ig-panel-section').forEach((s) => { sections[s.querySelector('.ig-panel-subhead').textContent] = s.textContent.replace(/\s+/g, ' '); });
      return { facts: panel.querySelector('.ig-panel-facts').textContent.replace(/\s+/g, ' '), sections };
    };
    out.pid3 = open('PID_IZ', 3);
    out.pid1 = open('PID_IZ', 1);
    out.pid5 = open('PID_IZ', 5);
    out.pid24 = open('PID_IZ', 24);
    out.pid25 = open('PID_IZ', 25);
    out.pid10 = open('PID_IZ', 10);
    out.obx5 = open('OBX_IZ', 5);
    out.obx3 = open('OBX_IZ', 3);
    out.cx1 = open('CX_IZ', 1);
    out.cx4 = open('CX_IZ', 4);
    out.cx5 = open('CX_IZ', 5);
    document.querySelector('.ig-panel-close').click();
    return out;
  });
  const has = (label, value, text) => { if (!value || value.indexOf(text) === -1) fail('known', label + ' should contain "' + text + '" but is ' + (value ? '"' + value.slice(0, 160) + '"' : 'missing')); };
  const lacks = (label, obj, key) => { if (obj.sections[key]) fail('known', label + ' should not have section ' + key + ': ' + obj.sections[key].slice(0, 120)); };
  has('PID-3 comment', known.pid3.sections['Comments'], 'At least one identifier');
  has('PID-3 definition', known.pid3.sections['Definition texts'], 'list of identi');
  has('PID-3 slicing', known.pid3.sections['Slicing'], 'CX_SR');
  has('PID-3 datatype', known.pid3.sections['Data type CX_IZ'], 'ID Number');
  lacks('PID-3', known.pid3, 'Constant Values');
  has('PID-1 constant', known.pid1.sections['Constant Values'], '1');
  has('PID-1 conformance', known.pid1.sections['Conformance Statements'], 'IZ-PID-01');
  if (known.pid1.sections['Value Set Bindings']) fail('known', 'PID-1 picked up bindings of PID-10..19: ' + known.pid1.sections['Value Set Bindings'].slice(0, 120));
  if (known.pid1.sections['Comments']) fail('known', 'PID-1 picked up comments of other fields');
  has('PID-5 slicing', known.pid5.sections['Slicing'], 'XPN_M');
  if (known.pid5.sections['Slicing'] && known.pid5.sections['Slicing'].indexOf('CX_SR') !== -1) fail('known', 'PID-5 slicing shows the PID-3 slices');
  has('PID-24 mentioned by predicate', known.pid24.sections['Conditional Predicates'], 'PID-25');
  has('PID-25 predicate', known.pid25.sections['Conditional Predicates'], 'Multiple Birth Indicator');
  has('PID-10 binding', known.pid10.sections['Value Set Bindings'], 'HL70005');
  has('PID-10 value set', known.pid10.sections['Value set HL70005'], '');
  has('OBX-5 dynamic mapping', known.obx5.sections['Dynamic mapping'], 'OBX-2');
  has('OBX-5 comment', known.obx5.sections['Comments'], 'determined by OBX-2');
  has('OBX-3 co-constraints', known.obx3.sections['Co-constraints'], 'Z22');
  has('OBX-3 co-constraints column', known.obx3.sections['Co-constraints'], 'OBX-3 (code)');
  has('CX.1 comment', known.cx1.sections['Comments'], 'without check d');
  has('CX.4 binding', known.cx4.sections['Value Set Bindings'], 'HL70363');
  has('CX.5 conformance', known.cx5.sections['Conformance Statements'], 'IZ-CX-01');
  has('PID-3 facts usage', known.pid3.facts, 'Usage');

  // co-constraint link scrolls to the table
  await page.evaluate(() => {
    const section = Array.prototype.find.call(document.querySelectorAll('section.ig-resource'), (s) => (s.querySelector(':scope > .ig-heading .ig-code') || {}).textContent === 'OBX_IZ');
    Array.prototype.find.call(section.querySelectorAll('tr.ig-field-row'), (r) => r.querySelector('td.c-seq').textContent.trim() === '3').click();
  });
  const ccItems = await page.evaluate(() => ({
    items: Array.prototype.map.call(document.querySelectorAll('#ig-panel .ig-panel-list li'), (li) => li.textContent.replace(/\s+/g, ' ')),
    overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    bodyDisplay: getComputedStyle(document.body).display
  }));
  if (ccItems.items.length !== 2) fail('known', 'OBX_IZ-3 should list the 2 co-constraint tables of Z22: ' + JSON.stringify(ccItems.items));
  if (new Set(ccItems.items).size !== ccItems.items.length) fail('known', 'co-constraint entries cannot be told apart: ' + JSON.stringify(ccItems.items));
  if (!ccItems.items.some((t) => t.indexOf('13 rows') !== -1) || !ccItems.items.some((t) => t.indexOf('Applies when') !== -1)) fail('known', 'co-constraint entries lack row count or condition: ' + JSON.stringify(ccItems.items));
  if (ccItems.overflow > 0 || ccItems.bodyDisplay !== 'block') fail('known', 'open panel changes the page layout: ' + JSON.stringify({ overflow: ccItems.overflow, body: ccItems.bodyDisplay }));
  await page.locator('.ig-panel-list a.ig-ref').first().click();
  await page.waitForTimeout(300);
  const cc = await page.evaluate(() => { const b = document.getElementById(location.hash.slice(1)); return b ? { cls: b.className, top: Math.round(b.getBoundingClientRect().top) } : null; });
  if (!cc || cc.cls.indexOf('ig-coconstraint') === -1 || cc.top < 0 || cc.top > 400) fail('known', 'co-constraint link did not scroll to the table: ' + JSON.stringify(cc));
  await shot('f-panel-cc.jpg');

  // search result for a field opens the panel
  await page.fill('#ig-search', 'PID-3');
  await page.waitForTimeout(250);
  const firstResult = await page.evaluate(() => { const a = document.querySelector('#ig-search-results a.ig-result'); return a ? a.textContent.replace(/\s+/g, ' ') : null; });
  if (!firstResult || firstResult.indexOf('PID_IZ-3') === -1) fail('known', 'search for PID-3 first result: ' + firstResult);
  await page.keyboard.press('Enter');
  await page.waitForTimeout(300);
  const viaSearch = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, code: (document.querySelector('.ig-panel-code') || {}).textContent }));
  if (viaSearch.hidden || viaSearch.code !== 'PID_IZ-3') fail('known', 'search did not open the panel: ' + JSON.stringify(viaSearch) + ' result=' + firstResult);
  await shot('f-panel.jpg');

  // where used: open, screenshot, row link opens the panel of that row
  await page.keyboard.press('Escape');
  await page.evaluate(() => {
    const section = Array.prototype.find.call(document.querySelectorAll('section.ig-resource'), (s) => (s.querySelector(':scope > .ig-heading .ig-code') || {}).textContent === 'CX_IZ');
    const d = section.querySelector('details.ig-used'); d.open = true; section.scrollIntoView(); window.scrollBy(0, -70);
  });
  await page.waitForTimeout(200);
  await shot('f-used.jpg');
  const usedText = await page.evaluate(() => {
    const section = Array.prototype.find.call(document.querySelectorAll('section.ig-resource'), (s) => (s.querySelector(':scope > .ig-heading .ig-code') || {}).textContent === 'CX_IZ');
    const d = section.querySelector('details.ig-used');
    d.querySelector('a.ig-ref-row').click();
    return d.textContent.replace(/\s+/g, ' ');
  });
  has('CX_IZ where used', usedText, 'PID_IZ-3');
  await page.waitForTimeout(300);
  const viaUsed = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, code: (document.querySelector('.ig-panel-code') || {}).textContent }));
  if (viaUsed.hidden) fail('known', 'row link from where-used did not open the panel');
  report.usedRowLinkOpens = viaUsed.code;

  // dark theme and phone width
  await page.click('#ig-theme');
  await shot('f-panel-dark.jpg');
  await page.click('#ig-theme');
  await page.setViewportSize({ width: 390, height: 800 });
  await page.waitForTimeout(200);
  const phone = await page.evaluate(() => { const b = document.getElementById('ig-panel').getBoundingClientRect(); return { left: Math.round(b.left), width: Math.round(b.width), overflow: document.documentElement.scrollWidth - window.innerWidth }; });
  if (phone.left !== 0 || phone.width !== 390 || phone.overflow > 0) fail('known', 'phone panel layout ' + JSON.stringify(phone));
  await shot('f-panel-phone.jpg');
  await page.setViewportSize({ width: 1360, height: 900 });

  // ---- findings of the reviews, each with its own check
  await page.keyboard.press('Escape');
  await page.evaluate(() => document.querySelector('.ig-panel-close').click());
  const sectionOf = "(code) => Array.prototype.find.call(document.querySelectorAll('section.ig-resource'), (s) => (s.querySelector(':scope > .ig-heading .ig-code') || {}).textContent === code)";

  // a field link used with the keyboard moves focus into the panel, and closing gives it back to the link
  await page.evaluate((find) => {
    const section = eval(find)('CX_IZ');
    const d = section.querySelector('details.ig-used'); d.open = true;
    const link = d.querySelector('a.ig-ref-row'); link.scrollIntoView({ block: 'center' }); link.focus();
  }, sectionOf);
  await page.keyboard.press('Enter');
  await page.waitForTimeout(200);
  let focus = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, inPanel: document.getElementById('ig-panel').contains(document.activeElement) }));
  if (focus.hidden || !focus.inPanel) fail('known', 'keyboard use of a where-used link does not focus the panel: ' + JSON.stringify(focus));
  // the reader followed the link: closing leaves them on the row, without scrolling back to the link
  const before = await page.evaluate(() => window.scrollY);
  await page.keyboard.press('Escape');
  await page.waitForTimeout(150);
  focus = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, onRow: document.activeElement.id, hash: decodeURIComponent(location.hash.slice(1)), scrollY: window.scrollY }));
  if (!focus.hidden || !focus.onRow || focus.onRow !== focus.hash || Math.abs(focus.scrollY - before) > 4) fail('known', 'closing after a where-used link: ' + JSON.stringify(focus) + ' scroll before ' + before);

  // the reader scrolled on while the panel was open: closing does not pull the page back to the row
  for (const way of ['button', 'Escape']) {
    await page.evaluate((find) => {
      const row = eval(find)('PID_IZ').querySelector('tr.ig-field-row');
      row.scrollIntoView({ block: 'center' });
      row.click();
    }, sectionOf);
    await page.waitForTimeout(200);
    await page.mouse.move(400, 500);
    await page.mouse.wheel(0, 4000);
    await page.waitForTimeout(400);
    const away = await page.evaluate(() => window.scrollY);
    if (way === 'button') { await page.locator('.ig-panel-close').click(); } else { await page.locator('.ig-panel-close').focus(); await page.keyboard.press('Escape'); }
    await page.waitForTimeout(300);
    const after = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, scrollY: window.scrollY, onRow: document.activeElement.classList.contains('ig-field-row') }));
    if (!after.hidden || !after.onRow || Math.abs(after.scrollY - away) > 4) fail('known', 'closing by ' + way + ' after scrolling away: ' + JSON.stringify(after) + ', was at ' + away);
  }

  // search with the keyboard: focus in the panel, and the same result opens it again after it was closed
  for (let round = 0; round < 2; round++) {
    await page.fill('#ig-search', 'PID-3');
    await page.waitForTimeout(250);
    await page.keyboard.press('Enter');
    await page.waitForTimeout(250);
    focus = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, inPanel: document.getElementById('ig-panel').contains(document.activeElement), code: (document.querySelector('.ig-panel-code') || {}).textContent }));
    if (focus.hidden || !focus.inPanel || focus.code !== 'PID_IZ-3') fail('known', 'search by keyboard, round ' + (round + 1) + ': ' + JSON.stringify(focus));
    await page.keyboard.press('Escape');
    focus = await page.evaluate(() => ({ hidden: document.getElementById('ig-panel').hidden, onRow: document.activeElement.classList.contains('ig-field-row') }));
    if (!focus.hidden || !focus.onRow) fail('known', 'closing after a search, round ' + (round + 1) + ': ' + JSON.stringify(focus));
  }

  // co-constraints only from the profiles that use this flavor of the segment
  const flavors = await page.evaluate((find) => {
    const items = (code) => {
      const section = eval(find)(code);
      const row = section && Array.prototype.find.call(section.querySelectorAll('tr.ig-field-row'), (r) => r.querySelector('td.c-seq').textContent.trim() === '3');
      if (!row) return null;
      row.click();
      const id = section.querySelector(':scope > .ig-heading').id;
      const out = Array.prototype.map.call(document.querySelectorAll('#ig-panel .ig-panel-list li'), (li) => {
        const block = document.getElementById(li.querySelector('a').getAttribute('href').slice(1));
        const profile = block.closest('section.ig-resource');
        // the flavor the structure of that profile has for an OBX segment
        const structure = Array.prototype.filter.call(profile.querySelectorAll('table.ig-structure td.c-flavor a.ig-ref'), (a) => a.closest('tr').querySelector('td.c-segment').textContent.trim() === 'OBX');
        return { profile: li.querySelector('a').textContent, uses: structure.length > 0 && structure.every((a) => a.getAttribute('href') === '#' + id) };
      });
      document.querySelector('.ig-panel-close').click();
      return out;
    };
    return { ref: items('OBX_REF_1'), iz: items('OBX_IZ') };
  }, sectionOf);
  if (!flavors.iz || flavors.iz.length !== 2 || flavors.iz.some((x) => !x.uses || x.profile !== 'Z22')) fail('known', 'OBX_IZ-3 co-constraints: ' + JSON.stringify(flavors.iz));
  if (flavors.ref && (flavors.ref.length !== 2 || flavors.ref.some((x) => !x.uses || x.profile !== 'Z22_Refusal'))) fail('known', 'OBX_REF_1-3 lists co-constraints of profiles that do not use it: ' + JSON.stringify(flavors.ref));
  report.coConstraintFlavors = { OBX_IZ: flavors.iz && flavors.iz.length, OBX_REF_1: flavors.ref && flavors.ref.map((x) => x.profile) };

  // a profile whose segment at that path is not linked (left out of the export) lends its tables to no other flavor
  const unlinked = await page.evaluate((find) => {
    const profile = eval(find)('Z22');
    const cell = Array.prototype.find.call(profile.querySelectorAll('table.ig-structure td.c-flavor'), (td) => td.closest('tr').querySelector('td.c-segment').textContent.trim() === 'OBX');
    const saved = cell.innerHTML;
    cell.textContent = cell.textContent;
    const count = (code) => {
      const row = Array.prototype.find.call(eval(find)(code).querySelectorAll('tr.ig-field-row'), (r) => r.querySelector('td.c-seq').textContent.trim() === '3');
      row.click();
      const items = Array.prototype.map.call(document.querySelectorAll('#ig-panel .ig-panel-list li a'), (a) => a.textContent);
      document.querySelector('.ig-panel-close').click();
      return items;
    };
    const out = { iz: count('OBX_IZ'), ref: count('OBX_REF_1') };
    cell.innerHTML = saved;
    return out;
  }, sectionOf);
  if (unlinked.iz.length !== 0 || unlinked.ref.some((code) => code !== 'Z22_Refusal')) fail('known', 'co-constraints of an unlinked segment are credited elsewhere: ' + JSON.stringify(unlinked));

  // the parts of a date format are not called components
  const part = await page.evaluate((find) => {
    const section = eval(find)('DT');
    const row = section && section.querySelector('tr.ig-field-row');
    if (!row) return null;
    row.click();
    const kind = document.querySelector('.ig-panel-kind').textContent;
    document.querySelector('.ig-panel-close').click();
    return kind;
  }, sectionOf);
  if (part !== null && part.indexOf('Part of') !== 0) fail('known', 'a date format row is titled "' + part + '"');

  // a table filtered down to its last row is still shown from its first rows in a preview
  const filtered = await page.evaluate(() => {
    const input = Array.prototype.find.call(document.querySelectorAll('.ig-table-filter'), (box) => {
      const section = box.closest('section.ig-resource');
      return section && document.querySelector('#ig-content a.ig-ref[href="#' + section.querySelector(':scope > .ig-heading').id + '"]');
    });
    if (!input) return null;
    const section = input.closest('section.ig-resource');
    const rows = section.querySelector('table').tBodies[0].rows;
    input.value = rows[rows.length - 1].textContent.trim().split(/\s+/)[0] + ' ' + rows[rows.length - 1].cells[rows[rows.length - 1].cells.length - 1].textContent.trim().split(/\s+/)[0];
    input.dispatchEvent(new Event('input', { bubbles: true }));
    const hiddenRows = Array.prototype.filter.call(rows, (row) => row.hidden).length;
    const id = section.querySelector(':scope > .ig-heading').id;
    const link = Array.prototype.find.call(document.querySelectorAll('#ig-content a.ig-ref[href="#' + id + '"]'), (a) => !a.closest('.ig-used') && a.getClientRects().length);
    link.scrollIntoView({ block: 'center' });
    link.setAttribute('data-e2e', 'filtered');
    return { id, hiddenRows, total: rows.length };
  });
  if (filtered) {
    if (filtered.hiddenRows < 8) fail('known', 'the filter did not hide the first rows: ' + JSON.stringify(filtered));
    await page.locator('a[data-e2e="filtered"]').hover();
    await page.waitForTimeout(450);
    const shown = await page.evaluate(() => {
      const card = document.getElementById('ig-preview');
      const rows = card.querySelectorAll('tbody tr');
      return { hidden: card.hidden, rows: rows.length, visible: Array.prototype.filter.call(rows, (row) => row.getClientRects().length).length };
    });
    if (shown.hidden || shown.rows === 0 || shown.visible !== shown.rows) fail('known', 'preview of a filtered table: ' + JSON.stringify(shown));

    // the card stays while the pointer is on it, and goes when the pointer leaves it
    const box = await page.evaluate(() => { const b = document.getElementById('ig-preview').getBoundingClientRect(); return { x: b.left + b.width / 2, y: b.top + b.height / 2 }; });
    await page.mouse.move(box.x, box.y, { steps: 6 });
    await page.waitForTimeout(400);
    if (await page.evaluate(() => document.getElementById('ig-preview').hidden)) fail('known', 'the preview closes when the pointer moves onto it');
    await page.mouse.move(5, 5, { steps: 4 });
    await page.waitForTimeout(300);
    if (!(await page.evaluate(() => document.getElementById('ig-preview').hidden))) fail('known', 'the preview stays after the pointer left it');
    await page.evaluate(() => { const input = document.querySelector('a[data-e2e="filtered"]'); input.removeAttribute('data-e2e'); });
  }
  report.filteredPreview = filtered;

  // card of a first link, a quick pass over a second link, then away: nothing stays open
  let pair = await page.evaluate(() => {
    const row = Array.prototype.find.call(document.querySelectorAll('tr.ig-field-row'), (r) => r.querySelector('td.c-datatype a.ig-ref') && r.querySelector('td.c-valueset a.ig-ref'));
    if (!row) return null;
    row.setAttribute('data-e2e', 'pair');
    return true;
  });
  if (pair) {
    const first = page.locator('tr[data-e2e="pair"] td.c-datatype a.ig-ref').first();
    const second = page.locator('tr[data-e2e="pair"] td.c-valueset a.ig-ref').first();
    const centre = async (locator) => { const b = await locator.boundingBox(); return { x: b.x + b.width / 2, y: b.y + b.height / 2 }; };
    // sections above render late and move the row: a first hover brings it near, the second one is on the link
    await first.hover();
    await page.waitForTimeout(400);
    await page.mouse.move(5, 5);
    await first.hover();
    await page.waitForTimeout(450);
    if (await page.evaluate(() => document.getElementById('ig-preview').hidden)) fail('known', 'no preview on the first link of the pair');
    pair = { a: await centre(first), b: await centre(second) };
    await page.mouse.move(pair.b.x, pair.b.y);
    await page.waitForTimeout(60);
    await page.mouse.move(pair.b.x, pair.b.y + 260);
    await page.waitForTimeout(500);
    if (!(await page.evaluate(() => document.getElementById('ig-preview').hidden))) fail('known', 'a preview stays open after a quick pass over a second link');
    // and resting on the second link replaces the card
    await page.mouse.move(pair.a.x, pair.a.y, { steps: 3 });
    await page.waitForTimeout(450);
    const firstCode = await page.evaluate(() => (document.querySelector('#ig-preview .ig-preview-code') || {}).textContent);
    await page.mouse.move(pair.b.x, pair.b.y, { steps: 3 });
    await page.waitForTimeout(450);
    const shown2 = await page.evaluate(() => ({ hidden: document.getElementById('ig-preview').hidden, code: (document.querySelector('#ig-preview .ig-preview-code') || {}).textContent }));
    if (shown2.hidden || !shown2.code || shown2.code === firstCode) fail('known', 'moving to a second link does not replace the card: ' + firstCode + ' -> ' + JSON.stringify(shown2));
    await page.mouse.move(5, 5);
    await page.waitForTimeout(300);
    await page.evaluate(() => document.querySelector('tr[data-e2e="pair"]').removeAttribute('data-e2e'));
  }

  // a link to a far field row shows the row, clear of the bar and of the header of its table
  for (const way of ['link', 'load']) {
    await page.goto(BASE + 'rich-all.html?t=' + Date.now() + (way === 'load' ? '#HL7CTD-V2-5-1-1' : ''));
    await page.waitForTimeout(400);
    if (way === 'link') {
      await page.evaluate(() => { const a = document.createElement('a'); a.href = '#HL7CTD-V2-5-1-1'; a.textContent = 'x'; document.getElementById('ig-content').prepend(a); a.click(); });
    }
    await page.waitForTimeout(700);
    const where = await page.evaluate(() => {
      const row = document.getElementById('HL7CTD-V2-5-1-1');
      const box = row.getBoundingClientRect();
      const at = document.elementFromPoint(box.left + 30, box.top + box.height / 2);
      return { top: Math.round(box.top), bottom: Math.round(box.bottom), height: window.innerHeight, uncovered: !!at && row.contains(at), panel: !document.getElementById('ig-panel').hidden };
    });
    if (!where.panel || !where.uncovered || where.top < 52 || where.bottom > where.height) fail('known', 'field row reached by ' + way + ' is not visible: ' + JSON.stringify(where));
  }

  // a hash that is not valid percent-encoding does not stop the page from working
  const hashErrors = [];
  const onHashError = (e) => hashErrors.push(String(e));
  page.on('pageerror', onHashError);
  await page.goto(BASE + 'rich-all.html?t=' + Date.now() + '#%');
  await page.waitForTimeout(300);
  await page.fill('#ig-search', 'PID-3');
  await page.waitForTimeout(250);
  const afterBadHash = await page.evaluate(() => document.querySelectorAll('#ig-search-results a.ig-result').length);
  page.off('pageerror', onHashError);
  if (hashErrors.length || !afterBadHash) fail('known', 'malformed hash: errors ' + JSON.stringify(hashErrors) + ', search results ' + afterBadHash);
  await page.keyboard.press('Escape');
  await page.evaluate(() => { const d = document.querySelector('tr.ig-field-row'); d.click(); });

  // print: panel and preview never print
  await page.emulateMedia({ media: 'print' });
  const print = await page.evaluate(() => getComputedStyle(document.getElementById('ig-panel')).display);
  if (print !== 'none') fail('known', 'panel visible in print');
  await page.emulateMedia({ media: 'screen' });

  return JSON.stringify({ failures, report }, null, 1);
};
