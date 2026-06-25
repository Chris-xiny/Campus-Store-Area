package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /** 关注/取关 */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId,
                         @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /** 查询是否已关注某用户 */
    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable("id") Long followUserId) {
        return followService.isFollowed(followUserId);
    }

    /** 查询我和某用户的共同关注列表 */
    @GetMapping("/common/{id}")
    public Result queryCommonFollows(@PathVariable("id") Long targetUserId) {
        return followService.queryCommonFollows(targetUserId);
    }

    /** 查询某用户的关注数 */
    @GetMapping("/count/{id}")
    public Result queryFollowCount(@PathVariable("id") Long userId) {
        return followService.queryFollowCount(userId);
    }
}
