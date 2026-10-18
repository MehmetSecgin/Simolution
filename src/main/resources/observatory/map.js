import { css } from './app.js';

export const LINEAGE_COLOURS = 12;
export const EMPTY = 255;
export const IDLE = 254;
const ACTION_TOKENS = { MOVE: '--s1', HARVEST: '--s3', REPRODUCE: '--s2', GROW: '--s5', ACTION_Y: '--s4' };
let actionNames = [];

export function useLayout(layout) {
  actionNames = layout.names.slice(layout.offsets.action, layout.total);
}

export function parseFrame(buffer) {
  const view = new DataView(buffer);
  const tick = view.getInt32(0, true);
  const width = view.getInt32(4, true);
  const cells = width * width;
  const levels = new Uint8Array(buffer, 8, cells);
  const count = view.getInt32(8 + cells, true);
  const units = new Int32Array(count * 3);
  for (let i = 0, at = 12 + cells; i < units.length; i++, at += 4) units[i] = view.getInt32(at, true);
  return { tick, width, levels, count, units };
}

export function parseState(buffer) {
  const view = new DataView(buffer);
  const tick = view.getInt32(0, true);
  const width = view.getInt32(4, true);
  const cells = width * width;
  const mass = new Float32Array(buffer.slice(8, 8 + 4 * cells));
  const action = new Uint8Array(buffer, 8 + 4 * cells, cells);
  let maxMass = 0;
  for (let cell = 0; cell < cells; cell++) if (action[cell] !== EMPTY && mass[cell] > maxMass) maxMass = mass[cell];
  return { tick, width, mass, action, maxMass };
}

export function unitAt(frame, cell) {
  for (let i = 0; i < frame.count; i++) {
    if (frame.units[i * 3] === cell) return { cell, slot: frame.units[i * 3 + 1], lineage: frame.units[i * 3 + 2] };
  }
  return null;
}

export function cellOfSlot(frame, slot) {
  for (let i = 0; i < frame.count; i++) if (frame.units[i * 3 + 1] === slot) return frame.units[i * 3];
  return -1;
}

function rgb(token) {
  const value = css(token);
  if (value.startsWith('#')) {
    const hex = value.length === 4 ? value.slice(1).split('').map((c) => c + c).join('') : value.slice(1, 7);
    return [0, 2, 4].map((i) => parseInt(hex.slice(i, i + 2), 16));
  }
  const parts = value.match(/\d+(\.\d+)?/g) || ['0', '0', '0'];
  return parts.slice(0, 3).map(Number);
}

function mix(a, b, t) {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

export function lineageColour(lineage) {
  return 'var(--s' + ((((lineage % LINEAGE_COLOURS) + LINEAGE_COLOURS) % LINEAGE_COLOURS) + 1) + ')';
}

function lineageRgb(palette, lineage) {
  return palette[((lineage % LINEAGE_COLOURS) + LINEAGE_COLOURS) % LINEAGE_COLOURS];
}

function actionToken(index) {
  const name = actionNames[index] || '';
  if (name.startsWith('MOVE')) return ACTION_TOKENS.MOVE;
  return ACTION_TOKENS[name] || '--muted';
}

export function legendItems(overlay, frame, state) {
  if (overlay === 'lineage' && frame) {
    const counts = new Map();
    for (let i = 0; i < frame.count; i++) {
      const lineage = frame.units[i * 3 + 2];
      counts.set(lineage, (counts.get(lineage) || 0) + 1);
    }
    const top = [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0] - b[0]).slice(0, 8);
    const items = top.map(([lineage, n]) => [lineageColour(lineage), 'lineage ' + lineage + ': ' + n.toLocaleString('en')]);
    if (counts.size > top.length) items.push(['transparent', (counts.size - top.length) + ' more lineages']);
    return items;
  }
  if (overlay === 'resource') return [['var(--field-lo)', 'empty cell'], ['var(--field-hi)', 'full cell (capacity)'], ['var(--fg)', 'unit']];
  if (overlay === 'mass') {
    const max = state ? state.maxMass : 0;
    return [['var(--s6)', 'light (mass near 0)'], ['var(--s7)', 'heaviest (mass ' + max.toFixed(2) + ')']];
  }
  if (overlay === 'action') {
    return [['var(--s1)', 'MOVE'], ['var(--s3)', 'HARVEST'], ['var(--s2)', 'REPRODUCE'], ['var(--s5)', 'GROW'],
      ['var(--s4)', 'ACTION_Y'], ['var(--muted)', 'idle (no action above 0)']];
  }
  return [];
}

const scratch = document.createElement('canvas');

export function drawFrame(canvas, frame, overlay, state, picked = -1) {
  const ctx = canvas.getContext('2d');
  const size = Math.max(100, Math.round(canvas.clientWidth * (window.devicePixelRatio || 1)));
  if (canvas.width !== size) {
    canvas.width = size;
    canvas.height = size;
  }
  const background = rgb('--map-bg');
  ctx.fillStyle = 'rgb(' + background.join(',') + ')';
  ctx.fillRect(0, 0, size, size);
  if (!frame) return;
  const width = frame.width;
  const cells = width * width;
  scratch.width = width;
  scratch.height = width;
  const sctx = scratch.getContext('2d');
  const image = sctx.createImageData(width, width);
  const pixels = image.data;
  const lo = rgb('--field-lo');
  const hi = rgb('--field-hi');
  const put = (cell, colour) => {
    pixels[cell * 4] = colour[0];
    pixels[cell * 4 + 1] = colour[1];
    pixels[cell * 4 + 2] = colour[2];
    pixels[cell * 4 + 3] = 255;
  };
  const exact = state && state.tick === frame.tick && (overlay === 'mass' || overlay === 'action');
  for (let cell = 0; cell < cells; cell++) {
    const level = frame.levels[cell] / 9;
    if (overlay === 'lineage') put(cell, mix(lo, hi, level * 0.35));
    else if (overlay === 'resource') put(cell, mix(lo, hi, level));
    else put(cell, background);
  }
  if (overlay === 'lineage') {
    const palette = Array.from({ length: LINEAGE_COLOURS }, (_, i) => rgb('--s' + (i + 1)));
    for (let i = 0; i < frame.count; i++) put(frame.units[i * 3], lineageRgb(palette, frame.units[i * 3 + 2]));
  } else if (overlay === 'resource') {
    const fg = rgb('--fg');
    for (let i = 0; i < frame.count; i++) {
      const cell = frame.units[i * 3];
      put(cell, mix(mix(lo, hi, frame.levels[cell] / 9), fg, 0.55));
    }
  } else if (exact && overlay === 'mass') {
    const light = rgb('--s6');
    const heavy = rgb('--s7');
    const max = state.maxMass || 1;
    for (let cell = 0; cell < cells; cell++) {
      if (state.action[cell] !== EMPTY) put(cell, mix(light, heavy, Math.min(1, state.mass[cell] / max)));
    }
  } else if (exact && overlay === 'action') {
    const cache = new Map();
    for (let cell = 0; cell < cells; cell++) {
      const action = state.action[cell];
      if (action === EMPTY) continue;
      if (!cache.has(action)) cache.set(action, rgb(action === IDLE ? '--muted' : actionToken(action)));
      put(cell, cache.get(action));
    }
  } else {
    const muted = rgb('--line');
    for (let i = 0; i < frame.count; i++) put(frame.units[i * 3], muted);
  }
  sctx.putImageData(image, 0, 0);
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(scratch, 0, 0, size, size);
  if (picked >= 0) {
    const px = size / width;
    const x = picked % width;
    const y = Math.floor(picked / width);
    ctx.lineWidth = Math.max(2, px * 0.25);
    ctx.strokeStyle = css('--fg');
    ctx.strokeRect(x * px - px, y * px - px, px * 3, px * 3);
  }
}

export function cellAt(canvas, frame, event) {
  const rect = canvas.getBoundingClientRect();
  const x = Math.floor((event.clientX - rect.left) / rect.width * frame.width);
  const y = Math.floor((event.clientY - rect.top) / rect.height * frame.width);
  if (x < 0 || y < 0 || x >= frame.width || y >= frame.width) return -1;
  return y * frame.width + x;
}
