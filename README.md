# Xiaohongshu Clone

这是一个用于学习和课程实践的小红书全栈复刻项目。

## 项目结构

- `backend/`：Spring Boot 后端服务。
- `frontend/`：Vue/Vite 前端服务。
- `crawler/`：公开页面测试数据采集、资源归档和种子数据生成。
- `docs/`：项目文档。
- `docker-compose.yml`：本地 Docker 编排。

## 爬虫与测试数据

爬虫相关文件已经集中在 `crawler/`，避免和业务代码混放：

- `crawler/scripts/crawl_xhs_public_data.js`：通过本机 Chrome 抓取未登录公开可见的帖子、评论、用户和媒体。
- `crawler/scripts/generate_xhs_seed.py`：把抓取结果转换为项目数据库可导入的 JSON 和 SQL。
- `crawler/scripts/reseed-xhs-test-data.ps1`：一键重置 Docker 数据卷、重启服务并导入测试数据。
- `crawler/output/`：抓取结果、原始响应和生成的种子 SQL/JSON，本地生成，不提交到 Git。
- `crawler/media/xhs-media/`：下载归档的图片、头像、视频等静态资源，本地生成，不提交到 Git。

完整重爬、重建容器并导入测试数据：

```powershell
powershell -ExecutionPolicy Bypass -File .\crawler\scripts\reseed-xhs-test-data.ps1
```

只复用已有抓取结果并重新导入当前数据库：

```powershell
powershell -ExecutionPolicy Bypass -File .\crawler\scripts\reseed-xhs-test-data.ps1 -SkipCrawl -SkipRebuild
```

重置脚本会把 `crawler/media/xhs-media` 复制到前端容器的 `/usr/share/nginx/html/xhs-media`，数据库中的 `/xhs-media/...` 路径可以直接通过前端服务访问。这里使用 `docker cp`，不是 bind mount；复制完成后，宿主机上的 `crawler/output/` 和 `crawler/media/` 可以清理或重新生成。若重建了前端容器，需要重新执行重置脚本复制媒体文件。

## 本地启动

```powershell
docker compose up -d --build
```

常用入口：

- 前端：http://localhost
- 后端 API：http://localhost:8080
- MinIO Console：http://localhost:9001
