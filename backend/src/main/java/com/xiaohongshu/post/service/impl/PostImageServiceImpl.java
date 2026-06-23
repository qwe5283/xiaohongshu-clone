package com.xiaohongshu.post.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohongshu.post.entity.PostImage;
import com.xiaohongshu.post.mapper.PostImageMapper;
import com.xiaohongshu.post.service.PostImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 笔记图片服务实现类
 */
@Slf4j
@Service
public class PostImageServiceImpl extends ServiceImpl<PostImageMapper, PostImage> implements PostImageService {
}
