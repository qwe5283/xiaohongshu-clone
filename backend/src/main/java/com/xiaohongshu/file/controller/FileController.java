package com.xiaohongshu.file.controller;

import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 上传图片
     */
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String fileUrl = fileService.uploadFile(file, "images");

        Map<String, String> result = new HashMap<>();
        result.put("url", fileUrl);

        return Result.success("上传成功", result);
    }

    /**
     * 上传视频
     */
    @PostMapping("/video")
    public Result<Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file) {
        String fileUrl = fileService.uploadFile(file, "videos");

        Map<String, String> result = new HashMap<>();
        result.put("url", fileUrl);

        return Result.success("上传成功", result);
    }

    /**
     * 通用文件上传
     */
    @PostMapping("/file")
    public Result<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        String fileUrl = fileService.uploadFile(file, "files");

        Map<String, String> result = new HashMap<>();
        result.put("url", fileUrl);

        return Result.success("上传成功", result);
    }
}
