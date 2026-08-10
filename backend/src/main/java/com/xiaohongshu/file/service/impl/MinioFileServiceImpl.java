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

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
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

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "webm", "mov");
    private static final Set<String> VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime"
    );
    private static final Set<String> FILE_EXTENSIONS = Set.of(
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv"
    );
    private static final Set<String> FILE_CONTENT_TYPES = Set.of(
            "text/plain", "text/csv", "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    @Override
    public String uploadFile(MultipartFile file) {
        return uploadFile(file, "images");
    }

    @Override
    public String uploadFile(MultipartFile file, String prefix) {
        try {
            String extension = validateFile(file, prefix);

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

            // 返回相对路径（/{bucket}/{objectName}），由前端 nginx 反代到 MinIO，
            // 避免把机器绑定的绝对地址（如 localhost:9000）写死入库，导致其他环境无法访问
            String fileUrl = "/" + minioConfig.getBucketName() + "/" + objectName;
            log.info("文件上传成功：{}", fileUrl);

            return fileUrl;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR);
        }
    }

    private String validateFile(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR, "上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String contentType = file.getContentType();
        if (!StringUtils.hasText(extension) || !StringUtils.hasText(contentType)) {
            throw new BusinessException(ResultCode.FILE_TYPE_ERROR);
        }

        extension = extension.toLowerCase(Locale.ROOT);
        contentType = contentType.toLowerCase(Locale.ROOT);

        byte[] header = readHeader(file);
        if ("images".equals(prefix)) {
            if (!IMAGE_EXTENSIONS.contains(extension) || !IMAGE_CONTENT_TYPES.contains(contentType) || !hasImageSignature(header, contentType)) {
                throw new BusinessException(ResultCode.FILE_TYPE_ERROR);
            }
        } else if ("videos".equals(prefix)) {
            if (!VIDEO_EXTENSIONS.contains(extension) || !VIDEO_CONTENT_TYPES.contains(contentType) || !hasVideoSignature(header, contentType)) {
                throw new BusinessException(ResultCode.FILE_TYPE_ERROR);
            }
        } else if ("files".equals(prefix)) {
            if (!FILE_EXTENSIONS.contains(extension) || !FILE_CONTENT_TYPES.contains(contentType)) {
                throw new BusinessException(ResultCode.FILE_TYPE_ERROR);
            }
        } else {
            throw new BusinessException(ResultCode.FILE_TYPE_ERROR);
        }

        return extension;
    }

    private byte[] readHeader(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(16);
        }
    }

    private boolean hasImageSignature(byte[] header, String contentType) {
        return switch (contentType) {
            case "image/png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            case "image/jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/gif" -> startsWith(header, new byte[]{0x47, 0x49, 0x46, 0x38});
            case "image/webp" -> startsWith(header, new byte[]{0x52, 0x49, 0x46, 0x46})
                    && header.length >= 12
                    && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
            default -> false;
        };
    }

    private boolean hasVideoSignature(byte[] header, String contentType) {
        if ("video/webm".equals(contentType)) {
            return startsWith(header, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
        }
        return header.length >= 12
                && header[4] == 0x66 && header[5] == 0x74 && header[6] == 0x79 && header[7] == 0x70;
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
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
