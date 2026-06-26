package com.xiaohongshu.file.service.impl;

import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
import com.xiaohongshu.config.MinioConfig;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MinioFileServiceImplTest {

    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @InjectMocks
    private MinioFileServiceImpl fileService;

    @Test
    void uploadImageRejectsSpoofedContentBeforeCallingMinio() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evil.png",
                "image/png",
                "not a png".getBytes()
        );

        assertThatThrownBy(() -> fileService.uploadFile(file, "images"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(ResultCode.FILE_TYPE_ERROR));
        verifyNoInteractions(minioClient);
    }

    @Test
    void uploadVideoRejectsUnsupportedExtensionBeforeCallingMinio() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "movie.exe",
                "video/mp4",
                new byte[]{0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32}
        );

        assertThatThrownBy(() -> fileService.uploadFile(file, "videos"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(ResultCode.FILE_TYPE_ERROR));
        verifyNoInteractions(minioClient);
    }
}
