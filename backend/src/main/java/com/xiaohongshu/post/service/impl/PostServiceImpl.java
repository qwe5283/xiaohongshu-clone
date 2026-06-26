package com.xiaohongshu.post.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
import com.xiaohongshu.interact.entity.Comment;
import com.xiaohongshu.interact.entity.UserAction;
import com.xiaohongshu.interact.mapper.CommentMapper;
import com.xiaohongshu.interact.mapper.UserActionMapper;
import com.xiaohongshu.post.dto.PostCreateDTO;
import com.xiaohongshu.post.dto.PostQueryDTO;
import com.xiaohongshu.post.dto.PostUpdateDTO;
import com.xiaohongshu.post.entity.Post;
import com.xiaohongshu.post.entity.PostImage;
import com.xiaohongshu.user.entity.User;
import com.xiaohongshu.post.mapper.PostMapper;
import com.xiaohongshu.post.service.PostImageService;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.user.service.UserService;
import com.xiaohongshu.post.vo.PostImageVO;
import com.xiaohongshu.post.vo.PostVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 笔记服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    private final PostImageService postImageService;
    private final UserService userService;
    private final CommentMapper commentMapper;
    // 直接注入 Mapper 查点赞状态，避免 PostService ↔ UserActionService 循环依赖
    private final UserActionMapper userActionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO createPost(Long userId, PostCreateDTO createDTO) {
        // 校验：视频和图片不能同时缺省
        boolean hasVideo = StringUtils.hasText(createDTO.getVideoUrl());
        boolean hasImages = !CollectionUtils.isEmpty(createDTO.getImageUrls());
        if (!hasVideo && !hasImages) {
            throw new BusinessException(ResultCode.POST_MEDIA_REQUIRED);
        }

        // 创建笔记
        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(createDTO.getTitle());
        post.setContent(createDTO.getContent());
        // 自动推导 type：有视频→1，仅图片→0
        post.setType(hasVideo ? 1 : 0);
        if (hasVideo) {
            post.setVideoUrl(createDTO.getVideoUrl());
        }
        // 自动设置封面图：优先使用显式传入的 coverImage
        if (StringUtils.hasText(createDTO.getCoverImage())) {
            post.setCoverImage(createDTO.getCoverImage());
        } else if (hasImages) {
            // 降级到使用第一张图片
            post.setCoverImage(createDTO.getImageUrls().get(0));
        }
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setCollectCount(0);
        post.setStatus(1); // 已发布
        post.setDeleted(0);

        // 保存笔记
        save(post);

        // 保存图片列表（视频笔记也可以附带图片）
        if (hasImages) {
            List<PostImage> images = new ArrayList<>();
            for (int i = 0; i < createDTO.getImageUrls().size(); i++) {
                PostImage image = new PostImage();
                image.setPostId(post.getId());
                image.setImageUrl(createDTO.getImageUrls().get(i));
                image.setSortOrder(i + 1);
                images.add(image);
            }
            postImageService.saveBatch(images);
        }

        log.info("笔记创建成功，ID：{}", post.getId());

        // 返回笔记详情
        return getPostById(post.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO updatePost(Long userId, PostUpdateDTO updateDTO) {
        Post post = getById(updateDTO.getId());
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }

        // 验证权限
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.POST_NO_PERMISSION);
        }

        // 更新基本信息
        if (StringUtils.hasText(updateDTO.getTitle())) {
            post.setTitle(updateDTO.getTitle());
        }
        if (updateDTO.getContent() != null) {
            post.setContent(updateDTO.getContent());
        }
        if (updateDTO.getVideoUrl() != null) {
            post.setVideoUrl(updateDTO.getVideoUrl());
        }

        // 更新图片列表
        boolean imagesUpdated = updateDTO.getImageUrls() != null;
        if (imagesUpdated) {
            // 删除旧图片
            postImageService.remove(new LambdaQueryWrapper<PostImage>()
                    .eq(PostImage::getPostId, post.getId()));

            // 保存新图片
            if (!CollectionUtils.isEmpty(updateDTO.getImageUrls())) {
                List<PostImage> images = new ArrayList<>();
                for (int i = 0; i < updateDTO.getImageUrls().size(); i++) {
                    PostImage image = new PostImage();
                    image.setPostId(post.getId());
                    image.setImageUrl(updateDTO.getImageUrls().get(i));
                    image.setSortOrder(i + 1);
                    images.add(image);
                }
                postImageService.saveBatch(images);
            }
        }

        // 校验：更新后视频和图片不能同时缺省
        boolean hasVideo = StringUtils.hasText(post.getVideoUrl());
        boolean hasImages;
        if (imagesUpdated) {
            hasImages = !CollectionUtils.isEmpty(updateDTO.getImageUrls());
        } else {
            // 图片未更新，查询数据库确认是否仍有图片
            hasImages = postImageService.exists(new LambdaQueryWrapper<PostImage>()
                    .eq(PostImage::getPostId, post.getId()));
        }
        if (!hasVideo && !hasImages) {
            throw new BusinessException(ResultCode.POST_MEDIA_REQUIRED);
        }

        // 自动推导 type：有视频→1，仅图片→0
        post.setType(hasVideo ? 1 : 0);

        // 自动设置封面图：优先使用显式传入的 coverImage
        if (StringUtils.hasText(updateDTO.getCoverImage())) {
            post.setCoverImage(updateDTO.getCoverImage());
        } else if (imagesUpdated) {
            post.setCoverImage(hasImages ? updateDTO.getImageUrls().get(0) : "");
        }

        updateById(post);

        log.info("笔记更新成功，ID：{}", post.getId());

        return getPostById(post.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long userId, Long postId) {
        Post post = getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }

        // 验证权限
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.POST_NO_PERMISSION);
        }

        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId));
        List<Long> commentIds = comments.stream()
                .map(Comment::getId)
                .collect(Collectors.toList());

        update(new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .set(Post::getLikeCount, 0)
                .set(Post::getCollectCount, 0)
                .set(Post::getCommentCount, 0));

        // 删除笔记（逻辑删除）
        removeById(postId);

        // 删除关联图片
        postImageService.remove(new LambdaQueryWrapper<PostImage>()
                .eq(PostImage::getPostId, postId));

        // 删除笔记下的全部评论（含回复）
        if (!comments.isEmpty()) {
            commentMapper.delete(new LambdaQueryWrapper<Comment>()
                    .eq(Comment::getPostId, postId));
        }

        // 删除笔记的点赞/收藏行为
        userActionMapper.delete(new LambdaQueryWrapper<UserAction>()
                .eq(UserAction::getTargetType, 1)
                .eq(UserAction::getTargetId, postId));

        // 删除评论的点赞行为
        if (!commentIds.isEmpty()) {
            userActionMapper.delete(new LambdaQueryWrapper<UserAction>()
                    .eq(UserAction::getTargetType, 2)
                    .in(UserAction::getTargetId, commentIds));
        }

        int postLikeCount = post.getLikeCount() != null ? post.getLikeCount() : 0;
        int postCollectCount = post.getCollectCount() != null ? post.getCollectCount() : 0;
        if (postLikeCount > 0 || postCollectCount > 0) {
            userService.update(new LambdaUpdateWrapper<User>()
                    .eq(User::getId, post.getUserId())
                    .setSql("liked_count = GREATEST(liked_count - " + postLikeCount + ", 0), " +
                            "collected_count = GREATEST(collected_count - " + postCollectCount + ", 0)"));
        }

        log.info("笔记删除成功，ID：{}，清理评论：{}，清理评论点赞：{}", postId, comments.size(), commentIds.size());
    }

    @Override
    public PostVO getPostById(Long postId) {
        Post post = getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }

        return convertToPostVO(post);
    }

    @Override
    public IPage<PostVO> getPostPage(PostQueryDTO queryDTO) {
        return getPostPage(queryDTO, null);
    }

    @Override
    public IPage<PostVO> getPostPage(PostQueryDTO queryDTO, Long userId) {
        // 构建查询条件
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.like(Post::getTitle, queryDTO.getKeyword());
        }

        // 作者筛选
        if (queryDTO.getUserId() != null) {
            wrapper.eq(Post::getUserId, queryDTO.getUserId());
        }

        // 类型筛选
        if (queryDTO.getType() != null) {
            wrapper.eq(Post::getType, queryDTO.getType());
        }

        // 状态筛选（默认查询已发布）
        wrapper.eq(Post::getStatus, queryDTO.getStatus() != null ? queryDTO.getStatus() : 1);

        // 排序
        if ("hot".equals(queryDTO.getSortType())) {
            wrapper.orderByDesc(Post::getLikeCount);
        } else {
            wrapper.orderByDesc(Post::getCreateTime);
        }

        // 分页查询
        Page<Post> page = new Page<>(queryDTO.getPageNumSafe(), queryDTO.getPageSizeSafe());
        IPage<Post> postPage = page(page, wrapper);

        // 转换为VO
        IPage<PostVO> voPage = postPage.convert(this::convertToPostVO);

        // 批量填充当前用户的点赞状态
        if (userId != null && !voPage.getRecords().isEmpty()) {
            fillLikedStatus(userId, voPage.getRecords());
        }

        return voPage;
    }

    @Override
    public IPage<PostVO> getUserPosts(Long userId, PostQueryDTO queryDTO) {
        queryDTO.setUserId(userId);
        return getPostPage(queryDTO);
    }

    @Override
    public void incrementViewCount(Long postId) {
        update(new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("view_count = view_count + 1"));
    }

    @Override
    public List<PostVO> getPostsByIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询笔记
        List<Post> posts = listByIds(postIds);
        if (posts.isEmpty()) {
            return new ArrayList<>();
        }

        // 按传入的ID顺序排列（保持收藏时间顺序）
        Map<Long, Post> postMap = posts.stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        List<PostVO> result = postIds.stream()
                .filter(postMap::containsKey)
                .map(id -> convertToPostVO(postMap.get(id)))
                .collect(Collectors.toList());

        return result;
    }

    /**
     * 批量填充笔记列表的点赞状态
     */
    private void fillLikedStatus(Long userId, List<PostVO> posts) {
        try {
            List<Long> postIds = posts.stream().map(PostVO::getId).collect(Collectors.toList());
            // 直接查 user_action 表，避免循环依赖
            List<UserAction> likedActions = userActionMapper.selectList(
                    new LambdaQueryWrapper<UserAction>()
                            .eq(UserAction::getUserId, userId)
                            .eq(UserAction::getTargetType, 1)   // 笔记
                            .eq(UserAction::getActionType, 1)   // 点赞
                            .in(UserAction::getTargetId, postIds));
            java.util.Set<Long> likedPostIds = likedActions.stream()
                    .map(UserAction::getTargetId)
                    .collect(Collectors.toSet());
            posts.forEach(post -> post.setLiked(likedPostIds.contains(post.getId())));
        } catch (Exception e) {
            log.warn("批量获取点赞状态失败：{}", e.getMessage());
        }
    }

    /**
     * 将Post实体转换为PostVO
     */
    private PostVO convertToPostVO(Post post) {
        PostVO postVO = new PostVO();
        BeanUtil.copyProperties(post, postVO);

        // 获取作者信息
        try {
            User author = userService.getById(post.getUserId());
            if (author != null) {
                postVO.setAuthorNickname(author.getNickname());
                postVO.setAuthorAvatar(author.getAvatar());
            }
        } catch (Exception e) {
            log.warn("获取作者信息失败：{}", e.getMessage());
        }

        // 获取图片列表
        try {
            List<PostImage> images = postImageService.list(new LambdaQueryWrapper<PostImage>()
                    .eq(PostImage::getPostId, post.getId())
                    .orderByAsc(PostImage::getSortOrder));

            List<PostImageVO> imageVOs = images.stream()
                    .map(img -> {
                        PostImageVO imgVO = new PostImageVO();
                        BeanUtil.copyProperties(img, imgVO);
                        return imgVO;
                    })
                    .collect(Collectors.toList());

            postVO.setImages(imageVOs);
        } catch (Exception e) {
            log.warn("获取笔记图片失败：{}", e.getMessage());
            postVO.setImages(new ArrayList<>());
        }

        return postVO;
    }
}
