package com.xiaohongshu.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaohongshu.social.entity.UserFollow;
import com.xiaohongshu.social.mapper.UserFollowMapper;
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
class FollowServiceImplTest {

    @Mock
    private UserService userService;
    @Mock
    private UserFollowMapper userFollowMapper;
    @Spy
    @InjectMocks
    private FollowServiceImpl followService;

    @Test
    void toggleFollowTreatsDuplicateInsertAsAlreadyFollowedWithoutIncrementingCounters() {
        doReturn(new User()).when(userService).getById(2L);
        doReturn(null).when(followService).getOne(any(LambdaQueryWrapper.class));
        doThrow(new DuplicateKeyException("duplicate")).when(followService).save(any(UserFollow.class));

        boolean followed = followService.toggleFollow(1L, 2L);

        assertThat(followed).isTrue();
        verify(userService, never()).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void toggleFollowUsesNonNegativeCounterSqlWhenRemoving() {
        doReturn(new User()).when(userService).getById(2L);

        UserFollow existing = new UserFollow();
        existing.setId(99L);
        doReturn(existing).when(followService).getOne(any(LambdaQueryWrapper.class));
        doReturn(true).when(followService).removeById(99L);

        boolean followed = followService.toggleFollow(1L, 2L);

        assertThat(followed).isFalse();

        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userService, org.mockito.Mockito.times(2)).update(captor.capture());
        assertThat(captor.getAllValues().get(0).getSqlSet()).contains("GREATEST(following_count - 1, 0)");
        assertThat(captor.getAllValues().get(1).getSqlSet()).contains("GREATEST(fans_count - 1, 0)");
    }
}
