# xhs-mock-server

Zero-dependency Node.js mock server for the **小红书 (Xiaohongshu) clone Android client**.

It implements **every endpoint** of [`client/docs/API契约-v2.md`](../docs/API契约-v2.md) (the client's
authoritative contract), including the three mock-only additions the contract marks as `[新增]`
(following feed, hot keywords, `like/posts/{userId}`, `text-image`, AI suggestions, the notification
category filter, `collected`/`followed` flags) — plus mock media generation, uploads, range-request
video streaming, fault injection and latency simulation.

**No npm dependencies, no `npm install`.** Node core only (`node:http`, `node:zlib`, `node:crypto`,
`node:fs`, `node:path`, `node:url`, `node:buffer`). State lives in memory and is re-seeded on every
startup.

---

## Quick start

```bash
cd client/mockserver
npm start                 # → node server.mjs, listens on http://localhost:8787
```

or directly:

```bash
node server.mjs
node server.mjs --seed-only   # print seed counts and exit (no server)
```

Sanity check:

```bash
curl http://localhost:8787/api/health
# {"code":200,"message":"服务正常","data":{"ok":true,"uptime":1234},"timestamp":...}
```

### Login accounts

All seeded users share the password **`123456`**. The five accounts the client team needs:

| username | nickname | notes |
|---|---|---|
| `admin` | 管理员 | id 1 — the "logged-in seed user": 6 own notes, 12 liked notes / 8 collected notes (`likedPostCount` / `collectedPostCount`), follows 3 users, unread notifications in **all 6 types** |
| `user1` | 用户A | id 2 — **`followUnread: 0`** so the client's badge-hiding logic is testable |
| `user2` | 用户C | id 3 |
| `user3` | 用户E | id 4 |
| `testuser` | 测试用户 | id 5 — **empty social graph**: 0 follows, 0 likes, 0 collects → exercises the empty states |

Every seeded user can log in with `123456` (handy for viewing other profiles).

### Environment variables

| var | default | meaning |
|---|---|---|
| `PORT` | `8787` | listen port |
| `HOST` | `0.0.0.0` | bind address |
| `PUBLIC_BASE` | `http://localhost:8787` | prefix baked into every absolute URL the server returns (`coverImage`, `images[].imageUrl`, `authorAvatar`, upload URLs, text-image URLs). **Must be reachable from the client.** |

### Pointing the Android emulator at it

The emulator reaches the **host machine** at `10.0.2.2`, not `localhost`. Because seed data and
upload responses contain absolute URLs, run the server with `PUBLIC_BASE` set accordingly:

```bash
PUBLIC_BASE=http://10.0.2.2:8787 node server.mjs
```

(Git Bash / macOS / Linux syntax above; on Windows `cmd.exe` use
`set PUBLIC_BASE=http://10.0.2.2:8787 && node server.mjs`, PowerShell
`$env:PUBLIC_BASE="http://10.0.2.2:8787"; node server.mjs`.)

Then point the client's Retrofit/OkHttp base URL at `http://10.0.2.2:8787/`. Physical device on the
same LAN: `PUBLIC_BASE=http://<your-lan-ip>:8787 node server.mjs`.

> If you forget `PUBLIC_BASE`, text and API calls still work (the client always calls the host it was
> configured with) but **images will not load**, because the returned URLs point at `localhost`.

To use a different port for the client only, keep the server at 8787 and use
`PUBLIC_BASE=http://10.0.2.2:8787`.

---

## Conventions

* **Base path** `/api` on every business endpoint.
* **Envelope** for every JSON response:
  `{"code":200,"message":"操作成功","data":<T>,"timestamp":1789000000000}`
  `code == 200` is success; anything else is a business failure. HTTP status is `401` for
  `1005/1006/1007` and `400` for every other business error (per contract §9).
* **Paged responses** use the MyBatis-Plus `IPage` shape — note the field names are `size` and
  `current`, **not** `pageSize`/`pageNum`:
  `{"records":[…],"total":0,"size":20,"current":1,"pages":0}`
  Request params **are** `pageNum` / `pageSize`. Defaults: `pageNum=1`, `pageSize=20`
  (the contract's default, not the legacy backend's 10); `pageSize` is clamped to 1–100.
* **Auth** `Authorization: Bearer <token>`. Endpoints marked 🔒 below return
  `HTTP 401 {"code":1005,"message":"用户未登录"}` when the token is missing or unknown.
  `expired-token` → `HTTP 401, code 1006`; `invalid-token` → `HTTP 401, code 1007`.
* **Times** `yyyy-MM-ddTHH:mm:ss` (local wall clock, no timezone, no millis) — helper `fmt(d)`.
* Empty collections are `[]`, never `null`; nullable strings are `""`.

---

## Endpoint table

`data` column describes the `data` field of the envelope. 🔒 = requires `Authorization`.
Every endpoint that returns a user returns the **same `UserVO` shape**, including the
amendment-#16 fields `collectedPostCount` / `likedPostCount`.

### User

| Method | Path | Auth | Params / body | `data` |
|---|---|---|---|---|
| POST | `/api/user/register` | — | `{username,password,nickname?,phone?}` | `UserVO` (message `注册成功`) |
| POST | `/api/user/login` | — | `{username,password}` | `{token,expiresIn:604800,user:UserVO}` |
| GET | `/api/user/me` | 🔒 | — | `UserVO` |
| GET | `/api/user/{id}` | — | — | `UserVO` |
| PUT | `/api/user/update` | 🔒 | `{nickname?,avatar?,gender?,email?,bio?,backgroundImage?,birthday?,region?,occupation?,school?}` | updated `UserVO` |

### Post

| Method | Path | Auth | Params / body | `data` |
|---|---|---|---|---|
| POST | `/api/post/create` | 🔒 | `{title,content?,videoUrl?,imageUrls?}` | `PostVO` (counters 0, `type` derived, `coverImage` = first image) |
| PUT | `/api/post/update` | 🔒 | `{id,title?,content?,videoUrl?,imageUrls?}` (`imageUrls` = full replace) | updated `PostVO` |
| DELETE | `/api/post/delete/{postId}` | 🔒 | — | `null` |
| GET | `/api/post/list` | — | `pageNum,pageSize,keyword,type,sortType(latest\|hot)` | `IPage<PostVO>` |
| GET | `/api/post/user/{userId}` | — | same as list | `IPage<PostVO>` |
| GET | `/api/post/my` | 🔒 | same as list | `IPage<PostVO>` |
| GET | `/api/post/following-feed` | 🔒 | `pageNum,pageSize` | `IPage<PostVO>` (only followed authors, newest first) |
| GET | `/api/post/hot-keywords` | — | — | `string[]` (10 items) |
| GET | `/api/post/text-image/generate` | — | `text` (≤20 chars) | **raw `image/png`** 800×1200 |
| POST | `/api/post/text-image` | — | `{text}` | `{url,width:800,height:1200}` (url served by `/files/{id}`) |
| GET | `/api/post/{postId}` | — | — | `PostVO` (**viewCount +1**) |

### Comment

| Method | Path | Auth | Params / body | `data` |
|---|---|---|---|---|
| POST | `/api/comment/create` | 🔒 | `{postId,content,parentId?,replyUserId?}` | `CommentVO` |
| DELETE | `/api/comment/delete/{commentId}` | 🔒 | — | `null` |
| GET | `/api/comment/post/{postId}` | — | `pageNum,pageSize` | `IPage<CommentVO>` (`total` = first-level count, newest first) |
| GET | `/api/comment/replies/{commentId}` | — | `pageNum,pageSize` | `IPage<CommentVO>` (oldest first) |

### Like (toggle)

| Method | Path | Auth | `data` |
|---|---|---|---|
| POST | `/api/like/post/{postId}` | 🔒 | `{liked,message}` |
| POST | `/api/like/comment/{commentId}` | 🔒 | `{liked,message}` |
| GET | `/api/like/status/post/{postId}` | 🔒 | `{liked}` |
| GET | `/api/like/status/comment/{commentId}` | 🔒 | `{liked}` |
| GET | `/api/like/posts/{userId}` | 🔒 | `IPage<PostVO>` (by like time desc) |

### Collect (toggle)

| Method | Path | Auth | `data` |
|---|---|---|---|
| POST | `/api/collect/post/{postId}` | 🔒 | `{collected,message}` |
| GET | `/api/collect/status/post/{postId}` | 🔒 | `{collected}` |
| GET | `/api/collect/posts/{userId}` | 🔒 | `IPage<PostVO>` (by collect time desc, others viewable) |

### Follow (toggle)

| Method | Path | Auth | `data` |
|---|---|---|---|
| POST | `/api/follow/{userId}` | 🔒 | `{followed,message}` (`6003` when following yourself) |
| GET | `/api/follow/status/{userId}` | 🔒 | `{followed}` |
| GET | `/api/follow/following/{userId}` | — (token optional) | `IPage<FollowUserVO>` — `followed` filled when a token is sent |
| GET | `/api/follow/followers/{userId}` | — (token optional) | `IPage<FollowUserVO>` |
| GET | `/api/follow/count/{userId}` | — | `{followingCount,followersCount}` |

### Upload (`multipart/form-data`, field name **`file`**)

| Method | Path | Auth | Limit | `data` |
|---|---|---|---|---|
| POST | `/api/upload/image` | 🔒 | 10 MB | `{url}` |
| POST | `/api/upload/video` | 🔒 | 200 MB | `{url}` |
| POST | `/api/upload/file` | 🔒 | 200 MB | `{url}` |

Uploaded bytes are content-hashed and served back from `GET /files/{id}` with the original
`Content-Type`; they are also mirrored into `uploads/` so URLs keep working after a restart.

### Notification

| Method | Path | Auth | Params | `data` |
|---|---|---|---|---|
| GET | `/api/notification/unread-count` | 🔒 | — | `{unreadCount,likeUnread,commentUnread,followUnread}` |
| GET | `/api/notification/list` | 🔒 | `pageNum,pageSize,category(1\|2\|3),type(1..6)` | `IPage<NotificationVO>` (newest first) |
| PUT | `/api/notification/read/{id}` | 🔒 | — | updated `NotificationVO` |
| PUT | `/api/notification/read-all` | 🔒 | `category?` (omitted = all) | `{updated:N}` |

`category` ⇄ `type` mapping (contract §7.2): `1 → {1,2,5}`, `2 → {3,4}`, `3 → {6}`.
Unread counts are **derived live**, so reading a notification immediately moves the badges.

### AI assistant 「点点」

| Method | Path | Auth | Params / body | `data` |
|---|---|---|---|---|
| POST | `/api/ai/chat` | 🔒 | `{message,systemPrompt?}` | `{answer,notes:PostVO[]}` |
| GET | `/api/ai/suggestions` | 🔒 | — | `string[]` (5 items) |

`answer` contains real `\n` and `「• 」` bullets. `notes` is topic-matched on 6 themes
(咖啡 / 徒步 / 穿搭 / 彩妆 / 职场 / 美食) and falls back to the 3 hottest notes; it is never empty.

### Mock media, health, reset

| Method | Path | Auth | Params | Returns |
|---|---|---|---|---|
| GET | `/img` | — | `w,h,seed,text` | `image/png` (default 800×600, capped at 1200×1200) |
| GET | `/avatar` | — | `seed,size` | square `image/png` (default 200×200, max 512) |
| GET | `/files/{id}` | — | — | the uploaded / generated bytes |
| GET | `/video/{name}` | — | `Range` supported | `assets/{name}` (default `sample.mp4`) |
| GET | `/api/health` | — | — | `{ok:true,uptime}` |
| POST | `/api/_reset` | — | — | re-seeds the DB, returns the new counts (sessions **and** uploaded files are preserved, so the app keeps working) |

Unknown routes → `HTTP 404` with `{"code":404,"message":"接口不存在: …","data":null,…}`.

---

## Error codes

Returned as `{"code":<n>,"message":"…","data":null,…}`. HTTP is `401` for `1005/1006/1007`,
`400` otherwise.

| code | meaning | code | meaning |
|---|---|---|---|
| 1001 | 用户不存在 | 2001 | 笔记不存在 |
| 1002 | 密码错误 | 2002 | 笔记已删除 |
| 1003 | 用户已存在 | 2003 | 无权操作此笔记/评论 |
| 1004 | 用户被禁用 | 2004 | 图片数量不能超过 9 张 |
| **1005** | 用户未登录 (HTTP 401) | 2005 | 必须包含至少一张图片或一个视频 |
| **1006** | Token 已过期 (HTTP 401) | 3001 / 3002 | 评论不存在 / 已删除 |
| **1007** | Token 无效 (HTTP 401) | 4001 | 文件上传失败 / 文件过大 |
| 500 操作失败 | | 5001 / 5002 | 参数错误 / 参数缺失 |
| 6003 | 不能关注自己 | | |

---

## Developer conveniences

### Fault injection — `X-Mock-Fail`

Checked before routing/auth, so it works on any endpoint regardless of the token:

| header | effect |
|---|---|
| `X-Mock-Fail: 1` | `HTTP 500` `{"code":500,"message":"服务异常"}` |
| `X-Mock-Fail: 401` | `HTTP 401` `{"code":1005,"message":"用户未登录"}` |
| `X-Mock-Fail: timeout` | sleeps **20s** then replies 200 — exercises the client's 15s timeout path |

```bash
curl -s -H 'X-Mock-Fail: 1' http://localhost:8787/api/post/list
curl -s -H 'X-Mock-Fail: timeout' http://localhost:8787/api/post/list   # hangs 20s
```

### Token states

```bash
curl -s -H 'Authorization: Bearer expired-token' http://localhost:8787/api/user/me   # 401 / 1006
curl -s -H 'Authorization: Bearer invalid-token' http://localhost:8787/api/user/me   # 401 / 1007
curl -s -H 'Authorization: Bearer whatever'      http://localhost:8787/api/user/me   # 401 / 1005
```

### Latency simulation

Default **random 80–250 ms** on every `/api/*` request. Static media (`/img`, `/avatar`, `/files`,
`/video`) is intentionally **not** delayed — twenty waterfall cards × 200 ms would make the demo crawl.

```bash
curl -s -o /dev/null -w '%{time_total}s\n' -H 'X-Mock-Delay: 3000' http://localhost:8787/api/health
X-Mock-Delay: 0     # no delay
```

### CORS

All origins/methods/headers allowed, and `OPTIONS` preflight returns `204` — so the API is also
usable from a browser or a JS scratchpad.

### Request log

Every request is logged as `METHOD /path?query -> status (Nms)`.

---

## Seed data

Deterministic (ids, counts and relationships are stable across restarts; timestamps are anchored to
"now" so the feed always looks fresh).

* **10 users** with distinct Chinese nicknames, generated avatars, bios, genders, regions,
  occupations, schools, birthdays, `redId`, and varying counts.
* **50 posts**, ids 1–6 owned by `admin`. Mixed `type` (40 image / 10 video, i.e. 1 in 5 is a video
  note) and 1–9 images each. **Mixed aspect ratios** — `800x600` (4:3) and `600x800` (3:4)
  alternating by index, so the waterfall has visibly different card heights.
  Titles vary from one-liners to ones that truncate at 2 lines. `likeCount` deliberately includes
  `0` on 5 notes (ids 38 and 47–50, reserved so no like/collect record points at them — see
  `assertCounterConsistency`) so the client must render the text 「赞」, plus
  `12000`/`15800`/`23000` (client must render
  「1.2万」/「1.6万」/「2.3万」). `viewCount` varies independently, so `sortType=hot` really does
  differ from `latest`. `createTime` spreads over the last ~26 days.
* **154 comments** (68 first-level + 86 replies). The most-commented note is **post 1** (48 comments):
  one first-level comment has **27 replies** (→ `pageSize=10` batches of 10/10/7), another 12,
  another 3, others 0. Also comments with 0/3/12 replies elsewhere, varied timestamps, and one
  comment authored by `admin` on his own post so the 「作者」badge is testable.
  **`commentCount` on every post equals first-level comments + all replies** (contract change #13).
* **Notifications**: 20 for `admin` — unread items covering **all 6 types** (so all three G-entry
  badges are non-zero and the bottom-Tab badge is non-zero), plus read items. 7 for `user1` with
  `followUnread = 0` while `likeUnread`/`commentUnread` are non-zero (badge-hiding test).
* **Social graph**: `admin` follows users 2, 6, 7 (3 following / 8 followers), has **12 liked and 8
  collected notes** (`likedPostCount` / `collectedPostCount`) → the 「赞过」/「收藏」 tabs and the F1
  widget cards are non-empty. `testuser` has 0 of everything.
* **Received counters** (`likeCount`/`collectCount`/`likeAndCollectCount`) are derived by summing the
  counters of each user's own notes, so they grow when other people like or collect them. `admin`:
  `likeCount` 28247, `collectCount` 8572, `likeAndCollectCount` 36819.
* Links (`coverImage`, `images[].imageUrl`, `avatar`, `backgroundImage`, `videoUrl`) are absolute
  URLs pointing back at this server via `PUBLIC_BASE`.

---

## Mock images

There is no image library involved. `png.mjs` is a hand-written PNG encoder:

* CRC32 implemented from the standard table (~15 lines).
* Signature + `IHDR` (8-bit, truecolour **RGB**, colour type 2) + one `IDAT`
  (`zlib.deflateSync` over scanlines each prefixed with filter byte `0`) + `IEND`.
  Byte lengths are exactly as PNG requires, and the output decodes in Pillow/`file(1)`.
* Artwork is **deterministic from `seed`**: the seed is hashed (FNV-1a) → hue pair, saturation,
  lightness and direction; the image is a diagonal/vertical two-colour gradient with smoothstep
  interpolation, a subtle checkerboard, and a centred rounded-square motif with diagonal stripes
  (avatars get a circle + ring instead). `/img?...&text=…` and the 2:3 `text-image` variant also draw
  faux text lines.
* Requests larger than 1200×1200 pixels are scaled down before encoding, and generated bytes are
  cached in memory (LRU, 160 entries) because the feed reuses the same URLs constantly.
  A 1200×1200 image encodes in ~60 ms.

## Mock video

`assets/sample.mp4` is a committed **2 s, 320×568, H.264 + AAC, ~21 KB** clip with `+faststart`
(`moov` before `mdat`), generated once with `ffmpeg`. All 10 video notes point at
`GET /video/sample.mp4`.

The route supports **HTTP Range** (needed by Android's `MediaPlayer` for streaming):
`200` with `Accept-Ranges: bytes` for a full GET, `206` + correct `Content-Range` for
`bytes=0-100`, `bytes=100-`, and suffix ranges like `bytes=-64`, and `416` for unsatisfiable ranges.

If `assets/sample.mp4` is ever missing, the server logs a warning and tries to regenerate it with
`ffmpeg` (a Node built-in `node:child_process` call, still dependency-free); if ffmpeg is absent the
route returns a business error until the file is restored. Nothing is ever downloaded from the
internet.

---

## Known limitations & judgement calls

Read this before filing a bug against the mock.

1. **`text-image` does not rasterise glyphs.** Node core ships no font and no canvas, so the text
   only *seeds* the artwork deterministically (gradient + motif + faux text bars). The image is
   stable per input text and correctly sized 800×1200, but you will not read the characters in it.
2. **`UserVO` carries two deliberately opposite counter pairs** (contract amendment #16, §0
   "语义说明"). Do not mix them up:
   * **received** (获赞数 / 获藏数, legacy backend semantics) — `likeCount` = Σ likes on the notes
     *this user authored*, `collectCount` = Σ collects on them, `likeAndCollectCount` = their sum.
     The client does not display these; they exist to keep the shape a superset.
   * **own activity** — `likedPostCount` = how many notes this user *liked*,
     `collectedPostCount` = how many they *collected*. These are the F1 widget-card subtitles.
   They are seeded to obviously different magnitudes so a mix-up is visible at a glance: `admin`
   has `likeCount` 28247 / `collectCount` 8572 (received) versus `likedPostCount` 12 /
   `collectedPostCount` 8 (own).
   **Invariant (asserted in verification):** for every user,
   `likedPostCount === GET /api/like/posts/{id}.total` and
   `collectedPostCount === GET /api/collect/posts/{id}.total`, after any sequence of
   like/unlike/collect/uncollect note deletions — the four numbers are derived from the *same*
   records those endpoints page over, so they cannot drift.
3. **Unknown/invalid tokens return `1005`, not `1007`.** The task statement requires
   "missing/invalid → HTTP 401 `code 1005`", while contract §9 also lists `1007` for an invalid
   token. I follow the task statement for ordinary invalid tokens and expose
   `Authorization: Bearer invalid-token` (and `expired-token` → `1006`) as explicit ways to reach the
   other two codes.
4. **`keyword` matches title *or* content**, with title matches ordered first. The contract says
   "标题 LIKE"; content matching is a superset that only ever adds results, and it makes sure the
   `hot-keywords` chips never return an empty search. A keyword matching nothing still returns an
   empty page (so the B3-2 empty state is reachable).
5. **`sortType=hot` is `viewCount + likeCount*3 + collectCount*2 + commentCount*5`** (desc, tie-break
   newest). The contract does not define a hot formula.
6. **Notifications for `type=6` (关注) carry `postId: 0`, `postTitle: ""`, `postCoverImage: ""`** —
   a follow has no note attached. Every other type always carries a real note reference.
7. **Comment delete permission errors reuse `2003`** ("无权操作"), because the error-code table has
   no comment-specific "no permission" code. `3001/3002` are used for missing/deleted comments.
8. **Video notes always have a poster image**, because the mock cannot extract a first frame. A
   video-only note gets a generated `/img` poster as its `coverImage`; `type` is still derived from
   `videoUrl` alone.
9. **Latency is skipped for static media routes** (see above). `X-Mock-Delay` still applies anywhere.
10. **`GET /api/post/text-image/generate` rejects text longer than 20 characters with `5001`**
    (contract §2.10). `POST /api/post/text-image` accepts up to 100 characters and truncates, then
    stores the PNG so the returned URL is immediately fetchable.
11. Uploads are capped at 200 MB (the contract's video limit). The 1 MB body-headroom over that cap
    returns `4001 文件过大` rather than buffering unbounded input.
12. **`_reset` keeps sessions and uploaded files.** Tokens stay valid across a reset so the running
    app does not get logged out mid-demo; the DB itself (users/posts/comments/likes/follows/
    notifications) is rebuilt from scratch. A full process restart does invalidate tokens
    (sessions are in memory) and preserves uploaded files (mirrored to `uploads/`).

## Files

```
client/mockserver/
├── server.mjs      # HTTP server: routing, auth, multipart, range requests, fault injection
├── seed.mjs        # deterministic in-memory DB (users, 50 posts, comments, notifications)
├── png.mjs         # CRC32 + PNG encoder + deterministic placeholder artwork
├── package.json    # name/type/scripts only — zero dependencies
├── README.md
├── .gitignore      # ignores uploads/ and runtime artefacts
├── assets/
│   └── sample.mp4  # committed 2s H.264/AAC clip used by every video note
└── uploads/        # runtime mirror of uploaded files (created on demand)
```
