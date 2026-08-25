package com.wen.thumbsystembackend.controller;

import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ErrorCode;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理模块")
public class UserController {

    @Autowired
    private UserService userService;
    /**
     * 用户登录
     */
    @Operation(summary = "用户登录接口")
    @GetMapping("/login")
    public BaseResponse UserLogin(
            @Parameter(description = "用户ID", example = "1", required = true)
            @RequestParam Long userId) {
        if(userId==null){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        return userService.userLogin(userId);

    }

}
