package com.xiaohongshu.file.service.impl;

import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
import com.xiaohongshu.file.service.FileService;
import com.xiaohongshu.config.MinioConfig;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MinIO文件上传服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileServiceImpl implements FileService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    public String uploadFile(MultipartFile file) {
        return uploadFile(file, "images");
    }

    @Override
    public String uploadFile(MultipartFile file, String prefix) {
        try {
            // 检查存储桶是否存在，不存在则创建
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .build()
            );

            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .build()
                );

                // 设置桶的公开读策略，使前端可以直接访问图片
                String policy = """
                    {
                        "Version": "2012-10-17",
                        "Statement": [
                            {
                                "Effect": "Allow",
                                "Principal": {"AWS": ["*"]},
                                "Action": ["s3:GetObject"],
                                "Resource": ["arn:aws:s3:::%s/*"]
                            }
                        ]
                    }
                    """.formatted(minioConfig.getBucketName());

                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .config(policy)
                                .build()
                );

                log.info("创建存储桶并设置公开读策略：{}", minioConfig.getBucketName());
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = StringUtils.getFilenameExtension(originalFilename);
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String objectName = prefix + "/" + datePath + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;

            // 上传文件
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(objectName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            // 返回文件访问URL（使用对外访问地址，而非 MinIO 内部端点；若未配置则回退到 endpoint）
            String publicEndpoint = minioConfig.getPublicEndpoint();
            if (!StringUtils.hasText(publicEndpoint)) {
                publicEndpoint = minioConfig.getEndpoint();
            }
            String fileUrl = publicEndpoint + "/" + minioConfig.getBucketName() + "/" + objectName;
            log.info("文件上传成功：{}", fileUrl);

            return fileUrl;
        } catch (Exception e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            // 从URL中提取对象名
            String objectName = fileUrl.substring(fileUrl.indexOf(minioConfig.getBucketName()) + minioConfig.getBucketName().length() + 1);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功：{}", fileUrl);
        } catch (Exception e) {
            log.error("文件删除失败：{}", e.getMessage(), e);
            throw new RuntimeException("文件删除失败");
        }
    }
}
