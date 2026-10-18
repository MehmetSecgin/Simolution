import { api, go, fmt, ago, el, toast, onEvent, settingRow, readSettings, HEALTH_WORDS } from './app.js';

const selected = new Set();
let runs = [];
let configKeys = [];
let wired = false;
let refreshTimer = 0;
const progressBars = new Map();

function screenVisible() {
  return !document.getElementById('screen-runs').hidden;
}

function scheduleRefresh() {
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(() => {
    if (screenVisible()) load();
  }, 300);
}

function order(list) {
  const byDir = new Map(list.map((r) => [r.dir, r]));
  const children = new Map();
  const roots = [];
  for (const run of list) {
    const parent = run.twinOf || run.parent;
    if (parent && byDir.has(parent)) {
      if (!children.has(parent)) children.set(parent, []);
      children.get(parent).push(run);
    } else {
      roots.push(run);
    }
  }
  const newest = (a, b) => b.modified - a.modified;
  const out = [];
  const visit = (run, depth) => {
    out.push([run, depth]);
    for (const child of (children.get(run.dir) || []).sort(newest)) visit(child, depth + 1);
  };
  for (const run of roots.sort(newest)) visit(run, 0);
  return out;
}

function origin(run) {
  if (run.twinOf) return el('span', { class: 'branch-tag', text: 're-run of ' + run.twinOf });
  if (run.parent) return el('span', { class: 'branch-tag', text: 'branched at tick ' + fmt(run.branchTick) });
  return null;
}

function row(run, depth) {
  const status = run.status ? run.status.status : 'failed';
  const done = run.status ? run.status.ticksDone : 0;
  const box = el('input', {
    type: 'checkbox',
    'aria-label': 'Select ' + run.name + ' to compare',
    checked: selected.has(run.dir),
    disabled: !!run.unreadable,
    onclick: (event) => event.stopPropagation(),
    onchange: (event) => {
      if (event.target.checked) selected.add(run.dir);
      else selected.delete(run.dir);
      updateCompare();
    },
  });
  const remove = el('button', {
    class: 'btn small danger',
    text: 'Delete',
    'aria-label': 'Delete ' + run.name,
    disabled: status === 'running' || status === 'queued' || status === 'paused',
    onclick: async (event) => {
      event.stopPropagation();
      if (!confirm('Delete ' + run.dir + ' and every file in it? This cannot be undone.')) return;
      try {
        await api('runs/' + encodeURIComponent(run.dir), { method: 'DELETE' });
        selected.delete(run.dir);
        toast('Deleted ' + run.dir);
        load();
      } catch (error) {
        toast(error.message, true);
      }
    },
  });
  const nameCell = el('td', { class: 'tree-cell' + (depth ? ' child' : ''), style: '--depth: ' + depth },
    el('div', { class: 'runname', text: run.name }),
    el('div', { class: 'runmeta' }, run.dir, ' ', origin(run)));
  if (run.unreadable) {
    return el('tr', { class: 'unreadable' },
      el('td', {}, box), nameCell,
      el('td', { colspan: 4, text: 'Cannot open: ' + run.unreadable }),
      el('td', { class: 'runmeta', text: ago(run.modified) }), el('td', {}, remove));
  }
  const share = run.targetTicks ? Math.min(1, done / run.targetTicks) : 0;
  const fill = el('i', { style: 'width: ' + (share * 100).toFixed(1) + '%' });
  const bar = el('div', { class: 'prog', role: 'progressbar', 'aria-label': 'Progress', 'aria-valuenow': Math.round(share * 100), 'aria-valuemin': 0, 'aria-valuemax': 100 }, fill);
  const text = el('span', { class: 'runmeta', text: fmt(done) + ' / ' + fmt(run.targetTicks) + ' ticks' });
  progressBars.set(run.dir, { box: bar, fill, text });
  return el('tr', { class: 'run-row', tabindex: 0, onclick: () => go('#run/' + encodeURIComponent(run.dir)),
    onkeydown: (event) => {
      if (event.key === 'Enter') go('#run/' + encodeURIComponent(run.dir));
    } },
    el('td', {}, box),
    nameCell,
    el('td', {}, el('span', { class: 'pill ' + status, text: status })),
    el('td', {}, el('div', { class: 'row' }, bar, text)),
    el('td', { class: 'n', text: fmt(run.peakPopulation) }),
    el('td', {}, el('span', { class: 'chip ' + run.health, text: HEALTH_WORDS[run.health] || run.health })),
    el('td', { class: 'runmeta', text: ago(run.modified) }),
    el('td', {}, remove));
}

function updateCompare() {
  const button = document.getElementById('cmp-btn');
  button.disabled = selected.size < 2 || selected.size > 4;
  button.textContent = selected.size ? 'Compare ' + selected.size + ' runs' : 'Compare';
}

async function load() {
  try {
    runs = await api('runs');
  } catch (error) {
    toast(error.message, true);
    return;
  }
  for (const dir of [...selected]) if (!runs.some((r) => r.dir === dir)) selected.delete(dir);
  progressBars.clear();
  const rows = order(runs).map(([run, depth]) => row(run, depth));
  document.getElementById('run-rows').replaceChildren(...rows);
  document.getElementById('runs-empty').hidden = rows.length > 0;
  updateCompare();
}

async function loadKeys() {
  const file = document.getElementById('nr-config').value;
  if (!file) return;
  const config = await api('configs/' + encodeURIComponent(file));
  configKeys = Object.keys(config).filter((k) => typeof config[k] !== 'object');
  document.getElementById('nr-settings-keys')?.replaceChildren(...configKeys.map((k) => el('option', { value: k })));
}

async function openForm() {
  const form = document.getElementById('new-form');
  form.hidden = false;
  const select = document.getElementById('nr-config');
  try {
    const files = await api('configs');
    select.replaceChildren(...files.map((f) => el('option', { value: f, text: 'examples/' + f })));
    await loadKeys();
  } catch (error) {
    toast(error.message, true);
  }
  document.getElementById('nr-name').focus();
}

async function submit(event) {
  event.preventDefault();
  const number = (id) => {
    const value = document.getElementById(id).value.trim();
    return value === '' ? null : Number(value);
  };
  const overrides = readSettings(document.getElementById('nr-settings'));
  if (number('nr-seed') !== null) overrides.seed = number('nr-seed');
  if (number('nr-world') !== null) overrides.worldWidth = number('nr-world');
  const body = {
    name: document.getElementById('nr-name').value.trim(),
    configFile: document.getElementById('nr-config').value,
    overrides,
    founders: number('nr-founders'),
    genesPerFounder: number('nr-genes'),
    targetTicks: number('nr-ticks'),
    checkpointEvery: number('nr-every'),
  };
  try {
    const launch = await api('runs', { method: 'POST', body });
    toast(launch.existing ? 'Same run already exists; opening ' + launch.dir : 'Started ' + launch.dir);
    document.getElementById('new-form').hidden = true;
    go('#run/' + encodeURIComponent(launch.dir));
  } catch (error) {
    toast(error.message, true);
  }
}

function wire() {
  wired = true;
  document.getElementById('new-btn').addEventListener('click', openForm);
  document.getElementById('nr-cancel').addEventListener('click', () => {
    document.getElementById('new-form').hidden = true;
  });
  document.getElementById('nr-add').addEventListener('click', () => settingRow(document.getElementById('nr-settings'), configKeys));
  document.getElementById('new-form').addEventListener('submit', submit);
  document.getElementById('nr-config').addEventListener('change', () => loadKeys().catch((error) => toast(error.message, true)));
  document.getElementById('cmp-btn').addEventListener('click', () => {
    const chosen = order(runs).map(([run]) => run.dir).filter((dir) => selected.has(dir));
    go('#compare/' + chosen.map(encodeURIComponent).join(','));
  });
  onEvent('run', scheduleRefresh);
  onEvent('progress', (event) => {
    const bar = progressBars.get(event.dir);
    if (!bar || !screenVisible()) return;
    const share = event.targetTicks ? Math.min(1, event.tick / event.targetTicks) : 0;
    bar.fill.style.width = (share * 100).toFixed(1) + '%';
    bar.box.setAttribute('aria-valuenow', Math.round(share * 100));
    bar.text.textContent = fmt(event.tick) + ' / ' + fmt(event.targetTicks) + ' ticks';
  });
}

export function showRuns() {
  if (!wired) wire();
  load();
}
