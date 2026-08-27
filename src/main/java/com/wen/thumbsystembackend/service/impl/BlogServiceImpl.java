package com.wen.thumbsystembackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.common.redis.RedisBlog;
import com.wen.thumbsystembackend.common.redis.RedisLogicData;
import com.wen.thumbsystembackend.constant.BlogConstant;
import com.wen.thumbsystembackend.constant.ThumbConstant;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public BaseResponse<BlogVO> getBlogVO(Long blogId) {
        Long userId = UserContext.getUser().getUserId();

        //根据博客id获取博客
        Blog blog = blogMapper.selectById(blogId);
        if(blog==null){
            throw new RuntimeException("该博客不存在");
        }
        BlogVO vo = new BlogVO();
        BeanUtils.copyProperties(blog,vo);

        //检查当前用户是否给该博客点赞了
        //先判断当前博客是否是热门博客 若是且redis中能查到数据 则是为点赞
        //不是热门博客则需要查询数据库
        RedisBlog redisBlog = (RedisBlog) redisTemplate.opsForValue().get(BlogConstant.BLOG_KEY_PREFIX + blogId);
        if(redisBlog!=null){
            //当前博客是热门博客
            RedisLogicData<Long> data = (RedisLogicData<Long>) redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
            //能查到点赞记录
            if(data!=null){
                //当前用户点过赞且没过期
                if (data.getExpireTime() != null && data.getExpireTime().isAfter(LocalDateTime.now())) {
                    vo.setHasThumb(true);
                    return ResultUtils.success(vo);
                }
                //缓存过期了 删除缓存
                redisTemplate.opsForHash().delete(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
            }else{
                //当前用户没点过赞
                vo.setHasThumb(false);
                return ResultUtils.success(vo);
            }
        }

        //不是热门博客
        RedisLogicData<Long> data = (RedisLogicData<Long>) redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
        if(data!=null){
            //当前用户点过赞且没过期
            if (data.getExpireTime() != null && data.getExpireTime().isAfter(LocalDateTime.now())) {
                vo.setHasThumb(true);
                return ResultUtils.success(vo);
            }
            //缓存过期了 删除缓存
            redisTemplate.opsForHash().delete(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());

        }
        //不是热门博客且redis中也没有对应的点赞记录 则查询数据库
        Thumb thumb = thumbMapper.selectOne(new LambdaQueryWrapper<Thumb>().eq(Thumb::getBlogId, blogId).eq(Thumb::getUserId, UserContext.getUser().getUserId()));
        if(thumb!=null){
            vo.setHasThumb(true);
            return ResultUtils.success(vo);
        }else{
            vo.setHasThumb(false);
            return ResultUtils.success(vo);
        }


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
