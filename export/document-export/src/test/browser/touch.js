// usage: node touch.js
// The export on emulated touch devices (touch events, no hover, coarse pointer, device viewport):
// iPhone and iPad on the WebKit engine, a phone and a tablet on Chromium. Serves the same folder as
// export-features.e2e.js, with a data type library export as lib.html next to rich-default.html.
// PLAYWRIGHT=<path of a playwright or playwright-core module> when it is not installed next to this file.
const pw = require(process.env.PLAYWRIGHT || 'playwright');
const BASE = 'http://127.0.0.1:8777/';
(async () => {
  const runs = [['webkit', 'iPhone 13'], ['webkit', 'iPad (gen 7)'], ['chromium', 'Pixel 7'], ['chromium', 'Galaxy Tab S4']];
  const out = {};
  for (const [engine, device] of runs) {
    const descriptor = pw.devices[device];
    const browser = await pw[engine].launch();
    const context = await browser.newContext(Object.assign({}, descriptor));
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    const failures = [];
    const fail = (m) => failures.push(m);
    const errors = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
    const r = (out[device + ' / ' + engine + ' ' + browser.version()] = { viewport: descriptor.viewport, failures });
    const tap = async (locator) => {
      let box = null;
      for (let i = 0; i < 6; i++) {
        await locator.evaluate((el) => el.scrollIntoView({ block: 'center' }));
        await page.waitForTimeout(150);
        box = await locator.boundingBox();
        const again = await (async () => { await page.waitForTimeout(100); return locator.boundingBox(); })();
        if (box && again && Math.abs(box.y - again.y) < 1 && box.y > 60 && box.y + box.height < descriptor.viewport.height - 4) { break; }
      }
      await page.touchscreen.tap(box.x + Math.min(box.width / 2, 60), box.y + box.height / 2);
      await page.waitForTimeout(350);
    };
    const state = () => page.evaluate(() => {
      const panel = document.getElementById('ig-panel'); const b = panel.getBoundingClientRect();
      return { panel: !panel.hidden, panelLeft: Math.round(b.left), panelWidth: Math.round(b.width), overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth, preview: !document.getElementById('ig-preview').hidden, code: (document.querySelector('.ig-panel-code') || {}).textContent, hash: location.hash };
    });
    try {
      for (const file of ['rich-default', 'lib']) {
        await page.goto(BASE + file + '.html?t=' + Date.now());
        await page.waitForTimeout(500);
        const media = await page.evaluate(() => ({ hover: matchMedia('(hover: hover)').matches, coarse: matchMedia('(pointer: coarse)').matches, width: innerWidth, touch: 'ontouchstart' in window, overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth }));
        r[file + ' media'] = media;
        if (media.overflow > 0) fail(file + ': page scrolls sideways before anything is opened (' + media.overflow + 'px)');
        const narrow = media.width < 1500;

        // tap a field row: the panel opens, fits, and the page does not scroll sideways
        const row = page.locator('tr.ig-field-row').nth(2);
        await tap(row.locator('td.c-name'));
        let s = await state();
        if (!s.panel) fail(file + ': tap on a row does not open the panel');
        if (s.overflow > 0) fail(file + ': sideways scroll with the panel open (' + s.overflow + 'px)');
        if (s.panelLeft < 0 || s.panelWidth > media.width) fail(file + ': panel does not fit: ' + JSON.stringify(s));
        if (s.preview) fail(file + ': a preview shows after a tap on a row');
        // the content of the panel scrolls by touch (it is its own scroller), the close button is big enough
        const inside = await page.evaluate(() => { const b = document.querySelector('.ig-panel-body'); const c = getComputedStyle(b); const x = document.querySelector('.ig-panel-close').getBoundingClientRect(); return { scrolls: c.overflowY, close: [Math.round(x.width), Math.round(x.height)] }; });
        if (inside.scrolls !== 'auto' && inside.scrolls !== 'scroll') fail(file + ': the panel body does not scroll: ' + inside.scrolls);
        if (inside.close[0] < 44 || inside.close[1] < 44) fail(file + ': close button is small for a finger: ' + inside.close);
        r[file + ' close button'] = inside.close.join('x');
        await page.touchscreen.tap(...(await page.evaluate(() => { const x = document.querySelector('.ig-panel-close').getBoundingClientRect(); return [x.left + x.width / 2, x.top + x.height / 2]; })));
        await page.waitForTimeout(300);
        s = await state();
        if (s.panel) fail(file + ': tap on the close button does not close the panel');

        // tap the same row twice: opens, then closes
        await tap(row.locator('td.c-name'));
        if (!(await state()).panel) fail(file + ': second tap sequence did not open');
        await page.evaluate(() => document.querySelector('.ig-panel-close').click());

        // tap a link inside a row: goes to the resource, no panel, no preview left on screen
        const linkRow = page.locator('tr.ig-field-row:has(td a.ig-ref)').first();
        if (await linkRow.count()) {
          const link = linkRow.locator('td a.ig-ref').first();
          const href = await link.getAttribute('href');
          await tap(link);
          await page.waitForTimeout(400);
          s = await state();
          if (s.hash !== href) fail(file + ': tap on a link in a row did not navigate (' + s.hash + ' vs ' + href + ')');
          if (s.panel) fail(file + ': tap on a link in a row opened the panel');
          if (s.preview) fail(file + ': a preview is left on screen after a tap on a link');
          r[file + ' preview after tap'] = s.preview;
        }

        // where used: tap the summary to open it, tap a field link to open that field
        const used = page.locator('details.ig-used:has(a.ig-ref-row)').first();
        if (await used.count()) {
          await tap(used.locator('summary'));
          if (!(await used.evaluate((d) => d.open))) fail(file + ': tap on "Where used" does not open it');
          const rowLink = used.locator('a.ig-ref-row').first();
          const target = (await rowLink.getAttribute('href')).slice(1);
          await tap(rowLink);
          await page.waitForTimeout(500);
          s = await state();
          const selected = await page.evaluate(() => (document.querySelector('tr.is-selected') || {}).id);
          if (!s.panel || selected !== target) fail(file + ': tap on a field in "Where used" did not open it: ' + JSON.stringify({ s, selected, target }));
          if (s.overflow > 0) fail(file + ': sideways scroll after a where-used link (' + s.overflow + 'px)');
          await page.evaluate(() => document.querySelector('.ig-panel-close').click());
        }

        // search by touch
        await page.locator('#ig-search').tap().catch(() => fail(file + ': the search box cannot be tapped'));
        await page.locator('#ig-search').fill(file === 'lib' ? 'CX_LIB.4' : 'PID-3');
        await page.waitForTimeout(350);
        const result = page.locator('#ig-search-results a.ig-result').first();
        if (!(await result.count())) fail(file + ': search shows no result');
        else {
          const box = await result.boundingBox();
          if (!box || box.x < 0 || box.x + box.width > media.width + 1) fail(file + ': search results do not fit the screen: ' + JSON.stringify(box));
          await result.tap();
          await page.waitForTimeout(600);
          s = await state();
          if (!s.panel) fail(file + ': tap on a search result for a field did not open it');
          await page.evaluate(() => document.querySelector('.ig-panel-close').click());
        }

        // wide tables scroll inside their own box
        const wide = await page.evaluate(() => Array.prototype.filter.call(document.querySelectorAll('.ig-table-wrap'), (w) => w.scrollWidth > w.clientWidth + 2).map((w) => getComputedStyle(w).overflowX).filter((o) => o !== 'auto' && o !== 'scroll').length);
        if (wide) fail(file + ': ' + wide + ' wide tables are cut instead of scrolling');
        // the contents on a narrow screen
        if (narrow) {
          const menu = page.locator('#ig-nav-toggle, .ig-nav-toggle, button[aria-controls="ig-sidebar"]').first();
          r[file + ' contents button'] = await menu.count();
        }
        r[file + ' rows'] = await page.locator('tr.ig-field-row').count();
      }
    } catch (e) { fail('CRASH ' + String(e).slice(0, 600)); }
    r.consoleErrors = errors.slice(0, 3);
    if (errors.length) fail('console errors: ' + errors.length);
    await browser.close();
  }
  console.log(JSON.stringify(out, null, 1));
  process.exit(Object.keys(out).some((device) => out[device].failures.length) ? 1 : 0);
})();
