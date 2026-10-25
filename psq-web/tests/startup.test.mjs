import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';

const script = await readFile(new URL('../public/startup.js', import.meta.url), 'utf8');
const html = await readFile(new URL('../index.html', import.meta.url), 'utf8');

function startup({ modules = true, alreadyReady = false, documentState = 'complete' } = {}) {
  const element = () => ({ hidden: true, textContent: '', attributes: {}, setAttribute(key, value) { this.attributes[key] = value; }, getAttribute(key) { return this.attributes[key]; } });
  const nodes = Object.fromEntries(['psq-startup', 'psq-startup-title', 'psq-startup-message', 'psq-startup-help', 'root'].map(id => [id, element()]));
  if (alreadyReady) nodes.root.setAttribute('data-psq-ready', 'true');
  const events = { document: new Map(), window: new Map() }, timers = new Map();
  let timerId = 0;
  runInNewContext(script, {
    document: { readyState: documentState, getElementById: id => nodes[id], createElement: () => modules ? { noModule: false } : {}, addEventListener: (name, fn) => events.document.set(name, fn) },
    window: { addEventListener: (name, fn) => events.window.set(name, fn), setTimeout: fn => { const id = ++timerId; timers.set(id, fn); return id; }, clearTimeout: id => timers.delete(id) },
  });
  return {
    panel: nodes['psq-startup'], help: nodes['psq-startup-help'], timers,
    ready: () => events.document.get('psq:ready')(),
    documentReady: () => events.document.get('DOMContentLoaded')(),
    resourceError: tagName => events.window.get('error')({ target: { tagName } }),
    timeout: () => { for (const [id, fn] of timers) { timers.delete(id); fn(); } },
  };
}

test('JavaScript-disabled visitors have native Safari guidance and a reload link instead of countdown placeholders', () => {
  const fallback = html.match(/<noscript>([\s\S]*?)<\/noscript>/)?.[1];
  assert.ok(fallback);
  assert.match(fallback, /Safari/);
  assert.match(fallback, /JavaScript/);
  assert.match(fallback, /<details open>/);
  assert.match(fallback, /<a href="">/);
  assert.doesNotMatch(fallback, /DAYS|HOURS|<script|onclick=/);
});

test('successful startup dismisses the independent loader and cancels its timeout', () => {
  const page = startup();
  assert.equal(page.panel.hidden, false);
  assert.equal(page.panel.getAttribute('role'), 'status');
  page.ready();
  assert.equal(page.panel.hidden, true);
  assert.equal(page.timers.size, 0);
  page.resourceError('SCRIPT');
  assert.equal(page.panel.hidden, true);
});

test('an entry script loading failure offers recovery without treating a failed image as startup failure', () => {
  const page = startup();
  page.resourceError('IMG');
  assert.equal(page.help.hidden, true);
  page.resourceError('SCRIPT');
  assert.equal(page.panel.getAttribute('role'), 'alert');
  assert.equal(page.help.hidden, false);
  assert.equal(page.timers.size, 0);
});

test('a slow load shows recovery and still recovers if the page finishes later', () => {
  const page = startup();
  page.timeout();
  assert.equal(page.panel.getAttribute('role'), 'alert');
  assert.equal(page.help.hidden, false);
  page.ready();
  assert.equal(page.panel.hidden, true);
});

test('a browser without modules sees recovery immediately', () => {
  const page = startup({ modules: false });
  assert.equal(page.panel.getAttribute('role'), 'alert');
  assert.equal(page.help.hidden, false);
  assert.equal(page.timers.size, 0);
});

test('the loader stays hidden when the bundled entry becomes ready before loader initialization', () => {
  const page = startup({ alreadyReady: true });
  assert.equal(page.panel.hidden, true);
  assert.equal(page.timers.size, 0);
});

test('a script failing in the head is remembered before the fallback DOM exists', () => {
  const page = startup({ documentState: 'loading' });
  assert.equal(page.panel.hidden, true);
  page.resourceError('SCRIPT');
  page.documentReady();
  assert.equal(page.panel.getAttribute('role'), 'alert');
  assert.equal(page.help.hidden, false);
  assert.equal(page.timers.size, 0);
});

test('ready before DOMContentLoaded prevents a stale startup overlay', () => {
  const page = startup({ documentState: 'loading' });
  page.ready();
  page.documentReady();
  assert.equal(page.panel.hidden, true);
  assert.equal(page.timers.size, 0);
});
