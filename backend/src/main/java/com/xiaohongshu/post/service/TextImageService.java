package com.xiaohongshu.post.service;

import com.xiaohongshu.post.dto.TextImageDTO;

/**
 * 文本配图服务接口
 */
public interface TextImageService {

    /**
     * 根据文本生成配图
     *
     * @param dto 文本请求
     * @return PNG图片的字节数组
     */
    byte[] generateImage(TextImageDTO dto);
}
