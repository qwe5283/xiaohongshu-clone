package com.xiaohongshu.interact.service.impl;

import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
import com.xiaohongshu.interact.dto.CommentCreateDTO;
import com.xiaohongshu.interact.entity.Comment;
import com.xiaohongshu.interact.mapper.CommentMapper;
import com.xiaohongshu.interact.mapper.UserActionMapper;
import com.xiaohongshu.interact.vo.CommentVO;
import com.xiaohongshu.notification.service.NotificationService;
import com.xiaohongshu.post.entity.Post;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

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
    @Mock
    private NotificationService notificationService;
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
    void createCommentAllowsReplyToReply() {
        doReturn(new Post()).when(postService).getById(1L);

        Comment parent = new Comment();
        parent.setId(10L);
        parent.setPostId(1L);
        parent.setUserId(200L);
        parent.setParentId(9L);
        doReturn(parent).when(commentService).getById(10L);

        CommentCreateDTO dto = new CommentCreateDTO();
        dto.setPostId(1L);
        dto.setParentId(10L);
        dto.setContent("nested reply");

        doReturn(true).when(commentService).save(org.mockito.ArgumentMatchers.any(Comment.class));

        CommentVO vo = commentService.createComment(100L, dto);
        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);

        verify(commentService).save(commentCaptor.capture());
        Comment savedComment = commentCaptor.getValue();
        assertThat(savedComment.getParentId()).isEqualTo(10L);
        assertThat(savedComment.getReplyUserId()).isEqualTo(200L);
        assertThat(savedComment.getContent()).isEqualTo("nested reply");
        assertThat(vo).isNotNull();
        verify(notificationService).createCommentReplyNotification(100L, savedComment);
    }
}
