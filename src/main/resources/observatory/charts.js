import { css, compact } from './app.js';

export const CHARTS = [
  { key: 'population', label: 'Population', unit: 'units' },
  { key: 'energy_total', label: 'Energy held', unit: 'energy' },
  { key: 'births_per_tick', label: 'Births per tick', unit: 'births' },
  { key: 'lineages_alive', label: 'Lineages alive', unit: 'lineages' },
];

function resolve(colour) {
  const match = /^var\((--[\w-]+)\)$/.exec(colour);
  return match ? css(match[1]) : colour;
}

export function drawChart(canvas, series, options = {}) {
  const ratio = window.devicePixelRatio || 1;
  const width = Math.max(50, Math.round(canvas.clientWidth * ratio));
  const height = Math.max(30, Math.round(canvas.clientHeight * ratio));
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, width, height);
  const top = 14 * ratio;
  const bottom = height - 3 * ratio;
  let maxY = 0;
  let maxX = options.maxX || 1;
  for (const s of series) {
    for (let i = 0; i < s.y.length; i++) maxY = Math.max(maxY, s.y[i]);
    if (s.x.length) maxX = Math.max(maxX, s.x[s.x.length - 1]);
  }
  maxY = maxY > 0 ? maxY * 1.05 : 1;
  const px = (x) => (x / maxX) * width;
  const py = (y) => bottom - (y / maxY) * (bottom - top);
  ctx.strokeStyle = css('--line');
  ctx.lineWidth = ratio;
  ctx.beginPath();
  ctx.moveTo(0, bottom);
  ctx.lineTo(width, bottom);
  ctx.stroke();
  ctx.fillStyle = css('--muted');
  ctx.font = `${11 * ratio}px ${css('--font-data')}`;
  ctx.textBaseline = 'top';
  ctx.fillText('max ' + compact(maxY / 1.05), 2 * ratio, 0);
  if (options.dashedAt !== undefined && options.dashedAt !== null) {
    ctx.save();
    ctx.setLineDash([4 * ratio, 4 * ratio]);
    ctx.strokeStyle = css('--muted');
    ctx.beginPath();
    ctx.moveTo(px(options.dashedAt), top - 4 * ratio);
    ctx.lineTo(px(options.dashedAt), bottom);
    ctx.stroke();
    ctx.restore();
  }
  for (const s of series) {
    if (!s.x.length) continue;
    ctx.beginPath();
    for (let i = 0; i < s.x.length; i++) {
      if (i === 0) ctx.moveTo(px(s.x[i]), py(s.y[i]));
      else ctx.lineTo(px(s.x[i]), py(s.y[i]));
    }
    if (options.fill) {
      ctx.save();
      ctx.lineTo(px(s.x[s.x.length - 1]), bottom);
      ctx.lineTo(px(s.x[0]), bottom);
      ctx.closePath();
      ctx.fillStyle = css('--accent-soft');
      ctx.fill();
      ctx.restore();
      ctx.beginPath();
      for (let i = 0; i < s.x.length; i++) {
        if (i === 0) ctx.moveTo(px(s.x[i]), py(s.y[i]));
        else ctx.lineTo(px(s.x[i]), py(s.y[i]));
      }
    }
    ctx.strokeStyle = resolve(s.colour || 'var(--accent)');
    ctx.lineWidth = 1.8 * ratio;
    ctx.lineJoin = 'round';
    ctx.stroke();
  }
  if (options.cursor !== undefined && options.cursor !== null) {
    ctx.strokeStyle = css('--fg');
    ctx.lineWidth = ratio;
    ctx.beginPath();
    ctx.moveTo(px(options.cursor), top - 4 * ratio);
    ctx.lineTo(px(options.cursor), bottom);
    ctx.stroke();
  }
}

export function valueAt(x, y, tick) {
  if (!x.length) return null;
  let lo = 0;
  let hi = x.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (x[mid] <= tick) lo = mid;
    else hi = mid - 1;
  }
  return y[lo];
}
