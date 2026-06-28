package com.xiaohongshu.file.controller;

import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传控制器
 */
@Tag(name = "文件上传")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 上传图片
     */
    @Operation(summary = "上传图片", description = "上传一张图片文件，返回文件URL")
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(
            @Parameter(description = "图片文件", required = true)
            @RequestParam("file") MultipartFile file) {
        String fileUrl = fileService.uploadFile(file, "images");

        Map<String, String> result = new HashMap<>();
        result.put("url", fileUrl);

        return Result.success("上传成功", result);
    }

    /**
     * 上传视频
     */
    @Operation(summary = "上传视频", description = "上传一个视频文件，返回文件URL")
    @PostMapping("/video")
    public Result<Map<String, String>> uploadVideo(
            @Parameter(description = "视频文件", required = true)
            @RequestParam("file") MultipartFile file) {
        String fileUrl = fileService.uploadFile(file, "videos");

        Map<String, String> result = new HashMap<>();
        result.put("url", fileUrl);

        return Result.success("上传成功", result);
    }

    /**
     * 通用文件上传
     */
    @Operation(summary = "通用文件上传", description = "上传任意文件，返回文件URL")
    @PostMapping("/file")
    public Result<Map<String, String>> uploadFile(
            @Parameter(description = "文件", required = true)
            @RequestParam("file") MultipartFile file) {
        String fileUrl = fileService.uploadFile(file, "files");

        Map<String, String> result = new HashMap<>();
        result.put("url", fileUrl);

        return Result.success("上传成功", result);
    }
}
