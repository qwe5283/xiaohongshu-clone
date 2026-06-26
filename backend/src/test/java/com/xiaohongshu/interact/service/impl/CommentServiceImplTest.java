package com.xiaohongshu.interact.service.impl;

import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
import com.xiaohongshu.interact.dto.CommentCreateDTO;
import com.xiaohongshu.interact.entity.Comment;
import com.xiaohongshu.interact.mapper.CommentMapper;
import com.xiaohongshu.interact.mapper.UserActionMapper;
import com.xiaohongshu.post.entity.Post;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private PostService postService;
    @Mock
    private UserService userService;
    @Mock
    private UserActionMapper userActionMapper;
    @Mock
    private CommentMapper commentMapper;
    @Spy
    @InjectMocks
    private CommentServiceImpl commentService;

    @Test
    void createCommentRejectsParentFromAnotherPost() {
        doReturn(new Post()).when(postService).getById(1L);

        Comment parent = new Comment();
        parent.setId(10L);
        parent.setPostId(2L);
        parent.setParentId(0L);
        doReturn(parent).when(commentService).getById(10L);

        CommentCreateDTO dto = new CommentCreateDTO();
        dto.setPostId(1L);
        dto.setParentId(10L);
        dto.setContent("reply");

        assertThatThrownBy(() -> commentService.createComment(100L, dto))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
    }

    @Test
    void createCommentRejectsReplyToReply() {
        doReturn(new Post()).when(postService).getById(1L);

        Comment parent = new Comment();
        parent.setId(10L);
        parent.setPostId(1L);
        parent.setParentId(9L);
        doReturn(parent).when(commentService).getById(10L);

        CommentCreateDTO dto = new CommentCreateDTO();
        dto.setPostId(1L);
        dto.setParentId(10L);
        dto.setContent("nested reply");

        assertThatThrownBy(() -> commentService.createComment(100L, dto))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
    }
}
