package com.xiaohongshu.ai.service;

import com.xiaohongshu.ai.dto.ChatRequestDTO;
import com.xiaohongshu.ai.vo.ChatResponseVO;

/**
 * AI对话服务。
 */
public interface AiChatService {

    ChatResponseVO chat(Long userId, ChatRequestDTO requestDTO);
}
