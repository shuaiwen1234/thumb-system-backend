package com.wen.thumbsystembackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ErrorCode;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.entity.User;
import com.wen.thumbsystembackend.mapper.UserMapper;
import com.wen.thumbsystembackend.service.UserService;
import com.wen.thumbsystembackend.utils.UserContext;
import org.apache.tomcat.util.http.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public BaseResponse userLogin(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        UserContext.setUser(user);

        return ResultUtils.success(user);
    }
}
