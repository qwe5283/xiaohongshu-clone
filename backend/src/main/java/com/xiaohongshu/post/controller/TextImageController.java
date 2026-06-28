package com.xiaohongshu.post.controller;

import com.xiaohongshu.post.dto.TextImageDTO;
import com.xiaohongshu.post.service.TextImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文本配图控制器
 * <p>
 * 接收文本输入，返回2:3比例的PNG配图。
 */
@Tag(name = "文本配图")
@RestController
@RequestMapping("/post/text-image")
@RequiredArgsConstructor
public class TextImageController {

    private final TextImageService textImageService;

    /**
     * 根据文本生成配图
     *
     * @param dto 文本请求（最多20字）
     * @return PNG图片二进制流
     */
    @Operation(summary = "文本生成配图", description = "输入文本内容（最多20字），返回一张2:3比例的PNG配图")
    @GetMapping("/generate")
    public ResponseEntity<byte[]> generateImage(@Valid TextImageDTO dto) {
        byte[] imageBytes = textImageService.generateImage(dto);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }
}
