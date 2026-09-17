// 矢量图标工具库：pathData 解析 / 仿射变换 / 发射 / bbox / 圆弧采样
// 关键点：parseAbs 与 flatten 都必须处理 S/s、T/t 平滑曲线（反射上一控制点），
// 否则会整段丢路径 —— 这是本项目踩过的坑，别退回旧实现。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const ROOT = path.resolve(HERE, '../../../../');       // 工作区根
export const DRAWABLE = path.join(ROOT, 'client/app/src/main/res/drawable');
export const ASSETS = path.join(ROOT, 'client/docs/assets');   // 用户放新下载 SVG 的地方
export const WEB_ICONS = path.join(ROOT, 'frontend/src/assets/icons'); // Web 端图标源

export const P = (v, n = 2) => Math.round(v * 10 ** n) / 10 ** n;

/** pathData -> 绝对命令段。支持 M L H V C S Q T A Z（S/T 展开为 C/Q） */
export function parseAbs(d) {
  const toks = [];
  const re = /([MmLlHhVvCcSsQqTtAaZz])|(-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?)/g;
  let m;
  while ((m = re.exec(d))) toks.push(m[1] ?? parseFloat(m[2]));
  const segs = [];
  let cx = 0, cy = 0, sx = 0, sy = 0, cmd = null, pcx = null, pcy = null, prev = '';
  let i = 0;
  const num = () => toks[i++];
  while (i < toks.length) {
    if (typeof toks[i] === 'string') cmd = toks[i++];
    else if (cmd === 'M') cmd = 'L';
    else if (cmd === 'm') cmd = 'l';
    const rel = cmd >= 'a' && cmd <= 'z';
    const up = cmd.toUpperCase();
    const A2 = (x, y) => (rel ? [x + cx, y + cy] : [x, y]);
    switch (up) {
      case 'M': { const [x, y] = A2(num(), num()); segs.push(['M', x, y]); cx = sx = x; cy = sy = y; break; }
      case 'L': { const [x, y] = A2(num(), num()); segs.push(['L', x, y]); cx = x; cy = y; break; }
      case 'H': { let x = num(); if (rel) x += cx; segs.push(['L', x, cy]); cx = x; break; }
      case 'V': { let y = num(); if (rel) y += cy; segs.push(['L', cx, y]); cy = y; break; }
      case 'C': {
        const [x1, y1] = A2(num(), num()), [x2, y2] = A2(num(), num()), [x, y] = A2(num(), num());
        segs.push(['C', x1, y1, x2, y2, x, y]); pcx = x2; pcy = y2; cx = x; cy = y; break;
      }
      case 'S': {
        const [x2, y2] = A2(num(), num()), [x, y] = A2(num(), num());
        const ok = prev === 'C' || prev === 'S';
        segs.push(['C', ok ? 2 * cx - pcx : cx, ok ? 2 * cy - pcy : cy, x2, y2, x, y]);
        pcx = x2; pcy = y2; cx = x; cy = y; break;
      }
      case 'Q': {
        const [x1, y1] = A2(num(), num()), [x, y] = A2(num(), num());
        segs.push(['Q', x1, y1, x, y]); pcx = x1; pcy = y1; cx = x; cy = y; break;
      }
      case 'T': {
        const [x, y] = A2(num(), num());
        const ok = prev === 'Q' || prev === 'T';
        segs.push(['Q', ok ? 2 * cx - pcx : cx, ok ? 2 * cy - pcy : cy, x, y]);
        pcx = ok ? 2 * cx - pcx : cx; pcy = ok ? 2 * cy - pcy : cy; cx = x; cy = y; break;
      }
      case 'A': {
        const rx = num(), ry = num(), rot = num(), la = num(), sw = num();
        const [x, y] = A2(num(), num());
        segs.push(['A', rx, ry, rot, la, sw, x, y]); cx = x; cy = y; break;
      }
      case 'Z': { segs.push(['Z']); cx = sx; cy = sy; break; }
      default: i++;
    }
    prev = up;
  }
  return segs;
}

/** 均匀缩放 + 平移；圆弧半径同比缩放 */
export function applyTransform(segs, s, tx, ty) {
  return segs.map((g) => {
    if (g[0] === 'Z') return ['Z'];
    if (g[0] === 'A') return ['A', g[1] * s, g[2] * s, g[3], g[4], g[5], g[6] * s + tx, g[7] * s + ty];
    const out = [g[0]];
    for (let k = 1; k < g.length; k += 2) out.push(g[k] * s + tx, g[k + 1] * s + ty);
    return out;
  });
}

export function emit(segs, prec = 2) {
  const f = (v) => String(Math.round(v * 10 ** prec) / 10 ** prec);
  let out = '';
  for (const g of segs) {
    if (g[0] === 'Z') { out += 'Z'; continue; }
    if (g[0] === 'A') { out += `A${f(g[1])} ${f(g[2])} 0 ${g[4]} ${g[5]} ${f(g[6])} ${f(g[7])}`; continue; }
    out += g[0] + g.slice(1).map(f).join(' ');
  }
  return out;
}

/** 曲线采样求 bbox */
export function bbox(segs) {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  const add = (x, y) => { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); };
  let cx = 0, cy = 0;
  for (const g of segs) {
    const c = g[0];
    if (c === 'M' || c === 'L') { add(g[1], g[2]); cx = g[1]; cy = g[2]; }
    else if (c === 'C') { for (let t = 0; t <= 1; t += 0.02) { const u = 1 - t;
      add(u*u*u*cx + 3*u*u*t*g[1] + 3*u*t*t*g[3] + t*t*t*g[5], u*u*u*cy + 3*u*u*t*g[2] + 3*u*t*t*g[4] + t*t*t*g[6]); } cx = g[5]; cy = g[6]; }
    else if (c === 'Q') { for (let t = 0; t <= 1; t += 0.02) { const u = 1 - t;
      add(u*u*cx + 2*u*t*g[1] + t*t*g[3], u*u*cy + 2*u*t*g[2] + t*t*g[4]); } cx = g[3]; cy = g[4]; }
    else if (c === 'A') { arcPoints(cx, cy, g[1], g[2], g[3], g[4], g[5], g[6], g[7], add); cx = g[6]; cy = g[7]; }
  }
  return { x0, y0, x1, y1 };
}

/** 端点参数圆弧 -> 采样点 */
export function arcPoints(x0, y0, rx, ry, rot, la, sw, x, y, cb) {
  if (rx === 0 || ry === 0) { cb(x, y); return; }
  rx = Math.abs(rx); ry = Math.abs(ry);
  const phi = (rot * Math.PI) / 180, cosP = Math.cos(phi), sinP = Math.sin(phi);
  const dx2 = (x0 - x) / 2, dy2 = (y0 - y) / 2;
  const x1p = cosP * dx2 + sinP * dy2, y1p = -sinP * dx2 + cosP * dy2;
  const lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
  if (lam > 1) { const k = Math.sqrt(lam); rx *= k; ry *= k; }
  const num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
  const den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
  const co = (la !== sw ? 1 : -1) * Math.sqrt(Math.max(0, num / den));
  const cxp = (co * rx * y1p) / ry, cyp = (-co * ry * x1p) / rx;
  const ccx = cosP * cxp - sinP * cyp + (x0 + x) / 2, ccy = sinP * cxp + cosP * cyp + (y0 + y) / 2;
  const ang = (ux, uy, vx, vy) => {
    let a = Math.acos(Math.min(1, Math.max(-1, (ux*vx + uy*vy) / (Math.hypot(ux, uy) * Math.hypot(vx, vy)))));
    if (ux*vy - uy*vx < 0) a = -a;
    return a;
  };
  const th1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
  let dth = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
  if (!sw && dth > 0) dth -= 2 * Math.PI;
  if (sw && dth < 0) dth += 2 * Math.PI;
  const n = Math.max(8, Math.ceil(Math.abs(dth) / 0.02));
  for (let i = 0; i <= n; i++) {
    const a = th1 + (dth * i) / n;
    cb(cosP * rx * Math.cos(a) - sinP * ry * Math.sin(a) + ccx,
       sinP * rx * Math.cos(a) + cosP * ry * Math.sin(a) + ccy);
  }
}

/** pathData -> 折线点集（默认每段子路径一个数组） */
export function flatten(d, step = 0.01) {
  const segs = parseAbs(d);
  const out = [];
  let cur = null, cx = 0, cy = 0;
  const push = (x, y) => { cur.push([x, y]); cx = x; cy = y; };
  for (const g of segs) {
    if (g[0] === 'M') { cur = [[g[1], g[2]]]; out.push(cur); cx = g[1]; cy = g[2]; continue; }
    if (!cur) continue;
    if (g[0] === 'L') push(g[1], g[2]);
    else if (g[0] === 'C') { const x0 = cx, y0 = cy;
      for (let t = step; t <= 1.0001; t += step) { const u = 1 - t;
        cur.push([u*u*u*x0 + 3*u*u*t*g[1] + 3*u*t*t*g[3] + t*t*t*g[5],
                  u*u*u*y0 + 3*u*u*t*g[2] + 3*u*t*t*g[4] + t*t*t*g[6]]); }
      cx = g[5]; cy = g[6]; }
    else if (g[0] === 'Q') { const x0 = cx, y0 = cy;
      for (let t = step; t <= 1.0001; t += step) { const u = 1 - t;
        cur.push([u*u*x0 + 2*u*t*g[1] + t*t*g[3], u*u*y0 + 2*u*t*g[2] + t*t*g[4]]); }
      cx = g[3]; cy = g[4]; }
    else if (g[0] === 'A') { arcPoints(cx, cy, g[1], g[2], g[3], g[4], g[5], g[6], g[7], (x, y) => cur.push([x, y])); cx = g[6]; cy = g[7]; }
  }
  return out.filter((s) => s.length >= 2);
}

/** 点到线段集最短距离 */
export function minDistToPolyline(px, py, poly) {
  let best = Infinity;
  for (let i = 0; i < poly.length - 1; i++) {
    const ax = poly[i][0], ay = poly[i][1], bx = poly[i+1][0], by = poly[i+1][1];
    const dx = bx - ax, dy = by - ay, l2 = dx*dx + dy*dy;
    let t = l2 ? ((px - ax) * dx + (py - ay) * dy) / l2 : 0;
    t = Math.max(0, Math.min(1, t));
    const d = Math.hypot(px - (ax + t*dx), py - (ay + t*dy));
    if (d < best) best = d;
  }
  return best;
}

/** 拆子路径 */
export function splitSubpaths(segs) {
  const st = segs.map((g, i) => (g[0] === 'M' ? i : -1)).filter((i) => i >= 0);
  return st.map((a, i) => segs.slice(a, st[i + 1] ?? segs.length));
}

/** 读 drawable 里的图标定义 */
export function readIcon(file) {
  const xml = fs.readFileSync(path.isAbsolute(file) ? file : path.join(DRAWABLE, file), 'utf8');
  const vp = parseFloat(/viewportWidth="([\d.]+)"/.exec(xml)[1]);
  const wdp = parseFloat(/android:width="([\d.]+)dp"/.exec(xml)[1]);
  const sw = parseFloat((/android:strokeWidth="([\d.]+)"/.exec(xml) ?? [0, '0'])[1]);
  const hasFill = /android:fillColor="#FF000000"/.test(xml);
  const evenOdd = /fillType="evenOdd"/.test(xml);
  const d = [...xml.matchAll(/pathData="([^"]+)"/g)].map((m) => m[1]);
  return { xml, vp, wdp, sw, hasFill, evenOdd, ds: d, per: 24 / vp };
}

export const vecHead = (vp = 24, dp = 24) =>
  `<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n` +
  `    android:width="${dp}dp"\n    android:height="${dp}dp"\n    android:viewportWidth="${vp}"\n    android:viewportHeight="${vp}">\n`;

export const writeXml = (name, xml) => {
  const p = path.isAbsolute(name) ? name : path.join(DRAWABLE, name);
  fs.writeFileSync(p, xml);
  console.log('  ->', path.basename(p));
};

/** 找到某个文件的同名字段做替换（用于在既有 XML 上改 pathData/strokeWidth） */
export function svgSegs(file) {
  const svg = fs.readFileSync(path.isAbsolute(file) ? file : path.join(ASSETS, file), 'utf8');
  return parseAbs([...svg.matchAll(/<path[^>]*\sd="([^"]+)"/g)].map((m) => m[1]).join(''));
}
