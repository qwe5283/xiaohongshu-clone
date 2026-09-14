#!/usr/bin/env node
// ============================================================================
// Xiaohongshu (小红书) clone — zero-dependency mock server
//
//   node server.mjs                 # listen on :8787 (override with PORT)
//   node server.mjs --seed-only     # print seed counts and exit
//   PUBLIC_BASE=http://10.0.2.2:8787 node server.mjs   # for the Android emulator
//
// Only Node built-ins are used. No npm dependencies.
// Implements client/docs/API契约-v2.md in full.
// ============================================================================
import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { generateImage, clampSize } from './png.mjs';
import { buildDb, recomputeUserStats, fmt, NOTIFICATION_TYPE_TEXT, HOT_KEYWORDS, CATEGORY_TYPES } from './seed.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const PORT = Number.parseInt(process.env.PORT || '8787', 10);
const HOST = process.env.HOST || '0.0.0.0';
const PUBLIC_BASE = (process.env.PUBLIC_BASE || `http://localhost:${PORT}`).replace(/\/+$/, '');
const UPLOAD_DIR = path.join(__dirname, 'uploads');
const ASSET_DIR = path.join(__dirname, 'assets');
const SAMPLE_VIDEO = path.join(ASSET_DIR, 'sample.mp4');

const DEFAULT_LATENCY_MIN = 80;
const DEFAULT_LATENCY_MAX = 250;
const PAGED_DEFAULT_SIZE = 20; // contract: pageSize default 20

/* ========================================================================== *
 * small helpers
 * ========================================================================== */
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const randInt = (min, max) => min + Math.floor(Math.random() * (max - min + 1));

const ok = (data, message = '操作成功') => ({ code: 200, message, data, timestamp: Date.now() });
const fail = (code, message) => ({ code, message, data: null, timestamp: Date.now() });

/** Business code → HTTP status (contract §9: only the auth codes map to 401). */
function httpForCode(code) {
  if (code === 1005 || code === 1006 || code === 1007) return 401;
  return 400;
}
const J = (data, message) => ({ status: 200, body: ok(data, message) });
const E = (code, message) => ({ status: httpForCode(code), body: fail(code, message) });

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,PATCH,OPTIONS',
  'Access-Control-Allow-Headers': '*',
  'Access-Control-Expose-Headers': 'Content-Range, Accept-Ranges, Content-Length',
  'Access-Control-Max-Age': '86400',
};

/* ========================================================================== *
 * state
 * ========================================================================== */
let db = buildDb({ publicBase: PUBLIC_BASE });

/* image bytes cache — the seed reuses the same URLs hundreds of times */
const imgCache = new Map();
const IMG_CACHE_MAX = 160;
function cachedImage(key, producer) {
  const hit = imgCache.get(key);
  if (hit) return hit;
  const buf = producer();
  imgCache.set(key, buf);
  if (imgCache.size > IMG_CACHE_MAX) imgCache.delete(imgCache.keys().next().value);
  return buf;
}

/** Best-effort: recreate assets/sample.mp4 with ffmpeg if the committed asset is missing. */
async function ensureSampleVideoAsync() {
  if (fs.existsSync(SAMPLE_VIDEO)) return;
  console.warn(`[warn] ${path.relative(process.cwd(), SAMPLE_VIDEO)} is missing.`);
  try {
    const { execFileSync } = await import('node:child_process');
    fs.mkdirSync(ASSET_DIR, { recursive: true });
    execFileSync(
      'ffmpeg',
      [
        '-y', '-hide_banner', '-loglevel', 'error',
        '-f', 'lavfi', '-i', 'testsrc=size=320x568:rate=24:duration=2',
        '-f', 'lavfi', '-i', 'sine=frequency=440:duration=2',
        '-c:v', 'libx264', '-profile:v', 'baseline', '-level', '3.0',
        '-pix_fmt', 'yuv420p', '-preset', 'veryfast', '-crf', '40', '-g', '24',
        '-c:a', 'aac', '-b:a', '24k', '-movflags', '+faststart', '-t', '2',
        SAMPLE_VIDEO,
      ],
      { stdio: 'ignore' },
    );
    console.log('[warn] regenerated assets/sample.mp4 with ffmpeg');
  } catch {
    console.warn('[warn] ffmpeg unavailable — GET /video/sample.mp4 returns a business error until the asset exists.');
  }
}

/* ========================================================================== *
 * pagination + VO builders
 * ========================================================================== */
function paginate(list, query) {
  let pageNum = Number.parseInt(query.get('pageNum') ?? '1', 10);
  let pageSize = Number.parseInt(query.get('pageSize') ?? String(PAGED_DEFAULT_SIZE), 10);
  if (!Number.isFinite(pageNum) || pageNum < 1) pageNum = 1;
  if (!Number.isFinite(pageSize) || pageSize < 1) pageSize = PAGED_DEFAULT_SIZE;
  if (pageSize > 100) pageSize = 100;
  const total = list.length;
  const pages = Math.ceil(total / pageSize); // 0 when total is 0
  const start = (pageNum - 1) * pageSize;
  return { records: list.slice(start, start + pageSize), total, size: pageSize, current: pageNum, pages };
}

function userVO(u) {
  return {
    id: u.id,
    username: u.username,
    nickname: u.nickname,
    avatar: u.avatar,
    gender: u.gender,
    phone: u.phone,
    email: u.email,
    bio: u.bio,
    backgroundImage: u.backgroundImage,
    birthday: u.birthday,
    region: u.region,
    occupation: u.occupation,
    school: u.school,
    redId: u.redId,
    followingCount: u.followingCount,
    followersCount: u.followersCount,
    // received (获赞数 / 获藏数) — legacy semantics, not displayed by the client
    likeCount: u.likeCount,
    collectCount: u.collectCount,
    likeAndCollectCount: u.likeAndCollectCount,
    // amendment #16 — this user's OWN likes / collects; equal to the `total` of
    // GET /api/like/posts/{id} and GET /api/collect/posts/{id} respectively
    collectedPostCount: u.collectedPostCount,
    likedPostCount: u.likedPostCount,
    createTime: u.createTime,
  };
}

function postVO(p, viewer) {
  const author = db.usersById.get(p.userId);
  return {
    id: p.id,
    userId: p.userId,
    authorNickname: author ? author.nickname : '',
    authorAvatar: author ? author.avatar : '',
    title: p.title,
    content: p.content,
    type: p.type,
    coverImage: p.coverImage,
    videoUrl: p.videoUrl || '',
    images: p.images.map((im) => ({ id: im.id, imageUrl: im.imageUrl, sortOrder: im.sortOrder, width: im.width, height: im.height })),
    viewCount: p.viewCount,
    likeCount: p.likeCount,
    commentCount: p.commentCount,
    collectCount: p.collectCount,
    liked: viewer ? db.likeSet.has(`${viewer.id}:${p.id}`) : false,
    collected: viewer ? db.collectSet.has(`${viewer.id}:${p.id}`) : false,
    followed: viewer && viewer.id !== p.userId ? db.followSet.has(`${viewer.id}:${p.userId}`) : false,
    status: p.status,
    createTime: p.createTime,
    updateTime: p.updateTime,
  };
}

function commentVO(c, viewer) {
  const u = db.usersById.get(c.userId);
  const ru = c.replyUserId ? db.usersById.get(c.replyUserId) : null;
  return {
    id: c.id,
    postId: c.postId,
    userId: c.userId,
    userNickname: u ? u.nickname : '',
    userAvatar: u ? u.avatar : '',
    content: c.content,
    parentId: c.parentId,
    replyUserId: c.replyUserId || 0,
    replyUserNickname: ru ? ru.nickname : '',
    likeCount: c.likeCount,
    liked: viewer ? db.commentLikeSet.has(`${viewer.id}:${c.id}`) : false,
    replyCount: c.replyCount,
    createTime: c.createTime,
  };
}

function followUserVO(u, followTime, viewer) {
  return {
    id: u.id,
    nickname: u.nickname,
    avatar: u.avatar,
    bio: u.bio,
    followTime,
    followed: viewer ? db.followSet.has(`${viewer.id}:${u.id}`) : false,
  };
}

function notificationVO(n) {
  const sender = db.usersById.get(n.senderId);
  return {
    id: n.id,
    receiverId: n.receiverId,
    senderId: n.senderId,
    senderNickname: sender ? sender.nickname : '',
    senderAvatar: sender ? sender.avatar : '',
    type: n.type,
    typeText: NOTIFICATION_TYPE_TEXT[n.type] || '',
    postId: n.postId || 0,
    postTitle: n.postTitle || '',
    postCoverImage: n.postCoverImage || '',
    commentId: n.commentId || 0,
    content: n.content || '',
    read: !!n.read,
    createTime: n.createTime,
  };
}

/** live (never stale) — the three category badges are derived, not stored */
function unreadCounts(userId) {
  let like = 0;
  let comment = 0;
  let follow = 0;
  for (const n of db.notifications) {
    if (n.receiverId !== userId || n.read) continue;
    if (CATEGORY_TYPES[1].includes(n.type)) like += 1;
    else if (CATEGORY_TYPES[2].includes(n.type)) comment += 1;
    else if (CATEGORY_TYPES[3].includes(n.type)) follow += 1;
  }
  return { unreadCount: like + comment + follow, likeUnread: like, commentUnread: comment, followUnread: follow };
}

function hotScore(p) {
  return p.viewCount + p.likeCount * 3 + p.collectCount * 2 + p.commentCount * 5;
}

/** Every list that can be seen by a viewer is sorted through here. */
function sortedPosts(list, query) {
  const sortType = query.get('sortType') || 'latest';
  const keyword = (query.get('keyword') || '').trim().toLowerCase();
  const typeRaw = query.get('type');
  let arr = list.filter((p) => p.status === 1);
  if (typeRaw !== null && typeRaw !== '' && typeRaw !== 'undefined') {
    const t = Number.parseInt(typeRaw, 10);
    if (Number.isFinite(t)) arr = arr.filter((p) => p.type === t);
  }
  if (keyword) {
    const titleHits = arr.filter((p) => (p.title || '').toLowerCase().includes(keyword));
    const contentHits = arr.filter(
      (p) => !(p.title || '').toLowerCase().includes(keyword) && (p.content || '').toLowerCase().includes(keyword),
    );
    arr = [...titleHits, ...contentHits];
    return sortType === 'hot' ? arr.sort((a, b) => hotScore(b) - hotScore(a) || b._createdMs - a._createdMs) : arr.sort((a, b) => b._createdMs - a._createdMs);
  }
  return sortType === 'hot'
    ? [...arr].sort((a, b) => hotScore(b) - hotScore(a) || b._createdMs - a._createdMs)
    : [...arr].sort((a, b) => b._createdMs - a._createdMs);
}

/* ========================================================================== *
 * auth
 * ========================================================================== */
function bearerToken(req) {
  const raw = req.headers.authorization || '';
  const m = /^Bearer\s+(.+)$/i.exec(String(raw).trim());
  return m ? m[1].trim() : null;
}

function resolveAuth(req) {
  const token = bearerToken(req);
  if (!token) return { err: { code: 1005, message: '用户未登录' } };
  // special tokens for exercising the client's session-expiry branches
  if (token === 'expired-token') return { err: { code: 1006, message: 'Token 已过期' } };
  if (token === 'invalid-token') return { err: { code: 1007, message: 'Token 无效' } };
  const s = db.sessions.get(token);
  if (!s) return { err: { code: 1005, message: '用户未登录' } };
  const user = db.usersById.get(s.userId);
  if (!user) return { err: { code: 1005, message: '用户未登录' } };
  if (user.status !== 1) return { err: { code: 1004, message: '用户被禁用' } };
  return { user, token };
}

/** Never throws — returns null when the caller is a guest. */
function optionalUser(req) {
  const r = resolveAuth(req);
  return r.err ? null : r.user;
}

/* ========================================================================== *
 * body parsing (json + multipart, from the raw stream, no deps)
 * ========================================================================== */
function readBody(req, maxBytes) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let len = 0;
    let over = false;
    req.on('data', (c) => {
      if (over) return;
      len += c.length;
      if (len > maxBytes) {
        over = true;
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => resolve({ buf: Buffer.concat(chunks), over }));
    req.on('error', reject);
  });
}

function parseMultipart(buf, boundary) {
  const bnd = Buffer.from(`--${boundary}`);
  const parts = [];
  let idx = buf.indexOf(bnd);
  while (idx !== -1) {
    let cursor = idx + bnd.length;
    if (buf[cursor] === 0x2d && buf[cursor + 1] === 0x2d) break; // closing "--boundary--"
    if (buf[cursor] === 0x0d && buf[cursor + 1] === 0x0a) cursor += 2;
    const next = buf.indexOf(bnd, cursor);
    if (next === -1) break;
    let end = next;
    if (buf[end - 2] === 0x0d && buf[end - 1] === 0x0a) end -= 2;
    const section = buf.subarray(cursor, end);
    const hEnd = section.indexOf('\r\n\r\n');
    if (hEnd !== -1) {
      const headers = {};
      for (const line of section.subarray(0, hEnd).toString('latin1').split('\r\n')) {
        const c = line.indexOf(':');
        if (c > 0) headers[line.slice(0, c).trim().toLowerCase()] = line.slice(c + 1).trim();
      }
      const cd = headers['content-disposition'] || '';
      const nameM = /name="([^"]*)"/.exec(cd);
      const fileM = /filename="([^"]*)"/.exec(cd);
      parts.push({
        name: nameM ? nameM[1] : '',
        filename: fileM ? fileM[1] : null,
        contentType: headers['content-type'] || '',
        data: section.subarray(hEnd + 4),
      });
    }
    idx = next;
  }
  return parts;
}

/* ========================================================================== *
 * file store
 * ========================================================================== */
const EXT_BY_MIME = {
  'image/png': '.png',
  'image/jpeg': '.jpg',
  'image/jpg': '.jpg',
  'image/gif': '.gif',
  'image/webp': '.webp',
  'video/mp4': '.mp4',
  'video/quicktime': '.mov',
  'application/pdf': '.pdf',
};
const MIME_BY_EXT = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.mp4': 'video/mp4',
  '.mov': 'video/quicktime',
  '.webm': 'video/webm',
  '.pdf': 'application/pdf',
  '.txt': 'text/plain; charset=utf-8',
};

function storeFile(bytes, filename, mime) {
  const ext = (filename && path.extname(filename)) || EXT_BY_MIME[mime] || '.bin';
  const hash = crypto.createHash('sha256').update(bytes).digest('hex').slice(0, 16);
  const id = `${hash}${ext}`;
  const type = mime || MIME_BY_EXT[ext] || 'application/octet-stream';
  db.files.set(id, { bytes, contentType: type });
  try {
    fs.mkdirSync(UPLOAD_DIR, { recursive: true });
    const dest = path.join(UPLOAD_DIR, id);
    if (!fs.existsSync(dest)) fs.writeFileSync(dest, bytes);
  } catch {
    /* disk mirror is a convenience only */
  }
  return { id, url: `${PUBLIC_BASE}/files/${id}`, contentType: type };
}

/** Restore previously uploaded files so their URLs keep working after a restart. */
function loadExistingUploads() {
  try {
    if (!fs.existsSync(UPLOAD_DIR)) return 0;
    let n = 0;
    for (const name of fs.readdirSync(UPLOAD_DIR)) {
      const full = path.join(UPLOAD_DIR, name);
      if (!fs.statSync(full).isFile()) continue;
      db.files.set(name, { bytes: fs.readFileSync(full), contentType: MIME_BY_EXT[path.extname(name)] || 'application/octet-stream' });
      n += 1;
    }
    return n;
  } catch {
    return 0;
  }
}

/** Read pixel dimensions straight out of PNG / JPEG bytes (best effort). */
function dimsFromBytes(bytes) {
  try {
    if (bytes.length > 24 && bytes[0] === 0x89 && bytes[1] === 0x50) {
      return [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
    }
    if (bytes.length > 4 && bytes[0] === 0xff && bytes[1] === 0xd8) {
      let i = 2;
      while (i + 9 < bytes.length) {
        if (bytes[i] !== 0xff) {
          i += 1;
          continue;
        }
        const marker = bytes[i + 1];
        const size = bytes.readUInt16BE(i + 2);
        if (marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc) {
          return [bytes.readUInt16BE(i + 7), bytes.readUInt16BE(i + 5)];
        }
        i += 2 + size;
      }
    }
  } catch {
    /* fall through */
  }
  return null;
}

function dimsForUrl(url) {
  try {
    const u = new URL(url, PUBLIC_BASE);
    const w = Number.parseInt(u.searchParams.get('w') || '', 10);
    const h = Number.parseInt(u.searchParams.get('h') || '', 10);
    if (w > 0 && h > 0) return [w, h];
    const m = /^\/files\/(.+)$/.exec(u.pathname);
    if (m) {
      const rec = db.files.get(decodeURIComponent(m[1]));
      if (rec) {
        const d = dimsFromBytes(rec.bytes);
        if (d) return d;
      }
    }
  } catch {
    /* ignore */
  }
  return [800, 600];
}

/* ========================================================================== *
 * AI assistant (「点点」)
 * ========================================================================== */
const AI_TOPICS = [
  {
    keys: ['咖啡', '拉花', '手冲'],
    answer:
      '给你一套可以马上上手的手冲方案：\n\n• 器具：手摇磨豆机 + V60 滤杯 + 电子秤\n• 参数：15g 粉，92℃ 水温，粉水比 1:15\n• 步骤：焖蒸 30 秒 → 分三段注水，总时长 2 分 15 秒左右\n\n拉花的部分，先练打奶泡再练手法，奶泡太厚推不开、太薄没有形状。',
  },
  {
    keys: ['徒步', '爬山', '户外', '露营'],
    answer:
      '新手徒步可以从这三条原则开始：\n\n• 距离：第一次控制在 8 公里以内，爬升 300 米以内\n• 装备：登山鞋、登山杖、1.5L 水就够了\n• 时间：早上出发，下午 4 点前必须下山\n\n出发前把路线告诉一个朋友，山上信号不一定有。',
  },
  {
    keys: ['穿搭', '显瘦', '显高', 'ootd', '通勤'],
    answer:
      '给你一个不会出错的通勤公式：挺括外套 + 薄针织 + 九分直筒裤。\n\n• 颜色控制在三个以内，整体就很干净\n• 小个子记住「上短下长 + 露出脚踝」\n• 想显瘦就用同色系纵向延伸\n\n秋天温差大，中间加一层可以随时脱掉的衬衫最实用。',
  },
  {
    keys: ['化妆', '彩妆', '口红', '粉底', '眼妆'],
    answer: '新手化妆顺序帮你理一下：\n\n• 妆前保湿 + 防晒，等 3 分钟再上底妆\n• 底妆少量多次，用美妆蛋按压不要涂抹\n• 遮瑕只遮需要的地方，不要全脸铺\n• 定妆重点在 T 区\n\n单眼皮画眼线记得分段画再连起来，不要追求一笔完成。',
  },
  {
    keys: ['职场', '面试', '转行', '沟通', '存钱'],
    answer: '职场沟通三条最实用的习惯：\n\n• 结论先行：先说结果再说过程\n• 给选项而不是给问题：「我倾向 A，因为…」\n• 重要的事一定留痕，会议结束发一条总结\n\n这三条能省掉至少一半的返工。',
  },
  {
    keys: ['美食', '做饭', '菜谱', '早餐', '空气炸锅'],
    answer: '15 分钟一人食的通用套路：\n\n• 蛋白质提前一晚处理好放冰箱\n• 一个锅煮主食，一个锅做浇头\n• 调味只用盐、生抽、一点点糖\n\n空气炸锅 200 度、12 分钟翻面再 8 分钟，鸡翅就能脆皮。',
  },
];
const AI_FALLBACK =
  '我帮你从这几个角度看看：\n\n• 先明确你的目标（想要结果还是想要过程）\n• 把门槛降到低得不可能失败\n• 每次只改一个变量，才知道是什么起了作用\n\n下面是我找到的几篇相关笔记，可以点开看看。';

const AI_SUGGESTIONS = [
  '帮我写一段周末徒步的文案',
  '推荐 3 个适合新手的妆容',
  '手冲咖啡怎么入门',
  '通勤穿搭怎么显高',
  '转行做产品需要准备什么',
];

function aiReply(message) {
  const msg = message.toLowerCase();
  let topic = null;
  for (const t of AI_TOPICS) {
    if (t.keys.some((k) => msg.includes(k.toLowerCase()))) {
      topic = t;
      break;
    }
  }
  const answer = topic ? topic.answer : AI_FALLBACK;
  let notes;
  if (topic) {
    notes = db.posts
      .filter((p) => p.status === 1 && topic.keys.some((k) => (p.title + p.content).toLowerCase().includes(k.toLowerCase())))
      .slice(0, 3);
  }
  if (!notes || notes.length === 0) {
    notes = [...db.posts].filter((p) => p.status === 1).sort((a, b) => hotScore(b) - hotScore(a)).slice(0, 3);
  }
  return { answer, notes: notes.map((p) => postVO(p, null)) };
}

/* ========================================================================== *
 * routes
 * ========================================================================== */
const routes = [];
function R(method, routePath, opts, handler) {
  routes.push({ method, segs: routePath.split('/').filter(Boolean), needAuth: !!(opts && opts.auth), handler });
}

function matchRoute(method, pathname) {
  const segs = pathname.split('/').filter(Boolean);
  for (const r of routes) {
    if (r.method !== method || r.segs.length !== segs.length) continue;
    const params = {};
    let matched = true;
    for (let i = 0; i < r.segs.length; i++) {
      const s = r.segs[i];
      if (s.startsWith(':')) params[s.slice(1)] = decodeURIComponent(segs[i]);
      else if (s !== segs[i]) {
        matched = false;
        break;
      }
    }
    if (matched) return { route: r, params };
  }
  return null;
}

const intParam = (v) => {
  const n = Number.parseInt(v, 10);
  return Number.isFinite(n) ? n : null;
};

/* ----------------------------- health / meta ----------------------------- */
R('GET', '/api/health', {}, () => J({ ok: true, uptime: Math.round(process.uptime() * 1000) }, '服务正常'));

R('POST', '/api/_reset', {}, () => {
  const sessions = db.sessions;
  const files = db.files;
  const fresh = buildDb({ publicBase: PUBLIC_BASE });
  fresh.sessions = sessions;
  fresh.files = files;
  db = fresh;
  console.log('[mock] database re-seeded');
  return J(
    {
      posts: db.posts.length,
      users: db.users.length,
      comments: db.comments.length,
      notifications: db.notifications.length,
    },
    '已重新初始化',
  );
});

/* -------------------------------- users --------------------------------- */
R('POST', '/api/user/register', {}, (ctx) => {
  const b = ctx.json || {};
  const username = typeof b.username === 'string' ? b.username.trim() : '';
  const password = typeof b.password === 'string' ? b.password : '';
  const nickname = typeof b.nickname === 'string' ? b.nickname.trim() : '';
  const phone = typeof b.phone === 'string' ? b.phone.trim() : '';
  if (!username || !password) return E(5002, '参数缺失');
  if (username.length < 3 || username.length > 20) return E(5001, '用户名为 3-20 个字符');
  if (password.length < 6 || password.length > 20) return E(5001, '密码为 6-20 个字符');
  if (nickname.length > 20) return E(5001, '昵称不能超过 20 个字符');
  if (phone && !/^1[3-9]\d{9}$/.test(phone)) return E(5001, '手机号格式不正确');
  if (db.usernameIndex.has(username)) return E(1003, '用户名已存在');

  db.counters.user += 1;
  const id = db.counters.user;
  const u = {
    id,
    username,
    password,
    nickname: nickname || username,
    avatar: db.avatarUrl(`u${id}-${username}`, 200),
    gender: 0,
    phone,
    email: '',
    bio: '',
    backgroundImage: db.imgUrl(800, 450, `bg-u${id}`),
    birthday: '',
    region: '',
    occupation: '',
    school: '',
    redId: String(100000 + id * 137),
    status: 1,
    createTime: fmt(new Date()),
    // derived counters — all zero for a brand new account (see recomputeUserStats)
    followingCount: 0,
    followersCount: 0,
    likeCount: 0,
    collectCount: 0,
    likeAndCollectCount: 0,
    likedPostCount: 0,
    collectedPostCount: 0,
  };
  db.users.push(u);
  db.usersById.set(id, u);
  db.usernameIndex.set(username, u);
  recomputeUserStats(db);
  return J(userVO(u), '注册成功');
});

R('POST', '/api/user/login', {}, (ctx) => {
  const b = ctx.json || {};
  if (!b.username || !b.password) return E(5002, '参数缺失');
  const u = db.usernameIndex.get(String(b.username).trim());
  if (!u) return E(1001, '用户不存在');
  if (u.password !== b.password) return E(1002, '密码错误');
  if (u.status !== 1) return E(1004, '用户被禁用');
  const token = crypto.randomBytes(24).toString('hex');
  db.sessions.set(token, { userId: u.id, createdAt: Date.now() });
  return J({ token, expiresIn: 604800, user: userVO(u) }, '登录成功');
});

R('GET', '/api/user/me', { auth: true }, (ctx) => J(userVO(ctx.viewer)));

R('PUT', '/api/user/update', { auth: true }, (ctx) => {
  const b = ctx.json || {};
  const u = ctx.viewer;
  // validate everything BEFORE mutating
  if (b.nickname !== undefined && (typeof b.nickname !== 'string' || b.nickname.length === 0 || b.nickname.length > 20)) {
    return E(5001, '昵称不能为空且不超过 20 个字符');
  }
  if (b.bio !== undefined && (typeof b.bio !== 'string' || b.bio.length > 200)) return E(5001, '简介不能超过 200 个字符');
  let gender;
  if (b.gender !== undefined) {
    gender = intParam(b.gender);
    if (gender === null || gender < 0 || gender > 2) return E(5001, '参数错误');
  }
  // eslint-disable-next-line no-useless-assignment
  let birthday;
  if (b.birthday !== undefined) {
    birthday = String(b.birthday);
    if (birthday !== '' && !/^\d{4}-\d{2}-\d{2}$/.test(birthday)) return E(5001, '生日格式应为 yyyy-MM-dd');
  }

  // apply
  if (b.nickname !== undefined) u.nickname = b.nickname;
  if (b.avatar !== undefined) u.avatar = String(b.avatar);
  if (b.bio !== undefined) u.bio = b.bio;
  if (gender !== undefined) u.gender = gender;
  if (b.email !== undefined) u.email = String(b.email);
  if (b.backgroundImage !== undefined) u.backgroundImage = String(b.backgroundImage);
  if (b.region !== undefined) u.region = String(b.region);
  if (b.occupation !== undefined) u.occupation = String(b.occupation);
  if (b.school !== undefined) u.school = String(b.school);
  if (birthday !== undefined) u.birthday = birthday;
  return J(userVO(u), '更新成功');
});

R('GET', '/api/user/:id', {}, (ctx) => {
  const id = intParam(ctx.params.id);
  const u = id === null ? null : db.usersById.get(id);
  if (!u) return E(1001, '用户不存在');
  return J(userVO(u));
});

/* -------------------------------- posts ---------------------------------- */
R('POST', '/api/post/create', { auth: true }, (ctx) => {
  const b = ctx.json || {};
  const title = typeof b.title === 'string' ? b.title.trim() : '';
  const content = typeof b.content === 'string' ? b.content : '';
  const videoUrl = typeof b.videoUrl === 'string' ? b.videoUrl : '';
  const imageUrls = Array.isArray(b.imageUrls) ? b.imageUrls.filter((x) => typeof x === 'string' && x) : [];
  if (!title) return E(5002, '标题不能为空');
  if (title.length > 200) return E(5001, '标题不能超过 200 个字符');
  if (content.length > 10000) return E(5001, '正文不能超过 10000 个字符');
  if (imageUrls.length > 9) return E(2004, '图片数量不能超过 9 张');
  if (imageUrls.length === 0 && !videoUrl) return E(2005, '笔记必须包含至少一张图片或一个视频');

  db.counters.post += 1;
  const id = db.counters.post;
  const images = imageUrls.map((url, i) => {
    const [w, h] = dimsForUrl(url);
    db.counters.image += 1;
    return { id: db.counters.image, imageUrl: url, sortOrder: i + 1, width: w, height: h };
  });
  const created = Date.now();
  const p = {
    id,
    userId: ctx.viewer.id,
    title,
    content,
    category: '生活',
    type: videoUrl ? 1 : 0,
    coverImage: images.length ? images[0].imageUrl : db.imgUrl(600, 800, `video-poster-${id}`),
    videoUrl,
    images,
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    collectCount: 0,
    status: 1,
    createTime: fmt(new Date(created)),
    updateTime: fmt(new Date(created)),
    _createdMs: created,
  };
  db.posts.unshift(p);
  db.postsById.set(id, p);
  recomputeUserStats(db); // new note has 0 counters, but keep the derivation in one place
  return J(postVO(p, ctx.viewer), '发布成功');
});

R('PUT', '/api/post/update', { auth: true }, (ctx) => {
  const b = ctx.json || {};
  const id = intParam(b.id);
  if (id === null) return E(5002, '缺少笔记 ID');
  const p = db.postsById.get(id);
  if (!p) return E(2001, '笔记不存在');
  if (p.userId !== ctx.viewer.id) return E(2003, '无权操作此笔记');

  // validate everything BEFORE mutating, so a failed request never half-updates the note
  let nextTitle;
  if (b.title !== undefined) {
    nextTitle = String(b.title).trim();
    if (!nextTitle) return E(5002, '标题不能为空');
    if (nextTitle.length > 200) return E(5001, '标题不能超过 200 个字符');
  }
  let nextContent;
  if (b.content !== undefined) {
    nextContent = String(b.content);
    if (nextContent.length > 10000) return E(5001, '正文不能超过 10000 个字符');
  }
  const nextVideoUrl = b.videoUrl !== undefined ? String(b.videoUrl || '') : p.videoUrl || '';
  let nextImages = p.images;
  if (b.imageUrls !== undefined) {
    if (!Array.isArray(b.imageUrls)) return E(5001, '参数错误');
    const urls = b.imageUrls.filter((x) => typeof x === 'string' && x);
    if (urls.length > 9) return E(2004, '图片数量不能超过 9 张');
    nextImages = urls.map((url, i) => {
      const [w, h] = dimsForUrl(url);
      db.counters.image += 1;
      return { id: db.counters.image, imageUrl: url, sortOrder: i + 1, width: w, height: h };
    });
  }
  if (nextImages.length === 0 && !nextVideoUrl) return E(2005, '笔记必须包含至少一张图片或一个视频');

  // apply
  if (nextTitle !== undefined) p.title = nextTitle;
  if (nextContent !== undefined) p.content = nextContent;
  p.videoUrl = nextVideoUrl;
  p.images = nextImages;
  p.type = p.videoUrl ? 1 : 0;
  p.coverImage = p.images.length ? p.images[0].imageUrl : p.coverImage || db.imgUrl(600, 800, `video-poster-${p.id}`);
  p.updateTime = fmt(new Date());
  return J(postVO(p, ctx.viewer), '更新成功');
});

R('DELETE', '/api/post/delete/:postId', { auth: true }, (ctx) => {
  const id = intParam(ctx.params.postId);
  const p = id === null ? null : db.postsById.get(id);
  if (!p) return E(2001, '笔记不存在');
  if (p.userId !== ctx.viewer.id) return E(2003, '无权操作此笔记');
  db.posts = db.posts.filter((x) => x.id !== p.id);
  db.postsById.delete(p.id);
  db.notifications = db.notifications.filter((n) => n.postId !== p.id);
  // Drop the like/collect records pointing at the deleted note as well, otherwise
  // likedPostCount/collectedPostCount would keep counting a note that
  // GET /api/like/posts/{id} / /api/collect/posts/{id} can no longer return
  // (that would break the amendment-#16 invariant).
  db.postLikes = db.postLikes.filter((l) => l.postId !== p.id);
  db.collects = db.collects.filter((c) => c.postId !== p.id);
  // key shape is `${userId}:${postId}` — compare the postId segment exactly
  for (const key of [...db.likeSet]) if (key.split(':')[1] === String(p.id)) db.likeSet.delete(key);
  for (const key of [...db.collectSet]) if (key.split(':')[1] === String(p.id)) db.collectSet.delete(key);
  recomputeUserStats(db);
  return J(null, '删除成功');
});

// statics must be registered before the /:postId catch-all
R('GET', '/api/post/list', {}, (ctx) => {
  const page = paginate(sortedPosts(db.posts, ctx.query), ctx.query);
  page.records = page.records.map((p) => postVO(p, ctx.viewer));
  return J(page);
});

R('GET', '/api/post/my', { auth: true }, (ctx) => {
  const mine = db.posts.filter((p) => p.userId === ctx.viewer.id);
  const page = paginate(sortedPosts(mine, ctx.query), ctx.query);
  page.records = page.records.map((p) => postVO(p, ctx.viewer));
  return J(page);
});

R('GET', '/api/post/following-feed', { auth: true }, (ctx) => {
  const following = new Set(db.follows.filter((f) => f.followerId === ctx.viewer.id).map((f) => f.followingId));
  const feed = db.posts.filter((p) => following.has(p.userId));
  const page = paginate(sortedPosts(feed, ctx.query), ctx.query);
  page.records = page.records.map((p) => postVO(p, ctx.viewer));
  return J(page);
});

R('GET', '/api/post/hot-keywords', {}, () => J(HOT_KEYWORDS));

R('GET', '/api/post/text-image/generate', {}, (ctx) => {
  const text = ctx.query.get('text');
  if (!text) return E(5002, '参数缺失');
  if (Array.from(text).length > 20) return E(5001, '文本不能超过 20 个字');
  const [w, h] = [800, 1200];
  const buf = cachedImage(`gen|${w}x${h}|${text}`, () => generateImage(w, h, `textimg:${text}`, 'text'));
  return { status: 200, buf, contentType: 'image/png' };
});

R('POST', '/api/post/text-image', {}, (ctx) => {
  const b = ctx.json || {};
  const text = typeof b.text === 'string' ? b.text : '';
  if (!text.trim()) return E(5002, '参数缺失');
  const clipped = Array.from(text).slice(0, 100).join('');
  const w = 800;
  const h = 1200;
  const png = generateImage(w, h, `textimg:${clipped}`, 'text');
  const stored = storeFile(png, `text-image-${Date.now()}.png`, 'image/png');
  return J({ url: stored.url, width: w, height: h }, '生成成功');
});

R('GET', '/api/post/user/:userId', {}, (ctx) => {
  const uid = intParam(ctx.params.userId);
  if (uid === null || !db.usersById.has(uid)) return E(1001, '用户不存在');
  const list = db.posts.filter((p) => p.userId === uid);
  const page = paginate(sortedPosts(list, ctx.query), ctx.query);
  page.records = page.records.map((p) => postVO(p, ctx.viewer));
  return J(page);
});

R('GET', '/api/post/:postId', {}, (ctx) => {
  const id = intParam(ctx.params.postId);
  const p = id === null ? null : db.postsById.get(id);
  if (!p) return E(2001, '笔记不存在');
  if (p.status !== 1) return E(2002, '笔记已删除');
  p.viewCount += 1;
  return J(postVO(p, ctx.viewer));
});

/* ------------------------------- comments -------------------------------- */
R('POST', '/api/comment/create', { auth: true }, (ctx) => {
  const b = ctx.json || {};
  const postId = intParam(b.postId);
  const content = typeof b.content === 'string' ? b.content : '';
  if (postId === null) return E(5002, '缺少笔记 ID');
  if (!content.trim()) return E(5002, '评论内容不能为空');
  if (content.length > 500) return E(5001, '评论不能超过 500 个字符');
  const post = db.postsById.get(postId);
  if (!post) return E(2001, '笔记不存在');
  const parentId = intParam(b.parentId) || 0;
  const replyUserId = intParam(b.replyUserId) || 0;
  let parent = null;
  if (parentId) {
    parent = db.commentsById.get(parentId);
    if (!parent) return E(3001, '评论不存在');
    if (parent.status !== 1) return E(3002, '评论已删除');
  }
  db.counters.comment += 1;
  const created = Date.now();
  const c = {
    id: db.counters.comment,
    postId,
    userId: ctx.viewer.id,
    content,
    parentId,
    replyUserId,
    likeCount: 0,
    replyCount: 0,
    status: 1,
    createTime: fmt(new Date(created)),
    _createdMs: created,
  };
  db.comments.push(c);
  db.commentsById.set(c.id, c);
  if (parent) parent.replyCount += 1;
  post.commentCount += 1; // first-level + replies (contract change #13)

  // notification: never to yourself
  const targetUserId = parent ? parent.userId : post.userId;
  if (targetUserId !== ctx.viewer.id) {
    db.counters.notification += 1;
    db.notifications.push({
      id: db.counters.notification,
      receiverId: targetUserId,
      senderId: ctx.viewer.id,
      type: parent ? 4 : 3,
      postId: post.id,
      commentId: c.id,
      content,
      read: false,
      createTime: fmt(new Date(created)),
      postCoverImage: post.coverImage,
      postTitle: post.title,
    });
  }
  return J(commentVO(c, ctx.viewer), '评论成功');
});

R('DELETE', '/api/comment/delete/:commentId', { auth: true }, (ctx) => {
  const id = intParam(ctx.params.commentId);
  const c = id === null ? null : db.commentsById.get(id);
  if (!c) return E(3001, '评论不存在');
  if (c.userId !== ctx.viewer.id) return E(2003, '无权操作此评论');
  const victims = [c, ...db.comments.filter((x) => x.parentId === c.id)];
  const ids = new Set(victims.map((v) => v.id));
  db.comments = db.comments.filter((x) => !ids.has(x.id));
  for (const v of victims) db.commentsById.delete(v.id);
  const post = db.postsById.get(c.postId);
  if (post) post.commentCount = Math.max(0, post.commentCount - victims.length);
  if (c.parentId) {
    const parent = db.commentsById.get(c.parentId);
    if (parent) parent.replyCount = Math.max(0, parent.replyCount - 1);
  }
  return J(null, '删除成功');
});

R('GET', '/api/comment/post/:postId', {}, (ctx) => {
  const postId = intParam(ctx.params.postId);
  if (postId === null || !db.postsById.has(postId)) return E(2001, '笔记不存在');
  const list = db.comments
    .filter((c) => c.postId === postId && c.parentId === 0 && c.status === 1)
    .sort((a, b) => b._createdMs - a._createdMs);
  const page = paginate(list, ctx.query);
  page.records = page.records.map((c) => commentVO(c, ctx.viewer));
  return J(page);
});

R('GET', '/api/comment/replies/:commentId', {}, (ctx) => {
  const id = intParam(ctx.params.commentId);
  const parent = id === null ? null : db.commentsById.get(id);
  if (!parent) return E(3001, '评论不存在');
  const list = db.comments.filter((c) => c.parentId === parent.id && c.status === 1).sort((a, b) => a._createdMs - b._createdMs);
  const page = paginate(list, ctx.query);
  page.records = page.records.map((c) => commentVO(c, ctx.viewer));
  return J(page);
});

/* --------------------------------- likes --------------------------------- */
R('POST', '/api/like/post/:postId', { auth: true }, (ctx) => {
  const id = intParam(ctx.params.postId);
  const p = id === null ? null : db.postsById.get(id);
  if (!p) return E(2001, '笔记不存在');
  const key = `${ctx.viewer.id}:${p.id}`;
  if (db.likeSet.has(key)) {
    db.likeSet.delete(key);
    db.postLikes = db.postLikes.filter((l) => !(l.userId === ctx.viewer.id && l.postId === p.id));
    p.likeCount = Math.max(0, p.likeCount - 1);
    recomputeUserStats(db);
    return J({ liked: false, message: '取消点赞成功' });
  }
  db.likeSet.add(key);
  db.postLikes.unshift({ userId: ctx.viewer.id, postId: p.id, createTime: fmt(new Date()) });
  p.likeCount += 1;
  recomputeUserStats(db);
  return J({ liked: true, message: '点赞成功' });
});

R('POST', '/api/like/comment/:commentId', { auth: true }, (ctx) => {
  const id = intParam(ctx.params.commentId);
  const c = id === null ? null : db.commentsById.get(id);
  if (!c) return E(3001, '评论不存在');
  const key = `${ctx.viewer.id}:${c.id}`;
  if (db.commentLikeSet.has(key)) {
    db.commentLikeSet.delete(key);
    c.likeCount = Math.max(0, c.likeCount - 1);
    return J({ liked: false, message: '取消点赞成功' });
  }
  db.commentLikeSet.add(key);
  c.likeCount += 1;
  return J({ liked: true, message: '点赞成功' });
});

R('GET', '/api/like/status/post/:postId', { auth: true }, (ctx) => {
  const id = intParam(ctx.params.postId);
  return J({ liked: id !== null && db.likeSet.has(`${ctx.viewer.id}:${id}`) });
});

R('GET', '/api/like/status/comment/:commentId', { auth: true }, (ctx) => {
  const id = intParam(ctx.params.commentId);
  return J({ liked: id !== null && db.commentLikeSet.has(`${ctx.viewer.id}:${id}`) });
});

R('GET', '/api/like/posts/:userId', { auth: true }, (ctx) => {
  const uid = intParam(ctx.params.userId);
  if (uid === null || !db.usersById.has(uid)) return E(1001, '用户不存在');
  const ordered = db.postLikes
    .filter((l) => l.userId === uid)
    .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
    .map((l) => db.postsById.get(l.postId))
    .filter(Boolean);
  const page = paginate(ordered, ctx.query);
  page.records = page.records.map((p) => postVO(p, ctx.viewer));
  return J(page);
});

/* -------------------------------- collects ------------------------------- */
R('POST', '/api/collect/post/:postId', { auth: true }, (ctx) => {
  const id = intParam(ctx.params.postId);
  const p = id === null ? null : db.postsById.get(id);
  if (!p) return E(2001, '笔记不存在');
  const key = `${ctx.viewer.id}:${p.id}`;
  if (db.collectSet.has(key)) {
    db.collectSet.delete(key);
    db.collects = db.collects.filter((c) => !(c.userId === ctx.viewer.id && c.postId === p.id));
    p.collectCount = Math.max(0, p.collectCount - 1);
    recomputeUserStats(db);
    return J({ collected: false, message: '取消收藏成功' });
  }
  db.collectSet.add(key);
  db.collects.unshift({ userId: ctx.viewer.id, postId: p.id, createTime: fmt(new Date()) });
  p.collectCount += 1;
  recomputeUserStats(db);
  return J({ collected: true, message: '收藏成功' });
});

R('GET', '/api/collect/status/post/:postId', { auth: true }, (ctx) => {
  const id = intParam(ctx.params.postId);
  return J({ collected: id !== null && db.collectSet.has(`${ctx.viewer.id}:${id}`) });
});

R('GET', '/api/collect/posts/:userId', { auth: true }, (ctx) => {
  const uid = intParam(ctx.params.userId);
  if (uid === null || !db.usersById.has(uid)) return E(1001, '用户不存在');
  const ordered = db.collects
    .filter((c) => c.userId === uid)
    .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
    .map((c) => db.postsById.get(c.postId))
    .filter(Boolean);
  const page = paginate(ordered, ctx.query);
  page.records = page.records.map((p) => postVO(p, ctx.viewer));
  return J(page);
});

/* --------------------------------- follow -------------------------------- */
R('POST', '/api/follow/:userId', { auth: true }, (ctx) => {
  const uid = intParam(ctx.params.userId);
  const target = uid === null ? null : db.usersById.get(uid);
  if (!target) return E(1001, '用户不存在');
  if (target.id === ctx.viewer.id) return E(6003, '不能关注自己');
  const key = `${ctx.viewer.id}:${target.id}`;
  if (db.followSet.has(key)) {
    db.followSet.delete(key);
    db.follows = db.follows.filter((f) => !(f.followerId === ctx.viewer.id && f.followingId === target.id));
    recomputeUserStats(db);
    return J({ followed: false, message: '取消关注成功' });
  }
  db.followSet.add(key);
  db.follows.push({ id: db.follows.length + 1, followerId: ctx.viewer.id, followingId: target.id, createTime: fmt(new Date()) });
  recomputeUserStats(db);
  return J({ followed: true, message: '关注成功' });
});

R('GET', '/api/follow/status/:userId', { auth: true }, (ctx) => {
  const uid = intParam(ctx.params.userId);
  return J({ followed: uid !== null && db.followSet.has(`${ctx.viewer.id}:${uid}`) });
});

R('GET', '/api/follow/following/:userId', {}, (ctx) => {
  const uid = intParam(ctx.params.userId);
  if (uid === null || !db.usersById.has(uid)) return E(1001, '用户不存在');
  const recs = db.follows
    .filter((f) => f.followerId === uid)
    .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
    .map((f) => ({ u: db.usersById.get(f.followingId), t: f.createTime }))
    .filter((x) => x.u);
  const page = paginate(recs, ctx.query);
  page.records = page.records.map((x) => followUserVO(x.u, x.t, ctx.viewer));
  return J(page);
});

R('GET', '/api/follow/followers/:userId', {}, (ctx) => {
  const uid = intParam(ctx.params.userId);
  if (uid === null || !db.usersById.has(uid)) return E(1001, '用户不存在');
  const recs = db.follows
    .filter((f) => f.followingId === uid)
    .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
    .map((f) => ({ u: db.usersById.get(f.followerId), t: f.createTime }))
    .filter((x) => x.u);
  const page = paginate(recs, ctx.query);
  page.records = page.records.map((x) => followUserVO(x.u, x.t, ctx.viewer));
  return J(page);
});

R('GET', '/api/follow/count/:userId', {}, (ctx) => {
  const uid = intParam(ctx.params.userId);
  const u = uid === null ? null : db.usersById.get(uid);
  if (!u) return E(1001, '用户不存在');
  return J({ followingCount: u.followingCount, followersCount: u.followersCount });
});

/* --------------------------------- uploads ------------------------------- */
const UPLOAD_LIMITS = { image: 10 * 1024 * 1024, video: 200 * 1024 * 1024, file: 200 * 1024 * 1024 };

function handleUpload(kind) {
  return (ctx) => {
    const ct = String(ctx.req.headers['content-type'] || '');
    const m = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(ct);
    if (!/multipart\/form-data/i.test(ct) || !m) return E(4001, '文件上传失败');
    if (!ctx.rawBody || ctx.rawBody.length === 0) return E(4001, '文件上传失败');
    const boundary = (m[1] || m[2] || '').trim();
    const parts = parseMultipart(ctx.rawBody, boundary);
    // the contract fixes the field name to `file`
    const filePart = parts.find((p) => p.name === 'file');
    if (!filePart) {
      console.log(`[mock] upload rejected — no "file" part. parts received: [${parts.map((p) => p.name || '(unnamed)').join(', ')}]`);
      return E(4001, '文件上传失败');
    }
    if (!filePart.data || filePart.data.length === 0) return E(4001, '文件上传失败');
    if (filePart.data.length > UPLOAD_LIMITS[kind]) return E(4001, '文件过大');
    const stored = storeFile(filePart.data, filePart.filename, filePart.contentType || '');
    return J({ url: stored.url }, '上传成功');
  };
}
R('POST', '/api/upload/image', { auth: true }, handleUpload('image'));
R('POST', '/api/upload/video', { auth: true }, handleUpload('video'));
R('POST', '/api/upload/file', { auth: true }, handleUpload('file'));

/* ------------------------------ notifications ---------------------------- */
R('GET', '/api/notification/unread-count', { auth: true }, (ctx) => J(unreadCounts(ctx.viewer.id)));

R('GET', '/api/notification/list', { auth: true }, (ctx) => {
  const category = intParam(ctx.query.get('category'));
  const type = intParam(ctx.query.get('type'));
  let list = db.notifications.filter((n) => n.receiverId === ctx.viewer.id);
  if (category !== null && CATEGORY_TYPES[category]) {
    const allowed = CATEGORY_TYPES[category];
    list = list.filter((n) => allowed.includes(n.type));
  }
  if (type !== null) list = list.filter((n) => n.type === type);
  list = [...list].sort((a, b) => (a.createTime < b.createTime ? 1 : -1));
  const page = paginate(list, ctx.query);
  page.records = page.records.map(notificationVO);
  return J(page);
});

// read-all before read/:id — both are 3 segments
R('PUT', '/api/notification/read-all', { auth: true }, (ctx) => {
  const category = intParam(ctx.query.get('category'));
  const allowed = category !== null ? CATEGORY_TYPES[category] : null;
  let n = 0;
  for (const item of db.notifications) {
    if (item.receiverId !== ctx.viewer.id || item.read) continue;
    if (allowed && !allowed.includes(item.type)) continue;
    item.read = true;
    n += 1;
  }
  return J({ updated: n }, '已全部标记为已读');
});

R('PUT', '/api/notification/read/:id', { auth: true }, (ctx) => {
  const id = intParam(ctx.params.id);
  const n = id === null ? null : db.notifications.find((x) => x.id === id);
  if (!n) return E(5001, '通知不存在');
  if (n.receiverId !== ctx.viewer.id) return E(5001, '无权操作此通知');
  n.read = true;
  return J(notificationVO(n), '已标记为已读');
});

/* ----------------------------------- AI ---------------------------------- */
R('POST', '/api/ai/chat', { auth: true }, (ctx) => {
  const b = ctx.json || {};
  const message = typeof b.message === 'string' ? b.message : '';
  if (!message.trim()) return E(5002, '参数缺失');
  if (message.length > 4000) return E(5001, '问题不能超过 4000 个字符');
  if (b.systemPrompt !== undefined && String(b.systemPrompt).length > 1000) return E(5001, '参数错误');
  return J(aiReply(message));
});

R('GET', '/api/ai/suggestions', { auth: true }, () => J(AI_SUGGESTIONS));

/* ------------------------------ mock media ------------------------------- */
R('GET', '/img', {}, (ctx) => {
  const [w, h] = clampSize(ctx.query.get('w'), ctx.query.get('h'), 800, 600);
  const seed = ctx.query.get('seed') || `${w}x${h}`;
  const text = ctx.query.get('text') || '';
  const buf = cachedImage(`img|${w}x${h}|${seed}|${text}`, () => generateImage(w, h, seed, text ? 'text' : 'post'));
  return { status: 200, buf, contentType: 'image/png', cache: true };
});

R('GET', '/avatar', {}, (ctx) => {
  const size = Number.parseInt(ctx.query.get('size') || '200', 10);
  const s = clampSize(size, size, 200, 200, 512 * 512)[0];
  const seed = ctx.query.get('seed') || 'avatar';
  const buf = cachedImage(`avatar|${s}|${seed}`, () => generateImage(s, s, `avatar:${seed}`, 'avatar'));
  return { status: 200, buf, contentType: 'image/png', cache: true };
});

R('GET', '/files/:id', {}, (ctx) => {
  const rec = db.files.get(ctx.params.id);
  if (!rec) return E(5001, '文件不存在');
  return { status: 200, buf: rec.bytes, contentType: rec.contentType, cache: true };
});

R('GET', '/video/:name', {}, (ctx) => {
  const name = path.basename(ctx.params.name);
  const full = path.join(ASSET_DIR, name);
  if (!full.startsWith(ASSET_DIR)) return E(5001, '文件不存在');
  let buf = null;
  try {
    buf = fs.readFileSync(full);
  } catch {
    buf = null;
  }
  if (!buf) return E(5001, '文件不存在');
  return { status: 200, buf, contentType: MIME_BY_EXT[path.extname(name)] || 'application/octet-stream', cache: true };
});

/* ========================================================================== *
 * response writers
 * ========================================================================== */
function sendJson(ctx, status, body) {
  const s = JSON.stringify(body);
  ctx.status = status;
  ctx.res.writeHead(status, {
    ...CORS_HEADERS,
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(s),
    'Cache-Control': 'no-store',
  });
  ctx.res.end(s);
}

function sendBuffer(req, ctx, status, buf, contentType, { cache = false, extra = {} } = {}) {
  ctx.status = status;
  const range = req.headers.range;
  const headers = { ...CORS_HEADERS, 'Content-Type': contentType, 'Accept-Ranges': 'bytes', ...extra };
  if (cache) headers['Cache-Control'] = 'public, max-age=86400';
  else headers['Cache-Control'] = 'no-store';
  if (range) {
    const m = /^bytes=(\d*)-(\d*)$/.exec(String(range).trim());
    if (m) {
      let start = m[1] === '' ? null : Number.parseInt(m[1], 10);
      let end = m[2] === '' ? null : Number.parseInt(m[2], 10);
      if (start === null && end === null) {
        /* malformed */
      } else {
        if (start === null) {
          // suffix range: last N bytes
          start = Math.max(0, buf.length - end);
          end = buf.length - 1;
        } else if (end === null || end >= buf.length) {
          end = buf.length - 1;
        }
        if (start > end || start >= buf.length) {
          ctx.res.writeHead(416, { ...CORS_HEADERS, 'Content-Range': `bytes */${buf.length}` });
          ctx.res.end();
          return;
        }
        const slice = buf.subarray(start, end + 1);
        ctx.res.writeHead(206, {
          ...headers,
          'Content-Range': `bytes ${start}-${end}/${buf.length}`,
          'Content-Length': slice.length,
        });
        ctx.res.end(slice);
        return;
      }
    }
  }
  ctx.res.writeHead(status, { ...headers, 'Content-Length': buf.length });
  ctx.res.end(buf);
}

/* ========================================================================== *
 * request pipeline
 * ========================================================================== */
async function handle(req, res) {
  const started = Date.now();
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  let pathname = url.pathname;
  if (pathname.length > 1 && pathname.endsWith('/')) pathname = pathname.replace(/\/+$/, '');
  const ctx = { req, res, url, pathname, query: url.searchParams, params: {}, status: 200, viewer: null, json: {}, rawBody: null };
  res.on('finish', () => {
    const ms = Date.now() - started;
    console.log(`${req.method} ${pathname}${url.search || ''} -> ${ctx.status} (${ms}ms)`);
  });

  // CORS preflight
  if (req.method === 'OPTIONS') {
    ctx.status = 204;
    res.writeHead(204, CORS_HEADERS);
    res.end();
    return;
  }

  // fault injection — checked before anything else so it can be exercised on any route
  const mockFail = String(req.headers['x-mock-fail'] || '').trim().toLowerCase();
  if (mockFail === '1') {
    sendJson(ctx, 500, fail(500, '服务异常'));
    return;
  }
  if (mockFail === '401') {
    sendJson(ctx, 401, fail(1005, '用户未登录'));
    return;
  }
  if (mockFail === 'timeout') {
    console.log('[mock] X-Mock-Fail: timeout → sleeping 20s');
    await sleep(20000);
    sendJson(ctx, 200, ok(null, '超时模拟结束'));
    return;
  }

  // latency: only for /api/* by default (the waterfall would crawl if images were delayed too)
  const delayHeader = req.headers['x-mock-delay'];
  let delayMs;
  if (delayHeader !== undefined) {
    const v = Number.parseInt(String(delayHeader), 10);
    delayMs = Number.isFinite(v) ? Math.min(120000, Math.max(0, v)) : 0;
  } else {
    delayMs = pathname.startsWith('/api/') ? randInt(DEFAULT_LATENCY_MIN, DEFAULT_LATENCY_MAX) : 0;
  }

  // body
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    const { buf, over } = await readBody(req, UPLOAD_LIMITS.video + 1024 * 1024);
    if (over) {
      await sleep(delayMs);
      sendJson(ctx, 400, fail(4001, '文件过大'));
      return;
    }
    ctx.rawBody = buf;
    const ct = String(req.headers['content-type'] || '');
    if (/application\/json/i.test(ct) && buf.length) {
      try {
        ctx.json = JSON.parse(buf.toString('utf8'));
      } catch {
        await sleep(delayMs);
        sendJson(ctx, 400, fail(5001, '请求体不是合法 JSON'));
        return;
      }
    }
  }

  const hit = matchRoute(req.method, pathname);
  if (!hit) {
    await sleep(delayMs);
    sendJson(ctx, 404, { code: 404, message: `接口不存在: ${req.method} ${pathname}`, data: null, timestamp: Date.now() });
    return;
  }
  ctx.params = hit.params;

  if (hit.route.needAuth) {
    const { user, err } = resolveAuth(req);
    if (err) {
      await sleep(delayMs);
      sendJson(ctx, httpForCode(err.code), fail(err.code, err.message));
      return;
    }
    ctx.viewer = user;
  } else {
    ctx.viewer = optionalUser(req);
  }

  await sleep(delayMs);

  let out;
  try {
    out = await hit.route.handler(ctx);
  } catch (err) {
    console.error(`[error] ${req.method} ${pathname}:`, err);
    sendJson(ctx, 500, fail(500, '服务异常'));
    return;
  }
  if (!out) {
    sendJson(ctx, 200, ok(null));
    return;
  }
  if (out.buf !== undefined) {
    sendBuffer(req, ctx, out.status || 200, out.buf, out.contentType || 'application/octet-stream', { cache: !!out.cache });
    return;
  }
  sendJson(ctx, out.status || 200, out.body);
}

/* ========================================================================== *
 * bootstrap
 * ========================================================================== */
const seedOnly = process.argv.includes('--seed-only');

if (seedOnly) {
  console.log('— seed summary —');
  console.log(`users:         ${db.users.length}`);
  console.log(`posts:         ${db.posts.length}  (image: ${db.posts.filter((p) => p.type === 0).length}, video: ${db.posts.filter((p) => p.type === 1).length})`);
  const firstLevel = db.comments.filter((c) => c.parentId === 0).length;
  console.log(`comments:      ${db.comments.length}  (first-level: ${firstLevel}, replies: ${db.comments.length - firstLevel})`);
  console.log(`likes:         ${db.postLikes.length}`);
  console.log(`collects:      ${db.collects.length}`);
  console.log(`follows:       ${db.follows.length}`);
  console.log(`notifications: ${db.notifications.length}`);
  const admin = db.usersById.get(1);
  console.log(
    `admin(1): posts=${db.posts.filter((p) => p.userId === 1).length} following=${admin.followingCount} followers=${admin.followersCount}`,
  );
  console.log(
    `          received: likeCount=${admin.likeCount} collectCount=${admin.collectCount} likeAndCollectCount=${admin.likeAndCollectCount}`,
  );
  console.log(
    `          own:      likedPostCount=${admin.likedPostCount} collectedPostCount=${admin.collectedPostCount}`,
  );
  const quiet = db.eventCounts || {};
  console.log(
    `counter-consistency: ${quiet.bumped} aggregate(s) raised to cover their like/collect records; ` +
      `notes still at 0 likeCount=${quiet.zeroLikePosts} (ids ${(quiet.quietPosts || []).join(',')}) collectCount=${quiet.zeroCollectPosts}`,
  );
  // invariant: no note may display a count below the number of records pointing at it
  const recL = new Map();
  const recC = new Map();
  for (const l of db.postLikes) recL.set(l.postId, (recL.get(l.postId) || 0) + 1);
  for (const c of db.collects) recC.set(c.postId, (recC.get(c.postId) || 0) + 1);
  const bad = db.posts.filter((p) => p.likeCount < (recL.get(p.id) || 0) || p.collectCount < (recC.get(p.id) || 0));
  console.log(
    `                      notes with aggregate < record count: ${bad.length} ${bad.length ? '(this would drift!)' : '(no toggle can ever lose a decrement)'}`,
  );
  const unread = unreadCounts(1);
  console.log(`admin unread:  ${JSON.stringify(unread)}`);
  console.log(`user1 unread:  ${JSON.stringify(unreadCounts(2))}`);
  process.exit(0);
}

ensureSampleVideoAsync().then(() => {
  const restored = loadExistingUploads();
  if (restored) console.log(`[mock] restored ${restored} previously uploaded file(s) from uploads/`);
  const server = http.createServer((req, res) => {
    handle(req, res).catch((err) => {
      console.error('[fatal]', err);
      try {
        res.writeHead(500, { ...CORS_HEADERS, 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify(fail(500, '服务异常')));
      } catch {
        /* ignore */
      }
    });
  });
  server.listen(PORT, HOST, () => {
    console.log('');
    console.log('  小红书 mock server');
    console.log(`  listening    http://localhost:${PORT}  (bind ${HOST})`);
    console.log(`  PUBLIC_BASE  ${PUBLIC_BASE}`);
    console.log(`  seed         ${db.users.length} users / ${db.posts.length} posts / ${db.comments.length} comments / ${db.notifications.length} notifications`);
    console.log(`  login        admin · user1 · user2 · user3 · testuser   (password 123456)`);
    console.log(`  emulator     PUBLIC_BASE=http://10.0.2.2:${PORT} node server.mjs`);
    console.log('');
  });
});
