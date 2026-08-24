package com.wen.thumbsystembackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wen.thumbsystembackend.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
