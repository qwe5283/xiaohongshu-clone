import json
from datetime import datetime, timedelta
from pathlib import Path


CRAWLER_ROOT = Path(__file__).resolve().parents[1]
CRAWL_PATH = CRAWLER_ROOT / "output" / "crawl" / "xhs_public_100.json"
SQL_PATH = CRAWLER_ROOT / "output" / "xhs_seed_100.sql"
JSON_PATH = CRAWLER_ROOT / "output" / "xhs_seed_100.json"
PASSWORD_HASH = "$2a$10$.c3kAZ1MFSmMQfe0RlA9rOnxWrIX7N/CTFirwmf4/OTAu.n5XTuum"


def sql_string(value):
    if value is None:
        return "NULL"
    text = str(value).replace("\\", "\\\\").replace("'", "''")
    return "'" + text + "'"


def dt_from_ms(value, fallback):
    if value:
        try:
            return datetime.fromtimestamp(int(value) / 1000).strftime("%Y-%m-%d %H:%M:%S")
        except Exception:
            pass
    return fallback


def values_sql(rows):
    return ",\n".join("(" + ", ".join(row) + ")" for row in rows) + ";"


def add_user(users_by_source, user):
    source_id = (user or {}).get("source_user_id") or ""
    if not source_id:
        source_id = f"unknown_{len(users_by_source) + 1}"
    if source_id in users_by_source:
        current = users_by_source[source_id]
        current["nickname"] = current["nickname"] or (user or {}).get("nickname") or current["username"]
        current["avatar"] = current["avatar"] or (user or {}).get("avatar") or (user or {}).get("avatar_remote") or ""
        return current
    idx = len(users_by_source) + 1
    row = {
        "id": 1000 + idx,
        "source_user_id": source_id,
        "username": f"xhs_{idx:04d}",
        "nickname": ((user or {}).get("nickname") or f"小红书用户{idx}")[:50],
        "avatar": ((user or {}).get("avatar") or (user or {}).get("avatar_remote") or "")[:255],
        "xsec_token": (user or {}).get("xsec_token") or "",
        "gender": idx % 3,
        "liked_count": 0,
        "collected_count": 0,
        "fans_count": 0,
        "following_count": 0,
    }
    users_by_source[source_id] = row
    return row


def normalize_dataset():
    if not CRAWL_PATH.exists():
        raise FileNotFoundError(f"Missing crawl file: {CRAWL_PATH}. Run crawler/scripts/crawl_xhs_public_data.js first.")
    crawl = json.loads(CRAWL_PATH.read_text(encoding="utf-8"))
    posts = crawl.get("posts") or []
    if len(posts) != 100:
        raise RuntimeError(f"Expected exactly 100 crawled posts, got {len(posts)}.")

    users_by_source = {}
    normalized_posts = []
    normalized_comments = []
    comment_id = 1001
    base_time = datetime(2026, 7, 3, 0, 0, 0)

    for idx, post in enumerate(posts, start=1):
        author = add_user(users_by_source, post.get("author") or {})
        post_id = 1000 + idx
        created_at = (base_time - timedelta(minutes=idx * 9)).strftime("%Y-%m-%d %H:%M:%S")
        visible_comment_count = 0
        for raw_comment in post.get("comments") or []:
            comment_user = add_user(users_by_source, raw_comment.get("user") or {})
            normalized_comments.append(
                {
                    "id": comment_id,
                    "source_comment_id": raw_comment.get("source_comment_id", ""),
                    "post_id": post_id,
                    "user_id": comment_user["id"],
                    "content": (raw_comment.get("content") or "[图片/语音评论]")[:500],
                    "parent_id": 0,
                    "reply_user_id": 0,
                    "like_count": int(raw_comment.get("like_count") or 0),
                    "created_at": dt_from_ms(raw_comment.get("create_time"), created_at),
                }
            )
            parent_id = comment_id
            comment_id += 1
            visible_comment_count += 1
            for raw_reply in raw_comment.get("sub_comments") or []:
                reply_user = add_user(users_by_source, raw_reply.get("user") or {})
                target_user = users_by_source.get(raw_reply.get("source_reply_user_id") or "")
                normalized_comments.append(
                    {
                        "id": comment_id,
                        "source_comment_id": raw_reply.get("source_comment_id", ""),
                        "post_id": post_id,
                        "user_id": reply_user["id"],
                        "content": (raw_reply.get("content") or "[图片/语音回复]")[:500],
                        "parent_id": parent_id,
                        "reply_user_id": target_user["id"] if target_user else comment_user["id"],
                        "like_count": int(raw_reply.get("like_count") or 0),
                        "created_at": dt_from_ms(raw_reply.get("create_time"), created_at),
                    }
                )
                comment_id += 1
                visible_comment_count += 1

        like_count = int(post.get("like_count") or 0)
        collect_count = int(post.get("collect_count") or max(like_count // 5, 0))
        author["liked_count"] += like_count
        author["collected_count"] += collect_count
        title = (post.get("title") or f"小红书公开笔记 {idx}")[:200]
        content = post.get("content") or f"来源：小红书 Explore 未登录公开页面。源笔记ID：{post.get('source_note_id')}"
        image_urls = post.get("image_urls") or ([post.get("cover_image")] if post.get("cover_image") else [])
        normalized_posts.append(
            {
                "id": post_id,
                "source_note_id": post.get("source_note_id"),
                "user_id": author["id"],
                "title": title,
                "content": content,
                "type": int(post.get("type") or (1 if post.get("video_url") else 0)),
                "cover_image": (post.get("cover_image") or (image_urls[0] if image_urls else ""))[:255],
                "video_url": (post.get("video_url") or "")[:255],
                "image_urls": [url[:255] for url in image_urls if url],
                "width": int(post.get("width") or 0),
                "height": int(post.get("height") or 0),
                "view_count": int(post.get("view_count") or max(like_count * 8, 500)),
                "like_count": like_count,
                "comment_count": visible_comment_count,
                "collect_count": collect_count,
                "created_at": created_at,
                "crawl_comment_ok": bool(post.get("crawl_comment_ok")),
                "crawl_detail_ok": bool(post.get("crawl_detail_ok")),
            }
        )

    users = list(users_by_source.values())
    user_count = len(users)
    for idx, user in enumerate(users):
        user["following_count"] = min(20, max(1, user_count // 12))
        user["fans_count"] = max(5, min(99999, user["liked_count"] // 50 + idx % 37))
        user["bio"] = f"小红书未登录公开页面归档用户。源用户ID：{user['source_user_id']}"[:500]

    follows = set()
    for idx, user in enumerate(users):
        for step in range(1, min(5, user_count)):
            target = users[(idx + step) % user_count]
            if user["id"] != target["id"]:
                follows.add((user["id"], target["id"]))

    actions = set()
    for idx, post in enumerate(normalized_posts):
        for step in range(1, min(7, user_count)):
            user = users[(idx + step) % user_count]
            if user["id"] == post["user_id"]:
                continue
            actions.add((user["id"], post["id"], 1, 1))
            if step <= 3:
                actions.add((user["id"], post["id"], 1, 2))
    for comment in normalized_comments[::2]:
        user = users[(comment["id"] + 13) % user_count]
        if user["id"] != comment["user_id"]:
            actions.add((user["id"], comment["id"], 2, 1))

    notifications = []
    for idx, comment in enumerate(normalized_comments[:120], start=1):
        post = next(p for p in normalized_posts if p["id"] == comment["post_id"])
        if post["user_id"] != comment["user_id"]:
            notifications.append(
                {
                    "id": 1000 + idx,
                    "receiver_id": post["user_id"],
                    "sender_id": comment["user_id"],
                    "type": 3 if comment["parent_id"] == 0 else 4,
                    "post_id": post["id"],
                    "comment_id": comment["id"],
                    "content": comment["content"][:500],
                    "created_at": comment["created_at"],
                }
            )

    return {
        "source": crawl.get("source"),
        "crawled_at": crawl.get("crawled_at"),
        "users": users,
        "posts": normalized_posts,
        "comments": normalized_comments,
        "follows": sorted(follows),
        "actions": sorted(actions),
        "notifications": notifications,
    }


def write_sql(data):
    lines = [
        "-- Generated from crawler/output/crawl/xhs_public_100.json.",
        "-- Does not depend on backend/sql/schema-dml.sql.",
        "-- Password for generated users: 123456",
        "SET NAMES utf8mb4;",
        "USE xiaohongshu;",
        "",
        "DELETE FROM sys_notification WHERE id BETWEEN 1001 AND 999999;",
        "DELETE FROM user_action WHERE user_id BETWEEN 1001 AND 999999 OR target_id BETWEEN 1001 AND 999999;",
        "DELETE FROM user_follow WHERE user_id BETWEEN 1001 AND 999999 OR follow_user_id BETWEEN 1001 AND 999999;",
        "DELETE FROM comment WHERE id BETWEEN 1001 AND 999999 OR post_id BETWEEN 1001 AND 999999;",
        "DELETE FROM post_image WHERE id BETWEEN 1001 AND 999999 OR post_id BETWEEN 1001 AND 999999;",
        "DELETE FROM post WHERE id BETWEEN 1001 AND 999999;",
        "DELETE FROM sys_user WHERE id BETWEEN 1001 AND 999999;",
        "",
    ]

    user_rows = []
    for user in data["users"]:
        user_rows.append(
            [
                str(user["id"]),
                sql_string(user["username"]),
                sql_string(PASSWORD_HASH),
                sql_string(user["nickname"]),
                sql_string(user["avatar"]),
                str(user["gender"]),
                sql_string(""),
                sql_string(f"{user['username']}@example.test"),
                sql_string(user["bio"]),
                str(user["following_count"]),
                str(user["fans_count"]),
                str(user["liked_count"]),
                str(user["collected_count"]),
                "1",
                "0",
                "NOW()",
                "NOW()",
            ]
        )
    lines.append(
        "INSERT INTO sys_user (id, username, password, nickname, avatar, gender, phone, email, bio, following_count, fans_count, liked_count, collected_count, status, deleted, create_time, update_time) VALUES"
    )
    lines.append(values_sql(user_rows))
    lines.append("")

    post_rows = []
    image_rows = []
    image_id = 1001
    for post in data["posts"]:
        post_rows.append(
            [
                str(post["id"]),
                str(post["user_id"]),
                sql_string(post["title"]),
                sql_string(post["content"]),
                str(post["type"]),
                sql_string(post["cover_image"]),
                sql_string(post["video_url"]),
                str(post["view_count"]),
                str(post["like_count"]),
                str(post["comment_count"]),
                str(post["collect_count"]),
                "1",
                "0",
                sql_string(post["created_at"]),
                sql_string(post["created_at"]),
            ]
        )
        for sort_order, image_url in enumerate(post["image_urls"], start=1):
            image_rows.append(
                [
                    str(image_id),
                    str(post["id"]),
                    sql_string(image_url),
                    str(sort_order),
                    str(post["width"]),
                    str(post["height"]),
                    sql_string(post["created_at"]),
                ]
            )
            image_id += 1
    lines.append(
        "INSERT INTO post (id, user_id, title, content, type, cover_image, video_url, view_count, like_count, comment_count, collect_count, status, deleted, create_time, update_time) VALUES"
    )
    lines.append(values_sql(post_rows))
    lines.append("")
    lines.append("INSERT INTO post_image (id, post_id, image_url, sort_order, width, height, create_time) VALUES")
    lines.append(values_sql(image_rows))
    lines.append("")

    comment_rows = []
    for comment in data["comments"]:
        comment_rows.append(
            [
                str(comment["id"]),
                str(comment["post_id"]),
                str(comment["user_id"]),
                sql_string(comment["content"]),
                str(comment["parent_id"]),
                str(comment["reply_user_id"]),
                str(comment["like_count"]),
                "1",
                "0",
                sql_string(comment["created_at"]),
                sql_string(comment["created_at"]),
            ]
        )
    if comment_rows:
        lines.append(
            "INSERT INTO comment (id, post_id, user_id, content, parent_id, reply_user_id, like_count, status, deleted, create_time, update_time) VALUES"
        )
        lines.append(values_sql(comment_rows))
        lines.append("")

    if data["follows"]:
        lines.append("INSERT IGNORE INTO user_follow (user_id, follow_user_id, create_time) VALUES")
        lines.append(values_sql([[str(a), str(b), "NOW()"] for a, b in data["follows"]]))
        lines.append("")

    if data["actions"]:
        lines.append("INSERT IGNORE INTO user_action (user_id, target_id, target_type, action_type, create_time) VALUES")
        lines.append(values_sql([[str(u), str(t), str(tt), str(at), "NOW()"] for u, t, tt, at in data["actions"]]))
        lines.append("")

    if data["notifications"]:
        rows = []
        for item in data["notifications"]:
            rows.append(
                [
                    str(item["id"]),
                    str(item["receiver_id"]),
                    str(item["sender_id"]),
                    str(item["type"]),
                    str(item["post_id"]),
                    str(item["comment_id"]),
                    sql_string(item["content"]),
                    "0",
                    "NULL",
                    sql_string(item["created_at"]),
                    sql_string(item["created_at"]),
                ]
            )
        lines.append(
            "INSERT INTO sys_notification (id, receiver_id, sender_id, type, post_id, comment_id, content, is_read, read_time, create_time, update_time) VALUES"
        )
        lines.append(values_sql(rows))
        lines.append("")

    lines.extend(
        [
            "SELECT",
            "  (SELECT COUNT(*) FROM sys_user WHERE id BETWEEN 1001 AND 999999) AS seeded_users,",
            "  (SELECT COUNT(*) FROM post WHERE id BETWEEN 1001 AND 999999) AS seeded_posts,",
            "  (SELECT COUNT(*) FROM post_image WHERE post_id BETWEEN 1001 AND 999999) AS seeded_images,",
            "  (SELECT COUNT(*) FROM comment WHERE post_id BETWEEN 1001 AND 999999) AS seeded_comments,",
            "  (SELECT COUNT(*) FROM user_action WHERE target_id BETWEEN 1001 AND 999999) AS seeded_actions,",
            "  (SELECT COUNT(*) FROM user_follow WHERE user_id BETWEEN 1001 AND 999999) AS seeded_follows;",
        ]
    )
    SQL_PATH.parent.mkdir(parents=True, exist_ok=True)
    SQL_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    data = normalize_dataset()
    JSON_PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    write_sql(data)
    print(
        f"Generated SQL from crawl: {len(data['posts'])} posts, {len(data['users'])} users, "
        f"{len(data['comments'])} comments"
    )
    print(SQL_PATH)
    print(JSON_PATH)


if __name__ == "__main__":
    main()
