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
        if (panelEl.querySelector('[id]:not(#ig-panel)')) problems.push('copied id inside panel for ' + token);
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
      await page.waitForTimeout(40);
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
  if (ccItems.items.length !== 4) fail('known', 'OBX-3 should list 4 co-constraint tables: ' + JSON.stringify(ccItems.items));
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

  // print: panel and preview never print
  await page.emulateMedia({ media: 'print' });
  const print = await page.evaluate(() => getComputedStyle(document.getElementById('ig-panel')).display);
  if (print !== 'none') fail('known', 'panel visible in print');
  await page.emulateMedia({ media: 'screen' });

  return JSON.stringify({ failures, report }, null, 1);
};
