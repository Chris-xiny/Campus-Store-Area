package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IFollowService extends IService<Follow> {

    /** 关注/取关 */
    Result follow(Long followUserId, Boolean isFollow);

    /** 查询是否已关注某用户 */
    Result isFollowed(Long followUserId);

    /** 查询共同关注的用户列表 */
    Result queryCommonFollows(Long targetUserId);

    /** 查询某用户的关注数 */
    Result queryFollowCount(Long userId);
}
