import { api, fmt, compact, el, toast, layout, onEvent } from './app.js';
import { drawFrame, parseFrame, useLayout } from './map.js';
import { drawChart, CHARTS } from './charts.js';

const COLOURS = ['var(--s1)', 'var(--s2)', 'var(--s3)', 'var(--s4)'];
const LETTERS = ['A', 'B', 'C', 'D'];
const view = { dirs: [], result: null, metrics: [], frames: [], dashedAt: null, busy: false, wanted: null, key: '' };
let wired = false;
const $ = (id) => document.getElementById(id);

function ticksDone(run) {
  return run.status ? run.status.ticksDone : 0;
}

function banner(divergence) {
  const node = $('cmp-banner');
  node.replaceChildren();
  if (divergence.kind === 'ancestor') {
    node.className = 'banner split';
    node.append('Identical until tick ', el('b', { text: fmt(divergence.tick) }), ', then they split.');
    return divergence.tick;
  }
  if (divergence.kind === 'twin') {
    if (divergence.identical === null) {
      node.className = 'banner';
      node.append('The re-run is still going. The determinism check runs when it ends.');
    } else if (divergence.identical) {
      node.className = 'banner same';
      node.append('Byte-identical at all ', el('b', { text: fmt(divergence.checkpoints) }), ' checkpoints and the final state.');
    } else {
      node.className = 'banner bad';
      node.append('Differs from checkpoint tick ', el('b', { text: fmt(divergence.tick) }), ': a determinism bug.');
    }
    return null;
  }
  node.className = 'banner';
  node.append('Different from tick 0.');
  return null;
}

function legend(runs) {
  $('cmp-legend').replaceChildren(...runs.map((run, i) => el('span', {},
    el('i', { style: 'background: ' + COLOURS[i] + '; height: 3px; width: 18px' }),
    el('b', { text: LETTERS[i] + ' ' }), el('a', { href: '#run/' + encodeURIComponent(run.dir), text: run.manifest ? run.manifest.name : run.dir }))));
}

function settings() {
  const result = view.result;
  const showAll = $('diff-all').checked;
  const rows = result.settings.filter((s) => showAll || s.differs);
  $('diff-head').replaceChildren(el('tr', {}, el('th', { text: 'Setting' }), ...result.runs.map((_, i) => el('th', { text: LETTERS[i] }))));
  $('diff-body').replaceChildren(...rows.map((s) => el('tr', {},
    el('td', { class: 'num', text: s.key }),
    ...s.values.map((v, i) => el('td', { class: 'num' + (s.differs && i > 0 && v !== s.values[0] ? ' changed' : ''), text: v === null ? 'n/a' : v })))));
  $('diff-none').hidden = result.settings.some((s) => s.differs);
}

function outcomes() {
  const result = view.result;
  $('out-head').replaceChildren(el('tr', {}, el('th', { text: 'Outcome' }), ...result.runs.map((_, i) => el('th', { class: 'n', text: LETTERS[i] }))));
  $('out-body').replaceChildren(...result.outcomes.map((o) => el('tr', {},
    el('td', { text: o.name }),
    ...o.values.map((v, i) => {
      const cell = el('td', { class: 'n', text: compact(v) });
      const delta = o.deltaPercent[i];
      if (i > 0 && delta !== null && delta !== undefined) {
        cell.append(' ', el('span', { class: delta > 0 ? 'delta-up' : delta < 0 ? 'delta-down' : 'hint', text: '(' + (delta > 0 ? '+' : '') + delta.toFixed(1) + '%)' }));
      }
      return cell;
    }))));
}

function charts() {
  const maxX = Math.max(1, ...view.result.runs.map((r) => Math.max(r.targetTicks, ticksDone(r))));
  for (const chart of CHARTS) {
    const canvas = $('cmp-' + chart.key);
    drawChart(canvas, view.metrics.map((m, i) => ({ x: m.tick, y: m[chart.key], colour: COLOURS[i] })), {
      maxX, dashedAt: view.dashedAt, cursor: Number($('mm-scrub').value),
    });
  }
}

function minimaps() {
  view.result.runs.forEach((run, i) => {
    const canvas = $('mm-' + i);
    if (canvas) drawFrame(canvas, view.frames[i], 'lineage', null);
  });
}

async function loadFrames(tick) {
  view.wanted = tick;
  if (view.busy) return;
  view.busy = true;
  try {
    while (view.wanted !== null) {
      const wanted = view.wanted;
      view.wanted = null;
      const result = view.result;
      const frames = await Promise.all(result.runs.map((run) => {
        const at = Math.min(wanted, ticksDone(run));
        return api('runs/' + encodeURIComponent(run.dir) + '/frame?tick=' + at, { binary: true }).then(parseFrame);
      }));
      if (result !== view.result) continue;
      view.frames = frames;
      frames.forEach((frame, i) => {
        const label = $('mm-label-' + i);
        if (label) label.textContent = 'tick ' + fmt(frame.tick) + ', ' + fmt(frame.count) + ' units';
      });
      $('mm-tick').textContent = fmt(wanted);
      minimaps();
      charts();
    }
  } catch (error) {
    toast(error.message, true);
  } finally {
    view.busy = false;
  }
}

export async function showCompare(dirs) {
  if (!wired) wire();
  const key = dirs.join(',');
  view.key = key;
  $('cmp-sub').textContent = 'Loading ' + dirs.length + ' runs';
  try {
    useLayout(await layout());
    const result = await api('compare?runs=' + dirs.map(encodeURIComponent).join(','));
    const metrics = await Promise.all(dirs.map((d) => api('runs/' + encodeURIComponent(d) + '/metrics?points=600')));
    if (key !== view.key) return;
    Object.assign(view, { dirs, result, metrics, frames: [] });
  } catch (error) {
    $('cmp-sub').textContent = 'Cannot compare: ' + error.message;
    toast(error.message, true);
    return;
  }
  const runs = view.result.runs;
  const differing = view.result.settings.filter((s) => s.differs).length;
  $('cmp-sub').textContent = runs.length + ' runs, ' + (differing ? differing + (differing === 1 ? ' setting differs' : ' settings differ') : 'same settings');
  legend(runs);
  view.dashedAt = banner(view.result.divergence);
  $('cmp-dash').textContent = view.dashedAt === null ? '' : 'Dashed line: where they split';
  settings();
  outcomes();
  $('cmp-charts').replaceChildren(...CHARTS.map((c) => el('div', { class: 'chart' },
    el('div', { class: 'chart-label' }, el('span', { text: c.label })),
    el('canvas', { id: 'cmp-' + c.key, 'aria-label': c.label + ' for every run' }))));
  $('minimaps').replaceChildren(...runs.map((run, i) => el('div', { class: 'minimap' },
    el('div', { class: 'row' }, el('b', { text: LETTERS[i] }), el('span', { text: run.manifest ? run.manifest.name : run.dir }), el('span', { class: 'spacer' }), el('span', { class: 'hint', id: 'mm-label-' + i })),
    el('canvas', { id: 'mm-' + i, 'aria-label': 'Map of run ' + LETTERS[i] }))));
  const max = Math.max(...runs.map(ticksDone));
  const scrub = $('mm-scrub');
  scrub.max = max;
  scrub.value = view.dashedAt !== null ? Math.min(max, view.dashedAt) : max;
  charts();
  loadFrames(Number(scrub.value));
}

function wire() {
  wired = true;
  $('diff-all').addEventListener('change', () => {
    if (view.result) settings();
  });
  $('mm-scrub').addEventListener('input', () => {
    if (view.result) loadFrames(Number($('mm-scrub').value));
  });
  const redraw = () => {
    if (view.result && !$('screen-compare').hidden) {
      charts();
      minimaps();
    }
  };
  window.addEventListener('resize', redraw);
  window.addEventListener('themechange', redraw);
  onEvent('run', (event) => {
    if ($('screen-compare').hidden || !view.dirs.includes(event.dir)) return;
    if (['finished', 'extinct', 'stopped', 'failed'].includes(event.status)) showCompare(view.dirs);
  });
}
