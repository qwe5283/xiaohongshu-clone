package com.xiaohongshu.ai.service.impl;

import com.xiaohongshu.ai.client.LlmClient;
import com.xiaohongshu.ai.dto.ChatRequestDTO;
import com.xiaohongshu.ai.service.AiChatService;
import com.xiaohongshu.ai.vo.ChatResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI对话服务实现。
 */
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final LlmClient llmClient;

    @Override
    public ChatResponseVO chat(Long userId, ChatRequestDTO requestDTO) {
        String answer = llmClient.chat(requestDTO.getSystemPrompt(), requestDTO.getMessage());
        return new ChatResponseVO(answer);
    }
}
