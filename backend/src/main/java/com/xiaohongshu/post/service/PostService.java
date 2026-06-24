package com.xiaohongshu.post.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaohongshu.post.dto.PostCreateDTO;
import com.xiaohongshu.post.dto.PostQueryDTO;
import com.xiaohongshu.post.dto.PostUpdateDTO;
import com.xiaohongshu.post.entity.Post;
import com.xiaohongshu.post.vo.PostVO;

import java.util.List;

/**
 * 笔记服务接口
 */
public interface PostService extends IService<Post> {

    /**
     * 创建笔记
     *
     * @param userId      作者ID
     * @param createDTO   创建请求DTO
     * @return 笔记信息
     */
    PostVO createPost(Long userId, PostCreateDTO createDTO);

    /**
     * 更新笔记
     *
     * @param userId      作者ID
     * @param updateDTO   更新请求DTO
     * @return 笔记信息
     */
    PostVO updatePost(Long userId, PostUpdateDTO updateDTO);

    /**
     * 删除笔记
     *
     * @param userId 作者ID
     * @param postId 笔记ID
     */
    void deletePost(Long userId, Long postId);

    /**
     * 根据ID获取笔记详情
     *
     * @param postId 笔记ID
     * @return 笔记信息
     */
    PostVO getPostById(Long postId);

    /**
     * 分页查询笔记列表
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    IPage<PostVO> getPostPage(PostQueryDTO queryDTO);

    /**
     * 获取用户的笔记列表
     *
     * @param userId   用户ID
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    IPage<PostVO> getUserPosts(Long userId, PostQueryDTO queryDTO);

    /**
     * 增加浏览量
     *
     * @param postId 笔记ID
     */
    void incrementViewCount(Long postId);

    /**
     * 根据ID列表批量获取笔记（保持传入顺序）
     *
     * @param postIds 笔记ID列表
     * @return 笔记VO列表（按传入顺序）
     */
    List<PostVO> getPostsByIds(List<Long> postIds);
}
