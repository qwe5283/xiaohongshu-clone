-- ============================================================
-- 小红书数据库 DML 脚本（测试数据）
-- 执行顺序：在 DDL 脚本执行后执行此脚本
-- ============================================================
SET NAMES utf8mb4;

USE xiaohongshu;

-- ==================== 插入测试用户 ====================
-- 密码均为: 123456 (BCrypt加密)
-- 生成方式: new BCryptPasswordEncoder().encode("123456")
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `avatar`, `gender`, `phone`, `bio`) VALUES
('admin',  '$2a$10$.c3kAZ1MFSmMQfe0RlA9rOnxWrIX7N/CTFirwmf4/OTAu.n5XTuum', '管理员',       'https://picsum.photos/id/1005/100/100', 1, '13800138000', '系统管理员'),
('user1',  '$2a$10$.c3kAZ1MFSmMQfe0RlA9rOnxWrIX7N/CTFirwmf4/OTAu.n5XTuum', '小红书用户1',   'https://picsum.photos/id/1012/100/100', 2, '13800138001', '热爱生活，热爱分享'),
('user2',  '$2a$10$.c3kAZ1MFSmMQfe0RlA9rOnxWrIX7N/CTFirwmf4/OTAu.n5XTuum', '小红书用户2',   'https://picsum.photos/id/1025/100/100', 1, '13800138002', '美食博主');

-- ==================== 插入测试笔记 ====================
INSERT INTO `post` (`user_id`, `title`, `content`, `type`, `cover_image`, `view_count`, `like_count`, `comment_count`, `collect_count`, `status`) VALUES
(1, '欢迎来到小红书！',     '这是第一篇笔记，欢迎大家来到小红书社区！',         0, 'https://picsum.photos/id/1015/400/300', 100, 50, 10, 20, 1),
(2, '今天的美食分享',       '分享一道超级简单的家常菜做法...',                 0, 'https://picsum.photos/id/1080/400/500', 200, 80, 15, 30, 1),
(3, '旅行日记',             '记录一次难忘的旅行经历...',                       0, 'https://picsum.photos/id/1039/400/600', 150, 60,  8, 25, 1);

-- ==================== 插入测试图片 ====================
INSERT INTO `post_image` (`post_id`, `image_url`, `sort_order`, `width`, `height`) VALUES
(1, 'https://picsum.photos/id/1015/400/300', 1, 400, 300),
(2, 'https://picsum.photos/id/1080/400/500', 1, 400, 500),
(3, 'https://picsum.photos/id/1039/400/600', 1, 400, 600);

-- ==================== 插入测试评论 ====================
-- 一级评论
INSERT INTO `comment` (`post_id`, `user_id`, `content`, `parent_id`, `reply_user_id`, `like_count`, `status`) VALUES
(1, 2, '写得真好，支持！',       0, 0, 5, 1),
(1, 3, '感谢分享，学到了很多！', 0, 0, 3, 1),
(2, 1, '看起来好好吃啊！',       0, 0, 8, 1),
(2, 3, '求详细做法！',           0, 0, 2, 1),
(3, 1, '好美的风景！',           0, 0, 6, 1);

-- 回复（二级评论）
INSERT INTO `comment` (`post_id`, `user_id`, `content`, `parent_id`, `reply_user_id`, `like_count`, `status`) VALUES
(1, 1, '谢谢支持！',             1, 2, 1, 1),
(1, 2, '互相学习～',             2, 3, 0, 1),
(2, 2, '好的，下次出个详细教程！', 3, 1, 3, 1);

-- ==================== 插入测试用户行为（点赞/收藏） ====================
-- 点赞笔记
INSERT INTO `user_action` (`user_id`, `target_id`, `target_type`, `action_type`) VALUES
(2, 1, 1, 1),  -- user2 点赞 笔记1
(3, 1, 1, 1),  -- user3 点赞 笔记1
(1, 2, 1, 1),  -- user1 点赞 笔记2
(3, 2, 1, 1),  -- user3 点赞 笔记2
(1, 3, 1, 1),  -- user1 点赞 笔记3
(2, 3, 1, 1);  -- user2 点赞 笔记3

-- 收藏笔记
INSERT INTO `user_action` (`user_id`, `target_id`, `target_type`, `action_type`) VALUES
(2, 1, 1, 2),  -- user2 收藏 笔记1
(3, 1, 1, 2),  -- user3 收藏 笔记1
(1, 2, 1, 2),  -- user1 收藏 笔记2
(1, 3, 1, 2);  -- user1 收藏 笔记3

-- 点赞评论
INSERT INTO `user_action` (`user_id`, `target_id`, `target_type`, `action_type`) VALUES
(1, 1, 2, 1),  -- user1 点赞 评论1
(3, 1, 2, 1),  -- user3 点赞 评论1
(2, 3, 2, 1);  -- user2 点赞 评论3

-- ==================== 插入测试关注关系 ====================
INSERT INTO `user_follow` (`user_id`, `follow_user_id`) VALUES
(1, 2),  -- user1 关注 user2
(1, 3),  -- user1 关注 user3
(2, 1),  -- user2 关注 user1
(3, 1);  -- user3 关注 user1
