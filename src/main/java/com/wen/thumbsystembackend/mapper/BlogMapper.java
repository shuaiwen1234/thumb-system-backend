package com.wen.thumbsystembackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wen.thumbsystembackend.entity.Blog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogMapper extends BaseMapper<Blog> {
}
