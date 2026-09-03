package com.wen.thumbsystembackend.controller;

import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ErrorCode;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.entity.vo.BlogVO;
import com.wen.thumbsystembackend.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/blog")
@Tag(name = "博客管理接口")
public class BlogController {
    @Autowired
    @Qualifier("blogServiceRedis")
    private BlogService blogService;

    /**
     * 根据博客id以及当前用户来获取对应的博客VO
     * @param blogId 博客id
     * @return
     */
    @GetMapping()
    @Operation(summary = "获取博客",description = "根据博客id以及当前用户来获取对应的博客VO")
    public BaseResponse<BlogVO> getBlogVObyBlogId(
            @Parameter(description = "博客id",example = "1",required = true)
            @RequestParam Long blogId){
        if(blogId == null){
            return ResultUtils.success(new BlogVO());
        }
        return blogService.getBlogVO(blogId);
    }

    @GetMapping("/list")
    @Operation(summary = "获取博客列表",description = "根据当前登录的用户获取所有的博客vo(当前用户的作用是判断是否点赞了)")
    public BaseResponse<List<BlogVO>> getBlogVOList(){
        return blogService.getBlogVOList();
    }

}
