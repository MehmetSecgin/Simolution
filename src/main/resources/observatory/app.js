import { showRuns } from './runs.js';
import { showRun, leaveRun } from './run.js';
import { showCompare } from './compare.js';

const listeners = { run: [], progress: [] };
let layoutPromise = null;

export async function api(path, options = {}) {
  const init = { method: options.method || 'GET', headers: {} };
  if (options.body !== undefined) {
    init.body = JSON.stringify(options.body);
    init.headers['Content-Type'] = 'application/json';
  }
  const response = await fetch('/api/' + path, init);
  if (!response.ok) {
    let message = response.status + ' ' + response.statusText;
    try {
      message = (await response.json()).error || message;
    } catch (ignored) {
      message = response.status + ' ' + response.statusText;
    }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
  return options.binary ? response.arrayBuffer() : response.json();
}

export function layout() {
  layoutPromise = layoutPromise || api('layout');
  return layoutPromise;
}

export function onEvent(type, handler) {
  listeners[type].push(handler);
  return () => {
    listeners[type] = listeners[type].filter((h) => h !== handler);
  };
}

export function go(hash) {
  if (location.hash === hash) {
    route();
  } else {
    location.hash = hash;
  }
}

export function fmt(value, digits = 0) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return 'n/a';
  }
  return Number(value).toLocaleString('en', { maximumFractionDigits: digits, minimumFractionDigits: digits });
}

export function compact(value) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return 'n/a';
  }
  const abs = Math.abs(value);
  if (abs >= 1e9) return fmt(value / 1e9, 2) + ' B';
  if (abs >= 1e6) return fmt(value / 1e6, 2) + ' M';
  if (abs >= 1e4) return fmt(value / 1e3, 1) + ' k';
  return fmt(value, abs < 10 && abs !== Math.round(abs) ? 2 : 0);
}

export function ago(millis) {
  const seconds = Math.max(0, (Date.now() - millis) / 1000);
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return Math.round(seconds / 60) + ' min ago';
  if (seconds < 86400) return Math.round(seconds / 3600) + ' h ago';
  return new Date(millis).toLocaleDateString('en', { month: 'short', day: 'numeric' });
}

export function css(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

export function el(tag, attributes = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attributes)) {
    if (value === undefined || value === null || value === false) continue;
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (key === 'style') node.style.cssText = value;
    else node.setAttribute(key, value === true ? '' : value);
  }
  node.append(...children.filter((c) => c !== null && c !== undefined));
  return node;
}

let toastTimer = 0;
export function toast(message, error = false) {
  const node = document.getElementById('toast');
  node.textContent = message;
  node.className = error ? 'toast error' : 'toast';
  node.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    node.hidden = true;
  }, error ? 6000 : 3000);
}

export const STATUS_WORDS = {
  queued: 'queued', running: 'running', paused: 'paused', finished: 'finished',
  extinct: 'extinct', stopped: 'stopped', failed: 'failed',
};

export function settingRow(host, keys, key = '', value = '') {
  const listId = host.id + '-keys';
  if (!document.getElementById(listId)) {
    host.after(el('datalist', { id: listId }, ...keys.map((k) => el('option', { value: k }))));
  }
  const row = el('div', { class: 'setting' },
    el('label', { class: 'field' }, 'Setting', el('input', { list: listId, value: key, 'data-key': true, required: true })),
    el('label', { class: 'field' }, 'Value', el('input', { value, 'data-value': true, required: true })),
    el('button', { class: 'btn small', type: 'button', text: 'Remove', onclick: () => row.remove() }));
  host.append(row);
  row.querySelector('input').focus();
}

export function readSettings(host) {
  const overrides = {};
  for (const row of host.querySelectorAll('.setting')) {
    const key = row.querySelector('[data-key]').value.trim();
    const raw = row.querySelector('[data-value]').value.trim();
    if (!key) continue;
    try {
      overrides[key] = JSON.parse(raw);
    } catch (ignored) {
      overrides[key] = raw;
    }
  }
  return overrides;
}

function connect() {
  const source = new EventSource('/api/events');
  const conn = document.getElementById('conn');
  source.onopen = () => {
    conn.textContent = '';
  };
  source.onerror = () => {
    conn.textContent = 'server unreachable, retrying';
  };
  for (const type of Object.keys(listeners)) {
    source.addEventListener(type, (event) => {
      const payload = JSON.parse(event.data);
      for (const handler of listeners[type]) handler(payload);
    });
  }
}

function route() {
  const [screen, rest] = location.hash.replace(/^#/, '').split(/\/(.*)/s);
  const name = ['run', 'compare'].includes(screen) && rest ? screen : 'runs';
  for (const section of document.querySelectorAll('.screen')) {
    section.hidden = section.id !== 'screen-' + name;
  }
  for (const button of document.querySelectorAll('[data-nav]')) {
    if (button.dataset.nav === name) {
      button.setAttribute('aria-current', 'page');
      button.hidden = false;
    } else {
      button.removeAttribute('aria-current');
    }
    if (button.dataset.nav !== 'runs' && button.dataset.nav === name) {
      button.dataset.hash = location.hash;
    }
  }
  if (name !== 'run') leaveRun();
  if (name === 'runs') showRuns();
  if (name === 'run') showRun(decodeURIComponent(rest));
  if (name === 'compare') showCompare(rest.split(',').map(decodeURIComponent));
}

function theme() {
  const root = document.documentElement;
  let saved = null;
  try {
    saved = localStorage.getItem('observatory-theme');
  } catch (ignored) {
    saved = null;
  }
  if (saved) root.dataset.theme = saved;
  document.getElementById('theme').addEventListener('click', () => {
    const dark = root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
    root.dataset.theme = dark ? 'light' : 'dark';
    try {
      localStorage.setItem('observatory-theme', root.dataset.theme);
    } catch (ignored) {
      root.dataset.theme = dark ? 'light' : 'dark';
    }
    window.dispatchEvent(new Event('themechange'));
  });
}

document.addEventListener('click', (event) => {
  const target = event.target.closest('[data-go]');
  if (target) go(target.dataset.go);
  const nav = event.target.closest('[data-nav]');
  if (nav) go(nav.dataset.nav === 'runs' ? '#runs' : nav.dataset.hash || '#runs');
});
window.addEventListener('hashchange', route);
theme();
connect();
route();
