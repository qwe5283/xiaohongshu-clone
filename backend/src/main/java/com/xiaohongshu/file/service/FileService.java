package com.xiaohongshu.file.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务接口
 */
public interface FileService {

    /**
     * 上传文件
     *
     * @param file 文件
     * @return 文件访问URL
     */
    String uploadFile(MultipartFile file);

    /**
     * 上传文件到指定目录
     *
     * @param file   文件
     * @param prefix 目录前缀
     * @return 文件访问URL
     */
    String uploadFile(MultipartFile file, String prefix);

    /**
     * 删除文件
     *
     * @param fileUrl 文件URL
     */
    void deleteFile(String fileUrl);
}
