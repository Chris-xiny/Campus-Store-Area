package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOWS_KEY;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();

        // 不能关注自己
        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }

        String key = FOLLOWS_KEY + userId;

        if (isFollow) {
            // 关注：Redis SADD + DB insert
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            Follow follow = new Follow()
                    .setUserId(userId)
                    .setFollowUserId(followUserId);
            save(follow);
        } else {
            // 取关：Redis SREM + DB delete
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
        }

        return Result.ok();
    }

    @Override
    public Result isFollowed(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOWS_KEY + userId;

        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, followUserId.toString());
        return Result.ok(Boolean.TRUE.equals(isMember));
    }

    @Override
    public Result queryCommonFollows(Long targetUserId) {
        Long userId = UserHolder.getUser().getId();
        String myKey = FOLLOWS_KEY + userId;
        String targetKey = FOLLOWS_KEY + targetUserId;

        // SINTER 取交集：我和对方都关注的人
        Set<String> commonIds = stringRedisTemplate.opsForSet().intersect(myKey, targetKey);

        if (commonIds == null || commonIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 根据 ID 列表查询用户信息
        List<Long> ids = commonIds.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(ids);
        List<UserDTO> userDTOs = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOs);
    }

    @Override
    public Result queryFollowCount(Long userId) {
        String key = FOLLOWS_KEY + userId;
        Long count = stringRedisTemplate.opsForSet().size(key);
        return Result.ok(count == null ? 0 : count.intValue());
    }
}
