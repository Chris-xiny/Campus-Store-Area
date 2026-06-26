package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 分页查询热门博客（按点赞数倒序），并填充作者信息。
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据 ID 查询博客详情，并填充作者信息。
     */
    Result queryBlogById(Long id);

    /**
     * 滚动分页查询当前用户关注人的博客。
     */
    Result queryPageOfFollow(Long maxTime, Integer offset);

    /**
     * 点赞 / 取消点赞（切换状态）。
     */
    Result likeBlog(Long blogId);

    /**
     * 查询某篇博文的点赞用户列表（最多返回 5 个）。
     */
    Result queryBlogLikes(Long blogId);
}
