package com.wen.thumbsystembackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.service.BlogService;
import org.springframework.stereotype.Service;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper,Blog> implements BlogService {
}
