package com.xiaohongshu.interact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaohongshu.interact.entity.UserAction;
import com.xiaohongshu.interact.mapper.UserActionMapper;
import com.xiaohongshu.interact.service.CommentService;
import com.xiaohongshu.post.entity.Post;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.user.entity.User;
import com.xiaohongshu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserActionServiceImplTest {

    @Mock
    private PostService postService;
    @Mock
    private CommentService commentService;
    @Mock
    private UserService userService;
    @Mock
    private UserActionMapper userActionMapper;
    @Spy
    @InjectMocks
    private UserActionServiceImpl userActionService;

    @Test
    void toggleLikePostTreatsDuplicateInsertAsAlreadyLikedWithoutIncrementingCounters() {
        Post post = new Post();
        post.setId(10L);
        post.setUserId(20L);
        doReturn(post).when(postService).getById(10L);
        doReturn(null).when(userActionService).getOne(any(LambdaQueryWrapper.class));
        doThrow(new DuplicateKeyException("duplicate")).when(userActionService).save(any(UserAction.class));

        boolean liked = userActionService.toggleLikePost(1L, 10L);

        assertThat(liked).isTrue();
        verify(postService, never()).update(any(LambdaUpdateWrapper.class));
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void toggleLikePostUsesNonNegativeCounterSqlWhenRemoving() {
        Post post = new Post();
        post.setId(10L);
        post.setUserId(20L);
        doReturn(post).when(postService).getById(10L);

        UserAction existing = new UserAction();
        existing.setId(99L);
        doReturn(existing).when(userActionService).getOne(any(LambdaQueryWrapper.class));
        doReturn(true).when(userActionService).removeById(99L);

        boolean liked = userActionService.toggleLikePost(1L, 10L);

        assertThat(liked).isFalse();

        ArgumentCaptor<LambdaUpdateWrapper<Post>> postWrapperCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(postService).update(postWrapperCaptor.capture());
        assertThat(postWrapperCaptor.getValue().getSqlSet()).contains("GREATEST(like_count - 1, 0)");

        ArgumentCaptor<LambdaUpdateWrapper<User>> userWrapperCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userService).update(userWrapperCaptor.capture());
        assertThat(userWrapperCaptor.getValue().getSqlSet()).contains("GREATEST(liked_count - 1, 0)");
    }
}
