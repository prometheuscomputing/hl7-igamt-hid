// usage: node run.js <chromium|firefox|webkit> [channel=chrome] [pages=lib,other]
// Runs export-features.e2e.js in one browser and prints the failures and the counts per guide.
// PLAYWRIGHT=<path of a playwright or playwright-core module> when it is not installed next to this file.
const playwright = require(process.env.PLAYWRIGHT || 'playwright');
const test = require('./export-features.e2e.js');

(async () => {
  const engine = process.argv[2] || 'chromium';
  const named = {};
  process.argv.slice(3).forEach((arg) => { const at = arg.indexOf('='); if (at > 0) { named[arg.slice(0, at)] = arg.slice(at + 1); } });
  const browser = await playwright[engine].launch(named.channel ? { channel: named.channel } : {});
  const page = await (await browser.newContext({ viewport: { width: 1360, height: 900 } })).newPage();
  page.setDefaultTimeout(20000);
  const out = { browser: (named.channel || engine) + ' ' + browser.version() };
  try {
    const result = JSON.parse(await test(page, named.pages ? { pages: named.pages.split(',') } : undefined));
    out.failures = result.failures;
    out.guides = {};
    Object.keys(result.report).forEach((name) => {
      const guide = result.report[name];
      if (!guide || !guide.panel) { return; }
      out.guides[name] = { whereUsedBlocks: guide.whereUsed.blocks, fieldRows: guide.panel.rows, previewsHovered: guide.previews.hovered, consoleErrors: guide.errors };
    });
  } catch (e) {
    out.crashed = String(e).slice(0, 1500);
  }
  console.log(JSON.stringify(out, null, 1));
  await browser.close();
  process.exit(out.crashed || out.failures.length ? 1 : 0);
})();
