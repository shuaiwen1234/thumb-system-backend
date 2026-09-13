package com.wen.thumbsystembackend.controller;

import cn.hutool.core.util.StrUtil;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ErrorCode;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.entity.vo.BlogVO;
import com.wen.thumbsystembackend.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/create")
    @Operation(summary = "发布博客",description = "当前登录的用户进行发布博客")
    public BaseResponse<Long> createBlog(
            @Parameter(description = "博客VO 包含博客的标题,封面图以及内容",required = true)
            @RequestBody BlogVO blogVO){
        if(blogVO == null){
            throw new RuntimeException("创建的博客不能为空");
        }
        if(StrUtil.isBlank(blogVO.getTitle())){
            throw new RuntimeException("创建的博客时标题不能为空");
        }
        if(StrUtil.isBlank(blogVO.getContent())){
            throw new RuntimeException("创建博客时内容不能为空");
        }

        Long BlogId = blogService.createBlog(blogVO);
        return  ResultUtils.success(BlogId);
    }

    @GetMapping("/mine")
    @Operation(summary = "查看用户创建的博客",description = "根据当前登录的用户查看他创建的博客")
    public BaseResponse<List<BlogVO>> getMineBlogList(
            @Parameter(description = "分页数 默认为1",required = false)
            @RequestParam(value = "pageNum") Long pageNum,
            @Parameter(description = "分页大小 默认为10",required = false)
            @RequestParam(value = "pageSize") Long pageSize){
        if(pageNum == null){
            pageNum = 1L;
        }
        if(pageSize == null){
            pageSize = 10L;
        }
        return blogService.getMineBlogVOList(pageNum,pageSize);
    }

    @GetMapping("/mine/thumb")
    @Operation(summary = "查看用户点赞的博客", description = "根据当前登录的用户查看他点赞的博客")
    public BaseResponse<List<BlogVO>> getMineThumbBlogList(
            @Parameter(description = "分页数 默认为1", required = false)
            @RequestParam(value = "pageNum") Long pageNum,
            @Parameter(description = "分页大小 默认为10", required = false)
            @RequestParam(value = "pageSize") Long pageSize) {
        if (pageNum == null) {
            pageNum = 1L;
        }
        if(pageSize == null){
            pageSize = 10L;
        }
        return blogService.getMineThumbBlogVOList(pageNum,pageSize);
    }


    @PostMapping("/update")
    @Operation(summary = "修改博客",description = "修改博客 非该博客的记者无法修改")
    public BaseResponse<Boolean> updateBlog(
            @Parameter(description = "博客VO 前端传的字段包含id title coverImg(可选) content",required = true)
            @RequestBody BlogVO blogVO
    ) {
        if(blogVO == null){
            throw new RuntimeException("请求异常 请稍后再试");
        }
        if(StrUtil.isBlank(blogVO.getTitle())){
            throw new RuntimeException("操作错误 博客标题不可为空");
        }
        if(StrUtil.isBlank(blogVO.getContent())){
            throw new RuntimeException("操作错误 博客内容不可为空");
        }
        if(blogVO.getId() == null){
            throw new RuntimeException("操作错误 博客id不可为空");
        }

        return blogService.updateBlog(blogVO);
    }

    /**
     * 删除博客的同时也要删除预期相关的数据
     * @param blogId
     * @return
     */
    @Operation(summary = "删除博客",description = "删除用户选择的博客")
    @DeleteMapping("/{blogId}")
    public BaseResponse<Boolean> deleteBlog(
            @Parameter(description = "要删除的博客的id",required = true,example = "1")
            @PathVariable Long blogId){
        if(blogId == null){
            throw new RuntimeException("系统繁忙 请稍后再试");
        }

        return blogService.deleteBlog(blogId);
    }
}
