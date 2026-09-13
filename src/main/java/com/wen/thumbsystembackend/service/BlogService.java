package com.wen.thumbsystembackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.entity.vo.BlogVO;

import java.util.List;

public interface BlogService extends IService<Blog> {
    /**
     * 获取id为blogId的博客
     * @param blogId 博客id
     * @return
     */
    BaseResponse<BlogVO> getBlogVO(Long blogId);

    /**
     * 获取博客列表
     * @return
     */
    BaseResponse<List<BlogVO>> getBlogVOList();

    /**
     * 发布博客
     * @param blogVO 博客VO 包含博客标题 封面图(可选) 内容
     * @return
     */
    default Long createBlog(BlogVO blogVO){return 0L;};

    /**
     * 获取我的博客列表(用户创建的博客列表)
     * @param pageNum 第几页
     * @param pageSize 每页元素的个数
     * @return
     */
    default BaseResponse<List<BlogVO>> getMineBlogVOList(Long pageNum,Long pageSize){ return null;};

    /**
     * 获取用户的点赞博客列表
     * @param pageNum 第几页
     * @param pageSize 每页元素的个数
     * @return
     */
    default BaseResponse<List<BlogVO>> getMineThumbBlogVOList(Long pageNum, Long pageSize){return null;};


    /**
     * 根据博客VO更新博客
     * @param blogVO
     * @return
     */
    default BaseResponse<Boolean> updateBlog(BlogVO blogVO){return null;};

    /**
     *根据博客id删除博客
     * @param blogId
     * @return
     */
    BaseResponse<Boolean> deleteBlog(Long blogId);

}
