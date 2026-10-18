const SVG = 'http://www.w3.org/2000/svg';
const COLUMN_X = [130, 280, 400];
const ROW = 26;
const TOP = 18;

function node(tag, attributes, text) {
  const element = document.createElementNS(SVG, tag);
  for (const [key, value] of Object.entries(attributes)) element.setAttribute(key, value);
  if (text !== undefined) element.textContent = text;
  return element;
}

function columnOf(layout, index) {
  if (index < layout.offsets.internal) return 0;
  if (index < layout.offsets.action) return 1;
  return 2;
}

function isJunk(layout, index) {
  return Object.values(layout.junk).some(([from, to]) => index >= from && index < to);
}

export function drawCircuit(svg, unit, layout) {
  svg.replaceChildren();
  if (!unit) return;
  const outputs = unit.fields.outputsPrev || [];
  const used = new Set();
  for (const c of unit.connections) {
    used.add(c.source);
    used.add(c.destination);
  }
  const columns = [[], [], []];
  for (let index = 0; index < layout.total; index++) {
    if (!isJunk(layout, index) || used.has(index)) columns[columnOf(layout, index)].push(index);
  }
  const rows = Math.max(...columns.map((c) => c.length));
  const height = TOP * 2 + (rows - 1) * ROW;
  svg.setAttribute('viewBox', `0 0 520 ${height}`);
  const position = new Map();
  columns.forEach((indices, column) => {
    const offset = TOP + ((rows - indices.length) * ROW) / 2;
    indices.forEach((index, row) => position.set(index, [COLUMN_X[column], offset + row * ROW]));
  });
  const groups = new Map();
  for (const c of unit.connections) {
    const key = c.source + ':' + c.destination;
    if (!groups.has(key)) groups.set(key, { source: c.source, destination: c.destination, weights: [] });
    groups.get(key).weights.push(c.weight);
  }
  const edges = node('g', {});
  for (const group of groups.values()) {
    const [x1, y1] = position.get(group.source);
    const [x2, y2] = position.get(group.destination);
    const total = group.weights.reduce((a, b) => a + b, 0);
    const active = (outputs[group.source] || 0) !== 0;
    let d;
    if (group.source === group.destination) {
      d = `M${x1 + 5} ${y1 - 3} C${x1 + 26} ${y1 - 18}, ${x1 + 26} ${y1 + 18}, ${x1 + 5} ${y1 + 3}`;
    } else if (columnOf(layout, group.source) === columnOf(layout, group.destination)) {
      const bulge = x1 + 24 + Math.abs(y2 - y1) * 0.25;
      d = `M${x1 + 5} ${y1} C${bulge} ${y1}, ${bulge} ${y2}, ${x2 + 5} ${y2}`;
    } else {
      const mid = (x1 + x2) / 2;
      d = `M${x1 + 5} ${y1} C${mid} ${y1}, ${mid} ${y2}, ${x2 - 5} ${y2}`;
    }
    const path = node('path', {
      d,
      fill: 'none',
      stroke: total >= 0 ? 'var(--s1)' : 'var(--s7)',
      'stroke-width': (0.8 + Math.min(4, Math.abs(total)) * 0.7).toFixed(2),
      opacity: active ? 0.9 : 0.25,
    });
    const names = layout.names[group.source] + ' to ' + layout.names[group.destination];
    path.append(node('title', {}, names + ', weight ' + group.weights.map((w) => w.toFixed(3)).join(', ')));
    edges.append(path);
    if (group.weights.length > 1) {
      edges.append(node('text', { x: (x1 + x2) / 2, y: (y1 + y2) / 2 - 4, 'text-anchor': 'middle', class: 'dose' }, '×' + group.weights.length));
    }
  }
  svg.append(edges);
  for (const [index, [x, y]] of position) {
    const output = outputs[index] || 0;
    const junk = isJunk(layout, index);
    const circle = node('circle', {
      cx: x, cy: y, r: junk ? 3 : 5,
      fill: output > 0 ? 'var(--accent)' : 'var(--panel-2)',
      stroke: junk ? 'var(--line)' : 'var(--accent)',
      'stroke-width': 1.2,
    });
    circle.append(node('title', {}, layout.names[index] + ': output ' + Number(output).toFixed(4)));
    svg.append(circle);
    const column = columnOf(layout, index);
    svg.append(node('text', {
      x: column === 2 ? x + 10 : x - 10,
      y: y + 4,
      'text-anchor': column === 2 ? 'start' : 'end',
    }, layout.names[index]));
  }
}
