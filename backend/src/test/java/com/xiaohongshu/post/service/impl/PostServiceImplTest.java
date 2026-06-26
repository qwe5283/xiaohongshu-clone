package com.xiaohongshu.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaohongshu.interact.mapper.CommentMapper;
import com.xiaohongshu.interact.mapper.UserActionMapper;
import com.xiaohongshu.post.dto.PostUpdateDTO;
import com.xiaohongshu.post.entity.Post;
import com.xiaohongshu.post.entity.PostImage;
import com.xiaohongshu.post.service.PostImageService;
import com.xiaohongshu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostImageService postImageService;
    @Mock
    private UserService userService;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private UserActionMapper userActionMapper;
    @Spy
    @InjectMocks
    private PostServiceImpl postService;

    @Test
    void updatePostClearsCoverImageWhenImagesAreClearedButVideoRemains() {
        Post post = new Post();
        post.setId(10L);
        post.setUserId(1L);
        post.setTitle("title");
        post.setVideoUrl("http://cdn/video.mp4");
        post.setCoverImage("http://cdn/old.png");
        post.setType(1);
        doReturn(post).when(postService).getById(10L);
        doReturn(true).when(postImageService).remove(any(LambdaQueryWrapper.class));
        doReturn(true).when(postService).updateById(any(Post.class));
        doReturn(null).when(postService).getPostById(10L);

        PostUpdateDTO dto = new PostUpdateDTO();
        dto.setId(10L);
        dto.setImageUrls(List.of());

        postService.updatePost(1L, dto);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postService).updateById(captor.capture());
        assertThat(captor.getValue().getCoverImage()).isEmpty();
        assertThat(captor.getValue().getType()).isEqualTo(1);
    }
}
