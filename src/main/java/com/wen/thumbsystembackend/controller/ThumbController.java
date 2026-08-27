package com.wen.thumbsystembackend.controller;

import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.entity.dto.DoThumbRequest;
import com.wen.thumbsystembackend.service.ThumbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/thumb")
@Tag(name = "点赞管理接口")
public class ThumbController {
    @Autowired
    private ThumbService thumbService;

    @PostMapping("/do")
    @Operation(summary = "点赞接口")
    /**
     * 根据博客id以及当前登录的用户 对博客进行点赞
     */
    public BaseResponse<Boolean> doThumb(
            @Parameter(description = "博客的id",required = true,example = "{blogId: 1}")
            @RequestBody DoThumbRequest doThumbRequest){
        if(doThumbRequest==null||doThumbRequest.getBlogId()==null){
            return  ResultUtils.success(false);
        }

        return thumbService.doThumb(doThumbRequest);
    }

    /**
     * 根据博客id以及当前登录的用户 对博客进行取消点赞
     * @return
     */
    @PostMapping("/undo")
    @Operation(summary = "取消点赞接口")
    public BaseResponse<Boolean> undoThumb(
            @Parameter(description = "博客的id",required = true,example = "{blogId: 1}")
            @RequestBody DoThumbRequest doThumbRequest){
        if(doThumbRequest==null||doThumbRequest.getBlogId()==null){
            return  ResultUtils.success(false);
        }

        return thumbService.undoThumb(doThumbRequest);
    }
}
