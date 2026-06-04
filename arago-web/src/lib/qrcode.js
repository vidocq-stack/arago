// Tiny zero-dependency QR Code generator (byte mode, ECC level L, versions 1-5 → up to ~106 bytes,
// enough for the URLs Arago pins). Self-contained: no npm/runtime dependency, bundled in arago-web.
// Output is a boolean module matrix (true = dark); `toSvg` renders it. Verified scannable by decoding
// the generated codes with jsQR during development (arago-spec §4.4).
//
// Implements: ISO/IEC 18004 — Reed-Solomon over GF(256) (primitive 0x11d), finder/timing/alignment
// function patterns, the 8 data masks with penalty-based selection, and BCH format information.

const EC_LEVEL_L = 1; // format-info indicator for level L

// Per-version (1-5), level L: total codewords, data codewords (=> EC codewords = total - data),
// and the single alignment-pattern centre coordinate (null for v1). Single EC block (no interleaving).
const VERSIONS = {
  1: { total: 26,  data: 19,  align: null },
  2: { total: 44,  data: 34,  align: 18 },
  3: { total: 70,  data: 55,  align: 22 },
  4: { total: 100, data: 80,  align: 26 },
  5: { total: 134, data: 108, align: 30 },
};

// ---- GF(256) ----
const EXP = new Uint8Array(512);
const LOG = new Uint8Array(256);
(() => {
  let x = 1;
  for (let i = 0; i < 255; i++) {
    EXP[i] = x;
    LOG[x] = i;
    x <<= 1;
    if (x & 0x100) x ^= 0x11d;
  }
  for (let i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
})();
const gfMul = (a, b) => (a === 0 || b === 0 ? 0 : EXP[LOG[a] + LOG[b]]);

// Reed-Solomon divisor + remainder (canonical algorithm): divisor holds the generator polynomial
// coefficients with the implicit leading 1 omitted (length = degree).
function rsGenerator(degree) {
  const result = new Array(degree).fill(0);
  result[degree - 1] = 1; // start at the monomial 1
  let root = 1;
  for (let i = 0; i < degree; i++) {
    for (let j = 0; j < degree; j++) {
      result[j] = gfMul(result[j], root);
      if (j + 1 < degree) result[j] ^= result[j + 1];
    }
    root = gfMul(root, 0x02);
  }
  return result;
}

function rsEncode(data, ecLen) {
  const divisor = rsGenerator(ecLen);
  const result = new Array(ecLen).fill(0);
  for (const b of data) {
    const factor = b ^ result.shift();
    result.push(0);
    for (let i = 0; i < ecLen; i++) result[i] ^= gfMul(divisor[i], factor);
  }
  return result;
}

// ---- bit buffer ----
function dataCodewords(bytes, version) {
  const { data: dataCw } = VERSIONS[version];
  const bits = [];
  const push = (val, len) => { for (let i = len - 1; i >= 0; i--) bits.push((val >> i) & 1); };
  push(0b0100, 4);           // byte mode
  push(bytes.length, 8);     // character count (8 bits for v1-9)
  for (const b of bytes) push(b, 8);
  const capacity = dataCw * 8;
  // terminator (up to 4 zero bits)
  for (let i = 0; i < 4 && bits.length < capacity; i++) bits.push(0);
  while (bits.length % 8 !== 0) bits.push(0); // pad to a byte boundary
  const cw = [];
  for (let i = 0; i < bits.length; i += 8) {
    let v = 0;
    for (let j = 0; j < 8; j++) v = (v << 1) | bits[i + j];
    cw.push(v);
  }
  const pads = [0xec, 0x11];
  for (let i = 0; cw.length < dataCw; i++) cw.push(pads[i % 2]);
  return cw;
}

// ---- matrix placement ----
function newMatrix(size) {
  return Array.from({ length: size }, () => new Array(size).fill(null));
}

function placeFinder(m, r, c) {
  for (let dr = -1; dr <= 7; dr++) {
    for (let dc = -1; dc <= 7; dc++) {
      const rr = r + dr;
      const cc = c + dc;
      if (rr < 0 || cc < 0 || rr >= m.length || cc >= m.length) continue;
      const inRing = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6
        && (dr === 0 || dr === 6 || dc === 0 || dc === 6);
      const inCore = dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4;
      m[rr][cc] = inRing || inCore;
    }
  }
}

function reserve(m, r, c) { if (m[r][c] === null) m[r][c] = false; }

function buildFunctionPatterns(version) {
  const size = 17 + version * 4;
  const m = newMatrix(size);
  placeFinder(m, 0, 0);
  placeFinder(m, 0, size - 7);
  placeFinder(m, size - 7, 0);
  // timing patterns
  for (let i = 8; i < size - 8; i++) {
    m[6][i] = i % 2 === 0;
    m[i][6] = i % 2 === 0;
  }
  // alignment pattern (single, centred) for v2-5
  const a = VERSIONS[version].align;
  if (a !== null) {
    for (let dr = -2; dr <= 2; dr++) {
      for (let dc = -2; dc <= 2; dc++) {
        const ring = Math.max(Math.abs(dr), Math.abs(dc));
        m[a + dr][a + dc] = ring !== 1;
      }
    }
  }
  // dark module
  m[size - 8][8] = true;
  // reserve format-info areas (filled later)
  for (let i = 0; i <= 8; i++) { reserve(m, 8, i); reserve(m, i, 8); }
  for (let i = 0; i < 8; i++) { reserve(m, 8, size - 1 - i); reserve(m, size - 1 - i, 8); }
  return m;
}

function placeData(m, allCw) {
  const size = m.length;
  const bits = [];
  for (const cw of allCw) for (let i = 7; i >= 0; i--) bits.push((cw >> i) & 1);
  let bi = 0;
  let upward = true;
  for (let col = size - 1; col > 0; col -= 2) {
    if (col === 6) col = 5; // skip the vertical timing column
    for (let i = 0; i < size; i++) {
      const row = upward ? size - 1 - i : i;
      for (let dc = 0; dc < 2; dc++) {
        const c = col - dc;
        if (m[row][c] === null) {
          m[row][c] = bi < bits.length ? bits[bi++] === 1 : false;
        }
      }
    }
    upward = !upward;
  }
}

const MASKS = [
  (r, c) => (r + c) % 2 === 0,
  (r) => r % 2 === 0,
  (r, c) => c % 3 === 0,
  (r, c) => (r + c) % 3 === 0,
  (r, c) => (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0,
  (r, c) => ((r * c) % 2) + ((r * c) % 3) === 0,
  (r, c) => (((r * c) % 2) + ((r * c) % 3)) % 2 === 0,
  (r, c) => (((r + c) % 2) + ((r * c) % 3)) % 2 === 0,
];

function isFunction(fp, r, c) { return fp[r][c] !== null; }

function applyMask(data, fp, maskIdx) {
  const size = data.length;
  const out = data.map((row) => row.slice());
  const mask = MASKS[maskIdx];
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      if (!isFunction(fp, r, c) && mask(r, c)) out[r][c] = !out[r][c];
    }
  }
  return out;
}

// BCH(15,5) format info for (level, mask), masked with 0x5412.
function formatBits(level, maskIdx) {
  const data = (level << 3) | maskIdx; // level: L=01
  let rem = data;
  for (let i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) & 1 ? 0x537 : 0);
  return ((data << 10) | rem) ^ 0x5412;
}

const FORMAT_POS_1 = [[8, 0], [8, 1], [8, 2], [8, 3], [8, 4], [8, 5], [8, 7], [8, 8],
  [7, 8], [5, 8], [4, 8], [3, 8], [2, 8], [1, 8], [0, 8]];

function placeFormat(m, level, maskIdx) {
  const size = m.length;
  const bits = formatBits(level, maskIdx);
  const bit = (i) => ((bits >> i) & 1) === 1;
  // First copy around the top-left finder: MSB-first along FORMAT_POS_1.
  FORMAT_POS_1.forEach(([r, c], i) => { m[r][c] = bit(14 - i); });
  // Second copy: bits 0-7 along row 8 (cols size-1 .. size-8), bits 8-14 up col 8 (rows size-7 .. size-1).
  for (let i = 0; i <= 7; i++) m[8][size - 1 - i] = bit(i);
  for (let i = 0; i <= 6; i++) m[size - 7 + i][8] = bit(8 + i);
}

function penalty(m) {
  const size = m.length;
  let score = 0;
  // rule 1: runs of 5+ same colour
  for (let r = 0; r < size; r++) {
    for (const line of [m[r], m.map((row) => row[r])]) {
      let run = 1;
      for (let i = 1; i < size; i++) {
        if (line[i] === line[i - 1]) { run++; if (run === 5) score += 3; else if (run > 5) score++; }
        else run = 1;
      }
    }
  }
  // rule 2: 2x2 blocks
  for (let r = 0; r < size - 1; r++) {
    for (let c = 0; c < size - 1; c++) {
      const v = m[r][c];
      if (v === m[r][c + 1] && v === m[r + 1][c] && v === m[r + 1][c + 1]) score += 3;
    }
  }
  // rule 3: finder-like patterns
  const pat = [true, false, true, true, true, false, true];
  const hasAt = (line, i) => pat.every((p, k) => line[i + k] === p);
  for (let r = 0; r < size; r++) {
    const rows = m[r];
    const cols = m.map((row) => row[r]);
    for (let i = 0; i + 7 <= size; i++) {
      if (hasAt(rows, i)) score += 40;
      if (hasAt(cols, i)) score += 40;
    }
  }
  // rule 4: dark/light balance
  let dark = 0;
  for (let r = 0; r < size; r++) for (let c = 0; c < size; c++) if (m[r][c]) dark++;
  const ratio = (dark * 100) / (size * size);
  score += Math.floor(Math.abs(ratio - 50) / 5) * 10;
  return score;
}

function utf8Bytes(text) {
  return Array.from(new TextEncoder().encode(text));
}

/** Builds the QR module matrix (array of boolean rows) for `text`, or null if it does not fit v1-5/L. */
export function generate(text) {
  const bytes = utf8Bytes(text);
  let version = null;
  for (let v = 1; v <= 5; v++) {
    const cap = VERSIONS[v].data - 2; // minus 1.5 bytes header rounded; safe lower bound
    if (bytes.length <= cap) { version = v; break; }
  }
  if (version === null) return null;

  const { total, data: dataCw } = VERSIONS[version];
  const cw = dataCodewords(bytes, version);
  const ec = rsEncode(cw, total - dataCw);
  const all = cw.concat(ec);

  const fp = buildFunctionPatterns(version);
  const base = fp.map((row) => row.slice());
  placeData(base, all);

  let best = null;
  let bestScore = Infinity;
  for (let mask = 0; mask < 8; mask++) {
    const m = applyMask(base, fp, mask);
    placeFormat(m, EC_LEVEL_L, mask);
    const s = penalty(m);
    if (s < bestScore) { bestScore = s; best = m; }
  }
  return best;
}

/** Renders a module matrix as a crisp SVG string with a quiet zone. */
export function toSvg(matrix, { size = 200, margin = 4 } = {}) {
  const n = matrix.length;
  const dim = n + margin * 2;
  let path = '';
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      if (matrix[r][c]) path += `M${c + margin},${r + margin}h1v1h-1z`;
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" `
    + `viewBox="0 0 ${dim} ${dim}" shape-rendering="crispEdges" role="img">`
    + `<rect width="${dim}" height="${dim}" fill="#fff"/>`
    + `<path d="${path}" fill="#000"/></svg>`;
}
