package com.wen.thumbsystembackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wen.thumbsystembackend.common.BaseResponse;
import com.wen.thumbsystembackend.common.redis.RedisBlog;
import com.wen.thumbsystembackend.common.redis.RedisLogicData;
import com.wen.thumbsystembackend.common.ResultUtils;
import com.wen.thumbsystembackend.constant.BlogConstant;
import com.wen.thumbsystembackend.constant.ThumbConstant;
import com.wen.thumbsystembackend.entity.Blog;
import com.wen.thumbsystembackend.entity.Thumb;
import com.wen.thumbsystembackend.entity.dto.DoThumbRequest;
import com.wen.thumbsystembackend.mapper.BlogMapper;
import com.wen.thumbsystembackend.mapper.ThumbMapper;
import com.wen.thumbsystembackend.service.ThumbService;
import com.wen.thumbsystembackend.utils.UserContext;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

//因为@Autowired自动注入当类型相同时 会按名称注入
@Service(value = "thumbServiceDB")
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {
    @Autowired
    private ThumbMapper thumbMapper;
    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public BaseResponse<Boolean> doThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();
        // 1. 判断是否点过赞
        // 1.1 点过直接抛出异常
        if(this.hasThumb(userId,blogId)){
            throw new RuntimeException("该用户已对当前文档点赞,请勿重复操作");
        }

        // 1.2 调用点赞之后的更新逻辑
        Long thumbId;
        //这里加锁用userId的原因是锁粒度比blogId小 并且mysql的行级锁可以保证多个用户对同一个博客进行点赞时不会出现多线程安全问题
        synchronized (userId.toString().intern()) {
            //这里进行一下DoubleCheck 进一步保证安全
            //判断该用户是否点过赞
            // 点过直接抛出异常
            if(this.hasThumb(userId,blogId)){
                throw new RuntimeException("该用户已对当前文档点赞,请勿重复操作");
            }

            // 这里使用代理来执行点赞的方法是因为如果使用this会使this调用的方法上的事务失效
            ThumbService proxy = (ThumbService)AopContext.currentProxy();
            thumbId = proxy.doThumbOperation(userId, blogId);

            //根据当前的博客是否是热门博客来决定是否将数据插入redis
            //博客发布时会在redis中存一个月 此时就是热门博客
            //是
            if(redisTemplate.opsForValue().get(BlogConstant.BLOG_KEY_PREFIX + blogId)!=null){
                RedisLogicData<Long> data = new RedisLogicData();
                data.setData(thumbId);
                data.setExpireTime(LocalDateTime.now().plusMonths(1));
                redisTemplate.opsForHash().put(ThumbConstant.USER_THUMB_KEY_PREFIX+userId,blogId.toString(),data);
            }
        }
        return ResultUtils.success(true);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long doThumbOperation(Long userId, Long blogId) {
        //插入点赞表
        Thumb thumb = new Thumb();
        thumb.setUserId(userId);
        thumb.setBlogId(blogId);
        int insert = thumbMapper.insert(thumb);

        //更新博客表 将对应博客的点赞数加一
        LambdaUpdateWrapper<Blog> updateWrapper = new LambdaUpdateWrapper<Blog>();
        updateWrapper.setSql("thumb_count = thumb_count+1");
        updateWrapper.eq(Blog::getId, blogId);
        int update = blogMapper.update(updateWrapper);

        //更新失败直接抛出异常
        //只有抛出异常事务才会回滚
        if (insert <= 0 || update <= 0) {
            throw new RuntimeException("点赞失败");
        }

        return thumb.getId();
    }

    @Override
    public BaseResponse<Boolean> undoThumb(DoThumbRequest doThumbRequest) {
        Long userId = UserContext.getUser().getUserId();
        Long blogId = doThumbRequest.getBlogId();
        // 1.先判断当前用户是否点赞
        // 1.1没点赞则直接抛出异常
        if(!this.hasThumb(userId,blogId)){
            throw new RuntimeException("该用户暂时未对当前文档点赞");
        }

        // 1.2点赞了则进行取消点赞的一系列逻辑
        //博客的点赞数减一 将点赞记录删除
        boolean isSuccess;
        synchronized (userId.toString().intern()) {
            //这里也先做一下DoubleCheck
            // 判断当前用户是否点赞
            if(!this.hasThumb(userId,blogId)){
                throw new RuntimeException("该用户暂时未对当前文档点赞");
            }

            // 这里使用代理来执行点赞的方法是因为如果使用this会使this调用的方法上的事务失效
            ThumbService proxy = (ThumbService)AopContext.currentProxy();
            isSuccess = proxy.undoThumbOperation(userId, blogId);
            //从redis中删除数据
            redisTemplate.opsForHash().delete(ThumbConstant.USER_THUMB_KEY_PREFIX+userId,blogId.toString());
        }
        return ResultUtils.success(isSuccess);

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean undoThumbOperation(Long userId, Long blogId) {
        //删除点赞记录
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Thumb::getUserId, userId);
        wrapper.eq(Thumb::getBlogId, blogId);
        int delete = thumbMapper.delete(wrapper);
        //更新失败直接抛出异常
        //只有抛出异常 事务才会回滚
        if(delete<=0){
            throw new RuntimeException("取消点赞失败,该用户未对当前文档进行点赞");
        }

        //将博客的点赞记录减一
        LambdaUpdateWrapper<Blog> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.setSql("thumb_count = thumb_count-1");
        updateWrapper.eq(Blog::getId, blogId);
        int update = blogMapper.update(updateWrapper);

        //更新失败直接抛出异常
        //只有抛出异常 事务才会回滚
        if(update<=0){
            throw new RuntimeException("博客点赞数更新失败");
        }

        return true;
    }

    //判断是否点赞
    @Override
    public Boolean hasThumb(Long userId, Long blogId) {
        // 0.先从redis中查询对应的博客判断是否为热门博客(发布时间为一个月之内的)
        RedisBlog blog = (RedisBlog) redisTemplate.opsForValue().get(BlogConstant.BLOG_KEY_PREFIX + blogId);
        boolean isHot = blog!=null;


        // 1.先去redis中查
        RedisLogicData<Long> data = (RedisLogicData<Long>)redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX+userId.toString(), blogId.toString());
        if(data!=null){
            // 1.1查到了 判断是否过期
            LocalDateTime expireTime = data.getExpireTime();

            // 1.1.1未过期直接返回
            if(expireTime != null&&expireTime.isAfter(LocalDateTime.now())){
                return true;
            }

            // 1.1.2过期了先从redis中删除对应数据
            if(expireTime != null&&expireTime.isBefore(LocalDateTime.now())){
                redisTemplate.opsForHash().delete(ThumbConstant.USER_THUMB_KEY_PREFIX+userId, blogId.toString());
            }
        }

        // 1.2没查到 如果是热门博客 是直接返回false(未点赞)
        //因为当前博客是热门博客 点赞了缓存是不会因为过期而删除的 未查询到就只能代表没点过赞
        if(isHot){
            return false;
        }
        // 2.去数据库查询
        Thumb thumb = thumbMapper.selectOne(new LambdaQueryWrapper<Thumb>().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId));

        // 返回
        return  thumb != null;
    }
}
