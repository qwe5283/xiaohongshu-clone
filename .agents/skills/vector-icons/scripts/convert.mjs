#!/usr/bin/env node
// 图标转换/修复：SVG -> VectorDrawable、缩放、加粗、配对填充版。
//
//   node convert.mjs svg <file.svg> --art 19.5            # 归一化占位（实心字形）
//   node convert.mjs svg <file.svg> --line 64             # 按线宽归一到 1.8dp（线形字形；64 = 源线宽单位）
//   node convert.mjs stroke <file.svg> --art 19.5 [--w 1.8]  # 输出「中线 + strokeWidth」的描边式矢量
//   node convert.mjs rescale <ic_x.xml> --art 19.5        # 就地改既有图标：图形缩放并居中（源文件没了也能修）
//   node convert.mjs thicken <ic_x.xml> --from 1.63 --to 1.8   # 就地加粗（叠同色描边，形状不变形）
//   node convert.mjs pair <ic_x.xml> --out ic_x_filled.xml     # 由描边版生成同外缘的填充版
//
// 通用选项：--dir <drawable 目录>  --assets <SVG 目录>  --center（默认开）
// 说明：--art 指「图形外缘（含描边）占位」，与 audit.mjs 的口径一致。
import fs from 'node:fs';
import path from 'node:path';
import { parseAbs, applyTransform, emit, bbox, splitSubpaths, ASSETS, DRAWABLE, writeXml, vecHead, svgSegs, P } from './lib.mjs';

const argv = process.argv.slice(2);
const cmd = argv[0];
const opt = {};
const pos = [];
for (let i = 1; i < argv.length; i++) {
  if (argv[i].startsWith('--')) opt[argv[i].slice(2)] = (argv[i + 1] && !argv[i + 1].startsWith('--')) ? argv[++i] : true;
  else pos.push(argv[i]);
}
const f2 = (v) => P(v);
const TARGET_LINE = 1.8;

const D = opt.dir || DRAWABLE;
const abs = (file) => (path.isAbsolute(file) ? file : path.join(file.endsWith('.svg') ? ASSETS : D, file));
const readPath = (file) => {
  const xml = fs.readFileSync(abs(file), 'utf8');
  return { xml, ds: [...xml.matchAll(/pathData="([^"]+)"/g)].map((m) => m[1]) };
};
const segsOf = (file) =>
  file.endsWith('.svg')
    ? svgSegs(path.isAbsolute(file) ? file : path.join(ASSETS, file))  // SVG：把所有 path 合并
    : readPath(file).ds.flatMap((d) => parseAbs(d));   // Android：合并全部 pathData


/** 占位超出建议区间时提示第三条路：先归一化占位，再 thicken 补线宽（两个目标都达成） */
function hintBox(box, art) {
  const major = Math.max(box[0], box[1]);
  if (major > 20.5) console.log(`  ⚠ 占位 ${P(major)}dp 超出建议区间 18~20dp：若既要 1.8dp 线宽又要正常占位，` +
    `请改用 --art ${art} 归一化再用 convert.mjs thicken 补线宽（见 references/casebook.md 的扫帚案例）`);
}

if (!cmd) { console.log(fs.readFileSync(new URL(import.meta.url)).toString().split('\n').slice(1, 16).join('\n')); process.exit(0); }

/** 均匀缩放：把 segs 的图形长边变成 target（含 stroke 时按外缘算），并居中到 24 视口 */
function fit(segs, target, stroke = 0) {
  const bb = bbox(segs);
  const cur = Math.max(bb.x1 - bb.x0, bb.y1 - bb.y0);
  const s = (target - stroke) / cur;
  const t = applyTransform(segs, s, 0, 0);
  const tb = bbox(t);
  const out = applyTransform(segs, s, 12 - (tb.x0 + tb.x1) / 2, 12 - (tb.y0 + tb.y1) / 2);
  const ob = bbox(out);
  return { out, s, box: [ob.x1 - ob.x0 + stroke, ob.y1 - ob.y0 + stroke], stroke };
}
const fillPathXml = (d) => `    <path\n        android:fillColor="#FF000000"\n        android:pathData="${d}"\n        />\n`;
const strokePathXml = (d, w) =>
  `    <path\n        android:pathData="${d}"\n        android:strokeColor="#FF000000"\n        android:strokeWidth="${w}"\n` +
  `        android:strokeLineJoin="round"\n        android:strokeLineCap="round"\n        />\n`;

if (cmd === 'svg') {
  const file = pos[0];
  const segs = segsOf(file);
  const target = parseFloat(opt.art ?? 19.5);
  if (opt.line) {                                    // 按线宽定缩放：s = 1.8 / 源线宽
    const s = TARGET_LINE / parseFloat(opt.line);
    const bb = bbox(segs);
    const t = applyTransform(segs, s, 0, 0);
    const tb = bbox(t);
    const out = applyTransform(segs, s, 12 - (tb.x0 + tb.x1) / 2, 12 - (tb.y0 + tb.y1) / 2);
    console.log(`${path.basename(file)}: 按线宽 ${opt.line} 单位 -> 缩放 ×${f2(s)} -> 线宽 ${TARGET_LINE}dp，占位 ${f2((bb.x1-bb.x0)*s)}×${f2((bb.y1-bb.y0)*s)}dp`);
    hintBox([(bb.x1-bb.x0)*s, (bb.y1-bb.y0)*s], target);
    writeXml(opt.out || path.basename(file).replace(/\.svg$/, '.xml'), vecHead() + fillPathXml(emit(out)) + '</vector>\n');
  } else {
    const { out, box } = fit(segs, target);
    console.log(`${path.basename(file)}: 归一化占位 -> ${f2(box[0])}×${f2(box[1])}dp`);
    writeXml(opt.out || path.basename(file).replace(/\.svg$/, '.xml'), vecHead() + fillPathXml(emit(out)) + '</vector>\n');
  }
} else if (cmd === 'stroke') {
  const file = pos[0];
  const w = parseFloat(opt.w ?? TARGET_LINE);
  const { out, box } = fit(segsOf(file), parseFloat(opt.art ?? 19.5), w);
  console.log(`${path.basename(file)}: 描边式 -> 中线缩放后外缘 ${f2(box[0])}×${f2(box[1])}dp，strokeWidth=${w}`);
  writeXml(opt.out || path.basename(file).replace(/\.svg$/, '.xml'), vecHead() + strokePathXml(emit(out), w) + '</vector>\n');
} else if (cmd === 'rescale') {
  const file = pos[0];
  const { ds } = readPath(file);
  const segs = ds.flatMap((d) => parseAbs(d));
  const bb0 = bbox(segs);
  const sw = parseFloat((/strokeWidth="([\d.]+)"/.exec(fs.readFileSync(abs(file), 'utf8')) ?? [0, '0'])[1]);
  const target = parseFloat(opt.art ?? 19.5);
  const s = (target - sw) / Math.max(bb0.x1 - bb0.x0, bb0.y1 - bb0.y0);
  const t = applyTransform(segs, s, 0, 0);
  const tb = bbox(t);
  const out = applyTransform(segs, s, 12 - (tb.x0 + tb.x1) / 2, 12 - (tb.y0 + tb.y1) / 2);
  const ob = bbox(out);
  console.log(`${file}: 占位 ${f2(bb0.x1 - bb0.x0)}×${f2(bb0.y1 - bb0.y0)} -> 缩放 ×${f2(s)} -> 外缘 ${f2(ob.x1 - ob.x0 + sw)}×${f2(ob.y1 - ob.y0 + sw)}dp`);
  const body = sw > 0 ? strokePathXml(emit(out), sw) : fillPathXml(emit(out));
  writeXml(file, vecHead() + body + '</vector>\n');
} else if (cmd === 'thicken') {
  const file = pos[0];
  const from = parseFloat(opt.from), to = parseFloat(opt.to ?? TARGET_LINE);
  const need = P(to - from);
  if (!(need > 0)) { console.error(`--from ${from} 已不小于 --to ${to}，无需加粗`); process.exit(1); }
  const xml = fs.readFileSync(abs(file), 'utf8');
  const vp = parseFloat(/viewportWidth="([\d.]+)"/.exec(xml)[1]);
  const k = vp / 24;                                  // 折算到文件自身栅格
  const d0 = [...xml.matchAll(/pathData="([^"]+)"/g)].map((m) => m[1]).join('');
  const d = emit(parseAbs(d0));
  const out = `    <path\n        android:fillColor="#FF000000"\n        android:pathData="${d}"\n` +
    `        android:strokeColor="#FF000000"\n        android:strokeWidth="${P(need * k)}"\n        android:strokeLineJoin="miter"\n        />\n`;
  console.log(`${file}: 线宽 ${from} -> ${to}dp（叠 strokeWidth=${P(need * k)}，形状不变，外缘只扩 ${P(need / 2)}dp）`);
  writeXml(file, vecHead(vp) + out + '</vector>\n');
} else if (cmd === 'pair') {
  const file = pos[0];
  const { ds } = readPath(file);
  const segs = ds.flatMap((d) => parseAbs(d));
  const subs = splitSubpaths(segs);
  const area = (sp) => { let a = 0; for (let i = 0; i < sp.length - 1; i++) a += sp[i][0] * sp[i + 1][1] - sp[i + 1][0] * sp[i][1]; return a / 2; };
  // 取面积最大的一条闭合轮廓 = 外缘（描边版的填充版必须与它同外缘）
  const pts = (sp) => { const out = []; let cx = 0, cy = 0;
    for (const g of sp) { if (g[0] === 'M' || g[0] === 'L') { out.push([g[1], g[2]]); cx = g[1]; cy = g[2]; } }
    return out; };
  const ranked = subs.map((sp) => ({ sp, a: Math.abs(area(pts(sp))) })).sort((x, y) => y.a - x.a);
  const xml = fs.readFileSync(abs(file), 'utf8');
  const sw = parseFloat((/strokeWidth="([\d.]+)"/.exec(xml) ?? [0, '0'])[1]);
  const d = emit(ranked[0].sp);
  console.log(`${file}: 取最大轮廓作外缘生成填充版（原描边 ${sw}dp 一并保留，保证外缘一致）`);
  const body = sw > 0
    ? `    <path\n        android:fillColor="#FF000000"\n        android:pathData="${d}"\n        android:strokeColor="#FF000000"\n        android:strokeWidth="${sw}"\n        android:strokeLineJoin="miter"\n        />\n`
    : fillPathXml(d);
  writeXml(opt.out || file.replace(/\.xml$/, '_filled.xml'), vecHead(parseFloat(/viewportWidth="([\d.]+)"/.exec(xml)[1])) + body + '</vector>\n');
} else {
  console.error(`未知子命令 ${cmd}：可用 svg / stroke / rescale / thicken / pair`);
  process.exit(1);
}
