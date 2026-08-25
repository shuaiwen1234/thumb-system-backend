package com.wen.thumbsystembackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.entity.User;
import com.wen.thumbsystembackend.entity.vo.BlogVO;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import com.wen.thumbsystembackend.service.BlogService;
import com.wen.thumbsystembackend.utils.UserContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper,Blog> implements BlogService {
    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private ThumbMapper thumbMapper;

    @Override
    public BaseResponse<BlogVO> getBlogVO(Long blogId) {

        //根据博客id获取博客
        Blog blog = blogMapper.selectById(blogId);
        if(blog==null){
            return ResultUtils.success(new BlogVO());
        }
        BlogVO vo = new BlogVO();
        BeanUtils.copyProperties(blog,vo);
        if(UserContext.getUser()==null){
            return ResultUtils.success(vo);
        }
        //检查当前用户是否给该博客点赞了
        Long userId = UserContext.getUser().getUserId();
        LambdaQueryWrapper<Thumb> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Thumb::getUserId,userId);
        queryWrapper.eq(Thumb::getBlogId,blogId);
        Thumb thumb = thumbMapper.selectOne(queryWrapper);
        //没点
        if(thumb==null){
            vo.setHasThumb(false);
            return ResultUtils.success(vo);
        }

        //点了
        vo.setHasThumb(true);
        return ResultUtils.success(vo);
    }

    @Override
    public BaseResponse<List<BlogVO>> getBlogVOList() {
        Long userId = UserContext.getUser().getUserId();
        //查询所有的博客
        List<Blog> blogs = blogMapper.selectList(null);
        //查出现在登录的用户的点赞记录
        List<Thumb> thumbs = thumbMapper.selectList(new LambdaQueryWrapper<Thumb>().eq(Thumb::getUserId, userId));
        //点赞的博客的id的集合
        List<Long> collect = thumbs.stream().map(thumb -> thumb.getBlogId()).collect(Collectors.toList());

        List<BlogVO> vos = new ArrayList<>();
        for(Blog blog : blogs){
            BlogVO vo = new BlogVO();
            BeanUtils.copyProperties(blog,vo);
            if(collect.contains(blog.getId())){
                vo.setHasThumb(true);
            }else{
                vo.setHasThumb(false);
            }
            vos.add(vo);
        }
        return ResultUtils.success(vos);

    }
}
