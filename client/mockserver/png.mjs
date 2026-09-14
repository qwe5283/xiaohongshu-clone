// Zero-dependency PNG encoder + deterministic placeholder artwork generator.
// Only node:zlib + node:buffer are used.
import zlib from 'node:zlib';

/* ------------------------------------------------------------------ *
 * CRC32 (PNG chunk checksum)
 * ------------------------------------------------------------------ */
const CRC_TABLE = new Int32Array(256);
for (let n = 0; n < 256; n++) {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  CRC_TABLE[n] = c;
}

export function crc32(buf) {
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}

/* ------------------------------------------------------------------ *
 * Minimal PNG writer: 8-bit RGB (colour type 2), filter 0 per scanline.
 * ------------------------------------------------------------------ */
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const t = Buffer.from(type, 'latin1');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([t, data])), 0);
  return Buffer.concat([len, t, data, crc]);
}

/** @param {Uint8Array} rgb tightly packed w*h*3 bytes */
export function encodePNG(width, height, rgb) {
  const stride = width * 3;
  const raw = Buffer.alloc(height * (stride + 1));
  for (let y = 0; y < height; y++) {
    const rowStart = y * (stride + 1);
    raw[rowStart] = 0; // filter type 0 (None)
    raw.set(rgb.subarray(y * stride, y * stride + stride), rowStart + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // colour type: truecolour RGB
  ihdr[10] = 0; // compression: deflate
  ihdr[11] = 0; // filter: adaptive
  ihdr[12] = 0; // interlace: none
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 6 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/* ------------------------------------------------------------------ *
 * Hashing / PRNG helpers
 * ------------------------------------------------------------------ */
export function hash32(str) {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619) >>> 0;
  }
  return h >>> 0;
}

export function mulberry32(seed) {
  let a = seed >>> 0;
  return function next() {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/* ------------------------------------------------------------------ *
 * Colour helpers
 * ------------------------------------------------------------------ */
function hsl2rgb(h, s, l) {
  h = ((h % 360) + 360) % 360;
  s /= 100;
  l /= 100;
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const hp = h / 60;
  const x = c * (1 - Math.abs((hp % 2) - 1));
  let r = 0;
  let g = 0;
  let b = 0;
  if (hp < 1) [r, g, b] = [c, x, 0];
  else if (hp < 2) [r, g, b] = [x, c, 0];
  else if (hp < 3) [r, g, b] = [0, c, x];
  else if (hp < 4) [r, g, b] = [0, x, c];
  else if (hp < 5) [r, g, b] = [x, 0, c];
  else [r, g, b] = [c, 0, x];
  const m = l - c / 2;
  return [
    Math.round(Math.min(255, Math.max(0, (r + m) * 255))),
    Math.round(Math.min(255, Math.max(0, (g + m) * 255))),
    Math.round(Math.min(255, Math.max(0, (b + m) * 255))),
  ];
}

/* ------------------------------------------------------------------ *
 * Deterministic placeholder artwork.
 *
 * mode:
 *   'post'   gradient + subtle checkerboard + centred rounded-square motif
 *   'avatar' gradient + centred circle + inner ring
 *   'text'   gradient + rounded-square motif + faux "text lines"
 *
 * Text is NOT rasterised as glyphs (Node core ships no font) — it only
 * seeds the artwork deterministically. See README "Known limitations".
 * ------------------------------------------------------------------ */
function roundedRectInside(x, y, cx, cy, half, rad) {
  const dx = Math.abs(x - cx);
  const dy = Math.abs(y - cy);
  if (dx > half || dy > half) return false;
  if (dx <= half - rad || dy <= half - rad) return true;
  const ex = dx - (half - rad);
  const ey = dy - (half - rad);
  return ex * ex + ey * ey <= rad * rad;
}

export function generateImage(width, height, seed, mode = 'post') {
  const key = `${seed}|${mode}`;
  const h0 = hash32(key);

  const hueA = h0 % 360;
  const hueB = (hueA + 35 + ((h0 >>> 9) % 90)) % 360;
  const sat = 46 + ((h0 >>> 5) % 26); // 46..71
  const lightA = 77 + ((h0 >>> 13) % 11); // 77..87
  const lightB = 38 + ((h0 >>> 17) % 18); // 38..55
  const [r1, g1, b1] = hsl2rgb(hueA, sat, lightA);
  const [r2, g2, b2] = hsl2rgb(hueB, sat, lightB);
  const diag = ((h0 >>> 3) & 1) === 1;
  const cell = 26 + ((h0 >>> 21) % 3) * 14; // 26/40/54 px checkerboard

  const w = width;
  const h = height;
  const rgb = new Uint8Array(w * h * 3);
  const invW = 1 / Math.max(1, w - 1);
  const invH = 1 / Math.max(1, h - 1);

  for (let y = 0; y < h; y++) {
    const ty = y * invH;
    for (let x = 0; x < w; x++) {
      const tx = x * invW;
      let t = diag ? 0.62 * tx + 0.38 * ty : ty;
      t = t * t * (3 - 2 * t); // smoothstep
      let rr = r1 + (r2 - r1) * t;
      let gg = g1 + (g2 - g1) * t;
      let bb = b1 + (b2 - b1) * t;
      const chk = (((x / cell) | 0) + ((y / cell) | 0)) & 1;
      const d = chk ? 5 : -5;
      rr += d;
      gg += d;
      bb += d;
      const o = (y * w + x) * 3;
      rgb[o] = rr < 0 ? 0 : rr > 255 ? 255 : rr | 0;
      rgb[o + 1] = gg < 0 ? 0 : gg > 255 ? 255 : gg | 0;
      rgb[o + 2] = bb < 0 ? 0 : bb > 255 ? 255 : bb | 0;
    }
  }

  // high-contrast accent derived from the seed
  const accent = hsl2rgb((hueB + 180) % 360, Math.min(88, sat + 20), 30 + ((h0 >>> 25) % 12));
  const paper = [249, 248, 246];

  if (mode === 'avatar') {
    const cx = w / 2;
    const cy = h / 2;
    const rad = Math.min(w, h) * 0.37;
    const ringOuter = rad * 0.9;
    const ringInner = rad * 0.78;
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const dx = x - cx;
        const dy = y - cy;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist <= rad) {
          const t = dist / rad;
          const o = (y * w + x) * 3;
          const mix = 0.55 + 0.3 * (1 - t);
          rgb[o] = rgb[o] + (paper[0] - rgb[o]) * mix;
          rgb[o + 1] = rgb[o + 1] + (paper[1] - rgb[o + 1]) * mix;
          rgb[o + 2] = rgb[o + 2] + (paper[2] - rgb[o + 2]) * mix;
          if (dist <= ringOuter && dist >= ringInner) {
            const o2 = (y * w + x) * 3;
            rgb[o2] = accent[0];
            rgb[o2 + 1] = accent[1];
            rgb[o2 + 2] = accent[2];
          }
        }
      }
    }
    return encodePNG(w, h, rgb);
  }

  // shared: centred rounded-square motif
  const side = Math.min(w, h) * 0.44;
  const half = side / 2;
  const rad = side * 0.24;
  const cx = w / 2;
  const cy = h / 2;
  const stripe = ((h0 >>> 7) & 1) === 1;
  const y0 = Math.max(0, Math.floor(cy - half) - 1);
  const y1 = Math.min(h - 1, Math.ceil(cy + half) + 1);
  const x0 = Math.max(0, Math.floor(cx - half) - 1);
  const x1 = Math.min(w - 1, Math.ceil(cx + half) + 1);
  for (let y = y0; y <= y1; y++) {
    for (let x = x0; x <= x1; x++) {
      if (!roundedRectInside(x, y, cx, cy, half, rad)) continue;
      const o = (y * w + x) * 3;
      const band = stripe ? ((x + y) % 18) < 9 : ((x - y) % 22) < 11;
      const base = band ? accent : hsl2rgb(hueA, sat, 92);
      const a = band ? 0.85 : 0.55;
      rgb[o] = rgb[o] + (base[0] - rgb[o]) * a;
      rgb[o + 1] = rgb[o + 1] + (base[1] - rgb[o + 1]) * a;
      rgb[o + 2] = rgb[o + 2] + (base[2] - rgb[o + 2]) * a;
    }
  }

  if (mode === 'text') {
    // faux text lines so the 2:3 "文字配图" reads as a text card
    const widths = [0.66, 0.5, 0.72, 0.4, 0.58];
    const barH = Math.max(7, Math.round(h * 0.017));
    const gap = Math.round(barH * 2.6);
    const left = Math.round(w * 0.13);
    let by = Math.round(h * 0.60);
    for (const frac of widths) {
      const bw = Math.round(w * frac);
      for (let y = by; y < Math.min(h, by + barH); y++) {
        for (let x = left; x < Math.min(w, left + bw); x++) {
          const o = (y * w + x) * 3;
          const a = 0.8;
          rgb[o] = rgb[o] + (paper[0] - rgb[o]) * a;
          rgb[o + 1] = rgb[o + 1] + (paper[1] - rgb[o + 1]) * a;
          rgb[o + 2] = rgb[o + 2] + (paper[2] - rgb[o + 2]) * a;
        }
      }
      by += gap;
      if (by >= h) break;
    }
  }

  return encodePNG(w, h, rgb);
}

/** Clamp a requested size and cap the total pixel budget (keeps encoding fast). */
export function clampSize(w, h, defW = 800, defH = 600, maxPixels = 1200 * 1200) {
  let W = Number.parseInt(w, 10);
  let H = Number.parseInt(h, 10);
  if (!Number.isFinite(W) || W <= 0) W = defW;
  if (!Number.isFinite(H) || H <= 0) H = defH;
  W = Math.min(1200, Math.max(8, Math.round(W)));
  H = Math.min(1200, Math.max(8, Math.round(H)));
  if (W * H > maxPixels) {
    const k = Math.sqrt(maxPixels / (W * H));
    W = Math.max(8, Math.floor(W * k));
    H = Math.max(8, Math.floor(H * k));
  }
  return [W, H];
}
