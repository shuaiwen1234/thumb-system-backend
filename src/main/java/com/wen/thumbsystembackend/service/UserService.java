package com.wen.thumbsystembackend.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.entity.User;

public interface UserService extends IService<User> {
    /**
     * 用户登录
     * @param userId 用户id
     * @return
     */
    BaseResponse userLogin(Long userId);
}
