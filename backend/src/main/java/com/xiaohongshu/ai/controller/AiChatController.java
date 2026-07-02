package com.xiaohongshu.ai.controller;

import com.xiaohongshu.ai.dto.ChatRequestDTO;
import com.xiaohongshu.ai.service.AiChatService;
import com.xiaohongshu.ai.vo.ChatResponseVO;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI助手控制器。
 */
@Tag(name = "AI助手")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "AI对话", description = "调用大语言模型推理接口，返回单轮问答结果")
    @PostMapping("/chat")
    public Result<ChatResponseVO> chat(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody ChatRequestDTO requestDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        ChatResponseVO responseVO = aiChatService.chat(userId, requestDTO);
        return Result.success(responseVO);
    }
}
