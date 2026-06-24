# 小红书(Clone)后端 API 接口文档

> 基础地址: `http://localhost:8080`
> 认证方式: JWT Bearer Token（放在请求头 `Authorization: Bearer <token>`）
> 响应格式: 统一 JSON `{ "code": 200, "message": "操作成功", "data": ..., "timestamp": ... }`

---

## 目录

1. [用户模块](#1-用户模块)
2. [笔记模块](#2-笔记模块)
3. [评论模块](#3-评论模块)
4. [点赞模块](#4-点赞模块)
5. [收藏模块](#5-收藏模块)
6. [关注模块](#6-关注模块)
7. [文件上传模块](#7-文件上传模块)
8. [错误码对照表](#8-错误码对照表)

---

## 1. 用户模块

> **社交统计字段说明**: 用户信息中的 `followingCount`（关注数）、`followersCount`（粉丝数）、`likeCount`（获赞数）、`collectCount`（获藏数）存储在 `sys_user` 表中，在关注/取消关注、点赞/取消点赞、收藏/取消收藏操作时自动维护，无需实时计算。

### 1.1 用户注册

- **URL**: `POST /api/user/register`
- **权限**: 公开
- **请求体**:

```json
{
  "username": "testuser",      // 必填，3-20字符
  "password": "test123456",    // 必填，6-20字符
  "nickname": "测试用户",       // 可选，≤20字符
  "phone": "13900001111"       // 可选，中国大陆手机号格式
}
```

- **成功响应** (200):

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 4,
    "username": "testuser",
    "nickname": "测试用户",
    "avatar": "",
    "gender": 0,
    "phone": "13900001111",
    "email": "",
    "bio": "",
    "followingCount": 0,
    "followersCount": 0,
    "likeCount": 0,
    "collectCount": 0,
    "likeAndCollectCount": 0,
    "createTime": "2026-06-23T16:00:00"
  }
}
```

- **失败场景**:
  - 用户名已存在 → `code: 1003`
  - 用户名/密码不合规 → `code: 5001`

---

### 1.2 用户登录

- **URL**: `POST /api/user/login`
- **权限**: 公开
- **请求体**:

```json
{
  "username": "admin",
  "password": "123456"
}
```

- **成功响应** (200):

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "expiresIn": 604800,
    "user": {
      "id": 1,
      "username": "admin",
      "nickname": "管理员",
      "avatar": "https://...",
      "gender": 1,
      "phone": "13800138000",
      "email": "",
      "bio": "系统管理员",
      "followingCount": 5,
      "followersCount": 10,
      "likeCount": 100,
      "collectCount": 50,
      "likeAndCollectCount": 150,
      "createTime": "2026-06-23T09:22:47"
    }
  }
}
```

- **失败场景**:
  - 用户不存在 → `code: 1001`
  - 密码错误 → `code: 1002`
  - 用户被禁用 → `code: 1004`

---

### 1.3 获取当前用户信息

- **URL**: `GET /api/user/me`
- **权限**: 🔒 需登录
- **响应**: 同 1.2 中的 `user` 对象

---

### 1.4 根据 ID 获取用户信息

- **URL**: `GET /api/user/{id}`
- **权限**: 公开
- **路径参数**: `id` - 用户ID
- **响应**: 同 1.2 中的 `user` 对象

---

### 1.5 更新用户信息

- **URL**: `PUT /api/user/update`
- **权限**: 🔒 需登录
- **请求体** (所有字段可选):

```json
{
  "nickname": "新昵称",        // ≤20字符
  "avatar": "https://...",     // 头像URL
  "gender": 1,                 // 0-未知，1-男，2-女
  "email": "test@example.com",
  "bio": "新的个人简介"         // ≤200字符
}
```

- **响应**: 更新后的用户信息

---

## 2. 笔记模块

### 约束规则

| 规则 | 说明                           |
|------|------------------------------|
| 图片 | 可选，最多 9 张                    |
| 视频 | 可选，最多 1 个                    |
| 至少一项 | 图片和视频不能同时缺省                  |
| type 字段 | 由后端自动推导，前端无需传入：有视频→1，仅图片→0   |
| coverImage | 由后端自动设置，取第一张图片；视频笔记无图片时取视频首帧 |

### 2.1 创建笔记

- **URL**: `POST /api/post/create`
- **权限**: 🔒 需登录
- **请求体**:

图文笔记示例：

```json
{
  "title": "笔记标题",                          // 必填，≤200字符
  "content": "笔记正文内容",                     // 可选，≤10000字符
  "imageUrls": ["https://img1.jpg", "https://img2.jpg"]  // 可选，最多9张
}
```

视频笔记示例（可附带图片）：

```json
{
  "title": "视频笔记标题",                       // 必填，≤200字符
  "content": "笔记正文内容",                     // 可选，≤10000字符
  "videoUrl": "https://example.com/video.mp4",   // 可选，最多1个
  "imageUrls": ["https://img1.jpg"]              // 可选，最多9张
}
```

- **成功响应** (200): 返回完整的笔记详情（含 `id`）
- **失败场景**: 图片和视频同时缺省 → `code: 2005`

---

### 2.2 更新笔记

- **URL**: `PUT /api/post/update`
- **权限**: 🔒 需登录（仅作者）
- **请求体**:

```json
{
  "id": 1,                                       // 必填，笔记ID
  "title": "更新后的标题",                        // 可选
  "content": "更新后的内容",                      // 可选
  "videoUrl": "https://example.com/new.mp4",     // 可选
  "imageUrls": ["https://new1.jpg", "https://new2.jpg"]  // 可选，替换所有图片
}
```

- **失败场景**:
  - 非作者操作 → `code: 2003`
  - 更新后图片和视频同时缺省 → `code: 2005`

---

### 2.3 删除笔记

- **URL**: `DELETE /api/post/delete/{postId}`
- **权限**: 🔒 需登录（仅作者）
- **路径参数**: `postId` - 笔记ID

---

### 2.4 获取笔记详情

- **URL**: `GET /api/post/{postId}`
- **权限**: 公开
- **说明**: 每次访问自动 +1 浏览量
- **响应**:

```json
{
  "code": 200,
  "data": {
    "id": 1,
    "userId": 1,
    "authorNickname": "管理员",
    "authorAvatar": "https://...",
    "title": "笔记标题",
    "content": "笔记内容",
    "type": 0,
    "coverImage": "https://...",
    "videoUrl": "",
    "images": [
      { "id": 1, "imageUrl": "https://...", "sortOrder": 1, "width": 400, "height": 300 }
    ],
    "viewCount": 101,
    "likeCount": 50,
    "commentCount": 10,
    "collectCount": 20,
    "status": 1,
    "createTime": "2026-06-23T10:00:00",
    "updateTime": "2026-06-23T10:00:00"
  }
}
```

---

### 2.5 笔记列表（分页）

- **URL**: `GET /api/post/list`
- **权限**: 公开
- **查询参数**:

| 参数       | 类型     | 默认值    | 说明            |
| -------- | ------ | ------ | ------------- |
| pageNum  | int    | 1      | 页码（≥1）        |
| pageSize | int    | 10     | 每页数量（1-100）   |
| keyword  | string | -      | 标题关键词搜索       |
| type     | int    | -      | 类型筛选          |
| sortType | string | latest | 排序：latest/hot |

---

### 2.6 获取用户的笔记列表

- **URL**: `GET /api/post/user/{userId}`
- **权限**: 公开
- **查询参数**: 同 2.5

---

### 2.7 获取我的笔记列表

- **URL**: `GET /api/post/my`
- **权限**: 🔒 需登录
- **查询参数**: 同 2.5

---

## 3. 评论模块

### 3.1 发表评论

- **URL**: `POST /api/comment/create`
- **权限**: 🔒 需登录
- **请求体**:

```json
{
  "postId": 1,              // 必填，笔记ID
  "content": "评论内容",     // 必填，≤500字符
  "parentId": 0,            // 可选，父评论ID（0=一级评论，默认0）
  "replyUserId": 0          // 可选，回复的用户ID（一级评论时为0）
}
```

- **响应**: 评论详情（含 `id`, `userNickname`, `userAvatar`, `replyCount` 等）

---

### 3.2 删除评论

- **URL**: `DELETE /api/comment/delete/{commentId}`
- **权限**: 🔒 需登录（仅评论作者）

---

### 3.3 获取笔记评论列表（一级评论）

- **URL**: `GET /api/comment/post/{postId}`
- **权限**: 公开
- **查询参数**: `pageNum`, `pageSize`（分页）
- **响应**: 分页评论列表，每条评论包含 `replyCount` 字段

---

### 3.4 获取评论回复列表

- **URL**: `GET /api/comment/replies/{commentId}`
- **权限**: 公开
- **查询参数**: `pageNum`, `pageSize`（分页）
- **响应**: 该评论下的回复列表

---

## 4. 点赞模块

> 点赞采用 **toggle 模式**：重复请求自动取消

### 4.1 点赞/取消点赞笔记

- **URL**: `POST /api/like/post/{postId}`
- **权限**: 🔒 需登录
- **响应**:

```json
{
  "code": 200,
  "data": {
    "liked": true,          // true=点赞成功, false=取消点赞
    "message": "点赞成功"
  }
}
```

---

### 4.2 点赞/取消点赞评论

- **URL**: `POST /api/like/comment/{commentId}`
- **权限**: 🔒 需登录
- **响应**: 同 4.1

---

### 4.3 获取笔记点赞状态

- **URL**: `GET /api/like/status/post/{postId}`
- **权限**: 🔒 需登录
- **响应**: `{ "liked": true/false }`

---

### 4.4 获取评论点赞状态

- **URL**: `GET /api/like/status/comment/{commentId}`
- **权限**: 🔒 需登录
- **响应**: `{ "liked": true/false }`

---

## 5. 收藏模块

> 收藏采用 **toggle 模式**

### 5.1 收藏/取消收藏笔记

- **URL**: `POST /api/collect/post/{postId}`
- **权限**: 🔒 需登录
- **响应**:

```json
{
  "code": 200,
  "data": {
    "collected": true,       // true=收藏成功, false=取消收藏
    "message": "收藏成功"
  }
}
```

---

### 5.2 获取笔记收藏状态

- **URL**: `GET /api/collect/status/post/{postId}`
- **权限**: 🔒 需登录
- **响应**: `{ "collected": true/false }`

---

### 5.3 获取用户收藏笔记列表

- **URL**: `GET /api/collect/posts/{userId}`
- **权限**: 🔒 需登录（可查看他人收藏）
- **路径参数**: `userId` - 目标用户ID
- **查询参数**:

| 参数       | 类型  | 默认值 | 说明              |
|----------|-----|-----|-----------------|
| pageNum  | int | 1   | 页码（≥1）          |
| pageSize | int | 10  | 每页数量（1-100）     |

- **响应**:

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 1,
        "authorNickname": "管理员",
        "authorAvatar": "https://...",
        "title": "笔记标题",
        "content": "笔记内容",
        "type": 0,
        "coverImage": "https://...",
        "videoUrl": "",
        "images": [...],
        "viewCount": 101,
        "likeCount": 50,
        "commentCount": 10,
        "collectCount": 20,
        "status": 1,
        "createTime": "2026-06-23T10:00:00",
        "updateTime": "2026-06-23T10:00:00"
      }
    ],
    "total": 50,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 5
  }
}
```

- **说明**: 按收藏时间倒序排列

---

## 6. 关注模块

> 关注采用 **toggle 模式**

### 6.1 关注/取消关注用户

- **URL**: `POST /api/follow/{userId}`
- **权限**: 🔒 需登录
- **说明**: 不能关注自己
- **响应**:

```json
{
  "code": 200,
  "data": {
    "followed": true,        // true=关注成功, false=取消关注
    "message": "关注成功"
  }
}
```

- **失败场景**: 关注自己 → `code: 6003`

---

### 6.2 获取关注状态

- **URL**: `GET /api/follow/status/{userId}`
- **权限**: 🔒 需登录
- **响应**: `{ "followed": true/false }`

---

### 6.3 获取关注列表

- **URL**: `GET /api/follow/following/{userId}`
- **权限**: 公开
- **请求头**: `Authorization`（可选，Bearer token，携带时会填充 `followed` 字段）
- **查询参数**: `pageNum`, `pageSize`
- **响应**:

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 2,
        "nickname": "用户A",
        "avatar": "https://...",
        "bio": "个人简介",
        "followTime": "2026-06-23T10:00:00",
        "followed": true
      }
    ],
    "total": 50,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 5
  }
}
```

- **说明**: `followed` 字段仅在携带有效 token 时有值，表示当前登录用户是否关注了该用户

---

### 6.4 获取粉丝列表

- **URL**: `GET /api/follow/followers/{userId}`
- **权限**: 公开
- **请求头**: `Authorization`（可选，Bearer token，携带时会填充 `followed` 字段）
- **查询参数**: `pageNum`, `pageSize`
- **响应**: 同 6.3

---

### 6.5 获取关注/粉丝数量

- **URL**: `GET /api/follow/count/{userId}`
- **权限**: 公开
- **响应**:

```json
{
  "code": 200,
  "data": {
    "followingCount": 2,
    "followersCount": 3
  }
}
```

---

## 7. 文件上传模块

### 7.1 上传图片

- **URL**: `POST /api/upload/image`
- **权限**: 🔒 霘登录
- **Content-Type**: `multipart/form-data`
- **参数**: `file` - 图片文件（≤10MB）
- **响应**: `{ "url": "http://minio:9000/xiaohongshu/images/2026/06/23/uuid.jpg" }`

### 7.2 上传视频

- **URL**: `POST /api/upload/video`
- **说明**: 同 7.1，文件存储在 `videos/` 前缀下

### 7.3 通用文件上传

- **URL**: `POST /api/upload/file`
- **说明**: 同 7.1，文件存储在 `files/` 前缀下

---

## 8. 错误码对照表

| 错误码  | 含义       | HTTP状态码 |
|------| -------- | ------- |
| 200  | 操作成功     | 200     |
| 500  | 操作失败     | 400     |
| 1001 | 用户不存在    | 400     |
| 1002 | 密码错误     | 400     |
| 1003 | 用户已存在    | 400     |
| 1004 | 用户已被禁用   | 400     |
| 1005 | 用户未登录    | 401     |
| 1006 | Token已过期 | 401     |
| 1007 | Token无效  | 401     |
| 2001 | 笔记不存在    | 400     |
| 2002 | 笔记已删除    | 400     |
| 2003 | 无权操作此笔记  | 400     |
| 2004 | 图片数量不能超过9张 | 400     |
| 2005 | 笔记必须包含至少一张图片或一个视频 | 400     |
| 3001 | 评论不存在    | 400     |
| 3002 | 评论已删除    | 400     |
| 4001 | 文件上传失败   | 400     |
| 5001 | 参数错误     | 400     |
| 5002 | 参数缺失     | 400     |
| 6001 | 已关注该用户   | 400     |
| 6002 | 未关注该用户   | 400     |
| 6003 | 不能关注自己   | 400     |
| 7001 | 已操作过     | 400     |
| 7002 | 未操作过     | 400     |


