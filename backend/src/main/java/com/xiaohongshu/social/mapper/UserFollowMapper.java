package com.xiaohongshu.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaohongshu.social.entity.UserFollow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户关注Mapper接口
 */
public interface UserFollowMapper extends BaseMapper<UserFollow> {
}
