# Xiaohongshu Clone

一个用于学习和课程实践的小红书全栈复刻项目，涵盖用户认证、笔记发布、评论互动、点赞收藏、关注系统、消息通知、文件上传与 AI 聊天助手等核心功能。前后端分离，全 Docker 容器化部署。

## 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 3.3.7 |
| 语言 | Java | 17 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7 (Alpine) |
| 对象存储 | MinIO | latest |
| 认证 | Spring Security + JWT (HMAC-SHA) | jjwt 0.12.6 |
| API 文档 | Knife4j (OpenAPI 3) | 4.5.0 |
| 工具库 | Hutool | 5.8.40 |
| 前端框架 | Vue 3 (Composition API) | 3.5.x |
| 构建工具 | Vite | 8.x |
| 路由 | Vue Router | 5.x |
| 状态管理 | Pinia | 3.x |
| HTTP 客户端 | Axios | 1.x |
| CSS | Tailwind CSS | 4.x |

## 功能特性

- **用户认证** — 注册/登录、JWT 鉴权、个人信息编辑
- **笔记发布** — 图文/视频笔记，最多 9 张图片 + 1 个视频，支持编辑与删除
- **图片上传** — 基于 MinIO 的对象存储，按日期分目录
- **评论系统** — 两级嵌套评论（父评论 + 回复），支持 `@` 回复标注
- **点赞收藏** — 帖子/评论的点赞与帖子收藏，toggle 模式
- **关注系统** — 关注/取关，互相关注状态，关注数/粉丝数统计
- **消息通知** — 6 类通知（点赞帖子、收藏帖子、评论帖子、回复评论、点赞评论、新关注），未读数、已读标记
- **AI 聊天助手** — 接入 DeepSeek（兼容 OpenAI API），单轮对话
- **瀑布流首页** — 响应式瀑布流帖子展示，支持关键词搜索与最新/最热排序
- **个人主页** — 用户信息、帖子/收藏/点赞列表、关注/取消关注
- **模态详情** — 帖子详情弹窗 + 独立详情页双模式路由

## 项目结构

```
├── backend/                  # Spring Boot 后端
│   ├── src/main/java/com/xiaohongshu/
│   │   ├── ai/               # AI 聊天模块 (DeepSeek LLM 集成)
│   │   ├── common/           # 通用异常处理、统一响应
│   │   ├── config/           # Knife4j / MinIO / MyBatis-Plus / Security 配置
│   │   ├── file/             # 文件上传 (MinIO)
│   │   ├── interact/         # 评论、点赞、收藏
│   │   ├── notification/     # 消息通知
│   │   ├── post/             # 笔记管理
│   │   ├── security/         # JWT 过滤器与工具
│   │   ├── social/           # 关注系统
│   │   └── user/             # 用户管理
│   └── src/main/resources/
│       ├── application.yml          # 主配置（本地开发）
│       └── application-docker.yml   # Docker 环境覆盖配置
├── sql/                      # 数据库
│   ├── schema-ddl.sql        # 建表脚本
│   └── schema-dml.sql        # 测试数据（已废弃，换用爬虫生成测试数据）
├── frontend/                 # Vue + Vite 前端
│   └── src/
│       ├── api/              # Axios API 封装 (auth, post, comment, like, collect, follow, notification, upload, ai)
│       ├── auth/             # Token 管理与登录过期事件
│       ├── components/       # 组件 (chat, common, layout, message, post, user)
│       ├── composables/      # 可组合函数 (分页、关注、点赞、评论、详情路由等)
│       ├── router/           # 路由定义与导航守卫
│       ├── stores/           # Pinia 状态管理 (user, post, ui, notification)
│       └── utils/            # 工具函数 (格式化、媒体、Toast)
├── crawler/                  # 爬虫与测试数据
│   ├── scripts/
│   │   ├── crawl_xhs_public_data.js   # Node.js 爬虫 (通过 CDP 控制 Chrome)
│   │   ├── generate_xhs_seed.py       # 爬取结果转为种子 SQL / JSON
│   │   └── reseed-xhs-test-data.ps1   # 一键重置脚本
│   ├── output/               # 抓取结果、种子数据 (本地生成，不提交 Git)
│   └── media/xhs-media/      # 下载的图片/视频等静态资源 (本地生成，不提交 Git)
├── docker-compose.yml         # Docker 编排 (5 服务 + 3 数据卷)
└── .env.example               # 环境变量模板
```

## 快速开始

### 前置要求

- [Docker](https://www.docker.com/) 与 Docker Compose
- (可选) [Node.js](https://nodejs.org/) 18+ — 运行爬虫
- (可选) [Python](https://www.python.org/) 3.9+ — 生成种子数据

### 1. 配置环境变量

```powershell
copy .env.example .env
```

编辑 `.env`，至少修改以下配置：

- `MYSQL_PASSWORD` — MySQL root 密码
- `JWT_SECRET` — JWT 签名密钥（至少 32 字节的随机字符串）
- `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` — MinIO 凭证
- `LLM_API_KEY` — DeepSeek API Key（可选，不配则 AI 聊天不可用）

### 2. 启动服务

提供两种启动方式，根据需要选择。

#### 方式一：空表启动（仅建表，无数据）

```powershell
docker compose up -d --build
```

启动后数据库仅有表结构，适合从零开始手动注册用户、发布笔记。

#### 方式二：爬取真实数据启动

```powershell
powershell -ExecutionPolicy Bypass -File .\crawler\scripts\reseed-xhs-test-data.ps1
```

自动完成：爬取小红书公开内容 → 生成种子 SQL → 重建容器 → 导入数据 → 复制媒体文件。启动后即可用爬取到的账号和数据体验完整功能。

> **提示**：爬虫通过本机 Chrome 抓取公开页面，需提前安装 [Node.js](https://nodejs.org/) 18+ 和 [Python](https://www.python.org/) 3.9+。若已有爬取结果，可跳过重复步骤：
> ```powershell
> # 跳过爬取（使用已有抓取结果）
> powershell ... -SkipCrawl
> 
> # 跳过爬取 + 跳过重建（仅重新导入到已运行的容器）
> powershell ... -SkipCrawl -SkipRebuild
> ```

### 3. 访问入口

| 服务 | 地址 |
|---|---|
| 前端页面 | http://localhost |
| 后端 API | http://localhost:8080/api |
| Knife4j API 文档 | http://localhost:8080/api/doc.html |
| MinIO Console | http://localhost:9001 |

## 爬虫与测试数据参考

<details>
<summary>爬虫模块详情</summary>

### 核心脚本

| 脚本 | 说明 |
|---|---|
| `crawl_xhs_public_data.js` | 通过本机 Chrome DevTools Protocol 抓取未登录可见的公开内容 |
| `generate_xhs_seed.py` | 将抓取结果转换为 SQL 与 JSON 种子数据 |
| `reseed-xhs-test-data.ps1` | 一键编排：爬取 → 生成 → 重建容器 → 导入数据 |

### 媒体文件说明

重置脚本使用 `docker cp` 将 `crawler/media/xhs-media/` 复制到前端容器的 `/usr/share/nginx/html/xhs-media/`，数据库中 `/xhs-media/...` 路径可直接通过前端访问。若重建了前端容器，需重新执行重置脚本以复制媒体文件。

</details>

## API 概览

> 完整文档见 [docs/API接口文档.md](docs/API接口文档.md)

| 模块 | 前缀 | 说明 |
|---|---|---|
| 用户 | `/user` | 注册、登录、个人信息、更新 |
| 笔记 | `/post` | CRUD、列表、搜索、排序、文字转图片 |
| 评论 | `/comment` | 创建、删除、按帖子查询、回复列表 |
| 点赞 | `/like` | 帖子/评论点赞 toggle、状态查询、点赞列表 |
| 收藏 | `/collect` | 帖子收藏 toggle、状态查询、收藏列表 |
| 关注 | `/follow` | 关注 toggle、关注/粉丝列表、关注数 |
| 上传 | `/upload` | 图片、视频、通用文件上传至 MinIO |
| 通知 | `/notification` | 未读数、通知列表、已读标记 |
| AI | `/ai` | OpenAI 兼容聊天 |

## 本地开发

如需在宿主机直接运行（不使用 Docker），请确保：

1. 本地安装 JDK 17、Maven、Node.js 18+、MySQL 8.0、Redis 7
2. 修改 `.env` 中的连接地址为 `localhost`
3. 启动 MySQL 与 Redis，手动执行 `backend/src/main/resources/sql/schema-ddl.sql`
4. 后端：`cd backend && mvn spring-boot:run`
5. 前端：`cd frontend && npm install && npm run dev`（开发服务器默认 `http://localhost:5173`）

## License

本项目仅用于学习与课程实践。
