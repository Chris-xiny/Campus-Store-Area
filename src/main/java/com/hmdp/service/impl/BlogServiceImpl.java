package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class  BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 按点赞数倒序分页查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 填充作者信息
        List<Blog> records = page.getRecords();
        fillUserInfo(records);
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        // 填充作者信息（单篇博客复用列表填充逻辑）
        fillUserInfo(blog);
        // 填充当前用户是否已点赞
        fillIsLike(blog);
        return Result.ok(blog);
    }

    /**
     * 批量填充博客的作者昵称和头像。
     */
    private void fillUserInfo(List<Blog> blogs) {
        blogs.forEach(this::fillUserInfo);
    }

    /**
     * 填充单篇博客的作者昵称和头像。
     */
    private void fillUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }

    /**
     * 滚动分页查询当前用户关注的人发布的博文。
     * <p>
     * 前端传 lastId（上一页最后一条的 createTime 毫秒时间戳）和 offset（同一秒已跳过的条数），
     * 每次取一页（PAGE_SIZE 条），返回 ScrollResult{list, offset, minTime}。
     */
    @Override
    public Result queryPageOfFollow(Long maxTime, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        // 1. 从 Redis SET 获取当前用户关注的所有用户 ID
        Set<String> followSet = stringRedisTemplate.opsForSet().members(RedisConstants.FOLLOWS_KEY + userId);
        if (CollUtil.isEmpty(followSet)) {
            return Result.ok(new ScrollResult());
        }
        // 转为 Long 集合
        List<Long> followIds = followSet.stream().map(Long::valueOf).collect(Collectors.toList());

        // 2. 将 maxTime 毫秒时间戳转为 LocalDateTime
        LocalDateTime maxDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(maxTime), ZoneId.systemDefault());

        // 3. 查询关注用户的博文：create_time < maxTime，按时间倒序，取一页 + offset 跳过同秒记录
        Page<Blog> page = query()
                .in("user_id", followIds)
                .lt("create_time", maxDateTime)
                .orderByDesc("create_time")
                .page(new Page<>(1, offset + PAGE_SIZE));

        List<Blog> records = page.getRecords();
        if (CollUtil.isEmpty(records)) {
            return Result.ok(new ScrollResult());
        }

        // 4. 填充作者信息 + 是否已点赞
        fillUserInfo(records);
        records.forEach(this::fillIsLike);

        // 5. 计算下一页参数
        int size = records.size();
        LocalDateTime minDateTime = records.get(size - 1).getCreateTime();
        long minTime = minDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        int nextOffset = 0;
        // 统计末尾同一秒的记录数，作为下次 offset
        if (size == offset + PAGE_SIZE) {
            for (int i = size - 1; i >= 0; i--) {
                long t = records.get(i).getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (t == minTime) {
                    nextOffset++;
                } else {
                    break;
                }
            }
        }

        // 6. 截取有效记录（去掉 offset 跳过的部分）
        List<Blog> list = records.subList(offset, size);

        ScrollResult result = new ScrollResult();
        result.setList(list);
        result.setOffset(nextOffset);
        result.setMinTime(minTime);
        return Result.ok(result);
    }

    /**
     * 点赞 / 取消点赞（切换状态）。
     * 使用 Redis SET blog:liked:{blogId} 存储点赞用户 ID。
     */
    @Override
    public Result likeBlog(Long blogId) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blogId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if (Boolean.TRUE.equals(isMember)) {
            // 已点赞 → 取消点赞
            stringRedisTemplate.opsForSet().remove(key, userId.toString());
            update().setSql("liked = liked - 1").eq("id", blogId).gt("liked", 0).update();
        } else {
            // 未点赞 → 点赞
            stringRedisTemplate.opsForSet().add(key, userId.toString());
            update().setSql("liked = liked + 1").eq("id", blogId).update();
        }
        return Result.ok();
    }

    /**
     * 查询某篇博文的点赞用户列表（最多 5 个），返回 UserDTO 列表。
     */
    @Override
    public Result queryBlogLikes(Long blogId) {
        String key = RedisConstants.BLOG_LIKED_KEY + blogId;
        Set<String> top5 = stringRedisTemplate.opsForSet().members(key);
        if (CollUtil.isEmpty(top5)) {
            return Result.ok(CollUtil.newArrayList());
        }
        List<Long> userIds = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 批量查询用户信息，返回 UserDTO
        List<com.hmdp.dto.UserDTO> userDTOs = userService.listByIds(userIds)
                .stream()
                .map(user -> {
                    com.hmdp.dto.UserDTO dto = new com.hmdp.dto.UserDTO();
                    dto.setId(user.getId());
                    dto.setNickName(user.getNickName());
                    dto.setIcon(user.getIcon());
                    return dto;
                })
                .collect(Collectors.toList());
        return Result.ok(userDTOs);
    }

    /**
     * 填充当前用户是否已点赞该博客。
     */
    private void fillIsLike(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        blog.setIsLike(Boolean.TRUE.equals(isMember));
    }

    /** 关注 Feed 每页条数 */
    private static final int PAGE_SIZE = 5;
}
