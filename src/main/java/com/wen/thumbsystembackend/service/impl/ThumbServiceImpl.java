package com.wen.thumbsystembackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import com.wen.thumbsystembackend.service.ThumbService;
import org.springframework.stereotype.Service;

@Service
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {
}
