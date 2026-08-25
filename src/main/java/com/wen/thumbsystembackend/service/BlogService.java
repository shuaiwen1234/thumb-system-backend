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
}
