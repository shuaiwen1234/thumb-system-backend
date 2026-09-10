package com.wen.thumbsystembackend.controller;

import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ErrorCode;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.entity.dto.DoThumbRequest;
import com.wen.thumbsystembackend.service.ThumbService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/thumb")
@Tag(name = "点赞管理接口")
public class ThumbController {
    @Autowired
    @Qualifier("thumbServiceMQ")
    private ThumbService thumbService;
    private Counter successCounter;
    private Counter failureCounter;

    public ThumbController(MeterRegistry meterRegistry) {
        successCounter = Counter.builder("thumb.success.count").description("total successful count").register(meterRegistry);
        failureCounter = Counter.builder("thumb.failure.count").description("total failed count").register(meterRegistry);
    }

    @PostMapping("/do")
    @Operation(summary = "点赞接口")
    /**
     * 根据博客id以及当前登录的用户 对博客进行点赞
     */
    public BaseResponse doThumb(
            @Parameter(description = "博客的id",required = true,example = "{blogId: 1}")
            @RequestBody DoThumbRequest doThumbRequest){

        try {
            if(doThumbRequest==null||doThumbRequest.getBlogId()==null){
                failureCounter.increment();
                return  ResultUtils.success(false);
            }

            BaseResponse<Boolean> booleanBaseResponse = thumbService.doThumb(doThumbRequest);
            Boolean isSuccess = booleanBaseResponse.getData();
            if(isSuccess){
                successCounter.increment();
                return booleanBaseResponse;
            }else{
                failureCounter.increment();
                return booleanBaseResponse;
            }
        } catch (RuntimeException e) {
            failureCounter.increment();
            String msg = e.getMessage();
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, msg);
        }


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
