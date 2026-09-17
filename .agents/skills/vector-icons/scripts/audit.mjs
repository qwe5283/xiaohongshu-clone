#!/usr/bin/env node
// 图标体检：量出每个图标的「可见线宽」与「图形外缘占位」，并标出偏离规范的项目。
//
//   node audit.mjs                       # 扫 drawable 目录
//   node audit.mjs ic_heart.xml ic_close.xml
//   node audit.mjs --json dump.json      # 导出折线几何（给 contact-sheet.py 用）
//   node audit.mjs --dir <path>          # 换目录（默认 client/app/src/main/res/drawable）
//
// 判读规则（本套规范：24 视口 / 线宽 1.8dp / 占位 18~20dp）：
//   线宽 = 两条近平行轮廓的间距 + strokeWidth；单条中线描边字形 = strokeWidth 本身。
//   「近平行」判据：p10~p90 的离散度 < 中位的 40%（否则那是两个部件之间的空隙，不是线宽）。
// 注意：实心字形（如 ic_user、ic_play）没有线宽概念，只报占位。
import fs from 'node:fs';
import path from 'node:path';
import { DRAWABLE, flatten, minDistToPolyline, readIcon, P } from './lib.mjs';

const TARGET_LINE = 1.8, LINE_TOL = 0.15;      // 线宽容差 ±0.15dp
const BAND = [17.0, 20.5];                     // 占位容忍区间（按长边判；18~20 为目标）
const EXEMPT_SIZE = ['ic_close', 'ic_chevron_left', 'ic_chevron_right', 'ic_menu', 'ic_play', 'ic_more'];

const argv = process.argv.slice(2);
let jsonOut = null, dir = DRAWABLE, files = [];
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === '--json') jsonOut = argv[++i];
  else if (argv[i] === '--dir') dir = argv[++i];
  else files.push(argv[i]);
}
const list = files.length ? files : fs.readdirSync(dir).filter((f) => f.endsWith('.xml') && !f.startsWith('ic_launcher'));

/** 带符号面积：正负代表绕向。相对的两条轮廓才能构成「一条线」，同向的是两个部件。 */
function signedArea(sp) {
  let a = 0;
  for (let i = 0; i < sp.length - 1; i++) a += sp[i][0] * sp[i + 1][1] - sp[i + 1][0] * sp[i][1];
  if (sp[0][0] !== sp[sp.length - 1][0] || sp[0][1] !== sp[sp.length - 1][1]) {
    a += sp[sp.length - 1][0] * sp[0][1] - sp[0][0] * sp[sp.length - 1][1];
  }
  return a / 2;
}
/** 自环判定：前缀面积的最大幅值显著超过净面积 => 这条轮廓走了外圈又折回内圈 */
function isSelfLapping(sp) {
  let s = 0, best = 0;
  for (let i = 0; i < sp.length - 1; i++) {
    s += sp[i][0] * sp[i + 1][1] - sp[i + 1][0] * sp[i][1];
    if (Math.abs(s) > Math.abs(best)) best = s;
  }
  const net = Math.abs(signedArea(sp));
  return Math.abs(best) / 2 > net * 1.2 + 1e-6;
}
const polyArea = (sp) => Math.abs(signedArea(sp));
const polyLen = (sp) => {
  let l = 0;
  for (let i = 0; i < sp.length - 1; i++) l += Math.hypot(sp[i + 1][0] - sp[i][0], sp[i + 1][1] - sp[i][1]);
  return l;
};
/** 某点在整个路径（nonzero 规则）下的绕数；≠0 = 那里是墨迹 */
function windingAt(subs, px, py) {
  let w = 0;
  for (const sp of subs) for (let i = 0; i < sp.length - 1; i++) {
    const [x1, y1] = sp[i], [x2, y2] = sp[i + 1];
    if ((y1 <= py && py < y2) || (y2 <= py && py < y1)) {
      const xi = x1 + ((py - y1) * (x2 - x1)) / (y2 - y1);
      if (xi > px) w += y2 > y1 ? 1 : -1;
    }
  }
  return w;
}
/** 两条轮廓之间是否真的是墨迹（=同一条线的两侧）。两块独立实心之间是背景，不是线宽 */
function isInkBetween(subs, A, B) {
  let ink = 0, tested = 0;
  for (let i = 0; i < A.length; i += Math.max(1, Math.floor(A.length / 12))) {
    const p = A[i];
    let best = Infinity, q = null;
    for (let j = 0; j < B.length - 1; j++) {
      const ax = B[j][0], ay = B[j][1], bx = B[j + 1][0], by = B[j + 1][1];
      const dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
      let t = l2 ? ((p[0] - ax) * dx + (p[1] - ay) * dy) / l2 : 0;
      t = Math.max(0, Math.min(1, t));
      const d = Math.hypot(p[0] - (ax + t * dx), p[1] - (ay + t * dy));
      if (d < best) { best = d; q = [ax + t * dx, ay + t * dy]; }
    }
    if (!q || best > 6) continue;
    tested++;
    if (windingAt(subs, (p[0] + q[0]) / 2, (p[1] + q[1]) / 2) !== 0) ink++;
  }
  return tested > 0 && ink / tested > 0.7;
}

// 已知例外：不按 1.8dp/18~20dp 判（各自有独立场景，改了反而错）
const KNOWN = {
  'ic_ai_new_chat': 'AI 页图标族（2.0dp）', 'ic_ai_send': 'AI 页图标族（2.4dp）',
  'ic_ai_sidebar_toggle': 'AI 页图标族（2.0dp）', 'ic_male': '行内性别徽标',
  'ic_female': '行内性别徽标', 'ic_login': '插画（非图标）', 'ic_placeholder_illus': '占位插画',
};

function measure(file) {
  const ic = readIcon(path.join(dir, file));
  const subs = ic.ds.flatMap((d) => flatten(d));
  const bb = { x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity };
  for (const sp of subs) for (const [x, y] of sp) {
    bb.x0 = Math.min(bb.x0, x); bb.y0 = Math.min(bb.y0, y);
    bb.x1 = Math.max(bb.x1, x); bb.y1 = Math.max(bb.y1, y);
  }
  const boxW = (bb.x1 - bb.x0 + ic.sw) * ic.per, boxH = (bb.y1 - bb.y0 + ic.sw) * ic.per;
  let line = null, how = '实心', ambiguous = false;

  if (subs.length > 20) { how = '插画（多子路径，不测线宽）'; }
  else if (!ic.hasFill && ic.sw > 0) { line = ic.sw * ic.per; how = '描边式'; }
  else if (subs.length === 1 && ic.hasFill) { how = ic.sw > 0 ? `实心（+${P(ic.sw)} 外扩）` : '实心'; }
  else if (subs.length === 1) {
    const sp = subs[0];
    if (isSelfLapping(sp)) { line = (2 * polyArea(sp) / polyLen(sp)) * ic.per; how = '自环(2A/P)'; }
  } else if (subs.length >= 2) {
    const signs = subs.map((sp) => signedArea(sp) > 0);
    const vals = [];
    for (let i = 0; i < subs.length; i++) for (let j = 0; j < subs.length; j++) {
      if (i === j) continue;
      if (!ic.evenOdd && signs[i] === signs[j]) continue;   // 同向 = 两个部件
      if (!isInkBetween(subs, subs[i], subs[j])) continue;  // 轮廓之间是背景 = 也是两个部件
      const ds = subs[i].map(([x, y]) => minDistToPolyline(x, y, subs[j])).filter((v) => v < 4 / ic.per);
      if (ds.length < 4) continue;
      const s = [...ds].sort((a, b) => a - b);
      const lo = s[Math.floor(s.length * 0.1)], hi = s[Math.floor(s.length * 0.9)], m = s[s.length >> 1];
      if ((hi - lo) < 0.4 * m) vals.push((m + ic.sw) * ic.per);
    }
    if (vals.length) {
      const uniq = [...new Set(vals.map((v) => Math.round(v * 20) / 20))].sort((a, b) => a - b);
      if (uniq.length === 1) { line = uniq[0]; how = ic.sw > 0 ? `间距+${P(ic.sw)}描边` : '间距'; }
      // 多部件字形可能量出多组近平行间距（部件之间的空隙也会看着"平行"）——不自作判断，报出来让人判读
      else { line = null; how = `多处量值 ${uniq.map((v) => P(v)).join('/')}dp`; ambiguous = true; }
    }
  }
  return { file, per: ic.per, sw: ic.sw, boxW, boxH, line, how, ambiguous, subs: subs.length };
}

if (jsonOut) {
  const out = {};
  for (const f of list) {
    const ic = readIcon(path.join(dir, f));
    out[f] = { vw: ic.vp, wdp: ic.wdp, paths: ic.ds.map((d) => flatten(d)) };
  }
  fs.writeFileSync(jsonOut, JSON.stringify(out));
  console.log(`导出 ${Object.keys(out).length} 个图标的几何 -> ${jsonOut}`);
  process.exit(0);
}

console.log('文件'.padEnd(26) + '视口'.padEnd(6) + '外缘(含描边)'.padEnd(20) + '可见线宽'.padEnd(22) + '判定');
console.log('-'.repeat(96));
let bad = 0;
for (const f of list) {
  let r;
  try { r = measure(f); } catch { continue; }
  const base = path.basename(f, '.xml');
  const major = Math.max(r.boxW, r.boxH);          // 占位按长边判：宽扁字形（如 ic_share）短边天然更小
  const sizeOk = major >= BAND[0] && major <= BAND[1];
  const exempt = EXEMPT_SIZE.includes(base);
  const known = KNOWN[base];
  let verdict = '✓';
  if (known) verdict = `— ${known}`;
  else if (r.ambiguous) verdict = '△ 量值不一致，人工判读（看上面的"多处量值"）';
  else if (r.line != null && Math.abs(r.line - TARGET_LINE) > LINE_TOL) { verdict = `✗ 线宽偏离 ${P(r.line)}dp`; bad++; }
  else if (!sizeOk && !exempt) { verdict = `△ 占位长边 ${P(major)}dp 出区间`; bad++; }
  else if (!sizeOk && exempt) verdict = '✓（工具类字形，尺寸例外）';
  console.log(f.padEnd(26) + String(24 / r.per === 24 ? 24 : P(24 / r.per, 0)).padEnd(6) +
    `${P(r.boxW)}×${P(r.boxH)}dp`.padEnd(20) +
    (r.line != null ? `${P(r.line)}dp` : r.how).padEnd(22) + verdict);
}
console.log(`\n${list.length} 个图标，${bad} 个需处理。`);
