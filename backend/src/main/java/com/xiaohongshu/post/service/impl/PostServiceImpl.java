package com.xiaohongshu.post.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO createPost(Long userId, PostCreateDTO createDTO) {
        // 创建笔记
        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(createDTO.getTitle());
        post.setContent(createDTO.getContent());
        post.setType(createDTO.getType() != null ? createDTO.getType() : 0);
        post.setCoverImage(createDTO.getCoverImage());
        post.setVideoUrl(createDTO.getVideoUrl());
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setCollectCount(0);
        post.setStatus(1); // 已发布
        post.setDeleted(0);

        // 保存笔记
        save(post);

        // 保存图片列表
        if (!CollectionUtils.isEmpty(createDTO.getImageUrls())) {
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

        // 更新笔记信息
        if (StringUtils.hasText(updateDTO.getTitle())) {
            post.setTitle(updateDTO.getTitle());
        }
        if (updateDTO.getContent() != null) {
            post.setContent(updateDTO.getContent());
        }
        if (updateDTO.getType() != null) {
            post.setType(updateDTO.getType());
        }
        if (updateDTO.getCoverImage() != null) {
            post.setCoverImage(updateDTO.getCoverImage());
        }
        if (updateDTO.getVideoUrl() != null) {
            post.setVideoUrl(updateDTO.getVideoUrl());
        }

        updateById(post);

        // 更新图片列表
        if (updateDTO.getImageUrls() != null) {
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

        // 删除笔记（逻辑删除）
        removeById(postId);

        // 删除关联图片
        postImageService.remove(new LambdaQueryWrapper<PostImage>()
                .eq(PostImage::getPostId, postId));

        log.info("笔记删除成功，ID：{}", postId);
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
