import { api, layout, onEvent, go, fmt, compact, el, toast, settingRow, readSettings, STATUS_WORDS } from './app.js';
import { drawFrame, parseFrame, parseState, unitAt, cellOfSlot, cellAt, legendItems, useLayout } from './map.js';
import { drawChart, valueAt, CHARTS } from './charts.js';
import { drawCircuit } from './circuit.js';

const LIVE_POLL_MS = 100;
const DETAIL_REFRESH_MS = 3000;
const SEEK_DELAY_MS = 150;
const ENDED = new Set(['finished', 'extinct', 'stopped', 'failed']);
const HEALTH_WORDS = { ok: 'all good', warn: 'look at this', bad: 'problem' };
const MARKS = { ok: '✓', warn: '!', bad: '×' };

const view = {
  dir: null, run: null, metrics: null, layout: null, tick: 0, following: false, overlay: 'lineage',
  frame: null, state: null, picked: null, unit: null, pollTimer: 0, detailTimer: 0, seekTimer: 0,
  wantedFrame: null, frameBusy: false, liveBusy: false, generation: 0,
};
let wired = false;
const $ = (id) => document.getElementById(id);

function status() {
  return view.run && view.run.status ? view.run.status.status : 'failed';
}

function done() {
  return view.run && view.run.status ? view.run.status.ticksDone : 0;
}

function target() {
  if (!view.run) return 1;
  return ENDED.has(status()) ? Math.max(1, done()) : Math.max(view.run.targetTicks, done());
}

function needsState() {
  return view.overlay === 'mass' || view.overlay === 'action';
}

function path(suffix) {
  return 'runs/' + encodeURIComponent(view.dir) + suffix;
}

export function leaveRun() {
  view.generation++;
  view.dir = null;
  clearInterval(view.pollTimer);
  clearInterval(view.detailTimer);
  clearTimeout(view.seekTimer);
  view.pollTimer = 0;
  view.detailTimer = 0;
}

export async function showRun(dir) {
  if (!wired) wire();
  if (view.dir === dir) return;
  leaveRun();
  const generation = view.generation;
  Object.assign(view, { dir, run: null, metrics: null, frame: null, state: null, picked: null, unit: null, following: false });
  $('branch-panel').hidden = true;
  $('unit-kv').replaceChildren();
  $('unit-hint').textContent = 'Click a unit on the map';
  $('circuit').replaceChildren();
  $('circuit-card').hidden = true;
  try {
    view.layout = await layout();
    useLayout(view.layout);
    await Promise.all([loadDetail(), loadMetrics()]);
  } catch (error) {
    toast(error.message, true);
    return;
  }
  if (generation !== view.generation) return;
  buildCharts();
  if (status() === 'running') {
    follow();
  } else {
    pin(done());
  }
  view.detailTimer = setInterval(() => {
    if (status() === 'running') {
      loadDetail().catch(() => {});
      loadMetrics().then(drawCharts).catch(() => {});
    }
  }, DETAIL_REFRESH_MS);
}

async function loadDetail() {
  const dir = view.dir;
  const run = await api(path(''));
  if (dir !== view.dir) return;
  view.run = run;
  renderHeader();
  renderHealth();
  renderMoments();
}

async function loadMetrics() {
  const dir = view.dir;
  const metrics = await api(path('/metrics?points=600'));
  if (dir === view.dir) view.metrics = metrics;
}

function renderHeader() {
  const run = view.run;
  const s = status();
  $('rv-name').textContent = run.name;
  $('rv-pill').className = 'pill ' + s;
  $('rv-pill').textContent = STATUS_WORDS[s] || s;
  const width = run.config ? run.config.worldWidth : 0;
  $('rv-world').textContent = width + ' × ' + width + ' torus';
  const origin = $('rv-origin');
  origin.replaceChildren();
  if (run.twinOf) {
    origin.append('re-run of ', el('a', { href: '#run/' + encodeURIComponent(run.twinOf), text: run.twinOf }));
  } else if (run.parent) {
    origin.append('branched from ', el('a', { href: '#run/' + encodeURIComponent(run.parent), text: run.parent }), ' at tick ' + fmt(run.branchTick));
  }
  if (run.status && run.status.error) origin.append(' error: ' + run.status.error);
  const pause = $('rv-pause');
  pause.textContent = s === 'paused' ? 'Resume' : 'Pause';
  pause.disabled = !['running', 'paused', 'queued'].includes(s);
  $('rv-stop').disabled = !['running', 'paused', 'queued'].includes(s);
  $('rv-rerun').disabled = !ENDED.has(s) || s === 'failed';
  $('rv-parent').hidden = !(run.twinOf || run.parent);
  $('tl-max').textContent = fmt(run.targetTicks);
  $('tl-live').hidden = s !== 'running';
  const scrub = $('scrub');
  scrub.max = target();
  updateTick();
}

function renderHealth() {
  const checks = view.run.health || [];
  $('checks').replaceChildren(...checks.map((c) => el('li', {},
    el('span', { class: 'mark ' + c.level, text: MARKS[c.level], 'aria-label': c.level }),
    el('span', {}, el('b', { text: c.name + ': ' }), c.text))));
  const worst = checks.some((c) => c.level === 'bad') ? 'bad' : checks.some((c) => c.level === 'warn') ? 'warn' : 'ok';
  $('hc-badge').className = 'chip ' + worst;
  $('hc-badge').textContent = HEALTH_WORDS[worst];
}

function renderMoments() {
  const moments = view.run.moments || [];
  $('moments').replaceChildren(...(moments.length ? moments.map((m) => el('li', {},
    el('button', { onclick: () => pin(Math.min(m.tick, done())) },
      el('span', { class: 'num', text: fmt(m.tick) }),
      el('span', { class: 'mtag ' + m.mark, text: m.mark }),
      el('span', { text: m.label })))) : [el('li', { class: 'hint', text: 'Nothing notable yet.' })]));
  const lastLeft = [-100, -100];
  $('markers').replaceChildren(...moments.map((m) => {
    const left = m.tick / target() * 100;
    const lane = left - lastLeft[0] >= 2.5 ? 0 : left - lastLeft[1] >= 2.5 ? 1 : 0;
    lastLeft[lane] = left;
    return el('button', {
    class: 'mtag ' + m.mark,
    style: 'left: ' + left.toFixed(2) + '%; top: ' + lane * 20 + 'px',
    title: 'tick ' + fmt(m.tick) + ': ' + m.label,
    text: m.mark,
    onclick: () => pin(Math.min(m.tick, done())),
  });
  }));
}

function buildCharts() {
  $('charts').replaceChildren(...CHARTS.map((c) => el('div', { class: 'chart' },
    el('div', { class: 'chart-label' }, el('span', { text: c.label }), el('b', { id: 'rv-' + c.key + '-v', text: '0' })),
    el('canvas', { id: 'rv-' + c.key, 'aria-label': c.label + ' over time' }))));
  drawCharts();
}

function drawCharts() {
  if (!view.metrics || !view.run) return;
  const x = view.metrics.tick;
  for (const chart of CHARTS) {
    const canvas = $('rv-' + chart.key);
    if (!canvas) continue;
    drawChart(canvas, [{ x, y: view.metrics[chart.key] }], {
      maxX: target(), cursor: view.tick, fill: true, dashedAt: view.run.parent && !view.run.twinOf ? view.run.branchTick : null,
    });
    const value = valueAt(x, view.metrics[chart.key], view.tick);
    $('rv-' + chart.key + '-v').textContent = value === null ? 'n/a' : compact(value) + ' ' + chart.unit;
  }
}

function updateTick() {
  $('tl-tick').textContent = fmt(view.tick);
  $('scrub').value = view.tick;
  const mode = $('tl-mode');
  mode.textContent = view.following ? 'following live' : 'pinned';
  mode.className = view.following ? 'chip ok' : 'chip';
}

function render() {
  const note = $('map-note');
  const exact = needsState() && view.state && view.frame && view.state.tick === view.frame.tick;
  if (needsState() && !exact) {
    note.hidden = false;
    note.textContent = 'Loading the exact state of tick ' + fmt(view.tick) + ' for this overlay';
  } else {
    note.hidden = true;
  }
  const picked = view.picked && view.frame ? cellOfSlot(view.frame, view.picked.slot) : -1;
  drawFrame($('map'), view.frame, view.overlay, exact ? view.state : null, picked);
  const items = legendItems(view.overlay, view.frame, exact ? view.state : null);
  $('map-legend').replaceChildren(...items.map(([colour, label]) => el('span', {},
    el('i', { style: 'background: ' + colour }), label)));
  updateTick();
  drawCharts();
}

async function fetchFrame(tick) {
  return parseFrame(await api(path('/frame?tick=' + tick), { binary: true }));
}

async function fetchState(tick) {
  return parseState(await api(path('/state?tick=' + tick), { binary: true }));
}

function follow() {
  if (status() !== 'running') {
    pin(done());
    return;
  }
  view.following = true;
  clearInterval(view.pollTimer);
  view.pollTimer = setInterval(pollLive, LIVE_POLL_MS);
  pollLive();
}

async function pollLive() {
  if (view.liveBusy || !view.following) return;
  view.liveBusy = true;
  const dir = view.dir;
  try {
    const frame = await fetchFrame('live');
    const state = needsState() ? await fetchState(frame.tick) : null;
    if (dir !== view.dir || !view.following) return;
    view.frame = frame;
    if (state) view.state = state;
    view.tick = frame.tick;
    if (view.run && view.run.status && frame.tick > view.run.status.ticksDone) view.run.status.ticksDone = frame.tick;
    render();
  } catch (error) {
    if (error.status !== 404) toast(error.message, true);
  } finally {
    view.liveBusy = false;
  }
}

function stopFollowing() {
  view.following = false;
  clearInterval(view.pollTimer);
  view.pollTimer = 0;
}

async function showFrame(tick) {
  view.wantedFrame = tick;
  if (view.frameBusy) return;
  view.frameBusy = true;
  const dir = view.dir;
  try {
    while (view.wantedFrame !== null && dir === view.dir) {
      const wanted = view.wantedFrame;
      view.wantedFrame = null;
      const frame = await fetchFrame(wanted);
      if (dir !== view.dir || view.following) return;
      view.frame = frame;
      render();
    }
  } catch (error) {
    toast(error.message, true);
  } finally {
    view.frameBusy = false;
  }
}

function pin(tick, delay = 0) {
  stopFollowing();
  view.tick = Math.max(0, Math.min(tick, done()));
  updateTick();
  if (delay > 0) showFrame(view.tick);
  clearTimeout(view.seekTimer);
  view.seekTimer = setTimeout(() => exact(view.tick), delay);
}

async function exact(tick) {
  const dir = view.dir;
  try {
    const [frame, state] = await Promise.all([fetchFrame(tick), fetchState(tick)]);
    if (dir !== view.dir || view.following || view.tick !== tick) return;
    view.frame = frame;
    view.state = state;
    render();
    if (view.picked) await loadUnit(tick);
  } catch (error) {
    toast(error.message, true);
  }
}

function noUnit(slot, tick) {
  view.unit = null;
  $('unit-kv').replaceChildren();
  $('circuit').replaceChildren();
  $('circuit-card').hidden = true;
  $('unit-hint').textContent = 'Slot ' + slot + ' holds no living unit at tick ' + fmt(tick);
}

async function loadUnit(tick) {
  const dir = view.dir;
  const slot = view.picked.slot;
  if (view.frame && view.frame.tick === tick && cellOfSlot(view.frame, slot) < 0) {
    noUnit(slot, tick);
    return;
  }
  try {
    const unit = await api(path('/unit?tick=' + tick + '&slot=' + slot));
    if (dir !== view.dir || !view.picked || view.picked.slot !== slot) return;
    view.unit = unit;
    renderUnit();
  } catch (error) {
    if (error.status !== 404) {
      toast(error.message, true);
      return;
    }
    noUnit(slot, tick);
  }
}

function scalar(value) {
  if (typeof value !== 'number') return String(value);
  return Number.isInteger(value) ? fmt(value) : value.toFixed(Math.abs(value) < 10 ? 4 : 2);
}

function renderUnit() {
  const unit = view.unit;
  const width = view.frame ? view.frame.width : 1;
  const fields = unit.fields;
  const rows = [['tick', fmt(unit.tick)], ['slot', unit.slot]];
  if (typeof fields.position === 'number') rows.push(['cell (x, y)', (fields.position % width) + ', ' + Math.floor(fields.position / width)]);
  for (const [name, value] of Object.entries(fields)) {
    if (name === 'position' || Array.isArray(value)) continue;
    rows.push([name, scalar(value)]);
  }
  rows.push(['live connections', fmt(unit.connections.length)]);
  $('unit-kv').replaceChildren(...rows.flatMap(([k, v]) => [el('dt', { text: k }), el('dd', { text: v })]));
  $('unit-hint').textContent = 'exact state at tick ' + fmt(unit.tick);
  $('circuit-sub').textContent = 'slot ' + unit.slot + ' at tick ' + fmt(unit.tick);
  $('circuit-card').hidden = false;
  drawCircuit($('circuit'), unit, view.layout);
}

function step(delta) {
  pin(view.tick + delta, SEEK_DELAY_MS);
}

async function control(action) {
  try {
    await api(path('/' + action), { method: 'POST' });
    await loadDetail();
  } catch (error) {
    toast(error.message, true);
  }
}

function togglePause() {
  const s = status();
  if (s === 'paused') control('resume');
  else if (s === 'running' || s === 'queued') control('pause');
}

function openBranch() {
  if (!view.run || !view.run.config) return;
  const tick = view.tick;
  $('br-tick').textContent = fmt(tick);
  $('br-name').value = view.run.name + ' branch at ' + tick;
  $('br-target').value = view.run.targetTicks > tick ? view.run.targetTicks : tick + 1000;
  $('br-target').min = tick + 1;
  $('br-settings').replaceChildren();
  $('branch-panel').hidden = false;
  $('branch-panel').dataset.tick = tick;
  const keys = Object.keys(view.run.config).filter((k) => typeof view.run.config[k] !== 'object');
  settingRow($('br-settings'), keys);
}

async function submitBranch(event) {
  event.preventDefault();
  const tick = Number($('branch-panel').dataset.tick);
  const body = {
    tick,
    overrides: readSettings($('br-settings')),
    targetTicks: Number($('br-target').value),
    name: $('br-name').value.trim(),
  };
  try {
    const launch = await api(path('/branch'), { method: 'POST', body });
    toast(launch.existing ? 'This branch already exists; opening it' : 'Branch started');
    $('branch-panel').hidden = true;
    go('#compare/' + encodeURIComponent(view.dir) + ',' + encodeURIComponent(launch.dir));
  } catch (error) {
    toast(error.message, true);
  }
}

async function rerun() {
  try {
    const launch = await api(path('/rerun'), { method: 'POST' });
    toast('Re-run started with a different thread count');
    go('#compare/' + encodeURIComponent(view.dir) + ',' + encodeURIComponent(launch.dir));
  } catch (error) {
    toast(error.message, true);
  }
}

function compareWithOrigin() {
  const other = view.run && (view.run.twinOf || view.run.parent);
  if (other) go('#compare/' + encodeURIComponent(other) + ',' + encodeURIComponent(view.dir));
}

function onKey(event) {
  if ($('screen-run').hidden || !view.run) return;
  const typing = event.target instanceof Element && event.target.closest('input:not([type=range]), select, textarea, [contenteditable]');
  if (typing || event.metaKey || event.ctrlKey || event.altKey) return;
  const big = event.shiftKey ? 100 : 1;
  if (event.key === ' ') togglePause();
  else if (event.key === 'ArrowLeft') step(-big);
  else if (event.key === 'ArrowRight') step(big);
  else if (event.key === 'Home') pin(0);
  else if (event.key === 'End') follow();
  else if (event.key === 'b' || event.key === 'B') openBranch();
  else if (event.key === 'c' || event.key === 'C') compareWithOrigin();
  else return;
  event.preventDefault();
}

function wire() {
  wired = true;
  $('overlays').addEventListener('click', (event) => {
    const button = event.target.closest('[data-ov]');
    if (!button) return;
    view.overlay = button.dataset.ov;
    for (const b of $('overlays').querySelectorAll('button')) b.setAttribute('aria-pressed', String(b === button));
    render();
    if (needsState() && !view.following && (!view.state || view.state.tick !== view.tick)) exact(view.tick);
  });
  const scrub = $('scrub');
  scrub.addEventListener('input', () => {
    stopFollowing();
    view.tick = Math.min(Number(scrub.value), done());
    updateTick();
    showFrame(view.tick);
  });
  scrub.addEventListener('change', () => pin(Number(scrub.value)));
  $('map').addEventListener('click', (event) => {
    if (!view.frame) return;
    const cell = cellAt($('map'), view.frame, event);
    const unit = cell < 0 ? null : unitAt(view.frame, cell);
    if (!unit) {
      $('unit-hint').textContent = 'No unit in that cell; click a coloured cell';
      return;
    }
    view.picked = { slot: unit.slot };
    if (view.following) {
      pin(view.frame.tick);
    } else {
      render();
      loadUnit(view.tick);
    }
  });
  $('tl-live').addEventListener('click', follow);
  $('rv-pause').addEventListener('click', togglePause);
  $('rv-stop').addEventListener('click', () => {
    if (confirm('Stop ' + view.dir + '? A stopped run cannot be resumed.')) control('stop');
  });
  $('rv-branch').addEventListener('click', openBranch);
  $('rv-parent').addEventListener('click', compareWithOrigin);
  $('br-cancel').addEventListener('click', () => {
    $('branch-panel').hidden = true;
  });
  $('br-add').addEventListener('click', () => settingRow($('br-settings'), Object.keys(view.run.config).filter((k) => typeof view.run.config[k] !== 'object')));
  $('branch-panel').addEventListener('submit', submitBranch);
  $('rv-rerun').addEventListener('click', rerun);
  document.addEventListener('keydown', onKey);
  window.addEventListener('resize', () => {
    if (view.dir) render();
  });
  window.addEventListener('themechange', () => {
    if (view.dir) render();
  });
  onEvent('run', async (event) => {
    if (event.dir !== view.dir) return;
    const wasFollowing = view.following;
    await loadDetail().catch(() => {});
    await loadMetrics().catch(() => {});
    if (status() === 'running' && !view.pollTimer && wasFollowing) follow();
    if (ENDED.has(status()) && wasFollowing) pin(done());
    drawCharts();
  });
  onEvent('progress', (event) => {
    if (event.dir !== view.dir || !view.run) return;
    view.run.status.ticksDone = Math.max(view.run.status.ticksDone, event.tick);
  });
}
