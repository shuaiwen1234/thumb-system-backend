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
import com.wen.thumbsystembackend.entity.vo.BlogVO;
import com.wen.thumbsystembackend.manager.cache.CacheManager;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import com.wen.thumbsystembackend.service.BlogService;
import com.wen.thumbsystembackend.utils.UserContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service(value = "blogServiceRedis")
public class BlogServiceRedisImpl extends ServiceImpl<BlogMapper,Blog> implements BlogService {
    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private ThumbMapper thumbMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private CacheManager cacheManager;

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
        //判断该用户是否点过赞
        String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        String key = blogId.toString();
        Object object = cacheManager.get(hashKey, key);
        if(object != null){
            if(Long.valueOf(object.toString()) > 0){
                vo.setHasThumb(true);
                return ResultUtils.success(vo);
            }else{
                vo.setHasThumb(false);
                return ResultUtils.success(vo);
            }
        }
        //点赞记录现在在redis中不会删除
        String redisKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
    Boolean isExist = redisTemplate.opsForHash().hasKey(redisKey, blogId.toString());
        if(isExist==null||!isExist){
            //没点过赞
            vo.setHasThumb(false);
            return ResultUtils.success(vo);
        }
        vo.setHasThumb(true);
        return ResultUtils.success(vo);
    }

    //TODO 这个以后优化
    @Override
    public BaseResponse<List<BlogVO>> getBlogVOList() {
        //TODO 以后这里也要先查redis
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
