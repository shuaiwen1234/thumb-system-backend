package com.wen.thumbsystembackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.entity.User;
import com.wen.thumbsystembackend.mapper.UserMapper;
import com.wen.thumbsystembackend.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
