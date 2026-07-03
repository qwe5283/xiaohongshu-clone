const { spawn } = require("node:child_process");
const crypto = require("node:crypto");
const fs = require("node:fs/promises");
const fss = require("node:fs");
const http = require("node:http");
const path = require("node:path");

const CRAWLER_ROOT = path.resolve(__dirname, "..");
const OUTPUT_DIR = path.join(CRAWLER_ROOT, "output");
const OUT_DIR = path.join(OUTPUT_DIR, "crawl");
const RAW_DIR = path.join(OUT_DIR, "raw");
const MEDIA_DIR = path.join(CRAWLER_ROOT, "media", "xhs-media");
const DATA_PATH = path.join(OUT_DIR, "xhs_public_100.json");
const CHROME_PATH =
  process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const PORT = Number(process.env.XHS_CDP_PORT || 9223);
const TARGET_POSTS = Number(process.env.XHS_TARGET_POSTS || 100);
const MAX_CANDIDATES = Number(process.env.XHS_MAX_CANDIDATES || 420);
const MAX_ASSET_BYTES = Number(process.env.XHS_MAX_ASSET_BYTES || 80 * 1024 * 1024);

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function cleanGeneratedOutput() {
  console.log("Cleaning crawler output and media directories...");
  await fs.rm(OUTPUT_DIR, { recursive: true, force: true });
  await fs.rm(MEDIA_DIR, { recursive: true, force: true });
  await fs.mkdir(RAW_DIR, { recursive: true });
  await fs.mkdir(MEDIA_DIR, { recursive: true });
}

function requestJson(url) {
  return new Promise((resolve, reject) => {
    http
      .get(url, (res) => {
        let body = "";
        res.setEncoding("utf8");
        res.on("data", (chunk) => (body += chunk));
        res.on("end", () => {
          try {
            resolve(JSON.parse(body));
          } catch (error) {
            reject(error);
          }
        });
      })
      .on("error", reject);
  });
}

class CdpClient {
  constructor(wsUrl) {
    this.wsUrl = wsUrl;
    this.nextId = 1;
    this.pending = new Map();
    this.handlers = new Map();
  }

  async connect() {
    this.ws = new WebSocket(this.wsUrl);
    this.ws.onmessage = (event) => this.onMessage(event.data);
    await new Promise((resolve, reject) => {
      this.ws.onopen = resolve;
      this.ws.onerror = reject;
    });
  }

  on(event, handler) {
    if (!this.handlers.has(event)) this.handlers.set(event, []);
    this.handlers.get(event).push(handler);
  }

  onMessage(raw) {
    const message = JSON.parse(raw);
    if (message.id && this.pending.has(message.id)) {
      const { resolve, reject } = this.pending.get(message.id);
      this.pending.delete(message.id);
      if (message.error) reject(new Error(message.error.message));
      else resolve(message.result || {});
      return;
    }
    const handlers = this.handlers.get(message.method) || [];
    for (const handler of handlers) handler(message.params || {});
  }

  send(method, params = {}) {
    const id = this.nextId++;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id);
          reject(new Error(`CDP timeout: ${method}`));
        }
      }, 30000);
    });
  }

  close() {
    this.ws?.close();
  }
}

async function launchChrome() {
  const userDataDir = path.join(OUT_DIR, `chrome-profile-${Date.now()}`);
  await fs.mkdir(userDataDir, { recursive: true });
  const args = [
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${userDataDir}`,
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-popup-blocking",
    "--disable-background-networking",
    "--window-size=1440,1000",
    "about:blank",
  ];
  const child = spawn(CHROME_PATH, args, { stdio: "ignore", detached: false });
  for (let i = 0; i < 60; i++) {
    try {
      const version = await requestJson(`http://127.0.0.1:${PORT}/json/version`);
      return { child, wsUrl: version.webSocketDebuggerUrl };
    } catch (_) {
      await sleep(500);
    }
  }
  throw new Error("Chrome did not expose a DevTools endpoint in time.");
}

async function createPageClient(browserWsUrl) {
  const browser = new CdpClient(browserWsUrl);
  await browser.connect();
  const target = await browser.send("Target.createTarget", { url: "about:blank" });
  const targets = await requestJson(`http://127.0.0.1:${PORT}/json/list`);
  const page = targets.find((item) => item.id === target.targetId) || targets.find((item) => item.type === "page");
  browser.close();
  const client = new CdpClient(page.webSocketDebuggerUrl);
  await client.connect();
  await client.send("Page.enable");
  await client.send("Network.enable", { maxResourceBufferSize: 100000000, maxTotalBufferSize: 200000000 });
  await client.send("Runtime.enable");
  return client;
}

async function evaluate(client, expression, timeout = 30000) {
  const result = await client.send("Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true,
    timeout,
  });
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text || "Runtime.evaluate failed");
  }
  return result.result ? result.result.value : undefined;
}

async function navigate(client, url) {
  await client.send("Page.navigate", { url });
  await sleep(3000);
}

async function closeLoginOverlays(client) {
  await evaluate(
    client,
    `(() => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
      const candidates = [...document.querySelectorAll('button, svg, [class*=close], [aria-label*=关闭], [aria-label*=close]')];
      for (const el of candidates.slice(0, 20)) {
        const text = String(el.innerText || el.getAttribute('aria-label') || el.className || '');
        if (/关闭|close|Close|取消|modal/.test(text)) {
          try { el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window })); } catch (_) {}
        }
      }
      return true;
    })()`,
    10000
  ).catch(() => false);
}

async function clickCategory(client, name) {
  return evaluate(
    client,
    `(() => {
      const target = [...document.querySelectorAll('button, a, div, span')]
        .find(el => (el.innerText || '').trim() === ${JSON.stringify(name)});
      if (!target) return false;
      target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
      return true;
    })()`,
    10000
  ).catch(() => false);
}

function normalizeUrl(url) {
  if (!url) return "";
  if (url.startsWith("//")) return `https:${url}`;
  if (url.startsWith("http://")) return `https://${url.slice("http://".length)}`;
  return url;
}

function likeToInt(value, fallback = 0) {
  if (value == null) return fallback;
  const text = String(value).trim();
  if (!text) return fallback;
  if (text.includes("万")) {
    const n = Number(text.replace("万+", "").replace("万", ""));
    return Number.isFinite(n) ? Math.round(n * 10000) : fallback;
  }
  const n = Number(text.replace("+", ""));
  return Number.isFinite(n) ? Math.round(n) : fallback;
}

function noteCardToPost(item) {
  const card = item.note_card || item.noteCard || {};
  const user = card.user || {};
  const cover = card.cover || {};
  const infoList = cover.info_list || cover.infoList || [];
  const imageUrls = [];
  for (const info of infoList) {
    const url = normalizeUrl(info.url);
    if (url && !imageUrls.includes(url)) imageUrls.push(url);
  }
  const coverUrl = normalizeUrl(cover.url_default || cover.urlDefault || cover.url_pre || cover.urlPre || imageUrls[0]);
  if (coverUrl && !imageUrls.includes(coverUrl)) imageUrls.push(coverUrl);
  const type = card.type === "video" || card.video ? 1 : 0;
  return {
    source_note_id: item.id || item.note_id || item.noteId,
    xsec_token: item.xsec_token || item.xsecToken || "",
    title: (card.display_title || card.displayTitle || "").trim(),
    content: "",
    type,
    cover_image_remote: coverUrl,
    video_url_remote: "",
    image_urls_remote: imageUrls.slice(0, 4),
    width: Number(cover.width || 0),
    height: Number(cover.height || 0),
    like_count: likeToInt((card.interact_info || card.interactInfo || {}).liked_count || (card.interact_info || card.interactInfo || {}).likedCount),
    collect_count: 0,
    comment_count: 0,
    view_count: 0,
    author: {
      source_user_id: user.user_id || user.userId || "",
      nickname: user.nickname || user.nick_name || user.nickName || "",
      avatar_remote: normalizeUrl(user.avatar || user.image),
      xsec_token: user.xsec_token || user.xsecToken || "",
    },
    raw_feed_item: item,
  };
}

function postsFromHomefeed(payload) {
  const items = payload?.data?.items || payload?.data?.feeds || [];
  return items.map(noteCardToPost).filter((post) => post.source_note_id && post.xsec_token && post.cover_image_remote);
}

function postsFromInitialState(state) {
  const feeds = state?.feed?.feeds || state?.red?.feeds || [];
  return feeds.map(noteCardToPost).filter((post) => post.source_note_id && post.xsec_token && post.cover_image_remote);
}

function mergePosts(existing, incoming) {
  const byId = new Map(existing.map((post) => [post.source_note_id, post]));
  for (const post of incoming) {
    if (!byId.has(post.source_note_id)) byId.set(post.source_note_id, post);
  }
  return [...byId.values()];
}

async function getDomPosts(client) {
  return evaluate(
    client,
    `(() => {
      const normalize = (url) => {
        if (!url) return '';
        if (url.startsWith('//')) return 'https:' + url;
        return url;
      };
      return [...document.querySelectorAll('.note-item')].map((el) => {
        const cover = el.querySelector('a.cover[href*="/explore/"], a[href*="/explore/"]');
        const href = cover?.href || '';
        const noteId = (href.match(/\\/explore\\/([0-9a-f]{24})/) || [])[1] || '';
        const token = new URL(href || location.href).searchParams.get('xsec_token') || '';
        const title = (el.querySelector('.title')?.innerText || '').trim();
        const img = el.querySelector('img.cover, .cover img, img')?.src || '';
        const authorEl = el.querySelector('.author, .name, .nickname');
        const nickname = (authorEl?.innerText || '').trim();
        const avatar = el.querySelector('.author-wrapper img, .author img, img.avatar')?.src || '';
        const text = el.innerText || '';
        const like = (text.split('\\n').pop() || '').trim();
        return {
          source_note_id: noteId,
          xsec_token: token,
          title,
          content: '',
          type: el.innerHTML.includes('play') || el.querySelector('[class*=play]') ? 1 : 0,
          cover_image_remote: normalize(img),
          video_url_remote: '',
          image_urls_remote: img ? [normalize(img)] : [],
          width: 0,
          height: 0,
          like_count: like,
          collect_count: 0,
          comment_count: 0,
          view_count: 0,
          author: { source_user_id: 'dom_' + (nickname || noteId), nickname, avatar_remote: normalize(avatar), xsec_token: '' },
          raw_feed_item: null
        };
      }).filter(p => p.source_note_id && p.xsec_token && p.cover_image_remote);
    })()`,
    10000
  ).then((items) =>
    (items || []).map((post) => ({
      ...post,
      like_count: likeToInt(post.like_count),
    }))
  );
}

function parseDetail(payload) {
  const item = payload?.data?.items?.[0]?.note_card || payload?.data?.items?.[0]?.noteCard || payload?.data?.note || payload?.data;
  if (!item) return null;
  const interact = item.interact_info || item.interactInfo || {};
  const user = item.user || {};
  const imageList = item.image_list || item.imageList || [];
  const images = imageList
    .map((img) => normalizeUrl(img.url_default || img.urlDefault || img.url_pre || img.urlPre || img.url))
    .filter(Boolean);
  let videoUrl = "";
  const stream = item.video?.media?.stream || item.video?.stream;
  const h264 = stream?.h264 || [];
  if (h264.length) {
    const picked = h264.find((v) => v.master_url || v.masterUrl) || h264[0];
    videoUrl = normalizeUrl(picked.master_url || picked.masterUrl || (picked.backup_urls || picked.backupUrls || [])[0]);
  }
  return {
    title: item.title || "",
    content: item.desc || item.content || "",
    type: item.type === "video" || videoUrl ? 1 : 0,
    image_urls_remote: images,
    cover_image_remote: images[0] || "",
    video_url_remote: videoUrl,
    like_count: likeToInt(interact.liked_count || interact.likedCount),
    collect_count: likeToInt(interact.collected_count || interact.collectedCount),
    comment_count: likeToInt(interact.comment_count || interact.commentCount),
    author: {
      source_user_id: user.user_id || user.userId || "",
      nickname: user.nickname || "",
      avatar_remote: normalizeUrl(user.avatar || user.image),
      xsec_token: user.xsec_token || user.xsecToken || "",
    },
  };
}

function parseCommentUsers(comments) {
  const users = [];
  function add(user) {
    if (!user?.user_id && !user?.userId) return;
    users.push({
      source_user_id: user.user_id || user.userId,
      nickname: user.nickname || "",
      avatar_remote: normalizeUrl(user.image || user.avatar),
      xsec_token: user.xsec_token || user.xsecToken || "",
    });
  }
  for (const comment of comments) {
    add(comment.user_info || comment.userInfo);
    for (const sub of comment.sub_comments || comment.subComments || []) {
      add(sub.user_info || sub.userInfo);
      add(sub.target_comment?.user_info || sub.targetComment?.userInfo);
    }
  }
  return users;
}

function parseComments(payload) {
  const data = payload?.data || {};
  return {
    comments: data.comments || [],
    cursor: data.cursor || "",
    has_more: Boolean(data.has_more || data.hasMore),
    xsec_token: data.xsec_token || data.xsecToken || "",
  };
}

async function downloadAsset(url, localPrefix, index) {
  const remote = normalizeUrl(url);
  if (!remote) return "";
  const hash = crypto.createHash("sha1").update(remote).digest("hex").slice(0, 16);
  const safePrefix = localPrefix.replace(/[^a-z0-9_-]/gi, "_").slice(0, 40);
  const defaultExt = remote.includes(".mp4") ? ".mp4" : ".jpg";
  let ext = defaultExt;
  const match = remote.match(/\.(jpg|jpeg|png|webp|gif|mp4)(?:[?!]|$)/i);
  if (match) ext = `.${match[1].toLowerCase().replace("jpeg", "jpg")}`;
  const fileName = `${safePrefix}_${index}_${hash}${ext}`;
  const filePath = path.join(MEDIA_DIR, fileName);
  const publicUrl = `/xhs-media/${fileName}`;
  if (fss.existsSync(filePath)) return publicUrl;

  const response = await fetch(remote, {
    headers: {
      referer: "https://www.xiaohongshu.com/",
      "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/149 Safari/537.36",
    },
  });
  if (!response.ok) {
    console.warn(`asset ${response.status}: ${remote}`);
    return remote;
  }
  const length = Number(response.headers.get("content-length") || 0);
  if (length && length > MAX_ASSET_BYTES) {
    console.warn(`asset too large (${length} bytes), keeping remote URL: ${remote}`);
    return remote;
  }
  const chunks = [];
  let total = 0;
  for await (const chunk of response.body) {
    total += chunk.length;
    if (total > MAX_ASSET_BYTES) {
      console.warn(`asset exceeded cap, keeping remote URL: ${remote}`);
      return remote;
    }
    chunks.push(chunk);
  }
  await fs.writeFile(filePath, Buffer.concat(chunks));
  return publicUrl;
}

async function archiveAssets(dataset) {
  await fs.mkdir(MEDIA_DIR, { recursive: true });
  const users = new Map();
  const userRefs = new Map();
  function addUserRef(user) {
    if (!user?.source_user_id) return;
    users.set(user.source_user_id, user);
    if (!userRefs.has(user.source_user_id)) userRefs.set(user.source_user_id, []);
    userRefs.get(user.source_user_id).push(user);
  }
  for (const post of dataset.posts) {
    const prefix = post.source_note_id;
    post.cover_image = await downloadAsset(post.cover_image_remote, `${prefix}_cover`, 0);
    post.image_urls = [];
    for (let i = 0; i < post.image_urls_remote.length; i++) {
      post.image_urls.push(await downloadAsset(post.image_urls_remote[i], `${prefix}_img`, i));
    }
    post.video_url = post.video_url_remote ? await downloadAsset(post.video_url_remote, `${prefix}_video`, 0) : "";
    addUserRef(post.author);
    for (const comment of post.comments) {
      addUserRef(comment.user);
      comment.picture_urls = [];
      for (let i = 0; i < comment.picture_urls_remote.length; i++) {
        comment.picture_urls.push(await downloadAsset(comment.picture_urls_remote[i], `${prefix}_comment_${comment.source_comment_id}`, i));
      }
      for (const sub of comment.sub_comments) {
        addUserRef(sub.user);
        sub.picture_urls = [];
        for (let i = 0; i < sub.picture_urls_remote.length; i++) {
          sub.picture_urls.push(await downloadAsset(sub.picture_urls_remote[i], `${prefix}_reply_${sub.source_comment_id}`, i));
        }
      }
    }
  }
  for (const user of users.values()) {
    const avatar = await downloadAsset(user.avatar_remote, `avatar_${user.source_user_id}`, 0);
    for (const ref of userRefs.get(user.source_user_id) || []) {
      ref.avatar = avatar;
    }
  }
}

function pictureUrls(raw) {
  return (raw.pictures || [])
    .map((p) => normalizeUrl(p.url_default || p.urlDefault || p.url_pre || p.urlPre || p.url))
    .filter(Boolean);
}

function normalizeComment(raw) {
  const userInfo = raw.user_info || raw.userInfo || {};
  return {
    source_comment_id: raw.id || "",
    content: raw.content || (raw.audio_info || raw.audioInfo ? "[语音评论]" : "[图片评论]"),
    like_count: likeToInt(raw.like_count || raw.likeCount),
    create_time: raw.create_time || raw.createTime || 0,
    ip_location: raw.ip_location || raw.ipLocation || "",
    picture_urls_remote: pictureUrls(raw),
    user: {
      source_user_id: userInfo.user_id || userInfo.userId || "",
      nickname: userInfo.nickname || "",
      avatar_remote: normalizeUrl(userInfo.image || userInfo.avatar),
      xsec_token: userInfo.xsec_token || userInfo.xsecToken || "",
    },
    sub_comments: (raw.sub_comments || raw.subComments || []).slice(0, 1).map((sub) => {
      const subUser = sub.user_info || sub.userInfo || {};
      const targetUser = sub.target_comment?.user_info || sub.targetComment?.userInfo || {};
      return {
        source_comment_id: sub.id || "",
        source_parent_comment_id: raw.id || "",
        source_reply_user_id: targetUser.user_id || targetUser.userId || "",
        content: sub.content || (sub.audio_info || sub.audioInfo ? "[语音回复]" : "[图片回复]"),
        like_count: likeToInt(sub.like_count || sub.likeCount),
        create_time: sub.create_time || sub.createTime || 0,
        ip_location: sub.ip_location || sub.ipLocation || "",
        picture_urls_remote: pictureUrls(sub),
        user: {
          source_user_id: subUser.user_id || subUser.userId || "",
          nickname: subUser.nickname || "",
          avatar_remote: normalizeUrl(subUser.image || subUser.avatar),
          xsec_token: subUser.xsec_token || subUser.xsecToken || "",
        },
      };
    }),
  };
}

async function main() {
  if (process.argv.includes("--archive-existing")) {
    const dataset = JSON.parse(await fs.readFile(DATA_PATH, "utf8"));
    console.log("Archiving static assets from existing crawl dataset...");
    await archiveAssets(dataset);
    await fs.writeFile(DATA_PATH, JSON.stringify(dataset, null, 2), "utf8");
    console.log(`Updated ${DATA_PATH}`);
    return;
  }

  await cleanGeneratedOutput();

  const { child, wsUrl } = await launchChrome();
  const client = await createPageClient(wsUrl);
  const responses = new Map();
  const requestMeta = new Map();
  let sequence = 0;

  client.on("Network.responseReceived", (params) => {
    const url = params.response?.url || "";
    if (
      url.includes("/api/sns/web/v1/homefeed") ||
      url.includes("/api/sns/web/v1/feed") ||
      url.includes("/api/sns/web/v2/comment/page")
    ) {
      requestMeta.set(params.requestId, { url, status: params.response.status });
    }
  });
  client.on("Network.loadingFinished", async (params) => {
    const meta = requestMeta.get(params.requestId);
    if (!meta) return;
    try {
      const body = await client.send("Network.getResponseBody", { requestId: params.requestId });
      const parsed = JSON.parse(body.body);
      const key = `${String(++sequence).padStart(4, "0")}_${meta.url.includes("comment/page") ? "comment" : meta.url.includes("/feed") && !meta.url.includes("homefeed") ? "detail" : "homefeed"}`;
      responses.set(params.requestId, { ...meta, body: parsed, key });
      await fs.writeFile(path.join(RAW_DIR, `${key}.json`), JSON.stringify(parsed, null, 2), "utf8");
    } catch (_) {
      // Some streaming/media responses are not retained. They are not needed for structured data.
    }
  });

  try {
    console.log("Opening Explore...");
    await navigate(client, "https://www.xiaohongshu.com/");
    await closeLoginOverlays(client);
    await navigate(client, "https://www.xiaohongshu.com/explore");
    await closeLoginOverlays(client);
    let posts = [];
    const initialState = await evaluate(client, "window.__INITIAL_STATE__ || null");
    posts = mergePosts(posts, postsFromInitialState(initialState));
    posts = mergePosts(posts, await getDomPosts(client));
    for (let i = 0; i < 28 && posts.length < MAX_CANDIDATES; i++) {
      await evaluate(
        client,
        `new Promise(resolve => {
          window.scrollBy(0, Math.max(1200, window.innerHeight * 1.4));
          document.documentElement.scrollTop += Math.max(1200, window.innerHeight * 1.4);
          document.body.scrollTop += Math.max(1200, window.innerHeight * 1.4);
          for (const el of document.querySelectorAll('*')) {
            const style = getComputedStyle(el);
            if ((style.overflowY === 'auto' || style.overflowY === 'scroll') && el.scrollHeight > el.clientHeight) {
              el.scrollTop += Math.max(1200, el.clientHeight * 1.4);
            }
          }
          window.dispatchEvent(new WheelEvent('wheel', { deltaY: 1800, bubbles: true, cancelable: true }));
          document.dispatchEvent(new WheelEvent('wheel', { deltaY: 1800, bubbles: true, cancelable: true }));
          setTimeout(resolve, 1400);
        })`,
        5000
      );
      for (const response of responses.values()) {
        if (response.url.includes("homefeed")) posts = mergePosts(posts, postsFromHomefeed(response.body));
      }
      posts = mergePosts(posts, await getDomPosts(client));
      console.log(`feed candidates: ${posts.length}`);
    }
    const categories = ["穿搭", "美食", "彩妆", "影视", "职场", "情感", "家居", "游戏", "旅行", "健身", "视频"];
    for (const category of categories) {
      if (posts.length >= MAX_CANDIDATES) break;
      const clicked = await clickCategory(client, category);
      if (!clicked) continue;
      await sleep(2500);
      posts = mergePosts(posts, await getDomPosts(client));
      for (let i = 0; i < 8 && posts.length < MAX_CANDIDATES; i++) {
        await evaluate(
          client,
          `new Promise(resolve => {
            window.scrollBy(0, Math.max(1200, window.innerHeight * 1.4));
            document.documentElement.scrollTop += Math.max(1200, window.innerHeight * 1.4);
            document.body.scrollTop += Math.max(1200, window.innerHeight * 1.4);
            for (const el of document.querySelectorAll('*')) {
              const style = getComputedStyle(el);
              if ((style.overflowY === 'auto' || style.overflowY === 'scroll') && el.scrollHeight > el.clientHeight) {
                el.scrollTop += Math.max(1200, el.clientHeight * 1.4);
              }
            }
            window.dispatchEvent(new WheelEvent('wheel', { deltaY: 1800, bubbles: true, cancelable: true }));
            document.dispatchEvent(new WheelEvent('wheel', { deltaY: 1800, bubbles: true, cancelable: true }));
            setTimeout(resolve, 1200);
          })`,
          5000
        );
        for (const response of responses.values()) {
          if (response.url.includes("homefeed")) posts = mergePosts(posts, postsFromHomefeed(response.body));
        }
        posts = mergePosts(posts, await getDomPosts(client));
      }
      console.log(`feed candidates after ${category}: ${posts.length}`);
    }

    const selected = posts.slice(0, MAX_CANDIDATES);
    const complete = [];
    for (const [idx, post] of selected.entries()) {
      if (complete.length >= TARGET_POSTS) break;
      const beforeKeys = new Set([...responses.values()].map((r) => r.key));
      const url = `https://www.xiaohongshu.com/explore/${post.source_note_id}?xsec_token=${encodeURIComponent(post.xsec_token)}&xsec_source=pc_feed`;
      console.log(`[${idx + 1}/${selected.length}] ${post.source_note_id} ${post.title}`);
      await navigate(client, url);
      await sleep(2200);
      const pageStatus = await evaluate(
        client,
        `(() => ({ url: location.href, blocked: document.body.innerText.includes('当前笔记暂时无法浏览') }))()`
      ).catch(() => ({ url: "", blocked: false }));
      if (pageStatus.blocked) {
        console.log(`  skipped: not viewable without app/browser flow`);
        continue;
      }

      const newResponses = [...responses.values()].filter((r) => !beforeKeys.has(r.key));
      const detailPayload = [...newResponses].reverse().find((r) => r.url.includes("/api/sns/web/v1/feed"))?.body;
      const commentPayload = [...newResponses].reverse().find((r) => r.url.includes("/api/sns/web/v2/comment/page") && r.status === 200)?.body;
      let stateNote = null;
      try {
        stateNote = await evaluate(
          client,
          `(() => {
            const map = window.__INITIAL_STATE__?.note?.noteDetailMap || {};
            return map[${JSON.stringify(post.source_note_id)}]?.note || null;
          })()`
        );
      } catch (_) {
        stateNote = null;
      }
      const detail = parseDetail(detailPayload) || parseDetail({ data: { note: stateNote } });
      if (detail) {
        Object.assign(post, {
          title: detail.title || post.title,
          content: detail.content || post.content,
          type: detail.type,
          cover_image_remote: detail.cover_image_remote || post.cover_image_remote,
          image_urls_remote: detail.image_urls_remote.length ? detail.image_urls_remote : post.image_urls_remote,
          video_url_remote: detail.video_url_remote || post.video_url_remote,
          like_count: detail.like_count || post.like_count,
          collect_count: detail.collect_count || post.collect_count,
          comment_count: detail.comment_count || post.comment_count,
          author: detail.author.source_user_id ? detail.author : post.author,
        });
      }
      const commentData = parseComments(commentPayload);
      post.comments = commentData.comments.map(normalizeComment);
      post.comment_count_visible = post.comments.reduce((sum, c) => sum + 1 + c.sub_comments.length, 0);
      post.crawl_detail_ok = Boolean(detailPayload || stateNote);
      post.crawl_comment_ok = Boolean(commentPayload);
      post.source_url = url;

      if (post.cover_image_remote && post.author.source_user_id) {
        complete.push(post);
      }
      await sleep(900);
    }

    const finalPosts = complete.slice(0, TARGET_POSTS);
    if (finalPosts.length < TARGET_POSTS) {
      const debugPage = await evaluate(
        client,
        `(() => ({ url: location.href, title: document.title, text: document.body.innerText.slice(0, 3000), html: document.body.innerHTML.slice(0, 3000), noteItems: document.querySelectorAll('.note-item').length }))()`,
        10000
      ).catch((error) => ({ error: String(error) }));
      await fs.writeFile(path.join(OUT_DIR, "debug-explore.json"), JSON.stringify(debugPage, null, 2), "utf8");
      throw new Error(`Only crawled ${finalPosts.length} usable posts; expected ${TARGET_POSTS}.`);
    }
    const dataset = {
      crawled_at: new Date().toISOString(),
      source: "https://www.xiaohongshu.com/explore",
      notes: "Only public, not-logged-in visible page data was captured. Comment pages are collected from page-initiated requests.",
      posts: finalPosts,
    };
    console.log("Archiving static assets...");
    await archiveAssets(dataset);
    await fs.writeFile(DATA_PATH, JSON.stringify(dataset, null, 2), "utf8");
    console.log(`Wrote ${DATA_PATH}`);
  } finally {
    client.close();
    child.kill();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
